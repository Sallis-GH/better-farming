package com.betterfarming.ui;

import com.betterfarming.guidance.GuidanceService;
import com.betterfarming.item.ItemTracker;
import com.betterfarming.item.TeleportItemCheck;
import com.betterfarming.travel.RoutePlanner;
import com.betterfarming.travel.RunOrderService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;

/**
 * Sidebar section listing the planned run order: one row per active patch
 * group with the suggested teleport for that leg ("1. Trollheim — Stony
 * basalt"). Legs whose teleport items aren't on the player get a red
 * "missing" annotation — visible while banking, before the run strands.
 * The header carries the run lifecycle controls: Start/Stop (guidance is
 * opt-in per run) and Skip (check off the current leg without visiting).
 * RunOrderService may notify from the client thread, so the listener hops to
 * the EDT before rebuilding; ItemTracker's fanout already lands on the EDT.
 */
public class RunOrderSection extends JPanel
{
	private static final Color COLOR_TELEPORT = new Color(0x8A, 0xB4, 0xF8);
	private static final Color COLOR_MUTED = new Color(0x88, 0x88, 0x88);
	private static final Color COLOR_WARNING = new Color(0xE2, 0x6B, 0x6B);

	private final RunOrderService service;
	private final ItemTracker itemTracker;
	private final GuidanceService guidance;
	private final JPanel rowsContainer;
	private final JButton startStopButton;
	private final JButton skipButton;
	private final Runnable listener;
	private final Runnable trackerListener;
	private final Runnable guidanceListener;

	public RunOrderSection(RunOrderService service, ItemTracker itemTracker,
		GuidanceService guidance)
	{
		this.service = service;
		this.itemTracker = itemTracker;
		this.guidance = guidance;

		setLayout(new BorderLayout());
		setOpaque(false);
		setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel header = new JLabel("Run order");
		header.setName("section-header:RUN_ORDER");
		header.setForeground(Color.WHITE);
		header.setFont(header.getFont().deriveFont(Font.BOLD, 14f));
		header.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 16));

		startStopButton = new JButton();
		startStopButton.setName("runorder-startstop");
		startStopButton.setFont(startStopButton.getFont().deriveFont(11f));
		startStopButton.addActionListener(e -> {
			guidance.setRunActive(!guidance.runActive());
			syncControls();
		});
		skipButton = new JButton("Skip");
		skipButton.setName("runorder-skip");
		skipButton.setFont(skipButton.getFont().deriveFont(11f));
		skipButton.setToolTipText(
			"Set the current leg aside — it returns at the end of the run and on resume");
		skipButton.addActionListener(e -> guidance.requestSkipCurrentLeg());

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
		buttons.setOpaque(false);
		buttons.add(skipButton);
		buttons.add(startStopButton);

		JPanel headerPanel = new JPanel(new BorderLayout());
		headerPanel.setOpaque(true);
		headerPanel.setBackground(new Color(0x26, 0x26, 0x26));
		headerPanel.add(header, BorderLayout.WEST);
		headerPanel.add(buttons, BorderLayout.EAST);
		syncControls();

		rowsContainer = new JPanel();
		rowsContainer.setLayout(new BoxLayout(rowsContainer, BoxLayout.Y_AXIS));
		rowsContainer.setOpaque(true);
		rowsContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		rowsContainer.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 14));

		add(headerPanel, BorderLayout.NORTH);
		add(rowsContainer, BorderLayout.CENTER);

		rebuild(service.legs());

		listener = () -> SwingUtilities.invokeLater(() -> {
			rebuild(service.legs());
			revalidate();
			repaint();
		});
		service.addListener(listener);
		// Withdrawing a tablet must clear its red row without waiting for a
		// route change; ItemTracker notifies on the EDT already.
		trackerListener = () -> {
			rebuild(service.legs());
			revalidate();
			repaint();
		};
		itemTracker.addListener(trackerListener);
		// Stopping from the overlay's right-click menu (client thread) must
		// flip the sidebar button too.
		guidanceListener = () -> SwingUtilities.invokeLater(this::syncControls);
		guidance.addListener(guidanceListener);
	}

	/** Button state from the lifecycle: label toggles, Skip needs a run. */
	private void syncControls()
	{
		boolean active = guidance.runActive();
		startStopButton.setText(active ? "Stop" : "Start");
		skipButton.setEnabled(active);
	}

	@Override
	public void removeNotify()
	{
		service.removeListener(listener);
		itemTracker.removeListener(trackerListener);
		guidance.removeListener(guidanceListener);
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
		String eta = leg.estimatedTicks() < 0 ? "no route" : "~" + formatTicks(leg.estimatedTicks());
		JLabel detail = new JLabel("   " + how + "  (" + eta + ")");
		detail.setForeground(leg.teleport() == null ? COLOR_MUTED : COLOR_TELEPORT);
		detail.setFont(detail.getFont().deriveFont(10f));
		detail.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(detail);

		List<String> missing = TeleportItemCheck.missingOnPlayer(leg.teleport(), itemTracker);
		if (!missing.isEmpty())
		{
			detail.setForeground(COLOR_WARNING);
			JLabel warning = new JLabel("   missing: " + String.join(", ", missing));
			warning.setName("runorder-missing:" + leg.stop().groupKey());
			warning.setForeground(COLOR_WARNING);
			warning.setFont(warning.getFont().deriveFont(10f));
			warning.setAlignmentX(Component.LEFT_ALIGNMENT);
			row.add(warning);
		}
		return row;
	}

	private static String formatTicks(int ticks)
	{
		int seconds = (int) Math.round(ticks * 0.6);
		return seconds < 60 ? seconds + "s" : (seconds / 60) + "m" + (seconds % 60) + "s";
	}
}
