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
 * Sidebar section showing the run's CURRENT stop only — one detailed block
 * (location, patch type, suggested teleport, missing-item warning) that
 * advances as legs complete, instead of the full route list eating vertical
 * space. Stopped, it previews the first planned stop and the total; the
 * world-map route lines carry the whole-route picture. Legs whose teleport
 * items aren't on the player get a red "missing" annotation — visible while
 * banking, before the run strands. The header carries the run lifecycle
 * controls: Start/Stop (guidance is opt-in per run) and Skip (set the
 * current leg aside). RunOrderService may notify from the client thread, so
 * the listener hops to the EDT before rebuilding; ItemTracker's and
 * GuidanceService-via-invokeLater fanouts already land on the EDT.
 */
public class RunOrderSection extends JPanel
{
	private static final Color COLOR_TELEPORT = new Color(0x8A, 0xB4, 0xF8);
	private static final Color COLOR_MUTED = new Color(0x88, 0x88, 0x88);
	private static final Color COLOR_WARNING = new Color(0xE2, 0x6B, 0x6B);
	private static final Color COLOR_COMPLETE = new Color(0x6B, 0xE2, 0x6B);

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
		header.setFont(header.getFont().deriveFont(Font.BOLD, 16f));
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
		// Guidance changes drive both the button states (stopping from the
		// overlay's right-click menu must flip the sidebar button) and the
		// current-stop block (leg advances rebuild it).
		guidanceListener = () -> SwingUtilities.invokeLater(() -> {
			syncControls();
			rebuild(service.legs());
			revalidate();
			repaint();
		});
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
			empty.setFont(empty.getFont().deriveFont(12f));
			rowsContainer.add(empty);
			return;
		}
		boolean active = guidance.runActive();
		if (active && guidance.runComplete())
		{
			JLabel done = new JLabel("Farming run complete!");
			done.setName("runorder-complete");
			done.setForeground(COLOR_COMPLETE);
			done.setFont(done.getFont().deriveFont(Font.BOLD, 13f));
			rowsContainer.add(done);
			return;
		}

		// One detailed block for the current stop (next planned stop while
		// stopped) instead of the whole route — the run advances it leg by leg.
		RoutePlanner.Leg leg = active && guidance.currentLeg() != null
			? guidance.currentLeg() : legs.get(0);
		int position = active && guidance.currentIndex() > 0 ? guidance.currentIndex() : 1;
		rowsContainer.add(buildCurrentStop(leg, position, legs.size(), active));
	}

	private JPanel buildCurrentStop(RoutePlanner.Leg leg, int position, int total, boolean active)
	{
		JPanel block = new JPanel();
		block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
		block.setOpaque(false);
		block.setAlignmentX(Component.LEFT_ALIGNMENT);
		block.setName("runorder:" + leg.stop().groupKey());
		block.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

		JLabel status = new JLabel(active
			? "Stop " + position + " of " + total
			: total + " stops planned — press Start");
		status.setName("runorder-status");
		status.setForeground(COLOR_MUTED);
		status.setFont(status.getFont().deriveFont(12f));
		status.setAlignmentX(Component.LEFT_ALIGNMENT);
		block.add(status);

		// Bare place name; the navigational full name ("West of Port
		// Phasmatys") stays available as the tooltip.
		JLabel title = new JLabel(Locations.display(leg.stop().displayName()));
		title.setToolTipText(leg.stop().displayName());
		title.setForeground(Color.WHITE);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		block.add(title);

		String typeLabel = typeLabel(leg.stop().groupKey());
		if (typeLabel != null)
		{
			JLabel type = new JLabel(typeLabel + " patch");
			type.setForeground(COLOR_MUTED);
			type.setFont(type.getFont().deriveFont(12f));
			type.setAlignmentX(Component.LEFT_ALIGNMENT);
			block.add(type);
		}

		String how = leg.teleport() == null ? "Walk" : leg.teleport().displayLabel();
		String eta = leg.estimatedTicks() < 0 ? "no route" : "~" + formatTicks(leg.estimatedTicks());
		JLabel detail = new JLabel(how + "  (" + eta + ")");
		detail.setForeground(leg.teleport() == null ? COLOR_MUTED : COLOR_TELEPORT);
		detail.setFont(detail.getFont().deriveFont(13f));
		detail.setAlignmentX(Component.LEFT_ALIGNMENT);
		block.add(detail);

		List<String> missing = TeleportItemCheck.missingOnPlayer(leg.teleport(), itemTracker);
		if (!missing.isEmpty())
		{
			detail.setForeground(COLOR_WARNING);
			JLabel warning = new JLabel("missing: " + String.join(", ", missing));
			warning.setName("runorder-missing:" + leg.stop().groupKey());
			warning.setForeground(COLOR_WARNING);
			warning.setFont(warning.getFont().deriveFont(13f));
			warning.setAlignmentX(Component.LEFT_ALIGNMENT);
			block.add(warning);
		}
		return block;
	}

	/** Humanized patch type from the "TYPE|Location" group key, or null. */
	private static String typeLabel(String groupKey)
	{
		int sep = groupKey.indexOf('|');
		if (sep <= 0)
		{
			return null;
		}
		try
		{
			return PatchTypeSection.humanize(
				com.betterfarming.data.PatchType.valueOf(groupKey.substring(0, sep)));
		}
		catch (IllegalArgumentException ex)
		{
			return null;
		}
	}

	private static String formatTicks(int ticks)
	{
		int seconds = (int) Math.round(ticks * 0.6);
		return seconds < 60 ? seconds + "s" : (seconds / 60) + "m" + (seconds % 60) + "s";
	}
}
