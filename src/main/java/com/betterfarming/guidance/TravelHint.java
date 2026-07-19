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
		String info = t.displayInfo() != null ? t.displayInfo() : humanize(t.type().name());
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
			default:
				return info;
		}
	}

	private static String humanize(String enumName)
	{
		StringBuilder sb = new StringBuilder();
		for (String word : enumName.toLowerCase().split("_"))
		{
			if (sb.length() > 0)
			{
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return sb.toString();
	}
}
