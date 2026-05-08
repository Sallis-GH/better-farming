package com.betterfarming.ui;

import com.betterfarming.data.FarmingData;
import com.betterfarming.data.Patch;
import com.betterfarming.data.PatchType;
import com.betterfarming.data.Seed;
import com.betterfarming.data.requirement.PlayerState;
import com.betterfarming.data.requirement.QuestRequirement;
import com.betterfarming.data.requirement.Requirement;
import com.betterfarming.data.requirement.RequirementEvaluator;
import com.betterfarming.data.requirement.SkillRequirement;
import com.betterfarming.state.PatchSelectionService;
import com.betterfarming.testsupport.FakeClient;
import com.betterfarming.testsupport.FakeConfigStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.StatChanged;
import javax.swing.SwingUtilities;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PatchAccessibilityServiceTest
{
	private FakeClient client;
	private RequirementEvaluator evaluator;
	private FarmingData data;
	private PatchAccessibilityService service;

	private static final String FARMING_GUILD_KEY = "ALLOTMENT|Farming Guild";
	private static final String PRIFDDINAS_KEY = "ALLOTMENT|Prifddinas";
	private static final String FALADOR_KEY = "ALLOTMENT|South of Falador";

	@Before
	public void setUp()
	{
		client = new FakeClient();
		client.setGameState(GameState.LOGGED_IN);
		client.setLevel(Skill.FARMING, 50);  // default: Farming requirements met
		evaluator = new RequirementEvaluator();
		data = toyData();
		service = new PatchAccessibilityService(client, data, evaluator);
	}

	private FarmingData toyData()
	{
		Patch faladorOnly = patch("falador_a", "ALLOTMENT", "South of Falador",
			"NW", List.of());
		Patch farmingGuildA = patch("guild_a", "ALLOTMENT", "Farming Guild",
			"N", List.of(new SkillRequirement(Skill.FARMING, 45)));
		Patch farmingGuildB = patch("guild_b", "ALLOTMENT", "Farming Guild",
			"S", List.of(new SkillRequirement(Skill.FARMING, 45)));
		Patch prifddinasA = patch("prif_a", "ALLOTMENT", "Prifddinas",
			"N", List.of(new QuestRequirement(Quest.SONG_OF_THE_ELVES, QuestState.FINISHED)));
		Patch prifddinasB = patch("prif_b", "ALLOTMENT", "Prifddinas",
			"S", List.of(new QuestRequirement(Quest.SONG_OF_THE_ELVES, QuestState.FINISHED)));
		return new FarmingData(
			List.of(faladorOnly, farmingGuildA, farmingGuildB, prifddinasA, prifddinasB),
			List.<Seed>of());
	}

	private static Patch patch(String id, String type, String location, String label,
		List<Requirement> reqs)
	{
		return new Patch(id, id + "_display", PatchType.valueOf(type), location, label,
			new WorldPoint(0, 0, 0), reqs);
	}

	@Test
	public void constructor_seedsCacheToAccessibleForAllGroups()
	{
		// Cache is seeded empty per spec; before refresh, nothing is locked.
		assertFalse(service.isLocked(FALADOR_KEY));
		assertFalse(service.isLocked(FARMING_GUILD_KEY));
		assertFalse(service.isLocked(PRIFDDINAS_KEY));
	}

	@Test
	public void refresh_loggedIn_marksGroupsWithUnmetRequirementsLocked()
	{
		client.setLevel(Skill.FARMING, 30);  // below 45
		client.setQuestState(Quest.SONG_OF_THE_ELVES, QuestState.NOT_STARTED);

		service.refresh();

		assertTrue(service.isLocked(FARMING_GUILD_KEY));
		assertTrue(service.isLocked(PRIFDDINAS_KEY));
		assertFalse(service.isLocked(FALADOR_KEY));
	}

	@Test
	public void refresh_levelUpClearsLock_firesEvent() throws Exception
	{
		client.setLevel(Skill.FARMING, 30);
		service.refresh();
		assertTrue(service.isLocked(FARMING_GUILD_KEY));

		List<PatchAccessibilityEvent> received = new ArrayList<>();
		service.addListener(received::add);

		client.setLevel(Skill.FARMING, 99);
		service.refresh();
		flushEdt();

		assertEquals(1, received.size());
		PatchAccessibilityEvent e = received.get(0);
		assertEquals(FARMING_GUILD_KEY, e.groupKey());
		assertTrue(e.wasLocked());
		assertFalse(e.nowLocked());
		assertTrue(e.unmet().isEmpty());
	}

	@Test
	public void refresh_secondaryUnmetChange_firesEvent_evenIfStillLocked() throws Exception
	{
		// Build a fresh service with a single group that needs both Farming AND a quest.
		Patch combined = patch("combo", "ALLOTMENT", "Combo Spot", null,
			List.of(new SkillRequirement(Skill.FARMING, 45),
				new QuestRequirement(Quest.SONG_OF_THE_ELVES, QuestState.FINISHED)));
		FarmingData combo = new FarmingData(List.of(combined), List.<Seed>of());
		PatchAccessibilityService localService =
			new PatchAccessibilityService(client, combo, evaluator);

		client.setLevel(Skill.FARMING, 30);
		client.setQuestState(Quest.SONG_OF_THE_ELVES, QuestState.NOT_STARTED);
		localService.refresh();
		assertEquals(2, localService.unmetFor("ALLOTMENT|Combo Spot").size());

		List<PatchAccessibilityEvent> received = new ArrayList<>();
		localService.addListener(received::add);

		client.setQuestState(Quest.SONG_OF_THE_ELVES, QuestState.FINISHED);  // 1 left
		localService.refresh();
		flushEdt();

		assertEquals("unmet shrunk; tooltip needs refresh even though lock stays on",
			1, received.size());
		assertTrue(received.get(0).wasLocked());
		assertTrue(received.get(0).nowLocked());
		assertEquals(1, received.get(0).unmet().size());
	}

	@Test
	public void refresh_noChange_doesNotFire() throws Exception
	{
		client.setLevel(Skill.FARMING, 30);
		service.refresh();

		List<PatchAccessibilityEvent> received = new ArrayList<>();
		service.addListener(received::add);

		service.refresh();  // identical state
		flushEdt();

		assertTrue(received.isEmpty());
	}

	@Test
	public void onStatChanged_filtersByTrackedSkill() throws Exception
	{
		client.setLevel(Skill.FARMING, 30);
		service.refresh();

		List<PatchAccessibilityEvent> received = new ArrayList<>();
		service.addListener(received::add);

		// Untracked skill — must not trigger a refresh.
		service.onStatChanged(new StatChanged(Skill.WOODCUTTING, 0, 99, 99));
		flushEdt();
		assertTrue("WOODCUTTING isn't referenced by any patch in the toy data",
			received.isEmpty());

		// Tracked skill — must trigger a refresh that produces an event.
		client.setLevel(Skill.FARMING, 99);
		service.onStatChanged(new StatChanged(Skill.FARMING, 99 * 1000, 99, 99));
		flushEdt();
		assertEquals(1, received.size());
	}

	@Test
	public void onGameStateChanged_alwaysRefreshes() throws Exception
	{
		client.setLevel(Skill.FARMING, 30);
		service.refresh();
		assertTrue(service.isLocked(FARMING_GUILD_KEY));

		List<PatchAccessibilityEvent> received = new ArrayList<>();
		service.addListener(received::add);

		client.setLevel(Skill.FARMING, 99);
		GameStateChanged gsc = new GameStateChanged();
		gsc.setGameState(GameState.LOADING);  // region cross
		service.onGameStateChanged(gsc);
		flushEdt();

		assertEquals(1, received.size());
		assertFalse(service.isLocked(FARMING_GUILD_KEY));
	}

	@Test
	public void loggedOutSnapshot_clearsAllLocks() throws Exception
	{
		client.setLevel(Skill.FARMING, 30);
		service.refresh();
		assertTrue(service.isLocked(FARMING_GUILD_KEY));

		List<PatchAccessibilityEvent> received = new ArrayList<>();
		service.addListener(received::add);

		client.setGameState(GameState.LOGIN_SCREEN);
		service.refresh();
		flushEdt();

		assertFalse(service.isLocked(FARMING_GUILD_KEY));
		assertFalse(received.isEmpty());
	}

	@Test
	public void effectiveActive_combinesActiveAndUnlocked()
	{
		FakeConfigStore config = new FakeConfigStore();
		PatchSelectionService selection = new PatchSelectionService(config, data);

		// inactive + unlocked → false
		assertFalse(service.effectiveActive(FALADOR_KEY, selection));

		// active + unlocked → true
		selection.setGroupActive(FALADOR_KEY, true);
		assertTrue(service.effectiveActive(FALADOR_KEY, selection));

		// inactive + locked → false
		client.setLevel(Skill.FARMING, 30);
		service.refresh();
		assertFalse(service.effectiveActive(FARMING_GUILD_KEY, selection));

		// active + locked → false (canonical "lock shadows active")
		selection.setGroupActive(FARMING_GUILD_KEY, true);
		assertFalse(service.effectiveActive(FARMING_GUILD_KEY, selection));

		// active flag itself preserved verbatim across the lock cycle
		assertTrue("active flag must NOT be auto-cleared by the lock",
			selection.isGroupActive(FARMING_GUILD_KEY));
	}

	@Test
	public void listenerThrows_doesNotBreakSiblings() throws Exception
	{
		client.setLevel(Skill.FARMING, 30);
		service.refresh();

		AtomicBoolean siblingCalled = new AtomicBoolean(false);
		service.addListener(e -> { throw new RuntimeException("boom"); });
		service.addListener(e -> siblingCalled.set(true));

		client.setLevel(Skill.FARMING, 99);
		service.refresh();
		flushEdt();

		assertTrue("sibling listener still invoked despite the throw", siblingCalled.get());
	}

	@Test
	public void listenerFanoutHopsToEdt() throws Exception
	{
		client.setLevel(Skill.FARMING, 30);
		service.refresh();

		CountDownLatch latch = new CountDownLatch(1);
		AtomicBoolean wasOnEdt = new AtomicBoolean(false);
		service.addListener(e -> {
			wasOnEdt.set(SwingUtilities.isEventDispatchThread());
			latch.countDown();
		});

		client.setLevel(Skill.FARMING, 99);
		service.refresh();

		assertTrue("listener fired within 1s", latch.await(1, TimeUnit.SECONDS));
		assertTrue("listener observed EDT thread", wasOnEdt.get());
	}

	@Test
	public void unmetFor_returnsEmptyForUnknownGroupKey()
	{
		assertNotNull(service.unmetFor("BOGUS|Nowhere"));
		assertTrue(service.unmetFor("BOGUS|Nowhere").isEmpty());
	}

	private static void flushEdt() throws Exception
	{
		// Ensure any invokeLater work submitted by refresh() has run.
		SwingUtilities.invokeAndWait(() -> {});
	}
}
