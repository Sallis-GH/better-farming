package com.betterfarming.guidance;

import com.betterfarming.travel.RoutePlanner;
import com.betterfarming.travel.Teleport;
import com.betterfarming.travel.TeleportType;
import java.util.Collections;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TravelHintTest
{
	private static final WorldPoint DEST = new WorldPoint(3054, 3307, 0);

	private static RoutePlanner.Leg leg(Teleport teleport)
	{
		return new RoutePlanner.Leg(
			new RoutePlanner.Stop("falador", "Falador herb patch", DEST), teleport, 10);
	}

	private static Teleport teleport(TeleportType type, String displayInfo, boolean viaPoh)
	{
		return new Teleport(type, null, DEST, 4, displayInfo,
			Collections.emptyMap(), Collections.emptySet(), Collections.emptySet(),
			Collections.emptyList(), false, null, viaPoh);
	}

	@Test
	public void nullLegHasNoHint()
	{
		assertNull(TravelHint.text(null));
	}

	@Test
	public void walkingLegNamesTheStop()
	{
		assertEquals("Walk to Falador herb patch", TravelHint.text(leg(null)));
	}

	@Test
	public void spellsAreCast()
	{
		assertEquals("Cast Falador Teleport",
			TravelHint.text(leg(teleport(TeleportType.SPELL, "Falador Teleport", false))));
	}

	@Test
	public void tabletsAreBroken()
	{
		assertEquals("Break Falador teleport tablet",
			TravelHint.text(leg(teleport(TeleportType.ITEM, "Falador teleport tablet", false))));
	}

	@Test
	public void nonTabletItemsAreUsed()
	{
		assertEquals("Use Explorer's ring: Cabbage Patch",
			TravelHint.text(leg(teleport(TeleportType.ITEM, "Explorer's ring: Cabbage Patch", false))));
	}

	@Test
	public void houseChainsShowTheComposedDisplayAsIs()
	{
		assertEquals("Teleport to House → Ornate Jewellery Box: Falador Park",
			TravelHint.text(leg(teleport(TeleportType.JEWELLERY_BOX,
				"Teleport to House → Ornate Jewellery Box: Falador Park", true))));
	}

	@Test
	public void networkTypesGetTypePrefix()
	{
		assertEquals("Fairy ring: CKR",
			TravelHint.text(leg(teleport(TeleportType.FAIRY_RING, "CKR", false))));
		assertEquals("Spirit tree: Tree Gnome Village",
			TravelHint.text(leg(teleport(TeleportType.SPIRIT_TREE, "Tree Gnome Village", false))));
	}

	@Test
	public void missingDisplayInfoFallsBackToHumanizedType()
	{
		assertEquals("Cast Home Spell",
			TravelHint.text(leg(teleport(TeleportType.HOME_SPELL, null, false))));
	}
}
