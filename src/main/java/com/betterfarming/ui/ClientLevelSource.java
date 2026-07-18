package com.betterfarming.ui;

import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

/**
 * Narrow facade over net.runelite.api.Client used across services.
 * Lets tests substitute a fake without mocking the full Client interface.
 *
 * Production: ClientLevelSourceAdapter wraps the real Client. The plugin
 * binds this interface to that adapter at startup.
 */
public interface ClientLevelSource
{
	int getRealSkillLevel(Skill skill);

	GameState getGameState();

	QuestState getQuestState(Quest quest);

	/** Boosted (current) level — teleport spell availability uses boosts. */
	int getBoostedSkillLevel(Skill skill);

	int getVarbitValue(int varbitId);

	int getVarpValue(int varpId);

	/** Current player tile, or null when not logged in. */
	WorldPoint getPlayerPosition();
}
