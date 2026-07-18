package com.betterfarming.item;

import java.util.Set;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * One line of the run-items list: "Ranarr seed ×4 — in bank". itemIds is a set
 * because some items have interchangeable variants (e.g. regular or magic
 * secateurs both satisfy the secateurs line).
 */
@Value
@Accessors(fluent = true)
public class RunItem
{
	String displayName;
	Set<Integer> itemIds;
	int quantity;
	boolean recommended;
	RunItemCategory category;
	RunItemStatus status;
}
