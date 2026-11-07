"""Import the supplied workbench/SVG drawings into native Compose and VectorDrawable.

Run from the repository root: python tools/import_visual_assets.py [看板 HTML]
Only the three asset-generating functions are evaluated, in an isolated Node VM.

经典猫取自 design/source/visual-workbench.html；墨团（2.0.3 起）取自看板 HTML 的 catStyle='minimal'，
默认 E:/Chrome/阅读/看板+图标+开屏.html。头顶的叶子只在 leaf = true（当天读满半小时）时画，画成红叶。
"""
import json
import copy
import re
import subprocess
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'design/source'
OUT = ROOT / 'design/generated'
RES = ROOT / 'app/src/main/res'
JAVA = ROOT / 'app/src/main/java/com/kongye/reader/ui/components'
OUT.mkdir(parents=True, exist_ok=True)
BOARD = Path(sys.argv[1] if len(sys.argv) > 1 else 'E:/Chrome/阅读/看板+图标+开屏.html')
extract = r"""
const fs = require('fs'), vm = require('vm');
const html = fs.readFileSync('design/source/visual-workbench.html', 'utf8');
const begin = html.indexOf('        const state = {');
const end = html.indexOf('        function renderAllAssets()');
const context = vm.createContext({});
vm.runInContext(html.slice(begin, end), context, {timeout: 1000});
const writeSvg = (name, markup) => fs.writeFileSync(`design/generated/${name}`, markup.replace(/[ \t]+$/gm, '').trimEnd() + '\n');
for (const dark of [false, true]) {
  const suffix = dark ? '-dark' : '';
  for (const mood of ['focus', 'happy', 'sleep']) {
    const markup = vm.runInContext(`getMascotSvgMarkup('${mood}', ${dark})`, context);
    // Keep the rounded paw strokes inside the flat bottom edge in all moods.
    writeSvg(`home-${mood}${suffix}.svg`, markup.replaceAll('y2="231"', 'y2="228.5"'));
  }
  writeSvg(`splash${suffix}.svg`, vm.runInContext(`getSplashScreenSvgMarkup(${dark})`, context));
}
writeSvg('icon.svg', vm.runInContext("getAppIconSvgMarkup('light')", context));
"""
subprocess.run(['node', '-e', extract], cwd=ROOT, check=True)
# 胡须压到猫头下面（2.0.0 起）：胡须内侧被头盖住，像从脸后面长出来
import sys
sys.path.insert(0, str(ROOT / 'tools'))
from svg_whiskers import whiskers_under_head
for generated in OUT.glob('*.svg'):
    generated.write_text(whiskers_under_head(generated.read_text(encoding='utf-8')), encoding='utf-8')

def tag(el):
    return el.tag.split('}')[-1]

def num(n):
    n = str(n).removesuffix('px')
    return f'{float(n):g}f' if '.' in f'{float(n):g}' else f'{float(n):g}.0f'

def color(value):
    if value == 'none':
        return 'Color.Transparent'
    if value.startswith('rgb'):
        parts = [float(v) for v in re.findall(r'[\d.]+', value)]
        value = '#%02x%02x%02x%02x' % (round(parts[3] * 255) if len(parts) == 4 else 255, *map(int, parts[:3]))
    else:
        value = value.lstrip('#')
        if len(value) == 3:
            value = ''.join(c * 2 for c in value)
        value = '#' + ('ff' if len(value) == 6 else '') + value
    return f'Color(0x{value[1:].upper()})'

def shape(el):
    a, t = el.attrib, tag(el)
    if t == 'path':
        return a['d']
    if t == 'line':
        return f"M{a['x1']} {a['y1']}L{a['x2']} {a['y2']}"
    if t in ('polygon', 'polyline'):
        pts = re.findall(r'-?[\d.]+', a['points'])
        return 'M' + 'L'.join(f'{x} {y}' for x, y in zip(pts[::2], pts[1::2])) + ('Z' if t == 'polygon' else '')
    if t in ('circle', 'ellipse'):
        x, y = float(a['cx']), float(a['cy'])
        rx, ry = float(a.get('rx', a.get('r', 0))), float(a.get('ry', a.get('r', 0)))
        return f'M{x-rx:g} {y:g}a{rx:g} {ry:g} 0 1 0 {2*rx:g} 0a{rx:g} {ry:g} 0 1 0 {-2*rx:g} 0Z'
    if t == 'rect':
        x, y, w, h = map(float, [a.get('x', 0), a.get('y', 0), a['width'], a['height']])
        return f'M{x:g} {y:g}h{w:g}v{h:g}h{-w:g}Z'
    raise ValueError(t)

def transforms(value):
    lines = []
    for op, args in re.findall(r'(\w+)\(([^)]+)\)', value):
        v = [num(x) for x in re.findall(r'-?[\d.]+', args)]
        if op == 'translate':
            lines.append(f'translate({v[0]}, {v[1] if len(v)>1 else "0f"})')
        elif op == 'scale':
            lines.append(f'scale({v[0]}, {v[1] if len(v)>1 else v[0]}, pivot = Offset.Zero)')
        elif op == 'rotate':
            lines.append(f'rotate({v[0]}, pivot = Offset({v[1]}, {v[2]}))')
        else:
            raise ValueError(op)
    return lines

paths = []
def path_ref(d):
    if d not in paths:
        paths.append(d)
    return f'ImportedCatPaths.p{paths.index(d)}'

def classes(root):
    result = {}
    for style in root.iter():
        if tag(style) != 'style':
            continue
        for selectors, body in re.findall(r'([^{}]+)\{([^}]+)\}', style.text or ''):
            for name in re.findall(r'\.([\w-]+)', selectors):
                result.setdefault(name, {}).update(dict(re.findall(r'([\w-]+)\s*:\s*([^;]+);', body)))
    return result

def emit(el, dark_el, css, dark_css, inherited=None, dark_inherited=None, indent=1, wrapped=False):
    t = tag(el)
    if t in ('defs', 'style'):
        return []
    a = dict(inherited or {'fill': '#000000'})
    b = dict(dark_inherited or {'fill': '#000000'})
    for name in el.get('class', '').split():
        for props, source in [(a, css), (b, dark_css)]:
            values = source.get(name, {})
            # Animated group opacity is applied by assetGroup, not baked into its path.
            props.update({k:v for k,v in values.items() if k != 'opacity' or 'animation' not in values})
    a.update(el.attrib); b.update(dark_el.attrib)
    pad = '    ' * indent
    cls = el.get('class', '')
    tr = el.get('transform', '')
    if not wrapped and (cls or tr):
        head = [pad + f'assetGroup({json.dumps(cls)}, time) {{'] if cls else []
        level = indent + bool(cls)
        if tr:
            head += ['    '*level + 'withTransform({ ' + '; '.join(transforms(tr)) + ' }) {']
            level += 1
        head += emit(el, dark_el, css, dark_css, inherited, dark_inherited, level, True)
        if tr:
            head += ['    '*(level-1) + '}']
        if cls:
            head += [pad + '}']
        return head
    if t in ('svg', 'g'):
        inherit_keys = ('fill', 'stroke', 'stroke-width', 'stroke-linecap', 'stroke-linejoin')
        ia, ib = ({k:v for k,v in props.items() if k in inherit_keys} for props in (a,b))
        result = []
        for child, dc in zip(el, dark_el):
            result += emit(child, dc, css, dark_css, ia, ib, indent)
        return result
    def col(key):
        v1, v2 = a.get(key, 'none'), b.get(key, 'none')
        return color(v1) if v1 == v2 else f'(if (dark) {color(v2)} else {color(v1)})'
    if t == 'text':
        return [pad + f'assetText({json.dumps(el.text, ensure_ascii=False)}, {num(a["x"])}, {num(a["y"])}, {num(a["font-size"])}, {col("fill")}, bold = {str(a.get("font-weight") == "700").lower()}, centered = {str(a.get("text-anchor") == "middle").lower()}, spacing = {num(a.get("letter-spacing", 0))})']
    d = shape(el)
    ref = path_ref(d)
    reveal = 'splashProgress(time, 0f, 0.55f)' if cls == 'hero-leaf-outline' else 'splashProgress(time, 0.25f, 0.4f)' if cls == 'hero-leaf-vein' else '1f'
    result = []
    if t != 'line' and a.get('fill', 'none') != 'none':
        alpha = num(a.get('opacity', 1))
        if cls == 'hero-leaf-outline':
            alpha = 'splashProgress(time, 0.85f, 0.3f)'
        result.append(pad + f'drawPath({ref}, {col("fill")}, alpha = {alpha})')
    if a.get('stroke', 'none') != 'none':
        cap = {'round': 'Round', 'square': 'Square'}.get(a.get('stroke-linecap'), 'Butt')
        join = {'round': 'Round', 'bevel': 'Bevel'}.get(a.get('stroke-linejoin'), 'Miter')
        result.append(pad + f'assetStroke({ref}, {col("stroke")}, {num(a.get("stroke-width", 1))}, StrokeCap.{cap}, StrokeJoin.{join}, {num(a.get("opacity", 1))}, {reveal})')
    return result

LEAF_COLOR = r'(\(if \(dark\) Color\(0x\w+\) else Color\(0x\w+\)\)|Color\(0x\w+\))'


def leafify(lines, group, drop=False, plain=False):
    """头顶叶子：只有 leaf 为真才画，画成红叶（填充 + 深红描边）；drop 直接去掉（猫园趴着的猫）。
    plain：另加一个 plainLeaf 开关，画看板 HTML 里普通专注时的原样叶子（墨色描边，经典猫不填色、墨团填底色），
    商城、装扮里展示用"""
    i = next(i for i, line in enumerate(lines) if f'assetGroup("{group}", time) {{' in line)
    indent = len(lines[i]) - len(lines[i].lstrip())
    j = next(j for j in range(i + 1, len(lines)) if lines[j] == ' ' * indent + '}')
    if drop:
        return lines[:i] + lines[j + 1:]
    body = [re.sub(r'(assetStroke\(ImportedCatPaths\.p\d+, )' + LEAF_COLOR, r'\1Color(0xFFB8230C)',
                   re.sub(r'(drawPath\(ImportedCatPaths\.p\d+, )' + LEAF_COLOR, r'\1Color(0xFFEE4326)', line))
            for line in lines[i + 1:j] if not line.strip().startswith('if (leaf)')]
    if not any('drawPath(' in line for line in body):
        ref = re.search(r'assetStroke\((ImportedCatPaths\.p\d+)', body[0]).group(1)
        body.insert(0, ' ' * (indent + 4) + f'drawPath({ref}, Color(0xFFEE4326))')
    if plain:
        original = [line for line in lines[i + 1:j] if not line.strip().startswith('if (leaf)')]
        body += [' ' * indent + f'}} else if (plainLeaf) assetGroup("{group}", time) {{'] + original
    return lines[:i] + [' ' * indent + f'if (leaf) assetGroup("{group}", time) {{'] + body + lines[j:]


def follow_gaze(lines, pupil_refs):
    """瞳孔跟着手指：每个瞳孔（和它的高光）包一层平移"""
    first = next(i for i, line in enumerate(lines) if f'drawPath({pupil_refs[0]},' in line)
    last = next(i for i, line in enumerate(lines) if f'drawPath({pupil_refs[-1]},' in line)
    if last - first + 1 == len(pupil_refs) or len(pupil_refs) > 2:
        indent = lines[first][:len(lines[first]) - len(lines[first].lstrip())]
        return lines[:first] + [indent + 'withTransform({ translate(gaze.x * 5f, gaze.y * 5f) }) {',
                                *['    ' + line for line in lines[first:last + 1]], indent + '}'] + lines[last + 1:]
    out = []
    for line in lines:
        if any(f'drawPath({ref},' in line for ref in pupil_refs):
            indent = line[:len(line) - len(line.lstrip())]
            out += [indent + 'withTransform({ translate(gaze.x * 5f, gaze.y * 5f) }) {', '    ' + line, indent + '}']
        else:
            out.append(line)
    return out


functions = []
for name, filename in [('Focus','home-focus'), ('Happy','home-happy'), ('Sleep','home-sleep'), ('Splash','splash')]:
    light, dark = [ET.parse(OUT / f'{filename}{suffix}.svg').getroot() for suffix in ('', '-dark')]
    lines = emit(light,dark,classes(light),classes(dark))
    if name in ('Focus', 'Happy', 'Sleep'):
        lines = leafify(lines, 'svg-head-leaf', plain=name == 'Focus')
    if name == 'Focus':
        eyes = next(e for e in light.iter() if e.get('class') == 'svg-eye-blink')
        lines = follow_gaze(lines, [path_ref(shape(e)) for e in list(eyes)[2:]])
    params = {'Focus': ', leaf: Boolean = false, gaze: Offset = Offset.Zero',
              'Happy': ', leaf: Boolean = false', 'Sleep': ', leaf: Boolean = false'}.get(name, '')
    if name in ('Focus', 'Sleep'):
        body_color = '(if (dark) Color(0xFF232329) else Color(0xFF1E1E22))'
        lines = [line.replace(body_color, f'(bodyColor ?: {body_color})')
                 .replace('Color(0xFF47474E)', '(detailColor ?: Color(0xFF47474E))') for line in lines]
        lines[0] = lines[0].replace('drawPath(', 'if (shadow) drawPath(', 1)
        params += ', bodyColor: Color? = null, detailColor: Color? = null, shadow: Boolean = true'
    if name == 'Focus':
        params += ', plainLeaf: Boolean = false'
    functions += [f'internal fun DrawScope.drawImported{name}(dark: Boolean, time: Float{params}) {{', *lines, '}', '']

# User-supplied replacement for every legacy loaf pose. Preserve its paths and
# its own breathing timing/pivot rather than reshaping the sitting mascot.
loaf = ET.parse(SOURCE / 'cat-loaf.svg').getroot()
dark_loaf = copy.deepcopy(loaf)
for root in (loaf, dark_loaf):
    for element in root.iter():
        if element.get('class') == 'svg-cat-breathe':
            element.set('class', 'svg-loaf-breathe')
for element in dark_loaf.iter():
    for key in ('fill', 'stroke'):
        palette = {'#1E1E22':'#232329', '#EFEFEF':'rgba(0,0,0,0.45)', '#27272A':'#E4E4E7'}
        if element.get(key) in palette:
            element.set(key, palette[element.get(key)])
loaf_lines = leafify(emit(loaf, dark_loaf, classes(loaf), classes(dark_loaf)), 'svg-head-leaf', drop=True)
loaf_lines = [line.replace('(if (dark) Color(0xFF232329) else Color(0xFF1E1E22))',
                          '(bodyColor ?: (if (dark) Color(0xFF232329) else Color(0xFF1E1E22)))')
             .replace('Color(0xFF47474E)', '(detailColor ?: Color(0xFF47474E))')
             .replace('Color(0xFFFFFFFF)', '(eyeColor ?: Color.White)') for line in loaf_lines]
loaf_lines[0] = loaf_lines[0].replace('drawPath(', 'if (shadow) drawPath(', 1)
functions += ['internal fun DrawScope.drawImportedLoaf(dark: Boolean, time: Float, bodyColor: Color? = null, eyeColor: Color? = null, detailColor: Color? = null, shadow: Boolean = true) {',
              *loaf_lines, '}', '']

# —— 墨团：看板 HTML 的 catStyle='minimal'，坐标系 200x220。class 统一加 m- 前缀，
# 免得和经典猫同名的动画（爱心、腮红、Zzz、开屏耳朵）用错转动中心 ——
minimal_extract = r"""
const fs = require('fs'), vm = require('vm');
const html = fs.readFileSync(process.argv[1], 'utf8');
const begin = html.indexOf('        const state = {');
const end = html.indexOf('        function renderAllAssets()');
const context = vm.createContext({});
vm.runInContext(html.slice(begin, end) + ';this.S=state;', context, {timeout: 1000});
context.S.catStyle = 'minimal';
const writeSvg = (name, markup) => fs.writeFileSync(`design/generated/${name}`, markup.replace(/[ \t]+$/gm, '').trimEnd() + '\n');
for (const dark of [false, true]) {
  const suffix = dark ? '-dark' : '';
  for (const mood of ['focus', 'happy', 'sleep', 'loaf'])
    writeSvg(`min-${mood}${suffix}.svg`, vm.runInContext(`getMascotSvgMarkup('${mood}', ${dark})`, context));
  writeSvg(`min-splash${suffix}.svg`, vm.runInContext(`getSplashScreenSvgMarkup(${dark})`, context));
}
writeSvg('icon-min-light.svg', vm.runInContext("getAppIconSvgMarkup('light')", context));
"""
subprocess.run(['node', '-e', minimal_extract, str(BOARD)], cwd=ROOT, check=True)
MIN_BODY = '(if (dark) Color(0xFF232329) else Color(0xFF1B1B1B))'
for name in ('Focus', 'Happy', 'Sleep', 'Loaf', 'Splash'):
    light, dark = [ET.parse(OUT / f'min-{name.lower()}{suffix}.svg').getroot() for suffix in ('', '-dark')]
    for root in (light, dark):
        for el in root.iter():
            if el.get('class'):
                el.set('class', ' '.join('m-' + c for c in el.get('class').split()))
    lines = emit(light, dark, classes(light), classes(dark))
    params = ''
    if name in ('Focus', 'Happy', 'Sleep'):
        lines = leafify(lines, 'm-min-leaf', plain=name == 'Focus')
        params += ', leaf: Boolean = false'
    if name == 'Loaf':
        lines = leafify(lines, 'm-min-leaf', drop=True)
    if name == 'Focus':
        eyes = next(e for e in light.iter() if e.get('class') == 'm-min-eye-blink')
        lines = follow_gaze(lines, [path_ref(shape(e)) for e in eyes if e.get('r') == '6.5'])
        params += ', gaze: Offset = Offset.Zero'
    if name in ('Focus', 'Sleep', 'Loaf'):
        lines = [line.replace(MIN_BODY, f'(bodyColor ?: {MIN_BODY})') for line in lines]
        lines[0] = lines[0].replace('drawPath(', 'if (shadow) drawPath(', 1)
        params += ', bodyColor: Color? = null'
        if name == 'Loaf':
            lines = [line.replace('Color(0xFFFFFFFF)', '(eyeColor ?: Color.White)') for line in lines]
            params += ', eyeColor: Color? = null'
        params += ', shadow: Boolean = true'
    if name == 'Focus':
        params += ', plainLeaf: Boolean = false'
    functions += [f'internal fun DrawScope.drawMinimal{name}(dark: Boolean, time: Float{params}) {{', *lines, '}', '']

# —— 鎏金看板猫（成就「看板猫猫」的奖励）：成就 HTML 里的四个姿势，和看板猫同一套 240x260 骨架。
# 原稿没有动画 class，这里按部位分组，套上看板猫的 class（尾巴、呼吸、双耳、叶子、眨眼、腮红、爱心、Zzz），
# 动画、转动中心都和看板猫一样 ——
REWARDS = json.loads((ROOT / 'tools/achievement_rewards.json').read_text(encoding='utf-8'))
SVG_NS_ = 'http://www.w3.org/2000/svg'


def gold_tag(pose, markup):
    src = ET.fromstring(f'<svg xmlns="{SVG_NS_}" viewBox="0 0 240 260">{markup}</svg>')[0]
    kids = list(src)
    root = ET.Element(f'{{{SVG_NS_}}}svg', {'viewBox': '0 0 240 260'})

    def grp(cls, items):
        g = ET.Element(f'{{{SVG_NS_}}}g', {'class': cls})
        g.extend(items)
        return g

    def d(e):
        return (e.get('d') or '').replace(' ', '')

    def regroup(items):
        """耳朵（轮廓+内耳线）、叶子（叶片+叶柄+小金点）、眼睛、腮红各包成一组；嵌套的 <g> 往里找"""
        out, i = [], 0
        while i < len(items):
            e = items[i]
            tag = e.tag.split('}')[-1]
            if tag == 'g':
                inner = list(e)
                for c in inner:
                    e.remove(c)
                e.extend(regroup(inner))
                out.append(e)
                i += 1
            elif d(e).startswith('M74.4116'):
                out.append(grp('svg-ear-l', items[i:i + 2])); i += 2
            elif d(e).startswith('M165.6116'):
                out.append(grp('svg-ear-r', items[i:i + 2])); i += 2
            elif d(e).startswith('M12079C12066'):
                n = 3 if i + 2 < len(items) and items[i + 2].tag.endswith('circle') and items[i + 2].get('cx') == '142' else 2
                out.append(grp('svg-head-leaf', items[i:i + n])); i += n
            elif tag == 'circle' and e.get('fill') == '#FFFFFF' and e.get('cx') in ('99', '141') and pose == 'focus':
                out.append(grp('svg-eye-blink', items[i:i + 6])); i += 6
            elif tag == 'ellipse' and e.get('fill') == '#FF8A65':
                out.append(grp('svg-cheek-pulse', items[i:i + 2])); i += 2
            else:
                out.append(e); i += 1
        return out

    root.extend(kids[:2])  # 地上的两层光环影子
    body = next(i for i, e in enumerate(kids) if d(e).startswith('M77231') or d(e).startswith('M65231'))
    if pose != 'loaf':
        root.append(grp('svg-cat-tail-sleep' if pose == 'sleep' else 'svg-cat-tail', kids[2:body]))
    rest = kids[2:] if pose == 'loaf' else kids[body:]
    hearts = [e for e in rest if 'rotate(' in (e.get('transform') or '') and e.tag.endswith('path')]
    texts = [e for e in rest if e.tag.endswith('text')]
    keep = [e for e in rest if e not in hearts and e not in texts]
    root.append(grp('svg-loaf-breathe' if pose == 'loaf' else 'svg-cat-breathe', regroup(keep)))
    for n, h in enumerate(hearts, 1):
        root.append(grp(f'svg-heart-{n}', [h]))
    for n, t in enumerate(texts, 1):
        root.append(grp(f'svg-zzz-{n}', [t]))
    return root


for name in ('Focus', 'Happy', 'Sleep', 'Loaf'):
    root = gold_tag(name.lower(), REWARDS['poses'][name.lower()])
    (OUT / f'gold-{name.lower()}.svg').write_text(ET.tostring(root, encoding='unicode'), encoding='utf-8')
    lines = emit(root, root, {}, {})
    lines[0] = lines[0].replace('drawPath(', 'if (shadow) drawPath(', 1)
    lines[1] = lines[1].replace('drawPath(', 'if (shadow) drawPath(', 1)
    params = ''
    if name != 'Loaf':
        # 叶子和看板猫一样：当天读满半小时才长出来（鎏金叶子保持原色）
        i = next(i for i, line in enumerate(lines) if 'assetGroup("svg-head-leaf", time) {' in line)
        lines[i] = lines[i].replace('assetGroup(', 'if (leaf) assetGroup(', 1)
        params += ', leaf: Boolean = false'
    else:
        lines = leafify(lines, 'svg-head-leaf', drop=True)
    if name == 'Focus':
        eyes = next(e for e in root.iter() if e.get('class') == 'svg-eye-blink')
        lines = follow_gaze(lines, [path_ref(shape(e)) for e in list(eyes)[2:]])
        params += ', gaze: Offset = Offset.Zero'
    if name != 'Happy':
        params += ', shadow: Boolean = true'
    else:
        lines[0] = lines[0].replace('if (shadow) ', '')
        lines[1] = lines[1].replace('if (shadow) ', '')
    functions += [f'internal fun DrawScope.drawGold{name}(dark: Boolean, time: Float{params}) {{', *lines, '}', '']

# —— 鎏金墨团（成就「墨团猫猫」的奖励）：成就 HTML 只给了坐姿一张（E:/Chrome/reward-minimal-cat.svg），
# 四个姿势就用墨团自己的四张，换上奖励图里的金色部分：脚下双层金光环、头顶金叶呆毛和小金点、胸前金星章。
# class 和墨团一样带 m- 前缀，动画、转动中心、眼睛跟手指都和墨团一样 ——
GOLDMIN_STAR = [('polygon', {'points': '100,152 103,158 109,161 103,164 100,170 97,164 91,161 97,158', 'fill': '#FBBF24'}),
                ('circle', {'cx': '100', 'cy': '161', 'r': '2', 'fill': '#FFFBEB'})]
GOLDMIN_STAR_AT = {'focus': (0, 0), 'happy': (0, 0), 'sleep': (0, 0), 'loaf': (42, 8)}
# 尾巴上的双层金环 + 悬浮星芒（E:/Chrome/gemini-svg (3).svg，坐姿坐标）：后半圈在尾巴下面，前半圈、高光、星在上面。
GOLDMIN_RING_BACK = [('ellipse', {'cx': '157.5', 'cy': '158', 'rx': '8', 'ry': '3.3', 'transform': 'rotate(-14 157.5 158)', 'fill': 'none', 'stroke': '#B45309', 'stroke-width': '3'}),
                     ('ellipse', {'cx': '156.5', 'cy': '166', 'rx': '8', 'ry': '3.2', 'transform': 'rotate(-22 156.5 166)', 'fill': 'none', 'stroke': '#B45309', 'stroke-width': '2.6'})]
GOLDMIN_RING_FRONT = [('path', {'d': 'M150 160 C154.5 162.8, 161 161.5, 164.5 156.2', 'stroke': '#F59E0B', 'stroke-width': '4.2', 'stroke-linecap': 'round', 'fill': 'none'}),
                      ('path', {'d': 'M151.5 160 C155 162, 160 161, 163 157', 'stroke': '#FEF08A', 'stroke-width': '1.6', 'stroke-linecap': 'round', 'fill': 'none'}),
                      ('path', {'d': 'M149 167.5 C153.5 170.8, 160 169.5, 163.5 164', 'stroke': '#D97706', 'stroke-width': '3.4', 'stroke-linecap': 'round', 'fill': 'none'}),
                      ('circle', {'cx': '166.5', 'cy': '156.5', 'r': '1.8', 'fill': '#FBBF24'}),
                      ('polygon', {'points': '172,155 173.6,158.6 177.5,160.2 173.6,161.8 172,165.4 170.4,161.8 166.5,160.2 170.4,158.6', 'fill': '#FDE047'})]
# 别的姿势尾巴不在同一处：整套金环绕环心 (157,162) 转过去、挪到那个姿势的尾巴上（小憩：横着的那段；趴着：前绕的尾巴靠尾尖处）。
# 趴着的尾巴和身体是同一块，另画一小段尾巴（身体同色）把后半圈中间盖住。
GOLDMIN_RING_AT = {'focus': None, 'happy': None,
                   'sleep': ('translate(148 186) rotate(-100 0 0) scale(0.95 -0.95) translate(-157 -162)', None),
                   'loaf': ('translate(119 195.8) rotate(-80 0 0) scale(0.82) translate(-157 -162)', 'M113 195.9 L129 195.7')}


def goldmin_tag(root, pose):
    ns = '{http://www.w3.org/2000/svg}'
    shadow = next(e for e in root if e.tag == ns + 'ellipse')
    i = list(root).index(shadow)
    rx, ry = float(shadow.get('rx')), float(shadow.get('ry'))
    root.remove(shadow)
    halo = [ET.Element(ns + 'ellipse', {'cx': shadow.get('cx'), 'cy': shadow.get('cy'), 'rx': f'{rx + 8:g}', 'ry': f'{ry + 3:g}',
                                         'fill': '#FEF3C7', 'opacity': '0.65'}),
            ET.Element(ns + 'ellipse', {'cx': shadow.get('cx'), 'cy': shadow.get('cy'), 'rx': f'{rx:g}', 'ry': f'{ry:g}',
                                         'fill': '#F59E0B', 'opacity': '0.35'})]
    for n, e in enumerate(halo):
        root.insert(i + n, e)
    leaf = next(e for e in root.iter() if e.get('class') == 'm-min-leaf')
    blade, stem = list(leaf)[:2]
    blade.set('stroke', '#F59E0B'); blade.set('fill', '#FEF3C7')
    stem.set('stroke', '#F59E0B')
    leaf.append(ET.Element(ns + 'circle', {'cx': '125', 'cy': '24', 'r': '2.2', 'fill': '#FBBF24'}))
    body = next(e for e in root.iter() if e.get('class') in ('m-min-body', 'm-min-body-loaf'))
    dx, dy = GOLDMIN_STAR_AT[pose]
    holder = body
    if dx or dy:
        holder = ET.SubElement(body, ns + 'g', {'transform': f'translate({dx} {dy})'})
    for t, a in GOLDMIN_STAR:
        holder.append(ET.Element(ns + t, a))
    # 尾环：坐姿、小憩的尾巴是单独一笔，换成同名的组（跟着尾巴一起摆），后半圈、尾巴、前半圈依次画
    place = GOLDMIN_RING_AT[pose]
    tail = next((e for e in body if (e.get('class') or '').startswith('m-min-tail')), None)
    if tail is not None:
        group = ET.Element(ns + 'g', {'class': tail.get('class')})
        idx = list(body).index(tail)
        body.remove(tail)
        del tail.attrib['class']
        body.insert(idx, group)
        back = front = group
        if place:
            back = ET.SubElement(group, ns + 'g', {'transform': place[0]})
        for t, a in GOLDMIN_RING_BACK:
            back.append(ET.Element(ns + t, a))
        group.append(tail)
        if place:
            front = ET.SubElement(group, ns + 'g', {'transform': place[0]})
        for t, a in GOLDMIN_RING_FRONT:
            front.append(ET.Element(ns + t, a))
    else:
        fill = next(e for e in body if e.tag == ns + 'path').get('fill')
        back = ET.SubElement(body, ns + 'g', {'transform': place[0]})
        for t, a in GOLDMIN_RING_BACK:
            back.append(ET.Element(ns + t, a))
        body.append(ET.Element(ns + 'path', {'d': place[1], 'stroke': fill, 'stroke-width': '7.4', 'fill': 'none'}))
        front = ET.SubElement(body, ns + 'g', {'transform': place[0]})
        for t, a in GOLDMIN_RING_FRONT:
            front.append(ET.Element(ns + t, a))
    return root


for name in ('Focus', 'Happy', 'Sleep', 'Loaf'):
    light, dark = [ET.parse(OUT / f'min-{name.lower()}{suffix}.svg').getroot() for suffix in ('', '-dark')]
    for root in (light, dark):
        for el in root.iter():
            if el.get('class'):
                el.set('class', ' '.join('m-' + c for c in el.get('class').split()))
        goldmin_tag(root, name.lower())
    markup = '\n'.join(line.rstrip() for line in ET.tostring(light, encoding='unicode').splitlines()) + '\n'
    (OUT / f'goldmin-{name.lower()}.svg').write_text(markup, encoding='utf-8')
    lines = emit(light, dark, classes(light), classes(dark))
    lines[0] = lines[0].replace('drawPath(', 'if (shadow) drawPath(', 1)
    lines[1] = lines[1].replace('drawPath(', 'if (shadow) drawPath(', 1)
    params = ''
    if name != 'Loaf':
        # 和鎏金看板猫一样：金叶当天读满半小时才长出来，保持金色
        i = next(i for i, line in enumerate(lines) if 'assetGroup("m-min-leaf", time) {' in line)
        lines[i] = lines[i].replace('assetGroup(', 'if (leaf) assetGroup(', 1)
        params += ', leaf: Boolean = false'
    else:
        lines = leafify(lines, 'm-min-leaf', drop=True)
    if name == 'Focus':
        eyes = next(e for e in light.iter() if e.get('class') == 'm-min-eye-blink')
        lines = follow_gaze(lines, [path_ref(shape(e)) for e in eyes if e.get('r') == '6.5'])
        params += ', gaze: Offset = Offset.Zero'
    params += ', shadow: Boolean = true'
    functions += [f'internal fun DrawScope.drawGoldMin{name}(dark: Boolean, time: Float{params}) {{', *lines, '}', '']

# Three supplied chart drawings. Stretch only the body; preserve the eyes/leaf proportions.
for name, file in [('Black','black'),('Leaf','leaf'),('Gray','gray')]:
    root = ET.parse(SOURCE / f'bar-{file}.svg').getroot()
    lines = emit(root, root, classes(root), classes(root))
    body = next(shape(e) for e in root.iter() if tag(e) == 'path' and '296.78' in e.get('d', ''))
    ref = path_ref(body)
    lines = [line.replace(ref, 'body') for line in lines]
    functions += [f'internal fun DrawScope.drawImportedBar{name}(body: Path) {{', '    val time = 0f', *lines, '}', '']

header = '''// Generated by tools/import_visual_assets.py from the user-supplied artwork.
package com.kongye.reader.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform

'''
path_code = ['private object ImportedCatPaths {'] + [f'    val p{i} by lazy {{ svgPath({json.dumps(d)}) }}' for i,d in enumerate(paths)] + ['}', '']
from split_native_cat_shadows import split_native_cat_shadows
(JAVA / 'ImportedCatArt.kt').write_text(split_native_cat_shadows(header + '\n'.join(path_code + functions)), encoding='utf-8')

# Android adaptive icon: preserve all workbench paths, with artwork in the safe zone.
ANDROID = 'http://schemas.android.com/apk/res/android'
ET.register_namespace('android', ANDROID)
def va(**attrs):
    return {f'{{{ANDROID}}}{k}': str(v) for k,v in attrs.items()}

def vector(mono=False):
    v = ET.Element('vector', va(width='108dp',height='108dp',viewportWidth=512,viewportHeight=512))
    safe = ET.SubElement(v, 'group', va(scaleX=0.82,scaleY=0.82,translateX=46.08,translateY=46.08))
    def convert(el, parent, inherited=None):
        t = tag(el)
        if t in ('defs','style','rect'):
            return
        a = dict(inherited or {'fill': '#000000'}); a.update(el.attrib)
        tr = el.get('transform','')
        if tr:
            for op,args in re.findall(r'(\w+)\(([^)]+)\)',tr):
                nums = [float(x) for x in re.findall(r'-?[\d.]+',args)]
                if op == 'translate':
                    props = dict(translateX=nums[0],translateY=nums[1] if len(nums)>1 else 0)
                elif op == 'scale':
                    props = dict(scaleX=nums[0],scaleY=nums[1] if len(nums)>1 else nums[0])
                elif op == 'rotate':
                    props = dict(rotation=nums[0],pivotX=nums[1],pivotY=nums[2])
                parent = ET.SubElement(parent, 'group', va(**props))
        if t in ('svg','g'):
            for child in el:
                convert(child,parent,{k:v for k,v in a.items() if k in ('fill','stroke','stroke-width')})
            return
        attrs = dict(pathData=shape(el))
        for key, dest in [('fill','fillColor'),('stroke','strokeColor')]:
            if a.get(key,'none') != 'none':
                value = a[key]
                if mono:
                    # White cutouts use the adaptive icon's transparent mask.
                    if value.upper() in ('#FFFFFF','#FFF'):
                        attrs['fillAlpha' if key == 'fill' else 'strokeAlpha'] = 0
                    value = '#1E1E22'
                attrs[dest] = value
        if 'strokeColor' in attrs:
            attrs.update(strokeWidth=a.get('stroke-width',1),strokeLineCap=a.get('stroke-linecap','butt'),strokeLineJoin=a.get('stroke-linejoin','miter'))
        ET.SubElement(parent,'path',va(**attrs))
    convert(ET.parse(OUT / 'icon.svg').getroot(),safe)
    if mono:
        # A themed icon is an alpha mask: carve the eye whites out of the head,
        # and the highlights out of the pupils instead of painting them opaque.
        icon = ET.parse(OUT / 'icon.svg').getroot()
        circles = {float(e.get('cx')): shape(e) for e in icon.iter() if tag(e) == 'circle'}
        for element in v.iter('path'):
            data = element.get(f'{{{ANDROID}}}pathData', '')
            if data.startswith('M74 121a46 41'):
                element.set(f'{{{ANDROID}}}pathData', data + circles[99] + circles[141])
                element.set(f'{{{ANDROID}}}fillType', 'evenOdd')
            elif data == circles.get(101):
                element.set(f'{{{ANDROID}}}pathData', data + circles[104])
                element.set(f'{{{ANDROID}}}fillType', 'evenOdd')
            elif data == circles.get(139):
                element.set(f'{{{ANDROID}}}pathData', data + circles[142])
                element.set(f'{{{ANDROID}}}fillType', 'evenOdd')
    return v

for file, mono in [('ic_launcher_foreground.xml',False),('ic_launcher_monochrome.xml',True)]:
    v = vector(mono)
    ET.indent(v)
    ET.ElementTree(v).write(RES / 'drawable' / file,encoding='utf-8',xml_declaration=True)

def widget_vector(dark=False):
    root = ET.parse(OUT / ('home-focus-dark.svg' if dark else 'home-focus.svg')).getroot()
    v = ET.Element('vector', va(width='200dp', height='200dp', viewportWidth=210, viewportHeight=210))
    drawing = ET.SubElement(v, 'group', va(translateX=-15, translateY=-35))
    def walk(el, inherited=None):
        t = tag(el)
        if t in ('style', 'defs'):
            return
        a = dict(inherited or {'fill':'none'}); a.update(el.attrib)
        if t in ('svg','g'):
            if el.get('class') == 'svg-head-leaf':
                return  # 叶子要当天读满半小时才长，小组件上不画
            for child in el:
                walk(child, {k:value for k,value in a.items() if k in ('fill','stroke','stroke-width','stroke-linecap','stroke-linejoin')})
            return
        if t == 'ellipse' and el.get('cy') == '233':
            return  # The widget has its own surface; no ground shadow is needed.
        attrs = dict(pathData=shape(el))
        if a.get('fill','none') != 'none':
            attrs['fillColor'] = a['fill']
        if a.get('stroke','none') != 'none':
            attrs.update(strokeColor=a['stroke'], strokeWidth=a.get('stroke-width',1),
                         strokeLineCap=a.get('stroke-linecap','butt'),strokeLineJoin=a.get('stroke-linejoin','miter'))
        if 'opacity' in a:
            attrs.update(fillAlpha=a['opacity'],strokeAlpha=a['opacity'])
        ET.SubElement(drawing,'path',va(**attrs))
    walk(root)
    ET.indent(v)
    directory = RES / ('drawable-night' if dark else 'drawable')
    directory.mkdir(exist_ok=True)
    ET.ElementTree(v).write(directory / 'widget_cat.xml', encoding='utf-8', xml_declaration=True)

widget_vector()
widget_vector(True)

print(f'Imported {len(paths)} native paths, homepage states, splash, chart cats, and launcher icon.')
