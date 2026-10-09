# Rules of WatchTheTime

The app ships rule presets. Every preset value maps to a rulebook article; where a rulebook
is silent or ambiguous the choice made is listed below and can be changed in the **Custom**
preset (or by editing saved games' rules).

Preset values live in
`android/core-domain/src/commonMain/kotlin/com/watchthetime/domain/rules/Presets.kt`
and are covered by `RulesEngineTest.kt`.

## Sources

| Preset | Rulebook | Source |
|---|---|---|
| FIBA | Official Basketball Rules 2026 (valid 1 Oct 2026) | [fib.basketball rules](https://www.fib.basketball/en/rules) |
| NBA | NBA Official Rules 2026-27 | [nba.com rulebook PDF](https://pr.nba.com/wp-content/uploads/2023/11/NBA-Official-Rulebook-2023-24.pdf) (2026-27 edition used during research) |
| NCAA men | Men's Basketball Rules 2026-27 | NCAA Publications |
| NCAA women | Women's Basketball Rules 2025-26 / 2026-27 | NCAA Publications |
| FIBA 3x3 | 3x3 Basketball Rules (valid 1 Jan 2026) | [fib.basketball 3x3 rules](https://www.fib.basketball/en/3x3/rules) |

NFHS (US high school) was previously offered; it is hidden for new games (`selectable = false`)
but old games that used it keep working with the legacy schema.

---

## FIBA (2026)

| Value | Rule |
|---|---|
| 4 × 10 min, OT 5 min | Art. 8.1, 8.7 |
| Interval 2 min, half-time 15 min, OT interval 2 min | Art. 8.3, 8.4 |
| Player foul limit 5, warning at 4 | Art. 41.1 |
| Team fouls per period; bonus from the 5th team foul | Art. 41.1, 42.1.1 |
| Technical fouls: cat. 1 and cat. 2 (2026 split); both count as personal and team fouls; 1 FT | Art. 36.2.1, 36.3.1-2 |
| Unsportsmanlike → **flagrant** (2026): 2 FT + possession | Art. 38.2.2 |
| **Disruptive** foul (new 2026): 2 FT + possession | Art. 37.2.2 |
| **Disqualifying** foul (new 2026): immediate ejection | Art. 39.3 |
| Ejection: 2 cat. 1 technicals / flagrants, or 1 disqualifying | Art. 36.2.3, 38.2.3, 39.3.2 |
| One cat. 2 technical + one disruptive + one cat. 1 technical: still playing (all three count toward 5) | FIBA Interpretation 36-27 |
| Timeouts 60 s: 2 first half, 3 second half, 1 per OT, none carried | Art. 18.2.1, 18.2.5-6 |
| ≤ 2:00 of Q4: max 2 timeouts | Art. 18.2.5 |
| Timeout warning at 50 s remaining | Art. 50.3 |
| 24 s shot clock, 14 s on offensive rebound | Art. 29.1.1, 29.2 |
| Shot clock off when game clock shows less | Art. 51.5 / Interp. 29/51-52 |
| Tenths under 1:00 | Equipment rule 3.3 |
| Scores stop the clock in the last 2:00 (setting: every score or late only) | Art. 50.2 |

## NBA (2026-27)

| Value | Rule |
|---|---|
| 4 × 12 min, OT 5 min | Rule 5-II |
| Interval 2:30, half-time 15 min | Rule 5-II(c)(d) |
| Player foul limit 6, warning at 5 | Rule 3-I(a) |
| Offensive foul: personal but **not** a team foul | 12B-VII |
| Technicals do not count as personal or team fouls; 1 FT | 12A-V |
| Flagrant 1 / 2; ejections: 2 unsportsmanlike technicals, 2 F1, or 1 F2 | 12A-V(b), 12B-IV |
| Team fouls per quarter; bonus at 5, last two minutes of Q4 rule | 12B-V(a) |
| Timeouts 75 s: 7 per half (carry within the half), 2 per OT | 5-VI(a)(b) |
| Q4 / OT caps: max 4 after 12:00, max 2 after 3:00 | 5-V(a) |
| 24 s shot clock, 14 s on offensive rebound; off when game clock is lower | Rule 7, 7-II(i) |
| Scores stop the clock last 2:00 of Q4/OT (plus last 1:00 of any other period) | 5-V(b) |

**Not modelled / uncertain (NBA):**
- Whether a timeout carries into overtime is not stated in the rulebook; the preset does
  **not** carry unused timeouts into OT.
- Media timeouts are not modelled (non-media timeouts only).

## NCAA Men's (2026-27)

| Value | Rule |
|---|---|
| 2 × 20 min, OT 5 min | 5-6.1 |
| Half-time 15 min, OT interval 1 min | 5-6.1 |
| Player foul limit 5, warning at 4 | 4-12.1 |
| Team fouls per half; 1-and-1 from the 7th, double bonus from the 10th | 8-2.1, 8-2.2 |
| Class A technical: personal + team foul, 2 FT; Class B: 1 FT, no team foul | 10-3, 10-4 |
| Ejection: AA / ABB / BBB technical combinations, flagrant 2, 3 flagrant 1s | 10-3, 10-4, 4-15.2 |
| Timeouts 75 s: 4 per half (carry within half + into OT), 1 per OT; 2 × 30 s shorts | 5-14.4, 5-14.8 |
| 30 s shot clock, 20 s on offensive rebound; off when lower | 2-11, 2-11.2 |
| Scores stop the clock under 1:00 | 5-11.9 |

## NCAA Women's (2025-26 / 2026-27)

| Value | Rule |
|---|---|
| 4 × 10 min, OT 5 min | 5-6.1 |
| Interval 1:15, half-time 15 min, OT interval 1 min | 5-6.1 |
| Player foul limit 5 | 4-12.1 |
| Team fouls per quarter; bonus 2 FT from the 5th (no one-and-one) | 8-2.1 |
| Technical: personal + team foul, 2 FT + possession; admin technical separate | 8-2.2, 10-12 |
| Timeouts 60 s: 2 per half, 3 × 30 s shorts, 1 short per OT | 5-14.5, 5-14.9 |
| 30 s shot clock, 20 s offensive rebound | 2-11 |

## FIBA 3x3 (2026)

| Value | Rule |
|---|---|
| 1 × 10 min, first to 21 wins | Art. 1.2, 8.1, 9.5 |
| OT: untimed, first team to score 2 points wins | Art. 8.5, 9.6 |
| 1 min interval before OT | Art. 8.2 |
| No player foul-out; 2 unsportsmanlike → disqualification | Art. 41.1.3, 37.2.5 |
| Team fouls accumulate the **whole game**: 7th → bonus 2 FT; 10th → 2 FT + possession | Art. 41.1.1, 41.2.1 |
| Unsportsmanlike foul counts as 2 team fouls | Art. 37.2.2 |
| 1 × 30 s timeout; unused timeout carries into OT | Art. 18.2.1, 18.2.7 |
| 12 s shot clock | Art. 29.1.1 |
| Ball stays live after a made basket (running clock; no score stop) | Art. 50.4 area |

**Not modelled (3x3):** check-ball procedure details; halftime does not exist (single period).

---

## Clock modes (app feature, not a rulebook rule)

Two modes, chosen per game:

- **Stopping** — the clock stops on every score by default (option: only in the official
  late window of the chosen preset).
- **Running** — the clock keeps running after baskets; in the late window it switches to
  stopping (NBA: last 1:00 of Q4/OT; FIBA-style: last 2:00).

Timeouts always stop the clock in every mode. Mode changes are logged as undoable events.

## Legacy saved games

Games created before the rules engine used scalar flags (e.g. `technicalCountsAsPersonal`).
`Rules.validated()` converts those flags into an equivalent foul table, so old games keep
fouling out / team-foul behaviour they were recorded under (FIBA 2024-style: T + U ejects).

## What is not modelled (any preset)

- Defensive three seconds (NBA), goaltending call details, lane violation mechanics.
- Coach/assistant bench-clearing penalties beyond the technical foul entry.
- Media timeouts, commercial intervals, live-broadcast clock procedures.
- Rule differences for exhibition / youth / wheelchair games.
