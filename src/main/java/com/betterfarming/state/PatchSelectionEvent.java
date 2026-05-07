package com.betterfarming.state;

import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Fired by PatchSelectionService after a mutation. Listeners receive the
 * patchId together with the previous and current PatchSelection so they
 * can decide cheaply whether to react.
 *
 * If a patch had no prior selection (e.g. first-time toggle), oldSelection
 * is null. newSelection is never null.
 */
@Value
@Accessors(fluent = true)
public class PatchSelectionEvent
{
	String patchId;
	PatchSelection oldSelection;
	PatchSelection newSelection;
}
