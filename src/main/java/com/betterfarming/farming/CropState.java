package com.betterfarming.farming;

/**
 * Coarse crop state of a farming patch, collapsed from RuneLite core's
 * timetracking value tables (see resources/data/patch_states.json).
 */
public enum CropState
{
	/** Weeds or cleared soil — the patch wants planting. */
	EMPTY,
	/** A crop is growing; nothing to do here. */
	GROWING,
	/** Fully grown: harvest / check health. */
	HARVESTABLE,
	DISEASED,
	DEAD,
	/** No observation, unmapped value, or a stale prediction. */
	UNKNOWN;

	/** Every state except a confirmed growing crop warrants a stop. */
	public boolean needsVisit()
	{
		return this != GROWING;
	}
}
