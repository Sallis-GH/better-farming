package com.betterfarming.travel;

// Adapted from Skretzo/shortest-path (BSD-2-Clause) transport.requirement.ItemRequirement,
// see resources/transports/LICENSE-shortest-path

import lombok.Value;
import lombok.experimental.Accessors;

/**
 * One AND-term of a teleport's item needs: `quantity` of any of `itemIds`
 * (OR-variations, e.g. air/dust/smoke rune), OR any single item from
 * `staffIds`/`offhandIds` (an equippable that supplies the runes — quantity
 * does not apply to those).
 */
@Value
@Accessors(fluent = true)
public class TeleportItemRequirement
{
	int[] itemIds;
	int[] staffIds;
	int[] offhandIds;
	int quantity;

	/** Human label, e.g. "Law rune" — prettified from the TSV token. */
	String name;
}
