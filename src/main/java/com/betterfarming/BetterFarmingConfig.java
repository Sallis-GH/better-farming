package com.betterfarming;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(BetterFarmingConfig.GROUP)
public interface BetterFarmingConfig extends Config
{
	String GROUP = "betterfarming";
	String PATCH_SELECTIONS_KEY = "patchSelections";

	@ConfigItem(
		keyName = PATCH_SELECTIONS_KEY,
		name = "Patch selections (internal)",
		description = "Serialized patch selection state. Edit at your own risk.",
		hidden = true
	)
	default String patchSelections()
	{
		return "";
	}
}
