package com.betterfarming.data;

import lombok.Value;
import lombok.experimental.Accessors;
import net.runelite.api.coords.WorldPoint;

/**
 * Player-position box gating live farming-varbit reads for patches whose
 * FarmingRegion shares a map region with another one (transcribed from
 * FarmingWorld.java isInBounds overrides; see scripts/merge_patch_state_data.py).
 * All fields optional; absent means unconstrained.
 */
@Value
@Accessors(fluent = true)
public class StateBounds
{
	Integer minX;
	Integer maxX;
	Integer minY;
	Integer maxY;
	Integer plane;

	public boolean contains(WorldPoint p)
	{
		return (minX == null || p.getX() >= minX)
			&& (maxX == null || p.getX() <= maxX)
			&& (minY == null || p.getY() >= minY)
			&& (maxY == null || p.getY() <= maxY)
			&& (plane == null || p.getPlane() == plane);
	}
}
