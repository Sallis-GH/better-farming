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
		GameObject object = findObjectAt(lp, patch.worldPoint().getPlane());
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

	/**
	 * The GameObject covering the patch tile, searching the tile itself and
	 * its neighbours (patch objects span several tiles and the dataset point
	 * may sit on any of them).
	 */
	private GameObject findObjectAt(LocalPoint lp, int plane)
	{
		Scene scene = runeliteClient.getScene();
		Tile[][][] tiles = scene.getTiles();
		int sx = lp.getSceneX();
		int sy = lp.getSceneY();
		for (int dx = -1; dx <= 1; dx++)
		{
			for (int dy = -1; dy <= 1; dy++)
			{
				int x = sx + dx;
				int y = sy + dy;
				if (x < 0 || y < 0 || x >= tiles[plane].length || y >= tiles[plane][x].length)
				{
					continue;
				}
				Tile tile = tiles[plane][x][y];
				if (tile == null)
				{
					continue;
				}
				for (GameObject go : tile.getGameObjects())
				{
					if (go == null)
					{
						continue;
					}
					// The patch object is the one whose footprint covers the
					// dataset tile; decorations on neighbouring tiles don't.
					if (go.getSceneMinLocation().getX() <= sx && sx <= go.getSceneMaxLocation().getX()
						&& go.getSceneMinLocation().getY() <= sy && sy <= go.getSceneMaxLocation().getY())
					{
						return go;
					}
				}
			}
		}
		return null;
	}
}
