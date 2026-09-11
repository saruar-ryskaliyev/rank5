#!/usr/bin/env python3
"""Generate Play Store listing bitmaps.

Source screenshots (docs/screenshots/), scaled to fit 1080×2160 JPEG
(Play’s 2:1 max; side-padded with rank5_background so chrome is not cropped):
  home.png            -> docs/play/screenshots/phone-home.jpg
  pass-and-play.png   -> docs/play/screenshots/phone-pass-and-play.jpg
  decks.png           -> docs/play/screenshots/phone-decks.jpg
  ranking.png         -> docs/play/screenshots/phone-ranking.jpg
  reveal.png          -> docs/play/screenshots/phone-reveal.jpg
  profile.png         -> docs/play/screenshots/phone-profile.jpg
  join-room.png       -> docs/play/screenshots/phone-join-room.jpg
  handoff.png         -> docs/play/screenshots/phone-handoff.jpg

Icon and feature graphic reuse rank5_brand_mark.xml viewport-108 bar
geometry on the launcher purple field. First four bars are white
(ic_launcher_foreground); the last bar is brand_mark_last (#B3264B).
"""
from __future__ import annotations

from pathlib import Path
import struct
import subprocess
import zlib

ROOT = Path(__file__).resolve().parents[2]
PLAY = ROOT / "docs" / "play"
SCREENSHOTS_SRC = ROOT / "docs" / "screenshots"

# rank5_brand_mark.xml path data: M30,y hW a4,4 0 0 1 0,8 h-W a4,4 0 0 1 0,-8 z
VIEWPORT = 108.0
BAR_X = 30.0
BAR_HEIGHT = 8.0
BAR_RADIUS = 4.0
BAR_Y = (24.0, 37.0, 50.0, 63.0, 76.0)
BAR_WIDTHS = (48.0, 39.0, 30.0, 21.0, 12.0)

FIELD = (0x5A, 0x3F, 0xD6)
BAR_FILL = (0xFF, 0xFF, 0xFF)
LAST_BAR = (0xB3, 0x26, 0x4B)

# Play: long side ≤ 2× short side. 1080×2160 is the tallest allowed at this width.
SHOT_W = 1080
SHOT_H = 2160
PAD_COLOR = "F8F7FC"  # rank5_background

SCREENSHOTS = (
    ("home.png", "phone-home.jpg"),
    ("pass-and-play.png", "phone-pass-and-play.jpg"),
    ("decks.png", "phone-decks.jpg"),
    ("ranking.png", "phone-ranking.jpg"),
    ("reveal.png", "phone-reveal.jpg"),
    ("profile.png", "phone-profile.jpg"),
    ("join-room.png", "phone-join-room.jpg"),
    ("handoff.png", "phone-handoff.jpg"),
)


def _chunk(tag: bytes, data: bytes) -> bytes:
    crc = zlib.crc32(tag + data) & 0xFFFFFFFF
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", crc)


def write_png(path: Path, width: int, height: int, pixels: bytes, color_type: int) -> None:
    bpp = 4 if color_type == 6 else 3
    raw = bytearray()
    stride = width * bpp
    for y in range(height):
        raw.append(0)
        raw.extend(pixels[y * stride : (y + 1) * stride])
    ihdr = struct.pack(">IIBBBBB", width, height, 8, color_type, 0, 0, 0)
    payload = (
        b"\x89PNG\r\n\x1a\n"
        + _chunk(b"IHDR", ihdr)
        + _chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        + _chunk(b"IEND", b"")
    )
    path.write_bytes(payload)


def _in_capsule(px: float, py: float, left: float, top: float, width: float) -> bool:
    r = BAR_RADIUS
    cy = top + BAR_HEIGHT / 2.0
    if top <= py <= top + BAR_HEIGHT and left <= px <= left + width:
        return True
    dx0, dy0 = px - left, py - cy
    if dx0 * dx0 + dy0 * dy0 <= r * r:
        return True
    dx1, dy1 = px - (left + width), py - cy
    return dx1 * dx1 + dy1 * dy1 <= r * r


def _bar_color(index: int) -> tuple[int, int, int]:
    return LAST_BAR if index == len(BAR_Y) - 1 else BAR_FILL


def raster_bars(
    width: int,
    height: int,
    scale: float,
    origin_x: float,
    origin_y: float,
    *,
    alpha: bool,
    samples: int = 4,
) -> bytes:
    bpp = 4 if alpha else 3
    pixels = bytearray(width * height * bpp)
    step = 1.0 / samples
    offset = step / 2.0
    for y in range(height):
        for x in range(width):
            acc = [0.0, 0.0, 0.0]
            for sy in range(samples):
                for sx in range(samples):
                    px = ((x + sx * step + offset) - origin_x) / scale
                    py = ((y + sy * step + offset) - origin_y) / scale
                    rgb = FIELD
                    for i, (by, bw) in enumerate(zip(BAR_Y, BAR_WIDTHS)):
                        if _in_capsule(px, py, BAR_X, by, bw):
                            rgb = _bar_color(i)
                    acc[0] += rgb[0]
                    acc[1] += rgb[1]
                    acc[2] += rgb[2]
            n = samples * samples
            i = (y * width + x) * bpp
            pixels[i] = int(acc[0] / n + 0.5)
            pixels[i + 1] = int(acc[1] / n + 0.5)
            pixels[i + 2] = int(acc[2] / n + 0.5)
            if alpha:
                pixels[i + 3] = 255
    return bytes(pixels)


def write_icon() -> None:
    scale = 512.0 / VIEWPORT
    pixels = raster_bars(512, 512, scale, 0.0, 0.0, alpha=True)
    write_png(PLAY / "icon-512.png", 512, 512, pixels, color_type=6)


def write_feature_graphic() -> None:
    # Map the 108×108 viewport onto a 500×500 square, optically centered.
    scale = 500.0 / VIEWPORT
    origin_x = (1024.0 - 500.0) / 2.0
    origin_y = (500.0 - 500.0) / 2.0
    pixels = raster_bars(1024, 500, scale, origin_x, origin_y, alpha=False)
    write_png(PLAY / "feature-graphic-1024x500.png", 1024, 500, pixels, color_type=2)


def export_screenshots() -> None:
    dest_dir = PLAY / "screenshots"
    dest_dir.mkdir(parents=True, exist_ok=True)
    for src_name, dest_name in SCREENSHOTS:
        src = SCREENSHOTS_SRC / src_name
        dest = dest_dir / dest_name
        scaled = dest.with_suffix(".scaled.png")
        padded = dest.with_suffix(".png")
        try:
            # 1080×2400 → 972×2160, then pad sides to 1080×2160.
            subprocess.check_call(
                ["sips", "--resampleHeight", str(SHOT_H), str(src), "--out", str(scaled)]
            )
            # -p must precede --padColor; otherwise sips ignores the pad size.
            subprocess.check_call(
                [
                    "sips",
                    "-p",
                    str(SHOT_H),
                    str(SHOT_W),
                    "--padColor",
                    PAD_COLOR,
                    str(scaled),
                    "--out",
                    str(padded),
                ]
            )
            subprocess.check_call(["sips", "-s", "format", "jpeg", str(padded), "--out", str(dest)])
        finally:
            scaled.unlink(missing_ok=True)
            padded.unlink(missing_ok=True)


def main() -> None:
    PLAY.mkdir(parents=True, exist_ok=True)
    write_icon()
    write_feature_graphic()
    export_screenshots()


if __name__ == "__main__":
    main()
