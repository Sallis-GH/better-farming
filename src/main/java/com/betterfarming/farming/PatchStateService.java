package com.betterfarming.farming;

import com.betterfarming.data.Patch;
import com.betterfarming.ui.ClientLevelSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;

/**
 * Tracks per-patch crop state from two sources:
 *
 * 1. Live farming varbits — valid only while the player stands in one of the
 *    patch's FarmingRegion map regions (the FARMING_TRANSMIT_* slots are
 *    region-scoped; reading them elsewhere gives another region's patches).
 *    Observations are cached for the session.
 * 2. The Time Tracking plugin's persisted observations (config group
 *    "timetracking", RS-profile scoped, key "&lt;regionId&gt;.&lt;varbitId&gt;", value
 *    "&lt;varbitValue&gt;:&lt;unixSeconds&gt;" — format verified against
 *    FarmingTracker.java). Free coverage of remote patches whenever the user
 *    runs that default-on plugin; silently absent otherwise.
 *
 * A GROWING observation decays to UNKNOWN once the type's minimum total grow
 * time has elapsed — the crop may have finished, so the patch is worth a
 * visit again. Predictions are deliberately conservative: a patch is only
 * skipped when it is provably still growing.
 *
 * Threading: refresh() runs on the client thread (GameTick); readers get a
 * volatile immutable snapshot; the fanout isolates failures per listener.
 */
@Slf4j
public class PatchStateService
{
	private final List<Patch> statefulPatches;
	private final ClientLevelSource client;
	private final PatchStateTable table;
	private final Function<String, String> timetrackingLookup;
	private final LongSupplier nowEpochSeconds;

	private static final class Observation
	{
		final CropState state;
		final long epochSeconds;

		Observation(CropState state, long epochSeconds)
		{
			this.state = state;
			this.epochSeconds = epochSeconds;
		}
	}

	// Client thread only.
	private final Map<String, Observation> liveObserved = new HashMap<>();
	private volatile Map<String, CropState> effective = Collections.emptyMap();
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

	/**
	 * @param timetrackingLookup resolves a "regionId.varbitId" key to the Time
	 *     Tracking plugin's stored observation (RS-profile scoped config read
	 *     in production; a map lookup in tests). May return null.
	 * @param nowEpochSeconds injected clock for testable decay.
	 */
	public PatchStateService(List<Patch> patches, ClientLevelSource client, PatchStateTable table,
		Function<String, String> timetrackingLookup, LongSupplier nowEpochSeconds)
	{
		this.statefulPatches = new ArrayList<>();
		for (Patch p : patches)
		{
			if (p.stateVarbitId() != null && p.stateRegionIds() != null)
			{
				statefulPatches.add(p);
			}
		}
		this.client = client;
		this.table = table;
		this.timetrackingLookup = timetrackingLookup;
		this.nowEpochSeconds = nowEpochSeconds;
	}

	public void addListener(Runnable l)    { listeners.add(l); }
	public void removeListener(Runnable l) { listeners.remove(l); }

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		refresh();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			// Session cache is per-account; the next login may be another
			// profile whose patches differ.
			liveObserved.clear();
			refresh();
		}
	}

	public CropState state(Patch p)
	{
		return effective.getOrDefault(p.id(), CropState.UNKNOWN);
	}

	public boolean needsVisit(Patch p)
	{
		return state(p).needsVisit();
	}

	/**
	 * COMPLETE = every patch confirmed growing; INCOMPLETE = something needs
	 * work; UNKNOWN = not enough state to judge (proximity fallback applies).
	 */
	public StopProgress groupProgress(Collection<Patch> patches)
	{
		if (patches == null || patches.isEmpty())
		{
			return StopProgress.UNKNOWN;
		}
		boolean anyUnknown = false;
		for (Patch p : patches)
		{
			CropState s = state(p);
			if (s == CropState.UNKNOWN)
			{
				anyUnknown = true;
			}
			else if (s.needsVisit())
			{
				return StopProgress.INCOMPLETE;
			}
		}
		return anyUnknown ? StopProgress.UNKNOWN : StopProgress.COMPLETE;
	}

	/** Re-reads live varbits and rebuilds the snapshot; client thread only. */
	public void refresh()
	{
		WorldPoint player = client.getPlayerPosition();
		if (player != null)
		{
			int playerRegion = ((player.getX() >> 6) << 8) | (player.getY() >> 6);
			long now = nowEpochSeconds.getAsLong();
			for (Patch p : statefulPatches)
			{
				if (!p.stateRegionIds().contains(playerRegion))
				{
					continue;
				}
				if (p.stateBounds() != null && !p.stateBounds().contains(player))
				{
					continue;
				}
				CropState s = table.state(p.type(), client.getVarbitValue(p.stateVarbitId()));
				if (s != CropState.UNKNOWN)
				{
					liveObserved.put(p.id(), new Observation(s, now));
				}
			}
		}

		Map<String, CropState> next = new HashMap<>();
		for (Patch p : statefulPatches)
		{
			next.put(p.id(), effectiveState(p));
		}
		if (!next.equals(effective))
		{
			effective = Collections.unmodifiableMap(next);
			for (Runnable l : new ArrayList<>(listeners))
			{
				try
				{
					l.run();
				}
				// AssertionError included: RuneLite's dev-mode thread assertions
				// must not let one listener starve the rest of the fanout.
				catch (Exception | AssertionError ex)
				{
					log.warn("Better Farming: patch-state listener {} threw", l.getClass().getName(), ex);
				}
			}
		}
	}

	private CropState effectiveState(Patch p)
	{
		Observation obs = liveObserved.get(p.id());
		if (obs == null)
		{
			obs = remoteObservation(p);
		}
		if (obs == null)
		{
			return CropState.UNKNOWN;
		}
		if (obs.state == CropState.GROWING)
		{
			int minMinutes = table.minGrowMinutes(p.type());
			if (minMinutes > 0 && nowEpochSeconds.getAsLong() - obs.epochSeconds > minMinutes * 60L)
			{
				// The fastest crop of this type could have finished by now.
				return CropState.UNKNOWN;
			}
		}
		return obs.state;
	}

	private Observation remoteObservation(Patch p)
	{
		if (p.stateRegionId() == null)
		{
			return null;
		}
		String stored = timetrackingLookup.apply(p.stateRegionId() + "." + p.stateVarbitId());
		if (stored == null)
		{
			return null;
		}
		int sep = stored.indexOf(':');
		if (sep <= 0)
		{
			return null;
		}
		try
		{
			int value = Integer.parseInt(stored.substring(0, sep));
			long epoch = Long.parseLong(stored.substring(sep + 1));
			CropState s = table.state(p.type(), value);
			return s == CropState.UNKNOWN ? null : new Observation(s, epoch);
		}
		catch (NumberFormatException ex)
		{
			return null;
		}
	}
}
