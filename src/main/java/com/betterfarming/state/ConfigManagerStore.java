package com.betterfarming.state;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

@Singleton
public class ConfigManagerStore implements ConfigStore
{
	private final ConfigManager configManager;

	@Inject
	public ConfigManagerStore(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	@Override
	public String get(String group, String key)
	{
		return configManager.getConfiguration(group, key);
	}

	@Override
	public void set(String group, String key, String value)
	{
		configManager.setConfiguration(group, key, value);
	}
}
