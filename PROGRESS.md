# watchthetime: progress and handoff

Last updated: 2026-10-08

## Decisions made (by the user)
- **Build the MOBILE app only for now.** The watch app (Wear OS) is deferred. Apple Watch is not planned.
- The mobile app must **run a full game by itself** (clock, score, fouls, timeouts, sounds and haptics), not just act as a companion.
- Stack: native Android, Kotlin + Jetpack Compose (game logic is in a Kotlin Multiplatform module, so iOS can reuse it later).
- Default rules preset: **NBA** (4x12 min, foul-out at 6, bonus from the 5th team foul, last-2-minutes rule, 24/14 shot clock).
- Editing during a live game is allowed.
- Sounds: generated "real-equipment" sounds (buzzer, whistle, scorer's-table beeps). No licensed recordings.
- Design: arena scoreboard style. Black #0A0A0A, off-white #EFEBE3, amber #FF8A00, alert red #D7262B.
  DSEG7 seven-segment digits and Barlow Condensed. Square corners, no gradients, glow or glass effects, custom-drawn icons.

## Done
- `android/` Gradle project, version catalog `gradle/libs.versions.toml`, wrapper (Gradle 8.14.3)
  - AGP 8.13.1, Kotlin 2.2.20, KSP 2.2.20-2.0.4, Compose BOM 2025.09.00, Room 2.8.0
- `android/core-domain` (pure Kotlin, finished and tested):
  - `event/GameEvent.kt`: event-sourced events (scores, fouls, clock, periods, shot clock, timeouts, roster, rules)
  - `engine/Reducer.kt`: rebuilds game state from the event list
  - `engine/GameSession.kt`: single writer: commands → events, tick() for buzzer/shot clock/timeout end, undo/redo, merge
  - `engine/Command.kt`, `Cues.kt` (every sound/haptic moment), `ClockFormat.kt`, `EventText.kt`, `EventMerge.kt`
  - `state/GameState.kt`: drift-free clock, fouls, bonus/double bonus, timeouts, foul-out
  - `rules/Rules.kt`: NBA / FIBA / NCAA / NFHS / Custom presets
  - `stats/BoxScore.kt`: box score and CSV export
  - `settings/AppSettings.kt`: feedback, gesture and display settings
  - `sync/Wire.kt`: JSON format (for the future watch sync)
- **36 unit tests pass**: `ClockTest`, `FoulRulesTest`, `EditUndoRecomputeTest`
- Fonts downloaded to `android/core-brand/src/main/res/font/`; licences in `docs/licenses/`

## Next steps (mobile app)
1. `core-brand`: colours, typography, custom icon paths (whistle, flag, stopwatch, undo, pencil)
2. `core-data`: Room DB (events table, games index, saved teams/rosters, settings in DataStore)
3. `core-feedback`: SoundPool + Vibrator, one distinct sound and vibration pattern per CueType; earbud routing
4. Sound generation script (`tools/gen_sounds.py`) → `res/raw/*.ogg|wav`
5. `phone` app screens:
   - Games/history, new game wizard (preset → teams → rosters), teams and rosters (CSV import)
   - **Live game**: big clock, shot clock, +1/+2/+3 per team, undo, fouls with jersey picker and foul type, timeouts, period control
   - Event log (edit/delete/restore/insert), box score, PDF/CSV export via share sheet, settings
   - Foreground service so the clock keeps running when the screen is off
6. Docs: sound/haptic table, gesture table, setup, assumptions
7. Build `:phone:assembleDebug` and run on an emulator

## How to run what exists
```powershell
cd D:\watchthetime\android
.\gradlew.bat :core-domain:jvmTest
```
Report: `android/core-domain/build/reports/tests/jvmTest/index.html`

## Environment notes
- JDK 21 on PATH; Android SDK at `C:\Users\janak\AppData\Local\Android\Sdk` (platforms 34–36.1)
- `android/local.properties` points to the SDK
