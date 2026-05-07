package com.betterfarming.loader;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import net.runelite.api.coords.WorldPoint;

public class WorldPointDeserializer implements JsonDeserializer<WorldPoint>
{
	@Override
	public WorldPoint deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
		throws JsonParseException
	{
		JsonObject obj = json.getAsJsonObject();
		int x = obj.get("x").getAsInt();
		int y = obj.get("y").getAsInt();
		int plane = obj.get("plane").getAsInt();
		return new WorldPoint(x, y, plane);
	}
}
