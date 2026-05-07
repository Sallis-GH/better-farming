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
}
