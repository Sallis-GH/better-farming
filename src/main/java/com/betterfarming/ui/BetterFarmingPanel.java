package com.betterfarming.ui;

import com.betterfarming.BetterFarmingConfig;
import com.betterfarming.data.FarmingData;
import com.betterfarming.data.PatchGroup;
import com.betterfarming.data.PatchType;
import com.betterfarming.guidance.GuidanceService;
import com.betterfarming.item.ItemTracker;
import com.betterfarming.item.RunItemsService;
import com.betterfarming.state.PatchSelectionService;
import com.betterfarming.travel.RunOrderService;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Top-level sidebar panel. One PatchTypeSection per PatchType present in the
 * bundled FarmingData (and not hidden via config), in a fixed display order.
 * Seed choice is per-type (one picker per section, simple mode) or per-patch
 * (a dropdown in every group card) per BetterFarmingConfig.seedSelectionMode.
 *
 * The content column is rebuilt via rebuildContent() when the layout config
 * changes; swapping the viewport view detaches the old components, whose
 * removeNotify() hooks unsubscribe them from the services.
 */
public class BetterFarmingPanel extends PluginPanel
{
	/** Display order — everyday rotation first; rare singletons last. */
	private static final List<PatchType> DISPLAY_ORDER = List.of(
		PatchType.ALLOTMENT, PatchType.FLOWER, PatchType.HERB,
		PatchType.TREE, PatchType.FRUIT_TREE, PatchType.BUSH, PatchType.HOPS,
		PatchType.HARDWOOD_TREE, PatchType.SPIRIT_TREE, PatchType.CALQUAT,
		PatchType.CACTUS, PatchType.BELLADONNA, PatchType.MUSHROOM, PatchType.SEAWEED,
		PatchType.REDWOOD, PatchType.CRYSTAL_TREE, PatchType.CELASTRUS,
		PatchType.ANIMA, PatchType.HESPORI
	);

	private final FarmingData data;
	private final PatchSelectionService selectionService;
	private final SeedAvailabilityService availabilityService;
	private final PatchAccessibilityService accessibilityService;
	private final RunItemsService runItemsService;
	private final RunOrderService runOrderService;
	private final ItemTracker itemTracker;
	private final GuidanceService guidanceService;
	private final BetterFarmingConfig config;
	private final JScrollPane scroll;

	public BetterFarmingPanel(FarmingData data,
		PatchSelectionService selectionService,
		SeedAvailabilityService availabilityService,
		PatchAccessibilityService accessibilityService,
		RunItemsService runItemsService,
		RunOrderService runOrderService,
		ItemTracker itemTracker,
		GuidanceService guidanceService,
		BetterFarmingConfig config)
	{
		super(false);
		this.data = data;
		this.selectionService = selectionService;
		this.availabilityService = availabilityService;
		this.accessibilityService = accessibilityService;
		this.runItemsService = runItemsService;
		this.runOrderService = runOrderService;
		this.itemTracker = itemTracker;
		this.guidanceService = guidanceService;
		this.config = config;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		scroll = new JScrollPane();
		scroll.setBorder(null);
		scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		rebuildContent();

		add(scroll, BorderLayout.CENTER);
		setPreferredSize(new Dimension(225, 600));
	}

	/**
	 * (Re)builds the sidebar column from the current config — seed selection
	 * mode and hidden patch types. EDT only; replacing the viewport view
	 * detaches the previous components (their removeNotify unsubscribes).
	 */
	public void rebuildContent()
	{
		List<PatchGroup> groups = PatchGroup.groupAll(data.patches());
		Map<PatchType, List<PatchGroup>> byType = new EnumMap<>(PatchType.class);
		for (PatchGroup g : groups)
		{
			byType.computeIfAbsent(g.type(), k -> new ArrayList<>()).add(g);
		}

		ScrollableColumn column = new ScrollableColumn();
		column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
		column.setBackground(ColorScheme.DARK_GRAY_COLOR);

		RunOrderSection runOrderSection = new RunOrderSection(runOrderService, itemTracker,
			guidanceService);
		runOrderSection.setAlignmentX(Component.LEFT_ALIGNMENT);
		column.add(runOrderSection);

		RunItemsSection runItemsSection = new RunItemsSection(runItemsService);
		runItemsSection.setAlignmentX(Component.LEFT_ALIGNMENT);
		column.add(runItemsSection);

		TemplatesSection templatesSection = new TemplatesSection(selectionService);
		templatesSection.setAlignmentX(Component.LEFT_ALIGNMENT);
		column.add(templatesSection);

		boolean perType = config.seedSelectionMode()
			== BetterFarmingConfig.SeedSelectionMode.PER_TYPE;
		for (PatchType type : DISPLAY_ORDER)
		{
			List<PatchGroup> typeGroups = byType.get(type);
			if (typeGroups == null || typeGroups.isEmpty()
				|| !BetterFarmingConfig.showType(config, type))
			{
				continue;
			}
			List<PatchGroupCard> cards = new ArrayList<>(typeGroups.size());
			for (PatchGroup g : typeGroups)
			{
				cards.add(new PatchGroupCard(g, selectionService, availabilityService,
					accessibilityService, !perType));
			}
			TypeSeedRow seedRow = perType
				? new TypeSeedRow(type, typeGroups, selectionService, availabilityService)
				: null;
			PatchTypeSection section = new PatchTypeSection(type, cards, seedRow);
			section.setAlignmentX(Component.LEFT_ALIGNMENT);
			column.add(section);
		}

		scroll.setViewportView(column);
		revalidate();
		repaint();
	}

	/** See Phase 1's BetterFarmingPanel for the rationale on why this exists. */
	private static final class ScrollableColumn extends JPanel implements Scrollable
	{
		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return orientation == SwingConstants.VERTICAL ? 16 : 8;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return orientation == SwingConstants.VERTICAL
				? Math.max(visibleRect.height - 16, 16)
				: Math.max(visibleRect.width - 16, 16);
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}
}
