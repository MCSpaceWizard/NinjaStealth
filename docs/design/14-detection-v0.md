# 14 — Detection states & reactions, v0 (Stage 4a) 📄

This is a first, hand-coded slice of the AI behaviour stage, so NPCs actually *react* while perception and light are being built. The full S4 (behaviour trees in data, squads, alarms, propagation) replaces the **decision logic**. The **states, rules and HUD** stay.

## States

| State | When | What the NPC does |
|---|---|---|
| `unaware` | Never alerted (or cooled down) | Idle: wanders and looks around. **No "look at nearby player" goal**, because that would be free information |
| `heightened` | After any incident, for 3 Minecraft days (A-04) | Same as unaware, but awareness fills **1.3×** faster |
| `curious` | Awareness ≥ 0.2 (`noticedThreshold`) | Stops and looks at where it noticed something |
| `suspicious` | Awareness ≥ 0.5 (`suspiciousThreshold`) | Keeps staring; after 1.5 s guards start investigating |
| `investigating` | Suspicious for 1.5 s (guards) | Walks to the last known position and looks around; gives up when awareness decays (max 25 s) |
| `combat` | Detected (awareness 1.0) **and** the target was seen in the last 1.5 s | Vanilla melee against the target |
| `hunting` | Detected but the target slipped out of view | Runs to the **last known position**. Never to where the target really is (D-08) |
| `searching` | Reached the last known position, nothing there | Sweeps random spots within 8 blocks for `searchSeconds` (25 s), then goes back to `heightened` |
| `fleeing` | A non-guard (civilian, worker, target) detects you | Runs away; calms down 12 s after losing sight |

## Stimuli

- **Sight**, from perception: awareness rises and the last known position updates **only while you're seen**.
- **Melee hits** reveal the attacker: instant detection → combat for guards, flee for civilians.
- **Arrow hits** only reveal a direction: the NPC guesses a spot ~8 blocks back along the arrow's path (±2 blocks), becomes suspicious and investigates. This rewards ranged attacks from hiding.

## HUD

- **Above-head indicator** (D-04):
  - a "? ■■□□□" meter that fills white → yellow → orange as an NPC's awareness of *you* grows
  - an orange **?** while it searches for you
  - a red **!** once you're detected
- **Alert sounds** (placeholders, vanilla note-block chime/bell, each with a subtitle): one plays when an NPC becomes suspicious of you, one when you're detected.
- Both are toggles in the client config (`showDetectionIndicators`, `playAlertSounds`).
- The server only tells each player about awareness of **themselves** (M-01: no free information in co-op).

## Known v0 limitations (fixed in later stages)

- No hearing yet (S6), so NPCs never investigate noises.
- No information sharing between NPCs, alarms or zones (S9).
- No patrols or routines (S5). Idle NPCs just wander.
- Vanilla pathfinding: NPCs open wooden doors, but nothing smarter yet.
- Combat is vanilla melee only (S13).
