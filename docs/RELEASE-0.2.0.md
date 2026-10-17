# A playable frontier skirmish

Download **wesnoth-android-0.2.0.apk**, open it on an Android 8.0+ phone, and tap Install.

If you installed 0.1.0, uninstall that earlier prototype first. These development APKs use CI-generated debug certificates, so Android may reject an in-place update. This removes the earlier prototype's local data.

- 44 original Wesnoth unit sprites, animation frames and terrain textures, bundled for offline use.
- A responsive portrait/landscape interface, pan/zoom, movement previews, confirmation and undo.
- Weapon selection with hit chances, expected strike damage and retaliation previews.
- Three maps, computer opponents or pass-and-play, recruiting and castle/keep rules.
- Village capture, income/upkeep, healing, time of day, zones of control, slow, leadership and XP advancement.
- Autosave, manual save/load, a field guide, and in-app attribution/licenses.

The Kotlin engine is tested separately; Android emulator tests exercise movement, combat selection, recruitment, save/load, rotation and an AI turn. This remains an early skirmish implementation. Full WML/Lua campaigns, add-ons, multiplayer compatibility, trait generation, advancement choice and complete upstream rules parity are still pending.

Complete corresponding source and build scripts accompany the APK. New code is GPL-2.0-or-later. Unmodified original art is GPL-2.0-or-later or CC-BY-SA-4.0 per `app/src/main/assets/asset-provenance.json`; both license texts and original authors are included. `SHA256SUMS` contains installer/source checksums.
