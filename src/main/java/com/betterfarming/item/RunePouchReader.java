package com.betterfarming.item;

import com.betterfarming.ui.ClientLevelSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.EnumID;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;

/**
 * Mirrors the rune pouch contents into ItemTracker so spell requirements see
 * pouch runes. The pouch stores up to six (type, quantity) varbit pairs; the
 * type value maps to an item id through the RUNEPOUCH_RUNE cache enum — the
 * same mechanism RuneLite's own runepouch plugin uses. There is no public
 * "can this spell cast" API (that logic is clientscript-side), so counting
 * runes — pouch included — is also how cast counts are answered: casts =
 * min(available / cost) over the spell's rune requirements, which the
 * existing requirement checks already compute.
 *
 * Threading: VarbitChanged arrives on the client thread; refresh() reads
 * varbits and the cache enum there. ItemTracker's fanout does its own EDT
 * hop.
 */
public class RunePouchReader
{
	private static final int[] TYPE_VARBITS = {
		VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2,
		VarbitID.RUNE_POUCH_TYPE_3, VarbitID.RUNE_POUCH_TYPE_4,
		VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6};

	private static final int[] QUANTITY_VARBITS = {
		VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2,
		VarbitID.RUNE_POUCH_QUANTITY_3, VarbitID.RUNE_POUCH_QUANTITY_4,
		VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6};

	private static final Set<Integer> POUCH_VARBITS;

	static
	{
		Set<Integer> ids = new java.util.HashSet<>();
		for (int id : TYPE_VARBITS)
		{
			ids.add(id);
		}
		for (int id : QUANTITY_VARBITS)
		{
			ids.add(id);
		}
		POUCH_VARBITS = Set.copyOf(ids);
	}

	private final ClientLevelSource client;
	private final ItemTracker tracker;

	public RunePouchReader(ClientLevelSource client, ItemTracker tracker)
	{
		this.client = client;
		this.tracker = tracker;
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (POUCH_VARBITS.contains(event.getVarbitId()))
		{
			refresh();
		}
	}

	/** Re-reads all six slots and pushes the snapshot; client thread only. */
	public void refresh()
	{
		Map<Integer, Integer> contents = new HashMap<>();
		for (int slot = 0; slot < TYPE_VARBITS.length; slot++)
		{
			int type = client.getVarbitValue(TYPE_VARBITS[slot]);
			int quantity = client.getVarbitValue(QUANTITY_VARBITS[slot]);
			if (type <= 0 || quantity <= 0)
			{
				continue;
			}
			int itemId = client.getEnumValue(EnumID.RUNEPOUCH_RUNE, type);
			if (itemId > 0)
			{
				contents.merge(itemId, quantity, Integer::sum);
			}
		}
		tracker.updateRunePouch(contents);
	}
}
