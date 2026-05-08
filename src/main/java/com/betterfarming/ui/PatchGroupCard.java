package com.betterfarming.ui;

import com.betterfarming.data.Patch;
import com.betterfarming.data.PatchGroup;
import com.betterfarming.state.GroupActiveEvent;
import com.betterfarming.state.PatchSelectionService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;

/**
 * One card per (type, location) group. Header: location title + active toggle.
 * Body: one PatchSubRow per patch in the group, in JSON file order.
 *
 * The card subscribes to GroupActiveEvent for its own groupKey and to
 * SeedAvailabilityService at the card level (delegates to sub-rows). Sub-rows
 * subscribe individually to PatchSelectionEvent for their own patchId.
 *
 * Phase 1.5 hooks setLocked(reason)/clearLocked() are implemented but have
 * no callers in Phase 1.1.
 */
public class PatchGroupCard extends JPanel
{
	private final PatchGroup group;
	private final PatchSelectionService selectionService;
	private final SeedAvailabilityService availabilityService;

	private final TruncatingLabel titleLabel;
	private final JButton toggleButton;
	private final JLabel infoIcon;
	private final JPanel rightSlot;
	private final List<PatchSubRow> subRows = new ArrayList<>();
	private final Consumer<GroupActiveEvent> groupListener;
	private final Runnable availabilityListener;

	private boolean lockedState = false;

	public PatchGroupCard(PatchGroup group,
		PatchSelectionService selectionService,
		SeedAvailabilityService availabilityService)
	{
		this.group = group;
		this.selectionService = selectionService;
		this.availabilityService = availabilityService;

		setLayout(new BorderLayout(0, 4));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		// Right inset is wider so the toggle and the seed dropdowns clear the
		// JScrollPane's vertical scrollbar (≈12-14 px).
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 14));
		setAlignmentX(Component.LEFT_ALIGNMENT);

		// ── header row: location title + active toggle ──
		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);

		titleLabel = new TruncatingLabel(group.location());
		titleLabel.setForeground(Color.WHITE);
		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));
		header.add(titleLabel, BorderLayout.CENTER);

		rightSlot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		rightSlot.setOpaque(false);

		toggleButton = new JButton();
		toggleButton.setName("groupToggle:" + group.key());
		toggleButton.setMargin(new Insets(0, 0, 0, 0));
		toggleButton.setPreferredSize(new Dimension(18, 18));
		toggleButton.setFocusPainted(false);
		toggleButton.setFont(toggleButton.getFont().deriveFont(Font.BOLD, 11f));
		toggleButton.addActionListener(e -> onToggleClicked());
		rightSlot.add(toggleButton);

		infoIcon = new JLabel("!");
		infoIcon.setName("info:" + group.key());
		infoIcon.setForeground(new Color(0xD4, 0xA0, 0x5C));
		infoIcon.setFont(infoIcon.getFont().deriveFont(Font.BOLD, 11f));
		infoIcon.setBorder(BorderFactory.createLineBorder(new Color(0xB8, 0x8A, 0x47)));
		infoIcon.setPreferredSize(new Dimension(18, 18));
		infoIcon.setHorizontalAlignment(JLabel.CENTER);
		infoIcon.setVisible(false);
		rightSlot.add(infoIcon);

		header.add(rightSlot, BorderLayout.EAST);

		// ── body: BoxLayout of PatchSubRows ──
		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setOpaque(false);

		for (Patch p : group.patches())
		{
			PatchSubRow row = new PatchSubRow(p, p.subPatchLabel(), selectionService, availabilityService);
			row.setAlignmentX(Component.LEFT_ALIGNMENT);
			subRows.add(row);
			body.add(row);
		}

		add(header, BorderLayout.NORTH);
		add(body, BorderLayout.CENTER);

		// ── initial render of the toggle ──
		renderActive(selectionService.isGroupActive(group.key()));

		// ── subscribe ──
		groupListener = this::onGroupActiveEvent;
		availabilityListener = this::onAvailabilityChanged;
		selectionService.addGroupListener(groupListener);
		availabilityService.addListener(availabilityListener);
	}

	@Override
	public void removeNotify()
	{
		selectionService.removeGroupListener(groupListener);
		availabilityService.removeListener(availabilityListener);
		super.removeNotify();
	}

	// ── Phase 1.5 hooks (no callers in Phase 1.1) ──

	public void setLocked(String reason)
	{
		lockedState = true;
		toggleButton.setVisible(false);
		infoIcon.setVisible(true);
		infoIcon.setToolTipText(reason);
		for (PatchSubRow row : subRows)
		{
			row.setEnabled(false);
		}
		titleLabel.setForeground(new Color(0x66, 0x66, 0x66));
		repaint();
	}

	public void clearLocked()
	{
		lockedState = false;
		infoIcon.setVisible(false);
		toggleButton.setVisible(true);
		for (PatchSubRow row : subRows)
		{
			row.setEnabled(true);
		}
		titleLabel.setForeground(Color.WHITE);
		repaint();
	}

	public boolean isLocked()
	{
		return lockedState;
	}

	// ── interaction ──

	private void onToggleClicked()
	{
		boolean now = !selectionService.isGroupActive(group.key());
		selectionService.setGroupActive(group.key(), now);
	}

	// ── service event handlers (hop to EDT) ──

	private void onGroupActiveEvent(GroupActiveEvent event)
	{
		if (!event.groupKey().equals(group.key()))
		{
			return;
		}
		SwingUtilities.invokeLater(() -> renderActive(event.newActive()));
	}

	private void onAvailabilityChanged()
	{
		// Compute once; push to all sub-rows on the EDT.
		List<com.betterfarming.data.Seed> seeds = availabilityService.plantableSeeds(group.type());
		SwingUtilities.invokeLater(() -> {
			for (PatchSubRow row : subRows)
			{
				row.repopulateDropdown(seeds);
			}
		});
	}

	// ── rendering ──

	private void renderActive(boolean active)
	{
		toggleButton.setText(active ? "✓" : "");
		toggleButton.setBackground(new Color(0x1A, 0x1A, 0x1A));
		toggleButton.setForeground(new Color(0xB8, 0x8A, 0x47));
	}

	/**
	 * JLabel that retruncates its text with a trailing "…" whenever its
	 * bounds change, exposing the full text via tooltip. Lifted from Phase
	 * 1's PatchCard so we can render a long location name (e.g. "South-west
	 * Hosidius") cleanly in a narrow sidebar.
	 */
	static final class TruncatingLabel extends JLabel
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
