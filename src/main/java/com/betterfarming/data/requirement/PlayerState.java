package com.betterfarming.data.requirement;

import java.util.Collections;
import java.util.Map;
import lombok.Value;
import lombok.experimental.Accessors;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

/**
 * Immutable snapshot of everything a Requirement can depend on. Built once
 * per recompute by PatchAccessibilityService, passed to RequirementEvaluator.
 *
 * Decoupling evaluation from Client lets the evaluator be a pure function —
 * trivially testable with literal PlayerState fixtures, no mocks, no facade.
 */
@Value
@Accessors(fluent = true)
public class PlayerState
{
	boolean loggedIn;
	Map<Skill, Integer> skillLevels;
	Map<Quest, QuestState> questStates;

	public static PlayerState loggedOut()
	{
		return new PlayerState(false, Collections.emptyMap(), Collections.emptyMap());
	}
}
