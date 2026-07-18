package com.betterfarming.data.requirement;

import java.util.Collections;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.Skill;

/**
 * A single unlock condition on a Patch or Seed. Implementations are
 * self-contained: evaluation, display text, data validation, and the
 * client-state dimensions they depend on all live on the type itself,
 * so adding a new requirement kind touches exactly one class plus the
 * deserializer registry (RequirementDeserializer.TYPES).
 */
public interface Requirement
{
	/** True when the given player state satisfies this requirement. */
	boolean isMet(PlayerState state);

	/** Human-readable form for tooltips, e.g. "Farming 45" or "Bone Voyage". */
	String describe();

	/**
	 * Data sanity check, called once at load time by FarmingDataValidator.
	 * Throw IllegalArgumentException for out-of-range values; the validator
	 * wraps it with owner context.
	 */
	default void validate()
	{
	}

	/** Skills whose StatChanged events should trigger re-evaluation. */
	default Set<Skill> trackedSkills()
	{
		return Collections.emptySet();
	}

	/** Quests whose state this requirement reads from the snapshot. */
	default Set<Quest> trackedQuests()
	{
		return Collections.emptySet();
	}
}
