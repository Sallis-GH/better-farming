package com.betterfarming.ui;

import com.betterfarming.item.RunItem;
import com.betterfarming.item.RunItemsService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/**
 * Sidebar section listing the items needed for the currently active run,
 * color-coded by where each item is: green = on player, yellow = in bank,
 * red = missing. Rebuilt wholesale on every RunItemsService notification —
 * the list is small (≤ ~25 rows), so diffing isn't worth the complexity.
 *
 * RunItemsService notifies on the EDT (all its upstream sources fan out
 * there), so onItemsChanged mutates Swing state directly.
 */
public class RunItemsSection extends JPanel
{
	private static final Color COLOR_ON_PLAYER = new Color(0x4C, 0xAF, 0x50);
	private static final Color COLOR_IN_BANK = new Color(0xC8, 0xA0, 0x00);
	private static final Color COLOR_MISSING = new Color(0xE5, 0x57, 0x51);
	private static final Color COLOR_RECOMMENDED_NOTE = new Color(0x88, 0x88, 0x88);

	private final RunItemsService service;
	private final JPanel rowsContainer;
	private final Runnable listener;

	public RunItemsSection(RunItemsService service)
	{
		this.service = service;

		setLayout(new BorderLayout());
		setOpaque(false);
		setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel header = new JLabel("Run items");
		header.setName("section-header:RUN_ITEMS");
		header.setForeground(Color.WHITE);
		header.setFont(header.getFont().deriveFont(Font.BOLD, 14f));
		header.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 16));
		header.setOpaque(true);
		header.setBackground(new Color(0x26, 0x26, 0x26));

		rowsContainer = new JPanel();
		rowsContainer.setLayout(new BoxLayout(rowsContainer, BoxLayout.Y_AXIS));
		rowsContainer.setOpaque(true);
		rowsContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		rowsContainer.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 14));

		add(header, BorderLayout.NORTH);
		add(rowsContainer, BorderLayout.CENTER);

		rebuild(service.items());

		listener = this::onItemsChanged;
		service.addListener(listener);
	}

	@Override
	public void removeNotify()
	{
		service.removeListener(listener);
		super.removeNotify();
	}

	private void onItemsChanged()
	{
		rebuild(service.items());
		revalidate();
		repaint();
	}

	private void rebuild(List<RunItem> items)
	{
		rowsContainer.removeAll();
		if (items.isEmpty())
		{
			JLabel empty = new JLabel("No active patches");
			empty.setName("runitems-empty");
			empty.setForeground(COLOR_RECOMMENDED_NOTE);
			empty.setFont(empty.getFont().deriveFont(11f));
			rowsContainer.add(empty);
			return;
		}
		for (RunItem item : items)
		{
			rowsContainer.add(buildRow(item));
		}
	}

	private JPanel buildRow(RunItem item)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setName("runitem:" + item.displayName());

		Color color;
		String statusText;
		switch (item.status())
		{
			case ON_PLAYER:
				color = COLOR_ON_PLAYER;
				statusText = "On player";
				break;
			case IN_BANK:
				color = COLOR_IN_BANK;
				statusText = "In bank";
				break;
			default:
				color = COLOR_MISSING;
				statusText = "Missing";
				break;
		}

		JLabel dot = new JLabel("●");
		dot.setForeground(color);
		dot.setFont(dot.getFont().deriveFont(11f));
		row.add(dot, BorderLayout.WEST);

		// Outfit rows (pieces != null) read better without a ×N suffix.
		String text = item.displayName()
			+ (item.quantity() > 1 && item.pieces() == null ? " ×" + item.quantity() : "");
		JLabel label = new JLabel(text);
		label.setForeground(Color.WHITE);
		label.setFont(label.getFont().deriveFont(11f));
		String tooltip = statusText + (item.recommended() ? " — recommended, not required" : "");
		label.setToolTipText(tooltip);
		row.add(label, BorderLayout.CENTER);

		if (item.recommended())
		{
			JLabel note = new JLabel("(rec)");
			note.setForeground(COLOR_RECOMMENDED_NOTE);
			note.setFont(note.getFont().deriveFont(10f));
			note.setToolTipText(tooltip);
			row.add(note, BorderLayout.EAST);
		}
		return row;
	}
}
