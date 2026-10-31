"""
生成 app/src/main/assets/dict.db：长按查词用的离线词典。

用法：python -I tools/build_dict.py <数据目录> <输出 dict.db>

数据目录里需要：
  jieba_dict.txt            jieba 词表（MIT），提供分词词频和词性（人名 nr / 地名 ns ...）
  m_char_detail.json        mapull/chinese-dictionary 汉字释义（MIT）
  m_char_base.json          同上，汉字读音
  m_word.json               同上，词语
  m_idiom.json              同上，成语
  po_*.json                 chinese-poetry 诗词（MIT）：唐诗三百首、千家诗、宋词三百首、诗经、楚辞、纳兰、曹操
  ecdict.csv                skywind3000/ECDICT 英汉词典（MIT）
依赖：pypinyin、opencc-python-reimplemented
"""
import csv
import json
import os
import re
import sqlite3
import sys

from opencc import OpenCC
from pypinyin import Style, lazy_pinyin, pinyin

src, out = sys.argv[1], sys.argv[2]
t2s = OpenCC('t2s')
P = lambda name: os.path.join(src, name)


def jl(path):
    t = open(path, encoding='utf8').read().strip()
    try:
        return json.loads(t)
    except ValueError:
        pass
    dec, i, res = json.JSONDecoder(), 0, []
    while i < len(t):
        while i < len(t) and t[i] in ' \n\r\t,[]':
            i += 1
        if i >= len(t):
            break
        o, i = dec.raw_decode(t, i)
        res.append(o)
    return res


def is_han(ch):
    return '一' <= ch <= '鿿' or '㐀' <= ch <= '䶿'


def cn(s):
    s = s.replace(',', '，').replace(';', '；').replace(':', '：').replace('?', '？').replace('!', '！')
    s = s.replace('(', '（').replace(')', '）')
    s = re.sub(r'\s+', ' ', s).strip()
    return s


def cut(s, limit):
    if len(s) <= limit:
        return s
    head = s[:limit]
    k = max(head.rfind('；'), head.rfind('。'))
    if k >= limit * 0.5:
        return head[:k]
    return head.rstrip('，、；：') + '…'


def fix_py(s):
    return s.replace('ɡ', 'g').replace('  ', ' ').strip()


def py_of(word):
    return ' '.join(x[0] for x in pinyin(word, style=Style.TONE, errors='ignore'))


# —— jieba：词频 + 词性 ——
jieba = {}
total = 0
for line in open(P('jieba_dict.txt'), encoding='utf8'):
    parts = line.split()
    if len(parts) < 2:
        continue
    w, f = parts[0], int(parts[1])
    tag = parts[2] if len(parts) > 2 else ''
    jieba[w] = (f, tag)
    total += f
print('jieba', len(jieba), total)

# —— 汉字 ——
ETY = re.compile(r'^[（(](会意|形声|象形|指事|假借|会意兼形声|形声兼会意|本义|会意字|形声字)')


def char_senses(pr):
    senses = []
    for e in pr.get('explanations', []):
        c = (e.get('content') or '').strip()
        if c:
            if ETY.match(c) or (c[0] in '(（' and '本义' in c[:12]):
                k = c.find('本义')
                if k < 0:
                    continue
                # “(形声。本义:走遍) 同本义。” 只要括号里的本义
                c = re.split(r'[)）。]', c[k + 2:].lstrip(':：是 '), maxsplit=1)[0]
            elif c.startswith('同本义'):
                continue
            if e.get('same'):
                c = re.sub(r'^[,，]\s*|^[(（][a-zü]+[)）]。?', '', c)
                c = f"同“{e['same']}”，{c}"
        elif e.get('words'):
            w = e['words'][0]
            if not w.get('text'):
                continue
            c = f"{w['word']}：{w['text']}"
        else:
            continue
        c = cn(c).strip('。 ')
        c = re.sub(r'\s*——\s*', '——', c)
        if len(c) > 26:
            k = min([i for i in (c.find('，', 8), c.find('；', 8), c.find('。', 8)) if 0 < i <= 26] or [-1])
            c = c[:k] if k > 0 else c[:25] + '…'
        if c and c not in senses:
            senses.append(c)
        if len(senses) >= 3:
            break
    return senses


zi = {}
for x in jl(P('m_char_detail.json')):
    ch = x.get('char')
    if not ch or len(ch) != 1:
        continue
    lines = []
    pys = []
    prs = x.get('pronunciations', [])
    # 最常用的读音排在前面
    main = pinyin(ch, style=Style.TONE, errors='ignore')
    main = main[0][0] if main and main[0] else ''
    prs = sorted(prs, key=lambda pr: fix_py(pr.get('pinyin', '')) != main)
    for pr in prs[:4]:
        py = fix_py(pr.get('pinyin', ''))
        if not py:
            continue
        pys.append(py)
        s = char_senses(pr)
        if s:
            lines.append(py + '\t' + '；'.join(s))
    if pys:
        zi[ch] = (','.join(pys), '\n'.join(lines))
for x in jl(P('m_char_base.json')):
    ch = x.get('char')
    if ch and len(ch) == 1 and ch not in zi and x.get('pinyin'):
        zi[ch] = (','.join(fix_py(p) for p in x['pinyin']), '')
for cp in range(0x4E00, 0xA000):
    ch = chr(cp)
    if ch not in zi:
        p = pinyin(ch, style=Style.TONE, heteronym=True, errors='ignore')
        if p and p[0]:
            zi[ch] = (','.join(p[0][:3]), '')
print('zi', len(zi))

# —— 词语 / 成语 / 诗句 / 专名 ——
# k: 0 词语 1 成语 2 诗句 3 人名 4 地名 5 机构 6 其他专名
KIND = {'nr': 3, 'nrfg': 3, 'nrt': 3, 'ns': 4, 'nt': 5, 'nz': 6, 'i': 1}
ci = {}  # w -> [f, k, py, def]


def put(w, f, k, py, d):
    cur = ci.get(w)
    if cur is None:
        ci[w] = [f, k, py, d]
        return
    cur[0] = max(cur[0], f)
    if k and cur[1] == 0:
        cur[1] = k
    if py and not cur[2]:
        cur[2] = py
    if d and not cur[3]:
        cur[3] = d


def clean_word_def(s, limit, word=''):
    s = s.replace('\r', '').replace('\n', ' ')
    s = re.sub(r'\s*\d+\.\s*', '；', s)
    s = re.sub(r'[：:][^；。]*～[^；。]*', '', s)
    s = re.sub(r'[①②③④⑤⑥⑦⑧⑨⑩]', '；', s)
    s = cn(s)
    # 有的释义和例句粘在一起、例句之间用“｜”隔开：去掉带“｜”或带词条本身的片段（多半是例句）
    if '｜' in s:
        parts = [x.strip() for x in re.split(r'[；。]', s) if x.strip() and '｜' not in x and (not word or word not in x)]
        s = '；'.join(parts) if parts else s.split('｜')[0]
    s = re.sub(r'\s*；\s*', '；', s)
    s = re.sub(r'；+', '；', s).strip('；。 ')
    return cut(s, limit)


common_idioms = {x['word'] for x in jl(P('idiom.json'))}
idioms = jl(P('m_idiom.json'))
for x in idioms:
    w = x.get('word', '').strip()
    if len(w) < 3:
        continue
    # 生僻成语只留读音，常见的（jieba 或新华成语表里有）才带释义
    d = ''
    if w in jieba or w in common_idioms:
        d = clean_word_def(x.get('explanation', ''), 44, w)
        src_book = cn((x.get('source') or {}).get('book', ''))
        if src_book and len(src_book) <= 16:
            d += '\n出自' + src_book
    put(w, jieba.get(w, (3, ''))[0], 1, fix_py(x.get('pinyin', '')), d)
print('idioms', len(idioms))

words = jl(P('m_word.json'))
kept_words = 0
for x in words:
    w = x.get('word', '').strip()
    if len(w) < 2 or not all(is_han(c) for c in w):
        continue
    jf = jieba.get(w, (0, ''))[0]
    # 词典很大：只留 jieba 词表里也有的词
    if jf < 3:
        continue
    d = clean_word_def(x.get('explanation', ''), 48, w)
    put(w, jf, KIND.get(jieba.get(w, (0, ''))[1], 0), fix_py(x.get('pinyin', '')), d)
    kept_words += 1
print('words', len(words), 'kept', kept_words)

LABEL = {3: '人名', 4: '地名', 5: '机构', 6: '专名'}
for w, (f, tag) in jieba.items():
    if len(w) < 2 or not all(is_han(c) for c in w):
        continue
    k = KIND.get(tag, 0)
    if w in ci:
        if k and ci[w][1] == 0:
            ci[w][1] = k
        continue
    if k in LABEL:
        if f < {3: 10, 4: 5}.get(k, 20):
            continue
        put(w, f, k, py_of(w), LABEL[k])
    elif f >= 5 or k == 1:
        put(w, f, k, '', '')
print('ci after jieba', len(ci))

# 诗句
poems = []  # (paragraphs, author, title)
for x in jl(P('po_tang300.json'))['content']:
    for p in x['content']:
        poems.append(([t2s.convert(s) for s in p['paragraphs'] if isinstance(s, str)], t2s.convert(p.get('author', '')), t2s.convert(p.get('subchapter') or p.get('chapter', ''))))
for x in jl(P('po_qianjia.json'))['content']:
    for p in x['content']:
        a = re.sub(r'^（.*?）', '', t2s.convert(p.get('author', '')))
        poems.append(([t2s.convert(s) for s in p['paragraphs'] if isinstance(s, str)], a, t2s.convert(p.get('chapter', ''))))
for p in jl(P('po_song300.json')):
    poems.append((p['paragraphs'], p.get('author', ''), p.get('rhythmic', '')))
for p in jl(P('po_shijing.json')):
    poems.append((p['content'], '', f"诗经·{p.get('section', '')}·{p['title']}"))
for p in jl(P('po_chuci.json')):
    poems.append((p['content'], p.get('author', ''), p['title']))
for p in jl(P('po_nalan.json')):
    poems.append((p['para'], p.get('author', '纳兰性德'), p['title']))
for p in jl(P('po_caocao.json')):
    poems.append((p['paragraphs'], '曹操', p['title']))

nlines = 0
for paras, author, title in poems:
    title = title.strip()
    src_label = f"{author}《{title}》" if author else f"《{title}》"
    for para in paras:
        para = re.sub(r'[（(][^）)]*[）)]', '', para).strip()
        for clause in re.split(r'[，。！？；、：,.!?;:“”‘’「」『』\s]+', para):
            if not 4 <= len(clause) <= 12 or not all(is_han(c) for c in clause):
                continue
            if clause in ci and ci[clause][1] == 1:
                continue
            ci[clause] = [1, 2, py_of(clause), src_label + '\n' + cut(para, 36)]
            nlines += 1
print('poem lines', nlines)

# —— 英文 ——
en = {}
infl = {}
NAMES = {'p': '的过去式', 'd': '的过去分词', 'i': '的现在分词', '3': '的第三人称单数', 's': '的复数', 'r': '的比较级', 't': '的最高级'}
with open(P('ecdict.csv'), encoding='utf8', newline='') as fh:
    for row in csv.DictReader(fh):
        w = row['word']
        if not re.fullmatch(r"[A-Za-z][A-Za-z'\-]*", w):
            continue
        bnc = int(row['bnc'] or 0)
        frq = int(row['frq'] or 0)
        common = 0 < bnc <= 25000 or 0 < frq <= 25000 or row['tag'] or (row['collins'] or '0') != '0' or row['oxford'] == '1'
        if not common:
            continue
        tr = [l.strip() for l in row['translation'].replace('\\n', '\n').split('\n') if l.strip() and not l.startswith('[网络]')]
        if not tr:
            continue
        tr = [cut(l, 46) for l in tr[:4]]
        key = w.lower()
        if key in en and w != key:
            continue
        en[key] = (w, row['phonetic'], '\n'.join(tr), None, None)
        for part in (row['exchange'] or '').split('/'):
            if ':' not in part:
                continue
            t, form = part.split(':', 1)
            if t in NAMES and form and form.lower() != key:
                infl.setdefault(form.lower(), (w, NAMES[t]))
for form, (lemma, note) in infl.items():
    if form not in en:
        en[form] = (form, None, None, lemma, note)
print('en', len(en))

# —— 写库 ——
if os.path.exists(out):
    os.remove(out)
db = sqlite3.connect(out)
db.execute('PRAGMA page_size = 4096')
db.execute('CREATE TABLE zi(c TEXT PRIMARY KEY, f INTEGER, py TEXT, def TEXT) WITHOUT ROWID')
db.execute('CREATE TABLE ci(w TEXT PRIMARY KEY, f INTEGER, k INTEGER, py TEXT, def TEXT) WITHOUT ROWID')
db.execute('CREATE TABLE en(w TEXT PRIMARY KEY, word TEXT, ph TEXT, def TEXT, lemma TEXT, note TEXT) WITHOUT ROWID')
db.execute('CREATE TABLE meta(k TEXT PRIMARY KEY, v TEXT) WITHOUT ROWID')
db.executemany('INSERT INTO zi VALUES (?,?,?,?)', [(c, jieba.get(c, (1, ''))[0], py, d or None) for c, (py, d) in zi.items()])
db.executemany('INSERT INTO ci VALUES (?,?,?,?,?)', [(w, f, k, py or None, d or None) for w, (f, k, py, d) in ci.items()])
db.executemany('INSERT INTO en VALUES (?,?,?,?,?,?)', [(k, v[0] if v[0] != k else None, v[1] or None, v[2], v[3], v[4]) for k, v in en.items()])
db.executemany('INSERT INTO meta VALUES (?,?)', [('total', str(total)), ('maxlen', '12')])
db.commit()
db.execute('VACUUM')
db.close()
print('size', os.path.getsize(out))
