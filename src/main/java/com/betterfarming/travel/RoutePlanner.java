package com.betterfarming.travel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.ToIntFunction;
import lombok.Value;
import lombok.experimental.Accessors;
import net.runelite.api.coords.WorldPoint;

/**
 * Orders a set of farming stops to minimise estimated travel ticks, using the
 * currently-available teleports.
 *
 * Cost model: walking is straight-line Chebyshev distance at 2 tiles/tick
 * (running), with legs beyond MAX_WALK_TILES treated as impossible — farm
 * routes always teleport near a patch, and straight-line distance across
 * continents would otherwise look deceptively cheap. A teleport leg costs the
 * teleport's duration plus walking from its destination (plus walking to its
 * origin for network/portal edges). Plane changes add a small constant for
 * stairs/ladders.
 *
 * Ordering: exact Held-Karp DP up to 13 stops (open path from the player's
 * position, no return), nearest-neighbour + 2-opt beyond that. All methods are
 * pure — no client access — so the planner is fully unit-testable.
 */
public final class RoutePlanner
{
	static final double TICKS_PER_TILE = 0.5;
	static final int MAX_WALK_TILES = 250;
	static final double PLANE_CHANGE_TICKS = 7;
	static final double IMPOSSIBLE = 1e9;
	private static final int EXACT_LIMIT = 13;

	/**
	 * Soft cost per inventory slot a teleport's items occupy, folded into leg
	 * selection (not into the reported tick estimate): an equipped skills
	 * necklace (0 slots) beats a teleport tab (1 slot) within 2.5 ticks, and a
	 * three-rune spell within 7.5 — "prefer slot-free options at similar
	 * distances" without ever overriding a clearly faster teleport.
	 */
	static final double SLOT_PENALTY_TICKS = 2.5;

	/**
	 * Single-hop legs cheaper than this skip the multi-hop search: farm legs
	 * are normally one teleport + a short walk, and the full graph search is
	 * only worth its cost when no single hop covers the leg (islands like
	 * Harmony: teleport → ship → ship).
	 */
	static final double CHAIN_SEARCH_THRESHOLD_TICKS = 50;

	/** Default slot estimate: one inventory slot per AND-term item need. */
	public static final ToIntFunction<Teleport> DEFAULT_SLOT_COST = t -> t.items().size();

	private RoutePlanner()
	{
	}

	@Value
	@Accessors(fluent = true)
	public static class Stop
	{
		String groupKey;
		String displayName;
		WorldPoint point;
	}

	/** One leg of the planned route; teleport == null means walk. */
	@Value
	@Accessors(fluent = true)
	public static class Leg
	{
		Stop stop;
		Teleport teleport;
		int estimatedTicks;
	}

	public static List<Leg> plan(WorldPoint start, List<Stop> stops, List<Teleport> teleports)
	{
		return plan(start, stops, teleports, 0, DEFAULT_SLOT_COST);
	}

	public static List<Leg> plan(WorldPoint start, List<Stop> stops, List<Teleport> teleports,
		int pohBiasTicks)
	{
		return plan(start, stops, teleports, pohBiasTicks, DEFAULT_SLOT_COST);
	}

	/**
	 * @param pohBiasTicks when &gt; 0, a house-chain teleport wins a leg if its
	 *     cost is within this many ticks of the overall best — players who run
	 *     everything through their house save inventory space at a small time
	 *     cost.
	 * @param slotCost inventory slots a teleport's items occupy; live-aware in
	 *     production (currently-equipped jewellery costs nothing).
	 */
	public static List<Leg> plan(WorldPoint start, List<Stop> stops, List<Teleport> teleports,
		int pohBiasTicks, ToIntFunction<Teleport> slotCost)
	{
		if (stops.isEmpty() || start == null)
		{
			return Collections.emptyList();
		}
		int n = stops.size();

		// legCosts[i][j]: cost from point i to stop j, where index 0 is the
		// start position and 1..n are stops (source side); target side is 0..n-1.
		double[][] cost = new double[n + 1][n];
		Teleport[][] via = new Teleport[n + 1][n];
		for (int i = 0; i <= n; i++)
		{
			WorldPoint from = i == 0 ? start : stops.get(i - 1).point();
			for (int j = 0; j < n; j++)
			{
				if (i == j + 1)
				{
					cost[i][j] = IMPOSSIBLE;
					continue;
				}
				BestLeg best = bestLeg(from, stops.get(j).point(), teleports, pohBiasTicks, slotCost);
				cost[i][j] = best.cost;
				via[i][j] = best.teleport;
			}
		}

		int[] order = n <= EXACT_LIMIT ? heldKarp(cost, n) : twoOpt(cost, n);

		List<Leg> legs = new ArrayList<>(n);
		int prev = 0;
		for (int index : order)
		{
			double legCost = cost[prev][index];
			legs.add(new Leg(stops.get(index), via[prev][index],
				(int) Math.min(Math.round(legCost), Integer.MAX_VALUE)));
			prev = index + 1;
		}
		return capOncePerRun(start, legs, teleports, pohBiasTicks, slotCost);
	}

	public static List<Leg> planFixedOrder(WorldPoint start, List<Stop> stops,
		List<Teleport> teleports, int pohBiasTicks)
	{
		return planFixedOrder(start, stops, teleports, pohBiasTicks, DEFAULT_SLOT_COST);
	}

	/**
	 * Recomputes teleports and tick estimates for an already-decided stop
	 * sequence without reordering it — used to keep a mid-run route pinned
	 * while teleport availability shifts underneath it.
	 */
	public static List<Leg> planFixedOrder(WorldPoint start, List<Stop> stops,
		List<Teleport> teleports, int pohBiasTicks, ToIntFunction<Teleport> slotCost)
	{
		if (stops.isEmpty() || start == null)
		{
			return Collections.emptyList();
		}
		List<Leg> legs = new ArrayList<>(stops.size());
		WorldPoint from = start;
		for (Stop stop : stops)
		{
			BestLeg best = bestLeg(from, stop.point(), teleports, pohBiasTicks, slotCost);
			legs.add(new Leg(stop, best.teleport,
				(int) Math.min(Math.round(best.cost), Integer.MAX_VALUE)));
			from = stop.point();
		}
		return capOncePerRun(start, legs, teleports, pohBiasTicks, slotCost);
	}

	/**
	 * Once-per-run edges (free home teleport) may carry at most the first leg
	 * that uses one; later such legs are re-priced without them, so tab/rune
	 * alternatives (whose items then surface in the run-items list) or plain
	 * walking take over.
	 */
	private static List<Leg> capOncePerRun(WorldPoint start, List<Leg> legs,
		List<Teleport> teleports, int pohBiasTicks, ToIntFunction<Teleport> slotCost)
	{
		List<Teleport> reusable = null;
		boolean used = false;
		List<Leg> out = new ArrayList<>(legs.size());
		WorldPoint from = start;
		for (Leg leg : legs)
		{
			if (leg.teleport() != null && leg.teleport().oncePerRun() && used)
			{
				if (reusable == null)
				{
					reusable = new ArrayList<>();
					for (Teleport t : teleports)
					{
						if (!t.oncePerRun())
						{
							reusable.add(t);
						}
					}
				}
				BestLeg best = bestLeg(from, leg.stop().point(), reusable, pohBiasTicks, slotCost);
				out.add(new Leg(leg.stop(), best.teleport,
					(int) Math.min(Math.round(best.cost), Integer.MAX_VALUE)));
			}
			else
			{
				used |= leg.teleport() != null && leg.teleport().oncePerRun();
				out.add(leg);
			}
			from = leg.stop().point();
		}
		return out;
	}

	// ── leg cost ──

	private static final class BestLeg
	{
		/** Real ticks — slot penalties influence selection, not the estimate. */
		double cost = IMPOSSIBLE;
		/** Null = plain walk; a chainOf() composite for multi-hop paths. */
		Teleport teleport;
	}

	/**
	 * Cheapest way from `from` to `to`. Single teleport + walking resolves
	 * almost every farm leg; when nothing single-hop is decent the full
	 * multi-hop graph search runs (teleport → ship → ship reaches islands
	 * that straight-line walking never could). Selection is by penalized
	 * cost: real ticks + SLOT_PENALTY_TICKS per inventory slot.
	 */
	private static BestLeg bestLeg(WorldPoint from, WorldPoint to, List<Teleport> teleports,
		int pohBiasTicks, ToIntFunction<Teleport> slotCost)
	{
		BestLeg best = new BestLeg();
		best.cost = walkTicks(from, to);
		double bestPenalized = best.cost;
		BestLeg bestPoh = new BestLeg();
		double bestPohPenalized = IMPOSSIBLE;
		for (Teleport t : teleports)
		{
			double c = t.durationTicks() + walkTicks(t.destination(), to);
			if (t.origin() != null)
			{
				c += walkTicks(from, t.origin());
			}
			double penalized = c + SLOT_PENALTY_TICKS * slotCost.applyAsInt(t);
			if (penalized < bestPenalized)
			{
				bestPenalized = penalized;
				best.cost = c;
				best.teleport = t;
			}
			if (t.viaPoh() && penalized < bestPohPenalized)
			{
				bestPohPenalized = penalized;
				bestPoh.cost = c;
				bestPoh.teleport = t;
			}
		}

		if (bestPenalized > CHAIN_SEARCH_THRESHOLD_TICKS)
		{
			BestLeg chained = chainSearch(from, to, teleports, slotCost);
			double chainedPenalized = chained.teleport == null ? IMPOSSIBLE
				: chained.cost + SLOT_PENALTY_TICKS * slotCost.applyAsInt(chained.teleport);
			if (chainedPenalized < bestPenalized)
			{
				best = chained;
				bestPenalized = chainedPenalized;
			}
		}

		if (pohBiasTicks > 0 && bestPoh.teleport != null
			&& bestPohPenalized <= bestPenalized + pohBiasTicks)
		{
			return bestPoh;
		}
		return best;
	}

	/**
	 * Dijkstra over the transport graph: nodes are teleport destinations plus
	 * the start; every teleport is an edge from any node (walking to its
	 * origin when it has one). O(V²) — only invoked for legs no single hop
	 * covers, so the quadratic cost stays off the common path.
	 */
	private static BestLeg chainSearch(WorldPoint from, WorldPoint to, List<Teleport> teleports,
		ToIntFunction<Teleport> slotCost)
	{
		int n = teleports.size();
		// Node ids: 0..n-1 = teleport destinations, n = start.
		double[] dist = new double[n + 1];
		int[] prevNode = new int[n + 1];
		boolean[] done = new boolean[n + 1];
		Arrays.fill(dist, IMPOSSIBLE);
		Arrays.fill(prevNode, -1);
		dist[n] = 0;

		double[] penalty = new double[n];
		for (int j = 0; j < n; j++)
		{
			penalty[j] = SLOT_PENALTY_TICKS * slotCost.applyAsInt(teleports.get(j));
		}

		for (int iter = 0; iter <= n; iter++)
		{
			int u = -1;
			for (int v = 0; v <= n; v++)
			{
				if (!done[v] && dist[v] < IMPOSSIBLE && (u == -1 || dist[v] < dist[u]))
				{
					u = v;
				}
			}
			if (u == -1)
			{
				break;
			}
			done[u] = true;
			WorldPoint at = u == n ? from : teleports.get(u).destination();
			for (int j = 0; j < n; j++)
			{
				if (done[j])
				{
					continue;
				}
				Teleport t = teleports.get(j);
				double edge = t.durationTicks() + penalty[j];
				if (t.origin() != null)
				{
					edge += walkTicks(at, t.origin());
				}
				if (dist[u] + edge < dist[j])
				{
					dist[j] = dist[u] + edge;
					prevNode[j] = u;
				}
			}
		}

		// Best terminal node: walk from its point to the target.
		BestLeg best = new BestLeg();
		int bestNode = -1;
		double bestPenalized = IMPOSSIBLE;
		for (int j = 0; j < n; j++)
		{
			if (dist[j] >= IMPOSSIBLE)
			{
				continue;
			}
			double total = dist[j] + walkTicks(teleports.get(j).destination(), to);
			if (total < bestPenalized)
			{
				bestPenalized = total;
				bestNode = j;
			}
		}
		if (bestNode == -1)
		{
			return best;
		}

		List<Teleport> hops = new ArrayList<>();
		for (int node = bestNode; node != n; node = prevNode[node])
		{
			hops.add(0, teleports.get(node));
		}
		best.teleport = Teleport.chainOf(hops);
		// Real ticks, penalties excluded: durations + inter-hop walks + final walk.
		double real = 0;
		WorldPoint at = from;
		for (Teleport hop : hops)
		{
			if (hop.origin() != null)
			{
				real += walkTicks(at, hop.origin());
			}
			real += hop.durationTicks();
			at = hop.destination();
		}
		best.cost = real + walkTicks(at, to);
		return best;
	}

	static double walkTicks(WorldPoint a, WorldPoint b)
	{
		int dist = Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()));
		if (dist > MAX_WALK_TILES)
		{
			return IMPOSSIBLE;
		}
		double ticks = dist * TICKS_PER_TILE;
		if (a.getPlane() != b.getPlane())
		{
			ticks += PLANE_CHANGE_TICKS;
		}
		return ticks;
	}

	// ── ordering ──

	/** Exact open-path TSP. dp[mask][last] = min cost visiting mask, ending at last. */
	private static int[] heldKarp(double[][] cost, int n)
	{
		double[][] dp = new double[1 << n][n];
		int[][] parent = new int[1 << n][n];
		for (double[] row : dp)
		{
			Arrays.fill(row, Double.MAX_VALUE);
		}
		for (int j = 0; j < n; j++)
		{
			dp[1 << j][j] = cost[0][j];
			parent[1 << j][j] = -1;
		}
		for (int mask = 1; mask < (1 << n); mask++)
		{
			for (int last = 0; last < n; last++)
			{
				if ((mask & (1 << last)) == 0 || dp[mask][last] == Double.MAX_VALUE)
				{
					continue;
				}
				for (int next = 0; next < n; next++)
				{
					if ((mask & (1 << next)) != 0)
					{
						continue;
					}
					int nextMask = mask | (1 << next);
					double c = dp[mask][last] + cost[last + 1][next];
					if (c < dp[nextMask][next])
					{
						dp[nextMask][next] = c;
						parent[nextMask][next] = last;
					}
				}
			}
		}
		int full = (1 << n) - 1;
		int bestLast = 0;
		for (int j = 1; j < n; j++)
		{
			if (dp[full][j] < dp[full][bestLast])
			{
				bestLast = j;
			}
		}
		int[] order = new int[n];
		int mask = full;
		int last = bestLast;
		for (int i = n - 1; i >= 0; i--)
		{
			order[i] = last;
			int prev = parent[mask][last];
			mask ^= (1 << last);
			last = prev;
		}
		return order;
	}

	/** Nearest-neighbour construction + 2-opt improvement for larger runs. */
	private static int[] twoOpt(double[][] cost, int n)
	{
		int[] order = new int[n];
		boolean[] used = new boolean[n];
		int from = 0; // start row
		for (int i = 0; i < n; i++)
		{
			int best = -1;
			for (int j = 0; j < n; j++)
			{
				if (!used[j] && (best == -1 || cost[from][j] < cost[from][best]))
				{
					best = j;
				}
			}
			order[i] = best;
			used[best] = true;
			from = best + 1;
		}
		boolean improved = true;
		while (improved)
		{
			improved = false;
			for (int i = 0; i < n - 1; i++)
			{
				for (int k = i + 1; k < n; k++)
				{
					if (reverseGain(cost, order, i, k) < -1e-9)
					{
						reverse(order, i, k);
						improved = true;
					}
				}
			}
		}
		return order;
	}

	private static double reverseGain(double[][] cost, int[] order, int i, int k)
	{
		double before = segmentCost(cost, order, i, k, false);
		double after = segmentCost(cost, order, i, k, true);
		return after - before;
	}

	/**
	 * Cost of the sub-route covering positions [i..k] including the edge into
	 * position i (and out of k when k isn't last), optionally with the segment
	 * reversed. Teleport-based costs are asymmetric, so 2-opt must recompute
	 * the whole segment rather than just the two swapped edges.
	 */
	private static double segmentCost(double[][] cost, int[] order, int i, int k, boolean reversed)
	{
		int n = order.length;
		double total = 0;
		int prevRow = i == 0 ? 0 : order[i - 1] + 1;
		for (int pos = i; pos <= k; pos++)
		{
			int stop = reversed ? order[k - (pos - i)] : order[pos];
			total += cost[prevRow][stop];
			prevRow = stop + 1;
		}
		if (k + 1 < n)
		{
			total += cost[prevRow][order[k + 1]];
		}
		return total;
	}

	private static void reverse(int[] order, int i, int k)
	{
		while (i < k)
		{
			int tmp = order[i];
			order[i] = order[k];
			order[k] = tmp;
			i++;
			k--;
		}
	}
}
