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
 * recalculates as the player walks. "clear" is only ever posted when this
 * bridge previously posted a path — a path the user set themselves in
 * Shortest Path must never be erased by us.
 */
public class ShortestPathBridge
{
	private final EventBus eventBus;
	private final BetterFarmingConfig config;
	private final GuidanceService guidance;
	private final ClientLevelSource client;

	/** Target of the path we last posted; null = nothing of ours outstanding. */
	private WorldPoint lastPosted;

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
		WorldPoint start = client.getPlayerPosition();
		if (start == null)
		{
			// No origin to path from; retract our path (if any) and re-post
			// once the player is back.
			target = null;
		}
		if (Objects.equals(target, lastPosted))
		{
			return;
		}
		if (target == null)
		{
			clear();
			return;
		}
		Map<String, Object> data = new HashMap<>();
		data.put("start", start);
		data.put("target", target);
		eventBus.post(new PluginMessage("shortestpath", "path", data));
		lastPosted = target;
	}

	/** Retracts our posted path; no-op when we never posted one. */
	public void clear()
	{
		if (lastPosted == null)
		{
			return;
		}
		lastPosted = null;
		eventBus.post(new PluginMessage("shortestpath", "clear"));
	}
}
