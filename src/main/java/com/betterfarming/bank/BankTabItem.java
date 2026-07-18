package com.betterfarming.bank;

import java.util.List;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * One item slot in the farming bank tab. itemIds lists interchangeable
 * variants (first entry is the preferred display id when none are banked).
 * quantity is the run goal; 0 means "no goal" (used for the leftover
 * "Other items" section, which shows no /N overlay).
 */
@Value
@Accessors(fluent = true)
public class BankTabItem
{
	String text;
	List<Integer> itemIds;
	int quantity;

	/** Precomputed at build time: player+bank holdings cover the goal. */
	boolean satisfied;
}
