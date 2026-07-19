package com.betterfarming;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(BetterFarmingConfig.GROUP)
public interface BetterFarmingConfig extends Config
{
	String GROUP = "betterfarming";
	String PATCH_SELECTIONS_KEY = "patchSelections";

	@ConfigSection(
		name = "Guidance",
		description = "On-screen guidance along the planned run order",
		position = 5
	)
	String guidanceSection = "guidance";

	@ConfigSection(
		name = "Run items",
		description = "What counts as needed equipment for a run",
		position = 10
	)
	String runItemsSection = "runItems";

	@ConfigSection(
		name = "Player-owned house",
		description = "Which teleport facilities your house has — the plugin cannot detect house layout",
		position = 20
	)
	String pohSection = "poh";

	@ConfigSection(
		name = "POH portals",
		description = "Tick exactly the portals built in your house (portal chamber/nexus rooms)",
		position = 30,
		closedByDefault = true
	)
	String pohPortalsSection = "pohPortals";

	@ConfigItem(
		keyName = PATCH_SELECTIONS_KEY,
		name = "Patch selections (internal)",
		description = "Serialized patch selection state. Edit at your own risk.",
		hidden = true
	)
	default String patchSelections()
	{
		return "";
	}

	@ConfigItem(
		keyName = "teleportItemsFromBank",
		name = "Plan teleports with banked items",
		description = "Count banked runes/teleport items as usable when planning the run order.<br>"
			+ "Disable to only use teleports whose items you are carrying right now.",
		position = 1
	)
	default boolean teleportItemsFromBank()
	{
		return true;
	}

	// ── Guidance ──

	@ConfigItem(
		keyName = "showWorldArrow",
		name = "World arrow",
		description = "Arrow above the next patch in the game world; when the patch is out of<br>"
			+ "view, an arrow at the screen edge points in its direction.",
		section = guidanceSection,
		position = 1
	)
	default boolean showWorldArrow()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showMinimapArrow",
		name = "Minimap arrow",
		description = "Arrow on the minimap: above the next patch when close, orbiting the<br>"
			+ "player dot pointing toward it when far.",
		section = guidanceSection,
		position = 2
	)
	default boolean showMinimapArrow()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showWorldMapMarker",
		name = "World map marker",
		description = "Marker for the next patch on the world map (snaps to the map edge when<br>"
			+ "off-view; click to jump there).",
		section = guidanceSection,
		position = 3
	)
	default boolean showWorldMapMarker()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPlantingHighlights",
		name = "Planting highlights",
		description = "At the patch: outline the patch to work on and highlight the seed or<br>"
			+ "sapling (and rake) in your inventory; while travelling, highlight the<br>"
			+ "teleport item for the current leg (in the inventory and, for equipped<br>"
			+ "jewellery like a skills necklace, in the worn equipment tab).",
		section = guidanceSection,
		position = 4
	)
	default boolean showPlantingHighlights()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTravelHint",
		name = "Travel hint",
		description = "On-screen panel with the next stop and how to get there<br>"
			+ "(e.g. \"Cast Camelot Teleport\"). Right-click it to reset run progress.",
		section = guidanceSection,
		position = 5
	)
	default boolean showTravelHint()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useShortestPath",
		name = "Draw path via Shortest Path plugin",
		description = "If the Shortest Path plugin (Plugin Hub) is installed, drive it to draw<br>"
			+ "the exact tile path to the next patch. Does nothing when not installed.",
		section = guidanceSection,
		position = 6
	)
	default boolean useShortestPath()
	{
		return true;
	}

	// ── Run items ──

	@ConfigItem(
		keyName = "relyOnToolLeprechauns",
		name = "Rely on tool leprechauns",
		description = "Treat all farming tools as optional — every patch has a tool leprechaun<br>"
			+ "whose shared storage holds rake, spade, dibber, secateurs and compost.",
		section = runItemsSection,
		position = 1
	)
	default boolean relyOnToolLeprechauns()
	{
		return false;
	}

	// ── Player-owned house ──

	@ConfigItem(
		keyName = "preferPohTeleports",
		name = "Prefer POH teleports",
		description = "Bias route planning toward house teleports when they are close in travel<br>"
			+ "time — many players prefer one house tab over carrying rune sets.",
		section = pohSection,
		position = 1
	)
	default boolean preferPohTeleports()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohJewelleryBox",
		name = "Jewellery box",
		description = "Tier of the jewellery box in your house. Higher tiers include all<br>"
			+ "lower-tier teleports.",
		section = pohSection,
		position = 2
	)
	default JewelleryBoxTier pohJewelleryBox()
	{
		return JewelleryBoxTier.NONE;
	}

	@ConfigItem(
		keyName = "pohMountedGlory",
		name = "Mounted amulet of glory",
		description = "House has a mounted amulet of glory.",
		section = pohSection,
		position = 3
	)
	default boolean pohMountedGlory()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohMountedXerics",
		name = "Mounted Xeric's talisman",
		description = "House has a mounted Xeric's talisman.",
		section = pohSection,
		position = 4
	)
	default boolean pohMountedXerics()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohMountedDigsite",
		name = "Mounted digsite pendant",
		description = "House has a mounted digsite pendant.",
		section = pohSection,
		position = 5
	)
	default boolean pohMountedDigsite()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohMythicalCape",
		name = "Mythical cape mount",
		description = "House has a mounted mythical cape (Myths' Guild teleport).",
		section = pohSection,
		position = 6
	)
	default boolean pohMythicalCape()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohFairyRing",
		name = "Fairy ring",
		description = "House has a fairy ring (85 Construction, boostable).",
		section = pohSection,
		position = 7
	)
	default boolean pohFairyRing()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohSpiritTree",
		name = "Spirit tree",
		description = "House has a spirit tree (75 Construction, 83 Farming, boostable).",
		section = pohSection,
		position = 8
	)
	default boolean pohSpiritTree()
	{
		return false;
	}

	// ── POH portals (generated from transports/teleportation_portals_poh.tsv) ──

	@ConfigItem(
		keyName = "pohPortalAnnakarl",
		name = "Annakarl",
		description = "House has the Annakarl Portal.",
		section = pohPortalsSection,
		position = 1
	)
	default boolean pohPortalAnnakarl()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalApeAtollDungeon",
		name = "Ape Atoll Dungeon",
		description = "House has the Ape Atoll Dungeon Portal.",
		section = pohPortalsSection,
		position = 2
	)
	default boolean pohPortalApeAtollDungeon()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalArceuusLibrary",
		name = "Arceuus Library",
		description = "House has the Arceuus Library Portal.",
		section = pohPortalsSection,
		position = 3
	)
	default boolean pohPortalArceuusLibrary()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalArdougne",
		name = "Ardougne",
		description = "House has the Ardougne Portal.",
		section = pohPortalsSection,
		position = 4
	)
	default boolean pohPortalArdougne()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalBarbarianOutpost",
		name = "Barbarian Outpost",
		description = "House has the Barbarian Outpost Portal.",
		section = pohPortalsSection,
		position = 5
	)
	default boolean pohPortalBarbarianOutpost()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalBarrows",
		name = "Barrows",
		description = "House has the Barrows Portal.",
		section = pohPortalsSection,
		position = 6
	)
	default boolean pohPortalBarrows()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalBattlefront",
		name = "Battlefront",
		description = "House has the Battlefront Portal.",
		section = pohPortalsSection,
		position = 7
	)
	default boolean pohPortalBattlefront()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalCamelot",
		name = "Camelot",
		description = "House has the Camelot Portal.",
		section = pohPortalsSection,
		position = 8
	)
	default boolean pohPortalCamelot()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalCarrallanger",
		name = "Carrallanger",
		description = "House has the Carrallanger Portal.",
		section = pohPortalsSection,
		position = 9
	)
	default boolean pohPortalCarrallanger()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalCatherby",
		name = "Catherby",
		description = "House has the Catherby Portal.",
		section = pohPortalsSection,
		position = 10
	)
	default boolean pohPortalCatherby()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalCemetery",
		name = "Cemetery",
		description = "House has the Cemetery Portal.",
		section = pohPortalsSection,
		position = 11
	)
	default boolean pohPortalCemetery()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalCivitasIllaFortis",
		name = "Civitas illa Fortis",
		description = "House has the Civitas illa Fortis Portal.",
		section = pohPortalsSection,
		position = 12
	)
	default boolean pohPortalCivitasIllaFortis()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalDareeyak",
		name = "Dareeyak",
		description = "House has the Dareeyak Portal.",
		section = pohPortalsSection,
		position = 13
	)
	default boolean pohPortalDareeyak()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalDraynorManor",
		name = "Draynor Manor",
		description = "House has the Draynor Manor Portal.",
		section = pohPortalsSection,
		position = 14
	)
	default boolean pohPortalDraynorManor()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalFalador",
		name = "Falador",
		description = "House has the Falador Portal.",
		section = pohPortalsSection,
		position = 15
	)
	default boolean pohPortalFalador()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalFenkenstrainsCastle",
		name = "Fenkenstrain's Castle",
		description = "House has the Fenkenstrain's Castle Portal.",
		section = pohPortalsSection,
		position = 16
	)
	default boolean pohPortalFenkenstrainsCastle()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalFishingGuild",
		name = "Fishing Guild",
		description = "House has the Fishing Guild Portal.",
		section = pohPortalsSection,
		position = 17
	)
	default boolean pohPortalFishingGuild()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalGhorrock",
		name = "Ghorrock",
		description = "House has the Ghorrock Portal.",
		section = pohPortalsSection,
		position = 18
	)
	default boolean pohPortalGhorrock()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalGrandExchange",
		name = "Grand Exchange",
		description = "House has the Grand Exchange Portal.",
		section = pohPortalsSection,
		position = 19
	)
	default boolean pohPortalGrandExchange()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalHarmonyIsland",
		name = "Harmony Island",
		description = "House has the Harmony Island Portal.",
		section = pohPortalsSection,
		position = 20
	)
	default boolean pohPortalHarmonyIsland()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalIcePlateau",
		name = "Ice Plateau",
		description = "House has the Ice Plateau Portal.",
		section = pohPortalsSection,
		position = 21
	)
	default boolean pohPortalIcePlateau()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalKharyrll",
		name = "Kharyrll",
		description = "House has the Kharyrll Portal.",
		section = pohPortalsSection,
		position = 22
	)
	default boolean pohPortalKharyrll()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalKourend",
		name = "Kourend",
		description = "House has the Kourend Portal.",
		section = pohPortalsSection,
		position = 23
	)
	default boolean pohPortalKourend()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalLassar",
		name = "Lassar",
		description = "House has the Lassar Portal.",
		section = pohPortalsSection,
		position = 24
	)
	default boolean pohPortalLassar()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalLumbridge",
		name = "Lumbridge",
		description = "House has the Lumbridge Portal.",
		section = pohPortalsSection,
		position = 25
	)
	default boolean pohPortalLumbridge()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalLunarIsle",
		name = "Lunar Isle",
		description = "House has the Lunar Isle Portal.",
		section = pohPortalsSection,
		position = 26
	)
	default boolean pohPortalLunarIsle()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalMarim",
		name = "Marim",
		description = "House has the Marim Portal.",
		section = pohPortalsSection,
		position = 27
	)
	default boolean pohPortalMarim()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalMindAltar",
		name = "Mind Altar",
		description = "House has the Mind Altar Portal.",
		section = pohPortalsSection,
		position = 28
	)
	default boolean pohPortalMindAltar()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalOurania",
		name = "Ourania",
		description = "House has the Ourania Portal.",
		section = pohPortalsSection,
		position = 29
	)
	default boolean pohPortalOurania()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalPaddewwa",
		name = "Paddewwa",
		description = "House has the Paddewwa Portal.",
		section = pohPortalsSection,
		position = 30
	)
	default boolean pohPortalPaddewwa()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalPortKhazard",
		name = "Port Khazard",
		description = "House has the Port Khazard Portal.",
		section = pohPortalsSection,
		position = 31
	)
	default boolean pohPortalPortKhazard()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalRespawnCamelot",
		name = "Respawn (Camelot)",
		description = "House has the Respawn Portal (Camelot).",
		section = pohPortalsSection,
		position = 32
	)
	default boolean pohPortalRespawnCamelot()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalRespawnCivitasIllaFortis",
		name = "Respawn (Civitas illa Fortis)",
		description = "House has the Respawn Portal (Civitas illa Fortis).",
		section = pohPortalsSection,
		position = 33
	)
	default boolean pohPortalRespawnCivitasIllaFortis()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalRespawnEdgeville",
		name = "Respawn (Edgeville)",
		description = "House has the Respawn Portal (Edgeville).",
		section = pohPortalsSection,
		position = 34
	)
	default boolean pohPortalRespawnEdgeville()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalRespawnFalador",
		name = "Respawn (Falador)",
		description = "House has the Respawn Portal (Falador).",
		section = pohPortalsSection,
		position = 35
	)
	default boolean pohPortalRespawnFalador()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalRespawnFeroxEnclave",
		name = "Respawn (Ferox Enclave)",
		description = "House has the Respawn Portal (Ferox Enclave).",
		section = pohPortalsSection,
		position = 36
	)
	default boolean pohPortalRespawnFeroxEnclave()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalRespawnKourendCastle",
		name = "Respawn (Kourend Castle)",
		description = "House has the Respawn Portal (Kourend Castle).",
		section = pohPortalsSection,
		position = 37
	)
	default boolean pohPortalRespawnKourendCastle()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalRespawnLumbridge",
		name = "Respawn (Lumbridge)",
		description = "House has the Respawn Portal (Lumbridge).",
		section = pohPortalsSection,
		position = 38
	)
	default boolean pohPortalRespawnLumbridge()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalRespawnPrifddinas",
		name = "Respawn (Prifddinas)",
		description = "House has the Respawn Portal (Prifddinas).",
		section = pohPortalsSection,
		position = 39
	)
	default boolean pohPortalRespawnPrifddinas()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalSalveGraveyard",
		name = "Salve Graveyard",
		description = "House has the Salve Graveyard Portal.",
		section = pohPortalsSection,
		position = 40
	)
	default boolean pohPortalSalveGraveyard()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalSeersVillage",
		name = "Seers' Village",
		description = "House has the Seers' Village Portal.",
		section = pohPortalsSection,
		position = 41
	)
	default boolean pohPortalSeersVillage()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalSenntisten",
		name = "Senntisten",
		description = "House has the Senntisten Portal.",
		section = pohPortalsSection,
		position = 42
	)
	default boolean pohPortalSenntisten()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalTrollStronghold",
		name = "Troll Stronghold",
		description = "House has the Troll Stronghold Portal.",
		section = pohPortalsSection,
		position = 43
	)
	default boolean pohPortalTrollStronghold()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalTrollheim",
		name = "Trollheim",
		description = "House has the Trollheim Portal.",
		section = pohPortalsSection,
		position = 44
	)
	default boolean pohPortalTrollheim()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalVarrock",
		name = "Varrock",
		description = "House has the Varrock Portal.",
		section = pohPortalsSection,
		position = 45
	)
	default boolean pohPortalVarrock()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalWatchtower",
		name = "Watchtower",
		description = "House has the Watchtower Portal.",
		section = pohPortalsSection,
		position = 46
	)
	default boolean pohPortalWatchtower()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalWaterbirthIsland",
		name = "Waterbirth Island",
		description = "House has the Waterbirth Island Portal.",
		section = pohPortalsSection,
		position = 47
	)
	default boolean pohPortalWaterbirthIsland()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalWeiss",
		name = "Weiss",
		description = "House has the Weiss Portal.",
		section = pohPortalsSection,
		position = 48
	)
	default boolean pohPortalWeiss()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalWestArdougne",
		name = "West Ardougne",
		description = "House has the West Ardougne Portal.",
		section = pohPortalsSection,
		position = 49
	)
	default boolean pohPortalWestArdougne()
	{
		return false;
	}

	@ConfigItem(
		keyName = "pohPortalYanille",
		name = "Yanille",
		description = "House has the Yanille Portal.",
		section = pohPortalsSection,
		position = 50
	)
	default boolean pohPortalYanille()
	{
		return false;
	}
}
