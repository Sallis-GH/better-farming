package com.betterfarming;

import com.betterfarming.bank.FarmingBankTab;
import com.betterfarming.bank.FarmingBankTagService;
import com.betterfarming.data.FarmingData;
import com.betterfarming.item.ItemTracker;
import com.betterfarming.item.RunItemsService;
import com.betterfarming.loader.FarmingDataLoader;
import com.betterfarming.loader.FarmingDataValidator;
import com.betterfarming.state.ConfigManagerStore;
import com.betterfarming.state.ConfigStore;
import com.betterfarming.state.PatchSelectionService;
import com.betterfarming.data.requirement.RequirementEvaluator;
import com.betterfarming.ui.BetterFarmingPanel;
import com.betterfarming.ui.ClientLevelSource;
import com.betterfarming.ui.ClientLevelSourceAdapter;
import com.betterfarming.ui.PatchAccessibilityService;
import com.betterfarming.ui.SeedAvailabilityService;
import com.google.inject.Binder;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
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
	@Inject private RequirementEvaluator evaluator;
	@Inject private ItemTracker itemTracker;
	@Inject private ClientThread clientThread;
	@Inject private FarmingBankTab bankTab;

	private NavigationButton navButton;
	private Runnable bankTabRefreshListener;
	private SeedAvailabilityService availabilityService;
	private PatchSelectionService selectionService;
	private PatchAccessibilityService accessibilityService;
	private RunItemsService runItemsService;

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
		accessibilityService = new PatchAccessibilityService(clientLevelSource, data, evaluator);
		runItemsService = new RunItemsService(data, selectionService, accessibilityService, itemTracker);
		runItemsService.wire();

		// SeedAvailabilityService and PatchAccessibilityService have @Subscribe
		// methods — register both on the bus.
		eventBus.register(availabilityService);
		eventBus.register(accessibilityService);
		eventBus.register(itemTracker);

		// Bank tab: hand-wire the section service (RunItemsService is not
		// Guice-managed) and refresh the open tab whenever run items change.
		// RunItemsService notifies on the EDT; hop to the client thread.
		bankTab.setBankTagService(new FarmingBankTagService(runItemsService, itemTracker));
		bankTabRefreshListener = () -> clientThread.invokeLater(bankTab::refreshBankTab);
		runItemsService.addListener(bankTabRefreshListener);
		eventBus.register(bankTab);
		bankTab.startUp();

		// Initial pass so cards built mid-session-already-logged-in see real
		// lock state at construction. Without this, the first lock evaluation
		// would wait for a GameStateChanged or StatChanged that may never come
		// for an idle-logged-in player.
		accessibilityService.refresh();

		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icons/sidebar.png");
		SwingUtilities.invokeLater(() -> {
			BetterFarmingPanel panel = new BetterFarmingPanel(
				data, selectionService, availabilityService, accessibilityService, runItemsService);
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
		if (accessibilityService != null)
		{
			eventBus.unregister(accessibilityService);
			accessibilityService = null;
		}
		eventBus.unregister(itemTracker);
		eventBus.unregister(bankTab);
		bankTab.shutDown();
		if (runItemsService != null)
		{
			if (bankTabRefreshListener != null)
			{
				runItemsService.removeListener(bankTabRefreshListener);
				bankTabRefreshListener = null;
			}
			runItemsService.unwire();
			runItemsService = null;
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
