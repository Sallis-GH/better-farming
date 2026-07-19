package com.betterfarming.guidance;

import com.betterfarming.travel.Teleport;
import com.betterfarming.travel.TeleportType;
import com.betterfarming.travel.VarCheck;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.InterfaceID.MagicSpellbook;

/**
 * Maps a teleport spell's display name (vendored TSV "Display info") to its
 * spellbook widget, so guidance can highlight the spell to click rather than
 * the runes it consumes. All ids are gameval constants — compile-checked
 * against the RuneLite API, never hand-typed numbers. Notable trap: the
 * lunar Ice Plateau spell's internal name is "ghorrock" (TELE_GHORROCK),
 * while the ancient Ghorrock Teleport is ZAROSTELEPORT8.
 */
public final class SpellWidgets
{
	private static final Map<String, Integer> BY_NAME = new HashMap<>();

	static
	{
		// Standard
		BY_NAME.put("Varrock Teleport", MagicSpellbook.VARROCK_TELEPORT);
		BY_NAME.put("Lumbridge Teleport", MagicSpellbook.LUMBRIDGE_TELEPORT);
		BY_NAME.put("Falador Teleport", MagicSpellbook.FALADOR_TELEPORT);
		BY_NAME.put("Teleport to House", MagicSpellbook.TELEPORT_TO_YOUR_HOUSE);
		BY_NAME.put("Camelot Teleport", MagicSpellbook.CAMELOT_TELEPORT);
		BY_NAME.put("Kourend Castle Teleport", MagicSpellbook.KOUREND_TELEPORT);
		BY_NAME.put("Ardougne Teleport", MagicSpellbook.ARDOUGNE_TELEPORT);
		BY_NAME.put("Civitas illa Fortis Teleport", MagicSpellbook.FORTIS_TELEPORT);
		BY_NAME.put("Watchtower Teleport", MagicSpellbook.WATCHTOWER_TELEPORT);
		BY_NAME.put("Trollheim Teleport", MagicSpellbook.TROLLHEIM_TELEPORT);
		BY_NAME.put("Ape Atoll Teleport", MagicSpellbook.APE_TELEPORT);
		// Ancient (spellbook order)
		BY_NAME.put("Paddewwa Teleport", MagicSpellbook.ZAROSTELEPORT1);
		BY_NAME.put("Senntisten Teleport", MagicSpellbook.ZAROSTELEPORT2);
		BY_NAME.put("Kharyrll Teleport", MagicSpellbook.ZAROSTELEPORT3);
		BY_NAME.put("Lassar Teleport", MagicSpellbook.ZAROSTELEPORT4);
		BY_NAME.put("Dareeyak Teleport", MagicSpellbook.ZAROSTELEPORT5);
		BY_NAME.put("Carrallanger Teleport", MagicSpellbook.ZAROSTELEPORT6);
		BY_NAME.put("Annakarl Teleport", MagicSpellbook.ZAROSTELEPORT7);
		BY_NAME.put("Ghorrock Teleport", MagicSpellbook.ZAROSTELEPORT8);
		// Lunar
		BY_NAME.put("Moonclan Teleport", MagicSpellbook.TELE_MOONCLAN);
		BY_NAME.put("Ourania Teleport", MagicSpellbook.OURANIA_TELEPORT);
		BY_NAME.put("Waterbirth Teleport", MagicSpellbook.TELE_WATERBIRTH);
		BY_NAME.put("Barbarian Teleport", MagicSpellbook.TELE_BARB_OUT);
		BY_NAME.put("Khazard Teleport", MagicSpellbook.TELE_KHAZARD);
		BY_NAME.put("Fishing Guild Teleport", MagicSpellbook.TELE_FISH);
		BY_NAME.put("Catherby Teleport", MagicSpellbook.TELE_CATHER);
		BY_NAME.put("Ice Plateau Teleport", MagicSpellbook.TELE_GHORROCK);
		// Arceuus
		BY_NAME.put("Arceuus Library Teleport", MagicSpellbook.TELEPORT_ARCEUUS_LIBRARY);
		BY_NAME.put("Draynor Manor Teleport", MagicSpellbook.TELEPORT_DRAYNOR_MANOR);
		BY_NAME.put("Mind Altar Teleport", MagicSpellbook.TELEPORT_MIND_ALTAR);
		BY_NAME.put("Respawn Teleport", MagicSpellbook.TELEPORT_RESPAWN);
		BY_NAME.put("Salve Graveyard Teleport", MagicSpellbook.TELEPORT_SALVE_GRAVEYARD);
		BY_NAME.put("Fenkenstrain's Castle Teleport", MagicSpellbook.TELEPORT_FENKENSTRAIN_CASTLE);
		BY_NAME.put("West Ardougne Teleport", MagicSpellbook.TELEPORT_WEST_ARDOUGNE);
		BY_NAME.put("Harmony Island Teleport", MagicSpellbook.TELEPORT_HARMONY_ISLAND);
		BY_NAME.put("Cemetery Teleport", MagicSpellbook.TELEPORT_CEMETERY);
		BY_NAME.put("Barrows Teleport", MagicSpellbook.TELEPORT_BARROWS);
		BY_NAME.put("Battlefront Teleport", MagicSpellbook.TELEPORT_BATTLEFRONT);
		// Free home teleports
		BY_NAME.put("Lumbridge Home Teleport", MagicSpellbook.TELEPORT_HOME_STANDARD);
		BY_NAME.put("Edgeville Home Teleport", MagicSpellbook.TELEPORT_HOME_ZAROS);
		BY_NAME.put("Lunar Home Teleport", MagicSpellbook.TELEPORT_HOME_LUNAR);
		BY_NAME.put("Arceuus Home Teleport", MagicSpellbook.TELEPORT_HOME_ARCEUUS);
	}

	private SpellWidgets()
	{
	}

	/**
	 * The spellbook widget to click for this teleport, or null when it is not
	 * a spell (or an unmapped one — the rune highlight then leads instead).
	 */
	public static Integer widgetFor(Teleport teleport)
	{
		if (teleport == null
			|| (teleport.type() != TeleportType.SPELL && teleport.type() != TeleportType.HOME_SPELL)
			|| teleport.displayInfo() == null)
		{
			return null;
		}
		String name = teleport.displayInfo();
		// Destination qualifiers ("Camelot Teleport: Seers'", "Varrock
		// Teleport: GE") and house-mode suffixes name the same spell.
		int colon = name.indexOf(':');
		if (colon > 0)
		{
			name = name.substring(0, colon);
		}
		int paren = name.indexOf(" (");
		if (paren > 0)
		{
			name = name.substring(0, paren);
		}
		Integer widget = BY_NAME.get(name.trim());
		if (widget != null && "Ape Atoll Teleport".equals(name.trim()))
		{
			// Same display name on two books; the row's spellbook varbit
			// (4070: 0=standard, 3=arceuus) disambiguates.
			for (VarCheck check : teleport.varChecks())
			{
				if (check.id() == 4070 && check.value() == 3)
				{
					return MagicSpellbook.TELEPORT_APE_ATOLL_DUNGEON;
				}
			}
		}
		return widget;
	}
}
