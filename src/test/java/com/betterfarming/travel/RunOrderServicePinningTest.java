package com.betterfarming.travel;

import com.betterfarming.BetterFarmingConfig;
import com.betterfarming.data.FarmingData;
import com.betterfarming.data.Patch;
import com.betterfarming.data.PatchType;
import com.betterfarming.data.requirement.RequirementEvaluator;
import com.betterfarming.item.ItemTracker;
import com.betterfarming.state.PatchSelectionService;
import com.betterfarming.testsupport.FakeClient;
import com.betterfarming.testsupport.FakeConfigStore;
import com.betterfarming.ui.PatchAccessibilityService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Run-order stability: once planned, the sequence stays pinned while stops
 * complete or teleports shift; only a grown stop set or an explicit replan()
 * re-solves the ordering from the player's position.
 */
public class RunOrderServicePinningTest
{
	private static Patch patch(String id, String location, int x)
	{
		return new Patch(id, "Patch " + id, PatchType.HERB, location, null,
			new WorldPoint(x, 3200, 0), List.of());
	}

	private final FarmingData data = new FarmingData(
		List.of(
			patch("a", "A", 3200),
			patch("b", "B", 3300),
			patch("c", "C", 3400)),
		List.of());

	private final Set<String> growing = new HashSet<>();
	private FakeClient client;
	private PatchSelectionService selection;
	private RunOrderService service;

	@Before
	public void setUp()
	{
		client = new FakeClient();
		client.setPlayerPosition(new WorldPoint(3190, 3200, 0));
		FakeConfigStore store = new FakeConfigStore();
		selection = new PatchSelectionService(store, data);
		PatchAccessibilityService accessibility = new PatchAccessibilityService(
			client, data, new RequirementEvaluator());
		accessibility.refresh();
		BetterFarmingConfig config = new BetterFarmingConfig() {};
		TeleportAvailabilityService teleports = new TeleportAvailabilityService(
			List.of(), client, new ItemTracker(), config);
		selection.setGroupActive("HERB|A", true);
		selection.setGroupActive("HERB|B", true);
		selection.setGroupActive("HERB|C", true);
		service = new RunOrderService(data, selection, accessibility, teleports,
			client, config, Runnable::run, p -> !growing.contains(p.id()));
	}

	private List<String> order()
	{
		return service.legs().stream()
			.map(l -> l.stop().groupKey())
			.collect(Collectors.toList());
	}

	@Test
	public void initialPlanIsNearestFirst()
	{
		assertEquals(List.of("HERB|A", "HERB|B", "HERB|C"), order());
	}

	@Test
	public void playerMovementDoesNotReshuffleThePinnedOrder()
	{
		client.setPlayerPosition(new WorldPoint(3410, 3200, 0));
		service.recompute();
		assertEquals(List.of("HERB|A", "HERB|B", "HERB|C"), order());
	}

	@Test
	public void stopCompletedMidRunStaysInThePinnedRoute()
	{
		// Guidance checks completed stops off; dropping them here would make
		// the run counter lie. The crop-state filter is plan-time only.
		growing.add("b");
		service.recompute();
		assertEquals(List.of("HERB|A", "HERB|B", "HERB|C"), order());
	}

	@Test
	public void growingPatchesAreExcludedWhenPlanningAFreshRun()
	{
		growing.add("b");
		service.replan();
		assertEquals(List.of("HERB|A", "HERB|C"), order());

		// Grown by the next planning pass: routed again.
		growing.remove("b");
		service.replan();
		assertEquals(List.of("HERB|A", "HERB|B", "HERB|C"), order());
	}

	@Test
	public void deactivatedGroupDropsOutOfThePinnedRoute()
	{
		// Deactivation (unlike crop state) removes the stop mid-run too,
		// keeping the rest in planned order.
		selection.setGroupActive("HERB|B", false);
		service.recompute();
		assertEquals(List.of("HERB|A", "HERB|C"), order());
	}

	@Test
	public void replanReordersFromThePlayer()
	{
		client.setPlayerPosition(new WorldPoint(3410, 3200, 0));
		service.replan();
		assertEquals(List.of("HERB|C", "HERB|B", "HERB|A"), order());
	}
}
