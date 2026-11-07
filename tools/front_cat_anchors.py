"""空叶三只正脸猫的饰品锚点（tools/import_shop_assets.py 写进 shop.json）。
看板猫、鎏金看板猫：240x260；墨团：200x220。锚点照白膜Ⅰ类（正脸双目四足）的意思，按各自的头、眼、嘴、脖子、尾巴尖、脚下摆。
白膜Ⅰ类的头长在横着的身体右端，按外框换算会把帽子挂到右耳上、气球挂到左上角，所以这三只单独摆。
BEHIND、PAIRED 和 Creature.kt 的 drawOutfit 一致。"""


def a(x, y, s, r=0, **items):
    d = {'x': round(x, 2), 'y': round(y, 2), 's': round(s, 4), 'r': r}
    if items:
        d['items'] = {k.replace('_', '-'): v for k, v in items.items()}
    return d


def cat_slots(head, top, eyes, spacing, mouth, neck, ear, tail, ground, shadow_w, shoulder, f):
    cx = head[0]
    return {
        'A': a(cx, top + 1, 1.08 * f),
        'B': a(ear[0], ear[1], 1.0 * f, 16),
        'C': a(cx, eyes, spacing / 34),
        'D': a(cx, mouth, 1.0 * f, 16, acc_D_rose=a(cx, mouth - 2, 1.0 * f, -6)),
        'E': a(cx, neck, 1.0 * f, 0, acc_E_medal=a(cx - 10 * f, neck + 2, 1.0 * f, 10)),
        'F': a(cx, shoulder, 1.3, 0,
               acc_F_backpack=a(cx - 34, shoulder + 16, 1.05, -14),
               acc_F_windup=a(cx - 30, shoulder + 4, 1.0, -18)),
        'G': a(tail[0], tail[1], 0.84),
        'H': a(cx, ground, shadow_w * 1.17 / 216),
    }


MASCOT = cat_slots(head=(120, 121), top=80, eyes=126, spacing=42, mouth=142, neck=162, ear=(156, 76),
                   tail=(185, 173), ground=233, shadow_w=108, shoulder=176, f=1.062)
MINIMAL = cat_slots(head=(100, 96), top=56, eyes=98, spacing=40, mouth=114, neck=136, ear=(136.5, 51),
                    tail=(156, 146), ground=197, shadow_w=92, shoulder=152, f=1.072)

# 正脸坐着：背上的东西挂在身体后面；翅膀左右各一只（以锚点为中线镜像）
BEHIND = ('H', 'F')
PAIRED = ('acc-F-wings', 'acc-F-batwings')
