"""
Generates the scoreboard "real-equipment" cue sounds for watchthetime.

All sounds are synthesised (no recordings, no licences): arena horn, referee whistle,
scorer's-table beeps. Output: 16-bit mono WAV at 22.05 kHz in
android/core-feedback/src/main/res/raw/cue_<name>.wav (one file per CueType).

    python tools/gen_sounds.py

Requires numpy.
"""
from __future__ import annotations

import os
import wave

import numpy as np

SR = 22050
OUT = os.path.join(os.path.dirname(__file__), "..", "android", "core-feedback", "src", "main", "res", "raw")
rng = np.random.default_rng(7)  # deterministic output


def t(dur: float) -> np.ndarray:
    return np.arange(int(SR * dur)) / SR


def env(n: int, attack: float = 0.005, release: float = 0.02) -> np.ndarray:
    """Linear attack/release envelope (avoids clicks)."""
    e = np.ones(n)
    a = max(1, int(SR * attack))
    r = max(1, int(SR * release))
    e[:a] = np.linspace(0, 1, a)
    e[-r:] = np.minimum(e[-r:], np.linspace(1, 0, r))
    return e


def silence(dur: float) -> np.ndarray:
    return np.zeros(int(SR * dur))


def beep(freq: float, dur: float, square: float = 0.35, vol: float = 0.8) -> np.ndarray:
    """Scorer's-table piezo beep: sine blended with a soft square for that buzzy edge."""
    x = t(dur)
    s = np.sin(2 * np.pi * freq * x)
    sq = np.tanh(6 * s)
    return vol * ((1 - square) * s + square * sq) * env(len(x))


def sweep(f0: float, f1: float, dur: float, vol: float = 0.7) -> np.ndarray:
    x = t(dur)
    f = np.linspace(f0, f1, len(x))
    phase = 2 * np.pi * np.cumsum(f) / SR
    return vol * np.sin(phase) * env(len(x), 0.004, 0.03)


def horn(dur: float, base: float = 220.0, vol: float = 0.85) -> np.ndarray:
    """Arena buzzer: detuned sawtooth stack through a soft clipper, slight vibrato."""
    x = t(dur)
    out = np.zeros_like(x)
    for detune in (1.0, 1.006, 0.995, 2.0, 3.01):
        f = base * detune * (1 + 0.003 * np.sin(2 * np.pi * 6 * x))
        phase = np.cumsum(f) / SR
        saw = 2 * (phase - np.floor(phase + 0.5))
        out += saw / (1.0 if detune < 1.5 else detune)
    out = np.tanh(1.8 * out / 3)
    return vol * out / np.max(np.abs(out)) * env(len(x), 0.01, 0.08)


def whistle(dur: float, freq: float = 2900.0, trill: float = 28.0, vol: float = 0.75) -> np.ndarray:
    """Pea whistle: carrier with fast pea warble (FM + AM) and breath noise."""
    x = t(dur)
    fm = freq * (1 + 0.04 * np.sin(2 * np.pi * trill * x))
    phase = 2 * np.pi * np.cumsum(fm) / SR
    tone = np.sin(phase) + 0.25 * np.sin(2 * phase)
    am = 0.75 + 0.25 * np.sin(2 * np.pi * trill * x)
    noise = rng.normal(0, 0.12, len(x))
    # crude band-limit of the breath noise
    noise = np.convolve(noise, np.ones(4) / 4, mode="same")
    y = (tone * am + noise) / 1.4
    return vol * y * env(len(x), 0.02, 0.06)


def click(vol: float = 0.6) -> np.ndarray:
    x = t(0.025)
    return vol * np.sin(2 * np.pi * 1800 * x) * np.exp(-x * 180)


def seq(*parts: np.ndarray) -> np.ndarray:
    return np.concatenate(parts)


def normalise(y: np.ndarray, peak: float = 0.9) -> np.ndarray:
    m = np.max(np.abs(y))
    return y if m == 0 else y * (peak / m)


SOUNDS: dict[str, np.ndarray] = {
    # Clock
    "clock_start": seq(beep(1320, 0.07), silence(0.02), beep(1760, 0.09)),
    "clock_stop": seq(beep(1760, 0.07), silence(0.02), beep(1100, 0.11)),
    "clock_adjust": click(),
    "last_minute": seq(beep(1500, 0.08), silence(0.06), beep(1500, 0.08), silence(0.06), beep(1500, 0.08)),
    "period_end": horn(1.6, 220),
    "game_final": seq(horn(1.2, 220), silence(0.15), horn(1.8, 196)),
    "period_advance": seq(beep(880, 0.10, 0.1), beep(1175, 0.10, 0.1), beep(1568, 0.16, 0.1)),
    # Shot clock: higher, shorter horn so it can't be confused with the game buzzer
    "shot_clock_expired": horn(0.9, 330),
    "shot_clock_reset": seq(click(0.5), beep(2100, 0.04, 0.2, 0.5)),
    # Scoring: N pips for N points
    "score_1": beep(1050, 0.08),
    "score_2": seq(beep(1050, 0.07), silence(0.05), beep(1050, 0.07)),
    "score_3": seq(beep(1050, 0.06), silence(0.04), beep(1050, 0.06), silence(0.04), beep(1400, 0.10)),
    # Fouls: whistles
    "foul": whistle(0.45),
    "foul_flag": seq(whistle(0.35), silence(0.08), whistle(0.6, 3100)),
    "foul_warning": seq(whistle(0.3), silence(0.05), beep(700, 0.18, 0.5)),
    "foul_out": seq(whistle(0.25), silence(0.05), whistle(0.25), silence(0.05), whistle(0.9, 3100, 34)),
    "bonus": seq(beep(988, 0.09), silence(0.03), beep(784, 0.09), silence(0.03), beep(988, 0.09), silence(0.03), beep(784, 0.12)),
    # Timeouts
    "timeout_start": seq(whistle(0.35), silence(0.06), beep(660, 0.25, 0.5)),
    "timeout_end": seq(horn(0.35, 262), silence(0.12), horn(0.35, 262)),
    # Editing
    "undo": sweep(1400, 700, 0.14),
    "redo": sweep(700, 1400, 0.14),
    "edit_saved": seq(click(0.4), beep(1600, 0.05, 0.1, 0.4)),
    "error": seq(beep(180, 0.14, 0.8), silence(0.05), beep(180, 0.14, 0.8)),
    # --- Added for clock modes / landscape redesign. Keep new entries at the END: the whistle
    # noise uses a seeded RNG in order, so appending keeps every earlier file byte-identical.
    # Mode changed: up-down three-note chirp (no other cue goes up then down).
    "mode_changed": seq(beep(660, 0.08, 0.2), beep(990, 0.08, 0.2), beep(660, 0.12, 0.2)),
    # Timeout warning (FIBA signal before the end of a time-out): one short mid horn.
    "timeout_warning": horn(0.25, 294),
    # Interval over: medium horn + high pip (shorter and higher than the period-end horn).
    "interval_end": seq(horn(0.5, 247), silence(0.1), beep(1320, 0.12)),
    # Substitution: hi-lo-hi pips.
    "substitution": seq(beep(1200, 0.05), silence(0.03), beep(900, 0.05), silence(0.03), beep(1200, 0.05)),
    # Free throws: short whistle then two pips.
    "free_throws": seq(whistle(0.2, 2600), silence(0.05), beep(1050, 0.06), silence(0.08), beep(1050, 0.06)),
}


def write(name: str, y: np.ndarray) -> str:
    os.makedirs(OUT, exist_ok=True)
    path = os.path.normpath(os.path.join(OUT, f"cue_{name}.wav"))
    pcm = (normalise(y) * 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    return path


if __name__ == "__main__":
    for n, y in SOUNDS.items():
        p = write(n, y)
        print(f"{os.path.basename(p):28s} {len(y) / SR:5.2f}s")
