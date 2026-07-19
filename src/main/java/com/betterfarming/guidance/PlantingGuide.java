package com.betterfarming.guidance;

import com.betterfarming.data.Patch;
import com.betterfarming.data.PatchGroup;
import com.betterfarming.data.Seed;
import com.betterfarming.farming.CropState;
import com.betterfarming.farming.PatchStateService;
import com.betterfarming.state.PatchSelection;
import com.betterfarming.state.PatchSelectionService;
import com.betterfarming.travel.RoutePlanner;
import com.betterfarming.travel.Teleport;
import com.betterfarming.travel.TeleportItemRequirement;
import com.betterfarming.ui.ClientLevelSource;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;

/**
 * Per-tick "what to click next" context for the current leg, driving the
 * inventory-item and patch-object highlights:
 *
 * - Near the stop with work remaining: the first patch needing attention
 *   becomes the target; when it wants planting, the selected seed/sapling
 *   (plus a rake, in case of weeds) is highlighted in the inventory.
 * - Away from the stop: the leg's teleport items are highlighted instead,
 *   answering "what do I click to travel".
 *
 * Threading: recompute runs on the client thread (GameTick); overlays read
 * the volatile snapshot fields from the client thread each frame.
 */
public class PlantingGuide
{
	/** Within this many tiles of the stop the planting flow takes over. */
	public static final int GUIDE_RADIUS_TILES = 15;

	private static final Set<Integer> RAKE_IDS = Set.of(net.runelite.api.gameval.ItemID.RAKE);

	private final Map<String, PatchGroup> groupsByKey;
	private final Map<String, Seed> seedsById;
	private final PatchSelectionService selectionService;
	private final GuidanceService guidance;
	private final ClientLevelSource client;
	private final Function<Patch, CropState> stateFn;

	private volatile Patch targetPatch;
	private volatile CropState targetState = CropState.UNKNOWN;
	private volatile Set<Integer> highlightItemIds = Collections.emptySet();

	public PlantingGuide(List<PatchGroup> groups, List<Seed> seeds,
		PatchSelectionService selectionService, PatchStateService stateService,
		GuidanceService guidance, ClientLevelSource client)
	{
		this.groupsByKey = groups.stream().collect(Collectors.toMap(PatchGroup::key, g -> g));
		this.seedsById = seeds.stream().collect(Collectors.toMap(Seed::id, s -> s));
		this.selectionService = selectionService;
		this.guidance = guidance;
		this.client = client;
		this.stateFn = stateService::state;
	}

	/** The patch to work on right now, or null when travelling/idle. */
	public Patch targetPatch()
	{
		return targetPatch;
	}

	public CropState targetState()
	{
		return targetState;
	}

	/** Inventory item ids to highlight (seeds when planting, else teleport items). */
	public Set<Integer> highlightItemIds()
	{
		return highlightItemIds;
	}

	/** One-word instruction for the hint overlay, or null when travelling. */
	public String actionText()
	{
		Patch p = targetPatch;
		if (p == null)
		{
			return null;
		}
		switch (targetState)
		{
			case EMPTY:
				return "Plant";
			case HARVESTABLE:
				return "Harvest";
			case DISEASED:
				return "Cure";
			case DEAD:
				return "Clear";
			default:
				return "Check";
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		recompute();
	}

	public void recompute()
	{
		RoutePlanner.Leg leg = guidance.currentLeg();
		WorldPoint player = client.getPlayerPosition();
		if (leg == null || player == null)
		{
			clear();
			return;
		}
		PatchGroup group = groupsByKey.get(leg.stop().groupKey());
		boolean near = player.distanceTo2D(leg.stop().point()) <= GUIDE_RADIUS_TILES;
		if (!near || group == null)
		{
			targetPatch = null;
			targetState = CropState.UNKNOWN;
			highlightItemIds = teleportItemIds(leg.teleport());
			return;
		}

		Patch target = null;
		for (Patch p : group.patches())
		{
			if (stateFn.apply(p).needsVisit())
			{
				target = p;
				break;
			}
		}
		if (target == null)
		{
			// All patches growing; leg will complete on the next guidance tick.
			clear();
			return;
		}
		targetPatch = target;
		targetState = stateFn.apply(target);
		if (targetState == CropState.EMPTY)
		{
			// Seeds for every patch here that still wants planting, plus a
			// rake — EMPTY covers weedy patches too.
			Set<Integer> ids = new HashSet<>(RAKE_IDS);
			for (Patch p : group.patches())
			{
				if (stateFn.apply(p) != CropState.EMPTY)
				{
					continue;
				}
				Optional<PatchSelection> sel = selectionService.get(p.id());
				if (sel.isEmpty() || sel.get().seedId() == null)
				{
					continue;
				}
				Seed seed = seedsById.get(sel.get().seedId());
				if (seed != null && seed.plantableItemId() != null)
				{
					ids.add(seed.plantableItemId());
				}
			}
			highlightItemIds = Collections.unmodifiableSet(ids);
		}
		else
		{
			highlightItemIds = Collections.emptySet();
		}
	}

	private void clear()
	{
		targetPatch = null;
		targetState = CropState.UNKNOWN;
		highlightItemIds = Collections.emptySet();
	}

	private static Set<Integer> teleportItemIds(Teleport teleport)
	{
		if (teleport == null || teleport.items().isEmpty())
		{
			return Collections.emptySet();
		}
		Set<Integer> ids = new HashSet<>();
		for (TeleportItemRequirement req : teleport.items())
		{
			for (int id : req.itemIds())
			{
				ids.add(id);
			}
			for (int id : req.staffIds())
			{
				ids.add(id);
			}
			for (int id : req.offhandIds())
			{
				ids.add(id);
			}
		}
		return Collections.unmodifiableSet(ids);
	}
}
