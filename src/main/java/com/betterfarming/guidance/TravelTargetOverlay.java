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
 * Outlines what to click for the current travel hop once the player is close.
 * Boarding is staged: while approaching, the boarding object at the hop's
 * origin (a gangplank) leads; the ferry NPC (matched by the name/id in the
 * vendored "menuOption menuTarget id" column) only glows once the player is
 * essentially at the boarding tile — ferrymen like Bill Teach exist in
 * several places at once, and matching the nearest namesake from 40 tiles
 * out highlights the wrong one before the gangplank is even crossed. Makes
 * chain legs followable without knowing the route: walk to the arrow, click
 * the glow.
 */
public class TravelTargetOverlay extends Overlay
{
	private static final Color FILL = new Color(0, 184, 255, 40);

	/** Only highlight when the boarding point is about this close. */
	private static final int HIGHLIGHT_RADIUS_TILES = 40;

	/**
	 * The NPC stage begins this close to the boarding tile (2D — the tile's
	 * recorded plane is unreliable: ship decks are plane 1 while the vendored
	 * boarding tiles say 0, which once kept Bill Teach from ever glowing).
	 * Within this radius the nearest same-plane namesake is the right one.
	 */
	static final int NPC_STAGE_RADIUS_TILES = 15;

	/**
	 * How far from the boarding tile the ferryman may stand and still match:
	 * he roams the deck, so this is looser than the player threshold but far
	 * inside the old 40-tile search that caught dockside namesakes.
	 */
	static final int NPC_MATCH_RADIUS_TILES = 20;

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

		// Gangplank first, ferryman only once reachable: the object highlight
		// leads while approaching; the NPC takes over when close AND on the
		// player's plane — a deck NPC one level up stays dark until the
		// gangplank has actually been crossed.
		Shape shape = null;
		if (npcStage(player.getWorldLocation(), target))
		{
			shape = npcShape(hop, target, player.getWorldLocation().getPlane());
		}
		if (shape == null)
		{
			shape = boardingObjectShape(target);
		}
		if (shape == null)
		{
			LocalPoint lp = GuidancePerspective.resolveLocalPointForWorldPoint(runeliteClient, target);
			shape = lp != null ? Perspective.getCanvasTilePoly(runeliteClient, lp) : null;
		}
		if (shape != null)
		{
			OverlayUtil.renderHoverableArea(graphics, shape,
				runeliteClient.getMouseCanvasPosition(), FILL,
				WorldArrowOverlay.ARROW_COLOR, WorldArrowOverlay.ARROW_COLOR.darker());
		}
		return null;
	}

	/**
	 * True when the player is close enough to the boarding point for the
	 * ferryman highlight to take over from the gangplank. Distance only —
	 * plane discrimination happens against the NPC's LIVE plane in
	 * {@link #npcShape}, because the recorded boarding tile's plane is
	 * untrustworthy (decks are plane 1, the data says 0).
	 */
	static boolean npcStage(WorldPoint player, WorldPoint target)
	{
		return player.distanceTo2D(target) <= NPC_STAGE_RADIUS_TILES;
	}

	/**
	 * Convex hull of the hop's NPC (ferryman) when it is at the boarding
	 * point on the player's plane — a deck NPC one level up stays dark until
	 * the player has crossed the gangplank onto that deck. Matched by the
	 * NAME in the vendored "menuOption menuTarget id" column — NPCs like
	 * Bill Teach exist under several ids and the data pins only one, so the
	 * id is a fallback, not the primary key. The search is bounded to the
	 * NPC match radius: a namesake standing anywhere else must never light
	 * up.
	 */
	private Shape npcShape(Teleport hop, WorldPoint target, int playerPlane)
	{
		String name = menuTargetName(hop.objectInfo());
		int npcId = trailingId(hop.objectInfo());
		if (name == null && npcId < 0)
		{
			return null;
		}
		for (NPC npc : runeliteClient.getTopLevelWorldView().npcs())
		{
			if (npc == null
				|| npc.getWorldLocation().getPlane() != playerPlane
				|| npc.getWorldLocation().distanceTo2D(target) > NPC_MATCH_RADIUS_TILES)
			{
				continue;
			}
			if ((name != null && name.equalsIgnoreCase(npc.getName()))
				|| (npcId >= 0 && npc.getId() == npcId))
			{
				return npc.getConvexHull();
			}
		}
		return null;
	}

	/** Menu options marking a click-to-travel object (gangplank, ladder). */
	private static final java.util.Set<String> TRAVEL_ACTIONS = java.util.Set.of(
		"Cross", "Board", "Travel", "Enter", "Climb", "Climb-up", "Climb-down");

	private static final int OBJECT_SCAN_RADIUS = 5;

	// Object resolution is a tile scan with composition lookups; cache per
	// waypoint and re-resolve when the clickbox goes stale (scene reload).
	private WorldPoint cachedObjectFor;
	private GameObject cachedObject;

	/**
	 * Clickbox of the nearest travel object (a gangplank's "Cross", a
	 * ladder's "Climb") around the boarding tile. The dataset tile is where
	 * the player stands to board, so the clickable object is usually on a
	 * neighbouring tile rather than covering it.
	 */
	private Shape boardingObjectShape(WorldPoint target)
	{
		if (!target.equals(cachedObjectFor) || cachedObject == null
			|| cachedObject.getClickbox() == null)
		{
			cachedObjectFor = target;
			cachedObject = findBoardingObject(target);
		}
		return cachedObject != null ? cachedObject.getClickbox() : null;
	}

	private GameObject findBoardingObject(WorldPoint target)
	{
		LocalPoint lp = GuidancePerspective.resolveLocalPointForWorldPoint(runeliteClient, target);
		if (lp == null)
		{
			return null;
		}
		net.runelite.api.Tile[][][] tiles = runeliteClient.getScene().getTiles();
		int plane = target.getPlane();
		GameObject best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (int dx = -OBJECT_SCAN_RADIUS; dx <= OBJECT_SCAN_RADIUS; dx++)
		{
			for (int dy = -OBJECT_SCAN_RADIUS; dy <= OBJECT_SCAN_RADIUS; dy++)
			{
				int x = lp.getSceneX() + dx;
				int y = lp.getSceneY() + dy;
				if (x < 0 || y < 0 || x >= tiles[plane].length || y >= tiles[plane][x].length
					|| tiles[plane][x][y] == null)
				{
					continue;
				}
				for (GameObject go : tiles[plane][x][y].getGameObjects())
				{
					if (go == null)
					{
						continue;
					}
					int distance = Math.max(Math.abs(dx), Math.abs(dy));
					if (distance >= bestDistance || !hasTravelAction(go.getId()))
					{
						continue;
					}
					best = go;
					bestDistance = distance;
				}
			}
		}
		return best;
	}

	private boolean hasTravelAction(int objectId)
	{
		net.runelite.api.ObjectComposition def = runeliteClient.getObjectDefinition(objectId);
		if (def == null || def.getActions() == null)
		{
			return false;
		}
		for (String action : def.getActions())
		{
			if (action != null && TRAVEL_ACTIONS.contains(action))
			{
				return true;
			}
		}
		return false;
	}

	/** The name between the menu option and the trailing id, or null. */
	private static String menuTargetName(String objectInfo)
	{
		if (objectInfo == null || objectInfo.isBlank())
		{
			return null;
		}
		String[] parts = objectInfo.trim().split(" ");
		if (parts.length < 3)
		{
			return null;
		}
		return String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length - 1));
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
