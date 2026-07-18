package com.betterfarming.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PatchType
{
	ALLOTMENT(3, false),
	FLOWER(1, false),
	HERB(1, false),
	HOPS(4, false),
	BUSH(1, false),
	TREE(1, true),
	FRUIT_TREE(1, true),
	HARDWOOD_TREE(1, true),
	SPIRIT_TREE(1, true),
	CALQUAT(1, true),
	CELASTRUS(1, true),
	REDWOOD(1, true),
	CRYSTAL_TREE(1, true),
	ANIMA(1, false),
	HESPORI(1, false),
	CACTUS(1, false),
	MUSHROOM(1, false),
	BELLADONNA(1, false),
	SEAWEED(1, false);

	/** Plantables consumed per patch: 3 seeds per allotment, 4 per hops, else 1. */
	private final int plantablesPerPatch;

	/** Sapling crops are planted with a spade; ground crops with a seed dibber. */
	private final boolean sapling;
}
