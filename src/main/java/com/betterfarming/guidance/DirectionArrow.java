/*
 * Adapted from Zoinkwiz/quest-helper (steps/overlay/DirectionArrow.java), BSD-2-Clause.
 * See src/main/resources/LICENSE-quest-helper for the full license text.
 *
 * Copyright (c) 2021, Zoinkwiz <https://github.com/Zoinkwiz>
 * All rights reserved.
 */
package com.betterfarming.guidance;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * Static arrow-drawing primitives for the world view and minimap. The minimap
 * arrow hovers above the destination when it is in draw range and becomes an
 * edge direction hint (a short arrow orbiting the player dot, pointing at the
 * goal) when it is not.
 */
public class DirectionArrow
{
	/**
	 * @param client the {@link Client}
	 * @return the rough number of tiles distance the minimap can draw
	 */
	public static int getMaxMinimapDrawDistance(Client client)
	{
		var minimapZoom = client.getMinimapZoom();
		if (minimapZoom > 0.0)
		{
			return (int) (64.0 / client.getMinimapZoom());
		}
		return 16;
	}

	public static void renderMinimapArrowFromLocal(Graphics2D graphics, Client client, LocalPoint localPoint, Color color)
	{
		var maxMinimapDrawDistance = getMaxMinimapDrawDistance(client);
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}

		if (localPoint == null)
		{
			return;
		}

		WorldPoint playerRealLocation = GuidancePerspective.getWorldPointConsideringWorldView(client, player.getLocalLocation());
		WorldPoint goalRealLocation = GuidancePerspective.getWorldPointConsideringWorldView(client, localPoint);
		if (playerRealLocation == null || goalRealLocation == null)
		{
			return;
		}

		if (goalRealLocation.distanceTo(playerRealLocation) >= maxMinimapDrawDistance)
		{
			createMinimapDirectionArrow(graphics, client, goalRealLocation, color);
			return;
		}

		Point posOnMinimap = Perspective.localToMinimap(client, localPoint);
		if (posOnMinimap == null)
		{
			return;
		}

		Line2D.Double line = new Line2D.Double(posOnMinimap.getX(), posOnMinimap.getY() - 18, posOnMinimap.getX(),
				posOnMinimap.getY() - 8);

		drawMinimapArrow(graphics, line, color);
	}

	public static void createMinimapDirectionArrow(Graphics2D graphics, Client client, WorldPoint wp, Color color)
	{
		Player player = client.getLocalPlayer();

		if (player == null)
		{
			return;
		}

		WorldPoint playerRealWp = GuidancePerspective.getWorldPointConsideringWorldView(client, player.getLocalLocation());

		if (wp == null || playerRealWp == null)
		{
			return;
		}

		Point playerPosOnMinimap = player.getMinimapLocation();

		Point destinationPosOnMinimap = GuidancePerspective.getMinimapPoint(client, playerRealWp, wp);

		if (playerPosOnMinimap == null || destinationPosOnMinimap == null)
		{
			return;
		}

		Line2D.Double line = getLine(playerPosOnMinimap, destinationPosOnMinimap);

		drawMinimapArrow(graphics, line, color);
	}

	private static Line2D.Double getLine(Point playerPosOnMinimap, Point destinationPosOnMinimap)
	{
		double xDiff = playerPosOnMinimap.getX() - destinationPosOnMinimap.getX();
		double yDiff = destinationPosOnMinimap.getY() - playerPosOnMinimap.getY();
		double angle = Math.atan2(yDiff, xDiff);

		int startX = (int) (playerPosOnMinimap.getX() - (Math.cos(angle) * 55));
		int startY = (int) (playerPosOnMinimap.getY() + (Math.sin(angle) * 55));

		int endX = (int) (playerPosOnMinimap.getX() - (Math.cos(angle) * 65));
		int endY = (int) (playerPosOnMinimap.getY() + (Math.sin(angle) * 65));

		return new Line2D.Double(startX, startY, endX, endY);
	}

	public static void drawWorldArrow(Graphics2D graphics, Color color, int startX, int startY)
	{
		Line2D.Double line = new Line2D.Double(startX, startY - 13, startX, startY);

		int headWidth = 5;
		int headHeight = 4;
		int lineWidth = 9;

		drawArrow(graphics, line, color, lineWidth, headHeight, headWidth);
	}

	public static void drawMinimapArrow(Graphics2D graphics, Line2D.Double line, Color color)
	{
		drawArrow(graphics, line, color, 6, 2, 2);
	}

	public static void drawArrow(Graphics2D graphics, Line2D.Double line, Color color, int width, int tipHeight,
								 int tipWidth)
	{
		graphics.setColor(Color.BLACK);
		graphics.setStroke(new BasicStroke(width));
		graphics.draw(line);
		drawWorldArrowHead(graphics, line, tipHeight, tipWidth);

		graphics.setColor(color);
		graphics.setStroke(new BasicStroke(width - 3));
		graphics.draw(line);
		drawWorldArrowHead(graphics, line, tipHeight - 2, tipWidth - 2);
		graphics.setStroke(new BasicStroke(1));
	}

	public static void drawWorldArrowHead(Graphics2D g2d, Line2D.Double line, int extraSizeHeight, int extraSizeWidth)
	{
		AffineTransform tx = new AffineTransform();

		Polygon arrowHead = new Polygon();
		arrowHead.addPoint(0, 6 + extraSizeHeight);
		arrowHead.addPoint(-6 - extraSizeWidth, -1 - extraSizeHeight);
		arrowHead.addPoint(6 + extraSizeWidth, -1 - extraSizeHeight);

		tx.setToIdentity();
		double angle = Math.atan2(line.y2 - line.y1, line.x2 - line.x1);
		tx.translate(line.x2, line.y2);
		tx.rotate((angle - Math.PI / 2d));

		Graphics2D g = (Graphics2D) g2d.create();
		g.setTransform(tx);
		g.fill(arrowHead);
		g.dispose();
	}

	public static void drawLineArrowHead(Graphics2D g2d, Line2D.Double line)
	{
		AffineTransform tx = new AffineTransform();

		Polygon arrowHead = new Polygon();
		arrowHead.addPoint(0, 0);
		arrowHead.addPoint(-3, -6);
		arrowHead.addPoint(3, -6);

		tx.setToIdentity();
		double angle = Math.atan2(line.y2 - line.y1, line.x2 - line.x1);
		tx.translate(line.x2, line.y2);
		tx.rotate((angle - Math.PI / 2d));

		Graphics2D g = (Graphics2D) g2d.create();
		g.setTransform(tx);
		g.fill(arrowHead);
		g.dispose();
	}

	public static void drawLine(Graphics2D graphics, Line2D.Double line, Color color, Rectangle clippingRegion)
	{
		graphics.setStroke(new BasicStroke(1));
		graphics.setClip(clippingRegion);
		graphics.setColor(color);
		graphics.draw(line);

		drawLineArrowHead(graphics, line);
	}
}
