package com.betterfarming.travel;

import net.runelite.api.gameval.ItemID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JewelleryChargesTest
{
	private static TeleportItemRequirement req(int... ids)
	{
		return new TeleportItemRequirement(ids, new int[0], new int[0], 1, "test");
	}

	@Test
	public void chargeTiersOfOneItemClassify()
	{
		assertTrue(JewelleryCharges.isChargeJewellery(req(
			ItemID.JEWL_NECKLACE_OF_SKILLS_1, ItemID.JEWL_NECKLACE_OF_SKILLS_2,
			ItemID.JEWL_NECKLACE_OF_SKILLS_3, ItemID.JEWL_NECKLACE_OF_SKILLS_4,
			ItemID.JEWL_NECKLACE_OF_SKILLS_5, ItemID.JEWL_NECKLACE_OF_SKILLS_6)));
		assertTrue(JewelleryCharges.isChargeJewellery(req(
			ItemID.RING_OF_DUELING_8, ItemID.RING_OF_DUELING_1)));
	}

	@Test
	public void chargesComeFromTheGamevalNameSuffix()
	{
		assertEquals(4, JewelleryCharges.chargesOf(ItemID.JEWL_NECKLACE_OF_SKILLS_4));
		assertEquals(6, JewelleryCharges.chargesOf(ItemID.JEWL_NECKLACE_OF_SKILLS_6));
		assertEquals(8, JewelleryCharges.chargesOf(ItemID.RING_OF_DUELING_8));
		assertEquals("uncharged necklace has no suffix", 0,
			JewelleryCharges.chargesOf(ItemID.JEWL_NECKLACE_OF_SKILLS));
	}

	@Test
	public void singleIdsAndInterchangeableItemsDoNotClassify()
	{
		assertFalse("single id (a rune) has no tiers",
			JewelleryCharges.isChargeJewellery(req(ItemID.LAWRUNE)));
		assertFalse("air-rune variations are different items, not tiers",
			JewelleryCharges.isChargeJewellery(req(
				ItemID.AIRRUNE, ItemID.MISTRUNE, ItemID.DUSTRUNE, ItemID.SMOKERUNE)));
		assertFalse("uncharged variant breaks the all-tiers rule",
			JewelleryCharges.isChargeJewellery(req(
				ItemID.JEWL_NECKLACE_OF_SKILLS, ItemID.JEWL_NECKLACE_OF_SKILLS_1)));
	}
}
