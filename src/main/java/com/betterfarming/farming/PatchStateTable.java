package com.betterfarming.farming;

import com.betterfarming.data.PatchType;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Varbit value → coarse crop state, per patch type, loaded from
 * resources/data/patch_states.json (extracted by research agents from
 * RuneLite core's timetracking PatchImplementation tables — never
 * hand-edited). Also carries the minimum total grow minutes per type used
 * to decay remote GROWING observations to UNKNOWN.
 */
public final class PatchStateTable
{
	private static final String RESOURCE = "/data/patch_states.json";

	private final Map<PatchType, List<Range>> ranges;
	private final Map<PatchType, Integer> minGrowMinutes;

	private static final class Range
	{
		final int from;
		final int to;
		final CropState state;

		Range(int from, int to, CropState state)
		{
			this.from = from;
			this.to = to;
			this.state = state;
		}
	}

	private PatchStateTable(Map<PatchType, List<Range>> ranges, Map<PatchType, Integer> minGrowMinutes)
	{
		this.ranges = ranges;
		this.minGrowMinutes = minGrowMinutes;
	}

	public CropState state(PatchType type, int varbitValue)
	{
		List<Range> list = ranges.get(type);
		if (list == null)
		{
			return CropState.UNKNOWN;
		}
		for (Range r : list)
		{
			if (varbitValue >= r.from && varbitValue <= r.to)
			{
				return r.state;
			}
		}
		return CropState.UNKNOWN;
	}

	/** Minimum minutes a crop of this type takes to fully grow; 0 = unknown. */
	public int minGrowMinutes(PatchType type)
	{
		return minGrowMinutes.getOrDefault(type, 0);
	}

	public static PatchStateTable load(Gson gson) throws IOException
	{
		try (InputStream in = PatchStateTable.class.getResourceAsStream(RESOURCE))
		{
			if (in == null)
			{
				throw new IOException("Required resource not found on classpath: " + RESOURCE);
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				JsonObject root = gson.fromJson(reader, JsonObject.class);
				Map<PatchType, List<Range>> ranges = new EnumMap<>(PatchType.class);
				JsonObject states = root.getAsJsonObject("states");
				for (Map.Entry<String, JsonElement> e : states.entrySet())
				{
					PatchType type;
					try
					{
						type = PatchType.valueOf(e.getKey());
					}
					catch (IllegalArgumentException ex)
					{
						// Implementation with no matching patch type; skip.
						continue;
					}
					List<Range> list = new ArrayList<>();
					JsonArray arr = e.getValue().getAsJsonArray();
					for (JsonElement el : arr)
					{
						JsonObject o = el.getAsJsonObject();
						list.add(new Range(o.get("from").getAsInt(), o.get("to").getAsInt(),
							CropState.valueOf(o.get("state").getAsString())));
					}
					ranges.put(type, list);
				}
				Map<PatchType, Integer> minGrow = new EnumMap<>(PatchType.class);
				JsonObject growth = root.getAsJsonObject("growth");
				if (growth != null)
				{
					for (Map.Entry<String, JsonElement> e : growth.entrySet())
					{
						try
						{
							minGrow.put(PatchType.valueOf(e.getKey()),
								e.getValue().getAsJsonObject().get("minTotalMinutes").getAsInt());
						}
						catch (IllegalArgumentException ex)
						{
							// growth entry for a type we don't model
						}
					}
				}
				return new PatchStateTable(ranges, minGrow);
			}
		}
	}
}
