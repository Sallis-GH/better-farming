"""Align patch worldPoints to DidICompost's object anchor tiles.

AbusiveTuna/DidICompost (FarmingPatches.java) pairs each patch OBJECT id with
the object's anchor WorldPoint — guaranteed to be covered by the object's
footprint, which is what PatchHighlightOverlay's geometric lookup needs. Our
tiles came from wiki research and sit up to a few tiles off, occasionally
just outside the footprint (reported in-game: Morytania herb marker on
grass).

Matching is geometric per patch type (nearest DidICompost entry of the same
type within 6 tiles) — DidICompost's own N/S naming disagrees with
FarmingWorld's for some sites, so names are never trusted for pairing.
The four Ortus Farm (civitas_*) entries keep our pixel-verified tiles.

Usage: py scripts/adopt_didicompost_tiles.py <dic_list.txt from FarmingPatches.java>
"""
import json
import re
import sys

TYPE_TOKENS = {
    "ALLOTMENT": "ALLOTMENT", "FLOWER": "FLOWER", "HERB": "HERB",
    "HOP": "HOPS", "BUSH": "BUSH", "SPIRIT": "SPIRIT_TREE",
    "FRUIT": "FRUIT_TREE", "CRYSTAL": "CRYSTAL_TREE", "CELSTRUS": "CELASTRUS",
    "REDWOOD": "REDWOOD", "HARDWOOD": "HARDWOOD_TREE", "CALQUAT": "CALQUAT",
    "CACTUS": "CACTUS", "MUSHROOM": "MUSHROOM", "BELL": "BELLADONNA",
    "SEAWEED": "SEAWEED", "ANIMA": "ANIMA", "HESPORI": "HESPORI",
    "TREE": "TREE",  # checked last: FRUIT/SPIRIT/CRYSTAL/... take priority
}

SKIP_IDS = {"civitas_herb", "civitas_flower",
            "civitas_allotment_north", "civitas_allotment_south"}

# Second pass applied 2026-07-19: five flower patches sat 7-15 tiles off and
# exceeded the conservative 6-tile bound; adopted DidICompost anchors after
# manual review (falador/morytania/ardougne/hosidius/farming_guild flower).


def dic_type(name):
    for token, ptype in TYPE_TOKENS.items():
        if token in name:
            return ptype
    return None


def main(listing):
    entries = []
    for line in open(listing, encoding="utf-8"):
        m = re.match(r"([A-Z_0-9]+)\((\d+), new WorldPoint\((\d+),\s*(\d+),\s*(\d)\)\)",
                     line.strip())
        if m:
            t = dic_type(m.group(1))
            if t:
                entries.append((t, int(m.group(3)), int(m.group(4)), int(m.group(5))))

    patches = json.load(open("src/main/resources/data/patches.json", encoding="utf-8"))
    changed = 0
    for patch in patches:
        if patch["id"] in SKIP_IDS:
            continue
        w = patch["worldPoint"]
        best, bd = None, 99
        for t, x, y, p in entries:
            if t != patch["type"] or p != w["plane"]:
                continue
            d = max(abs(w["x"] - x), abs(w["y"] - y))
            if d < bd:
                best, bd = (x, y), d
        if best and 0 < bd <= 6:
            print(f"{patch['id']:35s} ({w['x']},{w['y']}) -> {best} d={bd}")
            w["x"], w["y"] = best
            changed += 1

    with open("src/main/resources/data/patches.json", "w", encoding="utf-8", newline="\n") as f:
        json.dump(patches, f, indent=2)
        f.write("\n")
    print(f"adopted {changed} tiles")


if __name__ == "__main__":
    main(sys.argv[1])
