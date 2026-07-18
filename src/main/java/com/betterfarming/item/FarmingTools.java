package com.betterfarming.item;

import java.util.Set;

/**
 * Item ids for the standard farming-run tools. Ids are OSRS item ids
 * (stable protocol values, cross-checked against the wiki).
 */
final class FarmingTools
{
	static final int RAKE = 5341;
	static final int SPADE = 952;
	static final int SEED_DIBBER = 5343;
	static final int SECATEURS = 5329;
	static final int MAGIC_SECATEURS = 7409;
	static final int BOTTOMLESS_COMPOST_BUCKET = 22994;
	static final int BOTTOMLESS_COMPOST_BUCKET_FILLED = 22997;

	static final Set<Integer> ANY_SECATEURS = Set.of(SECATEURS, MAGIC_SECATEURS);
	static final Set<Integer> ANY_BOTTOMLESS_BUCKET =
		Set.of(BOTTOMLESS_COMPOST_BUCKET, BOTTOMLESS_COMPOST_BUCKET_FILLED);

	private FarmingTools()
	{
	}
}
