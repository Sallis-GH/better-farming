package com.betterfarming;

/**
 * POH jewellery box tier. Higher tiers include all lower-tier teleports, so
 * a player's box `covers` a requirement tier when theirs is at least as high.
 */
public enum JewelleryBoxTier
{
	NONE,
	BASIC,
	FANCY,
	ORNATE;

	public boolean covers(JewelleryBoxTier required)
	{
		return ordinal() >= required.ordinal();
	}
}
