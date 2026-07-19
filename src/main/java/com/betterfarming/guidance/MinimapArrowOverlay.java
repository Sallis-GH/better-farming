package com.betterfarming.guidance;

import com.betterfarming.BetterFarmingConfig;
import com.betterfarming.travel.RoutePlanner;
import java.awt.Dimension;
import java.awt.Graphics2D;
import net.runelite.api.Client;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Minimap arrow for the current leg: hovers above the destination when it is
 * within minimap draw range, otherwise orbits the player dot pointing toward
 * it (DirectionArrow handles both cases).
 */
public class MinimapArrowOverlay extends Overlay
{
	private final Client runeliteClient;
	private final BetterFarmingConfig config;
	private final GuidanceService guidance;

	public MinimapArrowOverlay(Client client, BetterFarmingConfig config, GuidanceService guidance)
	{
		this.runeliteClient = client;
		this.config = config;
		this.guidance = guidance;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showMinimapArrow())
		{
			return null;
		}
		RoutePlanner.Leg leg = guidance.currentLeg();
		if (leg == null)
		{
			return null;
		}
		// Chain-aware: point at the current waypoint, not the final stop.
		WorldPoint target = guidance.travelTarget();
		if (target == null)
		{
			target = leg.stop().point();
		}

		LocalPoint lp = GuidancePerspective.resolveLocalPointForWorldPoint(runeliteClient, target);
		if (lp != null)
		{
			DirectionArrow.renderMinimapArrowFromLocal(graphics, runeliteClient, lp, WorldArrowOverlay.ARROW_COLOR);
		}
		else
		{
			// Out of scene: direction-only arrow around the player dot.
			DirectionArrow.createMinimapDirectionArrow(graphics, runeliteClient, target, WorldArrowOverlay.ARROW_COLOR);
		}
		return null;
	}
}
