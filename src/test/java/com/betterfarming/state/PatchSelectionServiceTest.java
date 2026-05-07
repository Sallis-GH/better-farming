package com.betterfarming.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PatchSelectionServiceTest
{
	private PatchSelectionService service;
	private List<PatchSelectionEvent> events;
	private Consumer<PatchSelectionEvent> eventsListener;

	@Before
	public void setUp()
	{
		service = new PatchSelectionService();
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

		assertEquals("downstream listener still fires", 1, received.size());
	}

	@Test
	public void removeListenerStopsReceivingEvents()
	{
		service.removeListener(eventsListener);  // initial listener from setUp; now removing
		events.clear();

		service.setSelected("p1", true);

		assertTrue(events.isEmpty());
	}
}
