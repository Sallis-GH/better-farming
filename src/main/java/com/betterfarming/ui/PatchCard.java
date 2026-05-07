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
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

/**
 * One row in the sidebar: patch name + select toggle (top), location +
 * seed dropdown (bottom). Wires its own listeners on construction;
 * unsubscribes via removeNotify() when removed from its parent.
 *
 * Phase 1.5 hooks setLocked(reason)/clearLocked() are implemented but
 * have no callers in Phase 1.
 */
@Slf4j
public class PatchCard extends JPanel
{
	private static final String CHOOSE_SEED = "Choose seed…";
	private static final String LOGGED_OUT = "Log in to choose seeds";
	private static final String LEVEL_TOO_LOW = "Need higher Farming level";
	private static final Seed PLACEHOLDER_NONE = null; // null Seed → placeholder rendering

	private final Patch patch;
	private final PatchSelectionService selectionService;
	private final SeedAvailabilityService availabilityService;

	private final JButton toggleButton;
	private final JLabel infoIcon;          // for Phase 1.5 lock display
	private final JLabel locationLabel;
	private final JComboBox<Seed> seedDropdown;
	private final Consumer<PatchSelectionEvent> selectionListener;
	private final Runnable availabilityListener;

	private boolean lockedState = false;
	private boolean updatingDropdownProgrammatically = false;

	public PatchCard(Patch patch,
		PatchSelectionService selectionService,
		SeedAvailabilityService availabilityService)
	{
		this.patch = patch;
		this.selectionService = selectionService;
		this.availabilityService = availabilityService;

		setLayout(new BorderLayout(0, 4));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		// Right inset is wider than the others so the toggle and the seed
		// dropdown clear the JScrollPane's vertical scrollbar (≈12-14 px).
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 14));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		// ── header row: name + toggle/info ──
		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);

		// TruncatingLabel handles long patch names deterministically: it
		// re-truncates with a trailing "…" on every setBounds, and exposes
		// the full text via tooltip on hover.
		TruncatingLabel nameLabel = new TruncatingLabel(patch.displayName());
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));
		header.add(nameLabel, BorderLayout.CENTER);

		JPanel rightSlot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		rightSlot.setOpaque(false);

		toggleButton = new JButton();
		toggleButton.setName("toggle:" + patch.id());
		toggleButton.setMargin(new Insets(0, 0, 0, 0));
		toggleButton.setPreferredSize(new Dimension(18, 18));
		toggleButton.setFocusPainted(false);
		toggleButton.setFont(toggleButton.getFont().deriveFont(Font.BOLD, 11f));
		toggleButton.addActionListener(e -> onToggleClicked());
		rightSlot.add(toggleButton);

		infoIcon = new JLabel("!");
		infoIcon.setName("info:" + patch.id());
		infoIcon.setForeground(new Color(0xD4, 0xA0, 0x5C));
		infoIcon.setFont(infoIcon.getFont().deriveFont(Font.BOLD, 11f));
		infoIcon.setBorder(BorderFactory.createLineBorder(new Color(0xB8, 0x8A, 0x47)));
		infoIcon.setPreferredSize(new Dimension(18, 18));
		infoIcon.setHorizontalAlignment(JLabel.CENTER);
		infoIcon.setVisible(false);
		rightSlot.add(infoIcon);

		header.add(rightSlot, BorderLayout.EAST);

		// ── body row: location + seed dropdown ──
		// Layout note: BorderLayout.WEST sizes WEST to its preferred width,
		// which for a long location string (e.g. "Tree Gnome Stronghold")
		// can exceed available width and overlap the EAST slot. Putting
		// location at CENTER instead lets it shrink to whatever's left
		// after the dropdown's fixed slot, with TruncatingLabel adding "…".
		JPanel body = new JPanel(new BorderLayout(8, 0));
		body.setOpaque(false);

		locationLabel = new TruncatingLabel(patch.location());
		locationLabel.setForeground(new Color(0x99, 0x99, 0x99));
		locationLabel.setFont(locationLabel.getFont().deriveFont(11f));
		body.add(locationLabel, BorderLayout.CENTER);

		seedDropdown = new JComboBox<>();
		seedDropdown.setName("seed:" + patch.id());
		seedDropdown.setRenderer(new SeedRenderer());
		// Pin the dropdown's width so its slot doesn't grow with placeholder
		// text length ("Need higher Farming level" would otherwise stretch
		// the EAST slot and crowd out the location).
		Dimension dropPref = new Dimension(110, seedDropdown.getPreferredSize().height);
		seedDropdown.setPreferredSize(dropPref);
		seedDropdown.setMinimumSize(dropPref);
		seedDropdown.addActionListener(e -> {
			if (!updatingDropdownProgrammatically)
			{
				onSeedSelected();
			}
		});
		body.add(seedDropdown, BorderLayout.EAST);

		add(header, BorderLayout.NORTH);
		add(body, BorderLayout.CENTER);

		// ── initial render ──
		renderSelection(selectionService.get(patch.id()).orElse(null));
		renderDropdown();

		// ── subscribe ──
		selectionListener = this::onSelectionEvent;
		availabilityListener = this::onAvailabilityChanged;
		selectionService.addListener(selectionListener);
		availabilityService.addListener(availabilityListener);
	}

	@Override
	public void removeNotify()
	{
		selectionService.removeListener(selectionListener);
		availabilityService.removeListener(availabilityListener);
		super.removeNotify();
	}

	// ── Phase 1.5 hooks (no callers in Phase 1) ──

	public void setLocked(String reason)
	{
		lockedState = true;
		toggleButton.setVisible(false);
		infoIcon.setVisible(true);
		infoIcon.setToolTipText(reason);
		seedDropdown.setEnabled(false);
		// Reduce visual prominence
		for (Component c : new Component[] { locationLabel })
		{
			c.setForeground(new Color(0x66, 0x66, 0x66));
		}
		repaint();
	}

	public void clearLocked()
	{
		lockedState = false;
		infoIcon.setVisible(false);
		toggleButton.setVisible(true);
		seedDropdown.setEnabled(true);
		locationLabel.setForeground(new Color(0x99, 0x99, 0x99));
		repaint();
	}

	public boolean isLocked()
	{
		return lockedState;
	}

	// ── interaction handlers ──

	private void onToggleClicked()
	{
		boolean nowSelected = !selectionService.get(patch.id())
			.map(PatchSelection::selected).orElse(false);
		selectionService.setSelected(patch.id(), nowSelected);
	}

	private void onSeedSelected()
	{
		Seed picked = (Seed) seedDropdown.getSelectedItem();
		selectionService.setSeed(patch.id(), picked == null ? null : picked.id());
	}

	// ── service event handlers (hop to EDT) ──

	private void onSelectionEvent(PatchSelectionEvent event)
	{
		if (!event.patchId().equals(patch.id()))
		{
			return;
		}
		SwingUtilities.invokeLater(() -> renderSelection(event.newSelection()));
	}

	private void onAvailabilityChanged()
	{
		SwingUtilities.invokeLater(this::renderDropdown);
	}

	// ── rendering ──

	private void renderSelection(PatchSelection selection)
	{
		boolean selected = selection != null && selection.selected();
		toggleButton.setText(selected ? "✓" : "");
		toggleButton.setBackground(selected
			? new Color(0x2A, 0x3A, 0x2A)
			: new Color(0x1A, 0x1A, 0x1A));
		toggleButton.setForeground(new Color(0xB8, 0x8A, 0x47));

		// Restore the previously-chosen seed (if any) without firing the action listener
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

	private void renderDropdown()
	{
		List<Seed> seeds = availabilityService.plantableSeeds(patch.type());
		DefaultComboBoxModel<Seed> model = new DefaultComboBoxModel<>();
		model.addElement(PLACEHOLDER_NONE); // first item is null → "Choose seed…"
		for (Seed s : seeds)
		{
			model.addElement(s);
		}

		updatingDropdownProgrammatically = true;
		try
		{
			seedDropdown.setModel(model);
			// Restore previous selection if it's still in the list
			renderSelection(selectionService.get(patch.id()).orElse(null));
		}
		finally
		{
			updatingDropdownProgrammatically = false;
		}
	}

	/**
	 * JComboBox renderer: null Seed shows a context-appropriate placeholder
	 * — "Log in to choose seeds" when logged out, "Need higher Farming level"
	 * when logged in but no seeds of this patch type are plantable yet, or
	 * "Choose seed…" when seeds are available but none picked.
	 */
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

	/**
	 * JLabel that retruncates its text with a trailing "…" whenever its
	 * bounds change. Avoids relying on BasicLabelUI's auto-truncation
	 * (which is L&F-dependent and didn't show a visible ellipsis under
	 * RuneLite's theme) and exposes the full text via tooltip.
	 *
	 * Stores the original text in {@code fullText} so {@link #setText}
	 * can be called externally without losing the truncation source. The
	 * {@code updating} guard prevents the {@code super.setText} call
	 * inside {@link #retruncate} from re-entering the public override.
	 */
	private static final class TruncatingLabel extends JLabel
	{
		private static final String ELLIPSIS = "…";

		private String fullText = "";
		private boolean updating = false;

		TruncatingLabel(String text)
		{
			setText(text);
		}

		@Override
		public void setText(String text)
		{
			if (updating)
			{
				super.setText(text);
				return;
			}
			fullText = text == null ? "" : text;
			setToolTipText(fullText.isEmpty() ? null : fullText);
			super.setText(fullText);
			retruncate();
		}

		@Override
		public void setBounds(int x, int y, int width, int height)
		{
			super.setBounds(x, y, width, height);
			retruncate();
		}

		private void retruncate()
		{
			if (fullText.isEmpty() || getFont() == null)
			{
				return;
			}
			FontMetrics fm = getFontMetrics(getFont());
			Insets insets = getInsets();
			int avail = getWidth() - insets.left - insets.right;
			if (avail <= 0)
			{
				return;
			}
			String target;
			if (fm.stringWidth(fullText) <= avail)
			{
				target = fullText;
			}
			else
			{
				int budget = avail - fm.stringWidth(ELLIPSIS);
				int len = fullText.length();
				while (len > 0 && fm.stringWidth(fullText.substring(0, len)) > budget)
				{
					len--;
				}
				target = len == 0 ? "" : fullText.substring(0, len) + ELLIPSIS;
			}
			if (!target.equals(getText()))
			{
				updating = true;
				try
				{
					super.setText(target);
				}
				finally
				{
					updating = false;
				}
			}
		}
	}
}
