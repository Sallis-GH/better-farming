package com.betterfarming.item;

import java.util.List;
import java.util.Set;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * One line of the run-items list: "Ranarr seed ×4 — in bank". itemIds is a set
 * because some items have interchangeable variants (e.g. regular or magic
 * secateurs both satisfy the secateurs line).
 *
 * Outfit rows ("Graceful") set `pieces`: each inner set is one slot's
 * interchangeable variants, and the row is satisfied only when every slot is
 * covered. For piece rows, itemIds is the union of all pieces (bank
 * filtering) and quantity equals the piece count.
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

	/** Null for plain rows; per-slot pieces for outfit rows. */
	List<OutfitPiece> pieces;
}
