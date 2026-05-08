package com.betterfarming.loader;

import com.betterfarming.data.FarmingData;
import com.betterfarming.data.Patch;
import com.betterfarming.data.PatchType;
import com.betterfarming.data.Seed;
import com.betterfarming.data.requirement.Requirement;
import com.betterfarming.data.requirement.SkillRequirement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
		validateGroupRules(data);
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

	/**
	 * Group invariants for the (type, location) grouping introduced in Phase 1.1:
	 *  - group-requirements-identical: siblings unlock together, so they must share requirements.
	 *  - sub-patch-label-presence: multi-group patches require a non-empty subPatchLabel; singletons must not.
	 *  - sub-patch-label-unique-within-group: no duplicate labels within one group.
	 */
	private void validateGroupRules(FarmingData data)
	{
		LinkedHashMap<String, List<Patch>> groups = new LinkedHashMap<>();
		for (Patch p : data.patches())
		{
			String key = p.type().name() + "|" + p.location();
			groups.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
		}

		for (Map.Entry<String, List<Patch>> entry : groups.entrySet())
		{
			String key = entry.getKey();
			List<Patch> members = entry.getValue();
			validateGroupRequirementsIdentical(key, members);
			validateSubPatchLabels(key, members);
		}
	}

	private void validateGroupRequirementsIdentical(String groupKey, List<Patch> members)
	{
		List<Requirement> first = members.get(0).requirements();
		for (int i = 1; i < members.size(); i++)
		{
			List<Requirement> other = members.get(i).requirements();
			if (!first.equals(other))
			{
				throw new FarmingDataValidationException(
					"group-requirements-identical violated for group [" + groupKey + "]: "
						+ "patch " + members.get(0).id() + " has " + first
						+ " but patch " + members.get(i).id() + " has " + other);
			}
		}
	}

	private void validateSubPatchLabels(String groupKey, List<Patch> members)
	{
		boolean isMulti = members.size() > 1;
		Set<String> seenLabels = new HashSet<>();
		for (Patch p : members)
		{
			boolean hasLabel = p.subPatchLabel() != null && !p.subPatchLabel().isEmpty();
			if (isMulti && !hasLabel)
			{
				throw new FarmingDataValidationException(
					"sub-patch-label-presence violated for group [" + groupKey + "]: "
						+ "patch " + p.id() + " is in a multi-patch group but has no subPatchLabel");
			}
			if (!isMulti && hasLabel)
			{
				throw new FarmingDataValidationException(
					"sub-patch-label-presence violated for group [" + groupKey + "]: "
						+ "patch " + p.id() + " is a singleton but has subPatchLabel="
						+ p.subPatchLabel());
			}
			if (isMulti && !seenLabels.add(p.subPatchLabel()))
			{
				throw new FarmingDataValidationException(
					"sub-patch-label-unique-within-group violated for group [" + groupKey + "]: "
						+ "label '" + p.subPatchLabel() + "' appears on multiple patches "
						+ "(including " + p.id() + ")");
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
