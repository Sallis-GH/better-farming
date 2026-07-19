package com.betterfarming.travel;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

/**
 * One travel edge. `origin == null` means usable from anywhere (spells, items);
 * otherwise the player must first walk to the origin tile (network nodes,
 * POH portals, ship docks). All requirement collections must be satisfied
 * together.
 */
@Value
@Accessors(fluent = true)
@AllArgsConstructor
public class Teleport
{
	TeleportType type;
	WorldPoint origin;
	WorldPoint destination;
	int durationTicks;
	String displayInfo;
	Map<Skill, Integer> skillLevels;
	Set<Quest> quests;
	Set<VarCheck> varChecks;
	List<TeleportItemRequirement> items;
	boolean consumable;

	/** Raw "menuOption menuTarget objectID" column; classifies jewellery box tiers. */
	String objectInfo;

	/** True for synthesized house-chain edges (house teleport → POH facility). */
	boolean viaPoh;

	/**
	 * True for edges usable at most once per run: the free home teleport spell
	 * has a 30-minute cooldown, so a route may lean on it for one leg only.
	 */
	boolean oncePerRun;

	/**
	 * The individual hops of a chainOf() composite, in travel order — null for
	 * plain edges. Guidance walks the player through these one waypoint at a
	 * time (cast the item, walk to the gangplank, board, …).
	 */
	List<Teleport> chainHops;

	/** Pre-chainHops signature; hops default to null. */
	public Teleport(TeleportType type, WorldPoint origin, WorldPoint destination,
		int durationTicks, String displayInfo, Map<Skill, Integer> skillLevels,
		Set<Quest> quests, Set<VarCheck> varChecks, List<TeleportItemRequirement> items,
		boolean consumable, String objectInfo, boolean viaPoh, boolean oncePerRun)
	{
		this(type, origin, destination, durationTicks, displayInfo, skillLevels,
			quests, varChecks, items, consumable, objectInfo, viaPoh, oncePerRun, null);
	}

	/** Pre-oncePerRun signature; the flag defaults to false. */
	public Teleport(TeleportType type, WorldPoint origin, WorldPoint destination,
		int durationTicks, String displayInfo, Map<Skill, Integer> skillLevels,
		Set<Quest> quests, Set<VarCheck> varChecks, List<TeleportItemRequirement> items,
		boolean consumable, String objectInfo, boolean viaPoh)
	{
		this(type, origin, destination, durationTicks, displayInfo, skillLevels,
			quests, varChecks, items, consumable, objectInfo, viaPoh, false, null);
	}

	/** As {@link #chainOf(List, int)} with durations summed hop-only. */
	public static Teleport chainOf(List<Teleport> hops)
	{
		int duration = 0;
		for (Teleport hop : hops)
		{
			duration += hop.durationTicks();
		}
		return chainOf(hops, duration);
	}

	/**
	 * Collapses a multi-hop travel path (e.g. Ectophial → ship → ship) into a
	 * single edge so legs, run items, and hints handle chains unchanged:
	 * requirements union (identical item needs merged — two 2500-coin fares
	 * become one 5000-coin requirement occupying one stackable slot), display
	 * joins the hop labels.
	 *
	 * @param durationTicks total travel time including inter-hop walking —
	 *     the planner supplies it; hop durations alone understate the chain.
	 */
	public static Teleport chainOf(List<Teleport> hops, int durationTicks)
	{
		if (hops.size() == 1)
		{
			return hops.get(0);
		}
		Map<Skill, Integer> skills = new EnumMap<>(Skill.class);
		Set<Quest> quests = new HashSet<>();
		Set<VarCheck> varChecks = new LinkedHashSet<>();
		List<TeleportItemRequirement> items = new ArrayList<>();
		StringBuilder display = new StringBuilder();
		boolean consumable = false;
		boolean viaPoh = false;
		boolean oncePerRun = false;
		for (Teleport hop : hops)
		{
			hop.skillLevels().forEach((s, lvl) -> skills.merge(s, lvl, Math::max));
			quests.addAll(hop.quests());
			varChecks.addAll(hop.varChecks());
			for (TeleportItemRequirement req : hop.items())
			{
				mergeRequirement(items, req);
			}
			consumable |= hop.consumable();
			viaPoh |= hop.viaPoh();
			oncePerRun |= hop.oncePerRun();
			if (display.length() > 0)
			{
				display.append(" → ");
			}
			display.append(hop.displayLabel());
		}
		return new Teleport(hops.get(0).type(), hops.get(0).origin(),
			hops.get(hops.size() - 1).destination(), durationTicks, display.toString(),
			skills, quests, varChecks, items, consumable, null, viaPoh, oncePerRun,
			List.copyOf(hops));
	}

	private static void mergeRequirement(List<TeleportItemRequirement> items,
		TeleportItemRequirement req)
	{
		for (int i = 0; i < items.size(); i++)
		{
			TeleportItemRequirement existing = items.get(i);
			if (java.util.Arrays.equals(existing.itemIds(), req.itemIds())
				&& java.util.Arrays.equals(existing.staffIds(), req.staffIds())
				&& java.util.Arrays.equals(existing.offhandIds(), req.offhandIds()))
			{
				items.set(i, new TeleportItemRequirement(existing.itemIds(),
					existing.staffIds(), existing.offhandIds(),
					existing.quantity() + req.quantity(), existing.name()));
				return;
			}
		}
		items.add(req);
	}

	/**
	 * Human-readable label: the TSV display info when present, otherwise the
	 * humanized type name. Single source for the sidebar and guidance hints.
	 */
	public String displayLabel()
	{
		if (displayInfo != null)
		{
			return displayInfo;
		}
		StringBuilder sb = new StringBuilder();
		for (String word : type.name().toLowerCase().split("_"))
		{
			if (sb.length() > 0)
			{
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return sb.toString();
	}

	public boolean originInPoh()
	{
		return isInPoh(origin);
	}

	public boolean destinationInPoh()
	{
		return isInPoh(destination);
	}

	static boolean isInPoh(WorldPoint p)
	{
		// POH instances live in the 1856-2047 × 5632-5951 map block.
		return p != null && p.getX() >= 1856 && p.getX() < 2048
			&& p.getY() >= 5632 && p.getY() < 5952;
	}
}
