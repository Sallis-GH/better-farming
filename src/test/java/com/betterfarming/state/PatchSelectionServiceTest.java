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

		assertFalse(service.get("p1").isPresent());
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
	public void throwingGroupListener_doesNotStarveLaterListeners()
	{
		// Regression: RuneLite dev-mode thread assertions throw AssertionError
		// (not RuntimeException) from service listeners registered before the
		// UI cards; the dispatch loop must survive and still notify the cards.
		service.addGroupListener(e -> {
			throw new AssertionError("simulated client-thread assertion");
		});
		java.util.concurrent.atomic.AtomicReference<GroupActiveEvent> received =
			new java.util.concurrent.atomic.AtomicReference<>();
		service.addGroupListener(received::set);

		service.setGroupActive("ALLOTMENT|Catherby", true);

		assertTrue("later listener must still fire", received.get() != null);
		assertTrue(received.get().newActive());
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

	// ── persistence ──

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

	@Test
	public void persistsBlobAtVersion2()
	{
		service.setSeed("p1", "watermelon_seed");
		service.setGroupActive("ALLOTMENT|Catherby", true);

		String blob = configManager.peek("betterfarming", "patchSelections");
		assertNotNull(blob);
		assertTrue("blob should declare version 2", blob.contains("\"version\":2"));
		assertTrue("blob should contain seeds map", blob.contains("\"seeds\""));
		assertTrue("blob should contain activeGroups list", blob.contains("\"activeGroups\""));
		assertTrue(blob.contains("\"p1\""));
		assertTrue(blob.contains("\"ALLOTMENT|Catherby\""));
	}

	@Test
	public void loadsV2Blob()
	{
		configManager.putRaw("betterfarming", "patchSelections",
			"{\"version\":2,"
				+ "\"activeGroups\":[\"ALLOTMENT|Catherby\"],"
				+ "\"seeds\":{\"p1\":\"watermelon_seed\"}"
				+ "}");

		PatchSelectionService loaded = new PatchSelectionService(configManager,
			Set.of("p1"), Set.of("watermelon_seed"));

		assertTrue(loaded.get("p1").isPresent());
		assertEquals("watermelon_seed", loaded.get("p1").get().seedId());
		assertTrue(loaded.isGroupActive("ALLOTMENT|Catherby"));
	}

	@Test
	public void loadFiltersUnknownPatchIdsFromSeeds()
	{
		configManager.putRaw("betterfarming", "patchSelections",
			"{\"version\":2,"
				+ "\"activeGroups\":[],"
				+ "\"seeds\":{\"p1\":\"watermelon_seed\","
				+ "\"renamed_or_removed\":\"toadflax_seed\"}"
				+ "}");

		PatchSelectionService loaded = new PatchSelectionService(configManager,
			Set.of("p1"), Set.of("watermelon_seed", "toadflax_seed"));

		assertTrue(loaded.get("p1").isPresent());
		assertFalse(loaded.get("renamed_or_removed").isPresent());
	}

	@Test
	public void loadFiltersUnknownSeedIds()
	{
		configManager.putRaw("betterfarming", "patchSelections",
			"{\"version\":2,"
				+ "\"activeGroups\":[],"
				+ "\"seeds\":{\"p1\":\"removed_seed\"}"
				+ "}");

		PatchSelectionService loaded = new PatchSelectionService(configManager,
			Set.of("p1"), Set.of("watermelon_seed"));

		// Seed is dropped; no entry persists for the patch (since there's nothing else to record).
		assertFalse(loaded.get("p1").isPresent());
	}

	@Test
	public void loadKeepsUnknownGroupKeysFiltered()
	{
		configManager.putRaw("betterfarming", "patchSelections",
			"{\"version\":2,"
				+ "\"activeGroups\":[\"ALLOTMENT|Catherby\",\"HERB|Renamed Place\"],"
				+ "\"seeds\":{}"
				+ "}");

		// validGroupKeys is provided to the service via constructor.
		PatchSelectionService loaded = new PatchSelectionService(configManager,
			Set.of(), Set.of(), Set.of("ALLOTMENT|Catherby"));

		assertTrue(loaded.isGroupActive("ALLOTMENT|Catherby"));
		assertFalse(loaded.isGroupActive("HERB|Renamed Place"));
	}

	@Test
	public void loadHandlesLegacyV1BlobAsEmpty()
	{
		configManager.putRaw("betterfarming", "patchSelections",
			"{\"version\":1,\"selections\":{"
				+ "\"p1\":{\"selected\":true,\"seedId\":\"watermelon_seed\"}"
				+ "}}");

		PatchSelectionService loaded = new PatchSelectionService(configManager,
			Set.of("p1"), Set.of("watermelon_seed"));

		assertFalse("legacy v1 blobs are ignored", loaded.get("p1").isPresent());
	}

	@Test
	public void loadHandlesUnknownVersionAsEmpty()
	{
		configManager.putRaw("betterfarming", "patchSelections",
			"{\"version\":99,\"seeds\":{\"p1\":\"watermelon_seed\"}}");

		PatchSelectionService loaded = new PatchSelectionService(configManager,
			Set.of("p1"), Set.of("watermelon_seed"));

		assertFalse(loaded.get("p1").isPresent());
	}

	// ── templates ──

	private PatchSelectionService templateService()
	{
		return new PatchSelectionService(configManager,
			Set.of("p1", "p2"), Set.of("watermelon_seed", "toadflax_seed"),
			Set.of("HERB|Catherby", "TREE|Taverley"));
	}

	@Test
	public void saveAndLoadTemplateRoundTripsTheSetup()
	{
		PatchSelectionService s = templateService();
		s.setGroupActive("HERB|Catherby", true);
		s.setSeed("p1", "toadflax_seed");
		s.saveTemplate("herb run");

		// Change everything, then load the template back.
		s.setGroupActive("HERB|Catherby", false);
		s.setGroupActive("TREE|Taverley", true);
		s.setSeed("p1", "watermelon_seed");
		s.setSeed("p2", "watermelon_seed");

		assertTrue(s.loadTemplate("herb run"));
		assertTrue(s.isGroupActive("HERB|Catherby"));
		assertFalse("groups not in the template deactivate", s.isGroupActive("TREE|Taverley"));
		assertEquals("toadflax_seed", s.get("p1").orElseThrow().seedId());
		assertFalse("seeds not in the template clear", s.get("p2").isPresent());
	}

	@Test
	public void templatesPersistAcrossServiceInstances()
	{
		PatchSelectionService s = templateService();
		s.setGroupActive("HERB|Catherby", true);
		s.setSeed("p1", "toadflax_seed");
		s.saveTemplate("herb run");

		PatchSelectionService fresh = templateService();
		assertEquals(List.of("herb run"), fresh.templateNames());
		assertTrue(fresh.loadTemplate("herb run"));
		assertTrue(fresh.isGroupActive("HERB|Catherby"));
		assertEquals("toadflax_seed", fresh.get("p1").orElseThrow().seedId());
	}

	@Test
	public void loadFiresTheNormalEvents()
	{
		PatchSelectionService s = templateService();
		s.setSeed("p1", "toadflax_seed");
		s.saveTemplate("t");
		s.setSeed("p1", "watermelon_seed");

		List<PatchSelectionEvent> seen = new ArrayList<>();
		s.addListener(seen::add);
		s.loadTemplate("t");
		assertEquals(1, seen.size());
		assertEquals("toadflax_seed", seen.get(0).newSelection().seedId());
	}

	@Test
	public void unknownTemplateReturnsFalseAndChangesNothing()
	{
		PatchSelectionService s = templateService();
		s.setSeed("p1", "toadflax_seed");
		assertFalse(s.loadTemplate("nope"));
		assertEquals("toadflax_seed", s.get("p1").orElseThrow().seedId());
	}

	@Test
	public void deleteAndStaleIdsAreHandled()
	{
		PatchSelectionService s = templateService();
		s.saveTemplate("gone");
		s.deleteTemplate("gone");
		assertTrue(s.templateNames().isEmpty());
		assertFalse(s.loadTemplate("gone"));

		// A template carrying ids the dataset no longer has loads what it can.
		configManager.putRaw("betterfarming", "runTemplates",
			"{\"version\":1,\"templates\":{\"old\":{\"version\":2,"
				+ "\"activeGroups\":[\"HERB|Catherby\",\"HERB|Removed\"],"
				+ "\"seeds\":{\"p1\":\"toadflax_seed\",\"removed_patch\":\"toadflax_seed\","
				+ "\"p2\":\"removed_seed\"}}}}");
		assertTrue(s.loadTemplate("old"));
		assertTrue(s.isGroupActive("HERB|Catherby"));
		assertFalse(s.isGroupActive("HERB|Removed"));
		assertEquals("toadflax_seed", s.get("p1").orElseThrow().seedId());
		assertFalse("unknown seed id dropped", s.get("p2").isPresent());
	}
}
