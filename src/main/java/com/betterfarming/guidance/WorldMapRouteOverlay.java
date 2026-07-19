package com.betterfarming.guidance;

import com.betterfarming.BetterFarmingConfig;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Route preview on the world map: lines from the player through the remaining
 * stops in run order. The destination marker itself is a WorldMapPoint managed
 * by GuidanceWorldMapMarker; this overlay only draws the connecting lines.
 * All GuidancePerspective world-map helpers no-op while the map is closed.
 */
public class WorldMapRouteOverlay extends Overlay
{
	private final Client runeliteClient;
	private final BetterFarmingConfig config;
	private final GuidanceService guidance;

	public WorldMapRouteOverlay(Client client, BetterFarmingConfig config, GuidanceService guidance)
	{
		this.runeliteClient = client;
		this.config = config;
		this.guidance = guidance;
		// Same layer quest-helper draws its map lines from: the world map is a
		// widget, and the line helpers no-op while it is closed.
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showWorldMapRoute())
		{
			return null;
		}
		List<WorldPoint> remaining = guidance.remainingTargets();
		if (remaining.isEmpty())
		{
			return null;
		}
		Player player = runeliteClient.getLocalPlayer();
		if (player == null)
		{
			return null;
		}
		List<WorldPoint> points = new ArrayList<>(remaining.size() + 1);
		WorldPoint playerWp = GuidancePerspective.getWorldPointConsideringWorldView(
			runeliteClient, player.getLocalLocation());
		if (playerWp != null)
		{
			points.add(playerWp);
		}
		points.addAll(remaining);
		WorldLines.createWorldMapLines(graphics, runeliteClient, points, WorldArrowOverlay.ARROW_COLOR);
		return null;
	}
}
