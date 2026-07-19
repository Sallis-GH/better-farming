# Better Farming — agent notes

RuneLite plugin for OSRS farming runs. Owner: Sallis (salamon@newcommerce.no, GitHub Sallis-GH).
Target: RuneLite Plugin Hub eventually — keep licensing clean (repo is BSD-2), no automation-adjacent behavior.
V1 scope: herb + tree + fruit-tree runs. UI/design polish is deliberately deferred until the plugin is feature-complete.

## Status (see README table)

Phases 0–3.x are DONE and merged: dataset, sidebar with locks + seed selection, equipment
manager (run items with on-player/in-bank/missing), farming bank tab, teleport-aware
optimal routing with granular POH config, barbarian bare-handed planting, outfit gear
sections, tab-over-runes preference.

NEXT: **Phase 4 — guidance arrows & click highlights** (port quest-helper's DirectionArrow/
WorldLines primitives + overlays driven by RunOrderService legs).
THEN: **Phase 5 — per-patch step guidance** (rake→plant→harvest state machine off farming
varbits; port the varbit mappings from RuneLite core's timetracking plugin — also enables
planted-spirit-tree detection and skip-growing-patches routing).

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
  Skretzo/shortest-path, BSD-2; network files = entry-tiles × requirement-carrying exits),
  `TeleportAvailabilityService` (live quest/varbit/boosted-skill/item filtering; POH
  facilities gated by granular config + composed house-chain edges), `RoutePlanner` (pure;
  Held-Karp ≤13 stops, 2-opt beyond; 2-tick ties → fewer inventory slots win; POH bias 20
  ticks), `RunOrderService` (recomputes on client thread via injected executor),
  `PohPortal` (generated enum: TSV display name → config checkbox).
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
5. Data files are wiki-verified by research agents writing JSON, merged by script —
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

Rune pouch contents not counted; player-grown spirit trees not modeled (Phase 5); walking
legs are straight-line estimates (no collision map); POH facilities are config-declared
(house layout unreadable).
