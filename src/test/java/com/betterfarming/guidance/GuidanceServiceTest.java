package com.betterfarming.guidance;

import com.betterfarming.farming.StopProgress;
import com.betterfarming.testsupport.FakeClient;
import com.betterfarming.travel.RoutePlanner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
	public void resetWhileStandingAtPatchStillGuidesToIt()
	{
		service.update();
		client.setPlayerPosition(FALADOR);
		service.update();
		assertEquals("catherby", service.currentLeg().stop().groupKey());

		// Reset taken at the Falador patch: it must not instantly re-check
		// itself off — the new run needs to revisit it.
		service.reset();
		assertEquals("falador", service.currentLeg().stop().groupKey());
		service.update();
		assertEquals("falador", service.currentLeg().stop().groupKey());

		// Leaving and returning counts as a fresh arrival again.
		client.setPlayerPosition(new WorldPoint(3100, 3300, 0));
		service.update();
		client.setPlayerPosition(FALADOR);
		service.update();
		assertEquals("catherby", service.currentLeg().stop().groupKey());
	}

	@Test
	public void reorderedRouteWithSameStopsAndTeleportsIsNotAChange()
	{
		service.update();
		AtomicInteger notified = new AtomicInteger();
		service.addListener(notified::incrementAndGet);

		// Equal-but-not-identical legs (fresh Leg/Stop instances, same data),
		// as produced by a route recompute: no fanout.
		List<RoutePlanner.Leg> rebuilt = Arrays.asList(
			leg("falador", "Falador herb patch", FALADOR),
			leg("catherby", "Catherby herb patch", CATHERBY),
			leg("ardougne", "Ardougne herb patch", ARDOUGNE));
		legs.clear();
		legs.addAll(rebuilt);
		service.update();
		assertEquals(0, notified.get());
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

	// ── chain waypoint guidance ──

	@Test
	public void chainLegGuidesOneWaypointAtATime()
	{
		// Harmony-style: ectophial (anywhere) → ship at dock A → boat at dock B.
		com.betterfarming.travel.Teleport ecto = new com.betterfarming.travel.Teleport(
			com.betterfarming.travel.TeleportType.ITEM, null,
			new WorldPoint(3690, 3490, 0), 4, "Ectophial",
			java.util.Map.of(), java.util.Set.of(), java.util.Set.of(),
			Collections.emptyList(), false, null, false);
		com.betterfarming.travel.Teleport ship = new com.betterfarming.travel.Teleport(
			com.betterfarming.travel.TeleportType.SHIP,
			new WorldPoint(3702, 3488, 0), new WorldPoint(3680, 2950, 0), 6,
			"Sail to Mos Le'Harmless",
			java.util.Map.of(), java.util.Set.of(), java.util.Set.of(),
			Collections.emptyList(), false, "Travel Bill Teach 4016", false);
		com.betterfarming.travel.Teleport boat = new com.betterfarming.travel.Teleport(
			com.betterfarming.travel.TeleportType.TRANSPORT,
			new WorldPoint(3682, 2955, 0), new WorldPoint(3790, 2830, 0), 2,
			"Harmony Island",
			java.util.Map.of(), java.util.Set.of(), java.util.Set.of(),
			Collections.emptyList(), false, "Transport Brother Tranquility 550", false);
		com.betterfarming.travel.Teleport chain =
			com.betterfarming.travel.Teleport.chainOf(List.of(ecto, ship, boat), 30);
		WorldPoint harmonyPatch = new WorldPoint(3794, 2836, 0);
		legs.clear();
		legs.add(new RoutePlanner.Leg(
			new RoutePlanner.Stop("harmony", "Harmony herb patch", harmonyPatch), chain, 30));

		// Far away: first instruction is casting the ectophial.
		client.setPlayerPosition(new WorldPoint(3222, 3218, 0));
		service.update();
		assertEquals(ecto.destination(), service.travelTarget());
		assertEquals(ecto, service.travelHop());

		// Landed at Port Phasmatys: head for the gangplank / Bill Teach.
		client.setPlayerPosition(new WorldPoint(3690, 3490, 0));
		service.update();
		assertEquals(ship.origin(), service.travelTarget());
		assertEquals(ship, service.travelHop());

		// On Mos Le'Harmless: Brother Tranquility next.
		client.setPlayerPosition(new WorldPoint(3680, 2950, 0));
		service.update();
		assertEquals(boat.origin(), service.travelTarget());
		assertEquals(boat, service.travelHop());

		// On Harmony (outside the patch's arrival radius): walk to the
		// patch, no hop left.
		client.setPlayerPosition(new WorldPoint(3775, 2850, 0));
		service.update();
		assertEquals(harmonyPatch, service.travelTarget());
		assertNull(service.travelHop());

		// At the patch: the leg completes as usual.
		client.setPlayerPosition(harmonyPatch);
		service.update();
		assertNull(service.currentLeg());
	}

	@Test
	public void singleTeleportLegTargetsItsOriginWhenBoardingStyle()
	{
		com.betterfarming.travel.Teleport spiritTree = new com.betterfarming.travel.Teleport(
			com.betterfarming.travel.TeleportType.SPIRIT_TREE,
			new WorldPoint(3183, 3508, 0), new WorldPoint(2542, 3170, 0), 5,
			"Spirit tree: Tree Gnome Village",
			java.util.Map.of(), java.util.Set.of(), java.util.Set.of(),
			Collections.emptyList(), false, null, false);
		legs.clear();
		legs.add(new RoutePlanner.Leg(
			new RoutePlanner.Stop("village", "Village fruit tree", new WorldPoint(2490, 3180, 0)),
			spiritTree, 20));
		client.setPlayerPosition(new WorldPoint(3222, 3450, 0));
		service.update();
		assertEquals("walk to the tree first", spiritTree.origin(), service.travelTarget());
		assertEquals(spiritTree, service.travelHop());
	}

	// ── crop-state driven completion ──

	private final Map<String, StopProgress> progress = new HashMap<>();

	private GuidanceService stateAwareService()
	{
		return new GuidanceService(() -> legs, client,
			stop -> progress.getOrDefault(stop.groupKey(), StopProgress.UNKNOWN));
	}

	@Test
	public void incompleteStopIsNotCompletedByStandingOnIt()
	{
		GuidanceService s = stateAwareService();
		progress.put("falador", StopProgress.INCOMPLETE);
		client.setPlayerPosition(FALADOR);
		s.update();
		// Work remains (patch empty): stay on this leg until planted.
		assertEquals("falador", s.currentLeg().stop().groupKey());
	}

	@Test
	public void plantingCompletesTheStop()
	{
		GuidanceService s = stateAwareService();
		progress.put("falador", StopProgress.INCOMPLETE);
		client.setPlayerPosition(FALADOR);
		s.update();
		progress.put("falador", StopProgress.COMPLETE);
		s.update();
		assertEquals("catherby", s.currentLeg().stop().groupKey());
	}

	@Test
	public void walkingFarAwayFromAReachedIncompleteStopSkipsIt()
	{
		GuidanceService s = stateAwareService();
		progress.put("falador", StopProgress.INCOMPLETE);
		client.setPlayerPosition(FALADOR);
		s.update();
		assertEquals("falador", s.currentLeg().stop().groupKey());

		// Leaving a little (banking nearby) is not a skip...
		client.setPlayerPosition(new WorldPoint(FALADOR.getX() + 30, FALADOR.getY(), 0));
		s.update();
		assertEquals("falador", s.currentLeg().stop().groupKey());

		// ...teleporting far away is: no seeds, moving on.
		client.setPlayerPosition(new WorldPoint(3222, 3218, 0));
		s.update();
		assertEquals("catherby", s.currentLeg().stop().groupKey());
	}

	@Test
	public void completedStopThatRegressesIsUncheckedAgain()
	{
		GuidanceService s = stateAwareService();
		progress.put("falador", StopProgress.COMPLETE);
		s.update();
		assertEquals("catherby", s.currentLeg().stop().groupKey());

		// Harvested in passing / diseased: the stop needs work again.
		progress.put("falador", StopProgress.INCOMPLETE);
		s.update();
		assertEquals("falador", s.currentLeg().stop().groupKey());
	}

	@Test
	public void remoteCompletionChecksOffOutOfOrderStops()
	{
		GuidanceService s = stateAwareService();
		s.update();
		// Ardougne planted earlier (state says complete) without going near it.
		progress.put("ardougne", StopProgress.COMPLETE);
		s.update();
		assertEquals("falador", s.currentLeg().stop().groupKey());
		assertEquals(Arrays.asList(FALADOR, CATHERBY), s.remainingTargets());
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
