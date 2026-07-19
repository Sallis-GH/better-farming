package com.betterfarming.guidance;

import com.betterfarming.BetterFarmingConfig;
import com.betterfarming.data.Patch;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Perspective;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Outlines the patch object the player should work on next (PlantingGuide's
 * target): the clickbox of the GameObject on or next to the patch tile, with
 * a tile-poly fallback when no object is resolvable. Farming patches are
 * multi-tile GameObjects whose ids vary per location, so the object is found
 * geometrically — the object covering the dataset tile — rather than by id.
 */
public class PatchHighlightOverlay extends Overlay
{
	private static final Color FILL = new Color(0, 184, 255, 40);

	private final Client runeliteClient;
	private final BetterFarmingConfig config;
	private final PlantingGuide guide;

	public PatchHighlightOverlay(Client client, BetterFarmingConfig config, PlantingGuide guide)
	{
		this.runeliteClient = client;
		this.config = config;
		this.guide = guide;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showPlantingHighlights())
		{
			return null;
		}
		Patch patch = guide.targetPatch();
		if (patch == null)
		{
			return null;
		}
		LocalPoint lp = GuidancePerspective.resolveLocalPointForWorldPoint(runeliteClient, patch.worldPoint());
		if (lp == null)
		{
			return null;
		}

		// Search the patch's own plane: the player may stand on a rooftop or
		// upper floor where unrelated objects cover the same scene coords.
		GameObject object = GuidancePerspective.findObjectAt(runeliteClient, lp, patch.worldPoint().getPlane());
		Shape clickbox = object != null ? object.getClickbox() : null;
		if (clickbox == null)
		{
			clickbox = Perspective.getCanvasTilePoly(runeliteClient, lp);
		}
		if (clickbox != null)
		{
			OverlayUtil.renderHoverableArea(graphics, clickbox,
				runeliteClient.getMouseCanvasPosition(), FILL,
				WorldArrowOverlay.ARROW_COLOR, WorldArrowOverlay.ARROW_COLOR.darker());
		}
		return null;
	}

}
