package com.betterfarming.travel;

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
 * changed, and refreshed on demand via recompute(). Listener fanout happens
 * on whatever thread triggered the recompute (EDT for selection changes,
 * client thread for teleport availability); RunOrderSection hops to the EDT.
 */
@Slf4j
public class RunOrderService
{
	private final FarmingData data;
	private final PatchSelectionService selectionService;
	private final PatchAccessibilityService accessibilityService;
	private final TeleportAvailabilityService teleportService;
	private final ClientLevelSource client;

	private final Set<Runnable> listeners = new LinkedHashSet<>();
	private List<RoutePlanner.Leg> current = Collections.emptyList();

	private final Consumer<GroupActiveEvent> groupListener = e -> recompute();
	private final Consumer<PatchAccessibilityEvent> accessibilityListener = e -> recompute();
	private final Runnable teleportListener = this::recompute;

	public RunOrderService(FarmingData data,
		PatchSelectionService selectionService,
		PatchAccessibilityService accessibilityService,
		TeleportAvailabilityService teleportService,
		ClientLevelSource client)
	{
		this.data = data;
		this.selectionService = selectionService;
		this.accessibilityService = accessibilityService;
		this.teleportService = teleportService;
		this.client = client;
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

	public void recompute()
	{
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
			catch (RuntimeException ex)
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
		return RoutePlanner.plan(start, stops, teleportService.available());
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
			if (!x.stop().groupKey().equals(y.stop().groupKey())
				|| x.teleport() != y.teleport())
			{
				return false;
			}
		}
		return true;
	}
}
