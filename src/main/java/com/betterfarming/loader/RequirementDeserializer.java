package com.betterfarming.loader;

import com.betterfarming.data.requirement.QuestRequirement;
import com.betterfarming.data.requirement.Requirement;
import com.betterfarming.data.requirement.SkillRequirement;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.util.Map;

public class RequirementDeserializer implements JsonDeserializer<Requirement>
{
	/**
	 * Registry of JSON "type" discriminator → concrete class. Adding a new
	 * requirement kind means implementing Requirement and adding one entry here.
	 */
	private static final Map<String, Class<? extends Requirement>> TYPES = Map.of(
		"SKILL", SkillRequirement.class,
		"QUEST", QuestRequirement.class
	);

	@Override
	public Requirement deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
		throws JsonParseException
	{
		JsonObject obj = json.getAsJsonObject();
		if (!obj.has("type"))
		{
			throw new JsonParseException("Requirement is missing required \"type\" field: " + obj);
		}
		String type = obj.get("type").getAsString();
		Class<? extends Requirement> clazz = TYPES.get(type);
		if (clazz == null)
		{
			throw new JsonParseException("Unknown Requirement type \"" + type + "\" in: " + obj);
		}
		return context.deserialize(obj, clazz);
	}
}
