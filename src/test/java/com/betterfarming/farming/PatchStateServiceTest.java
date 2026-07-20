package com.betterfarming.farming;

import com.betterfarming.data.Patch;
import com.betterfarming.data.PatchType;
import com.betterfarming.data.StateBounds;
import com.betterfarming.testsupport.FakeClient;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Herb-value anchors used below (from the bundled table): 0-3 weeds/EMPTY,
 * 4-7 growing, 8-10 harvestable.
 */
public class PatchStateServiceTest
{
	// Mirrors falador_herb: region 12083, varbit 4774, bounds y >= 3272.
	private static final WorldPoint PATCH_TILE = new WorldPoint(3058, 3311, 0);
	private static final WorldPoint LUMBRIDGE = new WorldPoint(3222, 3218, 0);
	private static final int VARBIT = 4774;

	private static PatchStateTable table;

	private final Patch herb = new Patch("falador_herb", "Falador herb patch", PatchType.HERB,
		"South of Falador", null, PATCH_TILE, List.of(),
		VARBIT, 12083, List.of(12083), new StateBounds(null, null, 3272, null, null));
	private final Patch remoteHerb = new Patch("catherby_herb", "Catherby herb patch", PatchType.HERB,
		"Catherby", null, new WorldPoint(2813, 3463, 0), List.of(),
		4774, 11062, List.of(11062, 11061, 11318, 11317), null);

	private FakeClient client;
	private Map<String, String> timetracking;
	private AtomicLong now;
	private PatchStateService service;

	@BeforeClass
	public static void loadTable() throws IOException
	{
		table = PatchStateTable.load(new Gson());
	}

	@Before
	public void setUp()
	{
		client = new FakeClient();
		timetracking = new HashMap<>();
		now = new AtomicLong(1_000_000L);
		service = new PatchStateService(List.of(herb, remoteHerb), client, table,
			timetracking::get, now::get);
	}

	@Test
	public void liveVarbitReadWhileStandingInRegion()
	{
		client.setPlayerPosition(PATCH_TILE);
		client.setVarbit(VARBIT, 8);
		service.refresh();
		assertEquals(CropState.HARVESTABLE, service.state(herb));
		assertTrue(service.needsVisit(herb));

		client.setVarbit(VARBIT, 4);
		service.refresh();
		assertEquals(CropState.GROWING, service.state(herb));
		assertFalse(service.needsVisit(herb));
	}

	@Test
	public void noLiveReadOutsideTheRegion()
	{
		client.setPlayerPosition(LUMBRIDGE);
		client.setVarbit(VARBIT, 8);
		service.refresh();
		assertEquals(CropState.UNKNOWN, service.state(herb));
		// Unknown state still warrants a visit.
		assertTrue(service.needsVisit(herb));
	}

	@Test
	public void boundsGateBlocksLiveReadInSharedRegion()
	{
		// Region 12083 but south of y=3272: Port Sarim's side of the split.
		client.setPlayerPosition(new WorldPoint(3058, 3266, 0));
		client.setVarbit(VARBIT, 8);
		service.refresh();
		assertEquals(CropState.UNKNOWN, service.state(herb));
	}

	@Test
	public void liveObservationIsCachedAfterLeaving()
	{
		client.setPlayerPosition(PATCH_TILE);
		client.setVarbit(VARBIT, 4);
		service.refresh();
		client.setPlayerPosition(LUMBRIDGE);
		service.refresh();
		assertEquals(CropState.GROWING, service.state(herb));
	}

	@Test
	public void timetrackingObservationCoversRemotePatches()
	{
		client.setPlayerPosition(LUMBRIDGE);
		timetracking.put("11062.4774", "4:" + now.get());
		service.refresh();
		assertEquals(CropState.GROWING, service.state(remoteHerb));
		assertFalse(service.needsVisit(remoteHerb));
	}

	@Test
	public void growingDecaysToUnknownAfterMinGrowTime()
	{
		client.setPlayerPosition(LUMBRIDGE);
		timetracking.put("11062.4774", "4:" + now.get());
		service.refresh();
		assertEquals(CropState.GROWING, service.state(remoteHerb));

		// 81 minutes later the fastest herb could be done: worth a visit.
		now.addAndGet(81 * 60);
		service.refresh();
		assertEquals(CropState.UNKNOWN, service.state(remoteHerb));
		assertTrue(service.needsVisit(remoteHerb));
	}

	@Test
	public void harvestableObservationDoesNotDecay()
	{
		client.setPlayerPosition(LUMBRIDGE);
		timetracking.put("11062.4774", "8:" + (now.get() - 10_000));
		service.refresh();
		assertEquals(CropState.HARVESTABLE, service.state(remoteHerb));
	}

	@Test
	public void malformedTimetrackingValueIsIgnored()
	{
		client.setPlayerPosition(LUMBRIDGE);
		timetracking.put("11062.4774", "garbage");
		service.refresh();
		assertEquals(CropState.UNKNOWN, service.state(remoteHerb));
	}

	@Test
	public void loginScreenClearsTheLiveCache()
	{
		client.setPlayerPosition(PATCH_TILE);
		client.setVarbit(VARBIT, 4);
		service.refresh();
		assertEquals(CropState.GROWING, service.state(herb));

		client.setGameState(GameState.LOGIN_SCREEN);
		GameStateChanged event = new GameStateChanged();
		event.setGameState(GameState.LOGIN_SCREEN);
		service.onGameStateChanged(event);
		assertEquals(CropState.UNKNOWN, service.state(herb));
	}

	@Test
	public void groupProgressJudgement()
	{
		client.setPlayerPosition(PATCH_TILE);
		client.setVarbit(VARBIT, 4); // herb growing
		service.refresh();

		// Live GROWING + a varbit-mapped patch with no observation yet:
		// INCOMPLETE — its state will resolve on arrival, so completion
		// demands observation rather than proximity.
		assertEquals(StopProgress.INCOMPLETE, service.groupProgress(List.of(herb, remoteHerb)));
		// All growing → COMPLETE. A new Time Tracking write reaches the
		// service through config-change invalidation, as in production.
		timetracking.put("11062.4774", "4:" + now.get());
		net.runelite.client.events.ConfigChanged change = new net.runelite.client.events.ConfigChanged();
		change.setGroup("timetracking");
		service.onConfigChanged(change);
		service.refresh();
		assertEquals(StopProgress.COMPLETE, service.groupProgress(List.of(herb, remoteHerb)));
		// Anything needing work → INCOMPLETE, even alongside unknowns.
		client.setVarbit(VARBIT, 0);
		service.refresh();
		assertEquals(StopProgress.INCOMPLETE, service.groupProgress(List.of(herb, remoteHerb)));
	}

	@Test
	public void mappedPatchWithoutObservationDemandsAVisit()
	{
		// No live read (player far away), no timetracking data: the patch is
		// varbit-mapped, so the stop must not proximity-complete — a
		// HARVESTABLE crop would otherwise check off in the arrival-tick
		// window before the live read lands.
		client.setPlayerPosition(LUMBRIDGE);
		service.refresh();
		assertEquals(StopProgress.INCOMPLETE, service.groupProgress(List.of(herb)));
	}

	@Test
	public void unmappedPatchKeepsTheProximityFallback()
	{
		// A patch with no state varbit at all can never be observed: UNKNOWN
		// lets guidance fall back to proximity completion.
		Patch unmapped = new Patch("spirit_tree", "Spirit tree patch",
			PatchType.SPIRIT_TREE, "Etceteria", null, new WorldPoint(2613, 3855, 0),
			List.of(), null, null, null, null);
		PatchStateService s = new PatchStateService(List.of(unmapped), client, table,
			timetracking::get, now::get);
		s.refresh();
		assertEquals(StopProgress.UNKNOWN, s.groupProgress(List.of(unmapped)));
	}

	@Test
	public void watchedPlantingStartsTheCompostStep()
	{
		client.setPlayerPosition(PATCH_TILE);
		client.setVarbit(VARBIT, 0); // empty
		service.refresh();
		assertFalse(service.compostPending(herb));

		client.setVarbit(VARBIT, 4); // planted in front of us
		service.refresh();
		assertTrue(service.compostPending(herb));

		// "You treat the herb patch with supercompost." while standing there.
		service.onChatMessage(new net.runelite.api.events.ChatMessage(null,
			net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
			"You treat the herb patch with supercompost.", null, 0));
		assertFalse(service.compostPending(herb));
	}

	@Test
	public void compostBeforeSeedingCountsForTheNewCrop()
	{
		// Compost the EMPTY patch first, then plant: no compost step should
		// linger — treating before seeding is valid and carries into the crop.
		client.setPlayerPosition(PATCH_TILE);
		client.setVarbit(VARBIT, 0); // empty
		service.refresh();
		service.onChatMessage(new net.runelite.api.events.ChatMessage(null,
			net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
			"You treat the herb patch with supercompost.", null, 0));

		client.setVarbit(VARBIT, 4); // now planted
		service.refresh();
		assertFalse("pre-plant compost already satisfied the step",
			service.compostPending(herb));
	}

	@Test
	public void harvestStartsAFreshCompostCycle()
	{
		// Plant + compost one crop...
		client.setPlayerPosition(PATCH_TILE);
		client.setVarbit(VARBIT, 0);
		service.refresh();
		client.setVarbit(VARBIT, 4);
		service.refresh();
		service.onChatMessage(new net.runelite.api.events.ChatMessage(null,
			net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
			"You treat the herb patch with supercompost.", null, 0));
		assertFalse(service.compostPending(herb));

		// ...harvest to empty and replant: the flag reset with the cycle,
		// so the new crop prompts for compost again.
		client.setVarbit(VARBIT, 0);
		service.refresh();
		client.setVarbit(VARBIT, 4);
		service.refresh();
		assertTrue("new crop, new compost step", service.compostPending(herb));
	}

	@Test
	public void cropsAlreadyGrowingAtFirstSightHaveNoCompostStep()
	{
		// First observation is GROWING: the planting wasn't watched, the crop
		// may already be treated — never prompt.
		client.setPlayerPosition(PATCH_TILE);
		client.setVarbit(VARBIT, 4);
		service.refresh();
		assertFalse(service.compostPending(herb));
	}

	@Test
	public void compostChatIgnoredWhenNoPatchIsNearby()
	{
		client.setPlayerPosition(PATCH_TILE);
		client.setVarbit(VARBIT, 0);
		service.refresh();
		client.setVarbit(VARBIT, 4);
		service.refresh();

		client.setPlayerPosition(LUMBRIDGE);
		service.onChatMessage(new net.runelite.api.events.ChatMessage(null,
			net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
			"You treat the herb patch with compost.", null, 0));
		assertTrue("message far from any patch changes nothing",
			service.compostPending(herb));
	}

	@Test
	public void stateChangeNotifiesListeners()
	{
		java.util.concurrent.atomic.AtomicInteger notified = new java.util.concurrent.atomic.AtomicInteger();
		service.addListener(notified::incrementAndGet);
		client.setPlayerPosition(PATCH_TILE);
		client.setVarbit(VARBIT, 4);
		service.refresh();
		assertEquals(1, notified.get());
		service.refresh(); // unchanged
		assertEquals(1, notified.get());
	}
}
