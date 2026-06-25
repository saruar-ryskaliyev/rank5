#!/usr/bin/env python3
"""Deterministically synthesize the Rank5 Android sound pack.

Outputs mono 48 kHz Ogg Vorbis files into android/app/src/main/res/raw and a
machine-readable QA manifest into docs/audio. The palette intentionally uses
short modeled marimba, ceramic/wood, paper, and glass-like sounds rather than
sample-library material, so the pack is original and fully reproducible.
"""

from __future__ import annotations

import json
import hashlib
import math
from pathlib import Path

import numpy as np
import soundfile as sf


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "android/app/src/main/res/raw"
DOCS = ROOT / "docs/audio"
SR = 48_000
RNG = np.random.default_rng(5_050_505)

NOTES = {
    "A3": 220.00,
    "C4": 261.63,
    "D4": 293.66,
    "E4": 329.63,
    "F4": 349.23,
    "G4": 392.00,
    "A4": 440.00,
    "Bb4": 466.16,
    "B4": 493.88,
    "C5": 523.25,
    "D5": 587.33,
    "E5": 659.25,
    "F5": 698.46,
    "G5": 783.99,
    "A5": 880.00,
    "Bb5": 932.33,
    "B5": 987.77,
    "C6": 1046.50,
    "D6": 1174.66,
    "E6": 1318.51,
}


def samples(seconds: float) -> int:
    return max(1, int(round(seconds * SR)))


def fade_edges(x: np.ndarray, fade_in: float = 0.002, fade_out: float = 0.008) -> np.ndarray:
    y = x.copy()
    ni = min(samples(fade_in), len(y))
    no = min(samples(fade_out), len(y))
    if ni:
        y[:ni] *= np.sin(np.linspace(0, math.pi / 2, ni)) ** 2
    if no:
        y[-no:] *= np.cos(np.linspace(0, math.pi / 2, no)) ** 2
    return y


def exp_decay(duration: float, attack: float, decay: float, floor: float = 0.0) -> np.ndarray:
    n = samples(duration)
    t = np.arange(n) / SR
    env = np.exp(-np.maximum(0.0, t - attack) / max(decay, 1e-4))
    if attack > 0:
        a = np.clip(t / attack, 0.0, 1.0)
        env *= np.sin(a * math.pi / 2) ** 2
    if floor:
        env = floor + (1.0 - floor) * env
    return env


def band_noise(duration: float, center: float, width: float, amp: float = 1.0) -> np.ndarray:
    n = samples(duration)
    raw = RNG.normal(0.0, 1.0, n)
    spectrum = np.fft.rfft(raw)
    freqs = np.fft.rfftfreq(n, 1.0 / SR)
    shape = np.exp(-0.5 * ((freqs - center) / max(width, 1.0)) ** 2)
    y = np.fft.irfft(spectrum * shape, n)
    peak = np.max(np.abs(y)) or 1.0
    return amp * y / peak


def marimba(freq: float, duration: float, brightness: float = 0.35, softness: float = 1.0) -> np.ndarray:
    n = samples(duration)
    t = np.arange(n) / SR
    # Slightly inharmonic upper modes keep it tactile instead of synth-pure.
    partials = [(1.0, 1.0, 0.055), (2.01, 0.26 * brightness, 0.031),
                (3.93, 0.13 * brightness, 0.023), (6.10, 0.06 * brightness, 0.017)]
    y = np.zeros(n)
    phase = 0.08
    for ratio, level, decay in partials:
        y += level * np.sin(2 * math.pi * freq * ratio * t + phase) * np.exp(-t / decay)
        phase += 0.37
    mallet = band_noise(duration, min(2800.0, freq * 4.0), 1100.0, 0.12 * softness)
    mallet *= np.exp(-t / 0.006)
    y = y * exp_decay(duration, 0.0035 * softness, 0.090) + mallet
    return fade_edges(np.tanh(y * 0.82), 0.0015, 0.012)


def glass(freq: float, duration: float, brightness: float = 0.5) -> np.ndarray:
    n = samples(duration)
    t = np.arange(n) / SR
    modes = [(1.0, 0.72, 0.13), (2.32, 0.18 * brightness, 0.10),
             (3.71, 0.11 * brightness, 0.075), (5.43, 0.06 * brightness, 0.055)]
    y = np.zeros(n)
    for i, (ratio, level, decay) in enumerate(modes):
        y += level * np.sin(2 * math.pi * freq * ratio * t + i * 0.61) * np.exp(-t / decay)
    return fade_edges(y * exp_decay(duration, 0.004, 0.16), 0.002, 0.018)


def wood(freq: float, duration: float, firmness: float = 0.5) -> np.ndarray:
    n = samples(duration)
    t = np.arange(n) / SR
    body = (
        np.sin(2 * math.pi * freq * t)
        + 0.46 * np.sin(2 * math.pi * freq * 1.47 * t + 0.5)
        + 0.18 * np.sin(2 * math.pi * freq * 2.18 * t + 1.1)
    ) * np.exp(-t / (0.026 + firmness * 0.018))
    strike = band_noise(duration, 1150 + firmness * 900, 900, 0.32)
    strike *= np.exp(-t / (0.003 + firmness * 0.002))
    return fade_edges(np.tanh((body * 0.65 + strike) * 0.9), 0.0008, 0.010)


def paper(duration: float, lift: bool = True, color: float = 0.0) -> np.ndarray:
    n = samples(duration)
    t = np.arange(n) / SR
    center = (1550.0 + 1000.0 * t / max(duration, 1e-4)) if lift else (2500.0 - 900.0 * t / max(duration, 1e-4))
    # Blend two fixed bands to imply motion without harsh broadband hiss.
    low = band_noise(duration, 1450 + color * 120, 720, 0.65)
    high = band_noise(duration, 2650 + color * 180, 980, 0.55)
    blend = np.clip((center - 1450.0) / 1200.0, 0.0, 1.0)
    y = low * (1.0 - blend) + high * blend
    shape = np.sin(np.linspace(0, math.pi, n)) ** 1.6
    return fade_edges(y * shape * 0.52, 0.006, 0.014)


def airy(duration: float, rising: bool = True) -> np.ndarray:
    n = samples(duration)
    t = np.arange(n) / SR
    a = band_noise(duration, 3100 if rising else 2400, 1250, 0.22)
    shimmer = np.sin(2 * math.pi * (880 + (240 if rising else -130) * t / duration) * t)
    return fade_edges((a + shimmer * 0.07) * np.sin(np.linspace(0, math.pi, n)) ** 1.8, 0.020, 0.045)


def add(canvas: np.ndarray, sound: np.ndarray, at: float, gain: float = 1.0) -> None:
    start = samples(at)
    if start >= len(canvas):
        return
    end = min(len(canvas), start + len(sound))
    canvas[start:end] += sound[: end - start] * gain


def room(canvas: np.ndarray, amount: float = 0.08) -> np.ndarray:
    dry = canvas.copy()
    for delay, gain in ((0.018, 0.52), (0.031, 0.28), (0.047, 0.13)):
        d = samples(delay)
        canvas[d:] += dry[:-d] * gain * amount
    return canvas


def finish(x: np.ndarray, peak_db: float, room_amount: float = 0.0) -> np.ndarray:
    y = room(x.copy(), room_amount) if room_amount else x.copy()
    # Keep content centered on phone speakers and remove DC without adding bass.
    y -= float(np.mean(y))
    y = np.tanh(y * 1.07) / math.tanh(1.07)
    y = fade_edges(y, 0.001, 0.010)
    peak = float(np.max(np.abs(y))) or 1.0
    target = 10 ** (peak_db / 20.0)
    return (y * target / peak).astype(np.float32)


def canvas(duration: float) -> np.ndarray:
    return np.zeros(samples(duration), dtype=np.float64)


def tonal_sequence(notes: list[str], duration: float, spacing: float, note_len: float,
                   glass_mix: float = 0.18, start: float = 0.015) -> np.ndarray:
    x = canvas(duration)
    for i, note in enumerate(notes):
        f = NOTES[note]
        at = start + i * spacing
        add(x, marimba(f, note_len, 0.35 + i * 0.025), at, 0.72)
        if glass_mix:
            add(x, glass(f * 2, note_len * 0.9, 0.42), at + 0.004, glass_mix)
    return x


def build() -> dict[str, tuple[np.ndarray, float, str]]:
    cues: dict[str, tuple[np.ndarray, float, str]] = {}

    def put(name: str, x: np.ndarray, peak: float, family: str, ambience: float = 0.0) -> None:
        cues[name] = (finish(x, peak, ambience), peak, family)

    # Lobby and selection family.
    for i, (root, top, tint) in enumerate((("D5", "A5", -0.2), ("E5", "B5", 0.0), ("C5", "G5", 0.2)), 1):
        x = canvas(0.180)
        add(x, marimba(NOTES[root], 0.150, 0.25), 0.006, 0.78)
        add(x, marimba(NOTES[top], 0.110, 0.32), 0.065, 0.62)
        add(x, paper(0.060, True, tint), 0.000, 0.13)
        put(f"r5_player_join_{i:02d}", x, -7.5, "lobby", 0.025)

    x = canvas(0.180)
    add(x, marimba(NOTES["G5"], 0.100, 0.18), 0.005, 0.55)
    add(x, marimba(NOTES["D5"], 0.120, 0.15), 0.058, 0.66)
    put("r5_player_leave", x, -11.0, "lobby")

    x = canvas(0.070)
    add(x, wood(1180, 0.055, 0.25), 0.002, 0.42)
    add(x, glass(NOTES["D6"], 0.055, 0.25), 0.004, 0.18)
    put("r5_code_copy", x, -11.5, "utility")

    for i, freq in enumerate((720, 770, 680), 1):
        x = canvas(0.048)
        add(x, wood(freq, 0.043, 0.32), 0.001, 0.56)
        put(f"r5_selection_tick_{i:02d}", x, -12.5, "selection")

    # Three motif studies; A is the canonical note/rhythm identity.
    motif = ["D5", "F5", "G5", "A5", "D6"]
    x = tonal_sequence(motif, 0.650, 0.090, 0.210, 0.11)
    add(x, paper(0.120, True), 0.006, 0.10)
    put("r5_motif_alt_a_paper_steps", x, -6.5, "motif", 0.06)

    x = canvas(0.650)
    for i, note in enumerate(motif):
        add(x, wood(NOTES[note] * 0.72, 0.160, 0.44), 0.015 + i * 0.090, 0.58)
        add(x, marimba(NOTES[note], 0.175, 0.22), 0.020 + i * 0.090, 0.40)
    put("r5_motif_alt_b_ceramic_arc", x, -6.5, "motif", 0.035)

    x = canvas(0.680)
    for i, note in enumerate(motif):
        at = 0.016 + i * 0.092
        add(x, glass(NOTES[note] * 1.5, 0.220, 0.62), at, 0.48)
        if i >= 2:
            add(x, marimba(NOTES[motif[i - 2]], 0.210, 0.18), at, 0.24)
    put("r5_motif_alt_c_glass_bloom", x, -7.0, "motif", 0.09)

    # Canonical game-start asset mirrors motif A with a slightly firmer launch.
    x = tonal_sequence(motif, 0.650, 0.090, 0.205, 0.14)
    add(x, paper(0.090, True), 0.002, 0.12)
    add(x, wood(430, 0.065, 0.20), 0.010, 0.18)
    put("r5_game_start", x, -5.8, "motif", 0.06)

    # Dragging and placement family.
    for i, color in enumerate((-0.35, 0.0, 0.35), 1):
        x = canvas(0.110)
        add(x, paper(0.100, True, color), 0.002, 0.62)
        add(x, wood(420 + i * 28, 0.055, 0.18), 0.006, 0.16)
        put(f"r5_drag_lift_{i:02d}", x, -13.0, "drag")

    for i, freq in enumerate((880, 940, 820, 900), 1):
        x = canvas(0.030)
        add(x, wood(freq, 0.026, 0.15), 0.001, 0.48)
        put(f"r5_rank_cross_{i:02d}", x, -15.0, "drag")

    for i, detune in enumerate((1.0, 1.035), 1):
        x = canvas(0.180)
        add(x, paper(0.110, False, (i - 1.5) * 0.2), 0.000, 0.48)
        add(x, wood(360 * detune, 0.120, 0.62), 0.034, 0.52)
        add(x, glass(NOTES["D5"] * detune, 0.100, 0.22), 0.073, 0.20)
        put(f"r5_lock_in_{i:02d}", x, -7.0, "commit", 0.018)

    for i, freq in enumerate((587, 622), 1):
        x = canvas(0.120)
        add(x, marimba(freq, 0.105, 0.16), 0.003, 0.48)
        add(x, wood(freq * 0.62, 0.065, 0.22), 0.010, 0.22)
        put(f"r5_remote_lock_{i:02d}", x, -12.0, "remote")

    # Countdown is scheduled as three discrete assets; urgency comes from pitch.
    for number, note in ((3, "D5"), (2, "A5"), (1, "D6")):
        x = canvas(0.095)
        add(x, marimba(NOTES[note], 0.088, 0.22), 0.003, 0.56)
        put(f"r5_countdown_{number}", x, -10.0, "countdown")

    x = canvas(0.260)
    add(x, wood(510, 0.125, 0.48), 0.002, 0.52)
    add(x, marimba(NOTES["D5"], 0.185, 0.16), 0.055, 0.52)
    add(x, marimba(NOTES["A4"], 0.150, 0.12), 0.102, 0.40)
    put("r5_time_up", x, -7.5, "countdown", 0.025)

    # Reveal: five independently synchronized notes plus a reference sequence.
    reveal_notes = motif
    for i, note in enumerate(reveal_notes, 1):
        x = canvas(0.160)
        add(x, paper(0.075, True, (i - 3) * 0.12), 0.000, 0.28)
        add(x, marimba(NOTES[note], 0.145, 0.28 + i * 0.025), 0.012, 0.56)
        add(x, glass(NOTES[note] * 1.5, 0.135, 0.40), 0.018, 0.16 + i * 0.012)
        put(f"r5_reveal_card_{i:02d}", x, -9.0 + i * 0.25, "reveal", 0.025)

    x = canvas(0.760)
    for i, note in enumerate(reveal_notes):
        at = 0.012 + i * 0.132
        add(x, paper(0.070, True, (i - 2) * 0.12), at, 0.22)
        add(x, marimba(NOTES[note], 0.175, 0.30 + i * 0.025), at + 0.010, 0.54)
        add(x, glass(NOTES[note] * 1.5, 0.160, 0.42), at + 0.016, 0.17)
    put("r5_reveal_sequence_reference", x, -6.8, "reveal", 0.055)

    # Score movement and four increasingly resolved outcomes.
    x = canvas(0.780)
    for i, note in enumerate(("D5", "F5", "G5", "A5", "C6", "D6")):
        at = 0.020 + i * 0.104
        add(x, glass(NOTES[note] * 1.25, 0.205, 0.34), at, 0.17 + i * 0.012)
        add(x, marimba(NOTES[note], 0.130, 0.18), at + 0.003, 0.27)
    add(x, airy(0.590, True), 0.030, 0.22)
    put("r5_score_countup", x, -9.0, "score", 0.07)

    x = canvas(0.260)
    add(x, wood(410, 0.110, 0.28), 0.010, 0.45)
    add(x, marimba(NOTES["D4"], 0.165, 0.08), 0.055, 0.32)
    put("r5_score_way_off", x, -10.0, "score_outcome")

    x = canvas(0.320)
    add(x, marimba(NOTES["D5"], 0.160, 0.16), 0.010, 0.40)
    add(x, marimba(NOTES["F5"], 0.190, 0.18), 0.084, 0.43)
    put("r5_score_not_bad", x, -9.0, "score_outcome", 0.025)

    x = canvas(0.400)
    for i, note in enumerate(("D5", "G5", "A5")):
        add(x, marimba(NOTES[note], 0.185, 0.22), 0.010 + i * 0.072, 0.46)
        add(x, glass(NOTES[note] * 1.5, 0.160, 0.32), 0.016 + i * 0.072, 0.09 + i * 0.02)
    put("r5_score_so_close", x, -8.0, "score_outcome", 0.04)

    x = tonal_sequence(motif, 0.620, 0.070, 0.200, 0.20, 0.010)
    add(x, glass(NOTES["D6"] * 1.5, 0.280, 0.60), 0.305, 0.20)
    put("r5_score_perfect", x, -5.8, "score_outcome", 0.08)

    # Ready state and end-of-round transitions.
    for i, note in enumerate(("G5", "A5"), 1):
        x = canvas(0.120)
        add(x, marimba(NOTES[note], 0.105, 0.20), 0.004, 0.50)
        add(x, glass(NOTES[note] * 1.5, 0.090, 0.30), 0.008, 0.10)
        put(f"r5_round_ready_{i:02d}", x, -11.0, "ready")

    x = canvas(0.430)
    for i, note in enumerate(("D5", "G5", "A5", "D6")):
        add(x, marimba(NOTES[note], 0.180, 0.25), 0.008 + i * 0.070, 0.48)
    add(x, paper(0.140, True), 0.005, 0.15)
    put("r5_everyone_ready", x, -7.5, "ready", 0.05)

    x = canvas(1.520)
    # Cooperative: motif notes converge into a shared open D chord.
    for i, note in enumerate(motif):
        at = 0.025 + i * 0.105
        add(x, marimba(NOTES[note], 0.280, 0.33), at, 0.52)
        add(x, glass(NOTES[note] * 1.5, 0.260, 0.50), at + 0.008, 0.12)
    for note, gain in (("D5", 0.36), ("A5", 0.30), ("D6", 0.27)):
        add(x, glass(NOTES[note], 0.700, 0.45), 0.650, gain)
    add(x, airy(0.500, True), 0.260, 0.15)
    put("r5_final_results_coop", x, -4.8, "final", 0.13)

    x = canvas(1.560)
    # Versus: same motif, then a clear high-card crown without fanfare brass.
    for i, note in enumerate(motif):
        at = 0.025 + i * 0.095
        add(x, marimba(NOTES[note], 0.250, 0.38), at, 0.55)
        add(x, wood(NOTES[note] * 0.60, 0.120, 0.33), at, 0.15)
    for i, note in enumerate(("A5", "D6", "E6")):
        add(x, glass(NOTES[note], 0.520, 0.58), 0.575 + i * 0.105, 0.26 + i * 0.035)
    put("r5_final_results_versus", x, -4.5, "final", 0.12)

    # Errors and network state.
    for i, freq in enumerate((245, 265), 1):
        x = canvas(0.170)
        add(x, wood(freq, 0.120, 0.38), 0.004, 0.62)
        add(x, wood(freq * 1.12, 0.090, 0.22), 0.050, 0.25)
        put(f"r5_error_{i:02d}", x, -8.5, "error")

    x = canvas(0.420)
    add(x, airy(0.370, True), 0.010, 0.62)
    add(x, glass(NOTES["A4"], 0.300, 0.20), 0.030, 0.24)
    add(x, glass(NOTES["E5"], 0.270, 0.20), 0.060, 0.20)
    put("r5_connection_lost", x, -11.0, "network", 0.06)

    x = canvas(0.340)
    add(x, airy(0.220, False), 0.000, 0.26)
    add(x, marimba(NOTES["A4"], 0.220, 0.16), 0.020, 0.40)
    add(x, marimba(NOTES["D5"], 0.220, 0.20), 0.095, 0.52)
    add(x, glass(NOTES["D6"], 0.150, 0.30), 0.135, 0.10)
    put("r5_reconnect_success", x, -8.5, "network", 0.045)

    return cues


def rms_db(x: np.ndarray) -> float:
    rms = float(np.sqrt(np.mean(np.square(x, dtype=np.float64))))
    return 20.0 * math.log10(max(rms, 1e-12))


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    DOCS.mkdir(parents=True, exist_ok=True)
    cues = build()
    manifest = {
        "format": "Ogg Vorbis",
        "sample_rate_hz": SR,
        "channels": 1,
        "normalization": "category peak targets; minimum 4.5 dBFS headroom",
        "generator_seed": 5_050_505,
        "assets": [],
    }
    for name, (audio, target_peak, family) in sorted(cues.items()):
        path = OUT / f"{name}.ogg"
        sf.write(path, audio, SR, format="OGG", subtype="VORBIS")
        decoded, decoded_sr = sf.read(path, dtype="float32")
        if decoded_sr != SR or decoded.ndim != 1:
            raise RuntimeError(f"Unexpected encoded format for {path.name}")
        manifest["assets"].append({
            "file": path.name,
            "family": family,
            "duration_ms": round(len(decoded) * 1000 / SR),
            "peak_dbfs_decoded": round(20 * math.log10(float(np.max(np.abs(decoded)))), 2),
            "rms_dbfs_decoded": round(rms_db(decoded), 2),
            "target_peak_dbfs": target_peak,
            "size_bytes": path.stat().st_size,
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        })
    (DOCS / "asset-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"Wrote {len(cues)} OGG assets to {OUT}")


if __name__ == "__main__":
    main()
