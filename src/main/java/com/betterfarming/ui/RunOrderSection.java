package com.betterfarming.ui;

import com.betterfarming.travel.RoutePlanner;
import com.betterfarming.travel.RunOrderService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;

/**
 * Sidebar section listing the planned run order: one row per active patch
 * group with the suggested teleport for that leg ("1. Trollheim — Stony
 * basalt"). RunOrderService may notify from the client thread, so the
 * listener hops to the EDT before rebuilding.
 */
public class RunOrderSection extends JPanel
{
	private static final Color COLOR_TELEPORT = new Color(0x8A, 0xB4, 0xF8);
	private static final Color COLOR_MUTED = new Color(0x88, 0x88, 0x88);

	private final RunOrderService service;
	private final JPanel rowsContainer;
	private final Runnable listener;

	public RunOrderSection(RunOrderService service)
	{
		this.service = service;

		setLayout(new BorderLayout());
		setOpaque(false);
		setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel header = new JLabel("Run order");
		header.setName("section-header:RUN_ORDER");
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

		rebuild(service.legs());

		listener = () -> SwingUtilities.invokeLater(() -> {
			rebuild(service.legs());
			revalidate();
			repaint();
		});
		service.addListener(listener);
	}

	@Override
	public void removeNotify()
	{
		service.removeListener(listener);
		super.removeNotify();
	}

	private void rebuild(List<RoutePlanner.Leg> legs)
	{
		rowsContainer.removeAll();
		if (legs.isEmpty())
		{
			JLabel empty = new JLabel("No active patches (or logged out)");
			empty.setName("runorder-empty");
			empty.setForeground(COLOR_MUTED);
			empty.setFont(empty.getFont().deriveFont(11f));
			rowsContainer.add(empty);
			return;
		}
		int i = 1;
		for (RoutePlanner.Leg leg : legs)
		{
			rowsContainer.add(buildRow(i++, leg));
		}
	}

	private JPanel buildRow(int index, RoutePlanner.Leg leg)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setName("runorder:" + leg.stop().groupKey());
		row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

		JLabel title = new JLabel(index + ". " + leg.stop().displayName());
		title.setForeground(Color.WHITE);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(title);

		String how = leg.teleport() == null ? "walk" : leg.teleport().displayLabel();
		JLabel detail = new JLabel("   " + how + "  (~" + formatTicks(leg.estimatedTicks()) + ")");
		detail.setForeground(leg.teleport() == null ? COLOR_MUTED : COLOR_TELEPORT);
		detail.setFont(detail.getFont().deriveFont(10f));
		detail.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(detail);
		return row;
	}

	private static String formatTicks(int ticks)
	{
		int seconds = (int) Math.round(ticks * 0.6);
		return seconds < 60 ? seconds + "s" : (seconds / 60) + "m" + (seconds % 60) + "s";
	}
}
