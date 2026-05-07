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

public class RequirementDeserializer implements JsonDeserializer<Requirement>
{
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
		switch (type)
		{
			case "SKILL":
				return context.deserialize(obj, SkillRequirement.class);
			case "QUEST":
				return context.deserialize(obj, QuestRequirement.class);
			default:
				throw new JsonParseException("Unknown Requirement type \"" + type + "\" in: " + obj);
		}
	}
}
