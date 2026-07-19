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

	/** Pre-oncePerRun signature; the flag defaults to false. */
	public Teleport(TeleportType type, WorldPoint origin, WorldPoint destination,
		int durationTicks, String displayInfo, Map<Skill, Integer> skillLevels,
		Set<Quest> quests, Set<VarCheck> varChecks, List<TeleportItemRequirement> items,
		boolean consumable, String objectInfo, boolean viaPoh)
	{
		this(type, origin, destination, durationTicks, displayInfo, skillLevels,
			quests, varChecks, items, consumable, objectInfo, viaPoh, false);
	}

	/**
	 * Collapses a multi-hop travel path (e.g. Ectophial → ship → ship) into a
	 * single edge so legs, run items, and hints handle chains unchanged:
	 * requirements union, durations sum, display joins the hop labels.
	 */
	public static Teleport chainOf(List<Teleport> hops)
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
		int duration = 0;
		boolean consumable = false;
		boolean viaPoh = false;
		boolean oncePerRun = false;
		for (Teleport hop : hops)
		{
			hop.skillLevels().forEach((s, lvl) -> skills.merge(s, lvl, Math::max));
			quests.addAll(hop.quests());
			varChecks.addAll(hop.varChecks());
			items.addAll(hop.items());
			duration += hop.durationTicks();
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
			hops.get(hops.size() - 1).destination(), duration, display.toString(),
			skills, quests, varChecks, items, consumable, null, viaPoh, oncePerRun);
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
