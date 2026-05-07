package com.betterfarming;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Better Farming",
	description = "Helper for OSRS farming runs (patch selection, run planning, path guidance)",
	tags = {"farming", "skilling", "agriculture", "patches", "seeds"}
)
public class BetterFarmingPlugin extends Plugin
{
	@Override
	protected void startUp() throws Exception
	{
		log.info("Better Farming: started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Better Farming: stopped");
	}

	@Provides
	BetterFarmingConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BetterFarmingConfig.class);
	}
}
