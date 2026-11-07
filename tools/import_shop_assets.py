"""把商城素材转成 App 里用的 VectorDrawable 和目录 assets/shop.json。

用法（仓库根目录）：python tools/import_shop_assets.py [素材目录，默认 E:/Chrome/阅读] [看板 HTML]
需要：node（读 HTML 里的数据和图标生成函数）、Android Studio（用它自带的 Svg2Vector 转 SVG）。

- 生物：素材目录/生物 下的 SVG；E:/Chrome 下有同名的新版就用新版。小马宝莉是孩之宝的形象，
  转出来放 app/src/main/res-private（不进仓库）。墨团用看板 HTML 的极简风看板猫（专注坐姿）。
  Svg2Vector 不认 <style> 里的 class、<use> 上的属性，这里先把它们写回每个图形上，不然会画成黑色。
- 背景：素材目录/背景图 下的 SVG。去掉“返回首页”胶囊和所有文字，路牌上的字记下位置，由 App 自己写（门牌可改）。
- 饰品：生物/白膜配饰.html 里的 34 款配饰。配饰锚点是按 HTML 里 7 种“白膜”身体定的，这里按身体外框
  （tools/shop_measurements.json，浏览器里 getBBox 量的）等比换算到每只动物自己的坐标里。
- 挂件：背景图/白膜节日配饰.html 里 19 个节日 × 6 个位置（路牌、小屋、天空、三块空地）的挂件。
  HTML 舞台是 260x540，按路牌、小屋、太阳、空地对到背景的 400x800 坐标里（ORN_SLOTS）。
- 成就奖励：tools/achievement_rewards.json（tools/import_achievements.py 从成就 HTML 取出）里的鎏金看板猫（四个姿势）、
  4 件饰品、4 张背景、4 件挂件、3 个图标。lock 是对应成就的编号，点亮成就才能用，不用叶子买。
  奖励背景是 260x540 的布局，挂件位置按路牌、小屋、太阳、空地重新对过（REWARD_SLOTS）。
- 镇店之宝：至尊金猪皇和 8 件神装，见 tools/treasure.py。
- 图标：看板 HTML 的 getAppIconSvgMarkup：经典落日（免费）、极简（浅色，要解锁）、极简落日（要解锁），胡须压到猫头下面。
"""
import json
import math
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from svg_whiskers import whiskers_under_head  # noqa: E402
from front_cat_anchors import MASCOT as MASCOT_SLOTS, MINIMAL as MINIMAL_SLOTS  # noqa: E402

ART = Path(sys.argv[1] if len(sys.argv) > 1 else 'E:/Chrome/阅读')
BOARD = Path(sys.argv[2] if len(sys.argv) > 2 else ART / '看板+图标+开屏.html')
RES = ROOT / 'app/src/main/res'
RES_PRIVATE = ROOT / 'app/src/main/res-private'
ASSETS = ROOT / 'app/src/main/assets'
STUDIO = Path('D:/Program/Android/Android Studio')
MEASURE = json.loads((ROOT / 'tools/shop_measurements.json').read_text(encoding='utf-8'))

SVG_NS = 'http://www.w3.org/2000/svg'
ET.register_namespace('', SVG_NS)
ET.register_namespace('xlink', 'http://www.w3.org/1999/xlink')

NEWER = Path('E:/Chrome')  # 新画的版本先放在这里，和素材目录里同名的旧版二选一

# —— 商品清单：文件名 → (编号, 名字, 价格叶子)；商城按组排：空叶自己的猫、小动物、小马宝莉、表情 ——
KONGYE = [('极简猫.svg', 'mincat', '墨团', 200)]
ANIMALS = [
    ('小猫.svg', 'cat', '小猫', 120), ('小狗.svg', 'dog', '小狗', 120), ('小猪.svg', 'pig', '小猪', 120),
    ('小羊.svg', 'sheep', '小羊', 120), ('小老鼠.svg', 'mouse', '小老鼠', 120), ('小鸡.svg', 'chicken', '小鸡', 120),
    ('小鸭.svg', 'duck', '小鸭', 120), ('小牛.svg', 'cow', '小牛', 150), ('小马.svg', 'horse', '小马', 150),
    ('小猴.svg', 'monkey', '小猴', 150), ('乌鸫.svg', 'blackbird', '乌鸫', 150), ('乌鸦.svg', 'crow', '乌鸦', 150),
    ('玄凤鹦鹉.svg', 'cockatiel', '玄凤鹦鹉', 200), ('梅花鹿.svg', 'deer', '梅花鹿', 200), ('麋鹿.svg', 'elk', '麋鹿', 200),
    ('耳廓狐.svg', 'fennec', '耳廓狐', 200), ('长颈鹿.svg', 'giraffe', '长颈鹿', 200), ('火烈鸟.svg', 'flamingo', '火烈鸟', 200),
    ('小老虎.svg', 'tiger', '小老虎', 250), ('狮子.svg', 'lion', '小狮子', 250), ('雪豹.svg', 'snowleopard', '雪豹', 250),
    ('飞猪.svg', 'flypig', '飞猪', 300), ('独角兽.svg', 'unicorn', '独角兽', 300),
]
PONIES = [
    ('碧琪.svg', 'pinkie', '碧琪', 500), ('苹果嘉儿.svg', 'applejack', '苹果嘉儿', 500), ('柔柔.svg', 'fluttershy', '柔柔', 500),
    ('云宝.svg', 'rainbow', '云宝', 500), ('珍奇.svg', 'rarity', '珍奇', 500), ('紫悦.svg', 'twilight', '紫悦', 500),
    ('宇宙公主Q版.svg', 'celestia2', '宇宙公主·Q版', 600), ('月亮公主Q版.svg', 'luna2', '月亮公主·Q版', 600),
    ('宇宙公主.svg', 'celestia', '宇宙公主', 800), ('月亮公主.svg', 'luna', '月亮公主', 800),
]
EMOJI = [('emoji/谷歌猪.svg', 'notopig', '猪猪', 300)]
BACKGROUNDS = [
    ('默认农场.svg', 'farm', '默认农场', 0), ('农场plus.svg', 'farmplus', '农场 Plus', 200), ('春日.svg', 'spring', '春日', 200),
    ('盛夏.svg', 'summer', '盛夏', 200), ('金秋.svg', 'autumn', '金秋', 200), ('冬雪.svg', 'winter', '冬雪', 200),
    ('樱花.svg', 'sakura', '樱花庭', 300), ('沙滩.svg', 'beach', '海岛', 300), ('绿洲.svg', 'oasis', '绿洲', 300),
    ('水墨.svg', 'ink', '水墨', 300), ('星夜.svg', 'starry', '星夜', 400), ('月球.svg', 'moon', '月球', 400),
    ('云端.svg', 'cloud', '云端', 400), ('霓虹.svg', 'neon', '霓虹', 400), ('未来.svg', 'future', '未来', 400),
]
ACC_PRICE = {'A': 80, 'B': 60, 'C': 60, 'D': 50, 'E': 60, 'F': 100, 'G': 80, 'H': 120}
# 白膜配饰.html 里还是改名前的文件名；没列到的归到最像的那一类
ARCH_ALIAS = {
    '小猫.svg': '猫.svg', '小狗.svg': '狗.svg', '小猪.svg': '猪.svg', '小羊.svg': '绵羊.svg', '小老鼠.svg': '老鼠.svg',
    '小鸡.svg': '鸡.svg', '小鸭.svg': '鸭.svg', '小牛.svg': '牛.svg', '小马.svg': '马.svg', '小猴.svg': '猴子.svg',
    '小老虎.svg': '老虎.svg', '宇宙公主Q版.svg': '宇宙公主fake.svg', '月亮公主Q版.svg': '月亮公主fake.svg', '极简猫.svg': '猫.svg',
}
EXTRA_ARCH = {'月亮公主.svg': 'type7_alicorn_royal'}
# 挂件位置（背景 400x800 坐标）：x, y, 缩放。HTML 舞台的锚点按旁边的路牌、小屋、太阳、空地对过来
ORN_SLOTS = {
    'A': {'x': 125, 'y': 121, 's': 1.6, 'name': '路牌'},
    'B': {'x': 288, 'y': 105, 's': 1.3, 'name': '小屋'},
    'C': {'x': 219, 'y': 56, 's': 1.6, 'name': '天空'},
    'D': {'x': 121, 'y': 283, 's': 1.6, 'name': '上面的空地'},
    'E': {'x': 286, 'y': 480, 's': 1.6, 'name': '中间的空地'},
    'F': {'x': 157, 'y': 685, 's': 1.8, 'name': '下面的池塘'},
}
ORN_PRICE = {'A': 40, 'B': 60, 'C': 60, 'D': 40, 'E': 40, 'F': 50}
# 成就奖励背景（260x540，默认农场的另一种排法）上的挂件位置：
# 400x800 的锚点相对路牌、小屋、太阳、空地的偏移按 0.655 缩小，再挂到这张图的同一处
REWARD_SLOTS = {
    'A': {'x': 79, 'y': 74, 's': 1.05, 'name': '路牌'},
    'B': {'x': 186, 'y': 68.3, 's': 0.85, 'name': '小屋'},
    'C': {'x': 143, 'y': 37.5, 's': 1.05, 'name': '天空'},
    'D': {'x': 78.4, 'y': 193.5, 's': 1.05, 'name': '上面的空地'},
    'E': {'x': 186.3, 'y': 324.1, 's': 1.05, 'name': '中间的空地'},
    'F': {'x': 102, 'y': 462, 's': 1.18, 'name': '下面的池塘'},
}
REWARD_SIGN = {'x': 65.3, 'y': 87.5, 'size': 8.5, 'color': '#FFFFFF', 'bold': True, 'spacing': 0, 'kai': False, 'text': ''}
REWARDS = json.loads((ROOT / 'tools/achievement_rewards.json').read_text(encoding='utf-8'))
REWARD_BG = {'c14': 'rainwindow', 'c15': 'sunlibrary', 'c16': 'forest', 'c17': 'galaxytrain'}
REWARD_ICON = {'e7': ('gold', '#111115'), 'e8': ('neon', '#0D0B18'), 'e9': ('pixel', '#F4EFE6')}
# 极简版成就 HTML：鎏金墨团（成就「墨团猫猫」的奖励）和三个墨团版的成就图标（同一个成就解锁两款）。
# 抽出来存到 tools/minimal_rewards.json，HTML 不在本机时沿用上次抽出的
MIN_ACH = Path('E:/Chrome/阅读/成就系统_极简.html')
MIN_REWARDS_JSON = ROOT / 'tools/minimal_rewards.json'
REWARD_ICON_MIN = {'e7': ('mingold', '#111115'), 'e8': ('minneon', '#0D0B18'), 'e9': ('minpixel', '#F4EFE6')}
# 谷歌猪是 emoji：朝左、几乎占满画布，比别的动物大一圈。翻成朝右，放进更大的画布（size），
# 让身体宽度和别的动物差不多，脚落在画布 82% 高的地方（和别的动物在格子里站得一样低）
REFRAME = {'notopig': {'mirror': True, 'size': 236, 'feet': 0.82},
           # 乌鸦几乎占满画布，比乌鸫大一圈：放进 150 的画布，宽高和乌鸫差不多，脚和乌鸫一样落在 84% 的地方
           'crow': {'size': 150, 'feet': 0.842}}


def q(tag):
    return f'{{{SVG_NS}}}{tag}'


def local(el):
    return el.tag.split('}')[-1]


def minimal_rewards():
    if MIN_ACH.exists():
        h = MIN_ACH.read_text(encoding='utf-8')
        b0 = h.index('    const ACHIEVEMENT_REWARD_SVGS = {')
        b1 = h.index('    // 实机试穿状态')
        out = node_json(f"""
const vm = require('vm');
const ctx = vm.createContext({{}});
vm.runInContext({json.dumps(h[b0:b1])} + ';this.R = ACHIEVEMENT_REWARD_SVGS;', ctx);
const r = ctx.R, out = {{}};
for (const k of ['c7', 'e7', 'e8', 'e9']) out[k] = {{name: r[k].name, svg: r[k].svgBody}};
process.stdout.write(JSON.stringify(out));
""")
        MIN_REWARDS_JSON.write_text(json.dumps(out, ensure_ascii=False, indent=1), encoding='utf-8')
    return json.loads(MIN_REWARDS_JSON.read_text(encoding='utf-8'))


def node_json(script: str):
    # 脚本里嵌着整段 HTML，太长放不进命令行，从标准输入喂给 node
    r = subprocess.run(['node', '-'], input=script, cwd=ROOT, capture_output=True, text=True, encoding='utf-8')
    if r.returncode:
        raise SystemExit(r.stderr)
    return json.loads(r.stdout)


XLINK = '{http://www.w3.org/1999/xlink}href'
# 会被子元素继承的样式（opacity、clip-path 不继承，留在原处）
INHERITED = ('fill', 'fill-opacity', 'fill-rule', 'stroke', 'stroke-width', 'stroke-opacity', 'stroke-linecap',
             'stroke-linejoin', 'stroke-miterlimit', 'stroke-dasharray', 'clip-rule')
SHAPES = ('path', 'rect', 'circle', 'ellipse', 'line', 'polyline', 'polygon')


def inline_uses(root):
    """Svg2Vector 不认 <use>：换成一份被引用元素。<use> 上的 fill、stroke 等放在外面一层 <g> 上，
    按继承规则往下传（被引用元素自己写了的优先）；x/y/transform 也放在这层 <g> 上。
    clipPath 里不能套 <g>，直接把变换写到拷贝上"""
    ids = {el.get('id'): el for el in root.iter() if el.get('id')}
    for parent in list(root.iter()):
        for i, child in enumerate(list(parent)):
            if local(child) != 'use':
                continue
            ref = (child.get('href') or child.get(XLINK) or '').lstrip('#')
            target = ids.get(ref)
            parent.remove(child)
            if target is None:
                continue
            copy = ET.fromstring(ET.tostring(target))
            copy.attrib.pop('id', None)
            tr = f"translate({child.get('x', '0')},{child.get('y', '0')}) {child.get('transform', '')}".strip()
            if tr == 'translate(0,0)':
                tr = ''
            if local(parent) == 'clipPath':
                if tr:
                    copy.set('transform', f"{tr} {copy.get('transform', '')}".strip())
                parent.insert(i, copy)
                continue
            keep = {k: v for k, v in child.attrib.items() if k in INHERITED or k in ('opacity', 'clip-path')}
            g = ET.Element(q('g'), keep)
            if tr:
                g.set('transform', tr)
            g.append(copy)
            parent.insert(i, g)


def parse_decls(text):
    out = {}
    for part in text.split(';'):
        if ':' in part:
            k, v = part.split(':', 1)
            out[k.strip()] = v.strip()
    return out


def inline_css(root):
    """把 <style> 里的简单选择器（.class、标签、#id、标签.class）和 style="" 写成属性。
    优先级照 CSS：属性 < 样式表（先比选择器具体程度，再比先后）< style="" """
    rules = []
    for st in [e for e in root.iter() if local(e) == 'style']:
        css = re.sub(r'/\*.*?\*/', '', st.text or '', flags=re.S)
        css = re.sub(r'@keyframes[^{]*\{(?:[^{}]*\{[^{}]*\})*[^{}]*\}', '', css)
        for sel, body in re.findall(r'([^{}]+)\{([^{}]*)\}', css):
            decls = {k: v for k, v in parse_decls(body).items() if k not in ('animation', 'transform-origin', 'transition', 'cursor')}
            for one in sel.split(','):
                one = one.strip()
                m = re.fullmatch(r'([a-zA-Z]*)((?:\.[\w-]+)*)(#[\w-]+)?', one)
                if not one or not m:
                    continue
                tag, classes, hid = m.group(1), [c for c in m.group(2).split('.') if c], (m.group(3) or '')[1:]
                spec = (1 if hid else 0, len(classes), 1 if tag else 0)
                rules.append((spec, len(rules), tag, classes, hid, decls))
    rules.sort(key=lambda r: (r[0], r[1]))
    for el in root.iter():
        cls = (el.get('class') or '').split()
        for _, _, tag, classes, hid, decls in rules:
            if (tag and local(el) != tag) or (hid and el.get('id') != hid) or any(c not in cls for c in classes):
                continue
            for k, v in decls.items():
                el.set(k, v)
        for k, v in parse_decls(el.attrib.pop('style', '')).items():
            el.set(k, v)
        el.attrib.pop('class', None)
    for parent in list(root.iter()):
        for st in [e for e in parent if local(e) == 'style']:
            parent.remove(st)


def push_down(el, inherited=None):
    """继承来的 fill、stroke 等直接写到每个图形上（Svg2Vector 不一定照 <g> 往下传）"""
    mine = dict(inherited or {})
    for k in INHERITED:
        if el.get(k) is not None:
            mine[k] = el.get(k)
    if local(el) in SHAPES:
        for k, v in mine.items():
            if el.get(k) is None:
                el.set(k, v)
    for child in el:
        if local(child) not in ('defs', 'clipPath', 'linearGradient', 'radialGradient'):
            push_down(child, mine)


def prepare(root):
    inline_css(root)
    inline_uses(root)
    push_down(root)


def translate_of(el):
    m = re.findall(r'translate\(\s*(-?[\d.]+)[ ,]+(-?[\d.]+)\s*\)', el.get('transform', ''))
    return (float(m[0][0]), float(m[0][1])) if m else (0.0, 0.0)


def convert(jobs):
    """jobs: [(svg 文本, 输出 xml 路径)]，一次 java 进程转完"""
    if not jobs:
        return
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        cp = f"{STUDIO / 'plugins/android/lib'}\\*;{STUDIO / 'lib'}\\*"
        javac = STUDIO / 'jbr/bin/javac.exe'
        java = STUDIO / 'jbr/bin/java.exe'
        subprocess.run([str(javac), '-cp', cp, '-d', str(tmp), str(ROOT / 'tools/Svg2Vd.java')], check=True)
        args = []
        for k, (svg, out) in enumerate(jobs):
            src = tmp / f'{k}.svg'
            src.write_text(svg, encoding='utf-8')
            out.parent.mkdir(parents=True, exist_ok=True)
            args += [str(src), str(out)]
        r = subprocess.run([str(java), '-cp', f'{tmp};{cp}', 'Svg2Vd', *args], capture_output=True, text=True, encoding='utf-8')
        if r.returncode:
            raise SystemExit(r.stderr)
        for line in r.stdout.splitlines():
            print(' ', line[:160])


def viewbox(root):
    return [float(v) for v in re.split(r'[ ,]+', root.get('viewBox').strip())]


# —— 1. 白膜配饰.html：身体类型、锚点、配饰 ——
acc_html = (ART / '生物/白膜配饰.html').read_text(encoding='utf-8')
start, end = acc_html.index('const ARCHETYPES = {'), acc_html.index('let currentArchetype')
data = node_json(f"""
const vm = require('vm');
const ctx = vm.createContext({{}});
vm.runInContext({json.dumps(acc_html[start:end])} + ';this.A=ARCHETYPES;this.C=CATALOG;', ctx);
const arch = {{}};
for (const [k, a] of Object.entries(ctx.A)) arch[k] = {{files: a.files, dualEyes: a.dualEyes, facingLeft: a.facingLeft, anchors: a.anchors}};
process.stdout.write(JSON.stringify({{arch, catalog: ctx.C}}));
""")
file_arch = {f: k for k, a in data['arch'].items() for f in a['files']}
file_arch.update(EXTRA_ARCH)

defs_start = acc_html.index('<defs id="flat-accessory-defs"')
defs_end = acc_html.index('</defs>', defs_start) + len('</defs>')
defs = ET.fromstring(acc_html[defs_start:defs_end].replace('<defs', f'<defs xmlns="{SVG_NS}"', 1))
acc_shapes = {g.get('id'): g for g in defs if local(g) == 'g'}
shared_defs = [el for el in defs if local(el) != 'g']

jobs = []
catalog = {'animals': [], 'backgrounds': [], 'accessories': [], 'ornaments': [], 'festivals': [], 'ornamentSlots': ORN_SLOTS, 'icons': []}

for slot in data['catalog']:
    for item in slot['items']:
        aid = item['id']
        entry = {'id': aid, 'name': item['name'], 'slot': slot['code'], 'price': ACC_PRICE[slot['code']]}
        for variant in ([aid, f'{aid}-dual'] if f'{aid}-dual' in acc_shapes else [aid]):
            x, y, w, h = MEASURE['acc'][variant]
            pad = 4
            box = [round(x - pad, 1), round(y - pad, 1), round(w + 2 * pad, 1), round(h + 2 * pad, 1)]
            svg = ET.Element(q('svg'), {'viewBox': ' '.join(map(str, box))})
            if shared_defs:
                d = ET.SubElement(svg, q('defs'))
                d.extend(shared_defs)
            g = ET.fromstring(ET.tostring(acc_shapes[variant]))
            g.attrib.pop('id', None)
            svg.append(g)
            res = 'shop_acc_' + variant[4:].replace('-', '_').lower()
            jobs.append((ET.tostring(svg, encoding='unicode'), RES / 'drawable' / f'{res}.xml'))
            key = 'dual' if variant.endswith('-dual') else 'main'
            entry[key] = {'res': res, 'box': box}
        catalog['accessories'].append(entry)


# —— 2. 生物：转图，并把锚点换算到每只动物自己的坐标 ——
def map_anchor(a, src_box, dst_box):
    ax, ay, aw, ah = src_box
    bx, by, bw, bh = dst_box
    k = math.sqrt((bw / aw) * (bh / ah))
    return {'x': round(bx + (a['x'] - ax) / aw * bw, 2), 'y': round(by + (a['y'] - ay) / ah * bh, 2),
            's': round(a.get('scale', 1) * k, 4), 'r': a.get('rotate', 0) or 0}


def mincat_svg():
    """看板 HTML 的极简风看板猫（专注坐姿）：去掉动画和地上的影子"""
    board = BOARD.read_text(encoding='utf-8')
    b0, b1 = board.index('        const state = {'), board.index('        function renderAllAssets()')
    svg = node_json(f"""
const vm = require('vm');
const ctx = vm.createContext({{}});
vm.runInContext({json.dumps(board[b0:b1])} + ';this.S=state;', ctx);
ctx.S.catStyle = 'minimal';
process.stdout.write(JSON.stringify(vm.runInContext("getMascotSvgMarkup('focus', false)", ctx)));
""")
    root = ET.fromstring(svg)
    for el in [e for e in root if local(e) == 'ellipse']:
        root.remove(el)
    return root


def source(file, private):
    name = Path(file).name
    if (NEWER / name).exists():
        return NEWER / name
    return ART / '生物' / (f'小马宝莉/{file}' if private else file)


for group, items, private in (('kongye', KONGYE, False), ('animal', ANIMALS, False), ('emoji', EMOJI, False), ('pony', PONIES, True)):
    for file, aid, name, price in items:
        if aid == 'mincat':
            root = mincat_svg()
        else:
            path = source(file, private)
            if not path.exists():
                print('  缺素材', path)
                continue
            root = ET.parse(path).getroot()
        prepare(root)
        vb = viewbox(root)
        base = Path(file).name
        bbox = MEASURE['animals'][base]
        if aid in REFRAME:
            rf = REFRAME[aid]
            x, y, w, h = bbox
            if rf.get('mirror'):
                x = vb[0] + vb[2] - (x - vb[0] + w)
            size = rf['size']
            tx, ty = size / 2 - (x + w / 2), size * rf['feet'] - (y + h)
            mirror = f" translate({vb[0] * 2 + vb[2]},0) scale(-1,1)" if rf.get('mirror') else ''
            g = ET.Element(q('g'), {'transform': f'translate({tx:.2f},{ty:.2f}){mirror}'})
            for child in list(root):
                if local(child) == 'defs':
                    continue
                root.remove(child)
                g.append(child)
            root.append(g)
            root.set('viewBox', f'0 0 {size} {size}')
            root.attrib.pop('width', None)
            root.attrib.pop('height', None)
            vb = [0.0, 0.0, float(size), float(size)]
            bbox = [x + tx, y + ty, w, h]
        arch_id = file_arch[ARCH_ALIAS.get(base, base)]
        arch = data['arch'][arch_id]
        slots = {}
        for code, anchor in arch['anchors'].items():
            s = map_anchor(anchor, MEASURE['arch'][arch_id], bbox)
            over = {k: map_anchor({**anchor, **v}, MEASURE['arch'][arch_id], bbox)
                    for k, v in (anchor.get('itemOverrides') or {}).items()}
            if over:
                s['items'] = over
            slots[code] = s
        if aid == 'mincat':
            # 正脸坐着的墨团：白膜Ⅰ类的头在身体右边，按外框换算会偏到右耳上，改用照它自己的头、眼、嘴、尾巴摆的锚点
            slots = MINIMAL_SLOTS
        res = f'shop_animal_{aid}'
        jobs.append((ET.tostring(root, encoding='unicode'), (RES_PRIVATE if private else RES) / 'drawable' / f'{res}.xml'))
        catalog['animals'].append({'id': aid, 'name': name, 'price': price, 'group': group, 'res': res, 'private': private, 'vb': vb,
                                   'dual': arch['dualEyes'], 'flip': arch['facingLeft'], 'slots': slots})

# 成就「看板猫猫」：鎏金看板猫，240x260 和看板猫同一套坐标；四个姿势各一张图，首页按状态换
gold_res = {}
for pose, markup in REWARDS['poses'].items():
    root = ET.fromstring(f'<svg xmlns="{SVG_NS}" viewBox="0 0 240 260">{markup}</svg>')
    prepare(root)
    res = 'shop_animal_goldcat' if pose == 'focus' else f'shop_goldcat_{pose}'
    gold_res[pose] = res
    jobs.append((ET.tostring(root, encoding='unicode'), RES / 'drawable' / f'{res}.xml'))
arch = data['arch'][file_arch['猫.svg']]
# 和看板猫同一套骨架、正脸坐着：用看板猫的锚点（tools/front_cat_anchors.py），看板猫自己戴饰品也借这一套
gold_slots = MASCOT_SLOTS
catalog['animals'].insert(1, {'id': 'goldcat', 'name': '鎏金看板猫', 'price': 0, 'lock': 'c7', 'group': 'kongye',
                              'res': gold_res['focus'], 'private': False, 'vb': [0.0, 0.0, 240.0, 260.0],
                              'dual': arch['dualEyes'], 'flip': arch['facingLeft'], 'slots': gold_slots,
                              'poses': {k: v for k, v in gold_res.items() if k != 'focus'}})

# 成就「墨团猫猫」：鎏金墨团，200x220 和墨团同一套坐标；身体由 App 自己画（四个姿势都有），这张图只做目录里的占位
MIN_REWARDS = minimal_rewards()
_root = ET.fromstring(f'<svg xmlns="{SVG_NS}" viewBox="0 0 200 220">{MIN_REWARDS["c7"]["svg"]}</svg>')
prepare(_root)
jobs.append((ET.tostring(_root, encoding='unicode'), RES / 'drawable' / 'shop_animal_goldmin.xml'))
_mincat = next(a for a in catalog['animals'] if a['id'] == 'mincat')
catalog['animals'].insert(next(i for i, a in enumerate(catalog['animals']) if a['id'] == 'goldcat') + 1,
                          {'id': 'goldmin', 'name': '鎏金墨团', 'price': 0, 'lock': 'c7m', 'group': 'kongye',
                           'res': 'shop_animal_goldmin', 'private': False, 'vb': [0.0, 0.0, 200.0, 220.0],
                           'dual': _mincat['dual'], 'flip': False, 'slots': MINIMAL_SLOTS})

# 镇店之宝：至尊金猪皇（9999 叶子）放在空叶组最后，8 件神装由成就 c0 解锁（tools/treasure.py）
import treasure  # noqa: E402
_pig, _pig_accs, _pig_jobs = treasure.entries()
catalog['animals'].insert(max(i + 1 for i, a in enumerate(catalog['animals']) if a['group'] == 'kongye'), _pig)
catalog['accessories'] += _pig_accs
jobs += _pig_jobs

# 成就奖励饰品：和白膜配饰同样以部位锚点为原点
for aid in ('c10', 'c11', 'c12', 'c13'):
    r = REWARDS['rewards'][aid]
    entry = {'id': f'ach_{aid}', 'name': r['name'], 'slot': r['slot'], 'price': 0, 'lock': aid}
    variants = [('main', r['single'], f'{aid}-single'), ('dual', r['svg'], aid)] if r['single'] else [('main', r['svg'], aid)]
    for key, markup, mkey in variants:
        x, y, w, h = MEASURE['reward'][mkey]
        box = [round(x - 4, 1), round(y - 4, 1), round(w + 8, 1), round(h + 8, 1)]
        root = ET.fromstring(f'<svg xmlns="{SVG_NS}" viewBox="{" ".join(map(str, box))}">{markup}</svg>')
        prepare(root)
        res = f'shop_acc_ach_{aid}' + ('_dual' if key == 'dual' else '')
        jobs.append((ET.tostring(root, encoding='unicode'), RES / 'drawable' / f'{res}.xml'))
        entry[key] = {'res': res, 'box': box}
    catalog['accessories'].append(entry)

# —— 3. 背景：去掉文字和返回胶囊，路牌文字的位置留给 App ——
for file, bid, name, price in BACKGROUNDS:
    root = ET.parse(ART / '背景图' / file).getroot()
    prepare(root)
    sign = None

    def walk(el, ox, oy, parent):
        global sign
        tx, ty = translate_of(el)
        ox, oy = ox + tx, oy + ty
        texts = [t for t in el.iter(q('text'))]
        if local(el) == 'g' and any((t.text or '').strip() == '返回首页' for t in texts) and parent is not None:
            parent.remove(el)
            return
        for child in list(el):
            if local(child) == 'text':
                if (child.text or '').strip() != '返回首页' and sign is None:
                    sign = {'x': round(ox + float(child.get('x', 0)), 2), 'y': round(oy + float(child.get('y', 0)), 2),
                            'size': float(child.get('font-size', 13)), 'color': child.get('fill', '#FFFFFF'),
                            'bold': child.get('font-weight') == 'bold', 'spacing': float(child.get('letter-spacing', 0)),
                            'kai': 'Kaiti' in child.get('font-family', '') or 'KaiTi' in child.get('font-family', ''),
                            'text': (child.text or '').strip()}
                el.remove(child)
            else:
                walk(child, ox, oy, el)

    walk(root, 0.0, 0.0, None)
    res = f'shop_bg_{bid}'
    jobs.append((ET.tostring(root, encoding='unicode'), RES / 'drawable' / f'{res}.xml'))
    catalog['backgrounds'].append({'id': bid, 'name': name, 'price': price, 'res': res, 'vb': viewbox(root), 'sign': sign})

# 成就奖励背景：去掉底部的返回胶囊
for aid, bid in REWARD_BG.items():
    r = REWARDS['rewards'][aid]
    # 新画的版本优先：E:/Chrome/<编号>.svg（比如 bg-sun-library.svg），其次是收进仓库的 design/source/<编号>.svg
    name = f"bg-{ {'rainwindow': 'rain-window', 'sunlibrary': 'sun-library', 'forest': 'forest-sanctuary', 'galaxytrain': 'galaxy-train'}[bid] }.svg"
    newer = next((d / name for d in (NEWER, ROOT / 'design/source') if (d / name).exists()), None)
    if newer:
        root = ET.parse(newer).getroot()
        root.attrib.pop('width', None)
        root.attrib.pop('height', None)
    else:
        root = ET.fromstring(f'<svg xmlns="{SVG_NS}" viewBox="0 0 260 540">{r["svg"]}</svg>')
    for parent in list(root.iter()):
        for child in list(parent):
            if 'translate(130, 510)' in (child.get('transform') or ''):
                parent.remove(child)
    prepare(root)
    res = f'shop_bg_{bid}'
    jobs.append((ET.tostring(root, encoding='unicode'), RES / 'drawable' / f'{res}.xml'))
    catalog['backgrounds'].append({'id': bid, 'name': r['name'], 'price': 0, 'lock': aid, 'res': res,
                                   'vb': [0.0, 0.0, 260.0, 540.0], 'sign': REWARD_SIGN, 'slots': REWARD_SLOTS})

# —— 4. 节日挂件：每件按自己的外框转成小图，App 按 ORN_SLOTS 摆到背景上 ——
orn_html = (ART / '背景图/白膜节日配饰.html').read_text(encoding='utf-8')
o0, o1 = orn_html.index('const SCENE_SLOTS'), orn_html.index('const slotOffsets')
fests = node_json(f"""
const vm = require('vm');
const ctx = vm.createContext({{}});
vm.runInContext({json.dumps(orn_html[o0:o1])}.replace(/\\b(const|let) /g, 'var '), ctx);
process.stdout.write(JSON.stringify(ctx.FESTIVALS.map(f => ({{id: f.id, name: f.name, color: f.color, items: f.items}}))));
""")
for fest in fests:
    key = fest['id'].split('_', 1)[1]
    catalog['festivals'].append({'id': key, 'name': fest['name'], 'color': fest['color']})
    for slot, item in fest['items'].items():
        x, y, w, h = MEASURE['orn'][f"{fest['id']}__{slot}"]
        pad = 3
        box = [round(x - pad, 2), round(y - pad, 2), round(w + 2 * pad, 2), round(h + 2 * pad, 2)]
        root = ET.fromstring(f'<svg xmlns="{SVG_NS}" viewBox="{" ".join(map(str, box))}">{item["svg"]}</svg>')
        prepare(root)
        res = f'shop_orn_{key}_{slot.lower()}'
        jobs.append((ET.tostring(root, encoding='unicode'), RES / 'drawable' / f'{res}.xml'))
        catalog['ornaments'].append({'id': f'orn_{key}_{slot.lower()}', 'name': item['name'], 'fest': key, 'slot': slot,
                                     'price': ORN_PRICE[slot], 'res': res, 'box': box})

# 成就奖励挂件：单独一组「成就奖励」
catalog['festivals'].append({'id': 'achieve', 'name': '成就奖励', 'color': '#D89B3C'})
for aid in ('c18', 'c19', 'c20', 'c21'):
    r = REWARDS['rewards'][aid]
    x, y, w, h = MEASURE['reward'][aid]
    box = [round(x - 3, 2), round(y - 3, 2), round(w + 6, 2), round(h + 6, 2)]
    root = ET.fromstring(f'<svg xmlns="{SVG_NS}" viewBox="{" ".join(map(str, box))}">{r["svg"]}</svg>')
    prepare(root)
    res = f'shop_orn_ach_{aid}'
    jobs.append((ET.tostring(root, encoding='unicode'), RES / 'drawable' / f'{res}.xml'))
    catalog['ornaments'].append({'id': f'orn_ach_{aid}', 'name': r['name'], 'fest': 'achieve', 'slot': r['slot'],
                                 'price': 0, 'lock': aid, 'res': res, 'box': box})

# —— 5. 图标：看板 HTML 生成落日版、浅色极简版 ——
board = BOARD.read_text(encoding='utf-8')
b0, b1 = board.index('        const state = {'), board.index('        function renderAllAssets()')
icons = node_json(f"""
const vm = require('vm');
const ctx = vm.createContext({{}});
vm.runInContext({json.dumps(board[b0:b1])} + ';this.S=state;', ctx);
const out = {{}};
ctx.S.catStyle = 'classic';
out.sunset = vm.runInContext("getAppIconSvgMarkup('sunset')", ctx);
ctx.S.catStyle = 'minimal';
out.minimal = vm.runInContext("getAppIconSvgMarkup('sunset')", ctx);
out.minlight = vm.runInContext("getAppIconSvgMarkup('light')", ctx);
process.stdout.write(JSON.stringify(out));
""")
ANDROID = 'http://schemas.android.com/apk/res/android'
icon_bg = {'sunset': '#FAF8F5', 'minimal': '#FAF8F5', 'minlight': '#FFFFFF', **{k: c for k, c in REWARD_ICON.values()},
           **{k: c for k, c in REWARD_ICON_MIN.values()}}
icon_jobs = []
for key, markup in icons.items():
    markup = whiskers_under_head(markup)
    root = ET.fromstring(markup)
    # 底板交给自适应图标的背景层，前景只留猫、叶子和太阳
    for el in [e for e in root if local(e) == 'rect']:
        root.remove(el)
    (ROOT / f'design/generated/icon-{key}.svg').write_text(whiskers_under_head(markup), encoding='utf-8')
    out = RES / 'drawable' / f'ic_launcher_fg_{key}.xml'
    icon_jobs.append((ET.tostring(root, encoding='unicode'), out))
# 成就奖励图标：底板（大圆角矩形和装饰边框）交给背景层
for aid, key, markup in [(a, k, REWARDS['rewards'][a]['svg']) for a, (k, _) in REWARD_ICON.items()] +         [(a, k, MIN_REWARDS[a]['svg']) for a, (k, _) in REWARD_ICON_MIN.items()]:
    root = ET.fromstring(f'<svg xmlns="{SVG_NS}" viewBox="0 0 512 512">{markup}</svg>')
    for parent in list(root.iter()):
        for child in list(parent):
            if local(child) == 'rect' and float(child.get('width', 0)) >= 400:
                parent.remove(child)
    prepare(root)
    icon_jobs.append((ET.tostring(root, encoding='unicode'), RES / 'drawable' / f'ic_launcher_fg_{key}.xml'))
jobs += icon_jobs
# 名字「款式·猫」：看板猫的是经典，墨团的是极简；同一款的经典、极简挨着放
catalog['icons'] = [
    {'id': 'default', 'name': '浅色·经典', 'price': 0},
    {'id': 'minlight', 'name': '浅色·极简', 'price': 200},
    {'id': 'sunset', 'name': '落日·经典', 'price': 200},
    {'id': 'minimal', 'name': '落日·极简', 'price': 300},
]
for aid, (key, _) in REWARD_ICON.items():
    style = REWARDS['rewards'][aid]['name'].removesuffix('图标')
    catalog['icons'] += [{'id': key, 'name': f'{style}·经典', 'price': 0, 'lock': aid},
                         {'id': REWARD_ICON_MIN[aid][0], 'name': f'{style}·极简', 'price': 0, 'lock': aid}]

# —— 转换、收尾 ——
if RES_PRIVATE.exists():
    shutil.rmtree(RES_PRIVATE)
for old in (RES / 'drawable').glob('shop_*.xml'):
    old.unlink()
print(f'转换 {len(jobs)} 个 SVG …')
convert(jobs)
from split_animal_shadows import split_animal_shadows
split_animal_shadows(RES)
split_animal_shadows(RES_PRIVATE)

# 图标前景缩进自适应图标的安全区（和 import_visual_assets.py 的默认图标一致）
ET.register_namespace('android', ANDROID)
ET.register_namespace('aapt', 'http://schemas.android.com/aapt')
for _, out in icon_jobs:
    v = ET.parse(out).getroot()
    v.set(f'{{{ANDROID}}}width', '108dp')
    v.set(f'{{{ANDROID}}}height', '108dp')
    g = ET.Element('group', {f'{{{ANDROID}}}scaleX': '0.82', f'{{{ANDROID}}}scaleY': '0.82',
                             f'{{{ANDROID}}}translateX': '46.08', f'{{{ANDROID}}}translateY': '46.08'})
    for child in list(v):
        v.remove(child)
        g.append(child)
    v.append(g)
    ET.indent(v)
    ET.ElementTree(v).write(out, encoding='utf-8', xml_declaration=True)
for key, color in icon_bg.items():
    (RES / 'mipmap-anydpi-v26' / f'ic_launcher_{key}.xml').write_text(f'''<?xml version="1.0" encoding="utf-8"?>
<!-- 生成：tools/import_shop_assets.py -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_bg_{key}" />
    <foreground android:drawable="@drawable/ic_launcher_fg_{key}" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
''', encoding='utf-8')
(RES / 'values' / 'icon_colors.xml').write_text('<?xml version="1.0" encoding="utf-8"?>\n<!-- 生成：tools/import_shop_assets.py -->\n<resources>\n' +
                                               ''.join(f'    <color name="ic_launcher_bg_{k}">{c}</color>\n' for k, c in icon_bg.items()) +
                                               '</resources>\n', encoding='utf-8')
# 商城素材只按名字查（getIdentifier），告诉资源压缩别删
(RES / 'raw').mkdir(exist_ok=True)
(RES / 'raw' / 'shop_keep.xml').write_text('<?xml version="1.0" encoding="utf-8"?>\n<resources xmlns:tools="http://schemas.android.com/tools"\n    tools:keep="@drawable/shop_*" />\n', encoding='utf-8')
from pride_theme import apply_pride_theme
apply_pride_theme(catalog)
ASSETS.mkdir(exist_ok=True)
(ASSETS / 'shop.json').write_text(json.dumps(catalog, ensure_ascii=False, indent=1), encoding='utf-8')
print(f"动物 {len(catalog['animals'])}，背景 {len(catalog['backgrounds'])}，饰品 {len(catalog['accessories'])}，"
      f"挂件 {len(catalog['ornaments'])}，图标 {len(catalog['icons'])}")
