package com.betterfarming.data;

import com.betterfarming.data.requirement.Requirement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Immutable grouping of one or more Patches sharing the same (type, location).
 * Constructed via {@link #groupAll(List)}; the bucket preserves the input
 * (i.e. patches.json file) order both within a group and across groups.
 */
@Value
@Accessors(fluent = true)
public class PatchGroup
{
	PatchType type;
	String location;
	List<Patch> patches;

	public String key()
	{
		return type.name() + "|" + location;
	}

	public boolean isSingleton()
	{
		return patches.size() == 1;
	}

	/**
	 * Returns the requirements shared by every patch in this group.
	 *
	 * Relies on {@code FarmingDataValidator}'s "group-requirements-identical" rule, which
	 * guarantees every patch in a group has the same {@code requirements} list. This method
	 * returns {@code patches.get(0).requirements()}. If that validator rule is ever weakened
	 * or removed, this method must be rethought — it would silently return one of the
	 * divergent lists.
	 *
	 * The canary test {@code groupRequirementsMustBeIdentical_throws} in
	 * {@code FarmingDataValidatorTest} exists to make any deletion of the rule visible at CI time.
	 */
	public List<Requirement> requirements()
	{
		return patches.get(0).requirements();
	}

	public static List<PatchGroup> groupAll(List<Patch> patches)
	{
		LinkedHashMap<String, List<Patch>> bucket = new LinkedHashMap<>();
		for (Patch p : patches)
		{
			String k = p.type().name() + "|" + p.location();
			bucket.computeIfAbsent(k, key -> new ArrayList<>()).add(p);
		}
		List<PatchGroup> out = new ArrayList<>(bucket.size());
		bucket.forEach((k, ps) -> out.add(new PatchGroup(ps.get(0).type(), ps.get(0).location(), ps)));
		return out;
	}
}
