package com.betterfarming.ui;

import com.betterfarming.data.requirement.Requirement;
import java.util.List;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Fired when a group's lock state flips, OR when a still-locked group's
 * unmet list changes (e.g. one of two missing requirements becomes met —
 * tooltip needs to refresh even though the lock stays on).
 *
 * unmet is empty iff nowLocked == false.
 */
@Value
@Accessors(fluent = true)
public class PatchAccessibilityEvent
{
	String groupKey;
	boolean wasLocked;
	boolean nowLocked;
	List<Requirement> unmet;
}
