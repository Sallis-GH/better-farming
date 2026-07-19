/*
 * Adapted from Zoinkwiz/quest-helper (steps/tools/QuestPerspective.java), BSD-2-Clause.
 * See src/main/resources/LICENSE-quest-helper for the full license text.
 *
 * Copyright (c) 2021, Zoinkwiz <https://github.com/Zoinkwiz>
 * All rights reserved.
 */
package com.betterfarming.guidance;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;

/**
 * Coordinate conversions the guidance overlays need: world↔local across
 * instances/world-views, world-map pixel mapping, and minimap projection.
 * Subset of quest-helper's QuestPerspective; Zone/scenery helpers dropped.
 *
 * All methods read client state and must run on the client thread (overlay
 * render() and GameTick both do).
 */
public class GuidancePerspective
{
	/**
	 * Converts a LocalPoint to a WorldPoint, handling WorldView considerations
	 * for instanced areas like boats.
	 */
	public static WorldPoint getWorldPointConsideringWorldView(Client client, LocalPoint localPoint)
	{
		if (localPoint == null)
		{
			return null;
		}

		WorldView worldView = client.getWorldView(localPoint.getWorldView());

		// If in a non-top level WorldView (a boat) need to translate
		if (worldView != null && !worldView.isTopLevel())
		{
			var worldEntity = client.getTopLevelWorldView()
				.worldEntities()
				.byIndex(worldView.getId());

			if (worldEntity == null)
			{
				return null;
			}

			var mainLocal = worldEntity.transformToMainWorld(localPoint);
			return WorldPoint.fromLocalInstance(client, mainLocal, client.getTopLevelWorldView().getPlane());
		}
		else
		{
			return WorldPoint.fromLocalInstance(client, localPoint);
		}
	}

	private static LocalPoint getLocalPointFromWorldPointInInstance(WorldView wv, WorldPoint worldPoint)
	{
		if (worldPoint == null)
		{
			return null;
		}
		var instanceWps = WorldPoint.toLocalInstance(wv, worldPoint);
		if (instanceWps.isEmpty())
		{
			return null;
		}
		return LocalPoint.fromWorld(wv, instanceWps.iterator().next());
	}

	public static List<LocalPoint> getLocalPointsFromWorldPointInInstance(WorldView wv, WorldPoint worldPoint)
	{
		if (worldPoint == null)
		{
			return List.of();
		}
		var instanceWps = WorldPoint.toLocalInstance(wv, worldPoint);
		if (instanceWps.isEmpty())
		{
			return List.of();
		}

		List<LocalPoint> lps = new ArrayList<>();
		for (WorldPoint instanceWp : instanceWps)
		{
			var lp = LocalPoint.fromWorld(wv, instanceWp);
			if (lp != null)
			{
				lps.add(lp);
			}
		}
		return lps;
	}

	/**
	 * Resolves the LocalPoint to use for drawing a WorldPoint, checking the
	 * player's active view and finally the top-level view.
	 */
	public static LocalPoint resolveLocalPointForWorldPoint(Client client, WorldPoint worldPoint)
	{
		if (client == null || worldPoint == null)
		{
			return null;
		}

		var viewsToCheck = new LinkedHashSet<WorldView>();
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer != null && localPlayer.getWorldView() != null)
		{
			viewsToCheck.add(localPlayer.getWorldView());
		}

		var topLevel = client.getTopLevelWorldView();
		if (topLevel != null)
		{
			viewsToCheck.add(topLevel);
		}

		for (WorldView view : viewsToCheck)
		{
			LocalPoint localPoint = getLocalPointFromWorldPointInInstance(view, worldPoint);
			if (localPoint != null)
			{
				return localPoint;
			}
		}

		return null;
	}

	/**
	 * The GameObject covering the scene tile at lp on the given plane,
	 * searching the tile and its neighbours (multi-tile objects may anchor a
	 * tile away from the dataset point). Null when nothing covers it.
	 */
	public static net.runelite.api.GameObject findObjectAt(Client client, LocalPoint lp, int plane)
	{
		net.runelite.api.Tile[][][] tiles = client.getScene().getTiles();
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
				net.runelite.api.Tile tile = tiles[plane][x][y];
				if (tile == null)
				{
					continue;
				}
				for (net.runelite.api.GameObject go : tile.getGameObjects())
				{
					if (go == null)
					{
						continue;
					}
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

	public static Rectangle getWorldMapClipArea(Client client)
	{
		Widget widget = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
		if (widget == null)
		{
			return null;
		}

		return widget.getBounds();
	}

	public static Point mapWorldPointToGraphicsPoint(Client client, WorldPoint worldPoint)
	{
		var worldMap = client.getWorldMap();
		if (worldPoint == null || worldMap == null || worldMap.getWorldMapData() == null)
		{
			return null;
		}
		if (!worldMap.getWorldMapData().surfaceContainsPosition(worldPoint.getX(), worldPoint.getY()))
		{
			return null;
		}

		float pixelsPerTile = worldMap.getWorldMapZoom();

		Widget map = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
		if (map != null)
		{
			Rectangle worldMapRect = map.getBounds();

			int widthInTiles = (int) Math.ceil(worldMapRect.getWidth() / pixelsPerTile);
			int heightInTiles = (int) Math.ceil(worldMapRect.getHeight() / pixelsPerTile);

			var worldMapPosition = worldMap.getWorldMapPosition();

			// Offset in tiles from anchor sides
			int yTileMax = worldMapPosition.getY() - heightInTiles / 2;
			int yTileOffset = (yTileMax - worldPoint.getY() - 1) * -1;
			int xTileOffset = worldPoint.getX() + widthInTiles / 2 - worldMapPosition.getX();

			int xGraphDiff = ((int) (xTileOffset * pixelsPerTile));
			int yGraphDiff = (int) (yTileOffset * pixelsPerTile);

			// Center on tile.
			yGraphDiff -= pixelsPerTile - Math.ceil(pixelsPerTile / 2);
			xGraphDiff += pixelsPerTile - Math.ceil(pixelsPerTile / 2);

			yGraphDiff = worldMapRect.height - yGraphDiff;
			yGraphDiff += (int) worldMapRect.getY();
			xGraphDiff += (int) worldMapRect.getX();

			return new Point(xGraphDiff, yGraphDiff);
		}
		return null;
	}

	public static Point getMinimapPoint(Client client, WorldPoint start, WorldPoint destination)
	{
		var worldMap = client.getWorldMap();
		if (worldMap == null)
		{
			return null;
		}

		var worldMapData = worldMap.getWorldMapData();
		if (worldMapData == null)
		{
			return null;
		}

		if (worldMapData.surfaceContainsPosition(start.getX(), start.getY()) !=
			worldMapData.surfaceContainsPosition(destination.getX(), destination.getY()))
		{
			return null;
		}

		int x = (destination.getX() - start.getX());
		int y = (destination.getY() - start.getY());

		float maxDistance = Math.max(Math.abs(x), Math.abs(y));
		// Avoid division by zero when start == destination
		if (maxDistance == 0)
		{
			return null;
		}

		x = x * 100;
		y = y * 100;
		x /= maxDistance;
		y /= maxDistance;

		Widget minimapDrawWidget;
		if (client.isResized())
		{
			if (client.getVarbitValue(VarbitID.RESIZABLE_STONE_ARRANGEMENT) == 1)
			{
				minimapDrawWidget = client.getWidget(InterfaceID.ToplevelPreEoc.MINIMAP);
			}
			else
			{
				minimapDrawWidget = client.getWidget(InterfaceID.ToplevelOsrsStretch.MINIMAP);
			}
		}
		else
		{
			minimapDrawWidget = client.getWidget(InterfaceID.Toplevel.MINIMAP);
		}

		if (minimapDrawWidget == null)
		{
			return null;
		}

		final int angle = client.getCameraYawTarget() & 0x3FFF;

		final int sin = Perspective.SINE14[angle];
		final int cos = Perspective.COSINE14[angle];

		final int xx = y * sin + cos * x >> 16;
		final int yy = sin * x - y * cos >> 16;

		Point loc = minimapDrawWidget.getCanvasLocation();
		int miniMapX = loc.getX() + xx + minimapDrawWidget.getWidth() / 2;
		int miniMapY = minimapDrawWidget.getHeight() / 2 + loc.getY() + yy;
		return new Point(miniMapX, miniMapY);
	}
}
