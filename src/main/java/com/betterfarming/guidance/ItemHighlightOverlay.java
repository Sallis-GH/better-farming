package com.betterfarming.guidance;

import com.betterfarming.BetterFarmingConfig;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Highlights items the guidance flow wants clicked next: the selected
 * seed/sapling (and rake) while planting at a patch, the teleport item while
 * travelling a leg. Which ids glow is decided per tick by PlantingGuide;
 * this overlay just paints them. Shown in the inventory and the worn
 * equipment tab — jewellery teleports (skills necklace, glory) are usually
 * equipped and cast from the equipment interface.
 */
public class ItemHighlightOverlay extends WidgetItemOverlay
{
	private static final Color FILL = new Color(0, 184, 255, 50);

	private final BetterFarmingConfig config;
	private final PlantingGuide guide;

	public ItemHighlightOverlay(BetterFarmingConfig config, PlantingGuide guide)
	{
		this.config = config;
		this.guide = guide;
		showOnInventory();
		showOnEquipment();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (!config.showPlantingHighlights() || !guide.highlightItemIds().contains(itemId))
		{
			return;
		}
		Rectangle bounds = widgetItem.getCanvasBounds();
		if (bounds == null)
		{
			return;
		}
		graphics.setColor(FILL);
		graphics.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 6, 6);
		graphics.setColor(WorldArrowOverlay.ARROW_COLOR);
		graphics.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 6, 6);
	}
}
