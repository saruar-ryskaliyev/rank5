#!/usr/bin/env python3
from pathlib import Path
import struct
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
PLAY = ROOT / "docs" / "play"


def png_size(path: Path):
    with path.open("rb") as f:
        sig = f.read(8)
        assert sig == b"\x89PNG\r\n\x1a\n", path
        length, ttype = struct.unpack(">I4s", f.read(8))
        assert ttype == b"IHDR"
        w, h, bit, color = struct.unpack(">IIBB", f.read(10))
        return w, h, color  # color 2=truecolor, 6=truecolor+alpha


def jpeg_size(path: Path):
    out = subprocess.check_output(["sips", "-g", "pixelWidth", "-g", "pixelHeight", str(path)], text=True)
    w = h = None
    for line in out.splitlines():
        if "pixelWidth" in line:
            w = int(line.split()[-1])
        if "pixelHeight" in line:
            h = int(line.split()[-1])
    return w, h


def main():
    icon = PLAY / "icon-512.png"
    feat = PLAY / "feature-graphic-1024x500.png"
    shots = sorted((PLAY / "screenshots").glob("phone-*.jpg"))
    assert icon.is_file(), icon
    assert feat.is_file(), feat
    w, h, color = png_size(icon)
    assert (w, h) == (512, 512), (w, h)
    assert color == 6, color  # Play listing icon allows alpha
    w, h, color = png_size(feat)
    assert (w, h) == (1024, 500), (w, h)
    assert color == 2, color  # no alpha
    assert len(shots) >= 2, shots
    for p in shots:
        w, h = jpeg_size(p)
        assert (w, h) == (1080, 2160), (p, w, h)
        assert max(w, h) <= 2 * min(w, h)
    print(f"ok icon+feature+{len(shots)} screenshots")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(e, file=sys.stderr)
        sys.exit(1)
