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
	/**
	 * Decay floor for patch types without extracted growth data: the fastest
	 * crops anywhere finish in ~40 minutes, so an observation older than that
	 * can no longer prove the crop is still growing. Conservative — decaying
	 * early routes an extra visit; decaying late silently skips a ready patch.
	 */
	static final int DEFAULT_MIN_GROW_MINUTES = 40;

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
	/**
	 * Parsed Time Tracking observations keyed by patch id; NO_OBSERVATION
	 * marks confirmed-absent entries. Stored config barely ever changes for
	 * patches we are not standing at, so this is filled lazily and dropped
	 * wholesale when the timetracking config group or profile changes —
	 * re-reading ~85 profile-config keys every tick is pure waste otherwise.
	 * Client thread only.
	 */
	private final Map<String, Observation> remoteCache = new HashMap<>();
	private static final Observation NO_OBSERVATION = new Observation(CropState.UNKNOWN, 0);

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

	/** Listeners run on the client thread; Swing listeners must marshal. */
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
			// Session caches are per-account; the next login may be another
			// profile whose patches differ.
			liveObserved.clear();
			remoteCache.clear();
			refresh();
		}
	}

	@Subscribe
	public void onConfigChanged(net.runelite.client.events.ConfigChanged event)
	{
		// The Time Tracking plugin stored a fresh observation.
		if ("timetracking".equals(event.getGroup()))
		{
			remoteCache.clear();
		}
	}

	@Subscribe
	public void onProfileChanged(net.runelite.client.events.RuneScapeProfileChanged event)
	{
		remoteCache.clear();
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

		long now = nowEpochSeconds.getAsLong();
		Map<String, CropState> next = new HashMap<>();
		for (Patch p : statefulPatches)
		{
			next.put(p.id(), effectiveState(p, now));
		}
		if (!next.equals(effective))
		{
			effective = Collections.unmodifiableMap(next);
			// CopyOnWriteArrayList iteration is already a snapshot.
			for (Runnable l : listeners)
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

	private CropState effectiveState(Patch p, long now)
	{
		Observation obs = liveObserved.get(p.id());
		if (obs == null)
		{
			obs = remoteCache.computeIfAbsent(p.id(), id -> remoteObservation(p));
			if (obs == NO_OBSERVATION)
			{
				return CropState.UNKNOWN;
			}
		}
		if (obs.state == CropState.GROWING)
		{
			// Anchored at observation time, which can over-hold a mid-growth
			// observation by up to one grow cycle — stage-aware prediction is
			// deliberately out of scope (Phase 6).
			int minMinutes = table.minGrowMinutes(p.type());
			if (minMinutes <= 0)
			{
				minMinutes = DEFAULT_MIN_GROW_MINUTES;
			}
			if (now - obs.epochSeconds > minMinutes * 60L)
			{
				// The fastest crop of this type could have finished by now.
				return CropState.UNKNOWN;
			}
		}
		return obs.state;
	}

	/** Never null: absence is cached as NO_OBSERVATION. */
	private Observation remoteObservation(Patch p)
	{
		if (p.stateRegionId() == null)
		{
			return NO_OBSERVATION;
		}
		String stored = timetrackingLookup.apply(p.stateRegionId() + "." + p.stateVarbitId());
		if (stored == null)
		{
			return NO_OBSERVATION;
		}
		int sep = stored.indexOf(':');
		if (sep <= 0)
		{
			return NO_OBSERVATION;
		}
		try
		{
			int value = Integer.parseInt(stored.substring(0, sep));
			long epoch = Long.parseLong(stored.substring(sep + 1));
			CropState s = table.state(p.type(), value);
			return s == CropState.UNKNOWN ? NO_OBSERVATION : new Observation(s, epoch);
		}
		catch (NumberFormatException ex)
		{
			return NO_OBSERVATION;
		}
	}
}
