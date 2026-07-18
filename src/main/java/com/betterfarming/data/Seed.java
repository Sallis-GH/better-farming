package com.betterfarming.data;

import com.betterfarming.data.requirement.Requirement;
import java.util.List;
import java.util.Set;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
public class Seed
{
	String id;
	String displayName;
	Set<PatchType> compatiblePatchTypes;
	List<Requirement> requirements;

	/**
	 * Item id of what the player brings on a run to plant this crop: the seed
	 * itself for ground crops, the sapling for trees. Null until the data file
	 * gains coverage for this seed — the run-items feature skips such seeds.
	 */
	Integer plantableItemId;

	/** Display name of the plantable, e.g. "Yew sapling". Null iff plantableItemId is. */
	String plantableName;

	/**
	 * Farmer protection payment, or null/empty when the crop has none. A list
	 * because some crops take multiple items (spirit tree: monkey nuts + monkey
	 * bar + ground tooth).
	 */
	List<Payment> payments;
}
