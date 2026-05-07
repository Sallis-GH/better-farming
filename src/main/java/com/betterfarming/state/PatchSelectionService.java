package com.betterfarming.state;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * In-memory store + change broadcaster for which patches are selected and
 * what seed each is configured with. Single source of truth — Phase 2 path
 * planning will read selected() directly without touching Swing.
 *
 * Listeners receive PatchSelectionEvents on the EDT. (EDT-hop is added in
 * a later task once persistence is in place; for now listeners run on
 * whatever thread invokes the mutation. Test code is single-threaded so
 * this is fine for now.)
 */
@Singleton
@Slf4j
public class PatchSelectionService
{
	private final Map<String, PatchSelection> selections = new HashMap<>();
	private final Set<Consumer<PatchSelectionEvent>> listeners = new LinkedHashSet<>();

	@Inject
	public PatchSelectionService()
	{
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
}
