package com.betterfarming.item;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.events.GameStateChanged;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ItemTrackerTest
{
	private static final int RAKE = 5341;
	private static final int SPADE = 952;
	private static final int RANARR_SEED = 5295;

	private ItemTracker tracker;

	@Before
	public void setUp()
	{
		tracker = new ItemTracker();
	}

	private static void flushEdt() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> { });
	}

	@Test
	public void emptyTracker_countsZero_bankUnknown()
	{
		assertEquals(0, tracker.countOnPlayer(RAKE));
		assertEquals(0, tracker.countBanked(RAKE));
		assertFalse(tracker.bankKnown());
	}

	@Test
	public void inventoryAndEquipment_bothCountAsOnPlayer()
	{
		tracker.updateContainer(ItemTracker.CONTAINER_INVENTORY,
			new Item[]{new Item(RAKE, 1), new Item(RANARR_SEED, 6)});
		tracker.updateContainer(ItemTracker.CONTAINER_EQUIPMENT,
			new Item[]{new Item(SPADE, 1)});

		assertEquals(1, tracker.countOnPlayer(RAKE));
		assertEquals(6, tracker.countOnPlayer(RANARR_SEED));
		assertEquals(1, tracker.countOnPlayer(SPADE));
		assertEquals("bank not seen — banked count stays 0", 0, tracker.countBanked(RAKE));
	}

	@Test
	public void multiIdLookup_sumsAcrossIdsAndContainers()
	{
		tracker.updateContainer(ItemTracker.CONTAINER_INVENTORY,
			new Item[]{new Item(RAKE, 1)});
		tracker.updateContainer(ItemTracker.CONTAINER_EQUIPMENT,
			new Item[]{new Item(SPADE, 1)});

		assertEquals(2, tracker.countOnPlayer(Set.of(RAKE, SPADE)));
	}

	@Test
	public void bankUpdate_setsBankKnown_andRetainsAfterLaterInventoryChanges()
	{
		tracker.updateContainer(ItemTracker.CONTAINER_BANK,
			new Item[]{new Item(RANARR_SEED, 40)});
		assertTrue(tracker.bankKnown());
		assertEquals(40, tracker.countBanked(RANARR_SEED));

		// Bank closes; inventory keeps changing. Bank snapshot must persist.
		tracker.updateContainer(ItemTracker.CONTAINER_INVENTORY,
			new Item[]{new Item(RANARR_SEED, 6)});
		assertEquals(40, tracker.countBanked(RANARR_SEED));
		assertEquals(6, tracker.countOnPlayer(RANARR_SEED));
	}

	@Test
	public void containerUpdate_replacesPreviousContents()
	{
		tracker.updateContainer(ItemTracker.CONTAINER_INVENTORY,
			new Item[]{new Item(RAKE, 1)});
		tracker.updateContainer(ItemTracker.CONTAINER_INVENTORY,
			new Item[]{new Item(SPADE, 1)});

		assertEquals("rake was dropped from inventory", 0, tracker.countOnPlayer(RAKE));
		assertEquals(1, tracker.countOnPlayer(SPADE));
	}

	@Test
	public void emptySlots_ignored()
	{
		// Empty inventory slots arrive as id -1 / qty 0 entries.
		tracker.updateContainer(ItemTracker.CONTAINER_INVENTORY,
			new Item[]{new Item(-1, 0), new Item(RAKE, 1), new Item(-1, 0)});

		assertEquals(1, tracker.countOnPlayer(RAKE));
		assertEquals(0, tracker.countOnPlayer(-1));
	}

	@Test
	public void unknownContainer_ignored()
	{
		tracker.updateContainer(999, new Item[]{new Item(RAKE, 1)});
		assertEquals(0, tracker.countOnPlayer(RAKE));
	}

	@Test
	public void loginScreen_clearsAllCachesAndBankKnown()
	{
		tracker.updateContainer(ItemTracker.CONTAINER_INVENTORY,
			new Item[]{new Item(RAKE, 1)});
		tracker.updateContainer(ItemTracker.CONTAINER_BANK,
			new Item[]{new Item(RANARR_SEED, 40)});

		GameStateChanged event = new GameStateChanged();
		event.setGameState(GameState.LOGIN_SCREEN);
		tracker.onGameStateChanged(event);

		assertEquals(0, tracker.countOnPlayer(RAKE));
		assertEquals(0, tracker.countBanked(RANARR_SEED));
		assertFalse(tracker.bankKnown());
	}

	@Test
	public void listeners_notifiedOnEdt_afterUpdate() throws Exception
	{
		AtomicInteger calls = new AtomicInteger();
		tracker.addListener(calls::incrementAndGet);

		tracker.updateContainer(ItemTracker.CONTAINER_INVENTORY,
			new Item[]{new Item(RAKE, 1)});
		flushEdt();
		assertEquals(1, calls.get());
	}

	@Test
	public void removedListener_notNotified() throws Exception
	{
		AtomicInteger calls = new AtomicInteger();
		Runnable l = calls::incrementAndGet;
		tracker.addListener(l);
		tracker.removeListener(l);

		tracker.updateContainer(ItemTracker.CONTAINER_INVENTORY,
			new Item[]{new Item(RAKE, 1)});
		flushEdt();
		assertEquals(0, calls.get());
	}
}
