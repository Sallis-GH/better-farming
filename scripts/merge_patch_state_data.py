"""Merge research-agent farming-state data into the plugin resources.

Inputs (research-agent deliverables, extracted from RuneLite core's
timetracking plugin — FarmingWorld.java / PatchImplementation.java /
Produce.java / FarmingTracker.java):
  patch_varbits.json  - per-patch state varbit + FarmingRegion region ids
  patch_states.json   - varbit value -> coarse crop state tables + growth data

Outputs:
  src/main/resources/data/patches.json       - gains stateVarbitId,
      stateRegionId (timetracking config key region), stateRegionIds
      (regions whose transmit varbits carry this patch), stateBounds
  src/main/resources/data/patch_states.json  - copied verbatim

Usage: py scripts/merge_patch_state_data.py <scratchpad-dir>
"""
import json
import shutil
import sys

# Player-position boxes gating live varbit reads, transcribed from
# FarmingWorld.java isInBounds overrides for regions shared by two
# FarmingRegions. Conservative: a failed check only skips an observation,
# it never records a wrong one. Keys are all-optional minX/maxX/minY/maxY/plane.
STATE_BOUNDS = {
    # Region 12083 is split at y=3272 between the Falador farming area (north)
    # and Port Sarim's spirit tree (south).
    "falador_allotment_north_west": {"minY": 3272},
    "falador_allotment_south_east": {"minY": 3272},
    "falador_flower": {"minY": 3272},
    "falador_herb": {"minY": 3272},
    "port_sarim_spirit_tree": {"maxY": 3271},
    # Region 11317 is split between the Catherby vegetable patches
    # (x<2840, y>=3440, plane 0) and the fruit tree area.
    "catherby_allotment_north": {"maxX": 2839, "minY": 3440, "plane": 0},
    "catherby_allotment_south": {"maxX": 2839, "minY": 3440, "plane": 0},
    "catherby_flower": {"maxX": 2839, "minY": 3440, "plane": 0},
    "catherby_herb": {"maxX": 2839, "minY": 3440, "plane": 0},
    "catherby_fruit_tree": {"minX": 2840},
    # Fossil Island hardwoods are valid on the ground floor only (the
    # upstairs mushroom meadow shares the region ids).
    "fossil_island_hardwood_east": {"plane": 0},
    "fossil_island_hardwood_middle": {"plane": 0},
    "fossil_island_hardwood_west": {"plane": 0},
}

PATCHES = "src/main/resources/data/patches.json"
STATES_OUT = "src/main/resources/data/patch_states.json"


def main(scratch):
    with open(f"{scratch}/patch_varbits.json", encoding="utf-8") as f:
        varbits = json.load(f)
    if varbits["unmatched"]:
        sys.exit(f"unmatched patches present: {varbits['unmatched']}")
    matched = varbits["matched"]

    with open(PATCHES, encoding="utf-8") as f:
        patches = json.load(f)

    for patch in patches:
        entry = matched.get(patch["id"])
        if entry is None:
            sys.exit(f"no varbit mapping for patch {patch['id']}")
        patch["stateVarbitId"] = entry["varbitId"]
        patch["stateRegionId"] = entry["regionId"]
        patch["stateRegionIds"] = entry["allRegionIds"]
        bounds = STATE_BOUNDS.get(patch["id"])
        if bounds:
            patch["stateBounds"] = bounds
        else:
            patch.pop("stateBounds", None)

    unknown_bounds = set(STATE_BOUNDS) - {p["id"] for p in patches}
    if unknown_bounds:
        sys.exit(f"STATE_BOUNDS ids not in patches.json: {unknown_bounds}")

    with open(PATCHES, "w", encoding="utf-8", newline="\n") as f:
        json.dump(patches, f, indent=2)
        f.write("\n")

    shutil.copyfile(f"{scratch}/patch_states.json", STATES_OUT)
    print(f"merged {len(matched)} varbit mappings; wrote {STATES_OUT}")


if __name__ == "__main__":
    main(sys.argv[1])
