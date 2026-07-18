package com.betterfarming.item;

public enum RunItemStatus
{
	/** Full quantity in inventory or worn — ready to go. */
	ON_PLAYER,
	/** Not (fully) on player, but the shortfall is covered by the last-known bank. */
	IN_BANK,
	/** Not on player and not known to be banked. */
	MISSING
}
