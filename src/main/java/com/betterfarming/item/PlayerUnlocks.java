package com.betterfarming.item;

import com.betterfarming.ui.ClientLevelSource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;

/**
 * Tracks farming-relevant account unlocks that live in varbits rather than
 * quest state. Currently: Barbarian Training's bare-handed planting
 * (BRUT_FARMING_PLANTING == 3 — the section must be COMPLETE; at 1-2 the
 * player can attempt bare-handed planting but it may fail), which removes the
 * seed dibber from run items.
 *
 * Reads varbits on the client thread (events arrive there); listener fanout
 * hops to the EDT like the other item-layer services.
 */
@Singleton
@Slf4j
public class PlayerUnlocks
{
	private final ClientLevelSource client;
	private final Set<Runnable> listeners = new LinkedHashSet<>();
	private volatile boolean bareHandedPlanting = false;

	@Inject
	public PlayerUnlocks(ClientLevelSource client)
	{
		this.client = client;
	}

	/** True when planting ground crops needs no seed dibber (never fails). */
	public boolean bareHandedPlanting()
	{
		return bareHandedPlanting;
	}

	public void addListener(Runnable l)    { listeners.add(l); }
	public void removeListener(Runnable l) { listeners.remove(l); }

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			refresh();
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			update(false);
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarbitId() == VarbitID.BRUT_FARMING_PLANTING)
		{
			refresh();
		}
	}

	/** Re-read the varbits (client thread). */
	public void refresh()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		update(client.getVarbitValue(VarbitID.BRUT_FARMING_PLANTING) == 3);
	}

	private void update(boolean unlocked)
	{
		if (unlocked == bareHandedPlanting)
		{
			return;
		}
		bareHandedPlanting = unlocked;
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
					log.warn("Better Farming: unlocks listener {} threw", l.getClass().getName(), ex);
				}
			}
		});
	}
}
