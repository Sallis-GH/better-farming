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
		service = new RunItemsService(data, selection, accessibility, tracker, unlocks);
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
		assertTrue(!find("Rake").orElseThrow().recommended());
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
	public void deactivatingGroup_dropsItsRows()
	{
		selection.setGroupActive("HERB|Falador", true);
		selection.setSeed("herb_falador", "ranarr_seed");
		assertTrue(find("Ranarr seed").isPresent());

		selection.setGroupActive("HERB|Falador", false);
		assertTrue(service.items().isEmpty());
	}
}
