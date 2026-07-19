package com.betterfarming.guidance;

import com.betterfarming.travel.RoutePlanner;
import com.betterfarming.travel.Teleport;

/**
 * Turns a planned leg into a one-line "how to travel" instruction, e.g.
 * "Cast Camelot Teleport", "Break Falador teleport tablet", "Fairy ring CKR".
 * Pure string derivation from the teleport's type and display info.
 */
public final class TravelHint
{
	private TravelHint()
	{
	}

	public static String text(RoutePlanner.Leg leg)
	{
		if (leg == null)
		{
			return null;
		}
		Teleport t = leg.teleport();
		if (t == null)
		{
			return "Walk to " + leg.stop().displayName();
		}
		return forTeleport(t);
	}

	/** Instruction for one hop of a chain ("Sail: Mos Le'Harmless"). */
	public static String forTeleport(Teleport t)
	{
		String info = t.displayLabel();
		if (t.viaPoh())
		{
			// House-chain display already reads "<house teleport> → <facility>".
			return info;
		}
		switch (t.type())
		{
			case SPELL:
			case HOME_SPELL:
				return "Cast " + info;
			case ITEM:
				return (info.toLowerCase().contains("tablet") ? "Break " : "Use ") + info;
			case JEWELLERY_BOX:
				return "Jewellery box: " + info;
			case POH_PORTAL:
				return "House portal: " + info;
			case PORTAL:
				return "Enter portal: " + info;
			case FAIRY_RING:
				return "Fairy ring: " + info;
			case SPIRIT_TREE:
				return "Spirit tree: " + info;
			case GNOME_GLIDER:
				return "Gnome glider: " + info;
			case QUETZAL:
				return "Quetzal: " + info;
			case QUETZAL_WHISTLE:
				return "Blow quetzal whistle: " + info;
			case MUSHTREE:
				return "Mushtree: " + info;
			case SHIP:
			case BOAT:
				return "Sail: " + info;
			case CHARTER_SHIP:
				return "Charter ship: " + info;
			case TRANSPORT:
				return "Travel: " + info;
			default:
				return info;
		}
	}
}
