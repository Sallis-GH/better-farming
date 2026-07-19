"""One-shot correction of patch worldPoints that sat off their patch object.

Phase 5's patch-object highlighting made tile accuracy matter; these six
entries were verified against quest-helper HerbRun/TreeRun (Zoinkwiz),
AbusiveTuna/DidICompost FarmingPatches.java, chisel's cache object dump
(object id -> transmit varbit, matched to FarmingWorld's region slots), and
rendered map tiles (maps.runescape.wiki). The old Civitas values came from
the wiki's combined-map marker (all four landed on grass/buildings); the
Ardougne and Hosidius herb tiles were a few tiles off their objects.

Usage: py scripts/fix_patch_coordinates_2026_07.py
"""
import json

CORRECTIONS = {
    # Ortus Farm, west of Civitas illa Fortis (layout was mirrored:
    # north allotment = western L, south = eastern L, herb at the SW corner).
    "civitas_herb": (1582, 3094, 0),             # FARMING_HERB_PATCH_8 (50697)
    "civitas_flower": (1585, 3098, 0),           # FARMING_FLOWER_PATCH_8 (50693)
    "civitas_allotment_north": (1582, 3100, 0),  # FARMING_VEG_PATCH_16 (50696)
    "civitas_allotment_south": (1586, 3095, 0),  # FARMING_VEG_PATCH_17 (50695)
    # Herb patches a few tiles off their object (quest-helper HerbRun).
    "ardougne_herb": (2670, 3374, 0),
    "hosidius_herb": (1738, 3550, 0),
}

PATCHES = "src/main/resources/data/patches.json"

with open(PATCHES, encoding="utf-8") as f:
    patches = json.load(f)

remaining = dict(CORRECTIONS)
for patch in patches:
    fix = remaining.pop(patch["id"], None)
    if fix:
        patch["worldPoint"] = {"x": fix[0], "y": fix[1], "plane": fix[2]}
if remaining:
    raise SystemExit(f"ids not found: {list(remaining)}")

with open(PATCHES, "w", encoding="utf-8", newline="\n") as f:
    json.dump(patches, f, indent=2)
    f.write("\n")
print(f"corrected {len(CORRECTIONS)} worldPoints")
