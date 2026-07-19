package com.betterfarming.guidance;

import com.betterfarming.BetterFarmingConfig;
import com.betterfarming.travel.Teleport;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Outlines what to click for the current travel hop once the player is close:
 * the ferry NPC (matched by the id in the vendored "menuOption menuTarget id"
 * column — Bill Teach, Brother Tranquility) or, failing that, the boarding
 * object covering the hop's origin tile (a gangplank). Makes chain legs
 * followable without knowing the route: walk to the arrow, click the glow.
 */
public class TravelTargetOverlay extends Overlay
{
	private static final Color FILL = new Color(0, 184, 255, 40);

	/** Only highlight when the boarding point is about this close. */
	private static final int HIGHLIGHT_RADIUS_TILES = 40;

	private final Client runeliteClient;
	private final BetterFarmingConfig config;
	private final GuidanceService guidance;

	public TravelTargetOverlay(Client client, BetterFarmingConfig config, GuidanceService guidance)
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
		if (!config.showPlantingHighlights())
		{
			return null;
		}
		Teleport hop = guidance.travelHop();
		WorldPoint target = guidance.travelTarget();
		// Only boarding-style hops have a physical thing to click; casting an
		// item from anywhere is the item highlight's job.
		if (hop == null || hop.origin() == null || target == null)
		{
			return null;
		}
		Player player = runeliteClient.getLocalPlayer();
		if (player == null
			|| player.getWorldLocation().distanceTo2D(target) > HIGHLIGHT_RADIUS_TILES)
		{
			return null;
		}

		Shape shape = npcShape(hop, target);
		if (shape == null)
		{
			LocalPoint lp = GuidancePerspective.resolveLocalPointForWorldPoint(runeliteClient, target);
			if (lp == null)
			{
				return null;
			}
			GameObject object = GuidancePerspective.findObjectAt(runeliteClient, lp, target.getPlane());
			shape = object != null ? object.getClickbox() : null;
			if (shape == null)
			{
				shape = Perspective.getCanvasTilePoly(runeliteClient, lp);
			}
		}
		if (shape != null)
		{
			OverlayUtil.renderHoverableArea(graphics, shape,
				runeliteClient.getMouseCanvasPosition(), FILL,
				WorldArrowOverlay.ARROW_COLOR, WorldArrowOverlay.ARROW_COLOR.darker());
		}
		return null;
	}

	/** Convex hull of the hop's NPC (ferryman) when it is nearby. */
	private Shape npcShape(Teleport hop, WorldPoint target)
	{
		int npcId = trailingId(hop.objectInfo());
		if (npcId < 0)
		{
			return null;
		}
		for (NPC npc : runeliteClient.getTopLevelWorldView().npcs())
		{
			if (npc != null && npc.getId() == npcId
				&& npc.getWorldLocation().distanceTo2D(target) <= HIGHLIGHT_RADIUS_TILES)
			{
				return npc.getConvexHull();
			}
		}
		return null;
	}

	/** The trailing numeric token of "menuOption menuTarget id", or -1. */
	private static int trailingId(String objectInfo)
	{
		if (objectInfo == null || objectInfo.isBlank())
		{
			return -1;
		}
		String[] parts = objectInfo.trim().split(" ");
		try
		{
			return Integer.parseInt(parts[parts.length - 1]);
		}
		catch (NumberFormatException ex)
		{
			return -1;
		}
	}
}
