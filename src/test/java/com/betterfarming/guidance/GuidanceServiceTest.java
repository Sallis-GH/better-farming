package com.betterfarming.guidance;

import com.betterfarming.testsupport.FakeClient;
import com.betterfarming.travel.RoutePlanner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GuidanceServiceTest
{
	private static final WorldPoint FALADOR = new WorldPoint(3054, 3307, 0);
	private static final WorldPoint CATHERBY = new WorldPoint(2813, 3463, 0);
	private static final WorldPoint ARDOUGNE = new WorldPoint(2670, 3374, 0);

	private final List<RoutePlanner.Leg> legs = new ArrayList<>();
	private FakeClient client;
	private GuidanceService service;

	@Before
	public void setUp()
	{
		client = new FakeClient();
		client.setPlayerPosition(new WorldPoint(3222, 3218, 0)); // Lumbridge
		legs.clear();
		legs.addAll(Arrays.asList(
			leg("falador", "Falador herb patch", FALADOR),
			leg("catherby", "Catherby herb patch", CATHERBY),
			leg("ardougne", "Ardougne herb patch", ARDOUGNE)));
		service = new GuidanceService(() -> legs, client);
	}

	private static RoutePlanner.Leg leg(String key, String name, WorldPoint point)
	{
		return new RoutePlanner.Leg(new RoutePlanner.Stop(key, name, point), null, 10);
	}

	@Test
	public void firstLegIsCurrentInitially()
	{
		service.update();
		assertEquals("falador", service.currentLeg().stop().groupKey());
		assertEquals(1, service.currentIndex());
		assertEquals(3, service.totalLegs());
		assertFalse(service.runComplete());
	}

	@Test
	public void arrivingWithinRadiusAdvancesToNextLeg()
	{
		service.update();
		client.setPlayerPosition(new WorldPoint(FALADOR.getX() + 5, FALADOR.getY() - 5, 0));
		service.update();
		assertEquals("catherby", service.currentLeg().stop().groupKey());
		assertEquals(2, service.currentIndex());
	}

	@Test
	public void arrivalIgnoresPlaneDifference()
	{
		service.update();
		client.setPlayerPosition(new WorldPoint(FALADOR.getX(), FALADOR.getY(), 1));
		service.update();
		assertEquals("catherby", service.currentLeg().stop().groupKey());
	}

	@Test
	public void outOfOrderVisitChecksOffThatStopOnly()
	{
		service.update();
		// Player goes to Ardougne (leg 3) first.
		client.setPlayerPosition(ARDOUGNE);
		service.update();
		// Still guiding to Falador, but Ardougne no longer in remaining.
		assertEquals("falador", service.currentLeg().stop().groupKey());
		assertEquals(Arrays.asList(FALADOR, CATHERBY), service.remainingTargets());

		// After Falador and Catherby, the run is complete: Ardougne was done.
		client.setPlayerPosition(FALADOR);
		service.update();
		assertEquals("catherby", service.currentLeg().stop().groupKey());
		client.setPlayerPosition(CATHERBY);
		service.update();
		assertNull(service.currentLeg());
		assertTrue(service.runComplete());
	}

	@Test
	public void progressSurvivesRouteRecompute()
	{
		service.update();
		client.setPlayerPosition(FALADOR);
		service.update();
		assertEquals("catherby", service.currentLeg().stop().groupKey());

		// Re-plan drops the visited stop's leg order (e.g. a consumed tab
		// changed teleport availability): visited stops stay checked off.
		legs.clear();
		legs.addAll(Arrays.asList(
			leg("ardougne", "Ardougne herb patch", ARDOUGNE),
			leg("catherby", "Catherby herb patch", CATHERBY),
			leg("falador", "Falador herb patch", FALADOR)));
		client.setPlayerPosition(new WorldPoint(3100, 3300, 0));
		service.update();
		assertEquals("ardougne", service.currentLeg().stop().groupKey());
		assertEquals(Arrays.asList(ARDOUGNE, CATHERBY), service.remainingTargets());
	}

	@Test
	public void resetRestartsFromFirstLeg()
	{
		service.update();
		client.setPlayerPosition(FALADOR);
		service.update();
		assertEquals("catherby", service.currentLeg().stop().groupKey());

		client.setPlayerPosition(new WorldPoint(3222, 3218, 0));
		service.reset();
		assertEquals("falador", service.currentLeg().stop().groupKey());
	}

	@Test
	public void logoutResetsProgressViaGameState()
	{
		service.update();
		client.setPlayerPosition(FALADOR);
		service.update();

		client.setGameState(GameState.LOGIN_SCREEN);
		net.runelite.api.events.GameStateChanged event = new net.runelite.api.events.GameStateChanged();
		event.setGameState(GameState.LOGIN_SCREEN);
		service.onGameStateChanged(event);

		// Position is null while logged out; nothing to guide.
		service.update();
		assertNull(service.currentLeg());

		// Logging back in starts over from leg 1 even at the Falador patch:
		// the visit registers again immediately, which is correct behaviour.
		client.setGameState(GameState.LOGGED_IN);
		client.setPlayerPosition(new WorldPoint(3222, 3218, 0));
		service.update();
		assertEquals("falador", service.currentLeg().stop().groupKey());
	}

	@Test
	public void emptyLegsMeansNoGuidanceAndNotComplete()
	{
		legs.clear();
		service.update();
		assertNull(service.currentLeg());
		assertFalse(service.runComplete());
		assertEquals(Collections.emptyList(), service.remainingTargets());
	}

	@Test
	public void listenerNotifiedOnLegChangeOnly()
	{
		service.update();
		AtomicInteger notified = new AtomicInteger();
		service.addListener(notified::incrementAndGet);

		service.update(); // no movement, no change
		assertEquals(0, notified.get());

		client.setPlayerPosition(FALADOR);
		service.update();
		assertEquals(1, notified.get());

		service.update(); // still at Falador, already advanced
		assertEquals(1, notified.get());
	}

	@Test
	public void throwingListenerDoesNotStarveOthers()
	{
		service.update();
		AtomicInteger survivor = new AtomicInteger();
		service.addListener(() -> { throw new AssertionError("client thread only"); });
		service.addListener(survivor::incrementAndGet);

		client.setPlayerPosition(FALADOR);
		service.update();
		assertEquals(1, survivor.get());
	}
}
