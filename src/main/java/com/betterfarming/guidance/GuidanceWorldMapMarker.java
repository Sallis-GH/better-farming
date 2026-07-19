package com.betterfarming.guidance;

import com.betterfarming.BetterFarmingConfig;
import com.betterfarming.travel.RoutePlanner;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

/**
 * Owns the world-map destination marker for the current leg: a snap-to-edge,
 * jump-on-click WorldMapPoint that follows guidance state. update() is called
 * from the guidance listener fanout (client thread).
 */
public class GuidanceWorldMapMarker
{
	private final WorldMapPointManager manager;
	private final BetterFarmingConfig config;
	private final GuidanceService guidance;
	private final BufferedImage icon = buildIcon();

	private WorldMapPoint point;

	public GuidanceWorldMapMarker(WorldMapPointManager manager, BetterFarmingConfig config,
		GuidanceService guidance)
	{
		this.manager = manager;
		this.config = config;
		this.guidance = guidance;
	}

	public void update()
	{
		RoutePlanner.Leg leg = guidance.currentLeg();
		WorldPoint target = config.showWorldMapMarker() && leg != null ? leg.stop().point() : null;
		if (target == null)
		{
			remove();
			return;
		}
		if (point != null && target.equals(point.getWorldPoint()))
		{
			return;
		}
		remove();
		point = new WorldMapPoint(target, icon);
		point.setName("Better Farming: next patch");
		point.setSnapToEdge(true);
		point.setJumpOnClick(true);
		manager.add(point);
	}

	public void remove()
	{
		if (point != null)
		{
			manager.remove(point);
			point = null;
		}
	}

	/** Simple dot marker so no image resource is needed. */
	private static BufferedImage buildIcon()
	{
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(java.awt.Color.BLACK);
		g.fillOval(2, 2, 12, 12);
		g.setColor(WorldArrowOverlay.ARROW_COLOR);
		g.setStroke(new BasicStroke(1));
		g.fillOval(4, 4, 8, 8);
		g.dispose();
		return img;
	}
}
