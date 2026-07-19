package com.betterfarming.guidance;

import com.betterfarming.BetterFarmingConfig;
import com.betterfarming.travel.RoutePlanner;
import com.betterfarming.ui.ClientLevelSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;

/**
 * Drives the Shortest Path plugin's tile path via its PluginMessage API
 * (namespace "shortestpath", actions "path"/"clear") so the exact route to
 * the current leg is drawn when that plugin is installed. Posting is harmless
 * when it is not: nothing listens.
 *
 * update() runs from the guidance listener fanout (client thread) and only
 * re-posts when the destination actually changed; Shortest Path itself
 * recalculates as the player walks.
 */
public class ShortestPathBridge
{
	private final EventBus eventBus;
	private final BetterFarmingConfig config;
	private final GuidanceService guidance;
	private final ClientLevelSource client;

	private WorldPoint lastTarget;

	public ShortestPathBridge(EventBus eventBus, BetterFarmingConfig config,
		GuidanceService guidance, ClientLevelSource client)
	{
		this.eventBus = eventBus;
		this.config = config;
		this.guidance = guidance;
		this.client = client;
	}

	public void update()
	{
		RoutePlanner.Leg leg = guidance.currentLeg();
		WorldPoint target = config.useShortestPath() && leg != null ? leg.stop().point() : null;
		if (Objects.equals(target, lastTarget))
		{
			return;
		}
		lastTarget = target;
		if (target == null)
		{
			clear();
			return;
		}
		WorldPoint start = client.getPlayerPosition();
		if (start == null)
		{
			lastTarget = null;
			return;
		}
		Map<String, Object> data = new HashMap<>();
		data.put("start", start);
		data.put("target", target);
		eventBus.post(new PluginMessage("shortestpath", "path", data));
	}

	public void clear()
	{
		lastTarget = null;
		eventBus.post(new PluginMessage("shortestpath", "clear"));
	}
}
