package com.betterfarming.travel;

import java.io.IOException;
import java.util.List;
import net.runelite.api.Skill;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TeleportLoaderTest
{
	private final TeleportLoader loader = new TeleportLoader();

	@Test
	public void allBundledFilesLoadWithoutErrors() throws IOException
	{
		List<Teleport> all = loader.loadAll();
		assertTrue("expect a substantial teleport graph, got " + all.size(),
			all.size() > 300);
	}

	@Test
	public void spellRow_parsesRunesSkillAndVarbit() throws IOException
	{
		List<Teleport> spells = loader.load(TeleportType.SPELL);
		Teleport varrock = spells.stream()
			.filter(t -> "Varrock Teleport".equals(t.displayInfo()))
			.findFirst().orElseThrow();

		assertNull("spells are usable from anywhere", varrock.origin());
		assertEquals(3213, varrock.destination().getX());
		assertEquals((Integer) 25, varrock.skillLevels().get(Skill.MAGIC));
		// 3 AND-terms: air runes, fire runes, law runes
		assertEquals(3, varrock.items().size());
		// Standard spellbook varbit 4070=0
		assertTrue(varrock.varChecks().stream()
			.anyMatch(v -> v.id() == 4070 && v.value() == 0 && v.op() == VarCheck.Op.EQUAL));
	}

	@Test
	public void itemRow_parsesOrVariationsAndConsumable() throws IOException
	{
		List<Teleport> items = loader.load(TeleportType.ITEM);
		// Ardougne cloak farm teleport: consumable variant exists (T flag)
		assertTrue(items.stream().anyMatch(Teleport::consumable));
		assertTrue(items.stream().allMatch(t -> t.origin() == null));
	}

	@Test
	public void networkFile_meshesSitesNotTiles() throws IOException
	{
		List<Teleport> spiritTrees = loader.load(TeleportType.SPIRIT_TREE);
		assertFalse(spiritTrees.isEmpty());
		// Every edge has both endpoints (mesh), none are anywhere-teleports.
		for (Teleport t : spiritTrees)
		{
			assertNotNull(t.origin());
			assertNotNull(t.destination());
			assertFalse("self-edges must not exist", t.origin().equals(t.destination()));
		}
		// The tile-per-row dedup must collapse hard: the raw file has hundreds
		// of rows; sites number ~10-20, so edges = sites*(sites-1) < 600.
		assertTrue("mesh too large: " + spiritTrees.size(), spiritTrees.size() < 600);
	}

	@Test
	public void syntheticNetwork_pairsEntriesWithExits_unioningRequirements()
	{
		// Network files: origin-only rows are boarding points, destination-only
		// rows are reachable exits carrying the unlock requirements.
		String tsv = "# Origin\tDestination\tSkills\tQuests\tDuration\tDisplay info\n"
			+ "100 100 0\t\t\tDragon Slayer I\t3\tEntry A\n"
			+ "101 100 0\t\t\tDragon Slayer I\t3\tEntry A adjacent tile\n"
			+ "\t500 500 0\t\tMonkey Madness I\t3\tExit B\n"
			+ "\t102 100 0\t\t\t3\tExit at entry A (same site)\n";
		List<Teleport> edges = loader.parse(TeleportType.SPIRIT_TREE, tsv);

		assertEquals("adjacent tiles dedupe; same-site exit skipped", 1, edges.size());
		Teleport edge = edges.get(0);
		assertEquals(100, edge.origin().getX());
		assertEquals(500, edge.destination().getX());
		assertEquals("edge requires entry + exit quests", 2, edge.quests().size());
		assertEquals("Exit B", edge.displayInfo());
	}

	@Test
	public void syntheticBadRow_isSkippedNotFatal()
	{
		String tsv = "# Destination\tItems\tDuration\n"
			+ "not a coordinate\tAIR_RUNE=1\t4\n"
			+ "3000 3000 0\tAIR_RUNE=1\t4\n";
		List<Teleport> edges = loader.parse(TeleportType.SPELL, tsv);
		assertEquals(1, edges.size());
	}
}
