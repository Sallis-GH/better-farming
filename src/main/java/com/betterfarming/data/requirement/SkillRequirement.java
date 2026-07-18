package com.betterfarming.data.requirement;

import java.util.Set;
import lombok.Value;
import lombok.experimental.Accessors;
import net.runelite.api.Skill;

@Value
@Accessors(fluent = true)
public class SkillRequirement implements Requirement
{
	Skill skill;
	int level;

	@Override
	public boolean isMet(PlayerState state)
	{
		Integer actual = state.skillLevels().get(skill);
		return actual != null && actual >= level;
	}

	@Override
	public String describe()
	{
		return skill.getName() + " " + level;
	}

	@Override
	public void validate()
	{
		if (level < 1 || level > 99)
		{
			throw new IllegalArgumentException(
				"SkillRequirement with out-of-range level: " + level);
		}
	}

	@Override
	public Set<Skill> trackedSkills()
	{
		return Set.of(skill);
	}
}
