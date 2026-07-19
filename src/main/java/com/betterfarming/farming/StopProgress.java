package com.betterfarming.farming;

/**
 * Progress of one run stop (patch group) as far as crop state can tell.
 * UNKNOWN means guidance falls back to proximity-based completion.
 */
public enum StopProgress
{
	/** Every patch in the group is growing — the stop is done. */
	COMPLETE,
	/** At least one patch still needs work (plant/harvest/cure/clear). */
	INCOMPLETE,
	/** State not observable for some patch; can't judge. */
	UNKNOWN
}
