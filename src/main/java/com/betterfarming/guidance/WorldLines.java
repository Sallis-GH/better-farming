/*
 * Adapted from Zoinkwiz/quest-helper (steps/overlay/WorldLines.java), BSD-2-Clause.
 * See src/main/resources/LICENSE-quest-helper for the full license text.
 *
 * Copyright (c) 2021, Zoinkwiz <https://github.com/Zoinkwiz>
 * All rights reserved.
 */
package com.betterfarming.guidance;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Line2D;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Draws polyline paths on the world map and in the 3D world. Points equal to
 * WorldPoint(0,0,0) act as "don't render" separators between path segments.
 */
public class WorldLines
{
	public static void createWorldMapLines(Graphics2D graphics, Client client, List<WorldPoint> linePoints,
										   Color color)
	{
		Rectangle mapViewArea = GuidancePerspective.getWorldMapClipArea(client);

		for (int i = 0; i < linePoints.size() - 1; i++)
		{
			Point startPoint = GuidancePerspective.mapWorldPointToGraphicsPoint(client, linePoints.get(i));
			Point endPoint = GuidancePerspective.mapWorldPointToGraphicsPoint(client, linePoints.get(i + 1));

			WorldLines.renderWorldMapLine(graphics, client, mapViewArea, startPoint, endPoint,
				color);
		}
	}

	public static void renderWorldMapLine(Graphics2D graphics, Client client, Rectangle mapViewArea, Point startPoint,
									Point endPoint, Color color)
	{
		if (mapViewArea == null || startPoint == null || endPoint == null)
		{
			return;
		}
		if (!mapViewArea.contains(startPoint.getX(), startPoint.getY()) && !mapViewArea.contains(endPoint.getX(), endPoint.getY()))
		{
			return;
		}

		Line2D.Double line = new Line2D.Double(startPoint.getX(), startPoint.getY(), endPoint.getX(), endPoint.getY());
		DirectionArrow.drawLine(graphics, line, color, mapViewArea);
	}

	public static Line2D.Double getWorldLines(Client client, LocalPoint startLocation, LocalPoint endLocation)
	{
		final int plane = client.getPlane();

		final int startX = startLocation.getX();
		final int startY = startLocation.getY();
		final int endX = endLocation.getX();
		final int endY = endLocation.getY();

		final int sceneX = startLocation.getSceneX();
		final int sceneY = startLocation.getSceneY();

		if (sceneX < 0 || sceneY < 0 || sceneX >= Constants.SCENE_SIZE || sceneY >= Constants.SCENE_SIZE)
		{
			return null;
		}

		final int startHeight = Perspective.getTileHeight(client, startLocation, plane);
		final int endHeight = Perspective.getTileHeight(client, endLocation, plane);

		Point p1 = Perspective.localToCanvas(client, startX, startY, startHeight);
		Point p2 = Perspective.localToCanvas(client, endX, endY, endHeight);

		if (p1 == null || p2 == null)
		{
			return null;
		}

		return new Line2D.Double(p1.getX(), p1.getY(), p2.getX(), p2.getY());
	}

	public static void drawLinesOnWorld(Graphics2D graphics, Client client, List<WorldPoint> linePoints,
									   Color color)
	{
		for (int i = 0; i < linePoints.size() - 1; i++)
		{
			WorldPoint startWp = linePoints.get(i);
			WorldPoint endWp = linePoints.get(i + 1);

			if (startWp == null || endWp == null)
			{
				continue;
			}
			if (startWp.equals(new WorldPoint(0, 0, 0)))
			{
				continue;
			}
			if (endWp.equals(new WorldPoint(0, 0, 0)))
			{
				continue;
			}
			if (startWp.getPlane() != endWp.getPlane())
			{
				continue;
			}
			List<LocalPoint> startPoints = GuidancePerspective.getLocalPointsFromWorldPointInInstance(client.getTopLevelWorldView(), startWp);
			List<LocalPoint> destinationPoints = GuidancePerspective.getLocalPointsFromWorldPointInInstance(client.getTopLevelWorldView(), endWp);
			if (startPoints.isEmpty() || destinationPoints.isEmpty())
			{
				continue;
			}
			LocalPoint startPoint = startPoints.get(0);
			LocalPoint destinationPoint = destinationPoints.get(0);

			Line2D.Double newLine = getWorldLines(client, startPoint, destinationPoint);
			if (newLine != null)
			{
				OverlayUtil.renderPolygon(graphics, newLine, color);
			}
		}
	}
}
