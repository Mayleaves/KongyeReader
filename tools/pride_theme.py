"""Correct the Pride collection's labels while retaining existing ownership IDs."""
NAMES = {
    'orn_mothers_a': '骄傲彩虹旗',
    'orn_mothers_b': '彩虹气球屋',
    'orn_mothers_c': '自由花瓣雨',
    'orn_mothers_d': '骄傲花束',
    'orn_mothers_e': '骄傲徽章礼盒',
    'orn_mothers_f': '爱无界甜心',
}

def apply_pride_theme(catalog):
    for festival in catalog['festivals']:
        if festival['id'] == 'mothers':
            festival['name'] = '骄傲日'
    for ornament in catalog['ornaments']:
        if ornament['id'] in NAMES:
            ornament['name'] = NAMES[ornament['id']]
