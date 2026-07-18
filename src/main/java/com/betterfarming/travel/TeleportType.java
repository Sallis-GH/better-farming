package com.betterfarming.travel;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Source category of a teleport edge, keyed by which vendored TSV it loaded
 * from. `network` types have origin-only node rows that mesh into edges
 * (any node → any other node in the same network).
 */
@Getter
@RequiredArgsConstructor
public enum TeleportType
{
	SPELL("teleportation_spells.tsv", false),
	HOME_SPELL("teleportation_spells_home.tsv", false),
	ITEM("teleportation_items.tsv", false),
	JEWELLERY_BOX("teleportation_boxes.tsv", false),
	POH_PORTAL("teleportation_portals_poh.tsv", false),
	PORTAL("teleportation_portals.tsv", false),
	FAIRY_RING("fairy_rings.tsv", true),
	SPIRIT_TREE("spirit_trees.tsv", true),
	GNOME_GLIDER("gnome_gliders.tsv", true),
	QUETZAL("quetzals.tsv", true),
	QUETZAL_WHISTLE("quetzal_whistle.tsv", false),
	MUSHTREE("magic_mushtrees.tsv", true);

	private final String resourceFile;
	private final boolean network;
}
