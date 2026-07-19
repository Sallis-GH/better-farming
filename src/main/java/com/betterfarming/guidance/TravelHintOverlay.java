package com.betterfarming.guidance;

import com.betterfarming.BetterFarmingConfig;
import com.betterfarming.item.ItemTracker;
import com.betterfarming.item.TeleportItemCheck;
import com.betterfarming.travel.RoutePlanner;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * On-screen "what to do next" panel: current leg number, destination, and
 * either the travel instruction ("Cast Camelot Teleport", "Break Falador
 * teleport tablet") or — once at the patch — the work instruction ("Plant:
 * Falador herb patch"). Right-click (alt) the panel for "Reset farming run
 * progress" — useful when starting a second run in the same session.
 */
public class TravelHintOverlay extends OverlayPanel
{
	private static final Color COLOR_COMPLETE = new Color(0x6B, 0xE2, 0x6B);
	private static final Color COLOR_WARNING = new Color(0xE2, 0x6B, 0x6B);

	private final BetterFarmingConfig config;
	private final GuidanceService guidance;
	private final PlantingGuide plantingGuide;
	private final ItemTracker itemTracker;

	/**
	 * @param resetAction full run reset (replan + progress clear), invoked
	 *     from the overlay's right-click menu on the client thread.
	 */
	public TravelHintOverlay(BetterFarmingConfig config, GuidanceService guidance,
		PlantingGuide plantingGuide, ItemTracker itemTracker, Runnable resetAction)
	{
		this.config = config;
		this.guidance = guidance;
		this.plantingGuide = plantingGuide;
		this.itemTracker = itemTracker;
		setPosition(OverlayPosition.TOP_LEFT);
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Reset", "Farming run progress",
			e -> resetAction.run());
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
		String action = plantingGuide.actionText();
		if (action != null && plantingGuide.targetPatch() != null)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(action + ":")
				.right(plantingGuide.targetPatch().displayName())
				.rightColor(WorldArrowOverlay.ARROW_COLOR)
				.build());
		}
		else
		{
			// Running from here beats the planned teleport (the route priced
			// this leg from the previous stop): tell the player to walk.
			String travel = guidance.walkPreferred()
				? "Walk to " + leg.stop().displayName()
				: TravelHint.text(leg);
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Travel:")
				.right(travel)
				.rightColor(WorldArrowOverlay.ARROW_COLOR)
				.build());
			// Multi-hop legs additionally show the immediate step, so the
			// player follows one instruction at a time.
			com.betterfarming.travel.Teleport hop = guidance.travelHop();
			if (hop != null && leg.teleport() != null && leg.teleport().chainHops() != null)
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.left("Now:")
					.right(TravelHint.forTeleport(hop))
					.rightColor(Color.WHITE)
					.build());
			}
			// The planned teleport needs items the player isn't carrying
			// (tablet never withdrawn): warn before they're stranded. Not
			// shown while walking wins — nothing needs to be consumed then.
			if (!guidance.walkPreferred())
			{
				List<String> missing =
					TeleportItemCheck.missingOnPlayer(leg.teleport(), itemTracker);
				if (!missing.isEmpty())
				{
					panelComponent.getChildren().add(LineComponent.builder()
						.left("Missing:")
						.leftColor(COLOR_WARNING)
						.right(String.join(", ", missing))
						.rightColor(COLOR_WARNING)
						.build());
				}
			}
		}
		return super.render(graphics);
	}
}
