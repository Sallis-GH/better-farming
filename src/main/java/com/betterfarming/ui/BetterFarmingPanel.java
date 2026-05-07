package com.betterfarming.ui;

import com.betterfarming.data.FarmingData;
import com.betterfarming.data.Patch;
import com.betterfarming.data.PatchType;
import com.betterfarming.state.PatchSelectionService;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Top-level sidebar panel. One PatchTypeSection per PatchType present in
 * the bundled FarmingData, in a fixed display order. Wraps the column in
 * a JScrollPane.
 */
public class BetterFarmingPanel extends PluginPanel
{
	/**
	 * Display order (the everyday rotation first, rare singletons last).
	 * Types absent from the data are skipped.
	 */
	private static final List<PatchType> DISPLAY_ORDER = List.of(
		PatchType.ALLOTMENT, PatchType.FLOWER, PatchType.HERB,
		PatchType.TREE, PatchType.FRUIT_TREE, PatchType.BUSH, PatchType.HOPS,
		PatchType.HARDWOOD_TREE, PatchType.SPIRIT_TREE, PatchType.CALQUAT,
		PatchType.CACTUS, PatchType.BELLADONNA, PatchType.MUSHROOM, PatchType.SEAWEED,
		PatchType.REDWOOD, PatchType.CRYSTAL_TREE, PatchType.CELASTRUS,
		PatchType.ANIMA, PatchType.HESPORI
	);

	public BetterFarmingPanel(FarmingData data,
		PatchSelectionService selectionService,
		SeedAvailabilityService availabilityService)
	{
		super(false);
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		Map<PatchType, List<Patch>> byType = groupByType(data);

		JPanel column = new JPanel();
		column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
		column.setBackground(ColorScheme.DARK_GRAY_COLOR);

		for (PatchType type : DISPLAY_ORDER)
		{
			List<Patch> patches = byType.get(type);
			if (patches == null || patches.isEmpty())
			{
				continue;
			}
			List<PatchCard> cards = new ArrayList<>(patches.size());
			for (Patch p : patches)
			{
				cards.add(new PatchCard(p, selectionService, availabilityService));
			}
			PatchTypeSection section = new PatchTypeSection(type, cards);
			section.setAlignmentX(Component.LEFT_ALIGNMENT);
			column.add(section);
		}

		JScrollPane scroll = new JScrollPane(column);
		scroll.setBorder(null);
		// 85 cards always overflow the sidebar; ALWAYS keeps the viewport
		// width stable so children don't reflow when the bar appears/hides.
		scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);

		add(scroll, BorderLayout.CENTER);
		// PluginPanel sets a default sidebar width; preferred size lets headless
		// tests still get reasonable layout dimensions.
		setPreferredSize(new Dimension(225, 600));
	}

	private static Map<PatchType, List<Patch>> groupByType(FarmingData data)
	{
		Map<PatchType, List<Patch>> result = new EnumMap<>(PatchType.class);
		for (Patch p : data.patches())
		{
			result.computeIfAbsent(p.type(), k -> new ArrayList<>()).add(p);
		}
		Comparator<Patch> byDisplay = Comparator.comparing(Patch::displayName);
		for (List<Patch> patches : result.values())
		{
			patches.sort(byDisplay);
		}
		return result;
	}
}
