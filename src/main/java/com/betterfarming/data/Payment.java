package com.betterfarming.data;

import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Farmer protection payment for a crop, e.g. 1× "Basket of apples(5)" for a
 * willow tree. itemId is the exact item the farmer accepts (baskets/sacks have
 * their own ids). quantity is per patch planted.
 */
@Value
@Accessors(fluent = true)
public class Payment
{
	int itemId;
	String name;
	int quantity;
}
