package com.betterfarming.travel;

import com.betterfarming.item.ItemTracker;
import java.util.function.ToIntFunction;

/**
 * Live inventory-slot estimate for a teleport's item needs: a requirement
 * already satisfied by something the player is wearing (skills necklace,
 * Ardougne cloak, a staff supplying runes) costs no inventory space during
 * the harvest; anything else costs one slot per AND-term.
 *
 * Read on the client thread during route planning; ItemTracker's caches are
 * mutated there too.
 */
public final class TeleportSlotCost
{
	private TeleportSlotCost()
	{
	}

	public static ToIntFunction<Teleport> of(ItemTracker itemTracker)
	{
		return teleport -> {
			int slots = 0;
			for (TeleportItemRequirement req : teleport.items())
			{
				if (itemTracker.anyEquipped(req.itemIds())
					|| itemTracker.anyEquipped(req.staffIds())
					|| itemTracker.anyEquipped(req.offhandIds()))
				{
					continue;
				}
				slots++;
			}
			return slots;
		};
	}
}
