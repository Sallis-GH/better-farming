package com.betterfarming.data.requirement;

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
}
