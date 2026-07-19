package com.betterfarming.item;

import com.betterfarming.travel.Teleport;
import com.betterfarming.travel.TeleportItemRequirement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Per-leg check of a teleport's item requirements against what the player is
 * actually carrying, answering "can I execute this leg right now?" — distinct
 * from the run-items list, which sums needs across the whole run and also
 * looks in the bank. Used for the travel-hint warning line and the red run
 * order rows: a planned leg whose tablet was never withdrawn should say so
 * before the player stands at a patch with no way onward.
 *
 * Thread-agnostic: callers read ItemTracker from the client thread (overlay
 * render) or the EDT (sidebar rebuild), both established patterns.
 */
public final class TeleportItemCheck
{
	private TeleportItemCheck()
	{
	}

	/**
	 * Display names of the teleport's item requirements not satisfied by the
	 * player's inventory/equipment, in requirement order; empty for walk legs
	 * and fully-covered teleports. Chain composites check their merged
	 * requirements (two 2 500-coin fares need 5 000 coins carried, not 2 500
	 * twice) but label each shortfall by the hop that owns it.
	 */
	public static List<String> missingOnPlayer(Teleport teleport, ItemTracker tracker)
	{
		if (teleport == null)
		{
			return List.of();
		}
		List<String> missing = new ArrayList<>();
		for (TeleportItemRequirement req : teleport.items())
		{
			if (satisfiedOnPlayer(req, tracker))
			{
				continue;
			}
			String name = displayName(req, owningUnit(teleport, req));
			if (!missing.contains(name))
			{
				missing.add(name);
			}
		}
		return missing;
	}

	/**
	 * A requirement is on the player when a staff/offhand substitute is
	 * carried (rune requirements) or the item count meets the quantity.
	 */
	public static boolean satisfiedOnPlayer(TeleportItemRequirement req, ItemTracker tracker)
	{
		if (tracker.countOnPlayer(boxSet(req.staffIds())) > 0
			|| tracker.countOnPlayer(boxSet(req.offhandIds())) > 0)
		{
			return true;
		}
		return tracker.countOnPlayer(boxSet(req.itemIds())) >= req.quantity();
	}

	/**
	 * Requirements written as raw item ids in the transport data (teleport
	 * tabs, jewellery) prettify to "Item 8007" — the teleport's own name
	 * ("Varrock tablet") is the better label. Trailing ": destination"
	 * qualifiers are dropped.
	 */
	public static String displayName(TeleportItemRequirement req, Teleport teleport)
	{
		if (!req.name().startsWith("Item ") || teleport.displayInfo() == null)
		{
			return req.name();
		}
		String info = teleport.displayInfo();
		int colon = info.indexOf(':');
		return colon > 0 ? info.substring(0, colon) : info;
	}

	/**
	 * The chain hop a merged requirement came from (matched by item ids), for
	 * labelling — the composite's own display is the full joined chain, far
	 * too long for a warning line. Plain teleports own their requirements.
	 */
	private static Teleport owningUnit(Teleport teleport, TeleportItemRequirement req)
	{
		if (teleport.chainHops() == null)
		{
			return teleport;
		}
		for (Teleport hop : teleport.chainHops())
		{
			for (TeleportItemRequirement r : hop.items())
			{
				if (Arrays.equals(r.itemIds(), req.itemIds()))
				{
					return hop;
				}
			}
		}
		return teleport;
	}

	static Set<Integer> boxSet(int[] ids)
	{
		Set<Integer> out = new LinkedHashSet<>();
		for (int id : ids)
		{
			out.add(id);
		}
		return out;
	}
}
