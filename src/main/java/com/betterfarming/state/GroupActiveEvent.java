package com.betterfarming.state;

import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Fired by PatchSelectionService when a group's active flag changes.
 * Listeners receive the group key (composite "TYPE|location") together
 * with the previous and current active flag.
 */
@Value
@Accessors(fluent = true)
public class GroupActiveEvent
{
	String groupKey;
	boolean oldActive;
	boolean newActive;
}
