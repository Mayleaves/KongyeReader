package com.kongye.reader.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** 叶子、买到的东西、装扮、成就。一分钟阅读换一片叶子，成就另有奖励 */
@Serializable
data class Profile(
    val leaves: Int = 0,
    /** 已经换成叶子的阅读时长；清空阅读记录后跟着变小，叶子不退 */
    val countedMillis: Long = 0,
    val owned: Set<String> = emptySet(),
    /** 首页那只：Shop.MASCOT 是看板猫，否则是动物编号 */
    val homeAnimal: String = Shop.MASCOT,
    val gardenBg: String = Shop.DEFAULT_BG,
    /** 猫园里出现哪些动物，每次阅读按顺序轮着来 */
    val gardenAnimals: List<String> = listOf(Shop.MASCOT),
    /** 首页路牌和猫园路牌上的字（两处同步） */
    val signText: String = DEFAULT_SIGN,
    /** 猫园背景上挂的节日挂件：位置(A~F) → 挂件编号 */
    val scene: Map<String, String> = emptyMap(),
    /** 动物编号 → 部位(A~H) → 饰品编号 */
    val outfits: Map<String, Map<String, String>> = emptyMap(),
    /** 桌面图标：default / sunset / minimal */
    val icon: String = "default",
    /** 成就编号 → 解锁时间 */
    val achievements: Map<String, Long> = emptyMap(),
    /** 查字典、编辑、备份等次数 */
    val counters: Map<String, Int> = emptyMap(),
    /** 彩蛋开着：商城里的东西全部能用（不算买到）。null 是 2.0.3 以前的存档，读进来时换算 */
    val egg: Boolean? = null,
    /** 成就页的配色方案（BadgeArt.badgeStyles 的编号） */
    val badgeStyle: String = "classic",
    /** 猫园里自己挪过位置的动物：那次阅读的开始时间 → 左上角在院子里的位置（占宽、高的比例） */
    val gardenSpots: Map<String, List<Float>> = emptyMap(),
) {
    /** 能不能用：彩蛋开着、买到了、免费的；成就奖励要点亮对应成就 */
    fun has(id: String): Boolean {
        if (egg == true || id == Shop.MASCOT || id in owned) return true
        val lock = Shop.lockOf(id)
        return if (lock != null) lock in achievements else Shop.priceOf(id) == 0
    }

    companion object {
        const val DEFAULT_SIGN = "猫园"
    }
}

/**
 * 成就（2.0.4 起照成就设计 HTML 的 49 个）。tier：铜/银/金/紫晶；icon：BadgeArt 里的图形；
 * item：奖励的东西（商城里 lock 是这个成就编号的那件），没有就是奖励叶子
 */
class Achievement(
    val id: String,
    val group: String,
    val title: String,
    val desc: String,
    val reward: Int,
    val tier: String,
    val icon: String,
    val tag: String,
    val item: String? = null,
    val hidden: Boolean = false,
    /** (现在, 目标) */
    val progress: (AchieveCtx) -> Pair<Long, Long>,
)

class AchieveGroup(val id: String, val name: String, val subtitle: String)

class AchieveCtx(val sessions: List<Session>, val books: List<Book>, val profile: Profile) {
    val totalMillis by lazy { sessions.sumOf { it.millis } }
    val totalMinutes by lazy { totalMillis / 60_000 }
    val dayMillis by lazy { Stats.byDay(sessions) }
    private fun streak(days: Collection<LocalDate>): Int {
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (d in days.sorted()) {
            run = if (prev != null && prev.plusDays(1) == d) run + 1 else if (prev == d) run else 1
            best = maxOf(best, run)
            prev = d
        }
        return best
    }
    val longestStreak by lazy { streak(dayMillis.keys) }
    val bestWeekCats by lazy {
        sessions.filter { Stats.isCat(it) }.groupingBy { Stats.monday(Stats.date(it.start)) }.eachCount().values.maxOrNull() ?: 0
    }
    fun hours(h: Long) = Pair(totalMinutes, h * 60)
    fun minutes(m: Long) = Pair(totalMinutes, m)
    fun count(key: String) = (profile.counters[key] ?: 0).toLong()
    val adoptable by lazy { Shop.catalog.animals.filter { it.price > 0 && it.id != Shop.TREASURE } }
    val ownedAnimals by lazy { adoptable.count { it.id in profile.owned }.toLong() }  // 彩蛋送的不算
    val ownedBgs by lazy { Shop.catalog.backgrounds.count { it.lock == null && profile.has(it.id) }.toLong() }
    val ownedAcc by lazy { Shop.catalog.accessories.count { it.id in profile.owned }.toLong() }
    val finished by lazy { books.count { it.finished }.toLong() }
    private fun hourOf(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).hour
    /** 深夜：23 点到凌晨 4 点开始读 */
    private fun lateNight(s: Session) = hourOf(s.start).let { it >= 23 || it < 4 }
    /** 夜间：晚上 8 点到凌晨 4 点 */
    private fun night(s: Session) = hourOf(s.start).let { it >= 20 || it < 4 }
    /** 清晨：4 点到 7 点 */
    private fun morning(s: Session) = hourOf(s.start) in 4..6
    /** 晚间：晚上 6 点以后 */
    private fun evening(s: Session) = hourOf(s.start).let { it >= 18 || it < 4 }
    val lateNights by lazy { sessions.count { lateNight(it) }.toLong() }
    val nightMinutes by lazy { sessions.filter { night(it) }.sumOf { it.millis } / 60_000 }
    val lateFinished by lazy { sessions.count { it.finished && lateNight(it) }.toLong() }
    val mornings by lazy { sessions.count { morning(it) }.toLong() }
    val eveningStreak by lazy { streak(sessions.filter { evening(it) }.map { Stats.date(it.start) }.toSet()).toLong() }
    val focusCount by lazy { sessions.count { Stats.isCat(it) }.toLong() }
    /** 已经点亮的成就数（只算现在这套） */
    val lit: Long by lazy { Achievements.all.count { it.id in profile.achievements }.toLong() }
}

private fun flag(b: Boolean) = Pair(if (b) 1L else 0L, 1L)

object Achievements {
    val groups = listOf(
        AchieveGroup("reading", "阅读", "时长与时段里程碑"),
        AchieveGroup("persist", "坚持", "连续打卡习惯养成"),
        AchieveGroup("bookshelf", "书架", "典藏与完本成就"),
        AchieveGroup("companions", "收集 · 萌宠与配饰", "阅读解锁动物伙伴与动物配饰"),
        AchieveGroup("scenery", "场景 · 背景与挂饰", "阅读解锁猫园背景与挂饰"),
        AchieveGroup("explore", "探索 · 图标与彩蛋", "专属桌面图标与隐藏彩蛋"),
    )

    val all: List<Achievement> = listOf(
        Achievement("first_read", "reading", "初次翻开", "读完第一次（至少一分钟）", 20, "铜", "book-open", "里程碑") { Pair(it.sessions.size.toLong(), 1) },
        Achievement("read_1h", "reading", "一小时", "累计阅读 1 小时", 50, "铜", "clock-1h", "时长") { it.hours(1) },
        Achievement("read_10h", "reading", "十小时", "累计阅读 10 小时", 100, "银", "clock-10h", "时长") { it.hours(10) },
        Achievement("read_50h", "reading", "半百", "累计阅读 50 小时", 250, "银", "hourglass-50", "时长") { it.hours(50) },
        Achievement("read_100h", "reading", "百小时书虫", "累计阅读 100 小时", 400, "金", "crown-book", "殿堂时长") { it.hours(100) },
        Achievement("day_30m", "reading", "长出叶子", "一天读满半小时，猫头上长出叶子", 60, "铜", "leaf-sprout", "成长") { Pair((it.dayMillis.values.maxOrNull() ?: 0) / 60_000, 30) },
        Achievement("day_2h", "reading", "沉浸的一天", "一天累计阅读满 120 分钟", 120, "银", "sun-tarot", "专注") { Pair((it.dayMillis.values.maxOrNull() ?: 0) / 60_000, 120) },
        Achievement("marathon", "reading", "一口气", "一次连续读 60 分钟", 100, "银", "target-lock", "心流") { Pair((it.sessions.maxOfOrNull { s -> s.millis } ?: 0) / 60_000, 60) },
        Achievement("night_owl", "reading", "夜猫子", "深夜 23 点以后读一次书", 80, "铜", "night-owl", "时段") { Pair(minOf(it.lateNights, 1), 1) },
        Achievement("early_bird", "reading", "早起的鸟", "早上 7 点前读一次书", 80, "铜", "early-bird", "时段") { Pair(minOf(it.mornings, 1), 1) },

        Achievement("streak_3", "persist", "三天打鱼", "连续 3 天都读了书", 90, "铜", "flame-3", "打卡") { Pair(it.longestStreak.toLong(), 3) },
        Achievement("streak_7", "persist", "一周不断", "连续 7 天都读了书", 150, "银", "flame-7", "打卡") { Pair(it.longestStreak.toLong(), 7) },
        Achievement("streak_30", "persist", "月读", "连续 30 天都读了书", 300, "金", "moon-flame", "月度满贯") { Pair(it.longestStreak.toLong(), 30) },
        Achievement("garden_10", "persist", "热闹的猫园", "一周里来了 10 只小动物", 120, "银", "cat-ears", "猫园陪伴") { Pair(it.bestWeekCats.toLong(), 10) },

        Achievement("shelf_1", "bookshelf", "第一本书", "往书架里放一本书", 60, "铜", "bookmark-01", "藏书") { Pair(it.books.size.toLong(), 1) },
        Achievement("shelf_10", "bookshelf", "小书房", "书架上有 10 本书", 120, "银", "bookshelf-row", "藏书") { Pair(it.books.size.toLong(), 10) },
        Achievement("finish_1", "bookshelf", "合上最后一页", "读完一本书", 150, "银", "book-check", "完本") { Pair(it.finished, 1) },
        Achievement("finish_5", "bookshelf", "五本", "读完 5 本书", 220, "银", "star-five", "完本") { Pair(it.finished, 5) },
        Achievement("finish_10", "bookshelf", "十本", "读完 10 本书", 360, "金", "crown-ten", "完本殿堂") { Pair(it.finished, 10) },

        Achievement("c0", "companions", "镇店之宝", "花费 9999 叶子迎回商城镇店之宝", 0, "金", "crown-grand", "9999神兽", item = "8 件神装") { flag(Shop.TREASURE in it.profile.owned) },
        Achievement("animal_1", "companions", "新朋友", "在商城领养第一只动物", 100, "铜", "paw-single", "领养动物") { Pair(it.ownedAnimals, 1) },
        Achievement("animal_5", "companions", "小小动物园", "领养 5 只动物", 160, "银", "paw-array", "领养动物") { Pair(it.ownedAnimals, 5) },
        Achievement("animal_all", "companions", "全员到齐", "领养商城里所有的动物", 260, "金", "crown-grand", "全图鉴") { Pair(it.ownedAnimals, it.adoptable.size.toLong().coerceAtLeast(1)) },
        Achievement("bg_3", "companions", "换个背景", "拥有 3 张猫园背景", 120, "银", "sprout-horizon", "猫园背景") { Pair(it.ownedBgs, 3) },
        Achievement("acc_10", "companions", "衣帽间", "收集 10 件饰品", 120, "银", "hanger-basic", "动物配饰") { Pair(it.ownedAcc, 10) },
        Achievement("dress_1", "companions", "打扮一下", "给动物戴上一件饰品", 120, "铜", "hanger-bowtie", "动物配饰") { flag(it.profile.outfits.values.any { o -> o.isNotEmpty() }) },
        Achievement("c7", "companions", "看板猫猫", "在首页连着摸看板猫 ? 下", 0, "银", "cyber-cat", "鎏金看板猫", item = "鎏金看板猫") { Pair(minOf(it.count("pet20"), 1), 1) },
        Achievement("c8", "companions", "橘猫驾到", "累计阅读满 300 分钟", 180, "银", "fox-spirit", "稀有伙伴") { it.minutes(300) },
        Achievement("c9", "companions", "神秘夜行兽", "深夜（23 点以后）读 10 次书", 260, "紫晶", "owl-mecha", "夜行") { Pair(it.lateNights, 10) },
        Achievement("c10", "companions", "巫师尖帽", "读完 3 本书", 0, "银", "wizard-hat", "解锁饰品", item = "巫师尖帽") { Pair(it.finished, 3) },
        Achievement("c11", "companions", "学霸圆眼镜", "长按查 50 次字典", 0, "银", "glasses-nerd", "解锁饰品", item = "学霸圆眼镜") { Pair(it.count("dict"), 50) },
        Achievement("c12", "companions", "红格小领结", "累计阅读满 180 分钟", 0, "铜", "bowtie-plaid", "解锁饰品", item = "红格小领结") { it.minutes(180) },
        Achievement("c13", "companions", "星光披风", "连续 30 天都读了书", 0, "金", "royal-cape", "解锁饰品", item = "星光披风") { Pair(it.longestStreak.toLong(), 30) },

        Achievement("c14", "scenery", "雨夜窗台", "晚上 8 点以后累计读满 3 小时", 0, "银", "rain-window", "解锁背景", item = "雨夜窗台背景") { Pair(it.nightMinutes, 180) },
        Achievement("c15", "scenery", "阳光旧书屋", "累计阅读满 500 分钟", 0, "银", "sun-library", "解锁背景", item = "阳光旧书屋背景") { it.minutes(500) },
        Achievement("c16", "scenery", "深林秘境", "点亮 15 枚成就", 0, "银", "forest-sanctuary", "解锁背景", item = "深林秘境背景") { Pair(it.lit, 15) },
        Achievement("c17", "scenery", "银河铁道", "深夜读完 5 本书", 0, "金", "galaxy-train", "解锁背景", item = "银河铁道背景") { Pair(it.lateFinished, 5) },
        Achievement("c18", "scenery", "晴天风铃", "清晨（4 点到 7 点）读 3 次书", 0, "铜", "wind-chime", "解锁挂件", item = "晴天风铃挂件") { Pair(it.mornings, 3) },
        Achievement("c19", "scenery", "复古壁钟", "累计阅读满 200 分钟", 0, "银", "pendulum-clock", "解锁挂件", item = "复古壁钟挂件") { it.minutes(200) },
        Achievement("c20", "scenery", "暖光吊灯", "连续 7 天晚上读书", 0, "银", "warm-lamp", "解锁挂件", item = "暖光吊灯挂件") { Pair(it.eveningStreak, 7) },
        Achievement("c21", "scenery", "漂浮星尘", "累计读 50 次书（每次至少 3 分钟）", 0, "紫晶", "star-mobile", "解锁挂件", item = "漂浮星尘挂件") { Pair(it.focusCount, 50) },

        Achievement("dict_10", "explore", "查字典", "长按查 10 次字典", 80, "铜", "dict-search", "探索") { Pair(it.count("dict"), 10) },
        Achievement("dict_100", "explore", "活字典", "长按查 100 次字典", 200, "银", "dict-master", "探索") { Pair(it.count("dict"), 100) },
        Achievement("edit_1", "explore", "动笔", "编辑并保存一章", 80, "铜", "pen-nib", "编辑") { Pair(it.count("edit"), 1) },
        Achievement("rename", "explore", "改个名字", "改一次路牌上的字", 80, "铜", "cyber-key", "个性化") { flag(it.profile.signText != Profile.DEFAULT_SIGN) },
        Achievement("backup_1", "explore", "有备无患", "第一次备份", 150, "铜", "cloud-shield", "备份") { Pair(it.count("backup"), 1) },
        // 奖励不是叶子：找到彩蛋就解锁全商城（Profiles.unlockEverything 打开彩蛋开关）
        Achievement("egg", "explore", "空叶里的秘密", "在“关于”里找到彩蛋", 0, "紫晶", "secret-box", "解锁全商城", item = "全商城所有物品", hidden = true) { Pair(it.count("egg"), 1) },
        Achievement("e7", "explore", "暗金浮雕", "累计阅读满 20 小时", 0, "金", "app-icon-gold", "桌面图标", item = "暗金浮雕图标") { it.hours(20) },
        Achievement("e8", "explore", "霓虹幻彩", "点亮 25 枚成就", 0, "紫晶", "app-icon-cyber", "桌面图标", item = "霓虹幻彩图标") { Pair(it.lit, 25) },
        Achievement("e9", "explore", "像素复古", "连续 14 天都读了书", 0, "银", "app-icon-pixel", "桌面图标", item = "像素复古图标") { Pair(it.longestStreak.toLong(), 14) },
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
}

object Profiles {
    private val _state = MutableStateFlow(Profile())
    val state: StateFlow<Profile> = _state
    val value: Profile get() = _state.value

    /** 新解锁的成就，界面上弹一句 */
    private val _unlocked = MutableSharedFlow<Achievement>(extraBufferCapacity = 16)
    val unlocked: SharedFlow<Achievement> = _unlocked

    private val saver = Debounced(300) { Store.write(FILE, Profile.serializer(), _state.value) }

    fun load() {
        _state.value = migrateEgg(Store.read(FILE, Profile.serializer()) ?: Profile())
    }

    /**
     * 2.0.3 以前彩蛋是把所有东西直接放进 owned，分不清哪些是自己买的。
     * 赚到的叶子（阅读分钟 + 成就奖励）减去手上的叶子就是花掉的：一片没花过，owned 全是彩蛋送的，清掉改成开关；
     * 花过就保留 owned（关掉彩蛋时这些不收回）
     */
    private fun migrateEgg(p: Profile): Profile {
        if (p.egg != null) return p
        if ((p.counters["egg"] ?: 0) == 0) return p.copy(egg = false)
        val earned = p.countedMillis / 60_000 + Achievements.all.filter { it.id in p.achievements }.sumOf { it.reward }
        return if (earned - p.leaves <= 0) p.copy(egg = true, owned = emptySet()) else p.copy(egg = true)
    }

    fun update(f: (Profile) -> Profile) {
        _state.update(f)
        saver.poke()
        check()
    }

    fun count(key: String, by: Int = 1) = update { it.copy(counters = it.counters + (key to (it.counters[key] ?: 0) + by)) }

    /** 阅读记录变了：新读的整分钟换成叶子 */
    fun syncReading(sessions: List<Session>) {
        val total = sessions.sumOf { it.millis }
        _state.update { p ->
            val gained = (total / 60_000 - p.countedMillis / 60_000).toInt()
            if (gained > 0) p.copy(leaves = p.leaves + gained, countedMillis = total)
            else if (total < p.countedMillis) p.copy(countedMillis = total)
            else p
        }
        saver.poke()
        check()
    }

    /** 买东西：钱够就扣叶子，返回是否买到 */
    fun buy(id: String): Boolean {
        val price = Shop.priceOf(id) ?: return false
        if (Shop.lockOf(id) != null) return false  // 成就奖励不卖
        var ok = false
        update { p ->
            if (p.has(id) || p.leaves < price) p
            else {
                ok = true
                p.copy(leaves = p.leaves - price, owned = p.owned + id)
            }
        }
        return ok
    }

    fun equip(animal: String, slot: String, acc: String?) = update { p ->
        val cur = p.outfits[animal].orEmpty()
        val next = if (acc == null) cur - slot else cur + (slot to acc)
        p.copy(outfits = if (next.isEmpty()) p.outfits - animal else p.outfits + (animal to next))
    }

    /** 挂件挂到猫园的某个位置上，null 是摘下 */
    fun hang(slot: String, orn: String?) = update { p -> p.copy(scene = if (orn == null) p.scene - slot else p.scene + (slot to orn)) }

    /** 彩蛋：商城里的东西全部能用（之后上架的也算） */
    fun unlockEverything() = update { p -> p.copy(egg = true, counters = p.counters + ("egg" to 1)) }

    /** 彩蛋开关。关掉时，正在用的东西里没买到的换回默认 */
    fun setEgg(on: Boolean) = update { p ->
        if (on) return@update p.copy(egg = true)
        val q = p.copy(egg = false)
        q.copy(
            homeAnimal = q.homeAnimal.takeIf { q.has(it) } ?: Shop.MASCOT,
            gardenBg = q.gardenBg.takeIf { q.has(it) } ?: Shop.DEFAULT_BG,
            gardenAnimals = q.gardenAnimals.filter { q.has(it) }.ifEmpty { listOf(Shop.MASCOT) },
            outfits = q.outfits.mapValues { (_, o) -> o.filterValues { q.has(it) } }.filter { it.value.isNotEmpty() },
            scene = q.scene.filterValues { q.has(it) },
            icon = q.icon.takeIf { q.has("icon_$it") } ?: "default",
        )
    }

    /** 检查有没有新达成的成就，有就发奖励 */
    fun check() {
        val ctx = AchieveCtx(Stats.sessions.value, Library.books.value, _state.value)
        val fresh = Achievements.all.filter { it.id !in ctx.profile.achievements && it.progress(ctx).let { (a, b) -> a >= b } }
        if (fresh.isEmpty()) return
        val now = System.currentTimeMillis()
        _state.update { p ->
            p.copy(
                achievements = p.achievements + fresh.associate { it.id to now },
                leaves = p.leaves + fresh.sumOf { it.reward },
            )
        }
        saver.poke()
        fresh.forEach { _unlocked.tryEmit(it) }
        // 发的叶子可能又让“收集”类成就达成，再查一遍
        check()
    }

    fun flush() = saver.flush()

    private const val FILE = "profile.json"
}
