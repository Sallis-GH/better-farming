package com.betterfarming.guidance;

import com.betterfarming.travel.Teleport;
import com.betterfarming.travel.TeleportLoader;
import com.betterfarming.travel.TeleportType;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import net.runelite.api.gameval.InterfaceID.MagicSpellbook;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SpellWidgetsTest
{
	/** Spells we knowingly don't map (not travel-relevant for farming). */
	private static final Set<String> UNMAPPED_OK = Set.of("Teleport to Target");

	@Test
	public void everyBundledSpellResolvesToASpellbookWidget() throws IOException
	{
		Set<String> unresolved = new LinkedHashSet<>();
		for (Teleport t : new TeleportLoader().loadAll())
		{
			if ((t.type() == TeleportType.SPELL || t.type() == TeleportType.HOME_SPELL)
				&& SpellWidgets.widgetFor(t) == null && !UNMAPPED_OK.contains(t.displayInfo()))
			{
				unresolved.add(t.displayInfo());
			}
		}
		assertTrue("unmapped spells (TSV resync renamed something?): " + unresolved,
			unresolved.isEmpty());
	}

	@Test
	public void destinationQualifiersResolveToTheSameSpell() throws IOException
	{
		Integer plain = null;
		Integer qualified = null;
		for (Teleport t : new TeleportLoader().loadAll())
		{
			if (t.type() != TeleportType.SPELL)
			{
				continue;
			}
			if ("Camelot Teleport".equals(t.displayInfo()))
			{
				plain = SpellWidgets.widgetFor(t);
			}
			if ("Camelot Teleport: Seers'".equals(t.displayInfo()))
			{
				qualified = SpellWidgets.widgetFor(t);
			}
		}
		assertEquals((Integer) MagicSpellbook.CAMELOT_TELEPORT, plain);
		assertEquals(plain, qualified);
	}

	@Test
	public void lunarIcePlateauAndAncientGhorrockAreDistinct()
	{
		Teleport icePlateau = spell("Ice Plateau Teleport");
		Teleport ghorrock = spell("Ghorrock Teleport");
		assertEquals((Integer) MagicSpellbook.TELE_GHORROCK, SpellWidgets.widgetFor(icePlateau));
		assertEquals((Integer) MagicSpellbook.ZAROSTELEPORT8, SpellWidgets.widgetFor(ghorrock));
	}

	private static Teleport spell(String display)
	{
		return new Teleport(TeleportType.SPELL, null,
			new net.runelite.api.coords.WorldPoint(3000, 3000, 0), 4, display,
			java.util.Map.of(), java.util.Set.of(), java.util.Set.of(),
			java.util.List.of(), false, null, false);
	}
}
