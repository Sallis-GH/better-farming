package com.betterfarming.item;

import com.betterfarming.testsupport.FakeClient;
import java.util.Set;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RunePouchReaderTest
{
	/** RUNEPOUCH_RUNE enum keys are arbitrary here; only the mapping matters. */
	private static final int LAW_TYPE = 9;
	private static final int ASTRAL_TYPE = 15;

	private FakeClient client;
	private ItemTracker tracker;
	private RunePouchReader reader;

	@Before
	public void setUp()
	{
		client = new FakeClient();
		tracker = new ItemTracker();
		reader = new RunePouchReader(client, tracker);
		client.setEnumValue(EnumID.RUNEPOUCH_RUNE, LAW_TYPE, ItemID.LAWRUNE);
		client.setEnumValue(EnumID.RUNEPOUCH_RUNE, ASTRAL_TYPE, ItemID.ASTRALRUNE);
	}

	private void fillPouch(int slotType, int slotQuantity, int type, int quantity)
	{
		client.setVarbit(slotType, type);
		client.setVarbit(slotQuantity, quantity);
	}

	@Test
	public void pouchRunesCountOnlyWhileAPouchIsCarried()
	{
		fillPouch(VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_QUANTITY_1, LAW_TYPE, 300);
		reader.refresh();

		assertEquals("no pouch in the inventory: contents don't count",
			0, tracker.countOnPlayer(ItemID.LAWRUNE));

		tracker.updateContainer(ItemTracker.CONTAINER_INVENTORY,
			new Item[]{new Item(ItemID.BH_RUNE_POUCH, 1)});
		assertEquals(300, tracker.countOnPlayer(ItemID.LAWRUNE));

		tracker.updateContainer(ItemTracker.CONTAINER_INVENTORY, new Item[]{});
		assertEquals("pouch banked again: contents stop counting",
			0, tracker.countOnPlayer(ItemID.LAWRUNE));
	}

	@Test
	public void pouchAndInventoryRunesSum()
	{
		fillPouch(VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_QUANTITY_2, LAW_TYPE, 40);
		reader.refresh();
		tracker.updateContainer(ItemTracker.CONTAINER_INVENTORY, new Item[]{
			new Item(ItemID.DIVINE_RUNE_POUCH, 1), new Item(ItemID.LAWRUNE, 2)});

		assertEquals(42, tracker.countOnPlayer(ItemID.LAWRUNE));
		assertEquals("multi-id lookups see pouch runes too", 42,
			tracker.countOnPlayer(Set.of(ItemID.LAWRUNE)));
	}

	@Test
	public void emptyAndUnmappedSlotsAreIgnored()
	{
		// Slot 1: valid astral runes. Slot 3: type set but zero quantity.
		// Slot 4: quantity set but no type. Slot 5: type unknown to the enum.
		fillPouch(VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_QUANTITY_1, ASTRAL_TYPE, 25);
		client.setVarbit(VarbitID.RUNE_POUCH_TYPE_3, LAW_TYPE);
		client.setVarbit(VarbitID.RUNE_POUCH_QUANTITY_4, 100);
		fillPouch(VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_QUANTITY_5, 77, 50);
		reader.refresh();
		tracker.updateContainer(ItemTracker.CONTAINER_INVENTORY,
			new Item[]{new Item(ItemID.BH_RUNE_POUCH_TROUVER, 1)});

		assertEquals(25, tracker.countOnPlayer(ItemID.ASTRALRUNE));
		assertEquals(0, tracker.countOnPlayer(ItemID.LAWRUNE));
	}

	@Test
	public void varbitChangeRefreshesTheSnapshot()
	{
		tracker.updateContainer(ItemTracker.CONTAINER_INVENTORY,
			new Item[]{new Item(ItemID.BH_RUNE_POUCH, 1)});
		fillPouch(VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_QUANTITY_1, LAW_TYPE, 10);

		VarbitChanged event = new VarbitChanged();
		event.setVarbitId(VarbitID.RUNE_POUCH_QUANTITY_1);
		event.setValue(10);
		reader.onVarbitChanged(event);
		assertEquals(10, tracker.countOnPlayer(ItemID.LAWRUNE));

		// An unrelated varbit change does not re-read (snapshot unchanged).
		client.setVarbit(VarbitID.RUNE_POUCH_QUANTITY_1, 999);
		VarbitChanged unrelated = new VarbitChanged();
		unrelated.setVarbitId(12345);
		reader.onVarbitChanged(unrelated);
		assertEquals(10, tracker.countOnPlayer(ItemID.LAWRUNE));
	}

	@Test
	public void logoutClearsPouchContents()
	{
		fillPouch(VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_QUANTITY_1, LAW_TYPE, 300);
		reader.refresh();
		tracker.updateContainer(ItemTracker.CONTAINER_INVENTORY,
			new Item[]{new Item(ItemID.BH_RUNE_POUCH, 1)});
		assertEquals(300, tracker.countOnPlayer(ItemID.LAWRUNE));

		GameStateChanged event = new GameStateChanged();
		event.setGameState(GameState.LOGIN_SCREEN);
		tracker.onGameStateChanged(event);
		tracker.updateContainer(ItemTracker.CONTAINER_INVENTORY,
			new Item[]{new Item(ItemID.BH_RUNE_POUCH, 1)});
		assertEquals("another account's pouch must not leak", 0,
			tracker.countOnPlayer(ItemID.LAWRUNE));
	}
}
