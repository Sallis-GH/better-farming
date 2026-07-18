package com.betterfarming.bank;

/**
 * A deferred text + optional sprite overlay (goal quantity "/ N" and its
 * tick/cross icon). Queued during item layout and created after all item
 * widgets so the overlays z-order on top.
 */
class BankText
{
	final String text;
	final int x;
	final int y;
	final int spriteId;
	final int spriteX;
	final int spriteY;

	BankText(String text, int x, int y, int spriteId, int spriteX, int spriteY)
	{
		this.text = text;
		this.x = x;
		this.y = y;
		this.spriteId = spriteId;
		this.spriteX = spriteX;
		this.spriteY = spriteY;
	}
}
