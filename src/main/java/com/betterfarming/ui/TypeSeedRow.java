package com.betterfarming.ui;

import com.betterfarming.data.Patch;
import com.betterfarming.data.PatchGroup;
import com.betterfarming.data.PatchType;
import com.betterfarming.data.Seed;
import com.betterfarming.state.PatchSelectionEvent;
import com.betterfarming.state.PatchSelectionService;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicComboBoxRenderer;

/**
 * The simple-mode seed picker: one dropdown per patch type that applies the
 * chosen seed to EVERY patch of that type, replacing the per-patch dropdowns
 * (BetterFarmingConfig.SeedSelectionMode.PER_TYPE). Selections still persist
 * per patch underneath, so run items, planting guidance and the bank tab are
 * untouched — and switching back to per-patch mode keeps what was applied.
 *
 * The dropdown shows the seed all patches share; any divergence (individual
 * choices made in per-patch mode) renders as the placeholder until a pick
 * overwrites them all.
 */
class TypeSeedRow extends JPanel
{
	private static final String CHOOSE_SEED = "Choose seed…";
	private static final String LOGGED_OUT = "Log in to choose seeds";
	private static final String LEVEL_TOO_LOW = "Need higher Farming level";

	private final PatchType type;
	private final List<Patch> patches;
	private final PatchSelectionService selectionService;
	private final SeedAvailabilityService availabilityService;
	private final JComboBox<Seed> dropdown;
	private final Runnable availabilityListener;
	private final Consumer<PatchSelectionEvent> selectionListener;

	private boolean updatingProgrammatically = false;

	TypeSeedRow(PatchType type, List<PatchGroup> groups,
		PatchSelectionService selectionService,
		SeedAvailabilityService availabilityService)
	{
		this.type = type;
		this.patches = new ArrayList<>();
		for (PatchGroup g : groups)
		{
			patches.addAll(g.patches());
		}
		this.selectionService = selectionService;
		this.availabilityService = availabilityService;

		setLayout(new FlowLayout(FlowLayout.LEFT, 8, 0));
		setOpaque(false);
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 14));

		JLabel label = new JLabel("Seed");
		label.setForeground(new Color(0xCC, 0xCC, 0xCC));
		label.setFont(label.getFont().deriveFont(12f));
		add(label);

		dropdown = new JComboBox<>();
		dropdown.setName("typeSeed:" + type.name());
		dropdown.setRenderer(new SeedRenderer());
		Dimension pref = new Dimension(150, dropdown.getPreferredSize().height);
		dropdown.setPreferredSize(pref);
		dropdown.setMinimumSize(pref);
		dropdown.setToolTipText("Applies to every " + PatchTypeSection.humanize(type).toLowerCase()
			+ " patch");
		dropdown.addActionListener(e -> {
			if (!updatingProgrammatically)
			{
				onSeedSelected();
			}
		});
		add(dropdown);

		repopulate(availabilityService.plantableSeeds(type));

		availabilityListener = this::onAvailabilityChanged;
		availabilityService.addListener(availabilityListener);
		selectionListener = this::onSelectionEvent;
		selectionService.addListener(selectionListener);
	}

	@Override
	public void removeNotify()
	{
		availabilityService.removeListener(availabilityListener);
		selectionService.removeListener(selectionListener);
		super.removeNotify();
	}

	private void onSeedSelected()
	{
		Seed picked = (Seed) dropdown.getSelectedItem();
		String seedId = picked == null ? null : picked.id();
		for (Patch p : patches)
		{
			selectionService.setSeed(p.id(), seedId);
		}
	}

	private void onAvailabilityChanged()
	{
		List<Seed> seeds = availabilityService.plantableSeeds(type);
		SwingUtilities.invokeLater(() -> repopulate(seeds));
	}

	private void onSelectionEvent(PatchSelectionEvent event)
	{
		// Cheap membership test: re-render on any change to one of our
		// patches (including our own writes — idempotent).
		for (Patch p : patches)
		{
			if (p.id().equals(event.patchId()))
			{
				SwingUtilities.invokeLater(this::renderCommonSelection);
				return;
			}
		}
	}

	private void repopulate(List<Seed> seeds)
	{
		DefaultComboBoxModel<Seed> model = new DefaultComboBoxModel<>();
		model.addElement(null);
		for (Seed s : seeds)
		{
			model.addElement(s);
		}
		updatingProgrammatically = true;
		try
		{
			dropdown.setModel(model);
		}
		finally
		{
			updatingProgrammatically = false;
		}
		renderCommonSelection();
	}

	/** The seed every patch of the type shares, or null (mixed/none). */
	private String commonSeedId()
	{
		String common = null;
		for (Patch p : patches)
		{
			String seedId = selectionService.get(p.id())
				.map(sel -> sel.seedId()).orElse(null);
			if (seedId == null)
			{
				return null;
			}
			if (common == null)
			{
				common = seedId;
			}
			else if (!Objects.equals(common, seedId))
			{
				return null;
			}
		}
		return common;
	}

	private void renderCommonSelection()
	{
		String seedId = commonSeedId();
		Seed match = null;
		if (seedId != null)
		{
			for (int i = 0; i < dropdown.getItemCount(); i++)
			{
				Seed item = dropdown.getItemAt(i);
				if (item != null && item.id().equals(seedId))
				{
					match = item;
					break;
				}
			}
		}
		updatingProgrammatically = true;
		try
		{
			dropdown.setSelectedItem(match);
		}
		finally
		{
			updatingProgrammatically = false;
		}
	}

	private class SeedRenderer extends BasicComboBoxRenderer
	{
		@Override
		public Component getListCellRendererComponent(javax.swing.JList<?> list,
			Object value, int index, boolean isSelected, boolean cellHasFocus)
		{
			super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			if (value == null)
			{
				setText(placeholderText());
				setForeground(new Color(0x66, 0x66, 0x66));
			}
			else
			{
				setText(((Seed) value).displayName());
				setForeground(Color.WHITE);
			}
			return this;
		}

		private String placeholderText()
		{
			if (!availabilityService.isLoggedIn())
			{
				return LOGGED_OUT;
			}
			if (availabilityService.plantableSeeds(type).isEmpty())
			{
				return LEVEL_TOO_LOW;
			}
			return CHOOSE_SEED;
		}
	}
}
