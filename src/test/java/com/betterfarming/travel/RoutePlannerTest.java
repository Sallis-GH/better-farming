package com.betterfarming.travel;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RoutePlannerTest
{
	private static Teleport anywhereTeleport(String name, WorldPoint dest, int ticks)
	{
		return new Teleport(TeleportType.SPELL, null, dest, ticks, name,
			Map.of(), Set.of(), Set.of(), Collections.emptyList(), false, null, false);
	}

	private static Teleport pohTeleport(String name, WorldPoint dest, int ticks)
	{
		return new Teleport(TeleportType.POH_PORTAL, null, dest, ticks, name,
			Map.of(), Set.of(), Set.of(), Collections.emptyList(), false, null, true);
	}

	private static RoutePlanner.Stop stop(String key, int x, int y)
	{
		return new RoutePlanner.Stop(key, key, new WorldPoint(x, y, 0));
	}

	@Test
	public void emptyStops_emptyPlan()
	{
		assertTrue(RoutePlanner.plan(new WorldPoint(3200, 3200, 0),
			List.of(), List.of()).isEmpty());
	}

	@Test
	public void nearbyStop_walkBeatsTeleport()
	{
		RoutePlanner.Stop near = stop("near", 3210, 3210);
		Teleport farTele = anywhereTeleport("Far Teleport", new WorldPoint(2500, 2500, 0), 4);

		List<RoutePlanner.Leg> legs = RoutePlanner.plan(
			new WorldPoint(3200, 3200, 0), List.of(near), List.of(farTele));

		assertEquals(1, legs.size());
		assertNull("10 tiles away — walk, don't teleport", legs.get(0).teleport());
	}

	@Test
	public void distantStop_requiresTeleport()
	{
		RoutePlanner.Stop far = stop("far", 2800, 3400);
		Teleport tele = anywhereTeleport("Falador Teleport", new WorldPoint(2810, 3400, 0), 4);

		List<RoutePlanner.Leg> legs = RoutePlanner.plan(
			new WorldPoint(3200, 3200, 0), List.of(far), List.of(tele));

		assertNotNull(legs.get(0).teleport());
		assertEquals("Falador Teleport", legs.get(0).teleport().displayInfo());
	}

	@Test
	public void ordering_minimisesTotalCost()
	{
		// Start at Lumbridge-ish. Stop A is close; B is far but has a teleport
		// landing on it; C is near B. Optimal: A (walk) then B (teleport) then
		// C (walk) — NOT the input order C, B, A.
		RoutePlanner.Stop a = stop("A", 3220, 3220);
		RoutePlanner.Stop b = stop("B", 2805, 3400);
		RoutePlanner.Stop c = stop("C", 2815, 3420);
		Teleport teleB = anywhereTeleport("B Teleport", new WorldPoint(2805, 3401, 0), 4);

		List<RoutePlanner.Leg> legs = RoutePlanner.plan(
			new WorldPoint(3210, 3210, 0), List.of(c, b, a), List.of(teleB));

		assertEquals("A", legs.get(0).stop().groupKey());
		assertEquals("B", legs.get(1).stop().groupKey());
		assertEquals("C", legs.get(2).stop().groupKey());
		assertNull(legs.get(0).teleport());
		assertNotNull(legs.get(1).teleport());
		assertNull(legs.get(2).teleport());
	}

	@Test
	public void originTeleport_costsIncludeWalkToOrigin()
	{
		// A network node 5 tiles from start; teleporting via it should win over
		// an anywhere-teleport landing 100 tiles from the target.
		RoutePlanner.Stop target = stop("target", 2461, 3444);
		Teleport network = new Teleport(TeleportType.SPIRIT_TREE,
			new WorldPoint(3205, 3205, 0), new WorldPoint(2461, 3443, 0), 6,
			"Spirit tree", Map.of(), Set.of(), Set.of(), Collections.emptyList(), false,
			null, false);
		Teleport badTele = anywhereTeleport("Bad Teleport", new WorldPoint(2461, 3344, 0), 4);

		List<RoutePlanner.Leg> legs = RoutePlanner.plan(
			new WorldPoint(3200, 3200, 0), List.of(target), List.of(network, badTele));

		assertEquals("Spirit tree", legs.get(0).teleport().displayInfo());
	}

	@Test
	public void largeRunUsesHeuristic_andVisitsEveryStop()
	{
		// 16 stops → beyond the exact limit; ensure heuristic still covers all.
		java.util.ArrayList<RoutePlanner.Stop> stops = new java.util.ArrayList<>();
		java.util.ArrayList<Teleport> teleports = new java.util.ArrayList<>();
		for (int i = 0; i < 16; i++)
		{
			int x = 2600 + i * 60;
			stops.add(stop("s" + i, x, 3300));
			teleports.add(anywhereTeleport("T" + i, new WorldPoint(x, 3301, 0), 4));
		}
		List<RoutePlanner.Leg> legs = RoutePlanner.plan(
			new WorldPoint(3200, 3200, 0), stops, teleports);

		assertEquals(16, legs.size());
		assertEquals(16, legs.stream().map(l -> l.stop().groupKey()).distinct().count());
	}

	@Test
	public void preferPoh_choosesHouseChainWithinTolerance()
	{
		RoutePlanner.Stop target = stop("target", 2805, 3400);
		Teleport direct = anywhereTeleport("Direct Teleport", new WorldPoint(2805, 3401, 0), 4);
		// House chain lands 20 tiles off → 10 ticks walking + 12 duration = 22
		// vs direct 4.5; within a 20-tick bias it should still win.
		Teleport viaHouse = pohTeleport("Teleport to House → Portal",
			new WorldPoint(2805, 3421, 0), 12);

		List<RoutePlanner.Leg> unbiased = RoutePlanner.plan(
			new WorldPoint(3200, 3200, 0), List.of(target), List.of(direct, viaHouse));
		assertEquals("Direct Teleport", unbiased.get(0).teleport().displayInfo());

		List<RoutePlanner.Leg> biased = RoutePlanner.plan(
			new WorldPoint(3200, 3200, 0), List.of(target), List.of(direct, viaHouse), 20);
		assertEquals("Teleport to House → Portal", biased.get(0).teleport().displayInfo());
	}

	@Test
	public void preferPoh_stillRejectsHouseChainBeyondTolerance()
	{
		RoutePlanner.Stop target = stop("target", 2805, 3400);
		Teleport direct = anywhereTeleport("Direct Teleport", new WorldPoint(2805, 3401, 0), 4);
		Teleport viaHouse = pohTeleport("Teleport to House → Portal",
			new WorldPoint(2805, 3521, 0), 12); // 120 tiles off → way past tolerance

		List<RoutePlanner.Leg> biased = RoutePlanner.plan(
			new WorldPoint(3200, 3200, 0), List.of(target), List.of(direct, viaHouse), 20);
		assertEquals("Direct Teleport", biased.get(0).teleport().displayInfo());
	}

	@Test
	public void walkTicks_impossibleBeyondCap()
	{
		assertTrue(RoutePlanner.walkTicks(
			new WorldPoint(3200, 3200, 0), new WorldPoint(2500, 3200, 0))
			>= RoutePlanner.IMPOSSIBLE);
	}
}
