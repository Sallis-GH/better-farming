package com.betterfarming.data.requirement;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RequirementEvaluatorTest
{
	private RequirementEvaluator evaluator;

	@Before
	public void setUp()
	{
		evaluator = new RequirementEvaluator();
	}

	private PlayerState loggedInWith(Map<Skill, Integer> skills, Map<Quest, QuestState> quests)
	{
		return new PlayerState(true, skills, quests);
	}

	@Test
	public void emptyRequirementList_returnsEmpty()
	{
		List<Requirement> result = evaluator.unmet(
			Collections.emptyList(),
			loggedInWith(Collections.emptyMap(), Collections.emptyMap()));
		assertTrue(result.isEmpty());
	}

	@Test
	public void loggedOut_returnsEmpty_evenWithRequirements()
	{
		List<Requirement> reqs = List.of(new SkillRequirement(Skill.FARMING, 45));
		List<Requirement> result = evaluator.unmet(reqs, PlayerState.loggedOut());
		assertTrue("logged-out always reports accessible (decision 2)", result.isEmpty());
	}

	@Test
	public void skillRequirementMet_omitsFromUnmet()
	{
		List<Requirement> reqs = List.of(new SkillRequirement(Skill.FARMING, 45));
		Map<Skill, Integer> skills = new HashMap<>();
		skills.put(Skill.FARMING, 50);

		List<Requirement> result = evaluator.unmet(reqs,
			loggedInWith(skills, Collections.emptyMap()));

		assertTrue(result.isEmpty());
	}

	@Test
	public void skillRequirementUnmet_includes()
	{
		SkillRequirement req = new SkillRequirement(Skill.FARMING, 45);
		Map<Skill, Integer> skills = new HashMap<>();
		skills.put(Skill.FARMING, 30);

		List<Requirement> result = evaluator.unmet(List.of(req),
			loggedInWith(skills, Collections.emptyMap()));

		assertEquals(List.of(req), result);
	}

	@Test
	public void skillNotInSnapshot_treatedAsUnmet()
	{
		SkillRequirement req = new SkillRequirement(Skill.FARMING, 45);
		// snapshot has no FARMING entry — conservative default: treat as unmet
		List<Requirement> result = evaluator.unmet(List.of(req),
			loggedInWith(Collections.emptyMap(), Collections.emptyMap()));

		assertEquals(List.of(req), result);
	}

	@Test
	public void questRequirementMet_omitsFromUnmet()
	{
		QuestRequirement req = new QuestRequirement(Quest.SONG_OF_THE_ELVES, QuestState.FINISHED);
		Map<Quest, QuestState> quests = new HashMap<>();
		quests.put(Quest.SONG_OF_THE_ELVES, QuestState.FINISHED);

		List<Requirement> result = evaluator.unmet(List.of(req),
			loggedInWith(Collections.emptyMap(), quests));

		assertTrue(result.isEmpty());
	}

	@Test
	public void questRequirementWrongState_includes()
	{
		QuestRequirement req = new QuestRequirement(Quest.SONG_OF_THE_ELVES, QuestState.FINISHED);
		Map<Quest, QuestState> quests = new HashMap<>();
		quests.put(Quest.SONG_OF_THE_ELVES, QuestState.IN_PROGRESS);

		List<Requirement> result = evaluator.unmet(List.of(req),
			loggedInWith(Collections.emptyMap(), quests));

		assertEquals(List.of(req), result);
	}

	@Test
	public void questNotInSnapshot_treatedAsUnmet()
	{
		QuestRequirement req = new QuestRequirement(Quest.SONG_OF_THE_ELVES, QuestState.FINISHED);
		// snapshot omits the quest entirely (e.g. client.getQuestState returned null)
		List<Requirement> result = evaluator.unmet(List.of(req),
			loggedInWith(Collections.emptyMap(), Collections.emptyMap()));

		assertEquals(List.of(req), result);
	}

	@Test
	public void mixedRequirements_partiallyMet_returnsOnlyUnmet()
	{
		SkillRequirement skillReq = new SkillRequirement(Skill.FARMING, 45);
		QuestRequirement questReq = new QuestRequirement(Quest.SONG_OF_THE_ELVES, QuestState.FINISHED);

		Map<Skill, Integer> skills = new HashMap<>();
		skills.put(Skill.FARMING, 50);          // met
		Map<Quest, QuestState> quests = new HashMap<>();
		quests.put(Quest.SONG_OF_THE_ELVES, QuestState.NOT_STARTED);   // unmet

		List<Requirement> result = evaluator.unmet(List.of(skillReq, questReq),
			loggedInWith(skills, quests));

		assertEquals(List.of(questReq), result);
	}

	@Test
	public void unknownRequirementSubtype_treatedAsUnmet()
	{
		Requirement unknown = new Requirement() {};
		List<Requirement> result = evaluator.unmet(List.of(unknown),
			loggedInWith(Collections.emptyMap(), Collections.emptyMap()));

		assertEquals("unknown subtype is conservative-locked, not silently unlocked",
			List.of(unknown), result);
	}

	@Test
	public void unmetOrderMatchesInputOrder()
	{
		SkillRequirement first = new SkillRequirement(Skill.FARMING, 99);
		QuestRequirement second = new QuestRequirement(Quest.SONG_OF_THE_ELVES, QuestState.FINISHED);
		List<Requirement> reqs = List.of(first, second);

		List<Requirement> result = evaluator.unmet(reqs,
			loggedInWith(Collections.emptyMap(), Collections.emptyMap()));

		assertEquals(reqs, result);
	}
}
