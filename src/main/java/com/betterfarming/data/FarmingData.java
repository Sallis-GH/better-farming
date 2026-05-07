package com.betterfarming.data;

import java.util.List;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
public class FarmingData
{
	List<Patch> patches;
	List<Seed> seeds;
}
