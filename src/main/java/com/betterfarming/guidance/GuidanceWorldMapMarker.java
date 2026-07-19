package com.betterfarming.guidance;

import com.betterfarming.BetterFarmingConfig;
import com.betterfarming.travel.RoutePlanner;
import java.awt.image.BufferedImage;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

/**
 * Owns the world-map destination marker for the current leg: a watering-can
 * icon (item sprite via ItemManager, ~36x32 px so it reads at a glance) that
 * snaps to the map edge when off-view and jumps the map to the patch when
 * clicked. update() is called from the guidance listener fanout (client
 * thread).
 */
public class GuidanceWorldMapMarker
{
	private final WorldMapPointManager manager;
	private final BetterFarmingConfig config;
	private final GuidanceService guidance;
	private final ItemManager itemManager;

	private WorldMapPoint point;
	private BufferedImage icon;

	public GuidanceWorldMapMarker(WorldMapPointManager manager, BetterFarmingConfig config,
		GuidanceService guidance, ItemManager itemManager)
	{
		this.manager = manager;
		this.config = config;
		this.guidance = guidance;
		this.itemManager = itemManager;
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
		if (icon == null)
		{
			// AsyncBufferedImage: may paint a frame or two late while the
			// sprite loads; the WorldMapPoint keeps the reference and fills in.
			icon = itemManager.getImage(ItemID.WATERING_CAN_8);
		}
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
}
