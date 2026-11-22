# 空叶阅读

[下载APK](https://github.com/Mayleaves/KongyeReader/releases/latest) · [更新日志](CHANGELOG.md)

安安静静读一本书。

空叶阅读是一款 Android 本地 TXT / EPUB 阅读器，以黑白手绘界面和黑猫陪伴阅读。支持离线阅读、查词、编辑与阅读统计，也可以用阅读积累的叶子领养动物、装扮猫园。

- 支持 Android 8.0 及以上系统。
- 包名：`com.kongye.reader`。
- 书籍和阅读记录保存在本机；云备份由用户自行配置 WebDAV 服务。

## 阅读与书架

- **导入书籍**：从系统文件选择器多选导入，也可从文件管理器打开文件，或接收其他应用分享的文件、文字。
- **TXT 与 EPUB**：TXT 自动识别常用编码与章节；EPUB 读取目录、正文和封面。导入时识别书名、作者、简介与标签，支持手动修改和重新识别。
- **翻页与排版**：平移、覆盖、上下滚动，支持点击和音量键翻页；字号、行距、段距、首行缩进、页边距及两端对齐均可调整。
- **阅读外观**：多种纸色、夜间模式、亮度调节与字体选择，支持跟随系统深浅色。
- **搜索与查词**：全书搜索并跳转到对应位置；长按选字、拖动选区、复制、分享，使用内置离线中英词典查询释义。
- **章节编辑**：查找替换、撤销重做、切换章节，将段落设为标题或转回正文。
- **进度管理**：记录阅读位置、书签与读完状态，可设置启动时继续上次阅读。

## 阅读陪伴

- **首页黑猫**：呼吸、眨眼、摸头互动与眼神跟随；当天阅读满半小时，猫头上会长出叶子。
- **猫园**：阅读积累动物来访记录，按周查看；可更换背景、选择来访动物、移动动物位置和布置节日挂件。
- **叶子与商城**：每阅读一分钟获得一片叶子，成就也会奖励叶子或专属物品。叶子可用于换取动物、背景、饰品、挂件和桌面图标。
- **个性装扮**：首页动物、猫园、动物各部位饰品及图标均可更换；路牌名称最多六字，也可以留空。
- **成就与徽章**：阅读、坚持、书架、收集、场景和探索等类别，可查看完成进度、奖励与徽章配色。
- **统计与时间轴**：查看阅读时长、阅读记录和读完的书籍，并提供桌面小组件。

## 备份与恢复

在「设置 → 备份与恢复」中操作：

- **本地备份**：导出 ZIP 文件，也可以从备份文件恢复。
- **WebDAV 云备份**：填写自己的服务地址、账号与密码，支持测试连接、手动备份及每天自动备份。
- **历史备份**：按日期选择云端备份恢复，查看备份时间和大小，也可多选删除指定备份；恢复和删除前会确认。

备份包含书籍正文、目录、封面、书架与进度、阅读记录、设置、叶子和装扮；不包含词典、字体及 WebDAV 连接配置。云端使用 `KongyeReader` 目录，并兼容读取旧目录；保留最新备份和按日期命名的历史备份，同一天再次备份会覆盖当天那份。

## 下载与更新

在 [最新发行版](https://github.com/Mayleaves/KongyeReader/releases/latest) 的附件中下载 APK 安装。

应用每次进入前台时会在后台检查版本，发现新版本后在「关于 → 检查更新」显示提示；点击可查看更新说明并下载安装包。应用内的「更新历史」支持搜索和分页，完整版本记录也可查看 [更新日志](CHANGELOG.md)。

## 数据与联网

阅读、查词和查看本地记录均可离线使用。联网用于用户自行配置的 WebDAV 云备份，以及从 GitHub 检查版本和下载安装包。

## 从源码构建

项目使用 Kotlin、Jetpack Compose 和 Gradle。需要 Android SDK 与 JDK 17 或以上版本，SDK 配置见 `app/build.gradle.kts`。

Windows 本工作区先运行 `E:\Vibe Coding\Android\Open Android Build Shell.cmd`，使用其中配置的 JDK、SDK、Gradle 缓存和临时目录，再进入工程目录：

```bat
cd /d "E:\Vibe Coding\Projects\KongyeReader"
gradlew.bat :app:assembleRelease -Ppublic
```

其他环境配置好 JDK、Android SDK 和 `local.properties` 后，在项目根目录运行相同的 Gradle 任务。依赖已经缓存时可加 `--offline`。

构建产物为 `app/build/outputs/apk/release/app-release.apk`。目前 release 构建沿用本机 debug 签名；自行构建的 APK 与已有安装签名不一致时无法直接覆盖安装。

### 字体与素材

- 路牌装饰字体「站酷快乐体」随仓库提供，附 [SIL OFL 许可证](app/src/main/assets/licenses/zcool_kuaile_OFL.txt)。
- 两款可选正文字体不随仓库提供。缺少对应文件时会回退到系统字体；个人构建可自行准备字体并使用 `tools/subset_fonts.py`，放入 `app/src/main/assets-private/fonts/`。
- 仓耳今楷不得再分发；私有字体和小马宝莉素材分别位于 `app/src/main/assets-private/` 与 `app/src/main/res-private/`，不进入仓库。公开构建使用 `-Ppublic` 排除这些内容。
- 离线词典由 `tools/build_dict.py` 生成，数据来源与许可见 [第三方声明](THIRD_PARTY_NOTICES.md)。

### 代码导航

| 目录 | 内容 |
| --- | --- |
| `app/src/main/java/com/kongye/reader/data/` | 书架、导入解析、设置、记录、成就、备份及更新 |
| `app/src/main/java/com/kongye/reader/ui/reader/` | 阅读排版、分页、选字查词与章节编辑 |
| `app/src/main/java/com/kongye/reader/ui/screens/` | 首页、书架、猫园、统计、商城、装扮与设置 |
| `app/src/main/java/com/kongye/reader/ui/components/` | 通用组件、猫和动物绘制、场景与动画 |
| `app/src/main/assets/` | 商品目录、更新历史及离线词典等数据 |
| `design/source/`、`tools/` | 设计源文件、素材导入与数据生成工具 |
