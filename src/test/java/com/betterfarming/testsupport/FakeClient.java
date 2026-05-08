package com.betterfarming.testsupport;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/**
 * Tiny stand-in for net.runelite.api.Client exposing only the methods
 * services consume: getRealSkillLevel(Skill), getGameState(),
 * getQuestState(Quest). The real Client interface is huge; rather than
 * mock all 200+ methods we expose a narrowly-scoped helper interface and
 * FakeClient implements that.
 *
 * In production the helper interface is satisfied by a thin adapter
 * around the real Client. See ClientLevelSource in production code.
 */
public class FakeClient implements com.betterfarming.ui.ClientLevelSource
{
	private final Map<Skill, Integer> levels = new HashMap<>();
	private final Map<Quest, QuestState> questStates = new HashMap<>();

	private GameState gameState = GameState.LOGGED_IN;

	public void setGameState(GameState gameState)
	{
		this.gameState = gameState;
	}

	public void setLevel(Skill skill, int level)
	{
		levels.put(skill, level);
	}

	public void setQuestState(Quest quest, QuestState state)
	{
		questStates.put(quest, state);
	}

	@Override
	public int getRealSkillLevel(Skill skill)
	{
		return levels.getOrDefault(skill, 1);
	}

	@Override
	public GameState getGameState()
	{
		return gameState;
	}

	@Override
	public QuestState getQuestState(Quest quest)
	{
		// Match real RuneLite default for an account that hasn't touched a quest.
		return questStates.getOrDefault(quest, QuestState.NOT_STARTED);
	}
}
