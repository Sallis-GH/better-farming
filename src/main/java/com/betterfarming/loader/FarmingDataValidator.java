package com.betterfarming.loader;

import com.betterfarming.data.FarmingData;
import com.betterfarming.data.Patch;
import com.betterfarming.data.Seed;
import com.betterfarming.data.requirement.Requirement;
import com.betterfarming.data.requirement.SkillRequirement;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Singleton;

@Singleton
public class FarmingDataValidator
{
	public void validate(FarmingData data)
	{
		validateUniqueIds(data);
		validatePatchFields(data);
		validateSeedFields(data);
		validateRequirements(data);
	}

	private void validateUniqueIds(FarmingData data)
	{
		Set<String> seenPatchIds = new HashSet<>();
		for (Patch p : data.patches())
		{
			if (!seenPatchIds.add(p.id()))
			{
				throw new FarmingDataValidationException(
					"Duplicate patch id: " + p.id());
			}
		}
		Set<String> seenSeedIds = new HashSet<>();
		for (Seed s : data.seeds())
		{
			if (!seenSeedIds.add(s.id()))
			{
				throw new FarmingDataValidationException(
					"Duplicate seed id: " + s.id());
			}
		}
	}

	private void validatePatchFields(FarmingData data)
	{
		for (Patch p : data.patches())
		{
			requireNonEmpty(p.id(), "patch.id");
			requireNonEmpty(p.displayName(), "patch[" + p.id() + "].displayName");
			requireNonEmpty(p.location(), "patch[" + p.id() + "].location");
			if (p.type() == null)
			{
				throw new FarmingDataValidationException(
					"patch[" + p.id() + "].type is null");
			}
			if (p.worldPoint() == null)
			{
				throw new FarmingDataValidationException(
					"patch[" + p.id() + "].worldPoint is null");
			}
		}
	}

	private void validateSeedFields(FarmingData data)
	{
		for (Seed s : data.seeds())
		{
			requireNonEmpty(s.id(), "seed.id");
			requireNonEmpty(s.displayName(), "seed[" + s.id() + "].displayName");
			if (s.compatiblePatchTypes() == null || s.compatiblePatchTypes().isEmpty())
			{
				throw new FarmingDataValidationException(
					"seed[" + s.id() + "].compatiblePatchTypes is empty");
			}
		}
	}

	private void validateRequirements(FarmingData data)
	{
		for (Patch p : data.patches())
		{
			for (Requirement r : p.requirements())
			{
				validateRequirement(r, "patch[" + p.id() + "]");
			}
		}
		for (Seed s : data.seeds())
		{
			for (Requirement r : s.requirements())
			{
				validateRequirement(r, "seed[" + s.id() + "]");
			}
		}
	}

	private void validateRequirement(Requirement r, String owner)
	{
		if (r instanceof SkillRequirement)
		{
			SkillRequirement sr = (SkillRequirement) r;
			if (sr.level() < 1 || sr.level() > 99)
			{
				throw new FarmingDataValidationException(
					owner + " has SkillRequirement with out-of-range level: " + sr.level());
			}
		}
	}

	private static void requireNonEmpty(String value, String label)
	{
		if (value == null || value.isEmpty())
		{
			throw new FarmingDataValidationException(label + " is null or empty");
		}
	}
}
