package com.betterfarming.loader;

import com.betterfarming.data.Patch;
import com.betterfarming.data.PatchType;
import com.betterfarming.data.requirement.QuestRequirement;
import com.betterfarming.data.requirement.Requirement;
import com.betterfarming.data.requirement.SkillRequirement;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class FarmingDataLoaderTest
{
	private final Gson gson = new GsonBuilder()
		.registerTypeAdapter(WorldPoint.class, new WorldPointDeserializer())
		.registerTypeAdapter(Requirement.class, new RequirementDeserializer())
		.create();

	@Test
	public void deserializesWorldPointFromXYPlane()
	{
		String json = "{ \"x\": 2813, \"y\": 3464, \"plane\": 0 }";
		WorldPoint point = gson.fromJson(json, WorldPoint.class);
		assertEquals(2813, point.getX());
		assertEquals(3464, point.getY());
		assertEquals(0, point.getPlane());
	}

	@Test
	public void deserializesSkillRequirement()
	{
		String json = "{ \"type\": \"SKILL\", \"skill\": \"FARMING\", \"level\": 32 }";
		Requirement r = gson.fromJson(json, Requirement.class);
		assertTrue(r instanceof SkillRequirement);
		SkillRequirement sr = (SkillRequirement) r;
		assertEquals(Skill.FARMING, sr.skill());
		assertEquals(32, sr.level());
	}

	@Test
	public void deserializesQuestRequirement()
	{
		String json = "{ \"type\": \"QUEST\", \"quest\": \"MY_ARMS_BIG_ADVENTURE\", \"state\": \"FINISHED\" }";
		Requirement r = gson.fromJson(json, Requirement.class);
		assertTrue(r instanceof QuestRequirement);
		QuestRequirement qr = (QuestRequirement) r;
		assertEquals(Quest.MY_ARMS_BIG_ADVENTURE, qr.quest());
		assertEquals(QuestState.FINISHED, qr.state());
	}

	@Test
	public void unknownRequirementTypeThrows()
	{
		String json = "{ \"type\": \"BOGUS\", \"foo\": \"bar\" }";
		JsonParseException ex = assertThrows(JsonParseException.class,
			() -> gson.fromJson(json, Requirement.class));
		assertTrue("message should mention 'BOGUS'", ex.getMessage().contains("BOGUS"));
	}

	@Test
	public void deserializesCompletePatchFromInlineJson()
	{
		String json = "{"
			+ "\"id\":\"trollheim_herb\","
			+ "\"displayName\":\"Trollheim herb patch\","
			+ "\"type\":\"HERB\","
			+ "\"location\":\"Trollheim\","
			+ "\"worldPoint\":{\"x\":2826,\"y\":3694,\"plane\":0},"
			+ "\"requirements\":[{\"type\":\"QUEST\",\"quest\":\"MY_ARMS_BIG_ADVENTURE\",\"state\":\"FINISHED\"}]"
			+ "}";

		Patch patch = gson.fromJson(json, Patch.class);

		assertEquals("trollheim_herb", patch.id());
		assertEquals("Trollheim herb patch", patch.displayName());
		assertEquals(PatchType.HERB, patch.type());
		assertEquals("Trollheim", patch.location());
		assertEquals(2826, patch.worldPoint().getX());
		assertEquals(1, patch.requirements().size());
		assertTrue(patch.requirements().get(0) instanceof QuestRequirement);
	}
}
