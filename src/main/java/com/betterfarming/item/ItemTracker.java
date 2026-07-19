package com.betterfarming.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.eventbus.Subscribe;

/**
 * Tracks item counts across the three containers the equipment manager cares
 * about: inventory, worn equipment, and bank. Inventory/equipment update live
 * via ItemContainerChanged; the bank container only fires while the bank is
 * open, so its counts are a last-known snapshot (bankKnown() reports whether
 * one exists this session).
 *
 * All caches clear on LOGIN_SCREEN — a bank snapshot must not leak across an
 * account switch.
 *
 * Threading discipline mirrors the ui services: events mutate caches on the
 * client thread; listener fanout hops to the EDT with a listener snapshot.
 */
@Singleton
@Slf4j
public class ItemTracker
{
	static final int CONTAINER_INVENTORY = InventoryID.INV;
	static final int CONTAINER_EQUIPMENT = InventoryID.WORN;
	static final int CONTAINER_BANK = InventoryID.BANK;

	private final Map<Integer, Integer> inventory = new HashMap<>();
	private final Map<Integer, Integer> equipment = new HashMap<>();
	private final Map<Integer, Integer> bank = new HashMap<>();
	private boolean bankKnown = false;

	private final Set<Runnable> listeners = new LinkedHashSet<>();

	@Inject
	public ItemTracker()
	{
	}

	// ── public API ──

	/** Count in inventory + worn equipment for any of the given item ids. */
	public int countOnPlayer(Set<Integer> itemIds)
	{
		return count(inventory, itemIds) + count(equipment, itemIds);
	}

	/** Last-known bank count for any of the given item ids; 0 if bank unseen. */
	public int countBanked(Set<Integer> itemIds)
	{
		return count(bank, itemIds);
	}

	/** True when any of the given item ids is currently worn. */
	public boolean anyEquipped(int[] itemIds)
	{
		for (int id : itemIds)
		{
			if (equipment.getOrDefault(id, 0) > 0)
			{
				return true;
			}
		}
		return false;
	}

	/** True once the bank has been opened this session (bank counts are real). */
	public boolean bankKnown()
	{
		return bankKnown;
	}

	public void addListener(Runnable l)    { listeners.add(l); }
	public void removeListener(Runnable l) { listeners.remove(l); }

	// ── event subscriptions ──

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		updateContainer(event.getContainerId(), event.getItemContainer().getItems());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			inventory.clear();
			equipment.clear();
			bank.clear();
			bankKnown = false;
			notifyListeners();
		}
	}

	// ── internals (package-visible seam for tests) ──

	void updateContainer(int containerId, Item[] items)
	{
		Map<Integer, Integer> target;
		switch (containerId)
		{
			case CONTAINER_INVENTORY:
				target = inventory;
				break;
			case CONTAINER_EQUIPMENT:
				target = equipment;
				break;
			case CONTAINER_BANK:
				target = bank;
				bankKnown = true;
				break;
			default:
				return;
		}
		target.clear();
		for (Item item : items)
		{
			if (item.getId() > 0)
			{
				target.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
		notifyListeners();
	}

	private void notifyListeners()
	{
		if (listeners.isEmpty())
		{
			return;
		}
		// Snapshot on the client thread so listeners added after this update
		// don't spuriously observe it (same rationale as PatchAccessibilityService).
		List<Runnable> snapshot = new ArrayList<>(listeners);
		SwingUtilities.invokeLater(() -> {
			for (Runnable l : snapshot)
			{
				try
				{
					l.run();
				}
				catch (Exception | AssertionError ex)
				{
					log.warn("Better Farming: item listener {} threw", l.getClass().getName(), ex);
				}
			}
		});
	}

	private static int count(Map<Integer, Integer> container, Set<Integer> itemIds)
	{
		int total = 0;
		for (Integer id : itemIds)
		{
			total += container.getOrDefault(id, 0);
		}
		return total;
	}

	/** Convenience for single-id lookups. */
	public int countOnPlayer(int itemId)
	{
		return countOnPlayer(Collections.singleton(itemId));
	}

	public int countBanked(int itemId)
	{
		return countBanked(Collections.singleton(itemId));
	}
}
