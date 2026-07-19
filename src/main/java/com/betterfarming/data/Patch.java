package com.betterfarming.data;

import com.betterfarming.data.requirement.Requirement;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;
import net.runelite.api.coords.WorldPoint;

@Value
@Accessors(fluent = true)
@AllArgsConstructor
public class Patch
{
	/** Convenience: a patch without crop-state mappings (state stays UNKNOWN). */
	public Patch(String id, String displayName, PatchType type, String location,
		String subPatchLabel, WorldPoint worldPoint, List<Requirement> requirements)
	{
		this(id, displayName, type, location, subPatchLabel, worldPoint, requirements,
			null, null, null, null);
	}

	String id;
	String displayName;
	PatchType type;
	String location;
	String subPatchLabel;
	WorldPoint worldPoint;
	List<Requirement> requirements;

	/**
	 * Farming state varbit (FARMING_TRANSMIT_*) carrying this patch's crop
	 * state while the player stands in one of stateRegionIds. Null when the
	 * dataset has no mapping (state stays UNKNOWN).
	 */
	Integer stateVarbitId;

	/** FarmingRegion primary region id — the timetracking config key region. */
	Integer stateRegionId;

	/** All region ids whose transmit varbits carry this patch's state. */
	List<Integer> stateRegionIds;

	/** Optional player-position gate for split map regions; null = none. */
	StateBounds stateBounds;
}
