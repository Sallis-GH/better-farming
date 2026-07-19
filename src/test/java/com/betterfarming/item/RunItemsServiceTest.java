package com.betterfarming.item;

import com.betterfarming.data.FarmingData;
import com.betterfarming.data.Patch;
import com.betterfarming.data.PatchType;
import com.betterfarming.data.Payment;
import com.betterfarming.data.Seed;
import com.betterfarming.data.requirement.Requirement;
import com.betterfarming.data.requirement.RequirementEvaluator;
import com.betterfarming.state.PatchSelectionService;
import com.betterfarming.testsupport.FakeClient;
import com.betterfarming.testsupport.FakeConfigStore;
import com.betterfarming.ui.PatchAccessibilityService;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.runelite.api.Item;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RunItemsServiceTest
{
	private static final int RANARR_SEED = 5295;
	private static final int WILLOW_SAPLING = 5371;
	private static final int APPLES_5 = 5386;

	private final Seed ranarr = new Seed("ranarr_seed", "Ranarr seed",
		Set.of(PatchType.HERB), Collections.<Requirement>emptyList(),
		RANARR_SEED, "Ranarr seed", null);

	private final Seed willow = new Seed("willow_seed", "Willow seed",
		Set.of(PatchType.TREE), Collections.<Requirement>emptyList(),
		WILLOW_SAPLING, "Willow sapling",
		List.of(new Payment(APPLES_5, "Apples(5)", 1)));

	private final Patch herbPatch = new Patch("herb_falador", "Falador herb",
		PatchType.HERB, "Falador", null, new WorldPoint(3058, 3307, 0),
		Collections.<Requirement>emptyList());

	private final Patch treePatch = new Patch("tree_lumbridge", "Lumbridge tree",
		PatchType.TREE, "Lumbridge", null, new WorldPoint(3193, 3231, 0),
		Collections.<Requirement>emptyList());

	private FarmingData data;
	private FakeClient client;
	private PatchSelectionService selection;
	private PatchAccessibilityService accessibility;
	private ItemTracker tracker;
	private PlayerUnlocks unlocks;
	private com.betterfarming.BetterFarmingConfig config;
	private RunItemsService service;

	@Before
	public void setUp()
	{
		data = new FarmingData(List.of(herbPatch, treePatch), List.of(ranarr, willow));
		client = new FakeClient();
		client.setLevel(Skill.FARMING, 99);
		selection = new PatchSelectionService(new FakeConfigStore(), data);
		accessibility = new PatchAccessibilityService(client, data, new RequirementEvaluator());
		accessibility.refresh();
		tracker = new ItemTracker();
		unlocks = new PlayerUnlocks(client);
		config = new com.betterfarming.BetterFarmingConfig() {};
		service = new RunItemsService(data, selection, accessibility, tracker, unlocks, config);
		service.wire();
	}

	private Optional<RunItem> find(String name)
	{
		return service.items().stream()
			.filter(i -> i.displayName().equals(name))
			.findFirst();
	}

	@Test
	public void noActiveGroups_emptyList()
	{
		assertTrue(service.items().isEmpty());
	}

	@Test
	public void activeHerbGroup_listsToolsAndSelectedSeed()
	{
		selection.setGroupActive("HERB|Falador", true);
		selection.setSeed("herb_falador", "ranarr_seed");

		assertTrue(find("Rake").isPresent());
		assertTrue(find("Spade").isPresent());
		assertTrue("ground crop needs a dibber", find("Seed dibber").isPresent());
		RunItem seedRow = find("Ranarr seed").orElseThrow();
		assertEquals(1, seedRow.quantity());
		assertEquals("nothing on player, bank unseen", RunItemStatus.MISSING, seedRow.status());
	}

	@Test
	public void activeGroupWithoutSeedSelection_stillListsTools()
	{
		selection.setGroupActive("HERB|Falador", true);

		assertTrue(find("Rake").isPresent());
		assertTrue("no plantable row without a seed choice", find("Ranarr seed").isEmpty());
	}

	@Test
	public void treeOnlyRun_noDibber_paymentListed()
	{
		selection.setGroupActive("TREE|Lumbridge", true);
		selection.setSeed("tree_lumbridge", "willow_seed");

		assertTrue("sapling crops don't need a dibber", find("Seed dibber").isEmpty());
		RunItem sapling = find("Willow sapling").orElseThrow();
		assertEquals(1, sapling.quantity());
		RunItem payment = find("Apples(5)").orElseThrow();
		assertEquals(1, payment.quantity());
	}

	@Test
	public void recommendedRows_flagged()
	{
		selection.setGroupActive("HERB|Falador", true);

		assertTrue(find("Magic secateurs").orElseThrow().recommended());
		assertTrue(find("Bottomless compost bucket").orElseThrow().recommended());
		assertTrue("rake is always optional (no weeds on planted patches; tithe autoweed)",
			find("Rake").orElseThrow().recommended());
		assertTrue("spade stays required by default",
			!find("Spade").orElseThrow().recommended());
	}

	@Test
	public void relyOnToolLeprechauns_demotesAllToolsToRecommended()
	{
		config = new com.betterfarming.BetterFarmingConfig()
		{
			@Override
			public boolean relyOnToolLeprechauns()
			{
				return true;
			}
		};
		service = new RunItemsService(data, selection, accessibility, tracker, unlocks, config);
		selection.setGroupActive("HERB|Falador", true);
		service.recompute();

		assertTrue(find("Spade").orElseThrow().recommended());
		assertTrue(find("Seed dibber").orElseThrow().recommended());
		assertTrue("plantables stay required",
			!find("Ranarr seed").isPresent() || !find("Ranarr seed").orElseThrow().recommended());
	}

	@Test
	public void statusProgression_missingToBankToPlayer()
	{
		selection.setGroupActive("HERB|Falador", true);
		selection.setSeed("herb_falador", "ranarr_seed");
		assertEquals(RunItemStatus.MISSING, find("Ranarr seed").orElseThrow().status());

		tracker.updateContainer(ItemTracker.CONTAINER_BANK,
			new Item[]{new Item(RANARR_SEED, 40)});
		service.recompute();
		assertEquals(RunItemStatus.IN_BANK, find("Ranarr seed").orElseThrow().status());

		tracker.updateContainer(ItemTracker.CONTAINER_INVENTORY,
			new Item[]{new Item(RANARR_SEED, 1)});
		service.recompute();
		assertEquals(RunItemStatus.ON_PLAYER, find("Ranarr seed").orElseThrow().status());
	}

	@Test
	public void secateursVariant_eitherIdSatisfies()
	{
		selection.setGroupActive("HERB|Falador", true);
		tracker.updateContainer(ItemTracker.CONTAINER_INVENTORY,
			new Item[]{new Item(FarmingTools.MAGIC_SECATEURS, 1)});
		service.recompute();

		assertEquals(RunItemStatus.ON_PLAYER, find("Magic secateurs").orElseThrow().status());
	}

	@Test
	public void bareHandedPlanting_removesSeedDibber()
	{
		selection.setGroupActive("HERB|Falador", true);
		assertTrue(find("Seed dibber").isPresent());

		// Barbarian Training farming section complete (varbit 9609 == 3).
		client.setVarbit(net.runelite.api.gameval.VarbitID.BRUT_FARMING_PLANTING, 3);
		unlocks.refresh();
		service.recompute();

		assertTrue("bare-handed planting waives the dibber", find("Seed dibber").isEmpty());
		assertTrue("other tools unaffected", find("Rake").isPresent());
	}

	@Test
	public void outfitRows_requireEveryPiece()
	{
		selection.setGroupActive("HERB|Falador", true);
		RunItem graceful = find("Graceful").orElseThrow();
		assertTrue(graceful.recommended());
		assertEquals(RunItemStatus.MISSING, graceful.status());

		// Five pieces banked, hood worn: complete across containers → IN_BANK.
		tracker.updateContainer(ItemTracker.CONTAINER_EQUIPMENT,
			new Item[]{new Item(11851, 1)}); // graceful hood (worn id)
		tracker.updateContainer(ItemTracker.CONTAINER_BANK, new Item[]{
			new Item(11852, 1), new Item(11854, 1), new Item(11856, 1),
			new Item(11858, 1), new Item(11860, 1)});
		service.recompute();
		assertEquals(RunItemStatus.IN_BANK, find("Graceful").orElseThrow().status());

		// A full worn set → ON_PLAYER (mixing recolours is fine).
		tracker.updateContainer(ItemTracker.CONTAINER_EQUIPMENT, new Item[]{
			new Item(11851, 1), new Item(11853, 1), new Item(11855, 1),
			new Item(11857, 1), new Item(11859, 1), new Item(11861, 1)});
		service.recompute();
		assertEquals(RunItemStatus.ON_PLAYER, find("Graceful").orElseThrow().status());
	}

	@Test
	public void teleportItems_fromPlannedLegs_appearAsRows()
	{
		com.betterfarming.travel.Teleport falador = new com.betterfarming.travel.Teleport(
			com.betterfarming.travel.TeleportType.SPELL, null,
			new WorldPoint(2964, 3378, 0), 4, "Falador Teleport",
			java.util.Map.of(), Set.of(), Set.of(),
			List.of(new com.betterfarming.travel.TeleportItemRequirement(
				new int[]{563}, new int[0], new int[0], 1, "Law rune")),
			false, null, false);
		com.betterfarming.travel.TeleportAvailabilityService teleports =
			new com.betterfarming.travel.TeleportAvailabilityService(
				List.of(falador), client, tracker, config);
		// Player position is far from the herb patch, so the leg needs the spell.
		client.setPlayerPosition(new WorldPoint(3222, 3218, 0));
		tracker.updateContainer(ItemTracker.CONTAINER_BANK, new Item[]{new Item(563, 50)});
		teleports.refresh();
		com.betterfarming.travel.RunOrderService runOrder =
			new com.betterfarming.travel.RunOrderService(data, selection, accessibility,
				teleports, client, config, Runnable::run);
		service.setRunOrderService(runOrder);

		selection.setGroupActive("HERB|Falador", true);
		runOrder.recompute();
		service.recompute();

		RunItem laws = find("Law rune").orElseThrow();
		assertEquals(RunItemCategory.TELEPORT, laws.category());
		assertEquals(1, laws.quantity());
		assertEquals("50 banked laws", RunItemStatus.IN_BANK, laws.status());
	}

	@Test
	public void houseChainLegItems_appearAsTeleportRows()
	{
		// Entry: Teleport to House spell (law+air+earth runes) into the POH;
		// facility: Falador-side portal near the herb patch. The composed
		// chain must surface the entry's runes in the run-items list.
		com.betterfarming.travel.Teleport houseSpell = new com.betterfarming.travel.Teleport(
			com.betterfarming.travel.TeleportType.SPELL, null,
			new WorldPoint(1923, 5709, 0), 4, "Teleport to House",
			java.util.Map.of(), Set.of(), Set.of(),
			List.of(
				new com.betterfarming.travel.TeleportItemRequirement(
					new int[]{563}, new int[0], new int[0], 1, "Law rune"),
				new com.betterfarming.travel.TeleportItemRequirement(
					new int[]{556}, new int[0], new int[0], 1, "Air rune"),
				new com.betterfarming.travel.TeleportItemRequirement(
					new int[]{557}, new int[0], new int[0], 1, "Earth rune")),
			false, null, false);
		com.betterfarming.travel.Teleport faladorPortal = new com.betterfarming.travel.Teleport(
			com.betterfarming.travel.TeleportType.POH_PORTAL,
			new WorldPoint(1928, 5731, 0), new WorldPoint(2964, 3378, 0), 1,
			"Falador Portal", java.util.Map.of(), Set.of(), Set.of(),
			java.util.Collections.emptyList(), false,
			"Enter Falador Portal 13618", false);
		config = new com.betterfarming.BetterFarmingConfig()
		{
			@Override
			public boolean pohPortalFalador()
			{
				return true;
			}
		};
		com.betterfarming.travel.TeleportAvailabilityService teleports =
			new com.betterfarming.travel.TeleportAvailabilityService(
				List.of(houseSpell, faladorPortal), client, tracker, config);
		client.setPlayerPosition(new WorldPoint(3222, 3218, 0));
		tracker.updateContainer(ItemTracker.CONTAINER_BANK, new Item[]{
			new Item(563, 50), new Item(556, 500), new Item(557, 500)});
		teleports.refresh();
		com.betterfarming.travel.RunOrderService runOrder =
			new com.betterfarming.travel.RunOrderService(data, selection, accessibility,
				teleports, client, config, Runnable::run);
		service = new RunItemsService(data, selection, accessibility, tracker,
			new PlayerUnlocks(client), config);
		service.wire();
		service.setRunOrderService(runOrder);

		selection.setGroupActive("HERB|Falador", true);
		runOrder.recompute();
		service.recompute();

		com.betterfarming.travel.RoutePlanner.Leg leg = runOrder.legs().get(0);
		assertTrue("leg should ride the house chain", leg.teleport().viaPoh());
		RunItem laws = find("Law rune").orElseThrow();
		assertEquals(RunItemCategory.TELEPORT, laws.category());
		assertEquals(RunItemStatus.IN_BANK, laws.status());
		assertTrue(find("Air rune").isPresent());
		assertTrue(find("Earth rune").isPresent());
	}

	@Test
	public void reusableTeleportItemCountsOnceAcrossLegs()
	{
		// Two island stops, each reachable only by a chain that starts with
		// the refillable ectophial: the run needs ONE ectophial, not two.
		com.betterfarming.data.FarmingData islands = new com.betterfarming.data.FarmingData(
			List.of(
				new com.betterfarming.data.Patch("island_a", "Island A herb", PatchType.HERB,
					"Island A", null, new WorldPoint(2000, 2000, 0), List.of()),
				new com.betterfarming.data.Patch("island_b", "Island B herb", PatchType.HERB,
					"Island B", null, new WorldPoint(2600, 2000, 0), List.of())),
			List.of());
		WorldPoint port = new WorldPoint(3660, 3522, 0);
		com.betterfarming.travel.Teleport ecto = new com.betterfarming.travel.Teleport(
			com.betterfarming.travel.TeleportType.ITEM, null, port, 4, "Ectophial",
			java.util.Map.of(), Set.of(), Set.of(),
			List.of(new com.betterfarming.travel.TeleportItemRequirement(
				new int[]{4251, 4252}, new int[0], new int[0], 1, "Ectophial")),
			false, null, false);
		com.betterfarming.travel.Teleport shipA = new com.betterfarming.travel.Teleport(
			com.betterfarming.travel.TeleportType.SHIP,
			new WorldPoint(3662, 3524, 0), new WorldPoint(2002, 2002, 0), 6, "Ship to A",
			java.util.Map.of(), Set.of(), Set.of(), java.util.Collections.emptyList(),
			false, null, false);
		com.betterfarming.travel.Teleport shipB = new com.betterfarming.travel.Teleport(
			com.betterfarming.travel.TeleportType.SHIP,
			new WorldPoint(3664, 3520, 0), new WorldPoint(2602, 2002, 0), 6, "Ship to B",
			java.util.Map.of(), Set.of(), Set.of(), java.util.Collections.emptyList(),
			false, null, false);

		com.betterfarming.state.PatchSelectionService islandSelection =
			new com.betterfarming.state.PatchSelectionService(
				new com.betterfarming.testsupport.FakeConfigStore(), islands);
		com.betterfarming.ui.PatchAccessibilityService islandAccess =
			new com.betterfarming.ui.PatchAccessibilityService(client, islands,
				new com.betterfarming.data.requirement.RequirementEvaluator());
		islandAccess.refresh();
		tracker.updateContainer(ItemTracker.CONTAINER_BANK, new Item[]{new Item(4251, 1)});
		com.betterfarming.travel.TeleportAvailabilityService teleports =
			new com.betterfarming.travel.TeleportAvailabilityService(
				List.of(ecto, shipA, shipB), client, tracker, config);
		client.setPlayerPosition(new WorldPoint(3222, 3218, 0));
		teleports.refresh();
		com.betterfarming.travel.RunOrderService runOrder =
			new com.betterfarming.travel.RunOrderService(islands, islandSelection, islandAccess,
				teleports, client, config, Runnable::run);
		RunItemsService islandItems = new RunItemsService(islands, islandSelection, islandAccess,
			tracker, new PlayerUnlocks(client), config);
		islandItems.wire();
		islandItems.setRunOrderService(runOrder);

		islandSelection.setGroupActive("HERB|Island A", true);
		islandSelection.setGroupActive("HERB|Island B", true);
		runOrder.recompute();
		islandItems.recompute();

		// Both legs chain through the ectophial.
		int ectoLegs = 0;
		for (com.betterfarming.travel.RoutePlanner.Leg leg : runOrder.legs())
		{
			assertTrue(leg.teleport() != null);
			if (leg.teleport().displayLabel().contains("Ectophial"))
			{
				ectoLegs++;
			}
		}
		assertEquals(2, ectoLegs);

		RunItem ectoRow = islandItems.items().stream()
			.filter(i -> i.displayName().contains("Ectophial"))
			.findFirst().orElseThrow();
		assertEquals("refillable: one is enough for the whole run", 1, ectoRow.quantity());
	}

	@Test
	public void deactivatingGroup_dropsItsRows()
	{
		selection.setGroupActive("HERB|Falador", true);
		selection.setSeed("herb_falador", "ranarr_seed");
		assertTrue(find("Ranarr seed").isPresent());

		selection.setGroupActive("HERB|Falador", false);
		assertTrue(service.items().isEmpty());
	}
}
