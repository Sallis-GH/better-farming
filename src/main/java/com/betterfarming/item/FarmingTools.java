package com.betterfarming.item;

import java.util.Set;

/**
 * Item ids for the standard farming-run tools. Ids are OSRS item ids
 * (stable protocol values, cross-checked against the wiki).
 */
public final class FarmingTools
{
	static final int RAKE = 5341;

	/** Applicable compost variants (filled bottomless, not the empty bucket). */
	public static final Set<Integer> COMPOST_VARIANTS = Set.of(
		6032 /* compost */, 6034 /* supercompost */, 21483 /* ultracompost */,
		22997 /* bottomless (filled) */);
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
