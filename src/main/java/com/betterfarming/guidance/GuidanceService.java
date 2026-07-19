package com.betterfarming.guidance;

import com.betterfarming.travel.RoutePlanner;
import com.betterfarming.ui.ClientLevelSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;

/**
 * Tracks progress through the planned run: which stops the player has already
 * reached, and which leg is currently being travelled. The current leg is the
 * first stop in run order the player has not yet visited; a stop counts as
 * visited the moment the player comes within {@link #ARRIVAL_RADIUS_TILES} of
 * it, in any order — doing stop 5 before stop 2 simply checks off stop 5, and
 * guidance keeps pointing at stop 2.
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

	private final Supplier<List<RoutePlanner.Leg>> legsSupplier;
	private final ClientLevelSource client;

	private final Set<String> visitedKeys = new HashSet<>();
	private final Set<Runnable> listeners = new LinkedHashSet<>();

	private volatile RoutePlanner.Leg currentLeg;
	private volatile int currentIndex = -1;
	private volatile int totalLegs = 0;
	private volatile List<WorldPoint> remainingTargets = Collections.emptyList();
	private volatile boolean runComplete = false;

	/**
	 * @param legsSupplier the planned run order (RunOrderService::legs in
	 *     production; a mutable holder in tests).
	 */
	public GuidanceService(Supplier<List<RoutePlanner.Leg>> legsSupplier, ClientLevelSource client)
	{
		this.legsSupplier = legsSupplier;
		this.client = client;
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

	/** Clears run progress; the next tick starts guiding from leg 1 again. */
	public void reset()
	{
		visitedKeys.clear();
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
			// Plane ignored: arriving on a bridge or rooftop above the
			// patch tile still counts as being there.
			if (player.distanceTo2D(leg.stop().point()) <= ARRIVAL_RADIUS_TILES)
			{
				visitedKeys.add(leg.stop().groupKey());
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
		return a.stop().groupKey().equals(b.stop().groupKey()) && a.teleport() == b.teleport();
	}

	private void notifyListeners()
	{
		for (Runnable l : new ArrayList<>(listeners))
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
