# Better Farming — agent notes

RuneLite plugin for OSRS farming runs. Owner: Sallis (salamon@newcommerce.no, GitHub Sallis-GH).
Target: RuneLite Plugin Hub eventually — keep licensing clean (repo is BSD-2), no automation-adjacent behavior.
V1 scope: herb + tree + fruit-tree runs. UI/design polish is deliberately deferred until the plugin is feature-complete.

## Status (see README table)

Phases 0–3.x are DONE and merged: dataset, sidebar with locks + seed selection, equipment
manager (run items with on-player/in-bank/missing), farming bank tab, teleport-aware
optimal routing with granular POH config, barbarian bare-handed planting, outfit gear
sections, tab-over-runes preference.

Phase 4 (guidance arrows) is DONE: `guidance/` package — ported DirectionArrow/WorldLines/
GuidancePerspective (quest-helper, BSD-2), GuidanceService (visited-stop tracking off
GameTick, 10-tile arrival radius, out-of-order tolerant, progress survives re-plans,
resets on logout or via hint-overlay right-click), overlays (world arrow with edge-clamped
off-screen hint, minimap arrow, world-map route lines), WorldMapPoint marker,
TravelHint text ("Cast X"/"Break X"), ShortestPathBridge (PluginMessage "shortestpath"
path/clear), per-overlay config toggles.
Phase 6 additions: multi-hop chain legs carry their hop list (Teleport.chainHops);
GuidanceService derives a per-tick travelTarget/travelHop (sequential position-based
progression — never crow-flies waypoint picking, chains exist where straight-line distance
lies); arrows/shortest-path/item-highlights follow the waypoint; TravelTargetOverlay
outlines the boarding NPC (id from the TSV menu column) or gangplank object;
compost buckets glow while planting.
Phase 5 (crop state & planting guidance) is DONE: `farming/` package — patches.json now
carries per-patch state varbits + FarmingRegion ids (research-agent extracted from RuneLite
core timetracking, merged by scripts/merge_patch_state_data.py; patch_states.json holds the
value→state tables). PatchStateService reads live varbits (gated on player region +
StateBounds for split regions) and falls back to the Time Tracking plugin's RS-profile
config ("timetracking", key "<regionId>.<varbitId>", value "<value>:<epochSec>"); GROWING
observations decay to UNKNOWN after the type's min grow time. RunOrderService pins the
planned order mid-run (crop filter applies at plan time only; planFixedOrder recomputes
teleports along the pinned sequence); GuidanceService completes legs by crop state
(proximity only for UNKNOWN); PlantingGuide drives patch-object outlines + inventory
highlights (seed/rake at the patch, teleport items while travelling).
NEXT: **feedback backlog from 2026-07-19 in-game testing** (tackle in order; each item has
diagnosis notes from the session):

1. **Walk-beats-teleport for the current leg — DONE.** RoutePlanner.walkBeatsTeleport
   (pure; 3-tick margin favours the teleport on near-ties) evaluated per tick in
   GuidanceService.updateTravelTarget, only before a chain's first hop (mid-chain the
   whole-chain price would overstate); when walking wins, travelHop nulls (suppressing
   spell/item/boarding highlights) and TravelHintOverlay says "Walk to X". Pinned route
   untouched.
2. **Warn when a planned leg's teleport items aren't on the player.** Player reaches a
   stop, next leg says "Break X tablet", tablet was never withdrawn. Per-leg check of
   leg.teleport().items() vs ItemTracker on-player; warning line in TravelHintOverlay
   (+ red row in RunOrderSection). RunItemsService statuses already computed — reuse.
3. **Start/stop/skip run controls.** Plugin currently guides whenever groups are active —
   annoying during other activities. Add explicit run lifecycle (sidebar button in
   RunOrderSection + overlay right-click): stopped = no arrows/hints/shortest-path/
   deviation replans; skip = mark current leg visited (the 50-tile walk-away skip exists
   in GuidanceService, make it a button too).
4. **Jewellery charges, not item count.** Two skills-necklace legs currently show
   "Skills necklace ×2"; correct is ONE necklace with ≥2 charges, displayed like
   "Skills necklace(2)+". Charge variants are separate item ids (ItemVariations names
   encode charges); classify charge-jewellery requirements, sum charges needed, satisfy
   by any carried variant with ≥N charges.
5. **Run completion must track harvesting.** Visiting a HARVESTABLE patch without
   harvesting marked the run complete — that should only happen via the UNKNOWN-proximity
   path, so likely a value-table gap (snape grass allotment values?) or a live-read gate
   miss. Investigate the actual varbit value at the reported patch; consider timetracking's
   harvest-stage modelling (patch varbit steps down per pick). Completion should demand an
   observed EMPTY/planted state when a varbit mapping exists, never proximity.
6. **Bill Teach is multi-located.** Only highlight the ferry NPC after boarding (post-
   gangplank), not whichever namesake stands nearest: stage the ship boarding as
   gangplank-object first, NPC only once aboard (detect via tiny distance / same-tile
   plane after crossing).

THEN: **Phase 7 — polish** (protection payments, player-grown spirit trees as teleport
origins, agility shortcuts, diary cheaper teleports; UI/design pass).

## Architecture map

- `data/` — immutable Lombok @Value models loaded from `resources/data/patches.json` (~85
  patches with WorldPoints) + `seeds.json` (78 seeds, plantable ids, payments). Validated
  at startUp by `loader/FarmingDataValidator`.
- `data/requirement/` — self-evaluating `Requirement` (isMet/describe/validate/tracked*);
  new kinds = one class + one entry in `RequirementDeserializer.TYPES`.
- `state/PatchSelectionService` — seed choice + group-active persistence (versioned JSON
  blob in config) + listener fanout.
- `item/` — `ItemTracker` (inv/equip live, bank last-known, clears on LOGIN_SCREEN),
  `PlayerUnlocks` (barbarian bare-handed planting = varbit 9609 == 3), `RunItemsService`
  (tools/plantables/payments/teleport-items/gear rows), `Outfits` (generated, wiki-verified
  ids incl. worn/inventory variants — regenerate, don't hand-edit).
- `travel/` — `TeleportLoader` parses vendored `resources/transports/*.tsv` (from
  Skretzo/shortest-path, BSD-2; network files = entry-tiles × requirement-carrying exits;
  ships/boats/charters = origin+destination rows; transports_curated.tsv = hand-picked
  verbatim rows from upstream transports.tsv, e.g. the Mos Le'Harmless→Harmony boat),
  `TeleportAvailabilityService` (live quest/varbit/boosted-skill/item filtering; POH
  facilities gated by granular config + composed house-chain edges), `RoutePlanner` (pure;
  Held-Karp ≤13 stops, 2-opt beyond; slot-penalized selection — 2.5 ticks per inventory
  slot, equipped items cost 0 via TeleportSlotCost — POH bias 20 ticks; expensive legs
  (unreachable single-hop) fall back to a Dijkstra chain search over the transport graph,
  collapsed into one composite Teleport via Teleport.chainOf; free home teleport is
  oncePerRun — capOncePerRun re-prices later legs; its 30-min cooldown is data-driven via
  the vendored "892@30" varplayer check, NOT special-cased code. Home teleport ≠ Teleport
  to House: the free spell goes to the spellbook home area, never the POH),
  `RunOrderService` (recomputes on client thread via injected executor), `PohPortal`
  (generated enum).
- `bank/` — quest-helper-style bank tab (BSD-2 attribution): forces search state, hides
  real widgets, repaints sectioned layout, remaps withdraw clicks. Sections: Tools /
  Seeds & saplings / Payments / Teleports / one per outfit / Other items.
- `ui/` — sidebar panel: RunOrderSection, RunItemsSection, PatchTypeSections with
  PatchGroupCards. `ClientLevelSource` facade over Client (tests use FakeClient).

## Hard-won rules (violate at your peril)

1. **Client state reads (varbits, position, quests) only on the client thread.** RuneLite
   dev-mode assertions throw AssertionError from other threads. Services whose compute
   reads client state marshal via an injected `Consumer<Runnable>` executor
   (ClientThread::invokeLater in prod, Runnable::run in tests) — see RunOrderService.
2. **Every listener fanout catches `Exception | AssertionError` per listener** — one bad
   listener must never starve the rest (this bug shipped once; regression test exists).
3. Services mutate caches on the client thread, fan out to Swing via
   SwingUtilities.invokeLater with a listener snapshot; cards unsubscribe in removeNotify().
4. Headless tests pass ≠ works in-game. Threading bugs only reproduce in the real client
   (`.\gradlew.bat run` = dev client with --developer-mode).
5. **Plugin startUp/shutDown run on the EDT when toggled from the settings panel** — any
   client-state read (quest scripts assert even at the login screen) or EventBus post
   (subscribers run synchronously on the posting thread) in the lifecycle methods must be
   marshalled via clientThread.invokeLater, capturing locals since shutDown nulls fields.
   EventBus also rejects @Subscribe methods not named on<EventName> at registration —
   EventBusNamingTest guards this; both failure modes leave the settings toggle refusing
   to enable with only a log line to explain.
6. Data files are wiki-verified by research agents writing JSON, merged by script —
   never hand-type OSRS item ids from memory. Graceful pieces have separate worn/inventory
   ids; farmer pieces have body-type ids; spirit trees take multi-item payments.

## Conventions

- Build: `.\gradlew.bat build test` (JDK 25 works; Gradle 9.5 wrapper). All green before PR.
- Workflow: branch per phase (`phase-N-description`) → PR → **squash merge** → delete
  branch. Commit messages end with the Claude co-author trailer. Git identity: user.name
  "Sallis", user.email "74314359+Sallis-GH@users.noreply.github.com" (repo-local).
- Code style: tabs, braces on new line, javadoc-style comments explaining threading and
  non-obvious decisions (no TODO tags — prose notes). Test discipline is high: fakes over
  mocks (FakeClient/FakeConfigStore), pure functions where possible.
- Generated code (BetterFarmingConfig POH portals, PohPortal, Outfits): regenerate via the
  scripts pattern (python from TSV/JSON), don't hand-edit entries.

## Phase 4 implementation pointers (researched, ready to use)

Port from Zoinkwiz/quest-helper (BSD-2 — keep headers; license copy pattern already used
in resources/):
- `src/main/java/com/questhelper/steps/overlay/DirectionArrow.java` — static world/minimap/
  world-map arrow drawing with off-screen edge-clamping. Depends only on small helper types
  (DefinedPoint, QuestHelperWorldMapPoint) — copy those too.
- `steps/overlay/WorldLines.java` — path line drawing.
- Overlay classes to write fresh (thin): world arrow overlay (OverlayLayer.ABOVE_SCENE,
  DYNAMIC), minimap overlay, world-map point. Drive them from RunOrderService.legs():
  current leg = first unvisited stop; advance when player within ~10 tiles of the stop
  (GameTick, client thread). Object/NPC click highlighting for Phase 5 uses RuneLite's own
  ModelOutlineRenderer / OverlayUtil.renderHoverableArea directly.
- Optional integration: shortest-path PluginMessage API (namespace "shortestpath", actions
  "path"/"clear") to draw exact tile paths when that plugin is installed.

## Known limitations (documented, not bugs)

Rune pouch contents not counted; player-grown spirit trees not modeled as teleports
(Phase 6); walking legs are straight-line estimates (no collision map); POH facilities are
config-declared (house layout unreadable); remote crop state needs the Time Tracking
plugin's stored observations (absent → patches treated as worth visiting); run-items/bank
tab list seeds for all active groups, not just the planned route; a chain's total coin
fare isn't affordability-checked across hops; vendored home-teleport rows use fixed
spellbook destinations while the live spell goes to the respawn point (upstream data —
players with a moved respawn get a slightly wrong home-teleport landing tile).
