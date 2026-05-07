package com.betterfarming.data.requirement;

import lombok.Value;
import lombok.experimental.Accessors;
import net.runelite.api.Skill;

@Value
@Accessors(fluent = true)
public class SkillRequirement implements Requirement
{
	Skill skill;
	int level;
}
