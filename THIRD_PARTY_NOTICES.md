# 第三方数据许可

`app/src/main/assets/dict.db`（离线词典）由 `tools/build_dict.py` 从下面这些项目的数据生成，它们都使用 MIT 许可：

| 项目 | 用到的数据 | 版权 |
| --- | --- | --- |
| [mapull/chinese-dictionary](https://github.com/mapull/chinese-dictionary) | 汉字、词语、成语释义 | Copyright (c) 2021 码谱 |
| [pwxcoo/chinese-xinhua](https://github.com/pwxcoo/chinese-xinhua) | 常用成语表 | Copyright (c) 2018 PWXCOO |
| [fxsjy/jieba](https://github.com/fxsjy/jieba) | 分词词频、词性 | Copyright (c) 2013 Sun Junyi |
| [chinese-poetry/chinese-poetry](https://github.com/chinese-poetry/chinese-poetry) | 唐诗三百首、千家诗、宋词三百首、诗经、楚辞、纳兰、曹操 | Copyright (c) 2016 JackeyGao |
| [skywind3000/ECDICT](https://github.com/skywind3000/ECDICT) | 英汉释义、音标、词形变化 | Copyright (c) 2025 Linwei |

以上各项目的许可原文如下（版权行见上表）：

```
MIT License

Copyright (c) <year> <copyright holders>

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## 字体

两款正文字体不在仓库里，构建前按 README 自己下载、裁剪：

- 霞鹜文楷 GB：[lxgw/LxgwWenkaiGB](https://github.com/lxgw/LxgwWenkaiGB)，SIL Open Font License 1.1。保留字体名“霞鹜”“LXGW”等不能用于修改过的版本，裁剪也算修改，所以裁好的文件不随仓库发布。
- 仓耳今楷 02：[仓耳字库](http://tsanger.cn)，个人非商业使用免费，不得再分发字体文件。
