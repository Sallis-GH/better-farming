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
	 * are normally one teleport + a short walk. Above it, the graph search
	 * runs and may replace an expensive single hop — a leg can look "covered"
	 * by one teleport plus a huge straight-line walk (which happily crosses
	 * water) while a teleport → ship → boat chain is far faster; gating the
	 * search on outright unreachability shipped once and sent players walking
	 * from Lumbridge to Port Sarim for a charter instead of casting the
	 * ectophial (HarmonyRouteRegressionTest).
	 */
	static final double CHAIN_SEARCH_THRESHOLD_TICKS = 50;

	/**
	 * Selection penalty on charter ships: the fare occupies a coin stack and
	 * the real interaction (dialogue, confirm, fade) far exceeds the vendored
	 * duration, so any similarly-priced alternative should win. Selection
	 * only — the reported estimate stays data-driven.
	 */
	static final double CHARTER_PENALTY_TICKS = 20;

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
		PlanContext ctx = new PlanContext(teleports, slotCost);

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
				BestLeg best = bestLeg(from, stops.get(j).point(), ctx, pohBiasTicks);
				cost[i][j] = best.cost;
				via[i][j] = best.teleport;
			}
		}

		int[] order = n <= EXACT_LIMIT ? heldKarp(cost, n) : twoOpt(cost, n);

		List<Leg> legs = new ArrayList<>(n);
		int prev = 0;
		for (int index : order)
		{
			legs.add(toLeg(stops.get(index), via[prev][index], cost[prev][index]));
			prev = index + 1;
		}
		return capOncePerRun(start, legs, ctx, pohBiasTicks, slotCost);
	}

	/** Unreachable legs get UNREACHABLE_TICKS instead of a 1e9 cast artefact. */
	public static final int UNREACHABLE_TICKS = -1;

	private static Leg toLeg(Stop stop, Teleport teleport, double cost)
	{
		int ticks = cost >= IMPOSSIBLE ? UNREACHABLE_TICKS
			: (int) Math.min(Math.round(cost), Integer.MAX_VALUE);
		return new Leg(stop, teleport, ticks);
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
		PlanContext ctx = new PlanContext(teleports, slotCost);
		List<Leg> legs = new ArrayList<>(stops.size());
		WorldPoint from = start;
		for (Stop stop : stops)
		{
			BestLeg best = bestLeg(from, stop.point(), ctx, pohBiasTicks);
			legs.add(toLeg(stop, best.teleport, best.cost));
			from = stop.point();
		}
		return capOncePerRun(start, legs, ctx, pohBiasTicks, slotCost);
	}

	/**
	 * Once-per-run edges (free home teleport) may carry at most the first leg
	 * that uses one; later such legs are re-priced without them, so tab/rune
	 * alternatives (whose items then surface in the run-items list) or plain
	 * walking take over.
	 */
	private static List<Leg> capOncePerRun(WorldPoint start, List<Leg> legs,
		PlanContext ctx, int pohBiasTicks, ToIntFunction<Teleport> slotCost)
	{
		boolean used = false;
		List<Leg> out = new ArrayList<>(legs.size());
		WorldPoint from = start;
		for (Leg leg : legs)
		{
			if (leg.teleport() != null && leg.teleport().oncePerRun() && used)
			{
				BestLeg best = bestLeg(from, leg.stop().point(),
					ctx.withoutOncePerRun(slotCost), pohBiasTicks);
				out.add(toLeg(leg.stop(), best.teleport, best.cost));
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
		/** Selection cost: real ticks + SLOT_PENALTY_TICKS per inventory slot. */
		double penalized = IMPOSSIBLE;
		/** Null = plain walk; a chainOf() composite for multi-hop paths. */
		Teleport teleport;
	}

	/**
	 * Per-plan working state: slot penalties evaluated once per teleport (the
	 * live slot-cost function scans equipment maps — hundreds of thousands of
	 * evaluations per plan otherwise), and one cached multi-hop search per
	 * source point (its result is target-independent).
	 */
	private static final class PlanContext
	{
		final List<Teleport> teleports;
		final double[] penalty;
		final java.util.Map<WorldPoint, ChainPaths> chainCache = new java.util.HashMap<>();
		PlanContext filtered; // lazily built without once-per-run edges

		PlanContext(List<Teleport> teleports, ToIntFunction<Teleport> slotCost)
		{
			this.teleports = teleports;
			this.penalty = new double[teleports.size()];
			for (int i = 0; i < teleports.size(); i++)
			{
				Teleport t = teleports.get(i);
				penalty[i] = SLOT_PENALTY_TICKS * slotCost.applyAsInt(t)
					+ (t.type() == TeleportType.CHARTER_SHIP ? CHARTER_PENALTY_TICKS : 0);
			}
		}

		PlanContext withoutOncePerRun(ToIntFunction<Teleport> slotCost)
		{
			if (filtered == null)
			{
				List<Teleport> reusable = new ArrayList<>();
				for (Teleport t : teleports)
				{
					if (!t.oncePerRun())
					{
						reusable.add(t);
					}
				}
				filtered = new PlanContext(reusable, slotCost);
			}
			return filtered;
		}
	}

	/**
	 * Cheapest way from `from` to `to`. A single teleport + walking resolves
	 * every normal farm leg; when the best single hop costs
	 * {@link #CHAIN_SEARCH_THRESHOLD_TICKS} or more, the multi-hop graph
	 * search runs too and wins if cheaper (islands like Harmony: teleport →
	 * ship → boat beats one teleport plus a sea-crossing straight-line walk).
	 * Selection is by penalized cost (real ticks + SLOT_PENALTY_TICKS per
	 * inventory slot + CHARTER_PENALTY_TICKS for charters); reported cost is
	 * real ticks.
	 */
	private static BestLeg bestLeg(WorldPoint from, WorldPoint to, PlanContext ctx,
		int pohBiasTicks)
	{
		BestLeg best = new BestLeg();
		best.cost = walkTicks(from, to);
		best.penalized = best.cost;
		BestLeg bestPoh = new BestLeg();
		List<Teleport> teleports = ctx.teleports;
		for (int j = 0; j < teleports.size(); j++)
		{
			Teleport t = teleports.get(j);
			double c = t.durationTicks() + walkTicks(t.destination(), to);
			if (t.origin() != null)
			{
				c += walkTicks(from, t.origin());
			}
			double penalized = c + ctx.penalty[j];
			if (penalized < best.penalized)
			{
				best.penalized = penalized;
				best.cost = c;
				best.teleport = t;
			}
			if (t.viaPoh() && penalized < bestPoh.penalized)
			{
				bestPoh.penalized = penalized;
				bestPoh.cost = c;
				bestPoh.teleport = t;
			}
		}

		if (best.penalized >= CHAIN_SEARCH_THRESHOLD_TICKS)
		{
			BestLeg chained = chainedLeg(from, to, ctx);
			if (chained.penalized < best.penalized)
			{
				best = chained;
			}
		}

		if (pohBiasTicks > 0 && bestPoh.teleport != null
			&& bestPoh.penalized <= best.penalized + pohBiasTicks)
		{
			return bestPoh;
		}
		return best;
	}

	/**
	 * Shortest-path state from one source over the deduplicated node graph:
	 * nodes are distinct teleport destinations, dist is penalized, realDist
	 * tracks plain ticks along the same tree, arriveEdge/prevNode rebuild the
	 * hop chain. Target-independent, so one search serves a whole cost-matrix
	 * row.
	 */
	private static final class ChainPaths
	{
		WorldPoint[] nodePoint;
		double[] dist;
		double[] realDist;
		int[] arriveEdge;
		int[] prevNode;
	}

	private static ChainPaths chainFrom(WorldPoint from, PlanContext ctx)
	{
		ChainPaths cached = ctx.chainCache.get(from);
		if (cached != null)
		{
			return cached;
		}
		List<Teleport> teleports = ctx.teleports;
		java.util.Map<WorldPoint, Integer> nodeIds = new java.util.HashMap<>();
		for (Teleport t : teleports)
		{
			nodeIds.putIfAbsent(t.destination(), nodeIds.size());
		}
		int v = nodeIds.size();
		ChainPaths paths = new ChainPaths();
		paths.nodePoint = new WorldPoint[v + 1];
		nodeIds.forEach((p, id) -> paths.nodePoint[id] = p);
		paths.nodePoint[v] = from; // start node
		paths.dist = new double[v + 1];
		paths.realDist = new double[v + 1];
		paths.arriveEdge = new int[v + 1];
		paths.prevNode = new int[v + 1];
		Arrays.fill(paths.dist, IMPOSSIBLE);
		Arrays.fill(paths.arriveEdge, -1);
		paths.dist[v] = 0;
		paths.realDist[v] = 0;
		boolean[] done = new boolean[v + 1];

		// O(V² + V·E) with V = distinct destinations: fairy rings et al expand
		// to thousands of edges but only ~hundreds of points.
		for (int iter = 0; iter <= v; iter++)
		{
			int u = -1;
			for (int i = 0; i <= v; i++)
			{
				if (!done[i] && paths.dist[i] < IMPOSSIBLE && (u == -1 || paths.dist[i] < paths.dist[u]))
				{
					u = i;
				}
			}
			if (u == -1)
			{
				break;
			}
			done[u] = true;
			WorldPoint at = paths.nodePoint[u];
			for (int j = 0; j < teleports.size(); j++)
			{
				Teleport t = teleports.get(j);
				int target = nodeIds.get(t.destination());
				if (done[target])
				{
					continue;
				}
				double walkIn = t.origin() == null ? 0 : walkTicks(at, t.origin());
				double edgeReal = t.durationTicks() + walkIn;
				double edge = edgeReal + ctx.penalty[j];
				if (paths.dist[u] + edge < paths.dist[target])
				{
					paths.dist[target] = paths.dist[u] + edge;
					paths.realDist[target] = paths.realDist[u] + edgeReal;
					paths.arriveEdge[target] = j;
					paths.prevNode[target] = u;
				}
			}
		}
		ctx.chainCache.put(from, paths);
		return paths;
	}

	private static BestLeg chainedLeg(WorldPoint from, WorldPoint to, PlanContext ctx)
	{
		ChainPaths paths = chainFrom(from, ctx);
		BestLeg best = new BestLeg();
		int v = paths.nodePoint.length - 1;
		int bestNode = -1;
		for (int i = 0; i < v; i++)
		{
			if (paths.dist[i] >= IMPOSSIBLE)
			{
				continue;
			}
			double total = paths.dist[i] + walkTicks(paths.nodePoint[i], to);
			if (total < best.penalized)
			{
				best.penalized = total;
				bestNode = i;
			}
		}
		if (bestNode == -1)
		{
			return best;
		}
		List<Teleport> hops = new ArrayList<>();
		for (int node = bestNode; node != v; node = paths.prevNode[node])
		{
			hops.add(0, ctx.teleports.get(paths.arriveEdge[node]));
		}
		double finalWalk = walkTicks(paths.nodePoint[bestNode], to);
		best.cost = paths.realDist[bestNode] + finalWalk;
		// Composite duration includes inter-hop walks (consistent with house
		// chains); the final walk to the stop belongs to the leg estimate only.
		best.teleport = Teleport.chainOf(hops,
			(int) Math.min(Math.round(paths.realDist[bestNode]), Integer.MAX_VALUE));
		return best;
	}

	/**
	 * A teleport within this many ticks of plain walking still wins: teleporting
	 * is one click while running takes attention, so near-ties shouldn't nag the
	 * player to walk.
	 */
	static final double WALK_WINS_MARGIN_TICKS = 3;

	/**
	 * True when plain running from the player's live position to the leg's stop
	 * beats executing the leg's planned teleport from that same position. A
	 * pinned route prices leg N from stop N-1's tile, so a player who is already
	 * near the stop can be told to teleport away and walk back; guidance uses
	 * this per tick to hint "Walk" instead, without touching the pinned route.
	 * Also true when the teleport's boarding origin is out of walking range from
	 * here while the stop itself is walkable (the player left the planned path
	 * behind).
	 */
	public static boolean walkBeatsTeleport(WorldPoint player, Leg leg)
	{
		if (player == null || leg == null || leg.teleport() == null)
		{
			return false;
		}
		double walk = walkTicks(player, leg.stop().point());
		if (walk >= IMPOSSIBLE)
		{
			return false;
		}
		Teleport t = leg.teleport();
		double teleport = t.durationTicks() + walkTicks(t.destination(), leg.stop().point());
		if (t.origin() != null)
		{
			teleport += walkTicks(player, t.origin());
		}
		return walk + WALK_WINS_MARGIN_TICKS < teleport;
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
