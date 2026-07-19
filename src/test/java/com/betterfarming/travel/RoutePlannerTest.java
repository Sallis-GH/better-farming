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

	private static Teleport originTeleport(String name, WorldPoint origin, WorldPoint dest, int ticks)
	{
		return new Teleport(TeleportType.SHIP, origin, dest, ticks, name,
			Map.of(), Set.of(), Set.of(), Collections.emptyList(), false, null, false);
	}

	private static Teleport oncePerRunTeleport(String name, WorldPoint dest, int ticks)
	{
		return new Teleport(TeleportType.HOME_SPELL, null, dest, ticks, name,
			Map.of(), Set.of(), Set.of(), Collections.emptyList(), false, null, false, true);
	}

	@Test
	public void unreachableStopIsReachedByChainingTeleportAndShips()
	{
		// Harmony-style: island target, no direct teleport; ectophial-like
		// anywhere teleport to a port, then two ship hops.
		WorldPoint port = new WorldPoint(3690, 3490, 0);
		WorldPoint island1 = new WorldPoint(3680, 2950, 0);
		WorldPoint island2 = new WorldPoint(3790, 2830, 0);
		Teleport ectophial = anywhereTeleport("Ectophial", port, 4);
		Teleport ship = originTeleport("Ship to Mos Le'Harmless",
			new WorldPoint(3700, 3488, 0), island1, 6);
		Teleport boat = originTeleport("Boat to Harmony",
			new WorldPoint(3682, 2955, 0), island2, 2);
		RoutePlanner.Stop harmony = stop("harmony", 3794, 2836);

		List<RoutePlanner.Leg> legs = RoutePlanner.plan(
			new WorldPoint(3200, 3200, 0), List.of(harmony),
			List.of(ectophial, ship, boat));

		assertEquals(1, legs.size());
		Teleport chain = legs.get(0).teleport();
		assertEquals("chain reaches the island",
			"Ectophial → Ship to Mos Le'Harmless → Boat to Harmony", chain.displayInfo());
		assertTrue("estimate covers hops + walks, well under IMPOSSIBLE",
			legs.get(0).estimatedTicks() < 100);
	}

	@Test
	public void cheapSingleHopLegsDoNotChain()
	{
		WorldPoint dest = new WorldPoint(2810, 3400, 0);
		Teleport direct = anywhereTeleport("Falador Teleport", dest, 4);
		Teleport ship = originTeleport("Pointless ship", new WorldPoint(2812, 3402, 0),
			new WorldPoint(2805, 3398, 0), 1);
		RoutePlanner.Stop near = stop("falador", 2808, 3401);

		List<RoutePlanner.Leg> legs = RoutePlanner.plan(
			new WorldPoint(3200, 3200, 0), List.of(near), List.of(direct, ship));
		assertEquals(direct, legs.get(0).teleport());
	}

	@Test
	public void oncePerRunTeleportCarriesOnlyTheFirstLegThatUsesIt()
	{
		// Two far-apart stops, each best served by a home-teleport-entered
		// house chain (both edges share the once-per-run cooldown).
		RoutePlanner.Stop a = stop("a", 2955, 3225);
		RoutePlanner.Stop b = stop("b", 3500, 2800);
		Teleport homeChainA = new Teleport(TeleportType.POH_PORTAL, null,
			new WorldPoint(2950, 3220, 0), 20, "Home Teleport → Portal A",
			Map.of(), Set.of(), Set.of(), Collections.emptyList(), false, null, true, true);
		Teleport homeChainB = new Teleport(TeleportType.POH_PORTAL, null,
			new WorldPoint(3495, 2795, 0), 20, "Home Teleport → Portal B",
			Map.of(), Set.of(), Set.of(), Collections.emptyList(), false, null, true, true);

		// Only home chains available: the first leg keeps one, the second may
		// not use the cooldown again and degrades to an (impossible) walk.
		List<RoutePlanner.Leg> capped = RoutePlanner.plan(
			new WorldPoint(3400, 3400, 0), List.of(a, b),
			List.of(homeChainA, homeChainB));
		assertTrue(capped.get(0).teleport() != null && capped.get(0).teleport().oncePerRun());
		assertNull("cooldown spent: no teleport left for the second leg",
			capped.get(1).teleport());

		// With a reusable tab near the second stop, the re-priced leg takes it
		// (and its item would surface in the run-items list).
		Teleport tabNearB = anywhereTeleport("B teleport tab", new WorldPoint(3498, 2798, 0), 4);
		List<RoutePlanner.Leg> rerouted = RoutePlanner.plan(
			new WorldPoint(3400, 3400, 0), List.of(a, b),
			List.of(homeChainA, homeChainB, tabNearB));
		int tabLegs = 0;
		int onceLegs = 0;
		for (RoutePlanner.Leg leg : rerouted)
		{
			if (leg.teleport() == tabNearB)
			{
				tabLegs++;
			}
			else if (leg.teleport() != null && leg.teleport().oncePerRun())
			{
				onceLegs++;
			}
		}
		assertEquals("exactly one leg may ride the home-teleport cooldown", 1, onceLegs);
		assertEquals(1, tabLegs);
	}

	@Test
	public void equippedTeleportWinsTiesButNotClearlyFasterOptions()
	{
		Teleport necklace = anywhereTeleport("Skills necklace", new WorldPoint(2605, 3092, 0), 4);
		Teleport tab = anywhereTeleport("Fishing guild tab", new WorldPoint(2601, 3092, 0), 4);
		RoutePlanner.Stop stop = stop("guild", 2600, 3090);

		// Slot costs: necklace equipped = 0, tab = 1. The necklace lands a few
		// tiles further (1.5 ticks slower) — within the slot preference
		// window, so the equipped item wins.
		java.util.function.ToIntFunction<Teleport> slots =
			t -> t == necklace ? 0 : 1;
		List<RoutePlanner.Leg> legs = RoutePlanner.plan(
			new WorldPoint(3200, 3200, 0), List.of(stop), List.of(necklace, tab), 0, slots);
		assertEquals(necklace, legs.get(0).teleport());

		// A clearly faster option still wins regardless of slots.
		Teleport fastTab = anywhereTeleport("Adjacent tab", new WorldPoint(2600, 3091, 0), 1);
		legs = RoutePlanner.plan(
			new WorldPoint(3200, 3200, 0), List.of(stop), List.of(necklace, fastTab), 0,
			t -> t == necklace ? 0 : 1);
		assertEquals(fastTab, legs.get(0).teleport());
	}

	@Test
	public void planFixedOrder_keepsTheGivenSequence()
	{
		// B first even though A is nearer the start: the order is pinned.
		RoutePlanner.Stop a = stop("a", 3210, 3200);
		RoutePlanner.Stop b = stop("b", 3300, 3200);
		Teleport tele = anywhereTeleport("B Teleport", new WorldPoint(3300, 3205, 0), 4);

		List<RoutePlanner.Leg> legs = RoutePlanner.planFixedOrder(
			new WorldPoint(3200, 3200, 0), List.of(b, a), List.of(tele), 0);

		assertEquals(2, legs.size());
		assertEquals("b", legs.get(0).stop().groupKey());
		assertEquals("a", legs.get(1).stop().groupKey());
		// Per-leg teleports are still optimised within the fixed order.
		assertEquals(tele, legs.get(0).teleport());
		assertNull("45 tiles back to a — walking wins", legs.get(1).teleport());
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
	public void equalCostTeleports_preferFewerInventorySlots()
	{
		WorldPoint dest = new WorldPoint(3213, 3424, 0);
		RoutePlanner.Stop target = stop("varrock", 3210, 3420);
		// Spell: three rune stacks. Tab: one stackable slot. Same destination
		// and duration → the tab must win regardless of list order.
		Teleport spell = new Teleport(TeleportType.SPELL, null, dest, 4,
			"Varrock Teleport", Map.of(), Set.of(), Set.of(),
			List.of(
				new TeleportItemRequirement(new int[]{556}, new int[0], new int[0], 3, "Air rune"),
				new TeleportItemRequirement(new int[]{554}, new int[0], new int[0], 1, "Fire rune"),
				new TeleportItemRequirement(new int[]{563}, new int[0], new int[0], 1, "Law rune")),
			false, null, false);
		Teleport tab = new Teleport(TeleportType.ITEM, null, dest, 4,
			"Varrock tablet", Map.of(), Set.of(), Set.of(),
			List.of(new TeleportItemRequirement(new int[]{8007}, new int[0], new int[0], 1, "Item 8007")),
			true, null, false);

		List<RoutePlanner.Leg> spellFirst = RoutePlanner.plan(
			new WorldPoint(3000, 3000, 0), List.of(target), List.of(spell, tab));
		assertEquals("Varrock tablet", spellFirst.get(0).teleport().displayInfo());

		List<RoutePlanner.Leg> tabFirst = RoutePlanner.plan(
			new WorldPoint(3000, 3000, 0), List.of(target), List.of(tab, spell));
		assertEquals("Varrock tablet", tabFirst.get(0).teleport().displayInfo());
	}

	@Test
	public void clearlyFasterTeleport_beatsSmallerFootprint()
	{
		RoutePlanner.Stop target = stop("target", 2800, 3400);
		Teleport fastSpell = new Teleport(TeleportType.SPELL, null,
			new WorldPoint(2801, 3400, 0), 4, "On-target Spell", Map.of(), Set.of(), Set.of(),
			List.of(
				new TeleportItemRequirement(new int[]{556}, new int[0], new int[0], 3, "Air rune"),
				new TeleportItemRequirement(new int[]{563}, new int[0], new int[0], 1, "Law rune")),
			false, null, false);
		Teleport farTab = new Teleport(TeleportType.ITEM, null,
			new WorldPoint(2801, 3440, 0), 4, "Far tablet", Map.of(), Set.of(), Set.of(),
			List.of(new TeleportItemRequirement(new int[]{8007}, new int[0], new int[0], 1, "Item 8007")),
			true, null, false);

		List<RoutePlanner.Leg> legs = RoutePlanner.plan(
			new WorldPoint(3000, 3000, 0), List.of(target), List.of(farTab, fastSpell));
		assertEquals("40 tiles of extra walking is no tie", "On-target Spell",
			legs.get(0).teleport().displayInfo());
	}

	@Test
	public void walkTicks_impossibleBeyondCap()
	{
		assertTrue(RoutePlanner.walkTicks(
			new WorldPoint(3200, 3200, 0), new WorldPoint(2500, 3200, 0))
			>= RoutePlanner.IMPOSSIBLE);
	}
}
