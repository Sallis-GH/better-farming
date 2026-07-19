package com.betterfarming.item;

import com.betterfarming.travel.Teleport;
import com.betterfarming.travel.TeleportItemRequirement;
import com.betterfarming.travel.TeleportType;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Item;
import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TeleportItemCheckTest
{
	private static final int LAW_RUNE = 563;
	private static final int AIR_RUNE = 556;
	private static final int AIR_STAFF = 1381;
	private static final int FALADOR_TAB = 8009;
	private static final int COINS = 995;

	private ItemTracker tracker;

	@Before
	public void setUp()
	{
		tracker = new ItemTracker();
	}

	private static Teleport teleport(String name, TeleportItemRequirement... reqs)
	{
		return new Teleport(TeleportType.ITEM, null, new WorldPoint(2966, 3379, 0), 4,
			name, Map.of(), Set.of(), Set.of(), List.of(reqs), true, null, false);
	}

	private static TeleportItemRequirement req(String name, int quantity, int... ids)
	{
		return new TeleportItemRequirement(ids, new int[0], new int[0], quantity, name);
	}

	@Test
	public void walkLegAndCoveredTeleportReportNothing()
	{
		assertTrue(TeleportItemCheck.missingOnPlayer(null, tracker).isEmpty());

		tracker.updateContainer(ItemTracker.CONTAINER_INVENTORY,
			new Item[]{new Item(FALADOR_TAB, 1)});
		Teleport tab = teleport("Falador tablet", req("Falador tablet", 1, FALADOR_TAB));
		assertTrue(TeleportItemCheck.missingOnPlayer(tab, tracker).isEmpty());
	}

	@Test
	public void missingTabletIsReportedByName()
	{
		Teleport tab = teleport("Falador tablet", req("Falador tablet", 1, FALADOR_TAB));
		assertEquals(List.of("Falador tablet"),
			TeleportItemCheck.missingOnPlayer(tab, tracker));
	}

	@Test
	public void bankedItemsDoNotCount()
	{
		tracker.updateContainer(ItemTracker.CONTAINER_BANK,
			new Item[]{new Item(FALADOR_TAB, 4)});
		Teleport tab = teleport("Falador tablet", req("Falador tablet", 1, FALADOR_TAB));
		assertEquals("in the bank is not on the player", List.of("Falador tablet"),
			TeleportItemCheck.missingOnPlayer(tab, tracker));
	}

	@Test
	public void quantityShortfallIsMissing()
	{
		tracker.updateContainer(ItemTracker.CONTAINER_INVENTORY,
			new Item[]{new Item(LAW_RUNE, 1)});
		Teleport spell = teleport("Falador Teleport",
			req("Law rune", 1, LAW_RUNE), req("Air rune", 3, AIR_RUNE));
		assertEquals(List.of("Air rune"), TeleportItemCheck.missingOnPlayer(spell, tracker));
	}

	@Test
	public void equippedStaffSubstitutesForRunes()
	{
		TeleportItemRequirement airWithStaff = new TeleportItemRequirement(
			new int[]{AIR_RUNE}, new int[]{AIR_STAFF}, new int[0], 3, "Air rune");
		tracker.updateContainer(ItemTracker.CONTAINER_EQUIPMENT,
			new Item[]{new Item(AIR_STAFF, 1)});
		tracker.updateContainer(ItemTracker.CONTAINER_INVENTORY,
			new Item[]{new Item(LAW_RUNE, 1)});
		Teleport spell = teleport("Falador Teleport",
			req("Law rune", 1, LAW_RUNE), airWithStaff);
		assertTrue(TeleportItemCheck.missingOnPlayer(spell, tracker).isEmpty());
	}

	@Test
	public void rawItemNamesPrettifyFromTheTeleportLabel()
	{
		Teleport tab = teleport("Varrock tablet: Grand Exchange",
			req("Item 8007", 1, 8007));
		assertEquals(List.of("Varrock tablet"),
			TeleportItemCheck.missingOnPlayer(tab, tracker));
	}

	@Test
	public void chainChecksMergedQuantitiesButLabelsPerHop()
	{
		// Two 2 500-coin fares merge to one 5 000-coin need: carrying 2 600
		// covers either fare alone but not the chain.
		TeleportItemRequirement fare = req("Coins", 2500, COINS);
		Teleport hopA = new Teleport(TeleportType.CHARTER_SHIP, new WorldPoint(3000, 3000, 0),
			new WorldPoint(3100, 3000, 0), 6, "Charter A", Map.of(), Set.of(), Set.of(),
			List.of(fare), false, null, false);
		Teleport hopB = new Teleport(TeleportType.CHARTER_SHIP, new WorldPoint(3102, 3000, 0),
			new WorldPoint(3200, 3000, 0), 6, "Charter B", Map.of(), Set.of(), Set.of(),
			List.of(fare), false, null, false);
		Teleport chain = Teleport.chainOf(List.of(hopA, hopB), 20);

		tracker.updateContainer(ItemTracker.CONTAINER_INVENTORY,
			new Item[]{new Item(COINS, 2600)});
		assertEquals(List.of("Coins"), TeleportItemCheck.missingOnPlayer(chain, tracker));

		tracker.updateContainer(ItemTracker.CONTAINER_INVENTORY,
			new Item[]{new Item(COINS, 5000)});
		assertTrue(TeleportItemCheck.missingOnPlayer(chain, tracker).isEmpty());
	}

	@Test
	public void chainRawItemNameLabelsFromTheOwningHop()
	{
		Teleport ecto = new Teleport(TeleportType.ITEM, null,
			new WorldPoint(3690, 3490, 0), 4, "Ectophial", Map.of(), Set.of(), Set.of(),
			List.of(req("Item 4251", 1, 4251)), false, null, false);
		Teleport ship = new Teleport(TeleportType.SHIP, new WorldPoint(3702, 3488, 0),
			new WorldPoint(3680, 2950, 0), 6, "Sail to Mos Le'Harmless",
			Map.of(), Set.of(), Set.of(), Collections.emptyList(), false, null, false);
		Teleport chain = Teleport.chainOf(List.of(ecto, ship), 20);

		assertEquals("labelled by the hop, not the joined chain display",
			List.of("Ectophial"), TeleportItemCheck.missingOnPlayer(chain, tracker));
	}
}
