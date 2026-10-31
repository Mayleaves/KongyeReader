# 空叶阅读

本地 TXT / EPUB 阅读器。黑白手绘风，参考「小鸡专注」：白底、粗黑圆角按钮、灰色胶囊，吉祥物是一只黑猫。不申请联网权限，书、阅读记录和词典都在手机里。

- 包名 `com.kongye.yuedu`（手机上已有一个签名不同的旧版 `com.kongye.reader`，所以换了包名）
- 最低 Android 8.0（API 26）

## 功能

- **导入**：系统文件选择器多选；文件管理器“用空叶阅读打开”；其他应用“分享到”（文件或一段文字）
- **TXT**：自动识别 UTF-8 / GBK(GB18030) / UTF-16；识别“第X章 / 序章 / 番外 / Chapter N”等标题分章，去掉开头的整页目录；认不出章节时按约一万字分节
- **EPUB**：按书脊顺序读正文，用 nav / ncx 目录分章（同一文件多个锚点也会切开），取出封面
- **翻页**：平移、覆盖、上下滚动；点击左右翻页、音量键翻页、沉浸式全屏
- **排版**：字号、行间距、段间距、首行缩进、上下边距、左右边距；两端对齐（自己按行补字间距，见下）
- **字体**：系统默认、霞鹜文楷 GB、仓耳今楷 02
- **纸色与亮度**：白 / 米 / 灰 / 青 / 夜间；“夜间”二级菜单里调亮度、夜间模式、跟随系统
- **长按**：自动选词（成语 / 诗句 > 词语、人名、地名、书中反复出现的人名 > 单字），选区下方弹字典（读音、简单释义；英文给音标和中文释义）；两端把手可拖；复制、字典、分享、全选
- **编辑本章**：参考 Notepad++，段号、当前段高亮、撤销 / 重做、查找替换、状态栏；版式和阅读页完全一样，打开时正在读的那一行不动，保存后回到光标处
- **启动后继续上次阅读**（设置里可关）
- **首页**：猫 + 今日阅读 + 继续阅读；读满 30 分钟猫头上长出一片叶子
- **统计 / 时间轴 / 猫园 / 桌面小组件**、深色模式

## 构建

```powershell
$env:JAVA_HOME = 'D:\Program\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleRelease --offline
```

产物在 `app\build\outputs\apk\release\app-release.apk`，用本机 debug 签名，可直接安装。

仓库里没有字体文件，直接构建也能用，只是“霞鹜文楷”“仓耳今楷”会显示成系统字体。要带上字体，先按下面“字体”一节准备好。

### 离线词典

`app/src/main/assets/dict.db` 由 `tools/build_dict.py` 生成（需要 `pypinyin`、`opencc-python-reimplemented`），数据都是 MIT 许可，版权声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)：

- [mapull/chinese-dictionary](https://github.com/mapull/chinese-dictionary)：汉字、词语、成语
- [pwxcoo/chinese-xinhua](https://github.com/pwxcoo/chinese-xinhua)：常用成语表
- [fxsjy/jieba](https://github.com/fxsjy/jieba)：分词词频、人名地名词性
- [chinese-poetry](https://github.com/chinese-poetry/chinese-poetry)：唐诗三百首、千家诗、宋词三百首、诗经、楚辞、纳兰、曹操
- [skywind3000/ECDICT](https://github.com/skywind3000/ECDICT)：英汉

改了词典要把 `data/Dict.kt` 里的 `VERSION` 加一，手机上才会重新解出新词典。

### 字体

字体放在 `app/src/main/assets/fonts/wenkai.ttf`、`jinkai.ttf`，不进仓库。下载原字体后用脚本裁掉谚文、扩展汉字和字形微调，只留常用汉字、拉丁字母和标点（需要 `fonttools`）：

```powershell
python -I tools/subset_fonts.py LXGWWenKaiGB-Regular.ttf TsangerJinKai02-W04.ttf
```

- 霞鹜文楷 GB：[lxgw/LxgwWenkaiGB](https://github.com/lxgw/LxgwWenkaiGB/releases) 的 `LXGWWenKaiGB-Regular.ttf`（SIL OFL 1.1，裁剪后的版本不能再用“霞鹜”的名字发布）
- 仓耳今楷 02-W04：[仓耳字库](http://tsanger.cn)（个人非商用免费，不得再分发字体文件）

带了仓耳今楷的安装包只给自己用，不要公开发布。

## 代码结构

- `data/` 书架、设置、阅读记录（JSON 存在应用私有目录），`Importer` / `TxtParser` / `Epub` 负责导入，`Dict` 查词和选词
- `ui/reader/Paginator.kt` 按章排版分页：长章按段落切块，段距用段落间的占位小段落，两端对齐按行补字间距（不用系统的两端对齐：Android 15 起它撑开字距但取字位置的接口不算这部分，长按会偏）
- `ui/reader/ReaderScreen.kt` 阅读页、上下滚动、长按；`Selection.kt` 选字浮层和字典卡片；`EditPage.kt` 编辑本章
- `ui/components/Mascot.kt` 猫的画稿（200×200 坐标），`Kit.kt` 按钮、胶囊、滑块、分段控件、弹层等
- `ui/screens/` 首页、书架、统计、时间轴、猫园、设置
