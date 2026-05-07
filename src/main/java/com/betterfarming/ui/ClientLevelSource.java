package com.betterfarming.ui;

import net.runelite.api.GameState;
import net.runelite.api.Skill;

/**
 * Narrow facade over net.runelite.api.Client for the Phase 1 sidebar.
 * Lets tests substitute a fake without mocking the full Client interface.
 *
 * Production: ClientLevelSourceAdapter wraps the real Client. The plugin
 * binds this interface to that adapter at startup.
 */
public interface ClientLevelSource
{
	int getRealSkillLevel(Skill skill);

	GameState getGameState();
}
