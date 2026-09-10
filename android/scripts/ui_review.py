"""Small, emulator-only UI review helper. Screenshots are unmodified device captures."""
import argparse
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ADB = Path.home() / 'Library/Android/sdk/platform-tools/adb'


def adb(*args, binary=False):
    return subprocess.check_output([str(ADB), '-s', 'emulator-5554', *args], text=not binary)


def nodes():
    adb('shell', 'uiautomator', 'dump', '/sdcard/rank5-review.xml')
    root = ET.fromstring(adb('shell', 'cat', '/sdcard/rank5-review.xml'))
    return list(root.iter('node'))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['state', 'tap', 'type', 'back', 'scroll', 'capture'])
    parser.add_argument('value', nargs='?', default='')
    args = parser.parse_args()
    if args.action == 'state':
        for n in nodes():
            a = n.attrib
            if a.get('text') or a.get('content-desc'):
                print(a.get('text') or a['content-desc'], a['bounds'],
                      'clickable' if a.get('clickable') == 'true' else '',
                      'enabled=' + a.get('enabled', ''))
    elif args.action == 'tap':
        matches = [n for n in nodes() if args.value in (n.get('text'), n.get('content-desc'))]
        if not matches:
            raise SystemExit('UI target not found: ' + args.value)
        n = matches[0]
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
        if x1 == x2 or y1 == y2:
            raise SystemExit('UI target has no visible bounds: ' + args.value)
        adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
    elif args.action == 'type':
        if not re.fullmatch(r'[A-Za-z0-9 ]{1,28}', args.value):
            raise SystemExit('Only short review names and room codes are supported')
        adb('shell', 'input', 'text', args.value.replace(' ', '%s'))
    elif args.action == 'back':
        adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    elif args.action == 'scroll':
        width, height = map(int, re.findall(r'(\d+)x(\d+)', adb('shell', 'wm', 'size'))[-1])
        adb('shell', 'input', 'swipe', str(width // 2), str(height * 7 // 10),
            str(width // 2), str(height * 3 // 10), '450')
    elif args.action == 'capture':
        if not re.fullmatch(r'[a-z0-9-]+', args.value):
            raise SystemExit('Use a lowercase screenshot name')
        path = ROOT / 'docs/screenshots' / (args.value + '.png')
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
        print(path)


if __name__ == '__main__':
    main()
