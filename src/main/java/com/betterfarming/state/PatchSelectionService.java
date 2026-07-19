package com.betterfarming.state;

import com.betterfarming.BetterFarmingConfig;
import com.betterfarming.data.FarmingData;
import com.betterfarming.data.Patch;
import com.betterfarming.data.PatchGroup;
import com.betterfarming.data.Seed;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * In-memory store + persistence + change broadcaster for patch selections.
 *
 * State is two pieces:
 *  - per-patch seedId (Map<String, PatchSelection>)
 *  - per-group active flag (Set<String> keyed by PatchGroup.key())
 *
 * Persisted as one JSON blob (version 2) under the betterfarming.patchSelections
 * config key. Legacy version-1 Phase 1 blobs are detected, logged at WARN, and
 * treated as empty (no migrator).
 *
 * Listeners run synchronously on whatever thread mutated the service. Swing
 * consumers are responsible for hopping to the EDT before touching components.
 */
@Singleton
@Slf4j
public class PatchSelectionService
{
	private static final int CURRENT_VERSION = 2;
	private static final Gson GSON = new Gson();

	private final ConfigStore configStore;
	private final Set<String> validPatchIds;
	private final Set<String> validSeedIds;
	private final Set<String> validGroupKeys;

	private final Map<String, PatchSelection> selections = new HashMap<>();
	private final Set<String> activeGroupKeys = new LinkedHashSet<>();
	private final Set<Consumer<PatchSelectionEvent>> listeners = new LinkedHashSet<>();
	private final Set<Consumer<GroupActiveEvent>> groupListeners = new LinkedHashSet<>();

	@Inject
	public PatchSelectionService(ConfigStore configStore, FarmingData data)
	{
		this(configStore,
			data.patches().stream().map(Patch::id).collect(Collectors.toUnmodifiableSet()),
			data.seeds().stream().map(Seed::id).collect(Collectors.toUnmodifiableSet()),
			PatchGroup.groupAll(data.patches()).stream().map(PatchGroup::key)
				.collect(Collectors.toUnmodifiableSet()));
	}

	// Visible for testing — production code uses the FarmingData constructor.
	PatchSelectionService(ConfigStore configStore,
		Set<String> validPatchIds, Set<String> validSeedIds)
	{
		this(configStore, validPatchIds, validSeedIds, Set.<String>of());
	}

	PatchSelectionService(ConfigStore configStore,
		Set<String> validPatchIds, Set<String> validSeedIds, Set<String> validGroupKeys)
	{
		this.configStore = configStore;
		this.validPatchIds = validPatchIds;
		this.validSeedIds = validSeedIds;
		this.validGroupKeys = validGroupKeys;
		load();
	}

	// ── seed methods ──

	public Optional<PatchSelection> get(String patchId)
	{
		return Optional.ofNullable(selections.get(patchId));
	}

	public void setSeed(String patchId, String seedId)
	{
		PatchSelection prev = selections.get(patchId);
		PatchSelection next = seedId == null ? null : new PatchSelection(patchId, seedId);
		if (Objects.equals(prev, next))
		{
			return;
		}
		if (next == null)
		{
			selections.remove(patchId);
		}
		else
		{
			selections.put(patchId, next);
		}
		save();
		PatchSelectionEvent event = new PatchSelectionEvent(patchId, prev, next);
		dispatchSeed(event);
	}

	public void addListener(Consumer<PatchSelectionEvent> listener)
	{
		listeners.add(listener);
	}

	public void removeListener(Consumer<PatchSelectionEvent> listener)
	{
		listeners.remove(listener);
	}

	// ── group-active methods ──

	public boolean isGroupActive(String groupKey)
	{
		return activeGroupKeys.contains(groupKey);
	}

	public Set<String> activeGroups()
	{
		return new LinkedHashSet<>(activeGroupKeys);
	}

	public void setGroupActive(String groupKey, boolean active)
	{
		boolean prev = activeGroupKeys.contains(groupKey);
		if (prev == active)
		{
			return;
		}
		if (active)
		{
			activeGroupKeys.add(groupKey);
		}
		else
		{
			activeGroupKeys.remove(groupKey);
		}
		save();
		GroupActiveEvent event = new GroupActiveEvent(groupKey, prev, active);
		dispatchGroup(event);
	}

	public void addGroupListener(Consumer<GroupActiveEvent> listener)
	{
		groupListeners.add(listener);
	}

	public void removeGroupListener(Consumer<GroupActiveEvent> listener)
	{
		groupListeners.remove(listener);
	}

	// ── dispatch ──

	private void dispatchSeed(PatchSelectionEvent event)
	{
		for (Consumer<PatchSelectionEvent> listener : listeners)
		{
			try
			{
				listener.accept(event);
			}
			// AssertionError included: RuneLite's dev-mode thread assertions
			// must not let one listener starve the rest of the fanout.
			catch (Exception | AssertionError ex)
			{
				log.warn("Better Farming: seed listener {} threw on patch {}",
					listener.getClass().getName(), event.patchId(), ex);
			}
		}
	}

	private void dispatchGroup(GroupActiveEvent event)
	{
		for (Consumer<GroupActiveEvent> listener : groupListeners)
		{
			try
			{
				listener.accept(event);
			}
			catch (Exception | AssertionError ex)
			{
				log.warn("Better Farming: group listener {} threw on group {}",
					listener.getClass().getName(), event.groupKey(), ex);
			}
		}
	}

	// ── persistence ──

	private void load()
	{
		String raw = configStore.get(
			BetterFarmingConfig.GROUP, BetterFarmingConfig.PATCH_SELECTIONS_KEY);
		if (raw == null || raw.isEmpty())
		{
			return;
		}
		Blob blob;
		try
		{
			blob = GSON.fromJson(raw, Blob.class);
		}
		catch (JsonSyntaxException ex)
		{
			log.warn("Better Farming: could not parse patchSelections blob, starting empty. payload={}",
				raw.length() > 200 ? raw.substring(0, 200) + "…" : raw, ex);
			return;
		}
		if (blob == null)
		{
			log.warn("Better Farming: patchSelections blob parsed as null, starting empty");
			return;
		}
		if (blob.version == 1)
		{
			log.warn("Better Farming: ignoring legacy v1 patchSelections blob; reconfigure your patches");
			return;
		}
		if (blob.version != CURRENT_VERSION)
		{
			log.warn("Better Farming: patchSelections blob has unexpected version {}, starting empty",
				blob.version);
			return;
		}
		if (blob.seeds != null)
		{
			for (Map.Entry<String, String> entry : blob.seeds.entrySet())
			{
				String patchId = entry.getKey();
				String seedId = entry.getValue();
				if (!validPatchIds.contains(patchId))
				{
					log.debug("Better Farming: dropping seed for unknown patch {}", patchId);
					continue;
				}
				if (seedId != null && !validSeedIds.contains(seedId))
				{
					log.debug("Better Farming: dropping unknown seed {} from patch {}", seedId, patchId);
					continue;
				}
				if (seedId == null)
				{
					continue;
				}
				selections.put(patchId, new PatchSelection(patchId, seedId));
			}
		}
		if (blob.activeGroups != null)
		{
			for (String key : blob.activeGroups)
			{
				if (key == null || key.isEmpty())
				{
					continue;
				}
				if (!validGroupKeys.isEmpty() && !validGroupKeys.contains(key))
				{
					log.debug("Better Farming: dropping unknown group {}", key);
					continue;
				}
				activeGroupKeys.add(key);
			}
		}
	}

	private void save()
	{
		Blob blob = new Blob();
		blob.version = CURRENT_VERSION;
		blob.activeGroups = new LinkedHashSet<>(activeGroupKeys);
		blob.seeds = new LinkedHashMap<>();
		for (PatchSelection sel : selections.values())
		{
			if (sel.seedId() != null)
			{
				blob.seeds.put(sel.patchId(), sel.seedId());
			}
		}
		configStore.set(
			BetterFarmingConfig.GROUP,
			BetterFarmingConfig.PATCH_SELECTIONS_KEY,
			GSON.toJson(blob));
	}

	// Internal serialization shape — package-private fields for Gson.
	static class Blob
	{
		int version;
		Set<String> activeGroups;
		Map<String, String> seeds;
	}
}
