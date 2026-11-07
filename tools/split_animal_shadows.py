"""Separate existing ground ellipses from animal bodies without changing either drawing.

Run after SVG conversion. Animal art without a ground shadow needs no extra resources.
"""
import copy
import re
from pathlib import Path
import xml.etree.ElementTree as ET

ANDROID = 'http://schemas.android.com/apk/res/android'
AAPT = 'http://schemas.android.com/aapt'
A = '{' + ANDROID + '}'
ET.register_namespace('android', ANDROID)
ET.register_namespace('aapt', AAPT)

# Svg2Vector turns a ground ellipse into two relative arc segments. Only detach
# flat, un-stroked ground ellipses preceding the body's first painted shape.
ELLIPSE = re.compile(r'^M[-\d.,]+a([\d.]+),([\d.]+)\s+0,1\s+0,[-\d.]+\s+0a[\d.,\s-]+z$', re.I)

def split_animal_shadows(resources: Path) -> list[str]:
    changed = []
    for path in sorted((resources / 'drawable').glob('shop_animal_*.xml')):
        if path.stem.endswith(('_body', '_shadow')):
            continue
        root = ET.parse(path).getroot()
        shadow_indices = []
        for index, child in enumerate(root):
            match = ELLIPSE.fullmatch(child.get(A + 'pathData', ''))
            if child.tag != 'path' or not match or child.get(A + 'strokeColor') or float(match[1]) < float(match[2]) * 3:
                break
            shadow_indices.append(index)
        if not shadow_indices:
            continue
        for suffix, keep_shadow in [('_body', False), ('_shadow', True)]:
            layer = copy.deepcopy(root)
            for index, child in reversed(list(enumerate(layer))):
                if (index in shadow_indices) != keep_shadow:
                    layer.remove(child)
            ET.indent(layer)
            destination = path.with_stem(path.stem + suffix)
            rendered = ET.tostring(layer, encoding='unicode') + '\n'
            if not destination.exists() or destination.read_text(encoding='utf-8') != rendered:
                destination.write_text(rendered, encoding='utf-8')
                changed.append(destination.name)
    return changed

if __name__ == '__main__':
    root = Path(__file__).resolve().parents[1]
    for directory in ['res', 'res-private']:
        changed = split_animal_shadows(root / 'app/src/main' / directory)
        print(f'{directory}: {len(changed)} layer resources written')
