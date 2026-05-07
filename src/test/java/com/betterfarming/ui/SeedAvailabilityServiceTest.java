package com.betterfarming.ui;

import com.betterfarming.data.FarmingData;
import com.betterfarming.data.PatchType;
import com.betterfarming.data.Seed;
import com.betterfarming.data.requirement.SkillRequirement;
import com.betterfarming.testsupport.FakeClient;
import java.util.List;
import java.util.Set;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SeedAvailabilityServiceTest
{
	private FakeClient client;
	private SeedAvailabilityService availability;

	private final Seed potatoLvl1 = new Seed("potato_seed", "Potato seed",
		Set.of(PatchType.ALLOTMENT),
		List.of(new SkillRequirement(Skill.FARMING, 1)));

	private final Seed watermelonLvl47 = new Seed("watermelon_seed", "Watermelon seed",
		Set.of(PatchType.ALLOTMENT),
		List.of(new SkillRequirement(Skill.FARMING, 47)));

	private final Seed marigoldLvl2 = new Seed("marigold_seed", "Marigold seed",
		Set.of(PatchType.FLOWER),
		List.of(new SkillRequirement(Skill.FARMING, 2)));

	private final Seed seedWithoutSkillReq = new Seed("hespori_seed", "Hespori seed",
		Set.of(PatchType.HESPORI),
		List.of()); // no FARMING requirement modeled — treat as level 1

	@Before
	public void setUp()
	{
		client = new FakeClient();
		client.setLevel(Skill.FARMING, 50);
		FarmingData data = new FarmingData(List.of(),
			List.of(potatoLvl1, watermelonLvl47, marigoldLvl2, seedWithoutSkillReq));
		availability = new SeedAvailabilityService(client, data);
	}

	@Test
	public void filtersByPatchType()
	{
		List<Seed> allotment = availability.plantableSeeds(PatchType.ALLOTMENT);

		assertEquals(2, allotment.size()); // potato + watermelon
		assertTrue(allotment.contains(potatoLvl1));
		assertTrue(allotment.contains(watermelonLvl47));
	}

	@Test
	public void filtersByFarmingLevel()
	{
		client.setLevel(Skill.FARMING, 30);
		availability.refresh();

		List<Seed> allotment = availability.plantableSeeds(PatchType.ALLOTMENT);
		assertEquals(1, allotment.size());
		assertTrue(allotment.contains(potatoLvl1));
	}

	@Test
	public void seedWithoutFarmingRequirementIsAlwaysAvailable()
	{
		client.setLevel(Skill.FARMING, 1);
		availability.refresh();

		List<Seed> hespori = availability.plantableSeeds(PatchType.HESPORI);
		assertEquals(List.of(seedWithoutSkillReq), hespori);
	}

	@Test
	public void emptyListWhenLoggedOut()
	{
		client.setGameState(GameState.LOGIN_SCREEN);
		availability.refresh();

		assertTrue(availability.plantableSeeds(PatchType.ALLOTMENT).isEmpty());
		assertTrue(availability.plantableSeeds(PatchType.HESPORI).isEmpty());
	}

	@Test
	public void resultsAreSortedByFarmingLevelAscending()
	{
		client.setLevel(Skill.FARMING, 99);
		availability.refresh();

		List<Seed> allotment = availability.plantableSeeds(PatchType.ALLOTMENT);
		assertEquals(potatoLvl1, allotment.get(0));      // level 1 first
		assertEquals(watermelonLvl47, allotment.get(1)); // level 47 second
	}
}
