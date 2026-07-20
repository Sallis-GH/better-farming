package com.betterfarming.travel;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Full-pipeline routing regression over the real vendored transport data
 * (in-game report, 2026-07-20): from Lumbridge with no direct Harmony
 * teleport, the plan suggested walking to Port Sarim for a charter — the
 * chain search was gated on outright unreachability, so any single hop
 * "covering" the leg via a huge straight-line walk (across the sea!)
 * suppressed the far faster ectophial → Bill Teach → Tranquility chain.
 */
public class HarmonyRouteRegressionTest
{
	private static final WorldPoint LUMBRIDGE = new WorldPoint(3222, 3218, 0);
	private static final WorldPoint HARMONY = new WorldPoint(3794, 2836, 0);

	@Test
	public void harmonyWithoutDirectTeleportsRidesTheEctophialChain() throws Exception
	{
		List<Teleport> all = new TeleportLoader().loadAll();
		// The reported session: no Harmony tablet/spell/POH portal. The
		// Brother Tranquility boat also displays "Harmony Island" (curated
		// TRANSPORT row) and must stay — it is the only way ashore.
		List<Teleport> restricted = new ArrayList<>();
		for (Teleport t : all)
		{
			String label = t.displayLabel() == null ? "" : t.displayLabel();
			if (label.contains("Harmony Island") && t.type() != TeleportType.TRANSPORT)
			{
				continue;
			}
			restricted.add(t);
		}

		RoutePlanner.Stop harmony = new RoutePlanner.Stop(
			"ALLOTMENT|Harmony Island", "Harmony Island", HARMONY);
		List<RoutePlanner.Leg> legs = RoutePlanner.plan(
			LUMBRIDGE, List.of(harmony), restricted);
		assertEquals(1, legs.size());
		RoutePlanner.Leg leg = legs.get(0);

		assertNotNull("island leg must be a multi-hop chain", leg.teleport());
		assertNotNull("single hop + sea-crossing walk must not win",
			leg.teleport().chainHops());
		assertTrue("estimate well under the 84-tick sea-walk option: " + leg.estimatedTicks(),
			leg.estimatedTicks() < 60);

		// Also without the Mos le'harmless teleport scroll (most players
		// don't own one): the ectophial → Bill Teach → Tranquility chain.
		List<Teleport> noScroll = new ArrayList<>();
		for (Teleport t : restricted)
		{
			String label = t.displayLabel() == null ? "" : t.displayLabel();
			if (!label.toLowerCase().contains("mos le'harmless teleport"))
			{
				noScroll.add(t);
			}
		}
		RoutePlanner.Leg ecto = RoutePlanner.plan(
			LUMBRIDGE, List.of(harmony), noScroll).get(0);
		assertNotNull(ecto.teleport());
		String display = ecto.teleport().displayLabel();
		assertTrue("chain starts with the ectophial, got: " + display,
			display.startsWith("Ectophial"));
		assertTrue("estimate ~45 ticks: " + ecto.estimatedTicks(), ecto.estimatedTicks() < 60);
	}

	@Test
	public void directHarmonyTabletStillWinsWhenAvailable() throws Exception
	{
		List<Teleport> all = new TeleportLoader().loadAll();
		RoutePlanner.Stop harmony = new RoutePlanner.Stop(
			"ALLOTMENT|Harmony Island", "Harmony Island", HARMONY);
		List<RoutePlanner.Leg> legs = RoutePlanner.plan(
			LUMBRIDGE, List.of(harmony), all);
		assertEquals("Harmony Island tablet", legs.get(0).teleport().displayLabel());
	}
}
