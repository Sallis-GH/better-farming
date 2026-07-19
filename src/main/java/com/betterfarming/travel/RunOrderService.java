package com.betterfarming.travel;

import com.betterfarming.BetterFarmingConfig;
import com.betterfarming.data.FarmingData;
import com.betterfarming.data.PatchGroup;
import com.betterfarming.state.GroupActiveEvent;
import com.betterfarming.state.PatchSelectionService;
import com.betterfarming.ui.ClientLevelSource;
import com.betterfarming.ui.PatchAccessibilityEvent;
import com.betterfarming.ui.PatchAccessibilityService;
import com.betterfarming.data.Patch;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
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
	private final Predicate<Patch> needsVisit;
	private final ToIntFunction<Teleport> slotCost;

	private final Set<Runnable> listeners = new LinkedHashSet<>();
	// volatile: written on the client thread, read from the EDT (run items).
	private volatile List<RoutePlanner.Leg> current = Collections.emptyList();
	private boolean recomputeQueued = false;

	/**
	 * Group keys in planned order, pinned for the duration of a run: once a
	 * route is planned, recomputes that merely shrink the stop set (a stop
	 * completed, a patch turned out to be growing) keep the remaining stops
	 * in their planned sequence instead of re-solving the TSP from wherever
	 * the player happens to stand — a mid-run reshuffle makes "next stop"
	 * impossible to follow. A grown stop set (selection change), replan(),
	 * or logout re-plans from scratch. Client thread only.
	 */
	private List<String> pinnedOrder;

	/**
	 * All groups that were active when the pin was taken — including the ones
	 * the crop-state filter excluded from pinnedOrder. The pin stays valid as
	 * long as no NEW group activates; comparing against pinnedOrder alone
	 * would break the pin whenever any active group was skipped as growing.
	 */
	private Set<String> pinnedActiveKeys;

	/** Set under `this` lock; consumed by the next client-thread compute. */
	private boolean replanRequested = false;

	private final Consumer<GroupActiveEvent> groupListener = e -> recompute();
	private final Consumer<PatchAccessibilityEvent> accessibilityListener = e -> recompute();
	private final Runnable teleportListener = this::recompute;

	/** Convenience: no crop-state filtering — every active group is routed. */
	public RunOrderService(FarmingData data,
		PatchSelectionService selectionService,
		PatchAccessibilityService accessibilityService,
		TeleportAvailabilityService teleportService,
		ClientLevelSource client,
		BetterFarmingConfig config,
		Consumer<Runnable> clientThreadExecutor)
	{
		this(data, selectionService, accessibilityService, teleportService, client, config,
			clientThreadExecutor, p -> true, RoutePlanner.DEFAULT_SLOT_COST);
	}

	/**
	 * @param clientThreadExecutor marshals work onto the client thread
	 *     (ClientThread::invokeLater in production, Runnable::run in tests).
	 * @param needsVisit filters patches worth routing to (crop-state based in
	 *     production: skip patches confirmed still growing; p -&gt; true keeps
	 *     everything).
	 */
	public RunOrderService(FarmingData data,
		PatchSelectionService selectionService,
		PatchAccessibilityService accessibilityService,
		TeleportAvailabilityService teleportService,
		ClientLevelSource client,
		BetterFarmingConfig config,
		Consumer<Runnable> clientThreadExecutor,
		Predicate<Patch> needsVisit,
		ToIntFunction<Teleport> slotCost)
	{
		this.data = data;
		this.selectionService = selectionService;
		this.accessibilityService = accessibilityService;
		this.teleportService = teleportService;
		this.client = client;
		this.config = config;
		this.clientThreadExecutor = clientThreadExecutor;
		this.needsVisit = needsVisit;
		this.slotCost = slotCost;
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

	/**
	 * Drops the pinned order and re-plans from the player's position. The
	 * request is a flag consumed by the next compute rather than a separately
	 * queued task: a task could be overtaken by an already-queued recompute
	 * (whose own recompute() call the coalescing would then swallow), leaving
	 * the pin cleared but nothing re-planned.
	 */
	public void replan()
	{
		synchronized (this)
		{
			replanRequested = true;
		}
		recompute();
	}

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
		boolean dropPin;
		synchronized (this)
		{
			recomputeQueued = false;
			dropPin = replanRequested;
			replanRequested = false;
		}
		if (dropPin)
		{
			pinnedOrder = null;
			pinnedActiveKeys = null;
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
		List<RoutePlanner.Stop> activeStops = new ArrayList<>();
		List<RoutePlanner.Stop> worthVisiting = new ArrayList<>();
		for (PatchGroup g : PatchGroup.groupAll(data.patches()))
		{
			if (!accessibilityService.effectiveActive(g.key(), selectionService))
			{
				continue;
			}
			// Group members share a location; the first patch's tile stands
			// in for the whole group.
			RoutePlanner.Stop stop = new RoutePlanner.Stop(g.key(),
				g.location(), g.patches().get(0).worldPoint());
			activeStops.add(stop);
			if (g.patches().stream().anyMatch(needsVisit))
			{
				worthVisiting.add(stop);
			}
		}
		List<Teleport> teleports = teleportService.available();
		int bias = config.preferPohTeleports() ? POH_PREFERENCE_TICKS : 0;

		Set<String> activeKeys = activeStops.stream().map(RoutePlanner.Stop::groupKey)
			.collect(Collectors.toSet());
		if (pinnedOrder != null && pinnedActiveKeys.containsAll(activeKeys))
		{
			// Mid-run: keep the planned sequence, and keep stops that became
			// complete — guidance checks them off and the run counter stays
			// honest. The crop-state filter applies at plan time only.
			Map<String, RoutePlanner.Stop> byKey = activeStops.stream()
				.collect(Collectors.toMap(RoutePlanner.Stop::groupKey, s -> s));
			List<RoutePlanner.Stop> ordered = new ArrayList<>();
			for (String key : pinnedOrder)
			{
				RoutePlanner.Stop s = byKey.get(key);
				if (s != null)
				{
					ordered.add(s);
				}
			}
			return RoutePlanner.planFixedOrder(start, ordered, teleports, bias, slotCost);
		}

		// New run plan: route only patches that are empty, harvestable, or of
		// unknown state — confirmed-growing patches are not worth a stop.
		List<RoutePlanner.Leg> legs = RoutePlanner.plan(start, worthVisiting, teleports, bias, slotCost);
		pinnedOrder = new ArrayList<>();
		for (RoutePlanner.Leg leg : legs)
		{
			pinnedOrder.add(leg.stop().groupKey());
		}
		pinnedActiveKeys = activeKeys;
		return legs;
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
