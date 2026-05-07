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

	@Test
	public void getReturnsEmptyForUnknownPatch()
	{
		assertFalse(service.get("unknown").isPresent());
	}

	@Test
	public void setSelectedCreatesEntryWithNullSeed()
	{
		service.setSelected("falador_allotment_north_west", true);

		Optional<PatchSelection> sel = service.get("falador_allotment_north_west");
		assertTrue(sel.isPresent());
		assertTrue(sel.get().selected());
		assertNull(sel.get().seedId());
	}

	@Test
	public void setSeedCreatesEntryWithSelectedFalse()
	{
		service.setSeed("falador_allotment_north_west", "watermelon_seed");

		Optional<PatchSelection> sel = service.get("falador_allotment_north_west");
		assertTrue(sel.isPresent());
		assertFalse(sel.get().selected());
		assertEquals("watermelon_seed", sel.get().seedId());
	}

	@Test
	public void setSelectedPreservesSeed()
	{
		service.setSeed("p1", "watermelon_seed");
		service.setSelected("p1", true);

		assertEquals("watermelon_seed", service.get("p1").get().seedId());
		assertTrue(service.get("p1").get().selected());
	}

	@Test
	public void setSeedPreservesSelected()
	{
		service.setSelected("p1", true);
		service.setSeed("p1", "watermelon_seed");

		assertTrue(service.get("p1").get().selected());
		assertEquals("watermelon_seed", service.get("p1").get().seedId());
	}

	@Test
	public void setSeedNullClearsSeed()
	{
		service.setSeed("p1", "watermelon_seed");
		service.setSeed("p1", null);

		assertNull(service.get("p1").get().seedId());
	}

	@Test
	public void selectedStreamYieldsOnlySelectedEntries()
	{
		service.setSelected("p1", true);
		service.setSelected("p2", false);
		service.setSeed("p3", "watermelon_seed");  // seed only, not selected
		service.setSelected("p4", true);

		List<String> ids = new ArrayList<>();
		service.selected().forEach(s -> ids.add(s.patchId()));
		ids.sort(String::compareTo);

		assertEquals(List.of("p1", "p4"), ids);
	}

	@Test
	public void listenerReceivesEventOnMutation()
	{
		service.setSelected("p1", true);

		assertEquals(1, events.size());
		PatchSelectionEvent e = events.get(0);
		assertEquals("p1", e.patchId());
		assertNull(e.oldSelection());
		assertTrue(e.newSelection().selected());
	}

	@Test
	public void listenerReceivesOldAndNewOnSecondMutation()
	{
		service.setSelected("p1", true);
		service.setSeed("p1", "watermelon_seed");

		assertEquals(2, events.size());
		PatchSelectionEvent second = events.get(1);
		assertTrue(second.oldSelection().selected());
		assertNull(second.oldSelection().seedId());
		assertEquals("watermelon_seed", second.newSelection().seedId());
	}

	@Test
	public void noOpMutationFiresNoEvent()
	{
		service.setSelected("p1", true);
		events.clear();
		service.setSelected("p1", true);  // same value

		assertTrue("no-op should not fire event", events.isEmpty());
	}

	@Test
	public void listenerExceptionDoesNotBreakOtherListeners()
	{
		List<PatchSelectionEvent> received = new ArrayList<>();
		service.addListener(e -> { throw new RuntimeException("boom"); });
		service.addListener(received::add);

		service.setSelected("p1", true);

		assertEquals("upstream listener still fires", 1, events.size());
		assertEquals("downstream listener still fires", 1, received.size());
	}

	@Test
	public void removeListenerStopsReceivingEvents()
	{
		service.removeListener(eventsListener);  // initial listener from setUp; now removing
		events.clear();

		service.setSelected("p1", true);

		assertTrue("mutation should still be applied", service.get("p1").isPresent());
		assertTrue("removed listener should not receive event", events.isEmpty());
	}

	@Test
	public void persistsToConfigManagerOnMutation()
	{
		service.setSelected("p1", true);

		String blob = configManager.peek("betterfarming", "patchSelections");
		assertNotNull(blob);
		assertTrue("blob should mention p1", blob.contains("p1"));
	}

	@Test
	public void loadsFromConfigManagerOnConstruction()
	{
		configManager.putRaw("betterfarming", "patchSelections",
			"{\"version\":1,\"selections\":{"
				+ "\"p1\":{\"selected\":true,\"seedId\":\"watermelon_seed\"}"
				+ "}}");

		PatchSelectionService loaded = new PatchSelectionService(configManager,
			Set.of("p1"), Set.of("watermelon_seed"));

		assertTrue(loaded.get("p1").isPresent());
		assertTrue(loaded.get("p1").get().selected());
		assertEquals("watermelon_seed", loaded.get("p1").get().seedId());
	}

	@Test
	public void loadFiltersUnknownPatchIds()
	{
		configManager.putRaw("betterfarming", "patchSelections",
			"{\"version\":1,\"selections\":{"
				+ "\"p1\":{\"selected\":true,\"seedId\":null},"
				+ "\"renamed_or_removed\":{\"selected\":true,\"seedId\":null}"
				+ "}}");

		PatchSelectionService loaded = new PatchSelectionService(configManager,
			Set.of("p1"), Set.of());

		assertTrue(loaded.get("p1").isPresent());
		assertFalse(loaded.get("renamed_or_removed").isPresent());
	}

	@Test
	public void loadFiltersUnknownSeedIds()
	{
		configManager.putRaw("betterfarming", "patchSelections",
			"{\"version\":1,\"selections\":{"
				+ "\"p1\":{\"selected\":true,\"seedId\":\"removed_seed\"}"
				+ "}}");

		PatchSelectionService loaded = new PatchSelectionService(configManager,
			Set.of("p1"), Set.of("watermelon_seed"));

		// The patch is loaded but the seed is dropped
		assertTrue(loaded.get("p1").isPresent());
		assertNull(loaded.get("p1").get().seedId());
		assertTrue(loaded.get("p1").get().selected());
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
	public void loadHandlesEmptyBlob()
	{
		// No call to putRaw — getConfiguration returns null

		PatchSelectionService loaded = new PatchSelectionService(configManager,
			Set.of("p1"), Set.of());

		assertFalse(loaded.get("p1").isPresent());
	}

	@Test
	public void loadHandlesUnknownVersion()
	{
		configManager.putRaw("betterfarming", "patchSelections",
			"{\"version\":99,\"selections\":{"
				+ "\"p1\":{\"selected\":true,\"seedId\":null}"
				+ "}}");

		PatchSelectionService loaded = new PatchSelectionService(configManager,
			Set.of("p1"), Set.of());

		// Unknown version: start empty, do not attempt to migrate
		assertFalse(loaded.get("p1").isPresent());
	}

	@Test
	public void noOpMutationDoesNotWriteToConfig()
	{
		service.setSelected("p1", true);
		int writesAfterFirst = configManager.getWriteCount();

		service.setSelected("p1", true);  // no-op

		assertEquals(writesAfterFirst, configManager.getWriteCount());
	}
}
