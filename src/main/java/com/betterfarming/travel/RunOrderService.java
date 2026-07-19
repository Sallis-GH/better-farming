package com.betterfarming.travel;

import com.betterfarming.BetterFarmingConfig;
import com.betterfarming.data.FarmingData;
import com.betterfarming.data.PatchGroup;
import com.betterfarming.state.GroupActiveEvent;
import com.betterfarming.state.PatchSelectionService;
import com.betterfarming.ui.ClientLevelSource;
import com.betterfarming.ui.PatchAccessibilityEvent;
import com.betterfarming.ui.PatchAccessibilityService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

/**
 * Produces the ordered run plan (RoutePlanner legs) for the currently active
 * patch groups, re-planning when group activation, accessibility, or teleport
 * availability changes.
 *
 * The player's position moves constantly, so it is NOT a recompute trigger —
 * the route is planned from wherever the player was when the inputs last
 * changed, and refreshed on demand via recompute().
 *
 * Threading: change notifications arrive on the EDT (selection/accessibility)
 * and the client thread (teleports), but compute() reads live client state
 * (player position), which RuneLite asserts is client-thread-only. recompute()
 * therefore only marks and schedules; the actual work runs on the client
 * thread via the injected executor. UI listeners are notified from there and
 * must hop to the EDT themselves (RunOrderSection does).
 */
@Slf4j
public class RunOrderService
{
	/** How many extra ticks a house chain may cost and still win a leg. */
	private static final int POH_PREFERENCE_TICKS = 20;

	private final FarmingData data;
	private final PatchSelectionService selectionService;
	private final PatchAccessibilityService accessibilityService;
	private final TeleportAvailabilityService teleportService;
	private final ClientLevelSource client;
	private final BetterFarmingConfig config;
	private final Consumer<Runnable> clientThreadExecutor;

	private final Set<Runnable> listeners = new LinkedHashSet<>();
	// volatile: written on the client thread, read from the EDT (run items).
	private volatile List<RoutePlanner.Leg> current = Collections.emptyList();
	private boolean recomputeQueued = false;

	private final Consumer<GroupActiveEvent> groupListener = e -> recompute();
	private final Consumer<PatchAccessibilityEvent> accessibilityListener = e -> recompute();
	private final Runnable teleportListener = this::recompute;

	/**
	 * @param clientThreadExecutor marshals work onto the client thread
	 *     (ClientThread::invokeLater in production, Runnable::run in tests).
	 */
	public RunOrderService(FarmingData data,
		PatchSelectionService selectionService,
		PatchAccessibilityService accessibilityService,
		TeleportAvailabilityService teleportService,
		ClientLevelSource client,
		BetterFarmingConfig config,
		Consumer<Runnable> clientThreadExecutor)
	{
		this.data = data;
		this.selectionService = selectionService;
		this.accessibilityService = accessibilityService;
		this.teleportService = teleportService;
		this.client = client;
		this.config = config;
		this.clientThreadExecutor = clientThreadExecutor;
		recompute();
	}

	public void wire()
	{
		selectionService.addGroupListener(groupListener);
		accessibilityService.addListener(accessibilityListener);
		teleportService.addListener(teleportListener);
	}

	public void unwire()
	{
		selectionService.removeGroupListener(groupListener);
		accessibilityService.removeListener(accessibilityListener);
		teleportService.removeListener(teleportListener);
	}

	public List<RoutePlanner.Leg> legs()
	{
		return current;
	}

	public void addListener(Runnable l)    { listeners.add(l); }
	public void removeListener(Runnable l) { listeners.remove(l); }

	/** Schedule a re-plan on the client thread; back-to-back calls coalesce. */
	public void recompute()
	{
		synchronized (this)
		{
			if (recomputeQueued)
			{
				return;
			}
			recomputeQueued = true;
		}
		clientThreadExecutor.accept(this::recomputeOnClientThread);
	}

	private void recomputeOnClientThread()
	{
		synchronized (this)
		{
			recomputeQueued = false;
		}
		List<RoutePlanner.Leg> next = compute();
		if (legsEqual(next, current))
		{
			return;
		}
		current = next;
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
				log.warn("Better Farming: run-order listener {} threw", l.getClass().getName(), ex);
			}
		}
	}

	private List<RoutePlanner.Leg> compute()
	{
		WorldPoint start = client.getPlayerPosition();
		if (start == null)
		{
			return Collections.emptyList();
		}
		List<RoutePlanner.Stop> stops = new ArrayList<>();
		for (PatchGroup g : PatchGroup.groupAll(data.patches()))
		{
			if (accessibilityService.effectiveActive(g.key(), selectionService))
			{
				// Group members share a location; the first patch's tile stands
				// in for the whole group.
				stops.add(new RoutePlanner.Stop(g.key(),
					g.location(), g.patches().get(0).worldPoint()));
			}
		}
		return RoutePlanner.plan(start, stops, teleportService.available(),
			config.preferPohTeleports() ? POH_PREFERENCE_TICKS : 0);
	}

	/**
	 * Leg equality for change detection ignores estimatedTicks — the estimate
	 * shifts by a tick or two as the player walks, which shouldn't repaint.
	 */
	private static boolean legsEqual(List<RoutePlanner.Leg> a, List<RoutePlanner.Leg> b)
	{
		if (a.size() != b.size())
		{
			return false;
		}
		for (int i = 0; i < a.size(); i++)
		{
			RoutePlanner.Leg x = a.get(i);
			RoutePlanner.Leg y = b.get(i);
			// Structural teleport equality: composed house-chain edges are
			// freshly allocated per teleport refresh; identity comparison
			// would repaint (and re-fan-out) on every refresh.
			if (!x.stop().groupKey().equals(y.stop().groupKey())
				|| !Objects.equals(x.teleport(), y.teleport()))
			{
				return false;
			}
		}
		return true;
	}
}
