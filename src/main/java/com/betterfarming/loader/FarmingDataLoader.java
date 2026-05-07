package com.betterfarming.loader;

import com.betterfarming.data.FarmingData;
import com.betterfarming.data.Patch;
import com.betterfarming.data.Seed;
import com.betterfarming.data.requirement.Requirement;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.coords.WorldPoint;

@Singleton
public class FarmingDataLoader
{
	private static final String PATCHES_RESOURCE = "/data/patches.json";
	private static final String SEEDS_RESOURCE = "/data/seeds.json";

	private final Gson gson;

	@Inject
	public FarmingDataLoader()
	{
		this.gson = new GsonBuilder()
			.registerTypeAdapter(WorldPoint.class, new WorldPointDeserializer())
			.registerTypeAdapter(Requirement.class, new RequirementDeserializer())
			.create();
	}

	public FarmingData load() throws IOException
	{
		List<Patch> patches = readArray(PATCHES_RESOURCE, Patch.class);
		List<Seed> seeds = readArray(SEEDS_RESOURCE, Seed.class);
		return new FarmingData(patches, seeds);
	}

	private <T> List<T> readArray(String resourcePath, Class<T> elementType) throws IOException
	{
		try (InputStream in = getClass().getResourceAsStream(resourcePath))
		{
			if (in == null)
			{
				throw new IOException("Required resource not found on classpath: " + resourcePath);
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				Type listType = TypeToken.getParameterized(List.class, elementType).getType();
				List<T> result = gson.fromJson(reader, listType);
				return result == null ? List.of() : result;
			}
		}
	}
}
