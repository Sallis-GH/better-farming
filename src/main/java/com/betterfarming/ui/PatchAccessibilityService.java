package com.betterfarming.ui;

import com.betterfarming.data.FarmingData;
import com.betterfarming.data.Patch;
import com.betterfarming.data.PatchGroup;
import com.betterfarming.data.requirement.PlayerState;
import com.betterfarming.data.requirement.Requirement;
import com.betterfarming.data.requirement.RequirementEvaluator;
import com.betterfarming.state.PatchSelectionService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.eventbus.Subscribe;

/**
 * Evaluates each PatchGroup's Requirements against live player state and
 * notifies listeners (PatchGroupCard) when a group's lock state flips or
 * its unmet-requirements list changes.
 *
 * Subscribes to GameStateChanged (login/logout/world hop/region cross via
 * LOADING) + StatChanged (filtered to skills referenced in any
 * SkillRequirement). No VarbitChanged subscription — quest-completion
 * lock-clears wait until the next region cross or relog (Phase 1.5 spec
 * decision 4).
 *
 * Threading discipline mirrors SeedAvailabilityService: events arrive on
 * the client thread, cache mutation happens there, listener fanout hops
 * to the EDT via invokeLater.
 */
@Singleton
@Slf4j
public class PatchAccessibilityService
{
	private final ClientLevelSource client;
	private final FarmingData data;
	private final RequirementEvaluator evaluator;

	private final Map<String, List<Requirement>> unmetByGroup = new HashMap<>();
	private final Set<Consumer<PatchAccessibilityEvent>> listeners = new LinkedHashSet<>();

	private final Set<Skill> trackedSkills;
	private final Set<Quest> trackedQuests;

	@Inject
	public PatchAccessibilityService(ClientLevelSource client,
		FarmingData data, RequirementEvaluator evaluator)
	{
		this.client = client;
		this.data = data;
		this.evaluator = evaluator;
		this.trackedSkills = collectTrackedSkills(data);
		this.trackedQuests = collectTrackedQuests(data);
		// Initial cache: every group is "accessible" until first refresh().
		for (PatchGroup g : PatchGroup.groupAll(data.patches()))
		{
			unmetByGroup.put(g.key(), Collections.emptyList());
		}
	}

	// ── public API ──

	public List<Requirement> unmetFor(String groupKey)
	{
		return unmetByGroup.getOrDefault(groupKey, Collections.emptyList());
	}

	public boolean isLocked(String groupKey)
	{
		return !unmetFor(groupKey).isEmpty();
	}

	/**
	 * Canonical "is this group in the run?" check. Combines the user's persisted
	 * active flag with current accessibility — a locked group is never effectively
	 * active even if previously toggled on. The active flag itself is preserved
	 * verbatim across the lock cycle; this accessor shadows it. Future consumers
	 * (Phase 2 path planning) should always go through this rather than calling
	 * selection.isGroupActive directly.
	 */
	public boolean effectiveActive(String groupKey, PatchSelectionService selection)
	{
		return selection.isGroupActive(groupKey) && !isLocked(groupKey);
	}

	public void addListener(Consumer<PatchAccessibilityEvent> l)    { listeners.add(l); }
	public void removeListener(Consumer<PatchAccessibilityEvent> l) { listeners.remove(l); }

	// ── event subscriptions ──

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		refresh();
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (trackedSkills.contains(event.getSkill()))
		{
			refresh();
		}
	}

	/** Recompute every group's unmet set; fire listeners for any that changed. */
	public void refresh()
	{
		PlayerState snap = snapshot();
		List<PatchAccessibilityEvent> toFire = new ArrayList<>();
		for (PatchGroup g : PatchGroup.groupAll(data.patches()))
		{
			List<Requirement> previous = unmetByGroup.get(g.key());
			List<Requirement> current = evaluator.unmet(g.requirements(), snap);
			if (!current.equals(previous))
			{
				unmetByGroup.put(g.key(), current);
				toFire.add(new PatchAccessibilityEvent(
					g.key(), !previous.isEmpty(), !current.isEmpty(), current));
			}
		}
		if (!toFire.isEmpty())
		{
			// Snapshot listeners now (client thread) so that listeners added
			// after this refresh() call do not receive this batch of events.
			// Without the snapshot, a listener added between invokeLater() and
			// the EDT pickup would spuriously observe events it predates.
			List<Consumer<PatchAccessibilityEvent>> snapshot =
				new ArrayList<>(listeners);
			SwingUtilities.invokeLater(() -> dispatch(toFire, snapshot));
		}
	}

	// ── internals ──

	private PlayerState snapshot()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return PlayerState.loggedOut();
		}
		Map<Skill, Integer> levels = new HashMap<>();
		for (Skill s : trackedSkills)
		{
			levels.put(s, client.getRealSkillLevel(s));
		}
		Map<Quest, QuestState> quests = new HashMap<>();
		for (Quest q : trackedQuests)
		{
			QuestState state = client.getQuestState(q);
			if (state != null)
			{
				quests.put(q, state);
			}
			else
			{
				log.debug("Better Farming: getQuestState({}) returned null; treated as unmet", q);
			}
		}
		return new PlayerState(true, levels, quests);
	}

	private void dispatch(List<PatchAccessibilityEvent> events,
		List<Consumer<PatchAccessibilityEvent>> snapshot)
	{
		for (PatchAccessibilityEvent ev : events)
		{
			for (Consumer<PatchAccessibilityEvent> l : snapshot)
			{
				try
				{
					l.accept(ev);
				}
				catch (RuntimeException ex)
				{
					log.warn("Better Farming: accessibility listener {} threw",
						l.getClass().getName(), ex);
				}
			}
		}
	}

	private static Set<Skill> collectTrackedSkills(FarmingData data)
	{
		Set<Skill> out = EnumSet.noneOf(Skill.class);
		for (Patch p : data.patches())
		{
			for (Requirement r : p.requirements())
			{
				out.addAll(r.trackedSkills());
			}
		}
		return out;
	}

	private static Set<Quest> collectTrackedQuests(FarmingData data)
	{
		Set<Quest> out = EnumSet.noneOf(Quest.class);
		for (Patch p : data.patches())
		{
			for (Requirement r : p.requirements())
			{
				out.addAll(r.trackedQuests());
			}
		}
		return out;
	}
}
