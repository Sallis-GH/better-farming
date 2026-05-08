package com.betterfarming.ui;

import com.betterfarming.data.FarmingData;
import com.betterfarming.loader.FarmingDataLoader;
import com.betterfarming.state.PatchSelectionService;
import com.betterfarming.testsupport.FakeClient;
import com.betterfarming.testsupport.FakeConfigStore;
import java.io.IOException;
import net.runelite.api.Skill;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class BetterFarmingPanelIntegrationTest
{
	@BeforeClass
	public static void enableHeadless()
	{
		System.setProperty("java.awt.headless", "true");
	}

	private FarmingData data;
	private FakeClient client;
	private FakeConfigStore configManager;
	private PatchSelectionService selectionService;
	private SeedAvailabilityService availabilityService;
	private BetterFarmingPanel panel;

	@Before
	public void setUp() throws IOException
	{
		data = new FarmingDataLoader().load();
		client = new FakeClient();
		client.setLevel(Skill.FARMING, 99);
		configManager = new FakeConfigStore();

		selectionService = new PatchSelectionService(configManager, data);
		availabilityService = new SeedAvailabilityService(client, data);
		panel = new BetterFarmingPanel(data, selectionService, availabilityService);
	}

	@Test
	public void panelConstructsWithoutErrors()
	{
		assertNotNull(panel);
	}
}
