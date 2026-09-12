# Wesnoth Android

A touch-first, native Android reimplementation inspired by **The Battle for Wesnoth**. This repository is an early playable vertical slice, not yet a drop-in replacement for the upstream game.

## What runs today

- Native Kotlin and Jetpack Compose interface
- Axial hex map with terrain
- Unit selection and highlighted movement range
- Terrain-weighted pathfinding and movement points
- Adjacent combat with terrain defence, strikes, HP, death, and victory
- Alternating turns and deterministic engine tests
- Landscape layout designed for phones and tablets

## Build

Use Android Studio with JDK 17 and Android SDK 37, or Gradle 9.6+:

```bash
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Project principles

The rules engine lives outside the UI layer, stays deterministic under a seeded random source, and will remain data-driven. Imported upstream content must include provenance and its original license metadata.

See [ROADMAP.md](ROADMAP.md) for the staged parity plan and [NOTICE.md](NOTICE.md) for licensing guidance.

## License

Copyright (C) 2026 Domokos Kovacs and contributors.

Licensed under GNU GPL version 2 or (at your option) any later version. See [LICENSE](LICENSE). “The Battle for Wesnoth” and upstream content belong to their respective owners. This prototype currently contains no copied upstream art, music, campaign text, or code.
