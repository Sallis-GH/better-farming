package com.betterfarming.ui;

import com.betterfarming.data.FarmingData;
import com.betterfarming.data.PatchType;
import com.betterfarming.data.Seed;
import com.betterfarming.data.requirement.Requirement;
import com.betterfarming.data.requirement.SkillRequirement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.eventbus.Subscribe;

/**
 * Computes "what seeds can the player currently plant in a patch of type X"
 * given live Farming level + login state. Cached per-PatchType and rebuilt
 * on refresh(). Game event subscription (StatChanged, GameStateChanged)
 * is added in a follow-up task.
 */
@Singleton
@Slf4j
public class SeedAvailabilityService
{
	private final ClientLevelSource client;
	private final FarmingData data;
	private final Map<PatchType, List<Seed>> cache = new EnumMap<>(PatchType.class);
	private final Set<Runnable> listeners = new LinkedHashSet<>();

	@Inject
	public SeedAvailabilityService(ClientLevelSource client, FarmingData data)
	{
		this.client = client;
		this.data = data;
		refresh();
	}

	public List<Seed> plantableSeeds(PatchType type)
	{
		List<Seed> seeds = cache.get(type);
		return seeds == null ? Collections.emptyList() : seeds;
	}

	public void addListener(Runnable listener)
	{
		listeners.add(listener);
	}

	public void removeListener(Runnable listener)
	{
		listeners.remove(listener);
	}

	/**
	 * Rebuild the per-PatchType cache. Returns true if the cache changed.
	 */
	public boolean refresh()
	{
		Map<PatchType, List<Seed>> previous = new EnumMap<>(cache);
		cache.clear();

		if (client.getGameState() != GameState.LOGGED_IN)
		{
			// Logged out: empty for every type.
			boolean changed = !previous.isEmpty();
			if (changed)
			{
				fireChanged();
			}
			return changed;
		}

		int farmingLevel = client.getRealSkillLevel(Skill.FARMING);

		for (Seed seed : data.seeds())
		{
			int requiredFarming = farmingRequirementOf(seed);
			if (requiredFarming > farmingLevel)
			{
				continue;
			}
			for (PatchType type : seed.compatiblePatchTypes())
			{
				cache.computeIfAbsent(type, k -> new ArrayList<>()).add(seed);
			}
		}

		Comparator<Seed> byFarmingLevel = Comparator
			.comparingInt(SeedAvailabilityService::farmingRequirementOf)
			.thenComparing(Seed::displayName);
		for (List<Seed> seeds : cache.values())
		{
			seeds.sort(byFarmingLevel);
		}

		boolean changed = !Objects.equals(previous, cache);
		if (changed)
		{
			fireChanged();
		}
		return changed;
	}

	private void fireChanged()
	{
		for (Runnable listener : listeners)
		{
			try
			{
				listener.run();
			}
			catch (RuntimeException ex)
			{
				log.warn("Better Farming: availability listener {} threw",
					listener.getClass().getName(), ex);
			}
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() != Skill.FARMING)
		{
			return;
		}
		refresh();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// Refresh on every state change — login/logout/hopping all matter.
		refresh();
	}

	private static int farmingRequirementOf(Seed seed)
	{
		for (Requirement r : seed.requirements())
		{
			if (r instanceof SkillRequirement)
			{
				SkillRequirement sr = (SkillRequirement) r;
				if (sr.skill() == Skill.FARMING)
				{
					return sr.level();
				}
			}
		}
		return 1; // no FARMING requirement modeled → always plantable
	}
}
