# Better Farming

A [RuneLite](https://runelite.net/) plugin that helps Old School RuneScape players run farming routes: pick which patches you've unlocked, get an optimized run order, and (in later phases) walking-path overlays and per-patch step guidance.

This is a work-in-progress hobby project. Phase 0 ships only the data foundation — no UI yet.

## Status

| Phase | Description | Status |
| --- | --- | --- |
| 0 | Plugin scaffold + farming dataset (patches + seeds) | in progress |
| 1 | Sidebar with patch listing & user customization | planned |
| 2 | Per-patch seed selection | planned |
| 3 | Run profiles (ordered patch subsets) | planned |
| 4 | Path guidance between patches | planned |
| 5 | Per-patch step guidance | planned |
| 6+ | Polish: auto-detect player state, agility shortcuts, diary boosts | planned |

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
