package com.betterfarming.loader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FarmingDataLoaderTest
{
	private final Gson gson = new GsonBuilder()
		.registerTypeAdapter(WorldPoint.class, new WorldPointDeserializer())
		.create();

	@Test
	public void deserializesWorldPointFromXYPlane()
	{
		String json = "{ \"x\": 2813, \"y\": 3464, \"plane\": 0 }";
		WorldPoint point = gson.fromJson(json, WorldPoint.class);
		assertEquals(2813, point.getX());
		assertEquals(3464, point.getY());
		assertEquals(0, point.getPlane());
	}
}
