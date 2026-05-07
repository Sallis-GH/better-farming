package com.betterfarming.data;

import com.betterfarming.data.requirement.Requirement;
import java.util.List;
import lombok.Value;
import lombok.experimental.Accessors;
import net.runelite.api.coords.WorldPoint;

@Value
@Accessors(fluent = true)
public class Patch
{
	String id;
	String displayName;
	PatchType type;
	String location;
	WorldPoint worldPoint;
	List<Requirement> requirements;
}
