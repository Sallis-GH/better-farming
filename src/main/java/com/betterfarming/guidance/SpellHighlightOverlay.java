package com.betterfarming.guidance;

import com.betterfarming.BetterFarmingConfig;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Highlights the spellbook spell to cast for the current travel hop — the
 * click is the spell, not the runes it consumes. Only draws while the magic
 * tab is open (the widget exists and is visible); the travel-hint line
 * ("Cast Camelot Teleport") carries the instruction until then.
 */
public class SpellHighlightOverlay extends Overlay
{
	private static final Color FILL = new Color(0, 184, 255, 50);

	private final Client runeliteClient;
	private final BetterFarmingConfig config;
	private final GuidanceService guidance;

	public SpellHighlightOverlay(Client client, BetterFarmingConfig config, GuidanceService guidance)
	{
		this.runeliteClient = client;
		this.config = config;
		this.guidance = guidance;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showPlantingHighlights())
		{
			return null;
		}
		Integer widgetId = SpellWidgets.widgetFor(guidance.travelHop());
		if (widgetId == null)
		{
			return null;
		}
		Widget widget = runeliteClient.getWidget(widgetId);
		if (widget == null || widget.isHidden())
		{
			return null;
		}
		Rectangle bounds = widget.getBounds();
		graphics.setColor(FILL);
		graphics.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 6, 6);
		graphics.setColor(WorldArrowOverlay.ARROW_COLOR);
		graphics.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 6, 6);
		return null;
	}
}
