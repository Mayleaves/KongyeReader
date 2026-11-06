"""
生成 app/src/main/assets-private/fonts/ 下的 wenkai.ttf、jinkai.ttf：正文用的两款楷体。

字体不放进仓库（仓耳今楷不允许再分发；霞鹜文楷裁剪后不能再用原名发布），
构建前自己下载原字体，用这个脚本裁一遍：

  python -I tools/subset_fonts.py <LXGWWenKaiGB-Regular.ttf> <TsangerJinKai02-W04.ttf>

只留常用汉字、拉丁字母和标点，去掉字形微调，两款各从二十多 MB 缩到十四 MB 左右。
缺哪个文件，App 里对应的字体就退回系统默认。
依赖：fonttools
"""
import os
import sys

from fontTools import subset

UNICODES = 'U+0000-1FFF,U+2000-312F,U+3190-33FF,U+4E00-9FFF,U+F900-FAFF,U+FE10-FE6F,U+FF00-FFEF'
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src', 'main', 'assets-private', 'fonts')

os.makedirs(OUT, exist_ok=True)
for src, name in zip(sys.argv[1:3], ('wenkai.ttf', 'jinkai.ttf')):
    dst = os.path.join(OUT, name)
    subset.main([src, '--unicodes=' + UNICODES, '--layout-features=*', '--no-hinting', '--output-file=' + dst])
    print(name, os.path.getsize(dst))
