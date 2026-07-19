package com.betterfarming.item;

import java.util.Set;
import lombok.Value;
import lombok.experimental.Accessors;

/** One equipment slot of an outfit set: label + all interchangeable item ids. */
@Value
@Accessors(fluent = true)
public class OutfitPiece
{
	String name;
	Set<Integer> ids;
}
