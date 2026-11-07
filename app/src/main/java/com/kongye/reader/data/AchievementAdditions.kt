package com.kongye.reader.data

import kotlinx.serialization.Serializable
import java.time.YearMonth
import java.util.Locale

/** Existing profiles decode with empty memory; earned badges/rewards are never reset. */
@Serializable
data class AchievementMemory(
    val editedBooks: Set<String> = emptySet(),
    val searchedWords: Map<String, Set<String>> = emptyMap(),
    val wakeDates: Set<String> = emptySet(),
    val returnStartedAt: Map<String, Long> = emptyMap(),
)

object AchievementTracking {
    fun openedBook(id: String, previous: Long, now: Long) {
        if (previous <= 0 || now-previous < 14L*24*60*60_000 || "r11" in Profiles.value.achievements) return
        Profiles.update { p -> p.copy(achievementMemory=p.achievementMemory.copy(
            returnStartedAt=p.achievementMemory.returnStartedAt+(id to now))) }
    }

    fun editedBook(id: String) {
        val old=Profiles.value.achievementMemory.editedBooks
        if (id in old || old.size>=3) return
        Profiles.update { p -> p.copy(achievementMemory=p.achievementMemory.copy(editedBooks=p.achievementMemory.editedBooks+id)) }
    }

    fun lookedUp(id: String, raw: String) {
        val word=raw.trim().lowercase(Locale.ROOT)
        if (word.length<2) return
        val words=Profiles.value.achievementMemory.searchedWords[id].orEmpty()
        if (word in words || words.size>=10) return
        Profiles.update { p -> p.copy(achievementMemory=p.achievementMemory.copy(
            searchedWords=p.achievementMemory.searchedWords+(id to (p.achievementMemory.searchedWords[id].orEmpty()+word)))) }
    }

    fun wokeCat() {
        val date=Stats.today().toString()
        val old=Profiles.value.achievementMemory.wakeDates
        if (date in old || old.size>=3) return
        Profiles.update { p -> p.copy(achievementMemory=p.achievementMemory.copy(wakeDates=p.achievementMemory.wakeDates+date)) }
    }
}

/** Derive milestones from real sessions, retaining historical progress wherever possible. */
class AddedAchievementContext(private val ctx: AchieveCtx) {
    private val sessions get()=ctx.sessions
    private val byBook by lazy { sessions.groupBy { it.bookId } }
    private val books by lazy { ctx.books.associateBy { it.id } }
    private val completed by lazy { ctx.books.filter { it.finished }.map { it.id }.toSet()+sessions.filter { it.finished }.map { it.bookId } }
    private fun chars(id: String)=maxOf(books[id]?.chars ?: 0, byBook[id].orEmpty().maxOfOrNull { it.bookChars } ?: 0)
    private fun millis(id: String)=maxOf(books[id]?.readMillis ?: 0,byBook[id].orEmpty().sumOf { it.millis })
    private fun progress(value: Long, target: Long)=value.coerceIn(0,target) to target

    val reunion by lazy {
        var best=0L
        byBook.forEach { (id, list) ->
            val start=ctx.profile.achievementMemory.returnStartedAt[id]
            if(start!=null) best=maxOf(best,list.filter { it.start>=start }.sumOf { it.millis })
            val ordered=list.sortedBy { it.start }
            var returning=false; var accumulated=0L
            for(i in ordered.indices) {
                if(i>0 && ordered[i].start-ordered[i-1].end>=14L*24*60*60_000) { returning=true; accumulated=0 }
                if(returning) { accumulated+=ordered[i].millis; best=maxOf(best,accumulated) }
            }
        }
        progress(best/60_000,10)
    }

    val relay by lazy {
        val completions=sessions.filter { it.finished }
        val best=byBook.filter { (id,list) ->
            val first=list.minOf { it.start }
            completions.any { it.bookId!=id && it.end<=first }
        }.values.maxOfOrNull { list -> list.sumOf { it.millis } } ?: 0
        progress(best/60_000,30)
    }

    val voyage by lazy {
        val best=(books.keys+byBook.keys).filter { chars(it)>1_000_000 }.maxOfOrNull { id ->
            (millis(id)/3_600_000).coerceAtMost(if(id in completed) 30 else 29)
        } ?: 0
        progress(best,30)
    }

    val slowMonth by lazy {
        progress((ctx.dayMillis.keys.groupingBy { YearMonth.from(it) }.eachCount().values.maxOrNull() ?: 0).toLong(),20)
    }
    val seasons by lazy {
        progress((ctx.dayMillis.keys.groupBy { it.year }.values.maxOfOrNull { days -> days.map { (it.monthValue-1)/3 }.distinct().size } ?: 0).toLong(),4)
    }
    val evergreen by lazy {
        val months=ctx.dayMillis.keys.groupingBy { YearMonth.from(it) }.eachCount().filterValues { it>=15 }.keys.sorted()
        var best=0;var run=0;var previous: YearMonth?=null
        for(month in months) { run=if(previous?.plusMonths(1)==month) run+1 else 1;best=maxOf(best,run);previous=month }
        progress(best.toLong(),12)
    }
    val letters by lazy {
        val dates=sessions.filter { it.finished && chars(it.bookId)>200_000 }.map { Stats.date(it.end) }
        progress((dates.groupBy { it.year }.values.maxOfOrNull { it.map { d->d.monthValue }.distinct().size } ?: 0).toLong(),12)
    }
    val study get()=progress(ctx.profile.achievementMemory.editedBooks.size.toLong(),3)
    val world by lazy {
        val best=byBook.maxOfOrNull { (id,list) ->
            minOf(list.map { Stats.date(it.start) }.distinct().size.toLong(),millis(id)/3_600_000)
                .coerceAtMost(if(id in completed) 30 else 29)
        } ?: 0
        progress(best,30)
    }
    val parade by lazy { progress(sessions.filter { Stats.isCat(it) }.map { it.gardenAnimal }.filter { it.isNotBlank() }.distinct().size.toLong(),5) }
    val wake get()=progress(ctx.profile.achievementMemory.wakeDates.size.toLong(),3)
    val graduation by lazy {
        val pairs=sessions.filter { it.gardenAnimal.isNotBlank() && it.bookId in completed }
            .groupBy { it.gardenAnimal to it.bookId }
        progress(pairs.filterValues { it.sumOf { s->s.millis }>=3_600_000 }.keys.map { it.first }.distinct().size.toLong(),10)
    }
    val words get()=progress((ctx.profile.achievementMemory.searchedWords.values.maxOfOrNull { it.size } ?: 0).toLong(),10)
    val stars by lazy {
        progress(Achievements.groups.count { g -> Achievements.all.any { it.group==g.id && it.tier=="金" && it.id!="e11" && it.id in ctx.profile.achievements } }.toLong(),6)
    }
}

/** Group identity remains unchanged; within a category use difficulty bands and related chains. */
object AchievementOrdering {
    private val ids=listOf(
        "first_read","day_30m","read_1h","early_bird","night_owl","r11","r12","marathon","day_2h","read_10h","read_50h","read_100h","r13","r14",
        "streak_3","streak_7","garden_10","p5","p6","streak_30","p8","p7",
        "shelf_1","b6","shelf_10","finish_1","finish_5","finish_10","b7",
        "dress_1","animal_1","c23","bg_3","acc_10","c7","c7m","c12","c8","animal_5","c22","c10","c11","c9","c13","c24","animal_all","c0",
        "c18","c19","c15","c14","c20","c21","c16","c17",
        "rename","edit_1","backup_1","dict_10","e10","dict_100","e9","e7","e8","e7m","e8m","e9m","egg","e11"
    )
    fun category(group: String)=Achievements.all.filter { it.group==group }.sortedWith(compareBy<Achievement> { ids.indexOf(it.id).let { n->if(n<0) Int.MAX_VALUE else n } }.thenBy { it.title })
}
