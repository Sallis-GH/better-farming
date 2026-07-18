package com.betterfarming.travel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
				BestLeg best = bestLeg(from, stops.get(j).point(), teleports);
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
		return legs;
	}

	// ── leg cost ──

	private static final class BestLeg
	{
		double cost = IMPOSSIBLE;
		Teleport teleport;
	}

	private static BestLeg bestLeg(WorldPoint from, WorldPoint to, List<Teleport> teleports)
	{
		BestLeg best = new BestLeg();
		best.cost = walkTicks(from, to);
		for (Teleport t : teleports)
		{
			double c = t.durationTicks() + walkTicks(t.destination(), to);
			if (t.origin() != null)
			{
				c += walkTicks(from, t.origin());
			}
			if (c < best.cost)
			{
				best.cost = c;
				best.teleport = t;
			}
		}
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
