package com.betterfarming.guidance;

import com.betterfarming.BetterFarmingConfig;
import com.betterfarming.travel.RoutePlanner;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * On-screen "what to do next" panel: current leg number, destination, and the
 * travel instruction ("Cast Camelot Teleport", "Break Falador teleport
 * tablet"). Right-click (alt) the panel for "Reset farming run progress" —
 * useful when starting a second run in the same session.
 */
public class TravelHintOverlay extends OverlayPanel
{
	private static final Color COLOR_COMPLETE = new Color(0x6B, 0xE2, 0x6B);

	private final BetterFarmingConfig config;
	private final GuidanceService guidance;

	public TravelHintOverlay(BetterFarmingConfig config, GuidanceService guidance)
	{
		this.config = config;
		this.guidance = guidance;
		setPosition(OverlayPosition.TOP_LEFT);
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Reset", "Farming run progress",
			e -> guidance.reset());
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showTravelHint())
		{
			return null;
		}
		if (guidance.runComplete())
		{
			panelComponent.getChildren().add(TitleComponent.builder()
				.text("Farming run complete!")
				.color(COLOR_COMPLETE)
				.build());
			return super.render(graphics);
		}
		RoutePlanner.Leg leg = guidance.currentLeg();
		if (leg == null)
		{
			return null;
		}
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Farming run  " + guidance.currentIndex() + "/" + guidance.totalLegs())
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Next:")
			.right(leg.stop().displayName())
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Travel:")
			.right(TravelHint.text(leg))
			.rightColor(WorldArrowOverlay.ARROW_COLOR)
			.build());
		return super.render(graphics);
	}
}
