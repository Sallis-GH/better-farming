package com.betterfarming;

import com.betterfarming.bank.FarmingBankTab;
import com.betterfarming.bank.FarmingBankTagService;
import com.betterfarming.data.FarmingData;
import com.betterfarming.guidance.GuidanceService;
import com.betterfarming.guidance.GuidanceWorldMapMarker;
import com.betterfarming.guidance.MinimapArrowOverlay;
import com.betterfarming.guidance.ShortestPathBridge;
import com.betterfarming.guidance.TravelHintOverlay;
import com.betterfarming.guidance.WorldArrowOverlay;
import com.betterfarming.guidance.WorldMapRouteOverlay;
import com.betterfarming.item.ItemTracker;
import com.betterfarming.item.PlayerUnlocks;
import com.betterfarming.item.RunItemsService;
import com.betterfarming.loader.FarmingDataLoader;
import com.betterfarming.loader.FarmingDataValidator;
import com.betterfarming.state.ConfigManagerStore;
import com.betterfarming.state.ConfigStore;
import com.betterfarming.state.PatchSelectionService;
import com.betterfarming.travel.RunOrderService;
import com.betterfarming.travel.Teleport;
import com.betterfarming.travel.TeleportAvailabilityService;
import com.betterfarming.travel.TeleportLoader;
import com.betterfarming.data.requirement.RequirementEvaluator;
import com.betterfarming.ui.BetterFarmingPanel;
import com.betterfarming.ui.ClientLevelSource;
import com.betterfarming.ui.ClientLevelSourceAdapter;
import com.betterfarming.ui.PatchAccessibilityService;
import com.betterfarming.ui.SeedAvailabilityService;
import com.google.inject.Binder;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
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
	@Inject private PlayerUnlocks playerUnlocks;
	@Inject private ClientThread clientThread;
	@Inject private FarmingBankTab bankTab;
	@Inject private TeleportLoader teleportLoader;
	@Inject private BetterFarmingConfig config;
	@Inject private Client client;
	@Inject private OverlayManager overlayManager;
	@Inject private WorldMapPointManager worldMapPointManager;

	private NavigationButton navButton;
	private Runnable bankTabRefreshListener;
	private SeedAvailabilityService availabilityService;
	private PatchSelectionService selectionService;
	private PatchAccessibilityService accessibilityService;
	private RunItemsService runItemsService;
	private TeleportAvailabilityService teleportService;
	private RunOrderService runOrderService;
	private GuidanceService guidanceService;
	private GuidanceWorldMapMarker worldMapMarker;
	private ShortestPathBridge shortestPathBridge;
	private final List<Runnable> guidanceListeners = new java.util.ArrayList<>();
	private final List<Overlay> guidanceOverlays = new java.util.ArrayList<>();

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
		runItemsService = new RunItemsService(
			data, selectionService, accessibilityService, itemTracker, playerUnlocks, config);
		runItemsService.wire();

		List<Teleport> teleports = teleportLoader.loadAll();
		teleportService = new TeleportAvailabilityService(teleports, clientLevelSource, itemTracker, config);
		runOrderService = new RunOrderService(
			data, selectionService, accessibilityService, teleportService, clientLevelSource,
			config, clientThread::invokeLater);
		runOrderService.wire();
		// The planned legs feed teleport-item rows into the run-items list.
		runItemsService.setRunOrderService(runOrderService);

		// Services with @Subscribe methods register on the bus.
		eventBus.register(availabilityService);
		eventBus.register(accessibilityService);
		eventBus.register(itemTracker);
		eventBus.register(playerUnlocks);
		eventBus.register(teleportService);

		// Bank tab: hand-wire the section service (RunItemsService is not
		// Guice-managed) and refresh the open tab whenever run items change.
		// RunItemsService notifies on the EDT; hop to the client thread.
		bankTab.setBankTagService(new FarmingBankTagService(runItemsService, itemTracker));
		bankTabRefreshListener = () -> clientThread.invokeLater(bankTab::refreshBankTab);
		runItemsService.addListener(bankTabRefreshListener);
		eventBus.register(bankTab);
		bankTab.startUp();

		// Guidance: leg tracking on GameTick, overlays reading its snapshot,
		// and side-effect listeners (world-map marker, shortest-path push)
		// running inside the fanout on the client thread.
		guidanceService = new GuidanceService(runOrderService::legs, clientLevelSource);
		worldMapMarker = new GuidanceWorldMapMarker(worldMapPointManager, config, guidanceService);
		shortestPathBridge = new ShortestPathBridge(eventBus, config, guidanceService, clientLevelSource);
		// Separate listeners on purpose: the fanout isolates failures per
		// listener, and bundling both updates into one Runnable would let a
		// marker failure starve the shortest-path push (hard-won rule 2).
		guidanceListeners.add(worldMapMarker::update);
		guidanceListeners.add(shortestPathBridge::update);
		guidanceListeners.forEach(guidanceService::addListener);
		eventBus.register(guidanceService);
		guidanceOverlays.add(new WorldArrowOverlay(client, config, guidanceService));
		guidanceOverlays.add(new MinimapArrowOverlay(client, config, guidanceService));
		guidanceOverlays.add(new WorldMapRouteOverlay(client, config, guidanceService));
		guidanceOverlays.add(new TravelHintOverlay(config, guidanceService));
		guidanceOverlays.forEach(overlayManager::add);

		// Initial pass so cards built mid-session-already-logged-in see real
		// lock state at construction. Without this, the first lock evaluation
		// would wait for a GameStateChanged or StatChanged that may never come
		// for an idle-logged-in player.
		accessibilityService.refresh();
		playerUnlocks.refresh();
		teleportService.refresh();

		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icons/sidebar.png");
		SwingUtilities.invokeLater(() -> {
			BetterFarmingPanel panel = new BetterFarmingPanel(
				data, selectionService, availabilityService, accessibilityService,
				runItemsService, runOrderService);
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
		eventBus.unregister(playerUnlocks);
		eventBus.unregister(bankTab);
		bankTab.shutDown();
		if (teleportService != null)
		{
			eventBus.unregister(teleportService);
			teleportService = null;
		}
		if (guidanceService != null)
		{
			eventBus.unregister(guidanceService);
			guidanceListeners.forEach(guidanceService::removeListener);
			guidanceService = null;
		}
		guidanceListeners.clear();
		guidanceOverlays.forEach(overlayManager::remove);
		guidanceOverlays.clear();
		if (worldMapMarker != null)
		{
			worldMapMarker.remove();
			worldMapMarker = null;
		}
		if (shortestPathBridge != null)
		{
			// No-op unless we posted a path; a user-set Shortest Path route
			// must survive this plugin shutting down.
			shortestPathBridge.clear();
			shortestPathBridge = null;
		}
		if (runOrderService != null)
		{
			runOrderService.unwire();
			runOrderService = null;
		}
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

	/**
	 * Config toggles (POH facilities, leprechaun, POH preference) feed the
	 * teleport/run-item computations. ConfigChanged can arrive on the EDT
	 * (config panel writes), so the teleport refresh — which reads varbits —
	 * is marshalled to the client thread; the others marshal themselves.
	 */
	/** Config keys that only affect guidance display, not planning inputs. */
	private static final java.util.Set<String> GUIDANCE_DISPLAY_KEYS = java.util.Set.of(
		"showWorldArrow", "showMinimapArrow", "showWorldMapMarker",
		"showWorldMapRoute", "showTravelHint", "useShortestPath");

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!BetterFarmingConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		if (GUIDANCE_DISPLAY_KEYS.contains(event.getKey()))
		{
			// Overlay toggles don't change teleports, run items, or the route:
			// skip the varbit re-scan and TSP replan those refreshes cost.
			if (guidanceService != null)
			{
				clientThread.invokeLater(guidanceService::refresh);
			}
			return;
		}
		if (teleportService != null)
		{
			clientThread.invokeLater(teleportService::refresh);
		}
		if (runItemsService != null)
		{
			runItemsService.recompute();
		}
		if (runOrderService != null)
		{
			runOrderService.recompute();
		}
		if (guidanceService != null)
		{
			// Overlay toggles don't change guidance state, but the marker and
			// shortest-path listeners read config and need a forced fanout.
			clientThread.invokeLater(guidanceService::refresh);
		}
	}

	@Provides
	@Singleton
	BetterFarmingConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BetterFarmingConfig.class);
	}
}
