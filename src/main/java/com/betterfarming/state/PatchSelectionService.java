package com.betterfarming.state;

import com.betterfarming.BetterFarmingConfig;
import com.betterfarming.data.FarmingData;
import com.betterfarming.data.Patch;
import com.betterfarming.data.Seed;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * In-memory store + persistence + change broadcaster for patch selections.
 *
 * Loads from ConfigManager on construction (filtering out patch/seed ids
 * that no longer exist in the bundled FarmingData). Persists the whole
 * map back as a single JSON blob on every mutation.
 *
 * Listeners receive PatchSelectionEvents synchronously on whatever thread
 * triggers the mutation. A later task (PatchCard wiring) hops to the EDT
 * before refreshing UI components, so cards don't have to.
 */
@Singleton
@Slf4j
public class PatchSelectionService
{
	private static final int CURRENT_VERSION = 1;
	private static final Gson GSON = new Gson();

	private final ConfigStore configStore;
	private final Set<String> validPatchIds;
	private final Set<String> validSeedIds;
	private final Map<String, PatchSelection> selections = new HashMap<>();
	private final Set<Consumer<PatchSelectionEvent>> listeners = new LinkedHashSet<>();

	@Inject
	public PatchSelectionService(ConfigStore configStore, FarmingData data)
	{
		this(configStore,
			data.patches().stream().map(Patch::id).collect(Collectors.toUnmodifiableSet()),
			data.seeds().stream().map(Seed::id).collect(Collectors.toUnmodifiableSet()));
	}

	// Visible for testing — production code uses the FarmingData constructor.
	PatchSelectionService(ConfigStore configStore,
		Set<String> validPatchIds, Set<String> validSeedIds)
	{
		this.configStore = configStore;
		this.validPatchIds = validPatchIds;
		this.validSeedIds = validSeedIds;
		load();
	}

	public Optional<PatchSelection> get(String patchId)
	{
		return Optional.ofNullable(selections.get(patchId));
	}

	public Stream<PatchSelection> selected()
	{
		return selections.values().stream().filter(PatchSelection::selected);
	}

	public void setSelected(String patchId, boolean selected)
	{
		PatchSelection prev = selections.get(patchId);
		String seedId = prev == null ? null : prev.seedId();
		applyMutation(patchId, prev, new PatchSelection(patchId, selected, seedId));
	}

	public void setSeed(String patchId, String seedId)
	{
		PatchSelection prev = selections.get(patchId);
		boolean selected = prev != null && prev.selected();
		applyMutation(patchId, prev, new PatchSelection(patchId, selected, seedId));
	}

	public void addListener(Consumer<PatchSelectionEvent> listener)
	{
		listeners.add(listener);
	}

	public void removeListener(Consumer<PatchSelectionEvent> listener)
	{
		listeners.remove(listener);
	}

	private void applyMutation(String patchId, PatchSelection prev, PatchSelection next)
	{
		if (Objects.equals(prev, next))
		{
			return;
		}
		selections.put(patchId, next);
		save();
		PatchSelectionEvent event = new PatchSelectionEvent(patchId, prev, next);
		for (Consumer<PatchSelectionEvent> listener : listeners)
		{
			try
			{
				listener.accept(event);
			}
			catch (RuntimeException ex)
			{
				log.warn("Better Farming: listener {} threw on patch selection event for {}",
					listener.getClass().getName(), patchId, ex);
			}
		}
	}

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
		if (blob.version != CURRENT_VERSION)
		{
			log.warn("Better Farming: patchSelections blob has unexpected version {}, starting empty",
				blob.version);
			return;
		}
		if (blob.selections == null)
		{
			return;
		}
		for (Map.Entry<String, BlobEntry> entry : blob.selections.entrySet())
		{
			String patchId = entry.getKey();
			BlobEntry value = entry.getValue();
			if (value == null)
			{
				log.debug("Better Farming: dropping null entry for patch {}", patchId);
				continue;
			}
			if (!validPatchIds.contains(patchId))
			{
				log.debug("Better Farming: dropping selection for unknown patch {}", patchId);
				continue;
			}
			String seedId = value.seedId;
			if (seedId != null && !validSeedIds.contains(seedId))
			{
				log.debug("Better Farming: dropping unknown seed {} from patch {}", seedId, patchId);
				seedId = null;
			}
			selections.put(patchId, new PatchSelection(patchId, value.selected, seedId));
		}
	}

	private void save()
	{
		Blob blob = new Blob();
		blob.version = CURRENT_VERSION;
		blob.selections = new HashMap<>();
		for (PatchSelection sel : selections.values())
		{
			BlobEntry e = new BlobEntry();
			e.selected = sel.selected();
			e.seedId = sel.seedId();
			blob.selections.put(sel.patchId(), e);
		}
		configStore.set(
			BetterFarmingConfig.GROUP,
			BetterFarmingConfig.PATCH_SELECTIONS_KEY,
			GSON.toJson(blob));
	}

	// Internal serialization shapes — match the JSON format documented in the spec.
	// Package-private fields so Gson can read/write them by reflection.
	static class Blob
	{
		int version;
		Map<String, BlobEntry> selections;
	}

	static class BlobEntry
	{
		boolean selected;
		String seedId;
	}
}
