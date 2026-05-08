package com.betterfarming.ui;

import com.betterfarming.data.Patch;
import com.betterfarming.data.Seed;
import com.betterfarming.state.PatchSelection;
import com.betterfarming.state.PatchSelectionEvent;
import com.betterfarming.state.PatchSelectionService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicComboBoxRenderer;

/**
 * One row inside a PatchGroupCard. Layout: [subLabel] (filler) [seedDropdown].
 * For singletons, subLabel is null and the WEST slot is omitted.
 *
 * The row subscribes only to PatchSelectionEvent for its own patchId. The
 * parent PatchGroupCard owns the SeedAvailabilityService listener and pushes
 * dropdown updates via repopulateDropdown(...). Lifecycle: subscribe in
 * constructor, unsubscribe via removeNotify().
 */
class PatchSubRow extends JPanel
{
	private static final String CHOOSE_SEED = "Choose seed…";
	private static final String LOGGED_OUT = "Log in to choose seeds";
	private static final String LEVEL_TOO_LOW = "Need higher Farming level";
	private static final Map<String, String> LONG_FORM = new HashMap<>();
	static
	{
		LONG_FORM.put("N", "north");
		LONG_FORM.put("S", "south");
		LONG_FORM.put("E", "east");
		LONG_FORM.put("W", "west");
		LONG_FORM.put("NE", "north east");
		LONG_FORM.put("NW", "north west");
		LONG_FORM.put("SE", "south east");
		LONG_FORM.put("SW", "south west");
		LONG_FORM.put("MID", "middle");
	}

	private final Patch patch;
	private final PatchSelectionService selectionService;
	private final SeedAvailabilityService availabilityService;

	private final JComboBox<Seed> seedDropdown;
	private final Consumer<PatchSelectionEvent> selectionListener;

	private boolean updatingDropdownProgrammatically = false;

	PatchSubRow(Patch patch, String subLabel,
		PatchSelectionService selectionService,
		SeedAvailabilityService availabilityService)
	{
		this.patch = patch;
		this.selectionService = selectionService;
		this.availabilityService = availabilityService;

		setLayout(new BorderLayout(8, 0));
		setOpaque(false);
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

		if (subLabel != null && !subLabel.isEmpty())
		{
			JLabel label = new JLabel(subLabel);
			label.setName("subLabel:" + patch.id());
			label.setForeground(new Color(0xCC, 0xCC, 0xCC));
			label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
			label.setPreferredSize(new Dimension(28, label.getPreferredSize().height));
			String longForm = LONG_FORM.get(subLabel);
			if (longForm != null)
			{
				label.setToolTipText(longForm);
			}
			add(label, BorderLayout.WEST);
		}

		seedDropdown = new JComboBox<>();
		seedDropdown.setName("seed:" + patch.id());
		seedDropdown.setRenderer(new SeedRenderer());
		Dimension dropPref = new Dimension(110, seedDropdown.getPreferredSize().height);
		seedDropdown.setPreferredSize(dropPref);
		seedDropdown.setMinimumSize(dropPref);
		seedDropdown.addActionListener(e -> {
			if (!updatingDropdownProgrammatically)
			{
				onSeedSelected();
			}
		});
		add(seedDropdown, BorderLayout.EAST);

		// Initial render: populate dropdown and seat any saved selection.
		repopulateDropdown(availabilityService.plantableSeeds(patch.type()));
		renderSelection(selectionService.get(patch.id()).orElse(null));

		// Subscribe.
		selectionListener = this::onSelectionEvent;
		selectionService.addListener(selectionListener);
	}

	@Override
	public void removeNotify()
	{
		selectionService.removeListener(selectionListener);
		super.removeNotify();
	}

	void repopulateDropdown(List<Seed> seeds)
	{
		DefaultComboBoxModel<Seed> model = new DefaultComboBoxModel<>();
		model.addElement(null);
		for (Seed s : seeds)
		{
			model.addElement(s);
		}
		updatingDropdownProgrammatically = true;
		try
		{
			seedDropdown.setModel(model);
			renderSelection(selectionService.get(patch.id()).orElse(null));
		}
		finally
		{
			updatingDropdownProgrammatically = false;
		}
	}

	@Override
	public void setEnabled(boolean enabled)
	{
		super.setEnabled(enabled);
		seedDropdown.setEnabled(enabled);
	}

	private void onSeedSelected()
	{
		Seed picked = (Seed) seedDropdown.getSelectedItem();
		selectionService.setSeed(patch.id(), picked == null ? null : picked.id());
	}

	private void onSelectionEvent(PatchSelectionEvent event)
	{
		if (!event.patchId().equals(patch.id()))
		{
			return;
		}
		SwingUtilities.invokeLater(() -> renderSelection(event.newSelection()));
	}

	private void renderSelection(PatchSelection selection)
	{
		String seedId = selection == null ? null : selection.seedId();
		Seed match = null;
		if (seedId != null)
		{
			for (int i = 0; i < seedDropdown.getItemCount(); i++)
			{
				Seed item = seedDropdown.getItemAt(i);
				if (item != null && item.id().equals(seedId))
				{
					match = item;
					break;
				}
			}
		}
		updatingDropdownProgrammatically = true;
		try
		{
			seedDropdown.setSelectedItem(match);
		}
		finally
		{
			updatingDropdownProgrammatically = false;
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
			if (availabilityService.plantableSeeds(patch.type()).isEmpty())
			{
				return LEVEL_TOO_LOW;
			}
			return CHOOSE_SEED;
		}
	}
}
