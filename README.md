# Better Farming

A [RuneLite](https://runelite.net/) plugin that helps Old School RuneScape players run farming routes: pick which patches you've unlocked, get an optimized run order, and (in later phases) walking-path overlays and per-patch step guidance.

This is a work-in-progress hobby project.

## Status

| Phase | Description | Status |
| --- | --- | --- |
| 0 | Plugin scaffold + farming dataset (patches + seeds) | done |
| 1 | Sidebar with patch listing, grouping & requirement locks | done |
| 1.5 | Per-patch seed selection | done |
| 2 | Equipment manager: run-items list (tools, seeds/saplings, payments) with on-player / in-bank / missing states | done |
| 2.5 | Farming bank tab: withdraw run items from a dedicated bank side-tab | done |
| 3 | Optimal run routing from unlocked teleports (transport data + availability checks) | done |
| 4 | Guidance between patches: world/minimap arrows, world-map marker + route, travel hints, Shortest Path integration | done |
| 5 | Per-patch step guidance (rake → plant → harvest state machine) & click highlights at patches | planned |
| 6+ | Polish: compost/protection tracking, agility shortcuts, diary boosts | planned |

## Sideload (development)

```bash
./gradlew shadowJar
```

The plugin jar lands in `build/libs/`. Drop it into your RuneLite `~/.runelite/sideloaded-plugins/` directory (create it if missing) and restart RuneLite.

To run a RuneLite client with the plugin pre-loaded for testing:

```bash
./gradlew run
```

## License

BSD-2-Clause. See [LICENSE](LICENSE).
