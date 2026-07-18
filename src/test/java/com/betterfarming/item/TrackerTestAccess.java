package com.betterfarming.item;

import net.runelite.api.Item;

/**
 * Public bridge to ItemTracker's package-private test seam for tests living
 * outside com.betterfarming.item (e.g. travel service tests).
 */
public final class TrackerTestAccess
{
	public static final int INVENTORY = ItemTracker.CONTAINER_INVENTORY;
	public static final int EQUIPMENT = ItemTracker.CONTAINER_EQUIPMENT;
	public static final int BANK = ItemTracker.CONTAINER_BANK;

	private TrackerTestAccess()
	{
	}

	public static void update(ItemTracker tracker, int containerId, Item[] items)
	{
		tracker.updateContainer(containerId, items);
	}
}
