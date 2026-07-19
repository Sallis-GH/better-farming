package com.betterfarming.guidance;

import com.betterfarming.BetterFarmingConfig;
import com.betterfarming.travel.RoutePlanner;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws the quest-helper-style bouncing arrow above the current leg's
 * destination tile. When the destination is outside the loaded scene (the
 * usual case right after planning a leg), an edge-clamped arrow near the
 * viewport border points in its compass direction instead, rotated with the
 * camera so "up" always matches what the player sees.
 */
public class WorldArrowOverlay extends Overlay
{
	static final Color ARROW_COLOR = new Color(0, 184, 255);

	/** Pixels the arrow floats above the destination tile. */
	private static final int TILE_Z_OFFSET = 120;

	/** Distance from the viewport border for the off-screen direction hint. */
	private static final int EDGE_MARGIN = 60;

	private final Client runeliteClient;
	private final BetterFarmingConfig config;
	private final GuidanceService guidance;

	public WorldArrowOverlay(Client client, BetterFarmingConfig config, GuidanceService guidance)
	{
		this.runeliteClient = client;
		this.config = config;
		this.guidance = guidance;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showWorldArrow())
		{
			return null;
		}
		RoutePlanner.Leg leg = guidance.currentLeg();
		if (leg == null)
		{
			return null;
		}
		WorldPoint target = leg.stop().point();

		LocalPoint lp = GuidancePerspective.resolveLocalPointForWorldPoint(runeliteClient, target);
		if (lp != null)
		{
			// Current scene plane, not the target's world plane: in an
			// instance the template copy can sit on a different plane, and
			// tile-height lookup runs in scene space (same convention as the
			// ported WorldLines.getWorldLines).
			Point canvas = Perspective.localToCanvas(runeliteClient, lp,
				runeliteClient.getPlane(), TILE_Z_OFFSET);
			if (canvas != null)
			{
				DirectionArrow.drawWorldArrow(graphics, ARROW_COLOR, canvas.getX(), canvas.getY());
				return null;
			}
		}

		drawEdgeClampedHint(graphics, target);
		return null;
	}

	/**
	 * Direction hint for a destination that cannot be projected onto the
	 * scene: rotate the world-space offset by the camera yaw (same projection
	 * the minimap uses), then push an arrow from the viewport centre to the
	 * border along that direction.
	 */
	private void drawEdgeClampedHint(Graphics2D graphics, WorldPoint target)
	{
		Player player = runeliteClient.getLocalPlayer();
		if (player == null)
		{
			return;
		}
		WorldPoint playerWp = GuidancePerspective.getWorldPointConsideringWorldView(
			runeliteClient, player.getLocalLocation());
		if (playerWp == null)
		{
			return;
		}

		int dx = target.getX() - playerWp.getX();
		int dy = target.getY() - playerWp.getY();
		if (dx == 0 && dy == 0)
		{
			return;
		}

		final int angle = runeliteClient.getCameraYawTarget() & 0x3FFF;
		final int sin = Perspective.SINE14[angle];
		final int cos = Perspective.COSINE14[angle];
		// Screen-space direction: x grows right, y grows down.
		double sx = (dy * (double) sin + cos * (double) dx) / 65536.0;
		double sy = ((double) sin * dx - dy * (double) cos) / 65536.0;
		double len = Math.hypot(sx, sy);
		if (len == 0)
		{
			return;
		}
		sx /= len;
		sy /= len;

		int vx = runeliteClient.getViewportXOffset();
		int vy = runeliteClient.getViewportYOffset();
		int vw = runeliteClient.getViewportWidth();
		int vh = runeliteClient.getViewportHeight();
		if (vw <= 2 * EDGE_MARGIN || vh <= 2 * EDGE_MARGIN)
		{
			return;
		}
		double cx = vx + vw / 2.0;
		double cy = vy + vh / 2.0;

		// Scale factor to the first border of the margin-inset viewport.
		double tX = sx != 0 ? (vw / 2.0 - EDGE_MARGIN) / Math.abs(sx) : Double.MAX_VALUE;
		double tY = sy != 0 ? (vh / 2.0 - EDGE_MARGIN) / Math.abs(sy) : Double.MAX_VALUE;
		double t = Math.min(tX, tY);

		double tipX = cx + sx * t;
		double tipY = cy + sy * t;
		Line2D.Double line = new Line2D.Double(tipX - sx * 22, tipY - sy * 22, tipX, tipY);
		DirectionArrow.drawArrow(graphics, line, ARROW_COLOR, 9, 4, 5);
	}
}
