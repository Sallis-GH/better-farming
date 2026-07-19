package com.betterfarming.guidance;

import com.betterfarming.BetterFarmingConfig;
import com.betterfarming.testsupport.FakeClient;
import com.betterfarming.travel.RoutePlanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ShortestPathBridgeTest
{
	private static final WorldPoint FALADOR = new WorldPoint(3054, 3307, 0);

	/** Captures shortestpath plugin messages the bridge posts. */
	public static class Capture
	{
		final List<PluginMessage> messages = new ArrayList<>();

		@Subscribe
		public void onPluginMessage(PluginMessage message)
		{
			if ("shortestpath".equals(message.getNamespace()))
			{
				messages.add(message);
			}
		}
	}

	private final List<RoutePlanner.Leg> legs = new ArrayList<>();
	private FakeClient client;
	private GuidanceService guidance;
	private Capture capture;
	private ShortestPathBridge bridge;
	private boolean useShortestPath = true;

	@Before
	public void setUp()
	{
		client = new FakeClient();
		client.setPlayerPosition(new WorldPoint(3222, 3218, 0));
		guidance = new GuidanceService(() -> legs, client);
		EventBus eventBus = new EventBus();
		capture = new Capture();
		eventBus.register(capture);
		BetterFarmingConfig config = new BetterFarmingConfig()
		{
			@Override
			public boolean useShortestPath()
			{
				return useShortestPath;
			}
		};
		bridge = new ShortestPathBridge(eventBus, config, guidance, client);
	}

	private void addLeg()
	{
		legs.add(new RoutePlanner.Leg(
			new RoutePlanner.Stop("falador", "Falador herb patch", FALADOR), null, 10));
	}

	@Test
	public void postsPathOnceAndDedupesRepeats()
	{
		addLeg();
		guidance.update();
		bridge.update();
		bridge.update();
		assertEquals(1, capture.messages.size());
		PluginMessage m = capture.messages.get(0);
		assertEquals("path", m.getName());
		Map<String, Object> data = m.getData();
		assertEquals(FALADOR, data.get("target"));
		assertEquals(client.getPlayerPosition(), data.get("start"));
	}

	@Test
	public void clearIsNoOpWhenNothingWasPosted()
	{
		// A user-set Shortest Path route must survive our shutdown when we
		// never posted anything ourselves.
		bridge.clear();
		assertTrue(capture.messages.isEmpty());

		useShortestPath = false;
		addLeg();
		guidance.update();
		bridge.update();
		bridge.clear();
		assertTrue(capture.messages.isEmpty());
	}

	@Test
	public void arrivalClearsThePostedPath()
	{
		addLeg();
		guidance.update();
		bridge.update();
		client.setPlayerPosition(FALADOR);
		guidance.update();
		bridge.update();
		assertEquals(2, capture.messages.size());
		assertEquals("clear", capture.messages.get(1).getName());

		// Already cleared: no duplicate clear.
		bridge.clear();
		assertEquals(2, capture.messages.size());
	}

	@Test
	public void logoutRetractsAndReloginReposts()
	{
		addLeg();
		guidance.update();
		bridge.update();
		client.setGameState(GameState.LOGIN_SCREEN);
		guidance.update();
		bridge.update();
		assertEquals("clear", capture.messages.get(1).getName());

		client.setGameState(GameState.LOGGED_IN);
		guidance.update();
		bridge.update();
		assertEquals(3, capture.messages.size());
		assertEquals("path", capture.messages.get(2).getName());
	}
}
