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
) {
    fun has(id: String) = egg == true || id == Shop.MASCOT || id in owned || Shop.priceOf(id) == 0

    companion object {
        const val DEFAULT_SIGN = "猫园"
    }
}

class Achievement(
    val id: String,
    val group: String,
    val title: String,
    val desc: String,
    val reward: Int,
    val hidden: Boolean = false,
    /** (现在, 目标) */
    val progress: (AchieveCtx) -> Pair<Long, Long>,
)

class AchieveCtx(val sessions: List<Session>, val books: List<Book>, val profile: Profile) {
    val totalMillis by lazy { sessions.sumOf { it.millis } }
    val dayMillis by lazy { Stats.byDay(sessions) }
    val longestStreak by lazy {
        val days = dayMillis.keys.sorted()
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (d in days) {
            run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
            best = maxOf(best, run)
            prev = d
        }
        best
    }
    val bestWeekCats by lazy {
        sessions.filter { Stats.isCat(it) }.groupingBy { Stats.monday(Stats.date(it.start)) }.eachCount().values.maxOrNull() ?: 0
    }
    fun hours(h: Long) = Pair(totalMillis / 60_000, h * 60)
    fun count(key: String) = (profile.counters[key] ?: 0).toLong()
    /** 领养的动物只算要叶子换的（极简猫白送） */
    val adoptable by lazy { Shop.catalog.animals.filter { it.price > 0 } }
    val ownedAnimals by lazy { adoptable.count { it.id in profile.owned }.toLong() }  // 彩蛋送的不算
    val ownedBgs by lazy { Shop.catalog.backgrounds.count { profile.has(it.id) }.toLong() }
    val ownedAcc by lazy { Shop.catalog.accessories.count { it.id in profile.owned }.toLong() }
    private fun hourOf(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).hour
    val nightOwl by lazy { sessions.any { hourOf(it.start) in 0..3 } }
    val earlyBird by lazy { sessions.any { hourOf(it.start) in 5..6 } }
}

private fun flag(b: Boolean) = Pair(if (b) 1L else 0L, 1L)

object Achievements {
    val all = listOf(
        Achievement("first_read", "阅读", "初次翻开", "读完第一次（至少一分钟）", 20) { Pair(it.sessions.size.toLong(), 1) },
        Achievement("read_1h", "阅读", "一小时", "累计阅读 1 小时", 30) { it.hours(1) },
        Achievement("read_10h", "阅读", "十小时", "累计阅读 10 小时", 100) { it.hours(10) },
        Achievement("read_50h", "阅读", "半百", "累计阅读 50 小时", 300) { it.hours(50) },
        Achievement("read_100h", "阅读", "百小时书虫", "累计阅读 100 小时", 600) { it.hours(100) },
        Achievement("day_30m", "阅读", "长出叶子", "一天读满半小时，猫头上长出叶子", 30) { Pair((it.dayMillis.values.maxOrNull() ?: 0) / 60_000, 30) },
        Achievement("day_2h", "阅读", "沉浸的一天", "一天读满两小时", 100) { Pair((it.dayMillis.values.maxOrNull() ?: 0) / 60_000, 120) },
        Achievement("marathon", "阅读", "一口气", "一次连续读一小时", 80) { Pair((it.sessions.maxOfOrNull { s -> s.millis } ?: 0) / 60_000, 60) },
        Achievement("night_owl", "阅读", "夜猫子", "凌晨零点到四点之间翻开书", 50) { flag(it.nightOwl) },
        Achievement("early_bird", "阅读", "早起的鸟", "早上五六点开始读书", 50) { flag(it.earlyBird) },
        Achievement("streak_3", "坚持", "三天打鱼", "连续 3 天都读了书", 50) { Pair(it.longestStreak.toLong(), 3) },
        Achievement("streak_7", "坚持", "一周不断", "连续 7 天都读了书", 150) { Pair(it.longestStreak.toLong(), 7) },
        Achievement("streak_30", "坚持", "月读", "连续 30 天都读了书", 500) { Pair(it.longestStreak.toLong(), 30) },
        Achievement("garden_10", "坚持", "热闹的猫园", "一周里来了 10 只小动物", 100) { Pair(it.bestWeekCats.toLong(), 10) },
        Achievement("shelf_1", "书架", "第一本书", "往书架里放一本书", 20) { Pair(it.books.size.toLong(), 1) },
        Achievement("shelf_10", "书架", "小书房", "书架上有 10 本书", 100) { Pair(it.books.size.toLong(), 10) },
        Achievement("finish_1", "书架", "合上最后一页", "读完一本书", 50) { Pair(it.books.count { b -> b.finished }.toLong(), 1) },
        Achievement("finish_5", "书架", "五本", "读完 5 本书", 150) { Pair(it.books.count { b -> b.finished }.toLong(), 5) },
        Achievement("finish_10", "书架", "十本", "读完 10 本书", 400) { Pair(it.books.count { b -> b.finished }.toLong(), 10) },
        Achievement("animal_1", "收集", "新朋友", "在商城领养第一只动物", 50) { Pair(it.ownedAnimals, 1) },
        Achievement("animal_5", "收集", "小小动物园", "领养 5 只动物", 100) { Pair(it.ownedAnimals, 5) },
        Achievement("animal_all", "收集", "全员到齐", "领养商城里所有的动物", 1000) { Pair(it.ownedAnimals, it.adoptable.size.toLong().coerceAtLeast(1)) },
        Achievement("bg_3", "收集", "换个风景", "拥有 3 张猫园背景", 80) { Pair(it.ownedBgs, 3) },
        Achievement("acc_10", "收集", "衣帽间", "收集 10 件饰品", 100) { Pair(it.ownedAcc, 10) },
        Achievement("scene_6", "收集", "过节啦", "猫园的六个位置都挂上节日挂件", 100) { Pair(it.profile.scene.size.toLong(), 6) },
        Achievement("dress_1", "收集", "打扮一下", "给动物戴上一件饰品", 30) { flag(it.profile.outfits.values.any { o -> o.isNotEmpty() }) },
        Achievement("dict_10", "探索", "查字典", "长按查 10 次字典", 20) { Pair(it.count("dict"), 10) },
        Achievement("dict_100", "探索", "活字典", "长按查 100 次字典", 100) { Pair(it.count("dict"), 100) },
        Achievement("edit_1", "探索", "动笔", "编辑并保存一章", 30) { Pair(it.count("edit"), 1) },
        Achievement("rename", "探索", "改个名字", "改一次路牌上的字", 20) { flag(it.profile.signText != Profile.DEFAULT_SIGN) },
        Achievement("backup_1", "探索", "有备无患", "第一次备份", 30) { Pair(it.count("backup"), 1) },
        Achievement("egg", "探索", "空叶里的秘密", "在“关于”里找到彩蛋", 0, hidden = true) { Pair(it.count("egg"), 1) },
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
