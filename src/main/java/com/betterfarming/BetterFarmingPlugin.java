package com.betterfarming;

import com.betterfarming.bank.FarmingBankTab;
import com.betterfarming.bank.FarmingBankTagService;
import com.betterfarming.data.FarmingData;
import com.betterfarming.data.PatchGroup;
import com.betterfarming.farming.PatchStateService;
import com.betterfarming.farming.PatchStateTable;
import com.betterfarming.farming.StopProgress;
import com.betterfarming.guidance.GuidanceService;
import com.betterfarming.guidance.ItemHighlightOverlay;
import com.betterfarming.guidance.PatchHighlightOverlay;
import com.betterfarming.guidance.PlantingGuide;
import com.betterfarming.guidance.GuidanceWorldMapMarker;
import com.betterfarming.guidance.MinimapArrowOverlay;
import com.betterfarming.guidance.ShortestPathBridge;
import com.betterfarming.guidance.TravelHintOverlay;
import com.betterfarming.guidance.WorldArrowOverlay;
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
	@Inject private net.runelite.client.game.ItemManager itemManager;
	@Inject private ConfigManager configManager;
	@Inject private com.google.gson.Gson gson;

	private NavigationButton navButton;
	private Runnable bankTabRefreshListener;
	private SeedAvailabilityService availabilityService;
	private PatchSelectionService selectionService;
	private PatchAccessibilityService accessibilityService;
	private RunItemsService runItemsService;
	private TeleportAvailabilityService teleportService;
	private RunOrderService runOrderService;
	private PatchStateService patchStateService;
	private Runnable patchStateListener;
	private Runnable itemTrackerRouteListener;
	private PlantingGuide plantingGuide;
	private com.betterfarming.item.RunePouchReader runePouchReader;
	private volatile BetterFarmingPanel sidebarPanel;
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

		// Crop state: live farming varbits + the Time Tracking plugin's
		// persisted observations (RS-profile scoped config reads).
		PatchStateTable stateTable = PatchStateTable.load(gson);
		patchStateService = new PatchStateService(data.patches(), clientLevelSource, stateTable,
			key -> configManager.getRSProfileConfiguration("timetracking", key),
			() -> System.currentTimeMillis() / 1000L);

		List<Teleport> teleports = teleportLoader.loadAll();
		teleportService = new TeleportAvailabilityService(teleports, clientLevelSource, itemTracker, config);
		runOrderService = new RunOrderService(
			data, selectionService, accessibilityService, teleportService, clientLevelSource,
			config, clientThread::invokeLater, patchStateService::needsVisit,
			com.betterfarming.travel.TeleportSlotCost.of(itemTracker));
		runOrderService.wire();
		// State changes re-plan the route (pinned order keeps it stable).
		patchStateListener = runOrderService::recompute;
		patchStateService.addListener(patchStateListener);
		// Equipping/unequipping changes teleport slot costs even when the
		// available set is identical, so item changes re-price the legs too
		// (coalesced; the pin keeps the order stable).
		itemTrackerRouteListener = runOrderService::recompute;
		itemTracker.addListener(itemTrackerRouteListener);
		// The planned legs feed teleport-item rows into the run-items list.
		runItemsService.setRunOrderService(runOrderService);

		// Rune pouch contents count toward spell requirements.
		runePouchReader = new com.betterfarming.item.RunePouchReader(clientLevelSource, itemTracker);

		// Services with @Subscribe methods register on the bus.
		eventBus.register(availabilityService);
		eventBus.register(accessibilityService);
		eventBus.register(itemTracker);
		eventBus.register(runePouchReader);
		eventBus.register(playerUnlocks);
		eventBus.register(teleportService);
		eventBus.register(patchStateService);

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
		List<PatchGroup> groups = PatchGroup.groupAll(data.patches());
		java.util.Map<String, PatchGroup> groupsByKey = new java.util.HashMap<>();
		groups.forEach(g -> groupsByKey.put(g.key(), g));
		guidanceService = new GuidanceService(runOrderService::legs, clientLevelSource,
			stop -> {
				PatchGroup g = groupsByKey.get(stop.groupKey());
				if (g == null)
				{
					return StopProgress.UNKNOWN;
				}
				StopProgress progress = patchStateService.groupProgress(g.patches());
				// Composting is its own step: a fully-planted stop holds
				// while a watched-planted patch awaits treatment AND the
				// player actually carries compost (the walk-away skip still
				// escapes a missed chat detection).
				if (progress == StopProgress.COMPLETE
					&& itemTracker.countOnPlayer(
						com.betterfarming.item.FarmingTools.COMPOST_VARIANTS) > 0)
				{
					for (com.betterfarming.data.Patch p : g.patches())
					{
						if (patchStateService.compostPending(p))
						{
							return StopProgress.INCOMPLETE;
						}
					}
				}
				return progress;
			});
		// Going off-plan (own teleport to a later stop) re-plans the rest of
		// the route from wherever the player actually is.
		guidanceService.setOnDeviation(runOrderService::replan);
		plantingGuide = new PlantingGuide(groups, data.seeds(), selectionService,
			patchStateService, guidanceService, clientLevelSource);
		// PlantingGuide polls per GameTick, but EventBus delivery order vs
		// GuidanceService is unspecified — recomputing again on leg change
		// keeps its highlights from lagging the arrows by a tick.
		guidanceListeners.add(plantingGuide::recompute);
		eventBus.register(plantingGuide);
		worldMapMarker = new GuidanceWorldMapMarker(worldMapPointManager, config, guidanceService, itemManager);
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
		guidanceOverlays.add(new PatchHighlightOverlay(client, config, plantingGuide));
		guidanceOverlays.add(new com.betterfarming.guidance.TravelTargetOverlay(client, config, guidanceService));
		guidanceOverlays.add(new com.betterfarming.guidance.SpellHighlightOverlay(client, config, guidanceService));
		guidanceOverlays.add(new ItemHighlightOverlay(config, plantingGuide));
		guidanceOverlays.add(new TravelHintOverlay(config, guidanceService, plantingGuide,
			itemTracker,
			() -> {
				// Progress reset and replan are independent; a failure in one
				// must not swallow the other (hard-won rule 2 in spirit).
				try
				{
					guidanceService.reset();
				}
				catch (Exception | AssertionError ex)
				{
					log.warn("Better Farming: guidance reset threw", ex);
				}
				runOrderService.replan();
			}));
		guidanceOverlays.forEach(overlayManager::add);

		// Initial pass so cards built mid-session-already-logged-in see real
		// lock state at construction. Without this, the first lock evaluation
		// would wait for a GameStateChanged or StatChanged that may never come
		// for an idle-logged-in player. Marshalled: startUp runs on the EDT
		// when the plugin is toggled from the settings panel, and all three
		// refreshes read client state (quest scripts assert client thread
		// even before the varbit reads would).
		// Locals captured on purpose: shutDown nulls the fields, and the
		// queued refresh may run after a quick toggle-off.
		PatchAccessibilityService accessibility = accessibilityService;
		TeleportAvailabilityService teleportAvailability = teleportService;
		com.betterfarming.item.RunePouchReader pouchReader = runePouchReader;
		clientThread.invokeLater(() -> {
			accessibility.refresh();
			playerUnlocks.refresh();
			teleportAvailability.refresh();
			// Pouch varbits were sent before this plugin started; read them
			// once now, then VarbitChanged keeps the snapshot fresh.
			pouchReader.refresh();
		});

		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icons/sidebar.png");
		SwingUtilities.invokeLater(() -> {
			BetterFarmingPanel panel = new BetterFarmingPanel(
				data, selectionService, availabilityService, accessibilityService,
				runItemsService, runOrderService, itemTracker, guidanceService, config);
			sidebarPanel = panel;
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
		sidebarPanel = null;
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
		if (runePouchReader != null)
		{
			eventBus.unregister(runePouchReader);
			runePouchReader = null;
		}
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
		if (plantingGuide != null)
		{
			eventBus.unregister(plantingGuide);
			plantingGuide = null;
		}
		if (patchStateService != null)
		{
			eventBus.unregister(patchStateService);
			if (patchStateListener != null)
			{
				patchStateService.removeListener(patchStateListener);
				patchStateListener = null;
			}
			patchStateService = null;
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
			// must survive this plugin shutting down. Marshalled: shutDown
			// can run on the EDT (settings toggle) and EventBus subscribers
			// run synchronously on the posting thread.
			ShortestPathBridge bridge = shortestPathBridge;
			clientThread.invokeLater(bridge::clear);
			shortestPathBridge = null;
		}
		if (itemTrackerRouteListener != null)
		{
			itemTracker.removeListener(itemTrackerRouteListener);
			itemTrackerRouteListener = null;
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
	/**
	 * A fresh session starts a fresh run: drop the pinned route so the next
	 * plan is solved from wherever the player logs in (guidance progress and
	 * the live crop-state cache reset themselves via their own subscribers).
	 */
	@Subscribe
	public void onGameStateChanged(net.runelite.api.events.GameStateChanged event)
	{
		if (event.getGameState() == net.runelite.api.GameState.LOGIN_SCREEN
			&& runOrderService != null)
		{
			runOrderService.replan();
		}
	}

	/** Config keys that only affect guidance display, not planning inputs. */
	private static final java.util.Set<String> GUIDANCE_DISPLAY_KEYS = java.util.Set.of(
		"showWorldArrow", "showMinimapArrow", "showWorldMapMarker",
		"showTravelHint", "useShortestPath", "showPlantingHighlights");

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!BetterFarmingConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		String key = event.getKey();
		if ("seedSelectionMode".equals(key) || (key != null && key.startsWith("showType")))
		{
			// Pure sidebar layout: rebuild the panel column, nothing else.
			BetterFarmingPanel panel = sidebarPanel;
			if (panel != null)
			{
				SwingUtilities.invokeLater(panel::rebuildContent);
			}
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
