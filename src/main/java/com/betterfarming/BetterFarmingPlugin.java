package com.betterfarming;

import com.betterfarming.data.FarmingData;
import com.betterfarming.loader.FarmingDataLoader;
import com.betterfarming.loader.FarmingDataValidator;
import com.betterfarming.state.ConfigManagerStore;
import com.betterfarming.state.ConfigStore;
import com.betterfarming.state.PatchSelectionService;
import com.betterfarming.ui.BetterFarmingPanel;
import com.betterfarming.ui.ClientLevelSource;
import com.betterfarming.ui.ClientLevelSourceAdapter;
import com.betterfarming.ui.SeedAvailabilityService;
import com.google.inject.Binder;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Better Farming",
	description = "Helper for OSRS farming runs (patch selection, run planning, path guidance)",
	tags = {"farming", "skilling", "agriculture", "patches", "seeds"}
)
public class BetterFarmingPlugin extends Plugin
{
	@Inject private FarmingDataLoader loader;
	@Inject private FarmingDataValidator validator;
	@Inject private ConfigStore configStore;
	@Inject private ClientLevelSource clientLevelSource;
	@Inject private ClientToolbar clientToolbar;
	@Inject private EventBus eventBus;

	private NavigationButton navButton;
	private SeedAvailabilityService availabilityService;
	private PatchSelectionService selectionService;

	@Override
	public void configure(Binder binder)
	{
		binder.bind(ClientLevelSource.class).to(ClientLevelSourceAdapter.class);
		binder.bind(ConfigStore.class).to(ConfigManagerStore.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		FarmingData data = loader.load();
		validator.validate(data);
		log.info("Better Farming: started; loaded {} patches and {} seeds",
			data.patches().size(), data.seeds().size());

		selectionService = new PatchSelectionService(configStore, data);
		availabilityService = new SeedAvailabilityService(clientLevelSource, data);

		// SeedAvailabilityService has @Subscribe methods — register on the bus
		eventBus.register(availabilityService);

		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icons/sidebar.png");
		SwingUtilities.invokeLater(() -> {
			BetterFarmingPanel panel = new BetterFarmingPanel(
				data, selectionService, availabilityService);
			navButton = NavigationButton.builder()
				.tooltip("Better Farming")
				.icon(icon)
				.priority(7)
				.panel(panel)
				.build();
			clientToolbar.addNavigation(navButton);
		});
	}

	@Override
	protected void shutDown() throws Exception
	{
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		if (availabilityService != null)
		{
			eventBus.unregister(availabilityService);
			availabilityService = null;
		}
		selectionService = null;
		log.info("Better Farming: stopped");
	}

	@Provides
	@Singleton
	BetterFarmingConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BetterFarmingConfig.class);
	}
}
