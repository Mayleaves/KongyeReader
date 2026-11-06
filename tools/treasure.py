"""镇店之宝：至尊金猪皇（9999 片叶子）和它的 8 件神装。

来源：design/source/treasure-pig.html（金猪皇素体 + 8 件神装的调位台：getNakedPigMarkup、
pig-emperor-defs 里的神装、BASE_ANCHORS 是在 600x450 舞台上调好的锚点，猪在舞台上是 translate(185,135) scale(1.85)）。

- 金猪皇：128x128 的原图放进 160 的画布（脚落在 84% 高，比别的动物大一圈，镇店之宝嘛），加一块和别的动物一样的地面影子。
  原图头朝左（flip）；神装是照着它不翻转调的，所以 accFlip=false。锚点从舞台换算到它自己的坐标。
- 8 件神装：lock 是成就 c0「镇店之宝」（买下金猪皇就点亮），全动物通用，按各动物自己的 A~H 锚点戴。
  单片镜原点在一只眼睛上：正脸双眼的动物另做一份挪到右眼（白膜Ⅰ类两眼各离中线 17）。
- VectorDrawable 不认虚线：量外框时在浏览器里把虚线描边拆成一段段实线（getPointAtLength 取点），
  连同外框存进 tools/treasure_measurements.json；导入时直接用，不用再开浏览器。

用法：python -X utf8 tools/treasure.py [--measure]  —— 把金猪皇和神装写进 assets/shop.json 并转成 VectorDrawable。
--measure 先用无头 Edge 重新量（换了 HTML 时要加；要在 PowerShell 里跑，Git Bash 里 Edge 不吐 DOM）。
tools/import_shop_assets.py 也会调用 entries()。
"""
import json
import re
import subprocess
import sys
import tempfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'design/source/treasure-pig.html'
MEASURED = ROOT / 'tools/treasure_measurements.json'
RES = ROOT / 'app/src/main/res'
SHOP = ROOT / 'app/src/main/assets/shop.json'
STUDIO = Path('D:/Program/Android/Android Studio')
EDGE = Path('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')
SVG_NS = 'http://www.w3.org/2000/svg'

PIG_ID, PIG_NAME, PIG_PRICE, LOCK = 'goldpig', '至尊金猪皇', 9999, 'c0'
SIZE, FEET = 160, 0.84
DUAL_SHIFT = 17
GROUP = 'kongye'  # 放在空叶组里，排在鎏金看板猫后面


def parse():
    html = SRC.read_text(encoding='utf-8')
    d0 = html.index('<defs id="pig-emperor-defs">')
    defs = html[d0:html.index('</defs>', d0) + len('</defs>')]
    body = re.search(r'function getNakedPigMarkup\(\) \{\s*return `(.*?)`;', html, re.S).group(1)
    s0, s1 = html.index('    const PIG_ORIGIN_X'), html.index('    // 状态管理')
    r = subprocess.run(['node', '-'], input=f"""
const vm = require('vm');
const ctx = vm.createContext({{}});
vm.runInContext({json.dumps(html[s0:s1])} + `;this.O = {{ox: PIG_ORIGIN_X, oy: PIG_ORIGIN_Y, k: PIG_SCALE, anchors: BASE_ANCHORS,
  items: CATALOG.map(c => ({{slot: c.code, id: c.items[0].id, name: c.items[0].name}}))}};`, ctx);
process.stdout.write(JSON.stringify(ctx.O));
""", capture_output=True, text=True, encoding='utf-8', check=True)
    data = json.loads(r.stdout)
    for it in data['items']:
        # 名字去掉前面的表情和“(第一版)”
        it['name'] = re.sub(r'\s*[（(]第一版[)）]', '', re.sub(r'^[^\u4e00-\u9fff]+', '', it['name'])).strip()
    grads = ''.join(re.findall(r'<linearGradient.*?</linearGradient>', defs, re.S))
    groups = {m.group(1): m.group(0) for m in re.finditer(r'<g id="(acc-[^"]+)".*?\n      </g>', defs, re.S)}
    return {'defs': defs, 'grads': grads, 'groups': groups, 'body': body, **data}


MEASURE_JS = r"""
const out = {acc: {}, pig: null};
const NS = 'http://www.w3.org/2000/svg';
function expandDashes(root) {
  for (const el of [...root.querySelectorAll('[stroke-dasharray]')]) {
    const dash = el.getAttribute('stroke-dasharray').split(/[ ,]+/).map(Number);
    const len = el.getTotalLength();
    let d = '', pos = 0, i = 0;
    while (pos < len) {
      const on = dash[i % dash.length], off = dash[(i + 1) % dash.length];
      const end = Math.min(pos + on, len);
      for (let t = pos, first = true; ; t = Math.min(t + 0.8, end)) {
        const p = el.getPointAtLength(t);
        d += (first ? 'M' : 'L') + p.x.toFixed(2) + ' ' + p.y.toFixed(2);
        first = false;
        if (t >= end) break;
      }
      pos = end + off; i += 2;
    }
    const path = document.createElementNS(NS, 'path');
    for (const a of ['stroke', 'stroke-width', 'stroke-linecap', 'stroke-linejoin', 'opacity', 'stroke-opacity', 'transform'])
      if (el.hasAttribute(a)) path.setAttribute(a, el.getAttribute(a));
    path.setAttribute('fill', 'none');
    path.setAttribute('d', d);
    el.replaceWith(path);
  }
}
const box = el => { const b = el.getBBox(); return [b.x, b.y, b.width, b.height].map(v => +v.toFixed(2)); };
for (const g of document.querySelectorAll('#src > g')) {
  const svg = document.createElementNS(NS, 'svg');
  document.body.appendChild(svg);
  const wrap = document.createElementNS(NS, 'g');
  svg.appendChild(wrap);
  const copy = g.cloneNode(true);
  copy.removeAttribute('id');
  wrap.appendChild(copy);
  expandDashes(wrap);
  out.acc[g.id] = {box: box(wrap), svg: wrap.innerHTML};
}
const pig = document.getElementById('pig');
out.pig = box(pig);
document.getElementById('out').textContent = JSON.stringify(out);
"""


def measure(src):
    page = f"""<!DOCTYPE html><html><head><meta charset="utf-8"></head><body>
<svg width="0" height="0"><defs>{src['grads']}</defs><g id="src">{''.join(src['groups'].values())}</g></svg>
<svg viewBox="0 0 128 128" width="128" height="128"><g id="pig">{src['body']}</g></svg>
<pre id="out"></pre><script>{MEASURE_JS}</script></body></html>"""
    # Edge 的 Crashpad 退出得慢，临时目录删不干净就算了
    with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as tmp:
        f = Path(tmp) / 'measure.html'
        f.write_text(page, encoding='utf-8')
        # 单独的用户目录：不然会交给已经开着的 Edge，什么也拿不回来
        # 输出要重定向到文件：接管道的话 Edge 交给子进程去写，什么也拿不到
        dom = Path(tmp) / 'dom.html'
        with dom.open('wb') as out:
            subprocess.run([str(EDGE), '--headless', '--disable-gpu', f'--user-data-dir={Path(tmp) / "edge"}',
                            '--virtual-time-budget=3000', '--dump-dom', f.as_uri()],
                           stdout=out, stderr=subprocess.DEVNULL, timeout=120)
        # 启动的那个进程先退出，真正写 DOM 的子进程晚一点才写完
        for _ in range(120):
            text = dom.read_text(encoding='utf-8', errors='replace')
            if '</html>' in text:
                break
            time.sleep(0.5)
    m = re.search(r'<pre id="out">(.*?)</pre>', text, re.S)
    if not m:
        raise SystemExit('Edge 没量出来')
    text = m.group(1).replace('&lt;', '<').replace('&gt;', '>').replace('&quot;', '"').replace('&amp;', '&')
    data = json.loads(text)
    MEASURED.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding='utf-8')
    return data


def entries():
    """(金猪皇目录项, 8 件神装目录项, [(svg 文本, 输出 xml)])"""
    src = parse()
    m = json.loads(MEASURED.read_text(encoding='utf-8'))
    jobs = []
    # —— 金猪皇：放进 SIZE 的画布，身体居中、脚落在 FEET ——
    x, y, w, h = m['pig']
    tx, ty = round(SIZE / 2 - (x + w / 2), 2), round(SIZE * FEET - (y + h), 2)
    ox, oy, k = src['ox'], src['oy'], src['k']
    ground = src['anchors']['H']
    gx, gy = (ground['x'] - ox) / k, (ground['y'] - oy) / k
    shadow = f'<ellipse cx="{gx:.1f}" cy="{gy:.1f}" rx="55" ry="7" fill="#E0E0E0"/>'
    pig = (f'<svg xmlns="{SVG_NS}" viewBox="0 0 {SIZE} {SIZE}"><defs>{src["grads"]}</defs>'
           f'<g transform="translate({tx},{ty})">{shadow}{src["body"]}</g></svg>')
    jobs.append((pig, RES / 'drawable' / f'shop_animal_{PIG_ID}.xml'))
    slots = {}
    for code, a in src['anchors'].items():
        slots[code] = {'x': round((a['x'] - ox) / k + tx, 2), 'y': round((a['y'] - oy) / k + ty, 2),
                       's': round(a['scale'] / k, 4), 'r': a.get('rotate', 0) or 0}
    animal = {'id': PIG_ID, 'name': PIG_NAME, 'price': PIG_PRICE, 'group': GROUP, 'res': f'shop_animal_{PIG_ID}',
              'private': False, 'vb': [0.0, 0.0, float(SIZE), float(SIZE)], 'dual': False, 'flip': True, 'accFlip': False,
              'slots': slots}
    # —— 8 件神装：以挂点为原点，外框四周留 4 ——
    accs = []
    for it in src['items']:
        aid = it['id']
        entry = {'id': aid, 'name': it['name'], 'slot': it['slot'], 'price': 0, 'lock': LOCK}
        variants = [('main', m['acc'][aid]['svg'], m['acc'][aid]['box'])]
        if it['slot'] == 'C':
            bx, by, bw, bh = m['acc'][aid]['box']
            variants.append(('dual', f'<g transform="translate({DUAL_SHIFT},0)">{m["acc"][aid]["svg"]}</g>', [bx + DUAL_SHIFT, by, bw, bh]))
        for key, markup, (bx, by, bw, bh) in variants:
            box = [round(bx - 4, 1), round(by - 4, 1), round(bw + 8, 1), round(bh + 8, 1)]
            res = 'shop_acc_' + aid[4:].replace('-', '_').lower() + ('_dual' if key == 'dual' else '')
            svg = f'<svg xmlns="{SVG_NS}" viewBox="{" ".join(map(str, box))}"><defs>{src["grads"]}</defs>{markup}</svg>'
            jobs.append((svg, RES / 'drawable' / f'{res}.xml'))
            entry[key] = {'res': res, 'box': box}
        accs.append(entry)
    return animal, accs, jobs


def convert(jobs):
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        cp = f"{STUDIO / 'plugins/android/lib'}\\*;{STUDIO / 'lib'}\\*"
        subprocess.run([str(STUDIO / 'jbr/bin/javac.exe'), '-cp', cp, '-d', str(tmp), str(ROOT / 'tools/Svg2Vd.java')], check=True)
        args = []
        for i, (svg, out) in enumerate(jobs):
            (tmp / f'{i}.svg').write_text(svg, encoding='utf-8')
            args += [str(tmp / f'{i}.svg'), str(out)]
        r = subprocess.run([str(STUDIO / 'jbr/bin/java.exe'), '-cp', f'{tmp};{cp}', 'Svg2Vd', *args],
                           capture_output=True, text=True, encoding='utf-8')
        if r.returncode:
            raise SystemExit(r.stderr)
        for line in r.stdout.splitlines():
            print(' ', line[:200])


if __name__ == '__main__':
    if '--measure' in sys.argv or not MEASURED.exists():
        measure(parse())
    animal, accs, jobs = entries()
    convert(jobs)
    cat = json.loads(SHOP.read_text(encoding='utf-8'))
    rest = [a for a in cat['animals'] if a['id'] != PIG_ID]
    at = max((i + 1 for i, a in enumerate(rest) if a.get('group') == GROUP), default=0)
    cat['animals'] = rest[:at] + [animal] + rest[at:]
    ids = {a['id'] for a in accs}
    cat['accessories'] = [a for a in cat['accessories'] if a['id'] not in ids] + accs
    SHOP.write_text(json.dumps(cat, ensure_ascii=False, indent=1), encoding='utf-8')
    print(f'金猪皇 + {len(accs)} 件神装，{len(jobs)} 张图')
