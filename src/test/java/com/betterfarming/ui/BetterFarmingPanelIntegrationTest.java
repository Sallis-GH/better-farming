package com.betterfarming.ui;

import com.betterfarming.data.FarmingData;
import com.betterfarming.data.PatchType;
import com.betterfarming.loader.FarmingDataLoader;
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
import net.runelite.api.Skill;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
		panel = new BetterFarmingPanel(data, selectionService, availabilityService);
	}

	@Test
	public void buildsOneSectionPerPatchType()
	{
		List<PatchTypeSection> sections = findAll(panel, PatchTypeSection.class);
		// 19 distinct patch types in the bundled data
		assertEquals(19, sections.size());
	}

	@Test
	public void allotmentSectionContains17Cards()
	{
		// Find the allotment section by inspecting header label text via component name
		PatchTypeSection allotmentSection = findSectionForType(PatchType.ALLOTMENT);
		assertNotNull(allotmentSection);
		List<PatchCard> cards = findAll(allotmentSection, PatchCard.class);
		assertEquals(17, cards.size());
	}

	@Test
	public void clickingTogglePropagatesToServiceAndConfig()
	{
		PatchCard card = findFirst(panel, PatchCard.class);
		assertNotNull(card);
		JButton toggle = findToggleButton(card);
		assertNotNull(toggle);

		toggle.doClick();

		// Service was updated
		long selectedCount = selectionService.selected().count();
		assertEquals(1, selectedCount);

		// Config blob was written
		String blob = configManager.peek("betterfarming", "patchSelections");
		assertNotNull(blob);
		assertTrue(blob.contains("\"selected\":true"));
	}

	@Test
	public void seedDropdownContainsExpectedNumberOfPlantableSeeds()
	{
		// Find an allotment card; at level 99 all 8 allotment seeds should be plantable
		PatchTypeSection allotmentSection = findSectionForType(PatchType.ALLOTMENT);
		PatchCard card = findFirst(allotmentSection, PatchCard.class);
		JComboBox<?> combo = findFirst(card, JComboBox.class);

		// 8 plantable seeds + 1 placeholder = 9 items
		assertEquals(9, combo.getItemCount());
	}

	@Test
	public void seedDropdownIsEmptyWhenLoggedOut()
	{
		client.setGameState(net.runelite.api.GameState.LOGIN_SCREEN);
		availabilityService.refresh();

		PatchCard card = findFirst(panel, PatchCard.class);
		JComboBox<?> combo = findFirst(card, JComboBox.class);

		// Just the placeholder ("Log in to choose seeds")
		assertEquals(1, combo.getItemCount());
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
			// header label has name "section-header:<TYPE>"
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

	private JButton findToggleButton(PatchCard card)
	{
		for (JButton b : findAll(card, JButton.class))
		{
			if (b.getName() != null && b.getName().startsWith("toggle:"))
			{
				return b;
			}
		}
		return null;
	}
}
