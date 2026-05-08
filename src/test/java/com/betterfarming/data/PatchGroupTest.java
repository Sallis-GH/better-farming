package com.betterfarming.data;

import com.betterfarming.data.requirement.Requirement;
import com.betterfarming.data.requirement.SkillRequirement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PatchGroupTest
{
	private static Patch patch(String id, PatchType type, String location, String label,
		List<Requirement> reqs)
	{
		return new Patch(id, "Display " + id, type, location, label,
			new WorldPoint(0, 0, 0), reqs);
	}

	@Test
	public void groupAllBucketsByTypeAndLocation()
	{
		List<Patch> patches = Arrays.asList(
			patch("a1", PatchType.ALLOTMENT, "Falador", "NW", Collections.emptyList()),
			patch("a2", PatchType.ALLOTMENT, "Falador", "SE", Collections.emptyList()),
			patch("h1", PatchType.HERB, "Falador", null, Collections.emptyList())
		);

		List<PatchGroup> groups = PatchGroup.groupAll(patches);

		assertEquals(2, groups.size());
		assertEquals("ALLOTMENT|Falador", groups.get(0).key());
		assertEquals(2, groups.get(0).patches().size());
		assertEquals("HERB|Falador", groups.get(1).key());
		assertEquals(1, groups.get(1).patches().size());
	}

	@Test
	public void groupAllPreservesJsonFileOrderInsideGroup()
	{
		List<Patch> patches = Arrays.asList(
			patch("a2", PatchType.ALLOTMENT, "Falador", "SE", Collections.emptyList()),
			patch("a1", PatchType.ALLOTMENT, "Falador", "NW", Collections.emptyList())
		);

		List<PatchGroup> groups = PatchGroup.groupAll(patches);

		assertEquals(1, groups.size());
		// Order is the order patches appeared in the input list.
		assertEquals("a2", groups.get(0).patches().get(0).id());
		assertEquals("a1", groups.get(0).patches().get(1).id());
	}

	@Test
	public void groupAllPreservesGroupOrderByFirstAppearance()
	{
		List<Patch> patches = Arrays.asList(
			patch("h1", PatchType.HERB, "Catherby", null, Collections.emptyList()),
			patch("a1", PatchType.ALLOTMENT, "Falador", "NW", Collections.emptyList()),
			patch("a2", PatchType.ALLOTMENT, "Falador", "SE", Collections.emptyList())
		);

		List<PatchGroup> groups = PatchGroup.groupAll(patches);

		assertEquals("HERB|Catherby", groups.get(0).key());
		assertEquals("ALLOTMENT|Falador", groups.get(1).key());
	}

	@Test
	public void keyConcatenatesTypeNameAndLocationWithPipe()
	{
		List<Patch> patches = Arrays.asList(
			patch("p1", PatchType.HARDWOOD_TREE, "Fossil Island mushroom forest", "MID",
				Collections.emptyList()));
		PatchGroup g = PatchGroup.groupAll(patches).get(0);

		assertEquals("HARDWOOD_TREE|Fossil Island mushroom forest", g.key());
	}

	@Test
	public void isSingletonReturnsTrueForOnePatchGroup()
	{
		List<Patch> patches = Arrays.asList(
			patch("h1", PatchType.HERB, "Catherby", null, Collections.emptyList()));

		assertTrue(PatchGroup.groupAll(patches).get(0).isSingleton());
	}

	@Test
	public void isSingletonReturnsFalseForMultiPatchGroup()
	{
		List<Patch> patches = Arrays.asList(
			patch("a1", PatchType.ALLOTMENT, "Falador", "NW", Collections.emptyList()),
			patch("a2", PatchType.ALLOTMENT, "Falador", "SE", Collections.emptyList()));

		assertFalse(PatchGroup.groupAll(patches).get(0).isSingleton());
	}

	@Test
	public void requirementsReturnsFirstPatchRequirements()
	{
		List<Requirement> reqs = Arrays.asList(new SkillRequirement(Skill.FARMING, 45));
		List<Patch> patches = Arrays.asList(
			patch("a1", PatchType.ALLOTMENT, "Farming Guild", "N", reqs),
			patch("a2", PatchType.ALLOTMENT, "Farming Guild", "S", reqs));

		PatchGroup g = PatchGroup.groupAll(patches).get(0);
		assertEquals(reqs, g.requirements());
	}
}
