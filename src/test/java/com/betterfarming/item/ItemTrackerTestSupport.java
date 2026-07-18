package com.betterfarming.item;

import net.runelite.api.Item;

/** Exposes ItemTracker's package-private update seam to tests in other packages. */
public final class ItemTrackerTestSupport
{
	public static final int CONTAINER_INVENTORY = ItemTracker.CONTAINER_INVENTORY;
	public static final int CONTAINER_EQUIPMENT = ItemTracker.CONTAINER_EQUIPMENT;
	public static final int CONTAINER_BANK = ItemTracker.CONTAINER_BANK;

	private ItemTrackerTestSupport()
	{
	}

	public static void updateContainer(ItemTracker tracker, int containerId, Item[] items)
	{
		tracker.updateContainer(containerId, items);
	}
}
