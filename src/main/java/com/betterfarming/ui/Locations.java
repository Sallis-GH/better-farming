package com.betterfarming.ui;

import java.util.regex.Pattern;

/**
 * Display helpers for patch location strings. The dataset names locations
 * navigationally ("West of Port Phasmatys", "South of Falador"); the sidebar
 * shows just the place — the full navigational name belongs in a tooltip.
 * Compass forms without "of" ("South-west Etceteria", "South-east Etceteria")
 * are kept: stripping them would collide same-type groups into one name.
 */
public final class Locations
{
	private static final Pattern NAV_PREFIX = Pattern.compile(
		"^(?:north|south|east|west)(?:-(?:east|west))?\\s+of\\s+",
		Pattern.CASE_INSENSITIVE);

	private Locations()
	{
	}

	/** "West of Port Phasmatys" → "Port Phasmatys"; already-bare names pass through. */
	public static String display(String location)
	{
		if (location == null)
		{
			return null;
		}
		return NAV_PREFIX.matcher(location).replaceFirst("");
	}
}
