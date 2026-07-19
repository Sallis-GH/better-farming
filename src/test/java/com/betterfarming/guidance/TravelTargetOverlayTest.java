package com.betterfarming.guidance;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TravelTargetOverlayTest
{
	private static final WorldPoint BOARDING = new WorldPoint(3702, 3488, 1);

	@Test
	public void aboardMeansOnTheBoardingTilePlaneAndClose()
	{
		assertTrue("standing at the boarding point",
			TravelTargetOverlay.npcStage(BOARDING, BOARDING));
		assertTrue("a couple of tiles onto the deck",
			TravelTargetOverlay.npcStage(new WorldPoint(3704, 3490, 1), BOARDING));
	}

	@Test
	public void approachingFromTheDockIsNotAboard()
	{
		assertFalse("still 12 tiles out on the dock: gangplank stage",
			TravelTargetOverlay.npcStage(new WorldPoint(3690, 3488, 1), BOARDING));
	}

	@Test
	public void wrongPlaneIsNotAboard()
	{
		assertFalse("under the deck / dock level: the gangplank hasn't been crossed",
			TravelTargetOverlay.npcStage(new WorldPoint(3702, 3488, 0), BOARDING));
	}
}
