package com.betterfarming.state;

import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Immutable snapshot of a single patch's seed choice. Identity is by
 * patchId. seedId may be null — meaning "no seed picked yet".
 *
 * Activation lives separately on PatchSelectionService at the group level,
 * not per-patch. See PatchSelectionService.isGroupActive(String).
 */
@Value
@Accessors(fluent = true)
public class PatchSelection
{
	String patchId;
	String seedId;
}
