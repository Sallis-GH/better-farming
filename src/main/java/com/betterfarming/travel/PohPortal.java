package com.betterfarming.travel;

import com.betterfarming.BetterFarmingConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * One entry per POH portal in teleportation_portals_poh.tsv, mapping the
 * TSV's "Display info" name to its config checkbox. Generated alongside the
 * config items — keep in sync when the vendored TSV gains portals.
 */
public enum PohPortal
{
	PORTAL_ANNAKARL("Annakarl Portal", BetterFarmingConfig::pohPortalAnnakarl),
	PORTAL_APE_ATOLL_DUNGEON("Ape Atoll Dungeon Portal", BetterFarmingConfig::pohPortalApeAtollDungeon),
	PORTAL_ARCEUUS_LIBRARY("Arceuus Library Portal", BetterFarmingConfig::pohPortalArceuusLibrary),
	PORTAL_ARDOUGNE("Ardougne Portal", BetterFarmingConfig::pohPortalArdougne),
	PORTAL_BARBARIAN_OUTPOST("Barbarian Outpost Portal", BetterFarmingConfig::pohPortalBarbarianOutpost),
	PORTAL_BARROWS("Barrows Portal", BetterFarmingConfig::pohPortalBarrows),
	PORTAL_BATTLEFRONT("Battlefront Portal", BetterFarmingConfig::pohPortalBattlefront),
	PORTAL_CAMELOT("Camelot Portal", BetterFarmingConfig::pohPortalCamelot),
	PORTAL_CARRALLANGER("Carrallanger Portal", BetterFarmingConfig::pohPortalCarrallanger),
	PORTAL_CATHERBY("Catherby Portal", BetterFarmingConfig::pohPortalCatherby),
	PORTAL_CEMETERY("Cemetery Portal", BetterFarmingConfig::pohPortalCemetery),
	PORTAL_CIVITAS_ILLA_FORTIS("Civitas illa Fortis Portal", BetterFarmingConfig::pohPortalCivitasIllaFortis),
	PORTAL_DAREEYAK("Dareeyak Portal", BetterFarmingConfig::pohPortalDareeyak),
	PORTAL_DRAYNOR_MANOR("Draynor Manor Portal", BetterFarmingConfig::pohPortalDraynorManor),
	PORTAL_FALADOR("Falador Portal", BetterFarmingConfig::pohPortalFalador),
	PORTAL_FENKENSTRAINS_CASTLE("Fenkenstrain's Castle Portal", BetterFarmingConfig::pohPortalFenkenstrainsCastle),
	PORTAL_FISHING_GUILD("Fishing Guild Portal", BetterFarmingConfig::pohPortalFishingGuild),
	PORTAL_GHORROCK("Ghorrock Portal", BetterFarmingConfig::pohPortalGhorrock),
	PORTAL_GRAND_EXCHANGE("Grand Exchange Portal", BetterFarmingConfig::pohPortalGrandExchange),
	PORTAL_HARMONY_ISLAND("Harmony Island Portal", BetterFarmingConfig::pohPortalHarmonyIsland),
	PORTAL_ICE_PLATEAU("Ice Plateau Portal", BetterFarmingConfig::pohPortalIcePlateau),
	PORTAL_KHARYRLL("Kharyrll Portal", BetterFarmingConfig::pohPortalKharyrll),
	PORTAL_KOUREND("Kourend Portal", BetterFarmingConfig::pohPortalKourend),
	PORTAL_LASSAR("Lassar Portal", BetterFarmingConfig::pohPortalLassar),
	PORTAL_LUMBRIDGE("Lumbridge Portal", BetterFarmingConfig::pohPortalLumbridge),
	PORTAL_LUNAR_ISLE("Lunar Isle Portal", BetterFarmingConfig::pohPortalLunarIsle),
	PORTAL_MARIM("Marim Portal", BetterFarmingConfig::pohPortalMarim),
	PORTAL_MIND_ALTAR("Mind Altar Portal", BetterFarmingConfig::pohPortalMindAltar),
	PORTAL_OURANIA("Ourania Portal", BetterFarmingConfig::pohPortalOurania),
	PORTAL_PADDEWWA("Paddewwa Portal", BetterFarmingConfig::pohPortalPaddewwa),
	PORTAL_PORT_KHAZARD("Port Khazard Portal", BetterFarmingConfig::pohPortalPortKhazard),
	PORTAL_RESPAWN_CAMELOT("Respawn Portal (Camelot)", BetterFarmingConfig::pohPortalRespawnCamelot),
	PORTAL_RESPAWN_CIVITAS_ILLA_FORTIS("Respawn Portal (Civitas illa Fortis)", BetterFarmingConfig::pohPortalRespawnCivitasIllaFortis),
	PORTAL_RESPAWN_EDGEVILLE("Respawn Portal (Edgeville)", BetterFarmingConfig::pohPortalRespawnEdgeville),
	PORTAL_RESPAWN_FALADOR("Respawn Portal (Falador)", BetterFarmingConfig::pohPortalRespawnFalador),
	PORTAL_RESPAWN_FEROX_ENCLAVE("Respawn Portal (Ferox Enclave)", BetterFarmingConfig::pohPortalRespawnFeroxEnclave),
	PORTAL_RESPAWN_KOUREND_CASTLE("Respawn Portal (Kourend Castle)", BetterFarmingConfig::pohPortalRespawnKourendCastle),
	PORTAL_RESPAWN_LUMBRIDGE("Respawn Portal (Lumbridge)", BetterFarmingConfig::pohPortalRespawnLumbridge),
	PORTAL_RESPAWN_PRIFDDINAS("Respawn Portal (Prifddinas)", BetterFarmingConfig::pohPortalRespawnPrifddinas),
	PORTAL_SALVE_GRAVEYARD("Salve Graveyard Portal", BetterFarmingConfig::pohPortalSalveGraveyard),
	PORTAL_SEERS_VILLAGE("Seers' Village Portal", BetterFarmingConfig::pohPortalSeersVillage),
	PORTAL_SENNTISTEN("Senntisten Portal", BetterFarmingConfig::pohPortalSenntisten),
	PORTAL_TROLL_STRONGHOLD("Troll Stronghold Portal", BetterFarmingConfig::pohPortalTrollStronghold),
	PORTAL_TROLLHEIM("Trollheim Portal", BetterFarmingConfig::pohPortalTrollheim),
	PORTAL_VARROCK("Varrock Portal", BetterFarmingConfig::pohPortalVarrock),
	PORTAL_WATCHTOWER("Watchtower Portal", BetterFarmingConfig::pohPortalWatchtower),
	PORTAL_WATERBIRTH_ISLAND("Waterbirth Island Portal", BetterFarmingConfig::pohPortalWaterbirthIsland),
	PORTAL_WEISS("Weiss Portal", BetterFarmingConfig::pohPortalWeiss),
	PORTAL_WEST_ARDOUGNE("West Ardougne Portal", BetterFarmingConfig::pohPortalWestArdougne),
	PORTAL_YANILLE("Yanille Portal", BetterFarmingConfig::pohPortalYanille)
	;

	private static final Map<String, PohPortal> BY_DISPLAY_INFO = new HashMap<>();

	static
	{
		for (PohPortal p : values())
		{
			BY_DISPLAY_INFO.put(p.displayInfo, p);
		}
	}

	private final String displayInfo;
	private final Function<BetterFarmingConfig, Boolean> enabled;

	PohPortal(String displayInfo, Function<BetterFarmingConfig, Boolean> enabled)
	{
		this.displayInfo = displayInfo;
		this.enabled = enabled;
	}

	public boolean isEnabled(BetterFarmingConfig config)
	{
		return enabled.apply(config);
	}

	/** Null when the display name is unknown (new upstream portal). */
	public static PohPortal forDisplayInfo(String displayInfo)
	{
		return displayInfo == null ? null : BY_DISPLAY_INFO.get(displayInfo);
	}
}