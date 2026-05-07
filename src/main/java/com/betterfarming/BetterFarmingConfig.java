package com.betterfarming;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("betterfarming")
public interface BetterFarmingConfig extends Config
{
	@ConfigItem(
		keyName = "farmingLevel",
		name = "Farming level",
		description = "Your current Farming skill level (used to filter available seeds and patches)"
	)
	default int farmingLevel()
	{
		return 1;
	}

	@ConfigItem(
		keyName = "herbloreLevel",
		name = "Herblore level",
		description = "Your current Herblore skill level (used for herb-related options)"
	)
	default int herbloreLevel()
	{
		return 1;
	}

	@ConfigItem(
		keyName = "magicLevel",
		name = "Magic level",
		description = "Your current Magic skill level (used to determine available teleports)"
	)
	default int magicLevel()
	{
		return 1;
	}
}
