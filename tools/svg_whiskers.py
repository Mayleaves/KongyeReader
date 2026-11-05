"""把看板猫的胡须移到猫头下面：胡须内侧压在脸上的那一截被头盖住，看起来是从脸后面长出来的。

经典款：头是 <ellipse cx="120" cy="121" rx="46" ry="41">，后面紧跟几条 <line> 胡须；
极简款：头是 <ellipse cx="100" cy="96" rx="48" ry="40">，后面是一条 d="M42 94 H55 …"（或 L58 96）的 <path>。
两种都把紧跟在头后面的胡须（连同说明注释）整块挪到头前面。
"""
import re

HEAD = re.compile(r'^[ \t]*<ellipse cx="(?:120|100)" cy="(?:121|96)" rx="(?:46|48)" ry="(?:41|40)"[^>]*/>[ \t]*$')
WHISKER = re.compile(r'^[ \t]*(<line\b[^>]*/>|<path d="M4[02] 9[34] [HL][^"]*"[^>]*/>)[ \t]*$')
COMMENT = re.compile(r'^[ \t]*<!--.*-->[ \t]*$')


def whiskers_under_head(svg: str) -> str:
    lines = svg.split('\n')
    i = 0
    while i < len(lines):
        if HEAD.match(lines[i]):
            j = i + 1
            block = []
            while j < len(lines) and (not lines[j].strip() or COMMENT.match(lines[j]) or WHISKER.match(lines[j])):
                block.append(lines[j])
                j += 1
            # 胡须后面的空行和下一块的注释留在原地
            while block and not WHISKER.match(block[-1]):
                block.pop()
                j -= 1
            moved = [l for l in block if l.strip()]
            if any(WHISKER.match(l) for l in moved):
                lines[i:j] = moved + [lines[i]] + [''] * (len(block) - len(moved))
                i += len(moved)
        i += 1
    return '\n'.join(lines)
