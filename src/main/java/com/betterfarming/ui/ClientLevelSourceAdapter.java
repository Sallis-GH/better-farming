package com.betterfarming.ui;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

@Singleton
public class ClientLevelSourceAdapter implements ClientLevelSource
{
	private final Client client;

	@Inject
	public ClientLevelSourceAdapter(Client client)
	{
		this.client = client;
	}

	@Override
	public int getRealSkillLevel(Skill skill)
	{
		return client.getRealSkillLevel(skill);
	}

	@Override
	public GameState getGameState()
	{
		return client.getGameState();
	}

	@Override
	public QuestState getQuestState(Quest quest)
	{
		return quest.getState(client);
	}

	@Override
	public int getBoostedSkillLevel(Skill skill)
	{
		return client.getBoostedSkillLevel(skill);
	}

	@Override
	public int getVarbitValue(int varbitId)
	{
		return client.getVarbitValue(varbitId);
	}

	@Override
	public int getVarpValue(int varpId)
	{
		return client.getVarpValue(varpId);
	}

	@Override
	public int getEnumValue(int enumId, int key)
	{
		net.runelite.api.EnumComposition composition = client.getEnum(enumId);
		return composition == null ? -1 : composition.getIntValue(key);
	}

	@Override
	public WorldPoint getPlayerPosition()
	{
		Player local = client.getLocalPlayer();
		return local == null ? null : local.getWorldLocation();
	}
}
