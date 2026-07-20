package com.betterfarming.guidance;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Staging is distance-only: the recorded boarding tile's plane is
 * untrustworthy (ship decks are plane 1, the vendored tiles say 0 — Bill
 * Teach never highlighted while the stage required plane equality). Plane
 * discrimination lives in the NPC match, which compares the NPC's live
 * plane against the PLAYER's (not unit-testable without a Client).
 */
public class TravelTargetOverlayTest
{
	private static final WorldPoint BOARDING = new WorldPoint(3709, 3496, 0);

	@Test
	public void nearTheBoardingPointEntersTheNpcStage()
	{
		assertTrue("standing at the boarding point",
			TravelTargetOverlay.npcStage(BOARDING, BOARDING));
		assertTrue("on the deck one plane up — data plane must not gate",
			TravelTargetOverlay.npcStage(new WorldPoint(3711, 3498, 1), BOARDING));
		assertTrue("approaching the dock, ~12 tiles out",
			TravelTargetOverlay.npcStage(new WorldPoint(3697, 3496, 0), BOARDING));
	}

	@Test
	public void farFromTheBoardingPointStaysInTheObjectStage()
	{
		assertFalse("30 tiles out: arrow + gangplank guidance leads",
			TravelTargetOverlay.npcStage(new WorldPoint(3679, 3496, 0), BOARDING));
	}
}
