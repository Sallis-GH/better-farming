package com.betterfarming.item;

import java.util.List;
import java.util.Set;

/**
 * Wiki-verified outfit pieces (ids cover every recolour AND both
 * inventory/worn ids — weight-reducing graceful pieces change item id when
 * equipped, and farmer's pieces vary by body type; jacket and shirt are
 * interchangeable torso options).
 * Generated from the OSRS wiki infoboxes; regenerate rather than hand-edit.
 *
 * Declared order is MEANINGFUL: the default recolour comes first per piece,
 * and the bank tab's ghost icons fall back to the first variant — an
 * order-randomizing Set.of once made an all-withdrawn graceful set render as
 * mixed recolours.
 */
final class Outfits
{
	/** Insertion-ordered, unlike Set.of — see the class comment. */
	private static Set<Integer> ordered(Integer... ids)
	{
		return java.util.Collections.unmodifiableSet(
			new java.util.LinkedHashSet<>(java.util.Arrays.asList(ids)));
	}
	static final List<OutfitPiece> GRACEFUL = List.of(
		new OutfitPiece("Hood", ordered(
			11850, 11851, 13579, 13580, 13591, 13592, 13603, 13604,
			13615, 13616, 13627, 13628, 13667, 13668, 21061, 21063,
			24743, 24745, 25069, 25071, 27444, 27446, 30045, 30047)),
		new OutfitPiece("Cape", ordered(
			11852, 11853, 13581, 13582, 13593, 13594, 13605, 13606,
			13617, 13618, 13629, 13630, 13669, 13670, 21064, 21066,
			24746, 24748, 25072, 25074, 27447, 27449, 30048, 30050)),
		new OutfitPiece("Top", ordered(
			11854, 11855, 13583, 13584, 13595, 13596, 13607, 13608,
			13619, 13620, 13631, 13632, 13671, 13672, 21067, 21069,
			24749, 24751, 25075, 25077, 27450, 27452, 30051, 30053)),
		new OutfitPiece("Legs", ordered(
			11856, 11857, 13585, 13586, 13597, 13598, 13609, 13610,
			13621, 13622, 13633, 13634, 13673, 13674, 21070, 21072,
			24752, 24754, 25078, 25080, 27453, 27455, 30054, 30056)),
		new OutfitPiece("Gloves", ordered(
			11858, 11859, 13587, 13588, 13599, 13600, 13611, 13612,
			13623, 13624, 13635, 13636, 13675, 13676, 21073, 21075,
			24755, 24757, 25081, 25083, 27456, 27458, 30057, 30059)),
		new OutfitPiece("Boots", ordered(
			11860, 11861, 13589, 13590, 13601, 13602, 13613, 13614,
			13625, 13626, 13637, 13638, 13677, 13678, 21076, 21078,
			24758, 24760, 25084, 25086, 27459, 27461, 30060, 30062))
	);

	static final List<OutfitPiece> FARMERS = List.of(
		new OutfitPiece("Strawhat", ordered(
			13646, 13647)),
		new OutfitPiece("Jacket / shirt", ordered(
			13642, 13643)),
		new OutfitPiece("Boro trousers", ordered(
			13640, 13641)),
		new OutfitPiece("Boots", ordered(
			13644, 13645))
	);

	private Outfits()
	{
	}
}
