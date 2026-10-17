# Roadmap to Wesnoth compatibility

## Implemented in 0.2

- Native Compose UI with original sprites/terrain, movement and combat animation.
- Portrait/landscape layouts, pan/zoom, accessible hexes and contextual unit details.
- Path previews, confirmation, movement undo and weapon/retaliation previews.
- Three skirmish maps, computer opponent and local pass-and-play.
- Terrain/defence, zones of control, friendly traversal, alternating combat, resistance and alignment modifiers.
- Villages, gold/upkeep, recruiting, healing, slow, leadership, XP and single-path advancement.
- Deterministic saves including RNG state, autosave and manual save/load.
- Independent engine tests, Android emulator smoke tests, screenshots, lint and asset checks.

## Next: deeper tactical parity

- Unit-appropriate traits, advancement selection, recall and persistent campaign armies.
- Complete unit profiles, abilities/specials and upstream rounding/edge-case comparison tests.
- Fog, shroud, ambush, illumination, terrain composition and visibility-aware undo.
- Better AI strategy, difficulty settings and performance profiling on real devices.
- Full animation coverage, sound/music, clearer camera following and visual accessibility options.
- Stable release signing and seamless APK upgrades.

## Content compatibility

- Versioned WML parser/schema and a validated subset of scenario/event scripting.
- Maps, factions, eras, campaigns and terrain graphics import.
- Lua compatibility/security model and deterministic event execution.
- First complete campaign with per-file license and provenance checks.

## Full game systems

- Replay format, upstream save compatibility and scenario persistence.
- Multiplayer protocol/service integration and sync checks.
- Add-on management, map editor, localization, TalkBack review and controller support.
- Reproducible signed release builds, F-Droid metadata and store packaging.

Each major addition must include appropriate engine/UI verification and a playable APK. Full compatibility is not implied until documented upstream comparison tests demonstrate it.
