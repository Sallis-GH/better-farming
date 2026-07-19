package com.betterfarming.guidance;

import com.betterfarming.farming.StopProgress;
import com.betterfarming.travel.RoutePlanner;
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
 * crops are actually in the ground. Two escapes keep INCOMPLETE stops from
 * trapping the run: walking {@link #SKIP_DISTANCE_TILES} away from a stop
 * the player already reached counts as deliberately skipping it (no seeds,
 * no cure — move on), and a checked-off stop that regresses to needing work
 * (harvested in passing, disease) is un-checked. Only when state is UNKNOWN
 * does the proximity rule apply: visited the moment the player comes within
 * {@link #ARRIVAL_RADIUS_TILES} — unless the stop was first sighted with the
 * player already standing there (fresh reset/login at a patch), which
 * requires leaving and returning.
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

	/** Leaving a reached-but-unfinished stop this far behind skips it. */
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
	/** INCOMPLETE stops the player has reached — walking far away skips them. */
	private final Set<String> workStarted = new HashSet<>();
	/** Stops checked off by crop state, so a regression can un-check them. */
	private final Set<String> stateCompleted = new HashSet<>();
	// Copy-on-write: add/removeListener run on the EDT (plugin lifecycle),
	// the fanout iterates on the client thread.
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

	private volatile RoutePlanner.Leg currentLeg;
	private volatile int currentIndex = -1;
	private volatile int totalLegs = 0;
	private volatile List<WorldPoint> remainingTargets = Collections.emptyList();
	private volatile boolean runComplete = false;

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

	public void addListener(Runnable l)    { listeners.add(l); }
	public void removeListener(Runnable l) { listeners.remove(l); }

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
		refresh();
	}

	/** Recomputes state and always notifies, even without a leg change. */
	public void refresh()
	{
		recompute();
		notifyListeners();
	}

	/** Recomputes state, notifying only when the current leg changed. */
	public void update()
	{
		RoutePlanner.Leg before = currentLeg;
		boolean completeBefore = runComplete;
		recompute();
		if (!sameLeg(before, currentLeg) || completeBefore != runComplete)
		{
			notifyListeners();
		}
	}

	private void recompute()
	{
		List<RoutePlanner.Leg> legs = legsSupplier.get();
		WorldPoint player = client.getPlayerPosition();

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
				// Crops confirmed in the ground: done regardless of position.
				visitedKeys.add(key);
				stateCompleted.add(key);
				requireExit.remove(key);
				workStarted.remove(key);
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
					// Reached it, left it far behind unfinished: a deliberate
					// skip (no seeds, no cure) — stop pointing backwards.
					workStarted.remove(key);
					visitedKeys.add(key);
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
				visitedKeys.add(key);
			}
		}

		RoutePlanner.Leg next = null;
		int index = -1;
		List<WorldPoint> remaining = new ArrayList<>();
		for (int i = 0; i < legs.size(); i++)
		{
			RoutePlanner.Leg leg = legs.get(i);
			if (visitedKeys.contains(leg.stop().groupKey()))
			{
				continue;
			}
			if (next == null)
			{
				next = leg;
				index = i + 1;
			}
			remaining.add(leg.stop().point());
		}

		currentLeg = next;
		currentIndex = index;
		totalLegs = legs.size();
		remainingTargets = Collections.unmodifiableList(remaining);
		runComplete = !legs.isEmpty() && next == null;
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
