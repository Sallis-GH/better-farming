package com.betterfarming.farming;

import com.betterfarming.data.PatchType;
import com.google.gson.Gson;
import java.io.IOException;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Anchors the bundled patch_states.json against known farming facts. */
public class PatchStateTableTest
{
	private static PatchStateTable table;

	@BeforeClass
	public static void load() throws IOException
	{
		table = PatchStateTable.load(new Gson());
	}

	@Test
	public void herbValueAnchors()
	{
		// Weeds occupy the lowest values; herbs grow through 4 stages.
		assertEquals(CropState.EMPTY, table.state(PatchType.HERB, 0));
		assertEquals(CropState.EMPTY, table.state(PatchType.HERB, 3));
		assertEquals(CropState.GROWING, table.state(PatchType.HERB, 4));
		assertEquals(CropState.HARVESTABLE, table.state(PatchType.HERB, 8));
	}

	@Test
	public void valuesRuneLiteDoesNotHandleAreUnknown()
	{
		// Genuine gaps in RuneLite's PatchImplementation tables.
		assertEquals(CropState.UNKNOWN, table.state(PatchType.HERB, 220));
		assertEquals(CropState.UNKNOWN, table.state(PatchType.TREE, 76));
	}

	@Test
	public void minGrowMinutes()
	{
		assertEquals(80, table.minGrowMinutes(PatchType.HERB));
		assertEquals(160, table.minGrowMinutes(PatchType.TREE));
		assertEquals(960, table.minGrowMinutes(PatchType.FRUIT_TREE));
		// No growth data for types outside the v1 prediction scope.
		assertEquals(0, table.minGrowMinutes(PatchType.ALLOTMENT));
	}

	@Test
	public void allBundledTypesHaveStateTables()
	{
		for (PatchType type : PatchType.values())
		{
			// Value 0 is weeds (EMPTY) for every implementation.
			assertEquals("type " + type, CropState.EMPTY, table.state(type, 0));
		}
	}
}
