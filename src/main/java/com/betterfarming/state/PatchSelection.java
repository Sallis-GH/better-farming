package com.betterfarming.state;

import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Immutable snapshot of a single patch's selection state.
 * Identity is by patchId (matching the Patch.id values from FarmingData).
 * seedId may be null — meaning "no seed picked yet" — which is independent
 * of the selected flag.
 */
@Value
@Accessors(fluent = true)
public class PatchSelection
{
	String patchId;
	boolean selected;
	String seedId;
}
