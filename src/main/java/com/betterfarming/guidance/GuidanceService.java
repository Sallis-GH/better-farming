package com.betterfarming.guidance;

import com.betterfarming.farming.StopProgress;
import com.betterfarming.travel.RoutePlanner;
import com.betterfarming.travel.Teleport;
import com.betterfarming.ui.ClientLevelSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;

/**
 * Tracks progress through the planned run: which stops are done, and which
 * leg is currently being travelled. The current leg is the first stop in run
 * order not yet done.
 *
 * Completion is crop-state first: a stop whose patches are all confirmed
 * growing is COMPLETE (checked off wherever the player is — planting stop 5
 * before stop 2 checks off stop 5); a stop with known remaining work is
 * INCOMPLETE and stays current even while the player stands on it, until the
 * crops are actually in the ground. A checked-off stop that regresses to
 * needing work (harvested in passing, disease) is un-checked. Only when
 * state is UNKNOWN does the proximity rule apply: visited the moment the
 * player comes within {@link #ARRIVAL_RADIUS_TILES} — unless the stop was
 * first sighted with the player already standing there (fresh reset/login at
 * a patch), which requires leaving and returning.
 *
 * Skipping never completes: walking {@link #SKIP_DISTANCE_TILES} away from a
 * reached-but-unfinished stop, or pressing Skip, DEFERS the stop — guidance
 * moves on so the arrow stops pointing backwards, but the stop stays in the
 * remaining route, comes back as current once everything else is done, and
 * blocks run completion (a patch is only done once observed harvested and
 * replanted; end the run early with Stop instead). Deferrals are dropped
 * whenever the run is stopped, so resuming re-offers every skipped stop.
 *
 * Visited stops survive route recomputes on purpose: consuming a teleport tab
 * mid-run changes teleport availability, which re-plans the remaining route,
 * and that must not resurrect stops already done. Progress clears on logout
 * and via reset() (wired to the hint overlay's right-click menu).
 *
 * Threading: onGameTick runs on the client thread and is the only writer;
 * overlays read the volatile snapshot fields from the client thread, the
 * fanout may be observed from listeners doing their own marshalling.
 */
@Slf4j
public class GuidanceService
{
	public static final int ARRIVAL_RADIUS_TILES = 10;

	/** Leaving a reached-but-unfinished stop this far behind defers it. */
	public static final int SKIP_DISTANCE_TILES = 50;

	private final Supplier<List<RoutePlanner.Leg>> legsSupplier;
	private final ClientLevelSource client;
	private final Function<RoutePlanner.Stop, StopProgress> stopProgress;

	private final Set<String> visitedKeys = new HashSet<>();
	/**
	 * Stops first sighted with the player already inside the arrival radius
	 * (reset taken at a patch, login next to one): they only count as visited
	 * after the player leaves and comes back.
	 */
	private final Set<String> requireExit = new HashSet<>();
	/** Stops observed at least once since the last reset. */
	private final Set<String> seenKeys = new HashSet<>();
	/** INCOMPLETE stops the player has reached — walking far away defers them. */
	private final Set<String> workStarted = new HashSet<>();
	/** Stops checked off by crop state, so a regression can un-check them. */
	private final Set<String> stateCompleted = new HashSet<>();
	/**
	 * Stops set aside by Skip or the walk-away rule: not selected as current
	 * while other work remains, never counted as done. Cleared while the run
	 * is stopped, so resuming re-offers them. Client thread only.
	 */
	private final Set<String> deferred = new HashSet<>();
	// Copy-on-write: add/removeListener run on the EDT (plugin lifecycle),
	// the fanout iterates on the client thread.
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

	private volatile RoutePlanner.Leg currentLeg;
	private volatile int currentIndex = -1;
	private volatile int totalLegs = 0;
	private volatile List<WorldPoint> remainingTargets = Collections.emptyList();
	private volatile boolean runComplete = false;
	private volatile WorldPoint travelTarget;
	private volatile Teleport travelHop;
	private volatile boolean walkPreferred;

	/**
	 * Explicit run lifecycle: guidance only guides while a run is started.
	 * Default stopped — active patch groups alone must not paint arrows over
	 * unrelated play. Volatile flag written from UI threads (sidebar button on
	 * the EDT, overlay menu on the client thread); the next client-thread
	 * recompute applies it, so no client state is touched off-thread.
	 */
	private volatile boolean runActive;

	/** Skip requests queue here and are consumed on the next recompute. */
	private volatile boolean skipRequested;

	/**
	 * Fired (client thread) when the player turns up at an unvisited stop
	 * other than the current leg — they went their own way, so the remaining
	 * route should re-plan from where they actually are. Wired to
	 * RunOrderService::replan by the plugin. Throttled to once per stop.
	 */
	private volatile Runnable deviationListener;
	private String lastDeviationKey;

	/** Convenience: no crop-state input — pure proximity-based completion. */
	public GuidanceService(Supplier<List<RoutePlanner.Leg>> legsSupplier, ClientLevelSource client)
	{
		this(legsSupplier, client, stop -> StopProgress.UNKNOWN);
	}

	/**
	 * @param legsSupplier the planned run order (RunOrderService::legs in
	 *     production; a mutable holder in tests).
	 * @param stopProgress crop-state progress per stop
	 *     (PatchStateService::groupProgress in production; return UNKNOWN for
	 *     pure proximity behaviour).
	 */
	public GuidanceService(Supplier<List<RoutePlanner.Leg>> legsSupplier, ClientLevelSource client,
		Function<RoutePlanner.Stop, StopProgress> stopProgress)
	{
		this.legsSupplier = legsSupplier;
		this.client = client;
		this.stopProgress = stopProgress;
	}

	public RoutePlanner.Leg currentLeg()
	{
		return currentLeg;
	}

	/** 1-based position of the current leg in the run, or -1 when idle. */
	public int currentIndex()
	{
		return currentIndex;
	}

	public int totalLegs()
	{
		return totalLegs;
	}

	/** Stop tiles from the current leg onward, for world-map route lines. */
	public List<WorldPoint> remainingTargets()
	{
		return remainingTargets;
	}

	/** True when the run has legs and every stop has been reached. */
	public boolean runComplete()
	{
		return runComplete;
	}

	/**
	 * The tile guidance arrows should aim at right now: for multi-hop legs
	 * this walks the chain one waypoint at a time (the gangplank, the ferry
	 * NPC), not the far-away final stop; null when idle.
	 */
	public WorldPoint travelTarget()
	{
		return travelTarget;
	}

	/**
	 * The hop to execute at/toward {@link #travelTarget()} — cast this item,
	 * board here — or null when the remaining travel is plain walking.
	 */
	public Teleport travelHop()
	{
		return travelHop;
	}

	/**
	 * True when plain running from where the player stands beats the current
	 * leg's planned teleport: the pinned route priced this leg from the
	 * previous stop, not from here. The hint overlay shows "Walk"; travelHop
	 * is null while this holds, which also suppresses the spell/item/boarding
	 * highlights. The pinned route itself is never touched.
	 */
	public boolean walkPreferred()
	{
		return walkPreferred;
	}

	public boolean runActive()
	{
		return runActive;
	}

	/**
	 * Starts/stops the run. Stopped means idle everywhere: no current leg, no
	 * arrows/hints/highlights, no shortest-path posts, no deviation replans.
	 * Progress is kept — stop is pause, not reset. Takes effect on the next
	 * GameTick (within 0.6 s); callable from any thread.
	 */
	public void setRunActive(boolean active)
	{
		this.runActive = active;
	}

	/**
	 * Defers the current leg on the next recompute — the on-demand
	 * counterpart of the {@link #SKIP_DISTANCE_TILES} walk-away rule (no
	 * seeds, patch inaccessible right now). The stop is set aside, not
	 * completed: it returns once the rest of the run is done, and always on
	 * resume after a stop. Callable from any thread.
	 */
	public void requestSkipCurrentLeg()
	{
		skipRequested = true;
	}

	public void addListener(Runnable l)    { listeners.add(l); }
	public void removeListener(Runnable l) { listeners.remove(l); }

	public void setOnDeviation(Runnable listener)
	{
		this.deviationListener = listener;
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		update();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			// Logging out ends the run: progress clears, and the next session
			// must opt back in via Start rather than resume arrows unasked.
			runActive = false;
			reset();
		}
	}

	/**
	 * Clears run progress; guidance starts over from leg 1. Stops the player
	 * is standing at re-arm via the first-sighting rule in recompute() — no
	 * position snapshot needed here, so a replan landing a tick later still
	 * gets the same treatment.
	 */
	public void reset()
	{
		visitedKeys.clear();
		requireExit.clear();
		seenKeys.clear();
		workStarted.clear();
		stateCompleted.clear();
		deferred.clear();
		refresh();
	}

	/** Recomputes state and always notifies, even without a leg change. */
	public void refresh()
	{
		recompute();
		notifyListeners();
	}

	/** Recomputes state, notifying when the leg or travel waypoint changed. */
	public void update()
	{
		RoutePlanner.Leg before = currentLeg;
		boolean completeBefore = runComplete;
		WorldPoint targetBefore = travelTarget;
		boolean walkBefore = walkPreferred;
		recompute();
		// Waypoint changes matter to listeners too: the shortest-path bridge
		// re-aims its tile path at each chain waypoint.
		if (!sameLeg(before, currentLeg) || completeBefore != runComplete
			|| !Objects.equals(targetBefore, travelTarget) || walkBefore != walkPreferred)
		{
			notifyListeners();
		}
	}

	private void recompute()
	{
		if (!runActive)
		{
			// Stopped: idle everywhere, progress retained. A skip queued just
			// before stopping is dropped — it referred to a leg no longer shown.
			// Deferrals drop too: resuming must re-offer every skipped stop.
			skipRequested = false;
			deferred.clear();
			currentLeg = null;
			currentIndex = -1;
			totalLegs = 0;
			remainingTargets = Collections.emptyList();
			runComplete = false;
			travelTarget = null;
			travelHop = null;
			walkPreferred = false;
			return;
		}
		List<RoutePlanner.Leg> legs = legsSupplier.get();
		WorldPoint player = client.getPlayerPosition();
		String previousCurrentKey = currentLeg != null ? currentLeg.stop().groupKey() : null;
		String newlyVisitedOffPlan = null;

		if (skipRequested)
		{
			skipRequested = false;
			if (previousCurrentKey != null)
			{
				deferred.add(previousCurrentKey);
				workStarted.remove(previousCurrentKey);
			}
		}

		if (player == null)
		{
			// Logged out: nothing to guide. Visited keys are cleared by the
			// LOGIN_SCREEN subscriber, not here — a brief null during loading
			// screens must not wipe run progress.
			currentLeg = null;
			currentIndex = -1;
			totalLegs = 0;
			remainingTargets = Collections.emptyList();
			runComplete = false;
			travelTarget = null;
			travelHop = null;
			return;
		}

		for (RoutePlanner.Leg leg : legs)
		{
			String key = leg.stop().groupKey();
			StopProgress progress;
			try
			{
				progress = stopProgress.apply(leg.stop());
			}
			// A throwing progress function must degrade to proximity mode,
			// not kill the whole per-tick update for the session.
			catch (Exception | AssertionError ex)
			{
				log.warn("Better Farming: stop progress for {} threw", key, ex);
				progress = StopProgress.UNKNOWN;
			}
			// Plane ignored in distances: arriving on a bridge or rooftop
			// above the patch tile still counts as being there.
			int distance = player.distanceTo2D(leg.stop().point());
			boolean near = distance <= ARRIVAL_RADIUS_TILES;

			if (progress == StopProgress.COMPLETE)
			{
				// Crops confirmed in the ground: done regardless of position —
				// this is also how a deferred stop the player returns to on
				// their own gets checked off (state, never proximity).
				visitedKeys.add(key);
				stateCompleted.add(key);
				requireExit.remove(key);
				workStarted.remove(key);
				deferred.remove(key);
				continue;
			}
			if (progress == StopProgress.INCOMPLETE)
			{
				if (stateCompleted.remove(key))
				{
					// Regression (harvested in passing, disease): needs work
					// again, un-check it.
					visitedKeys.remove(key);
				}
				if (near)
				{
					workStarted.add(key);
				}
				else if (workStarted.contains(key) && distance > SKIP_DISTANCE_TILES)
				{
					// Reached it, left it far behind unfinished: defer — stop
					// pointing backwards, but the stop is NOT done (a patch
					// completes only on an observed harvest/replant) and comes
					// back once the rest of the run is.
					workStarted.remove(key);
					deferred.add(key);
				}
				continue;
			}
			// UNKNOWN state: proximity fallback.
			if (seenKeys.add(key) && near)
			{
				// First sighted with the player already here (reset/login at
				// the patch): require leaving and returning.
				requireExit.add(key);
				continue;
			}
			if (requireExit.contains(key))
			{
				if (!near)
				{
					requireExit.remove(key);
				}
			}
			else if (near)
			{
				if (visitedKeys.add(key) && !key.equals(previousCurrentKey))
				{
					// Checked off a stop we weren't guiding to — the player
					// went their own way; the rest should re-plan from here.
					newlyVisitedOffPlan = key;
				}
			}
		}

		RoutePlanner.Leg next = null;
		int index = -1;
		RoutePlanner.Leg nextDeferred = null;
		int indexDeferred = -1;
		List<WorldPoint> remaining = new ArrayList<>();
		for (int i = 0; i < legs.size(); i++)
		{
			RoutePlanner.Leg leg = legs.get(i);
			String key = leg.stop().groupKey();
			if (visitedKeys.contains(key))
			{
				continue;
			}
			if (deferred.contains(key))
			{
				if (nextDeferred == null)
				{
					nextDeferred = leg;
					indexDeferred = i + 1;
				}
			}
			else if (next == null)
			{
				next = leg;
				index = i + 1;
			}
			remaining.add(leg.stop().point());
		}
		// Deferred stops come back once everything else is done: they are
		// remaining work, never silently completed. Ending the run without
		// them is what Stop is for.
		if (next == null && nextDeferred != null)
		{
			next = nextDeferred;
			index = indexDeferred;
		}

		// Deviation: the player turned up at a different unvisited stop (own
		// teleport, changed mind). Guide the stop they are actually at, and
		// fire the replan hook once so the remaining order re-solves from
		// here instead of pointing backwards. Deferred stops are excluded —
		// standing next to a stop just skipped must not re-current it (its
		// state completion still registers if the player works it anyway).
		String deviationKey = newlyVisitedOffPlan;
		for (int i = 0; i < legs.size(); i++)
		{
			RoutePlanner.Leg leg = legs.get(i);
			String key = leg.stop().groupKey();
			if (next != null && !key.equals(next.stop().groupKey())
				&& !visitedKeys.contains(key) && !deferred.contains(key)
				&& player.distanceTo2D(leg.stop().point()) <= ARRIVAL_RADIUS_TILES)
			{
				deviationKey = key;
				next = leg;
				index = i + 1;
				break;
			}
		}
		if (deviationKey != null && !deviationKey.equals(lastDeviationKey))
		{
			lastDeviationKey = deviationKey;
			Runnable listener = deviationListener;
			if (listener != null)
			{
				try
				{
					listener.run();
				}
				catch (Exception | AssertionError ex)
				{
					log.warn("Better Farming: deviation listener threw", ex);
				}
			}
		}
		else if (deviationKey == null)
		{
			lastDeviationKey = null;
		}

		currentLeg = next;
		currentIndex = index;
		totalLegs = legs.size();
		remainingTargets = Collections.unmodifiableList(remaining);
		runComplete = !legs.isEmpty() && next == null;
		updateTravelTarget(player, next);
	}

	/** Being this close to a hop's landing/boarding area counts as being there. */
	static final int HOP_REACHED_RADIUS_TILES = 40;

	/**
	 * Walks the player through the current leg's travel chain one waypoint at
	 * a time. Progress is sequential and position-derived: standing near hop
	 * i's destination means hops 0..i are done (the next instruction is hop
	 * i+1), and standing near hop i's boarding origin means hop i is current.
	 * Straight-line "which waypoint is closest" reasoning is useless here —
	 * chains exist precisely where the crow-flies distance lies (the Harmony
	 * dock is geometrically closer to Lumbridge than Port Phasmatys is).
	 *
	 * A hop's waypoint is its origin when it has one (walk there and board)
	 * or its destination (cast-from-anywhere; the item highlight leads).
	 */
	private void updateTravelTarget(WorldPoint player, RoutePlanner.Leg leg)
	{
		if (leg == null || player == null)
		{
			travelTarget = null;
			travelHop = null;
			walkPreferred = false;
			return;
		}
		Teleport t = leg.teleport();
		List<Teleport> hops = t == null ? Collections.emptyList()
			: (t.chainHops() != null ? t.chainHops() : Collections.singletonList(t));
		int m = hops.size();

		int progress = 0;
		for (int i = 0; i < m; i++)
		{
			if (player.distanceTo2D(hops.get(i).destination()) <= HOP_REACHED_RADIUS_TILES)
			{
				progress = Math.max(progress, i + 1);
			}
			if (hops.get(i).origin() != null
				&& player.distanceTo2D(hops.get(i).origin()) <= HOP_REACHED_RADIUS_TILES)
			{
				progress = Math.max(progress, i);
			}
		}

		// Walk-beats-teleport only applies before the chain starts: once the
		// player is mid-chain (progress > 0), pricing the whole teleport from
		// their position overstates it — hops already done would count again —
		// and the remaining hops are usually the only way onward anyway.
		if (progress == 0 && RoutePlanner.walkBeatsTeleport(player, leg))
		{
			travelTarget = leg.stop().point();
			travelHop = null;
			walkPreferred = true;
			return;
		}
		walkPreferred = false;

		if (progress >= m)
		{
			travelTarget = leg.stop().point();
			travelHop = null;
		}
		else
		{
			Teleport hop = hops.get(progress);
			travelTarget = hop.origin() != null ? hop.origin() : hop.destination();
			travelHop = hop;
		}
	}

	private static boolean sameLeg(RoutePlanner.Leg a, RoutePlanner.Leg b)
	{
		if (a == null || b == null)
		{
			return a == b;
		}
		// Structural teleport equality: composed house-chain edges are freshly
		// allocated on every teleport refresh, so identity would report a
		// change (and fan out) on every route recompute.
		return a.stop().groupKey().equals(b.stop().groupKey())
			&& Objects.equals(a.teleport(), b.teleport());
	}

	private void notifyListeners()
	{
		// CopyOnWriteArrayList iteration is already a snapshot.
		for (Runnable l : listeners)
		{
			try
			{
				l.run();
			}
			// AssertionError included: RuneLite's dev-mode thread assertions
			// must not let one listener starve the rest of the fanout.
			catch (Exception | AssertionError ex)
			{
				log.warn("Better Farming: guidance listener {} threw", l.getClass().getName(), ex);
			}
		}
	}
}
