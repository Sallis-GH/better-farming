package com.betterfarming.travel;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.gameval.ItemID;

/**
 * Charge semantics for teleport jewellery. Each charge tier is a separate
 * item id (Skills necklace(4) ≠ (3)), and using a teleport steps the item
 * down one tier — so "two skills-necklace legs" needs ONE necklace with ≥2
 * charges, never two necklaces. The vendored transport data writes these
 * requirements as raw OR-variation id lists, which read as interchangeable
 * items unless classified.
 *
 * Classification is derived, not hand-typed (hard-won rule 6): the gameval
 * {@link ItemID} constant names encode charges as a trailing _N on a shared
 * base per family (JEWL_NECKLACE_OF_SKILLS_1..6, RING_OF_DUELING_1..8). A
 * requirement is charge jewellery exactly when every OR-variant id resolves
 * to the same base with a numeric charge suffix. Families with mixed bases
 * (crossbows, staves) or single ids (runes, coins) never qualify.
 *
 * The id→(base, charges) tables reflect over ItemID once at class load;
 * thread-safe via the class-initialization guarantee.
 */
public final class JewelleryCharges
{
	private static final Pattern CHARGE_SUFFIX = Pattern.compile("^(.+)_(\\d+)$");

	private static final Map<Integer, String> BASE_BY_ID = new HashMap<>();
	private static final Map<Integer, Integer> CHARGES_BY_ID = new HashMap<>();

	static
	{
		for (Field field : ItemID.class.getDeclaredFields())
		{
			if (!Modifier.isStatic(field.getModifiers()) || field.getType() != int.class)
			{
				continue;
			}
			Matcher m = CHARGE_SUFFIX.matcher(field.getName());
			if (!m.matches())
			{
				continue;
			}
			try
			{
				int id = field.getInt(null);
				// First mapping wins: gameval has no duplicate ids, and a
				// non-numeric-suffix name never lands here anyway.
				BASE_BY_ID.putIfAbsent(id, m.group(1));
				CHARGES_BY_ID.putIfAbsent(id, Integer.parseInt(m.group(2)));
			}
			catch (IllegalAccessException | NumberFormatException ignored)
			{
				// Unreadable or absurd constant: simply not charge jewellery.
			}
		}
	}

	private JewelleryCharges()
	{
	}

	/**
	 * True when the requirement's OR-variations are the charge tiers of one
	 * jewellery item: every id maps to the same gameval base name with a
	 * numeric charge suffix, and there are at least two tiers.
	 */
	public static boolean isChargeJewellery(TeleportItemRequirement req)
	{
		int[] ids = req.itemIds();
		if (ids.length < 2)
		{
			return false;
		}
		String base = null;
		for (int id : ids)
		{
			String b = BASE_BY_ID.get(id);
			if (b == null)
			{
				return false;
			}
			if (base == null)
			{
				base = b;
			}
			else if (!base.equals(b))
			{
				return false;
			}
		}
		return true;
	}

	/** Charges encoded in the id's gameval name suffix; 0 when uncharged/unknown. */
	public static int chargesOf(int itemId)
	{
		return CHARGES_BY_ID.getOrDefault(itemId, 0);
	}
}
