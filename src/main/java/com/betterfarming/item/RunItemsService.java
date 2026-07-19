package com.betterfarming.item;

import com.betterfarming.BetterFarmingConfig;
import com.betterfarming.data.FarmingData;
import com.betterfarming.data.Patch;
import com.betterfarming.data.PatchGroup;
import com.betterfarming.data.PatchType;
import com.betterfarming.data.Payment;
import com.betterfarming.data.Seed;
import com.betterfarming.state.GroupActiveEvent;
import com.betterfarming.state.PatchSelection;
import com.betterfarming.state.PatchSelectionEvent;
import com.betterfarming.state.PatchSelectionService;
import com.betterfarming.travel.RoutePlanner;
import com.betterfarming.travel.RunOrderService;
import com.betterfarming.travel.TeleportItemRequirement;
import com.betterfarming.ui.PatchAccessibilityEvent;
import com.betterfarming.ui.PatchAccessibilityService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * Derives the equipment-manager list from current selections: which items the
 * player needs for the active run (tools, plantables, payments) and where each
 * one currently is (on player / banked / missing) per ItemTracker.
 *
 * Not on the EventBus itself — it composes the three underlying services and
 * re-derives on any of their change notifications (wire()/unwire() manage the
 * subscriptions). All notification paths already land on the EDT, so recompute
 * and listener fanout stay on the EDT; compute() itself is thread-agnostic.
 */
@Slf4j
public class RunItemsService
{
	private final FarmingData data;
	private final PatchSelectionService selectionService;
	private final PatchAccessibilityService accessibilityService;
	private final ItemTracker itemTracker;
	private final PlayerUnlocks playerUnlocks;
	private final BetterFarmingConfig config;

	private final Set<Runnable> listeners = new LinkedHashSet<>();
	private List<RunItem> current = Collections.emptyList();

	private final Consumer<PatchSelectionEvent> seedListener = e -> recompute();
	private final Consumer<GroupActiveEvent> groupListener = e -> recompute();
	private final Consumer<PatchAccessibilityEvent> accessibilityListener = e -> recompute();
	private final Runnable trackerListener = this::recompute;
	private final Runnable unlocksListener = this::recompute;
	private final Runnable runOrderListener = this::recompute;

	private final Map<String, Seed> seedsById = new LinkedHashMap<>();

	/**
	 * Optional: when set, the planned legs' teleport items join the list.
	 * Setter-wired (like the bank tab) because RunOrderService is constructed
	 * after this service in startUp.
	 */
	private RunOrderService runOrderService;

	public RunItemsService(FarmingData data,
		PatchSelectionService selectionService,
		PatchAccessibilityService accessibilityService,
		ItemTracker itemTracker,
		PlayerUnlocks playerUnlocks,
		BetterFarmingConfig config)
	{
		this.data = data;
		this.selectionService = selectionService;
		this.accessibilityService = accessibilityService;
		this.itemTracker = itemTracker;
		this.playerUnlocks = playerUnlocks;
		this.config = config;
		for (Seed s : data.seeds())
		{
			seedsById.put(s.id(), s);
		}
		recompute();
	}

	/** Subscribe to the underlying services. Call once from plugin startUp. */
	public void wire()
	{
		selectionService.addListener(seedListener);
		selectionService.addGroupListener(groupListener);
		accessibilityService.addListener(accessibilityListener);
		itemTracker.addListener(trackerListener);
		playerUnlocks.addListener(unlocksListener);
	}

	/** Undo wire(). Call from plugin shutDown. */
	public void unwire()
	{
		selectionService.removeListener(seedListener);
		selectionService.removeGroupListener(groupListener);
		accessibilityService.removeListener(accessibilityListener);
		itemTracker.removeListener(trackerListener);
		playerUnlocks.removeListener(unlocksListener);
		if (runOrderService != null)
		{
			runOrderService.removeListener(runOrderListener);
		}
	}

	/** Wire the route plan in (teleport item rows). Call after wire(). */
	public void setRunOrderService(RunOrderService runOrderService)
	{
		this.runOrderService = runOrderService;
		runOrderService.addListener(runOrderListener);
		recompute();
	}

	public List<RunItem> items()
	{
		return current;
	}

	public void addListener(Runnable l)    { listeners.add(l); }
	public void removeListener(Runnable l) { listeners.remove(l); }

	// ── computation ──

	public void recompute()
	{
		List<RunItem> next = compute();
		if (next.equals(current))
		{
			return;
		}
		current = next;
		for (Runnable l : new ArrayList<>(listeners))
		{
			try
			{
				l.run();
			}
			catch (Exception | AssertionError ex)
			{
				log.warn("Better Farming: run-items listener {} threw", l.getClass().getName(), ex);
			}
		}
	}

	private List<RunItem> compute()
	{
		// 1. Collect active patches (group toggled on AND not requirement-locked).
		List<Patch> activePatches = new ArrayList<>();
		for (PatchGroup g : PatchGroup.groupAll(data.patches()))
		{
			if (accessibilityService.effectiveActive(g.key(), selectionService))
			{
				activePatches.addAll(g.patches());
			}
		}
		if (activePatches.isEmpty())
		{
			return Collections.emptyList();
		}

		// 2. Aggregate plantables and payments per selected seed, preserving
		// patch iteration order for stable row ordering.
		Set<PatchType> activeTypes = new LinkedHashSet<>();
		Map<Seed, Integer> plantableCounts = new LinkedHashMap<>();
		Map<Payment, Integer> paymentCounts = new LinkedHashMap<>();
		for (Patch p : activePatches)
		{
			activeTypes.add(p.type());
			Optional<PatchSelection> sel = selectionService.get(p.id());
			if (sel.isEmpty())
			{
				continue;
			}
			Seed seed = seedsById.get(sel.get().seedId());
			if (seed == null || seed.plantableItemId() == null)
			{
				continue;
			}
			plantableCounts.merge(seed, p.type().getPlantablesPerPatch(), Integer::sum);
			if (seed.payments() != null)
			{
				for (Payment payment : seed.payments())
				{
					paymentCounts.merge(payment, payment.quantity(), Integer::sum);
				}
			}
		}

		// 3. Build rows: tools, then plantables, then payments. With the
		// leprechaun option every tool is optional (shared tool storage at
		// each patch). The rake is always merely recommended: patches with a
		// living crop grow no weeds, and Tithe Farm's autoweed covers the rest.
		boolean lep = config.relyOnToolLeprechauns();
		List<RunItem> out = new ArrayList<>();
		out.add(row("Rake", Set.of(FarmingTools.RAKE), 1, true, RunItemCategory.TOOL));
		out.add(row("Spade", Set.of(FarmingTools.SPADE), 1, lep, RunItemCategory.TOOL));
		// Barbarian Training's bare-handed planting replaces the seed dibber.
		boolean anyGroundCrop = activeTypes.stream().anyMatch(t -> !t.isSapling());
		if (anyGroundCrop && !playerUnlocks.bareHandedPlanting())
		{
			out.add(row("Seed dibber", Set.of(FarmingTools.SEED_DIBBER), 1, lep, RunItemCategory.TOOL));
		}
		out.add(row("Magic secateurs", Set.of(FarmingTools.MAGIC_SECATEURS), 1, true, RunItemCategory.TOOL));
		out.add(row("Bottomless compost bucket", FarmingTools.ANY_BOTTOMLESS_BUCKET, 1, true, RunItemCategory.TOOL));

		for (Map.Entry<Seed, Integer> e : plantableCounts.entrySet())
		{
			Seed seed = e.getKey();
			out.add(row(seed.plantableName(), Set.of(seed.plantableItemId()), e.getValue(), false,
				RunItemCategory.PLANTABLE));
		}
		for (Map.Entry<Payment, Integer> e : paymentCounts.entrySet())
		{
			Payment payment = e.getKey();
			out.add(row(payment.name(), Set.of(payment.itemId()), e.getValue(), false,
				RunItemCategory.PAYMENT));
		}

		// 4. Teleport items consumed by the planned legs (runes, tabs,
		// jewellery), summed across legs. A staff/offhand substitute on the
		// player satisfies its row outright.
		out.addAll(teleportRows());

		// 5. Recommended run gear — one row per outfit set.
		out.add(outfitRow("Graceful", Outfits.GRACEFUL));
		out.add(outfitRow("Farming outfit", Outfits.FARMERS));

		return out;
	}

	private List<RunItem> teleportRows()
	{
		List<RunItem> out = new ArrayList<>();
		if (runOrderService == null)
		{
			return out;
		}
		Map<String, TeleportItemRequirement> byName = new LinkedHashMap<>();
		Map<String, Integer> totals = new LinkedHashMap<>();
		for (RoutePlanner.Leg leg : runOrderService.legs())
		{
			if (leg.teleport() == null)
			{
				continue;
			}
			for (TeleportItemRequirement req : leg.teleport().items())
			{
				byName.putIfAbsent(req.name(), req);
				totals.merge(req.name(), req.quantity(), Integer::sum);
			}
		}
		for (Map.Entry<String, Integer> e : totals.entrySet())
		{
			TeleportItemRequirement req = byName.get(e.getKey());
			Set<Integer> ids = boxSet(req.itemIds());
			RunItemStatus status;
			if (itemTracker.countOnPlayer(boxSet(req.staffIds())) > 0
				|| itemTracker.countOnPlayer(boxSet(req.offhandIds())) > 0)
			{
				status = RunItemStatus.ON_PLAYER;
			}
			else
			{
				status = statusFor(ids, e.getValue());
			}
			out.add(new RunItem(req.name(), ids, e.getValue(), false,
				RunItemCategory.TELEPORT, status, null));
		}
		return out;
	}

	/**
	 * Outfit row: satisfied only when every slot has at least one variant.
	 * ON_PLAYER = fully worn/carried; IN_BANK = complete across player+bank.
	 */
	private RunItem outfitRow(String name, List<Set<Integer>> pieces)
	{
		boolean allOnPlayer = true;
		boolean allSomewhere = true;
		Set<Integer> union = new LinkedHashSet<>();
		for (Set<Integer> piece : pieces)
		{
			union.addAll(piece);
			int onPlayer = itemTracker.countOnPlayer(piece);
			if (onPlayer == 0)
			{
				allOnPlayer = false;
				if (itemTracker.countBanked(piece) == 0)
				{
					allSomewhere = false;
				}
			}
		}
		RunItemStatus status = allOnPlayer ? RunItemStatus.ON_PLAYER
			: allSomewhere ? RunItemStatus.IN_BANK : RunItemStatus.MISSING;
		return new RunItem(name, union, pieces.size(), true,
			RunItemCategory.GEAR, status, pieces);
	}

	private RunItem row(String name, Set<Integer> ids, int quantity, boolean recommended,
		RunItemCategory category)
	{
		return new RunItem(name, ids, quantity, recommended, category,
			statusFor(ids, quantity), null);
	}

	private RunItemStatus statusFor(Set<Integer> ids, int quantity)
	{
		int onPlayer = itemTracker.countOnPlayer(ids);
		if (onPlayer >= quantity)
		{
			return RunItemStatus.ON_PLAYER;
		}
		if (onPlayer + itemTracker.countBanked(ids) >= quantity)
		{
			return RunItemStatus.IN_BANK;
		}
		return RunItemStatus.MISSING;
	}

	private static Set<Integer> boxSet(int[] ids)
	{
		Set<Integer> out = new LinkedHashSet<>();
		for (int id : ids)
		{
			out.add(id);
		}
		return out;
	}
}
