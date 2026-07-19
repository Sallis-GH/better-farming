package com.betterfarming.bank;

import com.betterfarming.data.FarmingData;
import com.betterfarming.data.Patch;
import com.betterfarming.data.PatchType;
import com.betterfarming.data.Payment;
import com.betterfarming.data.Seed;
import com.betterfarming.data.requirement.Requirement;
import com.betterfarming.data.requirement.RequirementEvaluator;
import com.betterfarming.item.ItemTracker;
import com.betterfarming.item.ItemTrackerTestSupport;
import com.betterfarming.item.RunItemsService;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FarmingBankTagServiceTest
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

	private PatchSelectionService selection;
	private ItemTracker tracker;
	private FarmingBankTagService service;

	@Before
	public void setUp()
	{
		FarmingData data = new FarmingData(List.of(herbPatch, treePatch), List.of(ranarr, willow));
		FakeClient client = new FakeClient();
		client.setLevel(Skill.FARMING, 99);
		selection = new PatchSelectionService(new FakeConfigStore(), data);
		PatchAccessibilityService accessibility =
			new PatchAccessibilityService(client, data, new RequirementEvaluator());
		accessibility.refresh();
		tracker = new ItemTracker();
		RunItemsService runItems = new RunItemsService(data, selection, accessibility, tracker,
			new com.betterfarming.item.PlayerUnlocks(client),
			new com.betterfarming.BetterFarmingConfig() {});
		runItems.wire();
		service = new FarmingBankTagService(runItems, tracker);
	}

	private Optional<BankTabItem> find(List<BankTabItems> sections, String sectionName, String itemText)
	{
		return sections.stream()
			.filter(s -> s.getName().equals(sectionName))
			.flatMap(s -> s.getItems().stream())
			.filter(i -> i.text().equals(itemText))
			.findFirst();
	}

	@Test
	public void noActivePatches_hasNoItems()
	{
		assertFalse(service.hasItems());
	}

	@Test
	public void sectionsSplitByCategory_recommendedSeparated()
	{
		selection.setGroupActive("HERB|Falador", true);
		selection.setSeed("herb_falador", "ranarr_seed");
		selection.setGroupActive("TREE|Lumbridge", true);
		selection.setSeed("tree_lumbridge", "willow_seed");

		List<BankTabItems> sections = service.buildSections();
		assertEquals(6, sections.size());
		assertEquals("Teleports", sections.get(3).getName());
		assertEquals("Graceful", sections.get(4).getName());
		assertEquals("Farming outfit", sections.get(5).getName());
		assertEquals("one slot per graceful piece", 6, sections.get(4).getItems().size());
		assertEquals("one slot per farmer piece", 4, sections.get(5).getItems().size());
		assertTrue(sections.get(4).getItems().stream().anyMatch(i -> i.text().equals("Hood")));
		assertTrue(sections.get(5).getItems().stream()
			.anyMatch(i -> i.text().equals("Boro trousers")));

		assertTrue(find(sections, "Tools", "Spade").isPresent());
		assertTrue(find(sections, "Seeds & saplings", "Ranarr seed").isPresent());
		assertTrue(find(sections, "Seeds & saplings", "Willow sapling").isPresent());
		assertTrue(find(sections, "Payments", "Apples(5)").isPresent());

		BankTabItems tools = sections.get(0);
		assertTrue("magic secateurs are in the recommended sub-list",
			tools.getRecommendedItems().stream().anyMatch(i -> i.text().equals("Magic secateurs")));
		assertTrue("rake is always optional now — recommended sub-list",
			tools.getRecommendedItems().stream().anyMatch(i -> i.text().equals("Rake")));
		assertFalse("recommended items not duplicated in the required list",
			tools.getItems().stream().anyMatch(i -> i.text().equals("Magic secateurs")));
	}

	@Test
	public void satisfied_reflectsLiveTrackerCounts()
	{
		selection.setGroupActive("HERB|Falador", true);
		selection.setSeed("herb_falador", "ranarr_seed");

		assertFalse(find(service.buildSections(), "Seeds & saplings", "Ranarr seed")
			.orElseThrow().satisfied());

		ItemTrackerTestSupport.updateContainer(tracker, ItemTrackerTestSupport.CONTAINER_BANK,
			new Item[]{new Item(RANARR_SEED, 40)});

		// buildSections reads the tracker directly — no EDT round-trip needed.
		assertTrue(find(service.buildSections(), "Seeds & saplings", "Ranarr seed")
			.orElseThrow().satisfied());
	}

	@Test
	public void plantableGoalQuantity_carriedThrough()
	{
		selection.setGroupActive("HERB|Falador", true);
		selection.setSeed("herb_falador", "ranarr_seed");

		BankTabItem seed = find(service.buildSections(), "Seeds & saplings", "Ranarr seed").orElseThrow();
		assertEquals(1, seed.quantity());
	}
}
