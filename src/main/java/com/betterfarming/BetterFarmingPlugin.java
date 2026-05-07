package com.betterfarming;

import com.betterfarming.data.FarmingData;
import com.betterfarming.loader.FarmingDataLoader;
import com.betterfarming.loader.FarmingDataValidator;
import com.google.inject.Provides;
import javax.inject.Inject;
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
	@Inject
	private FarmingDataLoader loader;

	@Inject
	private FarmingDataValidator validator;

	@Override
	protected void startUp() throws Exception
	{
		FarmingData data = loader.load();
		validator.validate(data);
		log.info("Better Farming: started; loaded {} patches and {} seeds",
			data.patches().size(), data.seeds().size());
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
