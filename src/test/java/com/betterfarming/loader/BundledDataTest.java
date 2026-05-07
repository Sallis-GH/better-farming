package com.betterfarming.loader;

import com.betterfarming.data.FarmingData;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BundledDataTest
{
	@Test
	public void bundledJsonLoadsAndValidates() throws Exception
	{
		FarmingDataLoader loader = new FarmingDataLoader();
		FarmingDataValidator validator = new FarmingDataValidator();

		FarmingData data = loader.load();
		assertNotNull(data);

		validator.validate(data);

		// Sanity floors — kept low while data is being populated.
		// Tighten in P18 step 10 once full population is complete.
		assertTrue("expected at least 1 patch", data.patches().size() >= 1);
		assertTrue("expected at least 1 seed",  data.seeds().size()   >= 1);
	}
}
