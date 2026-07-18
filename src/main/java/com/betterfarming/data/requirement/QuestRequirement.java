package com.betterfarming.data.requirement;

import java.util.Set;
import lombok.Value;
import lombok.experimental.Accessors;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;

@Value
@Accessors(fluent = true)
public class QuestRequirement implements Requirement
{
	Quest quest;
	QuestState state;

	@Override
	public boolean isMet(PlayerState playerState)
	{
		return playerState.questStates().get(quest) == state;
	}

	@Override
	public String describe()
	{
		return quest.getName();
	}

	@Override
	public Set<Quest> trackedQuests()
	{
		return Set.of(quest);
	}
}
