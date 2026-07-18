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

	@ConfigItem(
		keyName = "teleportItemsFromBank",
		name = "Plan teleports with banked items",
		description = "Count banked runes/teleport items as usable when planning the run order.<br>"
			+ "Disable to only use teleports whose items you are carrying right now.",
		position = 1
	)
	default boolean teleportItemsFromBank()
	{
		return true;
	}

	@ConfigItem(
		keyName = "assumePohFacilities",
		name = "Use POH teleports",
		description = "Include house portals, the jewellery box and in-house fairy ring/spirit tree<br>"
			+ "when planning routes. Enable only if your house has them — the plugin cannot<br>"
			+ "detect your house layout.",
		position = 2
	)
	default boolean assumePohFacilities()
	{
		return false;
	}
}
