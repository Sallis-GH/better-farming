package com.betterfarming.testsupport;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

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
	private final Map<Skill, Integer> boostedLevels = new HashMap<>();
	private final Map<Quest, QuestState> questStates = new HashMap<>();
	private final Map<Integer, Integer> varbits = new HashMap<>();
	private final Map<Integer, Integer> varps = new HashMap<>();

	private GameState gameState = GameState.LOGGED_IN;
	private WorldPoint playerPosition = new WorldPoint(3222, 3218, 0); // Lumbridge

	public void setGameState(GameState gameState)
	{
		this.gameState = gameState;
	}

	public void setLevel(Skill skill, int level)
	{
		levels.put(skill, level);
	}

	public void setBoostedLevel(Skill skill, int level)
	{
		boostedLevels.put(skill, level);
	}

	public void setVarbit(int id, int value)
	{
		varbits.put(id, value);
	}

	public void setVarp(int id, int value)
	{
		varps.put(id, value);
	}

	public void setPlayerPosition(WorldPoint position)
	{
		this.playerPosition = position;
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

	@Override
	public int getBoostedSkillLevel(Skill skill)
	{
		// Default: boosted == real unless explicitly boosted.
		return boostedLevels.getOrDefault(skill, getRealSkillLevel(skill));
	}

	@Override
	public int getVarbitValue(int varbitId)
	{
		return varbits.getOrDefault(varbitId, 0);
	}

	@Override
	public int getVarpValue(int varpId)
	{
		return varps.getOrDefault(varpId, 0);
	}

	@Override
	public WorldPoint getPlayerPosition()
	{
		return gameState == GameState.LOGGED_IN ? playerPosition : null;
	}
}
