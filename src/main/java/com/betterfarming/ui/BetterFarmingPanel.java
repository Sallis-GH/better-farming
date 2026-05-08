package com.betterfarming.ui;

import com.betterfarming.data.FarmingData;
import com.betterfarming.state.PatchSelectionService;
import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.JLabel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Top-level sidebar panel. Phase 1.1 work-in-progress: contents are filled
 * in by the new PatchGroupCard wiring (see plan Task 13). Until then, the
 * panel renders only a placeholder so the plugin starts up cleanly.
 */
public class BetterFarmingPanel extends PluginPanel
{
	public BetterFarmingPanel(FarmingData data,
		PatchSelectionService selectionService,
		SeedAvailabilityService availabilityService)
	{
		super(false);
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(new JLabel("Phase 1.1 in progress…"), BorderLayout.CENTER);
		setPreferredSize(new Dimension(225, 600));
	}
}
