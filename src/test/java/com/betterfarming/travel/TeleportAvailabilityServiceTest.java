package com.betterfarming.travel;

import com.betterfarming.BetterFarmingConfig;
import com.betterfarming.item.ItemTracker;
import com.betterfarming.item.TrackerTestAccess;
import com.betterfarming.testsupport.FakeClient;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TeleportAvailabilityServiceTest
{
	private static final int LAW_RUNE = 563;

	private FakeClient client;
	private ItemTracker tracker;
	private BetterFarmingConfig config;

	@Before
	public void setUp()
	{
		client = new FakeClient();
		tracker = new ItemTracker();
		config = new BetterFarmingConfig() {};
	}

	private TeleportAvailabilityService service(Teleport... teleports)
	{
		return new TeleportAvailabilityService(List.of(teleports), client, tracker, config);
	}

	private static Teleport spell(String name, int magicLevel, TeleportItemRequirement... items)
	{
		return new Teleport(TeleportType.SPELL, null, new WorldPoint(3213, 3424, 0), 4,
			name, Map.of(Skill.MAGIC, magicLevel), Set.of(), Set.of(), List.of(items), false,
			null, false);
	}

	@Test
	public void loggedOut_nothingAvailable()
	{
		client.setGameState(GameState.LOGIN_SCREEN);
		TeleportAvailabilityService s = service(spell("Varrock Teleport", 1));
		s.refresh();
		assertTrue(s.available().isEmpty());
	}

	@Test
	public void boostedSkillLevel_counts()
	{
		client.setLevel(Skill.MAGIC, 20);
		client.setBoostedLevel(Skill.MAGIC, 25); // wizard's mind bomb etc.
		TeleportAvailabilityService s = service(spell("Varrock Teleport", 25));
		s.refresh();
		assertEquals(1, s.available().size());
	}

	@Test
	public void missingItems_excludes_bankItems_includeWhenConfigured()
	{
		client.setLevel(Skill.MAGIC, 99);
		TeleportItemRequirement laws = new TeleportItemRequirement(
			new int[]{LAW_RUNE}, new int[0], new int[0], 1, "Law rune");
		TeleportAvailabilityService s = service(spell("Varrock Teleport", 25, laws));

		s.refresh();
		assertTrue("no law runes anywhere", s.available().isEmpty());

		TrackerTestAccess.update(tracker, TrackerTestAccess.BANK, new Item[]{new Item(LAW_RUNE, 100)});
		s.refresh();
		assertEquals("banked runes count by default", 1, s.available().size());
	}

	@Test
	public void staffSubstitutesRunes_regardlessOfQuantity()
	{
		client.setLevel(Skill.MAGIC, 99);
		int staffOfAir = 1381;
		TeleportItemRequirement airRunes = new TeleportItemRequirement(
			new int[]{556}, new int[]{staffOfAir}, new int[0], 3, "Air rune");
		TeleportAvailabilityService s = service(spell("Varrock Teleport", 25, airRunes));

		s.refresh();
		assertTrue(s.available().isEmpty());

		TrackerTestAccess.update(tracker, TrackerTestAccess.EQUIPMENT, new Item[]{new Item(staffOfAir, 1)});
		s.refresh();
		assertEquals(1, s.available().size());
	}

	@Test
	public void varbitGate_respected()
	{
		client.setLevel(Skill.MAGIC, 99);
		Teleport ancientVarrock = new Teleport(TeleportType.SPELL, null,
			new WorldPoint(3213, 3424, 0), 4, "Varrock Teleport",
			Map.of(), Set.of(),
			Set.of(new VarCheck(VarCheck.VarType.VARBIT, 4070, 0, VarCheck.Op.EQUAL)),
			Collections.emptyList(), false, null, false);
		TeleportAvailabilityService s = service(ancientVarrock);

		client.setVarbit(4070, 1); // on Ancients — standard spells unavailable
		s.refresh();
		assertTrue(s.available().isEmpty());

		client.setVarbit(4070, 0);
		s.refresh();
		assertEquals(1, s.available().size());
	}

	@Test
	public void fairyRings_needFairytale2Progress_andStaff()
	{
		Teleport ring = new Teleport(TeleportType.FAIRY_RING,
			new WorldPoint(2412, 4434, 0), new WorldPoint(3204, 3169, 0), 5,
			"Fairy ring", Map.of(), Set.of(), Set.of(), Collections.emptyList(), false,
			null, false);
		TeleportAvailabilityService s = service(ring);

		s.refresh();
		assertTrue("Fairytale II not started", s.available().isEmpty());

		client.setVarbit(VarbitID.FAIRY2_QUEENCURE_QUEST, 40);
		s.refresh();
		assertTrue("no dramen staff", s.available().isEmpty());

		client.setVarbit(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE, 1);
		s.refresh();
		assertEquals("Lumbridge elite waives the staff", 1, s.available().size());
	}

	@Test
	public void spiritTrees_needTreeGnomeVillage()
	{
		Teleport tree = new Teleport(TeleportType.SPIRIT_TREE,
			new WorldPoint(2542, 3170, 0), new WorldPoint(2461, 3444, 0), 6,
			"Spirit tree", Map.of(), Set.of(), Set.of(), Collections.emptyList(), false,
			null, false);
		TeleportAvailabilityService s = service(tree);

		s.refresh();
		assertTrue(s.available().isEmpty());

		client.setQuestState(Quest.TREE_GNOME_VILLAGE, QuestState.FINISHED);
		s.refresh();
		assertEquals(1, s.available().size());
	}

	private static Teleport houseTeleport()
	{
		// Teleport to House spell landing inside the POH instance block.
		return new Teleport(TeleportType.SPELL, null, new WorldPoint(1923, 5709, 0), 4,
			"Teleport to House", Map.of(), Set.of(), Set.of(), Collections.emptyList(), false,
			null, false);
	}

	private static Teleport ardougnePortal()
	{
		return new Teleport(TeleportType.POH_PORTAL,
			new WorldPoint(1928, 5731, 0), new WorldPoint(2662, 3305, 0), 1,
			"Ardougne Portal", Map.of(), Set.of(), Set.of(), Collections.emptyList(), false,
			"Enter Ardougne Portal 13619", false);
	}

	@Test
	public void pohPortal_disabledByDefault_andHouseEntryAloneIsUseless()
	{
		TeleportAvailabilityService s = service(houseTeleport(), ardougnePortal());
		s.refresh();
		assertTrue("no portal ticked, and a bare house teleport reaches no patch",
			s.available().isEmpty());
	}

	@Test
	public void enabledPortal_composesWithHouseEntry_intoViaPohEdge()
	{
		config = new BetterFarmingConfig()
		{
			@Override
			public boolean pohPortalArdougne()
			{
				return true;
			}
		};
		TeleportAvailabilityService s = service(houseTeleport(), ardougnePortal());
		s.refresh();

		assertEquals(1, s.available().size());
		Teleport chain = s.available().get(0);
		assertTrue(chain.viaPoh());
		assertEquals("outside-world destination", 2662, chain.destination().getX());
		assertEquals("Teleport to House → Ardougne Portal", chain.displayInfo());
		assertTrue("cost combines both hops + walk", chain.durationTicks() > 4 + 1);
	}

	@Test
	public void enabledPortal_withoutAnyHouseEntry_staysUnavailable()
	{
		config = new BetterFarmingConfig()
		{
			@Override
			public boolean pohPortalArdougne()
			{
				return true;
			}
		};
		TeleportAvailabilityService s = service(ardougnePortal());
		s.refresh();
		assertTrue("no way into the house — portal unreachable", s.available().isEmpty());
	}

	@Test
	public void jewelleryBox_gatedByTier()
	{
		Teleport fancyRow = new Teleport(TeleportType.JEWELLERY_BOX,
			new WorldPoint(1960, 5750, 0), new WorldPoint(2611, 3390, 0), 4,
			"E: Fishing Guild", Map.of(), Set.of(), Set.of(), Collections.emptyList(), false,
			"Teleport Menu Fancy Jewellery Box 37501", false);

		config = new BetterFarmingConfig()
		{
			@Override
			public com.betterfarming.JewelleryBoxTier pohJewelleryBox()
			{
				return com.betterfarming.JewelleryBoxTier.BASIC;
			}
		};
		TeleportAvailabilityService basic = service(houseTeleport(), fancyRow);
		basic.refresh();
		assertTrue("basic box lacks fancy teleports", basic.available().isEmpty());

		config = new BetterFarmingConfig()
		{
			@Override
			public com.betterfarming.JewelleryBoxTier pohJewelleryBox()
			{
				return com.betterfarming.JewelleryBoxTier.ORNATE;
			}
		};
		TeleportAvailabilityService ornate = service(houseTeleport(), fancyRow);
		ornate.refresh();
		assertEquals("ornate covers fancy", 1, ornate.available().size());
		assertTrue(ornate.available().get(0).viaPoh());
	}

	@Test
	public void homeTeleportOnCooldownIsUnavailable()
	{
		Teleport homeSpell = new Teleport(TeleportType.HOME_SPELL, null,
			new WorldPoint(3222, 3218, 0), 17, "Lumbridge Home Teleport",
			Map.of(), Set.of(), Set.of(), List.of(), false, null, false, true);

		// Just cast: VarPlayer.LAST_HOME_TELEPORT holds minutes-since-epoch.
		client.setVarp(net.runelite.api.VarPlayer.LAST_HOME_TELEPORT,
			(int) (System.currentTimeMillis() / 60_000L));
		TeleportAvailabilityService cooling = service(homeSpell);
		cooling.refresh();
		assertTrue("cooldown running: not plannable", cooling.available().isEmpty());

		// Cast over half an hour ago: available again.
		client.setVarp(net.runelite.api.VarPlayer.LAST_HOME_TELEPORT,
			(int) (System.currentTimeMillis() / 60_000L) - 31);
		TeleportAvailabilityService ready = service(homeSpell);
		ready.refresh();
		assertEquals(1, ready.available().size());
	}
}
