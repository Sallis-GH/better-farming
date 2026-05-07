package com.betterfarming.ui;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;

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
}
