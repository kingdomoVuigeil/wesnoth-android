# Roadmap to Wesnoth compatibility

“All mechanics” is treated as a testable compatibility program rather than a single milestone.

## Phase 1 — playable foundation (current)

- [x] Native Compose shell and touch-first board
- [x] Hex coordinates, terrain, weighted movement, turns, attacks, victory
- [x] Pure Kotlin rules engine and deterministic tests
- [ ] Correct cube rounding for taps, pan/zoom, responsive portrait layout
- [ ] Save/load, recruit dialog, villages, gold and income

## Phase 2 — core tactical parity

- Zones of control, alignment/time-of-day modifiers, resistances
- Ranged/melee retaliation, weapon specials, poison, slow and healing
- Experience, advancement, AMLA, traits and abilities
- Fog, shroud, undo rules, recall and scenario objectives
- Golden tests against documented upstream outcomes

## Phase 3 — content compatibility

- Versioned WML parser and schema
- Terrain graphics composition and animation pipeline
- Unit, faction, era, map and scenario importers
- Per-file SPDX/provenance manifest for every imported asset
- First complete GPL/CC-compliant campaign

## Phase 4 — full game systems

- Deterministic AI, replay format and hot-seat multiplayer
- Campaign persistence, Lua/WML events and add-on sandbox
- Network protocol research and multiplayer service
- Map editor, add-on manager, localization and accessibility

## Phase 5 — release quality

- Performance profiles on low/mid/high-tier Android devices
- Foldable/tablet layouts, TalkBack, keyboard/controller support
- Signed reproducible builds, F-Droid metadata and Play packaging
- Compatibility matrix against the supported upstream Wesnoth release

Each phase requires automated engine tests, UI smoke tests, license audit, and a playable APK before it is considered complete.
