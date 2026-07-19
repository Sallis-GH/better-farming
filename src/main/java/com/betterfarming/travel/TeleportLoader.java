package com.betterfarming.travel;

// TSV format and parsing rules adapted from Skretzo/shortest-path (BSD-2-Clause)
// transport.parser.*, see resources/transports/LICENSE-shortest-path

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.inject.Singleton;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

/**
 * Loads the vendored transport TSVs (resources/transports/, data from the
 * shortest-path plugin) into Teleport edges.
 *
 * Row shapes:
 *  - Destination only → usable from anywhere (spells, teleport items).
 *  - Origin + Destination → fixed edge (POH portals, quetzal whistle pairs).
 *  - Origin only, in a network-type file → node; every node pair in the file
 *    becomes an edge with the union of both nodes' requirements.
 */
@Singleton
@Slf4j
public class TeleportLoader
{
	private static final String COL_ORIGIN = "Origin";
	private static final String COL_DESTINATION = "Destination";
	private static final String COL_SKILLS = "Skills";
	private static final String COL_ITEMS = "Items";
	private static final String COL_QUESTS = "Quests";
	private static final String COL_DURATION = "Duration";
	private static final String COL_DISPLAY_INFO = "Display info";
	private static final String COL_CONSUMABLE = "Consumable";
	private static final String COL_VARBITS = "Varbits";
	private static final String COL_VARPLAYERS = "VarPlayers";
	private static final String COL_OBJECT_INFO = "menuOption menuTarget objectID";

	private static final Map<String, Quest> QUESTS_BY_NAME = new HashMap<>();

	static
	{
		for (Quest q : Quest.values())
		{
			QUESTS_BY_NAME.put(q.getName(), q);
		}
	}

	public List<Teleport> loadAll() throws IOException
	{
		List<Teleport> out = new ArrayList<>();
		for (TeleportType type : TeleportType.values())
		{
			out.addAll(load(type));
		}
		log.info("Better Farming: loaded {} teleport edges", out.size());
		return out;
	}

	List<Teleport> load(TeleportType type) throws IOException
	{
		String resource = "/transports/" + type.getResourceFile();
		try (InputStream in = TeleportLoader.class.getResourceAsStream(resource))
		{
			if (in == null)
			{
				throw new IOException("Missing bundled resource " + resource);
			}
			return parse(type, new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
	}

	List<Teleport> parse(TeleportType type, String contents)
	{
		List<Teleport> edges = new ArrayList<>();
		List<Row> entries = new ArrayList<>();
		List<Row> exits = new ArrayList<>();

		String[] lines = contents.split("\n");
		if (lines.length == 0)
		{
			return edges;
		}
		String[] headers = parseHeader(lines[0]);

		for (int i = 1; i < lines.length; i++)
		{
			String line = lines[i].stripTrailing();
			if (line.isBlank() || line.startsWith("#"))
			{
				continue;
			}
			Row row = parseRow(headers, line);
			if (row == null)
			{
				continue;
			}
			if (row.origin() != null && row.destination() != null)
			{
				edges.add(toTeleport(type, row.origin(), row.destination(), row, row));
			}
			else if (row.origin() == null && row.destination() != null)
			{
				if (type.isNetwork())
				{
					exits.add(row);
				}
				else
				{
					edges.add(toTeleport(type, null, row.destination(), row, row));
				}
			}
			else if (row.origin() != null && type.isNetwork())
			{
				entries.add(row);
			}
			// origin-only row in a non-network file: nothing to do with it.
		}

		// Network files list boarding tiles as origin-only rows and reachable
		// destinations as destination-only rows (with the unlock requirements
		// on the destination). Any entry can travel to any exit.
		for (Row entry : dedupeSites(entries))
		{
			for (Row exit : exits)
			{
				if (sameSite(entry.origin(), exit.destination()))
				{
					continue;
				}
				edges.add(toTeleport(type, entry.origin(), exit.destination(), entry, exit));
			}
		}
		return edges;
	}

	private static boolean sameSite(WorldPoint a, WorldPoint b)
	{
		return a.getPlane() == b.getPlane()
			&& Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY())) <= 10;
	}

	/**
	 * Entry rows repeat per interactable tile (a spirit tree has ~14 adjacent
	 * tiles). Collapse rows within 10 tiles of a kept row into one site so the
	 * edge set stays site×exit, not tile×exit.
	 */
	private static List<Row> dedupeSites(List<Row> nodes)
	{
		List<Row> sites = new ArrayList<>();
		for (Row node : nodes)
		{
			boolean duplicate = false;
			for (Row kept : sites)
			{
				if (sameSite(kept.origin(), node.origin()))
				{
					duplicate = true;
					break;
				}
			}
			if (!duplicate)
			{
				sites.add(node);
			}
		}
		return sites;
	}

	// ── row parsing ──

	@Value
	@Accessors(fluent = true)
	private static class Row
	{
		WorldPoint origin;
		WorldPoint destination;
		Map<Skill, Integer> skills;
		Set<Quest> quests;
		Set<VarCheck> varChecks;
		List<TeleportItemRequirement> items;
		int duration;
		String displayInfo;
		boolean consumable;
		String objectInfo;
	}

	private static String[] parseHeader(String headerLine)
	{
		String normalized = headerLine;
		if (normalized.startsWith("# "))
		{
			normalized = normalized.substring(2);
		}
		else if (normalized.startsWith("#"))
		{
			normalized = normalized.substring(1);
		}
		return normalized.stripTrailing().split("\t");
	}

	private Row parseRow(String[] headers, String line)
	{
		String[] fields = line.split("\t", -1);
		Map<String, String> byName = new HashMap<>();
		for (int i = 0; i < headers.length && i < fields.length; i++)
		{
			byName.put(headers[i], fields[i]);
		}
		try
		{
			Set<VarCheck> varChecks = new LinkedHashSet<>();
			varChecks.addAll(parseVarChecks(VarCheck.VarType.VARBIT, byName.get(COL_VARBITS)));
			varChecks.addAll(parseVarChecks(VarCheck.VarType.VARPLAYER, byName.get(COL_VARPLAYERS)));
			return new Row(
				parsePoint(byName.get(COL_ORIGIN)),
				parsePoint(byName.get(COL_DESTINATION)),
				parseSkills(byName.get(COL_SKILLS)),
				parseQuests(byName.get(COL_QUESTS)),
				varChecks,
				parseItems(byName.get(COL_ITEMS)),
				parseDuration(byName.get(COL_DURATION)),
				emptyToNull(byName.get(COL_DISPLAY_INFO)),
				"T".equalsIgnoreCase(byName.get(COL_CONSUMABLE)),
				emptyToNull(byName.get(COL_OBJECT_INFO)));
		}
		catch (RuntimeException ex)
		{
			log.warn("Better Farming: skipping unparseable transport row '{}'", line, ex);
			return null;
		}
	}

	private static WorldPoint parsePoint(String value)
	{
		if (value == null || value.isBlank())
		{
			return null;
		}
		String[] parts = value.trim().split(" ");
		return new WorldPoint(
			Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
	}

	private static Map<Skill, Integer> parseSkills(String value)
	{
		Map<Skill, Integer> out = new EnumMap<>(Skill.class);
		if (value == null || value.isEmpty())
		{
			return out;
		}
		for (String requirement : value.split(";"))
		{
			if (requirement.isEmpty())
			{
				continue;
			}
			String[] levelAndSkill = requirement.split(" ", 2);
			for (Skill s : Skill.values())
			{
				if (s.getName().equals(levelAndSkill[1]))
				{
					out.put(s, Integer.parseInt(levelAndSkill[0]));
				}
			}
			// "Total level"/"Combat level"/"Quest points" never gate teleports we load.
		}
		return out;
	}

	private static Set<Quest> parseQuests(String value)
	{
		Set<Quest> out = new HashSet<>();
		if (value == null || value.isEmpty())
		{
			return out;
		}
		for (String name : value.split(";"))
		{
			Quest quest = QUESTS_BY_NAME.get(name);
			if (quest != null)
			{
				out.add(quest);
			}
			else if (!name.isBlank())
			{
				// Warn, not debug: an unresolvable quest gate silently makes
				// the transport unconditionally available (upstream TSV
				// resyncs can reference quests newer than the pinned API).
				log.warn("Better Farming: unknown quest name '{}' in transport data", name);
			}
		}
		return out;
	}

	private static Set<VarCheck> parseVarChecks(VarCheck.VarType varType, String value)
	{
		Set<VarCheck> out = new LinkedHashSet<>();
		if (value == null || value.isEmpty())
		{
			return out;
		}
		for (String requirement : value.split(";"))
		{
			if (requirement.isEmpty())
			{
				continue;
			}
			VarCheck parsed = null;
			for (VarCheck.Op op : VarCheck.Op.values())
			{
				String[] parts = requirement.split(Pattern.quote(op.code()));
				if (parts.length == 2)
				{
					parsed = new VarCheck(varType,
						Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), op);
					break;
				}
			}
			if (parsed == null)
			{
				throw new IllegalArgumentException("Bad var requirement: " + requirement);
			}
			out.add(parsed);
		}
		return out;
	}

	/**
	 * Item grammar: AND-terms joined by '&&', each term OR-alternatives joined
	 * by '||'. Tokens are ItemVariations names or raw item ids, '=' quantity.
	 */
	private static List<TeleportItemRequirement> parseItems(String value)
	{
		List<TeleportItemRequirement> out = new ArrayList<>();
		if (value == null || value.isEmpty())
		{
			return out;
		}
		String normalized = value.replace(" ", "")
			.replace("&&", "&").replace("||", "|").toUpperCase();
		for (String andPart : normalized.split("&"))
		{
			List<int[]> ids = new ArrayList<>();
			List<int[]> staves = new ArrayList<>();
			List<int[]> offhands = new ArrayList<>();
			int maxQuantity = -1;
			String name = null;
			for (String orPart : andPart.split(Pattern.quote("|")))
			{
				String[] itemAndQuantity = orPart.split("=");
				if (itemAndQuantity.length != 2)
				{
					throw new IllegalArgumentException("Bad item requirement: " + andPart);
				}
				maxQuantity = Math.max(maxQuantity, Integer.parseInt(itemAndQuantity[1]));
				if (name == null)
				{
					name = prettifyItemToken(itemAndQuantity[0]);
				}
				ItemVariations variation = ItemVariations.fromName(itemAndQuantity[0]);
				if (variation != null)
				{
					ids.add(variation.getIds());
					// staves()/offhands() return null for variations with no substitute.
					int[] staffIds = ItemVariations.staves(variation);
					int[] offhandIds = ItemVariations.offhands(variation);
					staves.add(staffIds == null ? new int[0] : staffIds);
					offhands.add(offhandIds == null ? new int[0] : offhandIds);
				}
				else
				{
					ids.add(new int[]{Integer.parseInt(itemAndQuantity[0])});
					staves.add(new int[0]);
					offhands.add(new int[0]);
				}
			}
			out.add(new TeleportItemRequirement(
				concat(ids), concat(staves), concat(offhands), maxQuantity, name));
		}
		return out;
	}

	/** "AIR_RUNE" → "Air rune"; a raw numeric id → "Item 563". */
	private static String prettifyItemToken(String token)
	{
		if (token.chars().allMatch(Character::isDigit))
		{
			return "Item " + token;
		}
		String words = token.toLowerCase().replace('_', ' ');
		return Character.toUpperCase(words.charAt(0)) + words.substring(1);
	}

	private static int parseDuration(String value)
	{
		return value == null || value.isBlank() ? 1 : Integer.parseInt(value.trim());
	}

	private static String emptyToNull(String value)
	{
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static int[] concat(List<int[]> arrays)
	{
		int total = 0;
		for (int[] a : arrays)
		{
			total += a.length;
		}
		int[] out = new int[total];
		int pos = 0;
		for (int[] a : arrays)
		{
			System.arraycopy(a, 0, out, pos, a.length);
			pos += a.length;
		}
		return out;
	}

	private static Teleport toTeleport(TeleportType type, WorldPoint origin, WorldPoint dest,
		Row from, Row to)
	{
		if (from == to)
		{
			return new Teleport(type, origin, dest, from.duration(),
				from.displayInfo(), from.skills(), from.quests(), from.varChecks(),
				from.items(), from.consumable(), from.objectInfo(), false,
				// The free home teleport has a 30-minute cooldown.
				type == TeleportType.HOME_SPELL);
		}
		// Network edge: union both nodes' requirements; the traveller must be
		// allowed to use the origin node and the destination node.
		Map<Skill, Integer> skills = new EnumMap<>(Skill.class);
		skills.putAll(from.skills());
		for (Map.Entry<Skill, Integer> e : to.skills().entrySet())
		{
			skills.merge(e.getKey(), e.getValue(), Math::max);
		}
		Set<Quest> quests = new HashSet<>(from.quests());
		quests.addAll(to.quests());
		Set<VarCheck> varChecks = new LinkedHashSet<>(from.varChecks());
		varChecks.addAll(to.varChecks());
		List<TeleportItemRequirement> items = new ArrayList<>(from.items());
		items.addAll(to.items());
		int duration = Math.max(from.duration(), to.duration());
		String display = to.displayInfo() != null ? to.displayInfo() : from.displayInfo();
		return new Teleport(type, origin, dest, Math.max(duration, 1),
			display, skills, quests, varChecks, items, from.consumable() || to.consumable(),
			from.objectInfo(), false);
	}
}
