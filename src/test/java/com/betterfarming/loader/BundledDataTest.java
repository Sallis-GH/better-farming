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

		// Sanity floors — tightened after full data population in P18.
		// 85 patches and 79 seeds as of population; floors set ~10% below actual.
		assertTrue("expected at least 80 patches", data.patches().size() >= 80);
		assertTrue("expected at least 71 seeds",   data.seeds().size()   >= 71);
	}
}
