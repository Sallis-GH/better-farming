package com.betterfarming.travel;

import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Value;
import lombok.experimental.Accessors;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

/**
 * One travel edge. `origin == null` means usable from anywhere (spells, items);
 * otherwise the player must first walk to the origin tile (network nodes,
 * POH portals). All requirement collections must be satisfied together.
 */
@Value
@Accessors(fluent = true)
public class Teleport
{
	TeleportType type;
	WorldPoint origin;
	WorldPoint destination;
	int durationTicks;
	String displayInfo;
	Map<Skill, Integer> skillLevels;
	Set<Quest> quests;
	Set<VarCheck> varChecks;
	List<TeleportItemRequirement> items;
	boolean consumable;

	/** True when either endpoint sits inside the POH instance map area. */
	public boolean touchesPoh()
	{
		return isInPoh(origin) || isInPoh(destination);
	}

	private static boolean isInPoh(WorldPoint p)
	{
		// POH instances live in the 1856-2047 × 5632-5951 map block.
		return p != null && p.getX() >= 1856 && p.getX() < 2048
			&& p.getY() >= 5632 && p.getY() < 5952;
	}
}
