"""成就页：从成就设计 HTML 生成徽章画法（Kotlin）和 16 件成就奖励的 SVG。

用法（仓库根目录）：python -X utf8 tools/import_achievements.py [成就设计 HTML，默认 design/source/achievements.html]
需要 node。

- 48 个徽章中心图形：HTML 的 renderCenterGraphic 用四个占位色跑一遍，按占位色换成参数，
  写成 app/.../ui/components/BadgeArt.kt；配色方案（8 种风格）也写进去。
- 16 件奖励（鎏金看板猫四个姿势、4 件饰品、4 张背景、4 件挂件、3 个图标）写到
  tools/achievement_rewards.json，由 tools/import_shop_assets.py 转成 VectorDrawable 放进商城目录。
"""
import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HTML = Path(sys.argv[1] if len(sys.argv) > 1 else ROOT / 'design/source/achievements.html')
OUT_KT = ROOT / 'app/src/main/java/com/kongye/reader/ui/components/BadgeArt.kt'
OUT_JSON = ROOT / 'tools/achievement_rewards.json'

# 占位色：c1 c2 c3 dark
SENTINEL = {'#0A0B01': 'c1', '#0A0B02': 'c2', '#0A0B03': 'c3', '#0A0B04': 'dark'}

html = HTML.read_text(encoding='utf-8')
start = html.index('    const FACTIONS = {')
end = html.index('    function renderBadgeSVG(item)')
script = html[start:end]
data = json.loads(subprocess.run(['node', '-'], input=f"""
const vm = require('vm');
const ctx = vm.createContext({{}});
vm.runInContext({json.dumps(script)} + `
this.F = FACTIONS; this.C = achievementCategories; this.R = ACHIEVEMENT_REWARD_SVGS;
this.icon = renderCenterGraphic; this.mascot = typeof getHauteConnectedMascotSvg === 'function' ? getHauteConnectedMascotSvg : null;`, ctx);
const icons = {{}};
for (const cat of ctx.C) for (const it of cat.items) {{
  icons[it.icon] = {{
    on: ctx.icon(it.icon, '#0A0B01', '#0A0B02', '#0A0B03', '#0A0B04', false),
    bp: ctx.icon(it.icon, '#0A0B01', '#0A0B02', '#0A0B03', '#0A0B04', true),
  }};
}}
const rewards = {{}};
for (const [k, r] of Object.entries(ctx.R)) {{
  if (k === 'c7') continue;
  rewards[k] = {{name: r.name, slot: r.slotCode, viewBox: r.viewBox, svg: r.svgBody, single: r.singleEyeSvg || null}};
}}
const poses = {{}};
if (ctx.mascot) for (const p of ['focus', 'happy', 'sleep', 'loaf']) poses[p] = ctx.mascot(p);
process.stdout.write(JSON.stringify({{factions: ctx.F, categories: ctx.C, icons, rewards, poses}}));
""", capture_output=True, text=True, encoding='utf-8', check=True, cwd=ROOT).stdout)

# 新版成就 HTML 里没有鎏金看板猫四个姿势的生成函数了：沿用上次导出的姿势
if not data['poses'] and OUT_JSON.exists():
    data['poses'] = json.loads(OUT_JSON.read_text(encoding='utf-8'))['poses']
OUT_JSON.write_text(json.dumps({'rewards': data['rewards'], 'poses': data['poses'],
                                'categories': data['categories']}, ensure_ascii=False, indent=1), encoding='utf-8')

# —— 徽章图形 → Kotlin ——
SVG_NS = 'http://www.w3.org/2000/svg'
paths = []


def path_ref(d):
    if d not in paths:
        paths.append(d)
    return f'BadgePaths.p{paths.index(d)}'


def num(v):
    v = float(str(v).removesuffix('px'))
    return f'{v:g}f' if '.' in f'{v:g}' or 'e' in f'{v:g}' else f'{v:g}.0f'


def color(v):
    v = v.strip()
    if v.upper() in SENTINEL:
        return SENTINEL[v.upper()]
    if v in ('none', 'transparent'):
        return None
    if v.startswith('#'):
        h = v[1:]
        if len(h) == 3:
            h = ''.join(c * 2 for c in h)
        return f'Color(0xFF{h.upper()})'
    named = {'white': 'Color.White', 'black': 'Color.Black'}
    if v in named:
        return named[v]
    raise ValueError(v)


def shape(el):
    t, a = el.tag.split('}')[-1], el.attrib
    if t == 'path':
        return a['d']
    if t in ('polygon', 'polyline'):
        pts = re.findall(r'-?[\d.]+', a['points'])
        xy = list(zip(pts[::2], pts[1::2]))
        return 'M' + ' L'.join(f'{x} {y}' for x, y in xy) + (' Z' if t == 'polygon' else '')
    if t == 'line':
        return f"M{a['x1']} {a['y1']} L{a['x2']} {a['y2']}"
    if t in ('circle', 'ellipse'):
        x, y = float(a['cx']), float(a['cy'])
        rx, ry = float(a.get('rx', a.get('r', 0))), float(a.get('ry', a.get('r', 0)))
        return f'M{x - rx:g} {y:g}a{rx:g} {ry:g} 0 1 0 {2 * rx:g} 0a{rx:g} {ry:g} 0 1 0 {-2 * rx:g} 0Z'
    if t == 'rect':
        x, y, w, h = (float(a.get(k, 0)) for k in ('x', 'y', 'width', 'height'))
        return f'M{x:g} {y:g}h{w:g}v{h:g}h{-w:g}Z'
    raise ValueError(t)


def transforms(value):
    out = []
    for op, args in re.findall(r'(\w+)\(([^)]+)\)', value):
        v = [num(x) for x in re.findall(r'-?[\d.]+', args)]
        if op == 'translate':
            out.append(f'translate({v[0]}, {v[1] if len(v) > 1 else "0f"})')
        elif op == 'scale':
            out.append(f'scale({v[0]}, {v[1] if len(v) > 1 else v[0]}, pivot = Offset.Zero)')
        elif op == 'rotate':
            out.append(f'rotate({v[0]}, pivot = Offset({v[1] if len(v) > 1 else "0f"}, {v[2] if len(v) > 2 else "0f"}))')
        else:
            raise ValueError(op)
    return out


INHERIT = ('fill', 'stroke', 'stroke-width', 'stroke-linecap', 'stroke-linejoin', 'opacity-group')


def emit(el, inh, pad):
    t = el.tag.split('}')[-1]
    a = dict(inh)
    for k in ('fill', 'stroke', 'stroke-width', 'stroke-linecap', 'stroke-linejoin', 'stroke-dasharray'):
        if el.get(k) is not None:
            a[k] = el.get(k)
    lines = []
    tr = el.get('transform')
    op = float(el.get('opacity', 1))
    inner_pad = pad
    if t == 'g':
        alpha = float(inh.get('alpha', 1)) * op
        body = []
        for c in el:
            body += emit(c, {**a, 'alpha': alpha}, pad + ('    ' if tr else ''))
        if tr:
            return [pad + 'withTransform({ ' + '; '.join(transforms(tr)) + ' }) {'] + body + [pad + '}']
        return body
    alpha = float(inh.get('alpha', 1)) * op
    ref = path_ref(shape(el))
    if tr:
        lines.append(pad + 'withTransform({ ' + '; '.join(transforms(tr)) + ' }) {')
        inner_pad = pad + '    '
    al = '' if alpha == 1 else f', alpha = {num(alpha)}'
    fill = None if t == 'line' else color(a.get('fill', '#000000'))
    if fill:
        lines.append(inner_pad + f'drawPath({ref}, {fill}{al})')
    stroke = color(a['stroke']) if a.get('stroke') else None
    if stroke:
        cap = {'round': 'Round', 'square': 'Square'}.get(a.get('stroke-linecap'), 'Butt')
        join = {'round': 'Round', 'bevel': 'Bevel'}.get(a.get('stroke-linejoin'), 'Miter')
        dash = ''
        if a.get('stroke-dasharray') not in (None, 'none'):
            dash = ', pathEffect = PathEffect.dashPathEffect(floatArrayOf(' + ', '.join(num(x) for x in re.findall(r'[\d.]+', a['stroke-dasharray'])) + '))'
        lines.append(inner_pad + f'drawPath({ref}, {stroke}{al}, style = Stroke({num(a.get("stroke-width", 1))}, cap = StrokeCap.{cap}, join = StrokeJoin.{join}{dash}))')
    if tr:
        lines.append(pad + '}')
    return lines


def emit_icon(svg):
    root = ET.fromstring(f'<g xmlns="{SVG_NS}">{svg}</g>')
    return emit(root, {}, '            ')


branches = []
for key, v in data['icons'].items():
    on, bp = emit_icon(v['on']), emit_icon(v['bp'])
    if on == bp:
        branches += [f'        {json.dumps(key)} -> {{', *on, '        }']
    else:
        branches += [f'        {json.dumps(key)} -> if (blueprint) {{', *bp, '        } else {', *on, '        }']

# 配色顺序：原版黑金（免费）打头，然后 2077、政府、企业、帮派；后三类里按 2077 势力从高到低
STYLE_ORDER = ['classic', 'nightcity',
               'ncpd',
               'arasaka', 'militech', 'kangtao', 'kiroshi', 'trauma',
               'tyger', 'valentinos', 'maelstrom', 'sixthstreet', 'voodooboys', 'wraiths', 'mox', 'scavengers']
order = STYLE_ORDER + [k for k in data['factions'] if k not in STYLE_ORDER]


def spaced(name):
    """中文和英文、数字之间空一格：2077夜之城 → 2077 夜之城"""
    name = re.sub(r'([A-Za-z0-9])([\u4e00-\u9fff])', r'\1 \2', name)
    return re.sub(r'([\u4e00-\u9fff])([A-Za-z0-9])', r'\1 \2', name)


factions = []
for k in order:
    f = data['factions'].get(k)
    if f is None:
        continue
    cols = ', '.join(f'Color(0xFF{f[n][1:].upper()})' for n in ('pri', 'sec', 'acc', 'plate', 'sub', 'paper', 'card', 'lockProg'))
    factions.append(f'    BadgeStyle({json.dumps(k)}, {json.dumps(spaced(f["name"]), ensure_ascii=False)}, {json.dumps(f["code"])}, {cols}),')

kt = f'''// 生成：tools/import_achievements.py（来自成就设计 HTML 的 renderCenterGraphic 和 FACTIONS）
package com.kongye.reader.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform

/** 成就页的配色方案：第一套免费，其余用叶子换 */
data class BadgeStyle(
    val id: String, val name: String, val code: String,
    val pri: Color, val sec: Color, val acc: Color, val plate: Color, val sub: Color,
    val paper: Color, val card: Color, val lockProg: Color,
)

val badgeStyles = listOf(
{chr(10).join(factions)}
)

private object BadgePaths {{
{chr(10).join(f'    val p{i} by lazy {{ svgPath({json.dumps(d)}) }}' for i, d in enumerate(paths))}
}}

/** 徽章中心图形（88x88 坐标）。点亮时 c1=paper c2=pri c3=sec dark=plate；没点亮时 blueprint=true、灰色 */
internal fun DrawScope.drawBadgeIcon(key: String, c1: Color, c2: Color, c3: Color, dark: Color, blueprint: Boolean) {{
    when (key) {{
{chr(10).join(branches)}
        else -> drawPath(svgPath("M44 28 L58 44 L44 60 L30 44 Z"), c2)
    }}
}}
'''
OUT_KT.write_text(kt, encoding='utf-8')
print(f"徽章图形 {len(data['icons'])} 个，路径 {len(paths)} 条，配色 {len(factions)} 套，奖励 {len(data['rewards'])} 件 + 看板猫 4 个姿势")
