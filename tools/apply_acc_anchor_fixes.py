"""把 tools/acc_anchor_fixes.json 的逐只锚点校正套到目录上。

单独跑：python tools/apply_acc_anchor_fixes.py  —— 直接改 app/src/main/assets/shop.json
import_shop_assets.py 生成目录后也调 apply(catalog)。
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FIXES = ROOT / 'tools/acc_anchor_fixes.json'


def apply(catalog):
    fixes = json.loads(FIXES.read_text(encoding='utf-8'))
    for animal in catalog['animals']:
        fix = fixes.get(animal['id'])
        if not fix:
            continue
        if 'dual' in fix:
            animal['dual'] = fix['dual']
        for slot, v in fix.items():
            if slot == 'dual' or slot.startswith('_'):
                continue
            old = animal['slots'][slot]
            new = dict(old)
            new['x'], new['y'] = v[0], v[1]
            if len(v) > 2:
                new['r'] = v[2]
            if len(v) > 3:
                new['s'] = v[3]
            if old.get('items'):
                # 单件微调跟着部位一起挪，缩放按同样比例
                k = new['s'] / old['s']
                new['items'] = {aid: {**it, 'x': round(new['x'] + (it['x'] - old['x']) * k, 2),
                                      'y': round(new['y'] + (it['y'] - old['y']) * k, 2),
                                      's': round(it['s'] * k, 4)}
                                for aid, it in old['items'].items()}
            animal['slots'][slot] = new
    return catalog


if __name__ == '__main__':
    shop = ROOT / 'app/src/main/assets/shop.json'
    raw = shop.read_text(encoding='utf-8')
    catalog = json.loads(raw)
    apply(catalog)
    indent = 1 if raw.startswith('{\n ') and not raw.startswith('{\n  ') else 2
    shop.write_text(json.dumps(catalog, ensure_ascii=False, indent=indent), encoding='utf-8')
    print('ok')
