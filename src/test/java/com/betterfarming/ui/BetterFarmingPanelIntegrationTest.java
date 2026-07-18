package com.betterfarming.ui;

import com.betterfarming.data.FarmingData;
import com.betterfarming.data.PatchType;
import com.betterfarming.data.requirement.QuestRequirement;
import com.betterfarming.data.requirement.Requirement;
import com.betterfarming.data.requirement.RequirementEvaluator;
import com.betterfarming.data.requirement.SkillRequirement;
import com.betterfarming.loader.FarmingDataLoader;
import com.betterfarming.state.PatchSelection;
import com.betterfarming.state.PatchSelectionService;
import com.betterfarming.testsupport.FakeClient;
import com.betterfarming.testsupport.FakeConfigStore;
import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.StatChanged;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.betterfarming.item.ItemTracker;
import com.betterfarming.item.RunItemsService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BetterFarmingPanelIntegrationTest
{
	@BeforeClass
	public static void enableHeadless()
	{
		System.setProperty("java.awt.headless", "true");
	}

	private FarmingData data;
	private FakeClient client;
	private FakeConfigStore configManager;
	private PatchSelectionService selectionService;
	private SeedAvailabilityService availabilityService;
	private PatchAccessibilityService accessibilityService;
	private ItemTracker itemTracker;
	private RunItemsService runItemsService;
	private BetterFarmingPanel panel;

	@Before
	public void setUp() throws IOException
	{
		data = new FarmingDataLoader().load();
		client = new FakeClient();
		client.setLevel(Skill.FARMING, 99);
		configManager = new FakeConfigStore();

		selectionService = new PatchSelectionService(configManager, data);
		availabilityService = new SeedAvailabilityService(client, data);
		accessibilityService = new PatchAccessibilityService(
			client, data, new com.betterfarming.data.requirement.RequirementEvaluator());
		accessibilityService.refresh();
		itemTracker = new ItemTracker();
		runItemsService = new RunItemsService(data, selectionService, accessibilityService, itemTracker);
		panel = new BetterFarmingPanel(data, selectionService, availabilityService,
			accessibilityService, runItemsService);
	}

	@Test
	public void buildsOneSectionPerPatchType()
	{
		List<PatchTypeSection> sections = findAll(panel, PatchTypeSection.class);
		// 19 distinct patch types in the bundled data.
		assertEquals(19, sections.size());
	}

	@Test
	public void allotmentSectionContainsNineGroupCards()
	{
		PatchTypeSection allotmentSection = findSectionForType(PatchType.ALLOTMENT);
		assertNotNull(allotmentSection);
		List<PatchGroupCard> cards = findAll(allotmentSection, PatchGroupCard.class);
		// 17 allotment patches collapse into 9 (type, location) groups.
		assertEquals(9, cards.size());
	}

	@Test
	public void hardwoodTreeSectionContainsThreeGroupCards()
	{
		PatchTypeSection hardwoodSection = findSectionForType(PatchType.HARDWOOD_TREE);
		assertNotNull(hardwoodSection);
		List<PatchGroupCard> cards = findAll(hardwoodSection, PatchGroupCard.class);
		// 5 hardwood patches: 3 at Fossil Island + 2 singletons → 3 groups.
		assertEquals(3, cards.size());
	}

	@Test
	public void faladorAllotmentCardHasTwoSubRows()
	{
		PatchGroupCard card = findCardForGroupKey("ALLOTMENT|South of Falador");
		assertNotNull(card);
		List<PatchSubRow> rows = findAll(card, PatchSubRow.class);
		assertEquals(2, rows.size());
	}

	@Test
	public void fossilIslandHardwoodCardHasThreeSubRows()
	{
		PatchGroupCard card = findCardForGroupKey("HARDWOOD_TREE|Fossil Island mushroom forest");
		assertNotNull(card);
		List<PatchSubRow> rows = findAll(card, PatchSubRow.class);
		assertEquals(3, rows.size());
	}

	@Test
	public void singletonCardHasOneSubRowAndNoSubLabel()
	{
		PatchGroupCard card = findCardForGroupKey("ALLOTMENT|Harmony Island");
		assertNotNull(card);
		List<PatchSubRow> rows = findAll(card, PatchSubRow.class);
		assertEquals(1, rows.size());
		PatchSubRow row = rows.get(0);
		// No JLabel inside the row whose name starts with "subLabel:" → label slot suppressed.
		boolean hasLabel = false;
		for (javax.swing.JLabel l : findAll(row, javax.swing.JLabel.class))
		{
			if (l.getName() != null && l.getName().startsWith("subLabel:"))
			{
				hasLabel = true;
				break;
			}
		}
		assertFalse("singleton row should suppress the sub-label slot", hasLabel);
	}

	@Test
	public void clickingGroupTogglePropagatesToServiceAndConfig()
	{
		PatchGroupCard card = findCardForGroupKey("ALLOTMENT|South of Falador");
		assertNotNull(card);
		JButton toggle = findGroupToggleButton(card);
		assertNotNull(toggle);

		toggle.doClick();

		assertTrue(selectionService.isGroupActive("ALLOTMENT|South of Falador"));

		String blob = configManager.peek("betterfarming", "patchSelections");
		assertNotNull(blob);
		assertTrue(blob.contains("\"ALLOTMENT|South of Falador\""));
	}

	@Test
	public void selectingSeedInOneSubRowDoesNotAffectSiblings()
	{
		PatchGroupCard card = findCardForGroupKey("ALLOTMENT|South of Falador");
		List<PatchSubRow> rows = findAll(card, PatchSubRow.class);

		// Pick the first plantable seed in the first sub-row.
		PatchSubRow row0 = rows.get(0);
		JComboBox<?> combo0 = findFirst(row0, JComboBox.class);
		// item 0 is the null placeholder; pick item 1 (a real seed).
		combo0.setSelectedIndex(1);

		// Sibling sub-row should remain unselected.
		PatchSubRow row1 = rows.get(1);
		JComboBox<?> combo1 = findFirst(row1, JComboBox.class);
		assertNull("sibling sub-row should still show placeholder", combo1.getSelectedItem());

		// Service has the chosen seed for the first sub-row only.
		String firstPatchId = data.patches().stream()
			.filter(p -> "South of Falador".equals(p.location()) && "NW".equals(p.subPatchLabel()))
			.findFirst().get().id();
		String secondPatchId = data.patches().stream()
			.filter(p -> "South of Falador".equals(p.location()) && "SE".equals(p.subPatchLabel()))
			.findFirst().get().id();
		assertNotNull(selectionService.get(firstPatchId).map(PatchSelection::seedId).orElse(null));
		assertFalse(selectionService.get(secondPatchId).isPresent());
	}

	@Test
	public void togglingGroupOffPreservesSeeds()
	{
		PatchGroupCard card = findCardForGroupKey("ALLOTMENT|South of Falador");
		List<PatchSubRow> rows = findAll(card, PatchSubRow.class);
		JComboBox<?> combo0 = findFirst(rows.get(0), JComboBox.class);
		combo0.setSelectedIndex(1);

		String firstPatchId = data.patches().stream()
			.filter(p -> "South of Falador".equals(p.location()) && "NW".equals(p.subPatchLabel()))
			.findFirst().get().id();
		String savedSeed = selectionService.get(firstPatchId).get().seedId();

		// Toggle on, then off.
		JButton toggle = findGroupToggleButton(card);
		toggle.doClick();
		toggle.doClick();

		assertFalse(selectionService.isGroupActive("ALLOTMENT|South of Falador"));
		assertEquals("seed survives group deactivation", savedSeed,
			selectionService.get(firstPatchId).get().seedId());
	}

	@Test
	public void seedDropdownIsEmptyWhenLoggedOut() throws Exception
	{
		client.setGameState(net.runelite.api.GameState.LOGIN_SCREEN);
		availabilityService.refresh();
		SwingUtilities.invokeAndWait(() -> {});

		PatchGroupCard card = findFirst(panel, PatchGroupCard.class);
		PatchSubRow row = findFirst(card, PatchSubRow.class);
		JComboBox<?> combo = findFirst(row, JComboBox.class);

		// Just the placeholder.
		assertEquals(1, combo.getItemCount());
	}

	@Test
	public void farmingGuildAllotmentLocksWhenFarmingBelow45()
	{
		// Constructed in setUp() with FARMING=99, so all locks should be cleared.
		// Rebuild with FARMING=1 to trigger the lock at construction time.
		client.setLevel(Skill.FARMING, 1);
		accessibilityService.refresh();

		// Rebuild the panel so cards see the locked state on construction.
		panel = new BetterFarmingPanel(data, selectionService, availabilityService,
			accessibilityService, runItemsService);

		PatchGroupCard card = findCardForGroupKey("ALLOTMENT|Farming Guild");
		assertNotNull(card);
		assertTrue("Farming Guild allotment requires Farming 45", card.isLocked());

		PatchGroupCard falador = findCardForGroupKey("ALLOTMENT|South of Falador");
		assertNotNull(falador);
		assertFalse("South of Falador has no requirements", falador.isLocked());
	}

	@Test
	public void farmingGuildAllotmentUnlocksAfterLevelUp() throws Exception
	{
		client.setLevel(Skill.FARMING, 1);
		accessibilityService.refresh();
		panel = new BetterFarmingPanel(data, selectionService, availabilityService,
			accessibilityService, runItemsService);

		PatchGroupCard card = findCardForGroupKey("ALLOTMENT|Farming Guild");
		assertTrue("locked at Farming 1", card.isLocked());

		client.setLevel(Skill.FARMING, 99);
		accessibilityService.onStatChanged(
			new StatChanged(Skill.FARMING, 99 * 1000, 99, 99));
		SwingUtilities.invokeAndWait(() -> {});

		assertFalse("unlocked at Farming 99", card.isLocked());
		JButton toggle = findGroupToggleButton(card);
		assertTrue("toggle button reappears", toggle.isVisible());
	}

	@Test
	public void prifddinasUnlocksAfterQuestFinishedAndRegionCross() throws Exception
	{
		client.setQuestState(Quest.SONG_OF_THE_ELVES, QuestState.NOT_STARTED);
		accessibilityService.refresh();
		panel = new BetterFarmingPanel(data, selectionService, availabilityService,
			accessibilityService, runItemsService);

		PatchGroupCard card = findCardForGroupKey("ALLOTMENT|Prifddinas");
		assertTrue("locked when Song of the Elves not finished", card.isLocked());

		client.setQuestState(Quest.SONG_OF_THE_ELVES, QuestState.FINISHED);
		GameStateChanged gsc = new GameStateChanged();
		gsc.setGameState(GameState.LOADING);  // region cross
		accessibilityService.onGameStateChanged(gsc);
		SwingUtilities.invokeAndWait(() -> {});

		assertFalse("unlocked after quest finished + region cross", card.isLocked());
	}

	@Test
	public void singleRequirementReasonIsPlainProse()
	{
		client.setLevel(Skill.FARMING, 1);
		accessibilityService.refresh();
		panel = new BetterFarmingPanel(data, selectionService, availabilityService,
			accessibilityService, runItemsService);

		PatchGroupCard card = findCardForGroupKey("ALLOTMENT|Farming Guild");
		JLabel info = findInfoIcon(card);
		assertNotNull(info);
		assertEquals("Requires Farming 45", info.getToolTipText());
	}

	@Test
	public void multipleRequirementsReasonIsHtmlBulletList() throws Exception
	{
		// Synthetic group with two requirements — bundled data has no such group today.
		com.betterfarming.data.Patch combo = new com.betterfarming.data.Patch(
			"combo", "Combo display", PatchType.ALLOTMENT, "Combo Spot", null,
			new net.runelite.api.coords.WorldPoint(0, 0, 0),
			List.<Requirement>of(
				new SkillRequirement(Skill.FARMING, 45),
				new QuestRequirement(Quest.SONG_OF_THE_ELVES, QuestState.FINISHED)));
		FarmingData synthetic = new FarmingData(List.of(combo),
			java.util.List.<com.betterfarming.data.Seed>of());

		FakeClient localClient = new FakeClient();
		localClient.setLevel(Skill.FARMING, 1);
		localClient.setQuestState(Quest.SONG_OF_THE_ELVES, QuestState.NOT_STARTED);

		PatchAccessibilityService localAccess = new PatchAccessibilityService(
			localClient, synthetic, new RequirementEvaluator());
		localAccess.refresh();

		PatchSelectionService localSelection =
			new PatchSelectionService(new FakeConfigStore(), synthetic);
		BetterFarmingPanel localPanel = new BetterFarmingPanel(synthetic,
			localSelection,
			new SeedAvailabilityService(localClient, synthetic),
			localAccess,
			new RunItemsService(synthetic, localSelection, localAccess, new ItemTracker()));

		PatchGroupCard card = null;
		for (PatchGroupCard c : findAll(localPanel, PatchGroupCard.class))
		{
			JButton t = findGroupToggleButton(c);
			if (t != null && "groupToggle:ALLOTMENT|Combo Spot".equals(t.getName()))
			{
				card = c;
				break;
			}
		}
		assertNotNull(card);

		JLabel info = findInfoIcon(card);
		String tip = info.getToolTipText();
		assertNotNull(tip);
		assertTrue("multi-req tooltip uses HTML", tip.startsWith("<html>"));
		assertTrue("includes Farming 45", tip.contains("Farming 45"));
		assertTrue("includes quest name", tip.contains("Song of the Elves"));
	}

	@Test
	public void activeFlagPreservedAcrossLockCycle() throws Exception
	{
		client.setLevel(Skill.FARMING, 99);
		accessibilityService.refresh();
		panel = new BetterFarmingPanel(data, selectionService, availabilityService,
			accessibilityService, runItemsService);

		// User toggles a low-requirement group active (Farming Guild needs 45 — they have 99).
		PatchGroupCard card = findCardForGroupKey("ALLOTMENT|Farming Guild");
		JButton toggle = findGroupToggleButton(card);
		toggle.doClick();
		assertTrue(selectionService.isGroupActive("ALLOTMENT|Farming Guild"));
		assertFalse(card.isLocked());
		assertTrue("active + unlocked → effectively active",
			accessibilityService.effectiveActive("ALLOTMENT|Farming Guild", selectionService));

		// Skill drops below requirement (e.g. user reloads with a different account).
		client.setLevel(Skill.FARMING, 1);
		accessibilityService.onStatChanged(
			new StatChanged(Skill.FARMING, 0, 1, 1));
		SwingUtilities.invokeAndWait(() -> {});

		assertTrue("now locked", card.isLocked());
		assertTrue("active flag PRESERVED — not auto-cleared by the lock",
			selectionService.isGroupActive("ALLOTMENT|Farming Guild"));
		assertFalse("but effectiveActive shadows it to false",
			accessibilityService.effectiveActive("ALLOTMENT|Farming Guild", selectionService));

		// Skill recovers — lock clears, active flag flips back to effective without re-toggling.
		client.setLevel(Skill.FARMING, 99);
		accessibilityService.onStatChanged(
			new StatChanged(Skill.FARMING, 99 * 1000, 99, 99));
		SwingUtilities.invokeAndWait(() -> {});

		assertFalse("unlocked again", card.isLocked());
		assertTrue("effectiveActive recovers without user re-toggle",
			accessibilityService.effectiveActive("ALLOTMENT|Farming Guild", selectionService));
	}

	@Test
	public void subRowDropdownsDisabledWhileLocked() throws Exception
	{
		client.setLevel(Skill.FARMING, 1);
		accessibilityService.refresh();
		panel = new BetterFarmingPanel(data, selectionService, availabilityService,
			accessibilityService, runItemsService);

		PatchGroupCard card = findCardForGroupKey("ALLOTMENT|Farming Guild");
		assertTrue(card.isLocked());

		for (PatchSubRow row : findAll(card, PatchSubRow.class))
		{
			JComboBox<?> combo = findFirst(row, JComboBox.class);
			assertFalse("sub-row dropdown disabled while locked", combo.isEnabled());
		}

		client.setLevel(Skill.FARMING, 99);
		accessibilityService.onStatChanged(
			new StatChanged(Skill.FARMING, 99 * 1000, 99, 99));
		SwingUtilities.invokeAndWait(() -> {});

		for (PatchSubRow row : findAll(card, PatchSubRow.class))
		{
			JComboBox<?> combo = findFirst(row, JComboBox.class);
			assertTrue("sub-row dropdown re-enabled after unlock", combo.isEnabled());
		}
	}

	// ── helpers ──

	@SuppressWarnings("unchecked")
	private <T extends Component> T findFirst(Container root, Class<T> type)
	{
		for (Component c : root.getComponents())
		{
			if (type.isInstance(c))
			{
				return (T) c;
			}
			if (c instanceof Container)
			{
				T found = findFirst((Container) c, type);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private <T extends Component> List<T> findAll(Container root, Class<T> type)
	{
		List<T> out = new ArrayList<>();
		collectAll(root, type, out);
		return out;
	}

	private <T extends Component> void collectAll(Container root, Class<T> type, List<T> out)
	{
		for (Component c : root.getComponents())
		{
			if (type.isInstance(c))
			{
				out.add(type.cast(c));
			}
			if (c instanceof Container)
			{
				collectAll((Container) c, type, out);
			}
		}
	}

	private PatchTypeSection findSectionForType(PatchType type)
	{
		for (PatchTypeSection section : findAll(panel, PatchTypeSection.class))
		{
			for (Component c : findAll(section, javax.swing.JLabel.class))
			{
				String name = c.getName();
				if (name != null && name.equals("section-header:" + type.name()))
				{
					return section;
				}
			}
		}
		return null;
	}

	private PatchGroupCard findCardForGroupKey(String groupKey)
	{
		for (PatchGroupCard card : findAll(panel, PatchGroupCard.class))
		{
			JButton toggle = findGroupToggleButton(card);
			if (toggle != null && toggle.getName() != null
				&& toggle.getName().equals("groupToggle:" + groupKey))
			{
				return card;
			}
		}
		return null;
	}

	private JButton findGroupToggleButton(PatchGroupCard card)
	{
		for (JButton b : findAll(card, JButton.class))
		{
			if (b.getName() != null && b.getName().startsWith("groupToggle:"))
			{
				return b;
			}
		}
		return null;
	}

	private JLabel findInfoIcon(PatchGroupCard card)
	{
		for (JLabel l : findAll(card, JLabel.class))
		{
			if (l.getName() != null && l.getName().startsWith("info:"))
			{
				return l;
			}
		}
		return null;
	}
}
