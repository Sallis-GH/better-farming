package com.betterfarming.state;

import com.betterfarming.testsupport.FakeConfigStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PatchSelectionServiceTest
{
	private PatchSelectionService service;
	private List<PatchSelectionEvent> events;
	private Consumer<PatchSelectionEvent> eventsListener;
	private FakeConfigStore configManager;

	@Before
	public void setUp()
	{
		configManager = new FakeConfigStore();
		Set<String> validPatches = Set.of("p1", "p2", "p3", "p4",
			"falador_allotment_north_west", "catherby_herb");
		Set<String> validSeeds = Set.of("watermelon_seed", "toadflax_seed");
		service = new PatchSelectionService(configManager, validPatches, validSeeds);
		events = new ArrayList<>();
		eventsListener = events::add;
		service.addListener(eventsListener);
	}

	// ── seed methods ──

	@Test
	public void getReturnsEmptyForUnknownPatch()
	{
		assertFalse(service.get("unknown").isPresent());
	}

	@Test
	public void setSeedRecordsSeed()
	{
		service.setSeed("falador_allotment_north_west", "watermelon_seed");

		Optional<PatchSelection> sel = service.get("falador_allotment_north_west");
		assertTrue(sel.isPresent());
		assertEquals("watermelon_seed", sel.get().seedId());
	}

	@Test
	public void setSeedNullClearsSeed()
	{
		service.setSeed("p1", "watermelon_seed");
		service.setSeed("p1", null);

		assertNull(service.get("p1").get().seedId());
	}

	@Test
	public void listenerReceivesEventOnSeedChange()
	{
		service.setSeed("p1", "watermelon_seed");

		assertEquals(1, events.size());
		PatchSelectionEvent e = events.get(0);
		assertEquals("p1", e.patchId());
		assertNull(e.oldSelection());
		assertEquals("watermelon_seed", e.newSelection().seedId());
	}

	@Test
	public void listenerReceivesOldAndNewOnSecondSeedChange()
	{
		service.setSeed("p1", "watermelon_seed");
		service.setSeed("p1", "toadflax_seed");

		assertEquals(2, events.size());
		PatchSelectionEvent second = events.get(1);
		assertEquals("watermelon_seed", second.oldSelection().seedId());
		assertEquals("toadflax_seed", second.newSelection().seedId());
	}

	@Test
	public void noOpSeedChangeFiresNoEvent()
	{
		service.setSeed("p1", "watermelon_seed");
		events.clear();
		service.setSeed("p1", "watermelon_seed");

		assertTrue(events.isEmpty());
	}

	@Test
	public void listenerExceptionDoesNotBreakOtherListeners()
	{
		List<PatchSelectionEvent> received = new ArrayList<>();
		service.addListener(e -> { throw new RuntimeException("boom"); });
		service.addListener(received::add);

		service.setSeed("p1", "watermelon_seed");

		assertEquals(1, events.size());
		assertEquals(1, received.size());
	}

	@Test
	public void removeListenerStopsReceivingEvents()
	{
		service.removeListener(eventsListener);
		events.clear();

		service.setSeed("p1", "watermelon_seed");

		assertTrue(service.get("p1").isPresent());
		assertTrue(events.isEmpty());
	}

	// ── group-active methods ──

	@Test
	public void isGroupActiveDefaultsToFalse()
	{
		assertFalse(service.isGroupActive("ALLOTMENT|Catherby"));
	}

	@Test
	public void setGroupActiveTrueRecordsActiveState()
	{
		service.setGroupActive("ALLOTMENT|Catherby", true);

		assertTrue(service.isGroupActive("ALLOTMENT|Catherby"));
	}

	@Test
	public void setGroupActiveFalseClearsActiveState()
	{
		service.setGroupActive("ALLOTMENT|Catherby", true);
		service.setGroupActive("ALLOTMENT|Catherby", false);

		assertFalse(service.isGroupActive("ALLOTMENT|Catherby"));
	}

	@Test
	public void activeGroupsReturnsActiveSet()
	{
		service.setGroupActive("ALLOTMENT|Catherby", true);
		service.setGroupActive("HERB|Falador", true);
		service.setGroupActive("FLOWER|Catherby", true);
		service.setGroupActive("FLOWER|Catherby", false);

		Set<String> active = service.activeGroups();
		assertTrue(active.contains("ALLOTMENT|Catherby"));
		assertTrue(active.contains("HERB|Falador"));
		assertFalse(active.contains("FLOWER|Catherby"));
	}

	@Test
	public void activeGroupsReturnsDefensiveCopy()
	{
		service.setGroupActive("ALLOTMENT|Catherby", true);

		Set<String> active = service.activeGroups();
		active.clear();

		assertTrue(service.isGroupActive("ALLOTMENT|Catherby"));
	}

	@Test
	public void groupListenerReceivesEventOnActivate()
	{
		List<GroupActiveEvent> received = new ArrayList<>();
		service.addGroupListener(received::add);

		service.setGroupActive("ALLOTMENT|Catherby", true);

		assertEquals(1, received.size());
		assertEquals("ALLOTMENT|Catherby", received.get(0).groupKey());
		assertFalse(received.get(0).oldActive());
		assertTrue(received.get(0).newActive());
	}

	@Test
	public void noOpGroupActivateFiresNoEvent()
	{
		List<GroupActiveEvent> received = new ArrayList<>();
		service.addGroupListener(received::add);

		service.setGroupActive("ALLOTMENT|Catherby", false);

		assertTrue(received.isEmpty());
	}

	@Test
	public void removeGroupListenerStopsReceivingEvents()
	{
		List<GroupActiveEvent> received = new ArrayList<>();
		Consumer<GroupActiveEvent> listener = received::add;
		service.addGroupListener(listener);
		service.removeGroupListener(listener);

		service.setGroupActive("ALLOTMENT|Catherby", true);

		assertTrue(received.isEmpty());
	}

	@Test
	public void groupListenerExceptionDoesNotBreakOtherListeners()
	{
		List<GroupActiveEvent> received = new ArrayList<>();
		service.addGroupListener(e -> { throw new RuntimeException("boom"); });
		service.addGroupListener(received::add);

		service.setGroupActive("ALLOTMENT|Catherby", true);

		assertEquals(1, received.size());
	}

	// ── persistence (still v1 in this task; Task 10 bumps to v2) ──

	@Test
	public void persistsToConfigManagerOnSeedChange()
	{
		service.setSeed("p1", "watermelon_seed");

		String blob = configManager.peek("betterfarming", "patchSelections");
		assertNotNull(blob);
		assertTrue(blob.contains("\"p1\""));
	}

	@Test
	public void noOpSeedChangeDoesNotWriteToConfig()
	{
		service.setSeed("p1", "watermelon_seed");
		int writesAfterFirst = configManager.getWriteCount();

		service.setSeed("p1", "watermelon_seed");

		assertEquals(writesAfterFirst, configManager.getWriteCount());
	}

	@Test
	public void loadHandlesEmptyBlob()
	{
		PatchSelectionService loaded = new PatchSelectionService(configManager,
			Set.of("p1"), Set.of());

		assertFalse(loaded.get("p1").isPresent());
	}

	@Test
	public void loadHandlesCorruptedBlob()
	{
		configManager.putRaw("betterfarming", "patchSelections", "not-json{{{");

		PatchSelectionService loaded = new PatchSelectionService(configManager,
			Set.of("p1"), Set.of());

		assertFalse(loaded.get("p1").isPresent());
	}
}
