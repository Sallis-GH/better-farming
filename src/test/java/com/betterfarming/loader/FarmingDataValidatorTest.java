package com.betterfarming.loader;

import com.betterfarming.data.FarmingData;
import com.betterfarming.data.Patch;
import com.betterfarming.data.PatchType;
import com.betterfarming.data.Seed;
import com.betterfarming.data.requirement.Requirement;
import com.betterfarming.data.requirement.SkillRequirement;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class FarmingDataValidatorTest
{
	private final FarmingDataValidator validator = new FarmingDataValidator();

	private static Patch validPatch(String id)
	{
		return new Patch(id, "Display " + id, PatchType.HERB, "Somewhere",
			null, new WorldPoint(0, 0, 0), Collections.<Requirement>emptyList());
	}

	private static Seed validSeed(String id)
	{
		Set<PatchType> types = new HashSet<>();
		types.add(PatchType.HERB);
		return new Seed(id, "Display " + id, types, Collections.<Requirement>emptyList());
	}

	@Test
	public void validDataPasses()
	{
		Patch p1 = new Patch("p1", "Display p1", PatchType.HERB, "Location1",
			null, new WorldPoint(0, 0, 0), Collections.<Requirement>emptyList());
		Patch p2 = new Patch("p2", "Display p2", PatchType.ALLOTMENT, "Location2",
			null, new WorldPoint(0, 0, 0), Collections.<Requirement>emptyList());
		FarmingData data = new FarmingData(
			Arrays.asList(p1, p2),
			Arrays.asList(validSeed("s1"))
		);
		validator.validate(data); // should not throw
	}

	@Test
	public void duplicatePatchIdsFails()
	{
		FarmingData data = new FarmingData(
			Arrays.asList(validPatch("p1"), validPatch("p1")),
			Collections.<Seed>emptyList()
		);
		FarmingDataValidationException ex = assertThrows(FarmingDataValidationException.class,
			() -> validator.validate(data));
		assertTrue("message should name the duplicate id", ex.getMessage().contains("p1"));
	}

	@Test
	public void duplicateSeedIdsFails()
	{
		FarmingData data = new FarmingData(
			Arrays.asList(validPatch("p1")),
			Arrays.asList(validSeed("s1"), validSeed("s1"))
		);
		FarmingDataValidationException ex = assertThrows(FarmingDataValidationException.class,
			() -> validator.validate(data));
		assertTrue("message should name the duplicate id", ex.getMessage().contains("s1"));
	}

	@Test
	public void emptyPatchDisplayNameFails()
	{
		Patch p = new Patch("p1", "", PatchType.HERB, "Somewhere",
			null, new WorldPoint(0, 0, 0), Collections.<Requirement>emptyList());
		FarmingData data = new FarmingData(Arrays.asList(p), Collections.<Seed>emptyList());
		assertThrows(FarmingDataValidationException.class,
			() -> validator.validate(data));
	}

	@Test
	public void seedWithEmptyCompatiblePatchTypesFails()
	{
		Seed s = new Seed("s1", "Bogus seed", Collections.<PatchType>emptySet(),
			Collections.<Requirement>emptyList());
		FarmingData data = new FarmingData(Arrays.asList(validPatch("p1")), Arrays.asList(s));
		assertThrows(FarmingDataValidationException.class,
			() -> validator.validate(data));
	}

	@Test
	public void skillRequirementLevelOver99Fails()
	{
		Requirement bogus = new SkillRequirement(Skill.FARMING, 320);
		Patch p = new Patch("p1", "P1", PatchType.HERB, "Somewhere",
			null, new WorldPoint(0, 0, 0), Arrays.asList(bogus));
		FarmingData data = new FarmingData(Arrays.asList(p), Collections.<Seed>emptyList());
		assertThrows(FarmingDataValidationException.class,
			() -> validator.validate(data));
	}

	@Test
	public void skillRequirementLevelBelow1Fails()
	{
		Requirement bogus = new SkillRequirement(Skill.FARMING, 0);
		Patch p = new Patch("p1", "P1", PatchType.HERB, "Somewhere",
			null, new WorldPoint(0, 0, 0), Arrays.asList(bogus));
		FarmingData data = new FarmingData(Arrays.asList(p), Collections.<Seed>emptyList());
		assertThrows(FarmingDataValidationException.class,
			() -> validator.validate(data));
	}

	@Test
	public void groupRequirementsMustBeIdentical_throws()
	{
		Requirement req45 = new SkillRequirement(Skill.FARMING, 45);
		Patch a = new Patch("a1", "A1", PatchType.ALLOTMENT, "Catherby", null,
			new WorldPoint(0, 0, 0), Collections.<Requirement>emptyList());
		Patch b = new Patch("a2", "A2", PatchType.ALLOTMENT, "Catherby", null,
			new WorldPoint(0, 0, 0), Arrays.asList(req45));

		FarmingData data = new FarmingData(Arrays.asList(a, b), Collections.<Seed>emptyList());

		FarmingDataValidationException ex = assertThrows(FarmingDataValidationException.class,
			() -> validator.validate(data));
		assertTrue("message should name the rule",
			ex.getMessage().contains("group-requirements-identical"));
		assertTrue("message should name the offending group",
			ex.getMessage().contains("ALLOTMENT|Catherby"));
	}

	@Test
	public void subPatchLabelRequiredInMultiGroup_throws()
	{
		Patch a = new Patch("a1", "A1", PatchType.ALLOTMENT, "Catherby", "N",
			new WorldPoint(0, 0, 0), Collections.<Requirement>emptyList());
		// Same (type, location), but no subPatchLabel
		Patch b = new Patch("a2", "A2", PatchType.ALLOTMENT, "Catherby", null,
			new WorldPoint(0, 0, 0), Collections.<Requirement>emptyList());

		FarmingData data = new FarmingData(Arrays.asList(a, b), Collections.<Seed>emptyList());

		FarmingDataValidationException ex = assertThrows(FarmingDataValidationException.class,
			() -> validator.validate(data));
		assertTrue("message should name the rule",
			ex.getMessage().contains("sub-patch-label-presence"));
		assertTrue("message should name the offending patch",
			ex.getMessage().contains("a2"));
	}

	@Test
	public void subPatchLabelMustBeNullForSingleton_throws()
	{
		// Singleton with a non-null subPatchLabel
		Patch p = new Patch("h1", "H1", PatchType.HERB, "Catherby", "N",
			new WorldPoint(0, 0, 0), Collections.<Requirement>emptyList());

		FarmingData data = new FarmingData(Arrays.asList(p), Collections.<Seed>emptyList());

		FarmingDataValidationException ex = assertThrows(FarmingDataValidationException.class,
			() -> validator.validate(data));
		assertTrue("message should name the rule",
			ex.getMessage().contains("sub-patch-label-presence"));
		assertTrue("message should name the offending patch",
			ex.getMessage().contains("h1"));
	}
}
