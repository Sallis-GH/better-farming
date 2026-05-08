package com.betterfarming.ui;

import com.betterfarming.data.FarmingData;
import com.betterfarming.data.PatchType;
import com.betterfarming.data.requirement.RequirementEvaluator;
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
import javax.swing.SwingUtilities;
import net.runelite.api.Skill;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

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
		panel = new BetterFarmingPanel(data, selectionService, availabilityService,
			accessibilityService);
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
			accessibilityService);

		PatchGroupCard card = findCardForGroupKey("ALLOTMENT|Farming Guild");
		assertNotNull(card);
		assertTrue("Farming Guild allotment requires Farming 45", card.isLocked());

		PatchGroupCard falador = findCardForGroupKey("ALLOTMENT|South of Falador");
		assertNotNull(falador);
		assertFalse("South of Falador has no requirements", falador.isLocked());
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
}
