package com.kongye.reader

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kongye.reader.data.*
import com.kongye.reader.ui.Nav
import com.kongye.reader.ui.Screen
import com.kongye.reader.ui.components.T
import com.kongye.reader.ui.components.badgeStyles
import com.kongye.reader.ui.screens.Badge
import com.kongye.reader.ui.theme.*
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class AchievementChecks(private val test: Instrumentation) {
    private val output=File(test.targetContext.filesDir,"achievement-checks").apply { mkdirs() }
    private val log=StringBuilder()
    private lateinit var activity: MainActivity
    private val ids=listOf("r11","r12","r13","r14","p5","p6","p7","p8","b6","b7","c22","c23","c24","e10","e11")
    private fun stamp(day: LocalDate)=day.atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun session(id: String="a",day: LocalDate=LocalDate.of(2026,1,1),minutes: Long=10,done: Boolean=false,animal: String="",chars: Long=0): Session {
        val start=stamp(day)
        return Session(id,id,start,start+minutes*60_000,minutes*60_000,finished=done,gardenAnimal=animal,bookChars=chars)
    }
    private fun ctx(s: List<Session> = emptyList(),b: List<Book> = emptyList(),p: Profile=Profile(egg=false))=AchieveCtx(s,b,p)
    private fun earned(id: String,c: AchieveCtx): Boolean=Achievements.byId(id)!!.progress(c).let { it.first>=it.second }
    private fun expect(id: String,c: AchieveCtx,value: Boolean=true) {
        check(earned(id,c)==value) { "$id expected=$value actual=${Achievements.byId(id)!!.progress(c)}" }
        log.append("$id: ${if(value) "positive" else "negative"} passed\n")
    }

    fun run() {
        try { conditions(); persistence(); screens(); gallery() }
        finally {
            File(output,"result.txt").writeText(log.toString())
            if(::activity.isInitialized) test.runOnMainSync { activity.finish() }
        }
    }

    private fun conditions() {
        check(ids.size==15 && ids.all { Achievements.byId(it)!=null })
        check(Achievements.all.map { it.id }.distinct().size==Achievements.all.size)
        val first=session(minutes=10)
        val returnStart=first.end+14L*24*60*60_000
        val returned=Session("a","a",returnStart,returnStart+600_000,600_000)
        expect("r11",ctx(listOf(first,returned)))
        expect("r11",ctx(listOf(first,returned.copy(start=returnStart-1))),false)
        expect("r11",ctx(listOf(first,returned.copy(millis=599_999))),false)
        expect("r12",ctx(listOf(first.copy(finished=true),session("b",LocalDate.of(2026,1,2),30))))
        expect("r12",ctx(listOf(first.copy(finished=true),session("a",LocalDate.of(2026,1,2),30))),false)
        expect("r12",ctx(listOf(first.copy(finished=true),session("b",LocalDate.of(2025,12,31),30))),false)
        val voyage=session(minutes=1800,done=true,chars=1_000_001)
        expect("r13",ctx(listOf(voyage)))
        expect("r13",ctx(listOf(voyage.copy(bookChars=1_000_000))),false)
        expect("r13",ctx(listOf(voyage.copy(finished=false))),false)
        expect("r13",ctx(listOf(voyage.copy(millis=30*3_600_000L-1))),false)
        expect("r14",ctx(listOf(session(minutes=60_000))))
        expect("r14",ctx(listOf(session(minutes=59_999))),false)
        val days=(1..20).map { session(day=LocalDate.of(2026,1,it)) }
        expect("p5",ctx(days));expect("p5",ctx(days.take(19)),false)
        expect("p5",ctx(days.take(10)+days.take(10).map { it.copy(start=it.start+31L*24*3_600_000) }),false)
        expect("p6",ctx(listOf(1,4,7,10).map { session(day=LocalDate.of(2026,it,1)) }))
        expect("p6",ctx(listOf(1,4,7).map { session(day=LocalDate.of(2026,it,1)) }+session(day=LocalDate.of(2025,10,1))),false)
        val year=(0..11).flatMap { m -> (1..15).map { d->session(day=YearMonth.of(2025,12).plusMonths(m.toLong()).atDay(d)) } }
        expect("p7",ctx(year));expect("p7",ctx(year.drop(1)),false)
        val letters=(1..12).map { m->session("book$m",LocalDate.of(2026,m,1),60,true,chars=200_001) }
        expect("p8",ctx(letters));expect("p8",ctx(letters.dropLast(1)),false)
        expect("p8",ctx(letters.map { it.copy(bookChars=200_000) }),false)
        val memory=AchievementMemory(editedBooks=setOf("a","b","c"),wakeDates=setOf("1","2","3"),searchedWords=mapOf("a" to (1..10).map { "词语$it" }.toSet()))
        expect("b6",ctx(p=Profile(achievementMemory=memory)))
        expect("b6",ctx(p=Profile(achievementMemory=memory.copy(editedBooks=setOf("a","b")))),false)
        val world=(1..30).map { session(day=LocalDate.of(2026,1,it),minutes=60,done=it==30) }
        expect("b7",ctx(world));expect("b7",ctx(world.map { it.copy(finished=false) }),false)
        expect("b7",ctx(world.map { it.copy(start=world.first().start) }),false)
        val visitors=(1..10).map { session("book$it",minutes=60,done=true,animal="animal$it") }
        expect("c22",ctx(visitors.take(5)));expect("c22",ctx(visitors.take(4)),false)
        expect("c22",ctx(visitors.take(5).map { it.copy(millis=179_999,finished=false) }),false)
        expect("c23",ctx(p=Profile(achievementMemory=memory)))
        expect("c23",ctx(p=Profile(achievementMemory=memory.copy(wakeDates=setOf("1","2")))),false)
        expect("c24",ctx(visitors));expect("c24",ctx(visitors.dropLast(1)),false)
        expect("c24",ctx(visitors.map { it.copy(millis=3_599_999) }),false)
        expect("c24",ctx(visitors.map { it.copy(gardenAnimal="same") }),false)
        expect("e10",ctx(p=Profile(achievementMemory=memory)))
        expect("e10",ctx(p=Profile(achievementMemory=memory.copy(searchedWords=mapOf("a" to setOf("重复"))))),false)
        val gold=Achievements.groups.map { g->Achievements.all.first { it.group==g.id && it.tier=="金" && it.id!="e11" }.id }.associateWith { 1L }
        expect("e11",ctx(p=Profile(achievements=gold)))
        expect("e11",ctx(p=Profile(achievements=gold-gold.keys.first()+mapOf("e11" to 1L))),false)
        val sorted=Achievements.groups.flatMap { AchievementOrdering.category(it.id) }
        check(sorted.map { it.id }.toSet()==Achievements.all.map { it.id }.toSet() && sorted.size==Achievements.all.size)
        val reading=AchievementOrdering.category("reading").map { it.id }
        check(reading.first()=="first_read" && reading.last()=="r14")
        check(listOf("read_1h","read_10h","read_50h","read_100h","r14").map { reading.indexOf(it) }.zipWithNext().all { it.first<it.second })
        check(Shop.catalog.festivals.first { it.id=="mothers" }.name=="骄傲日")
        check(Shop.catalog.ornaments.count { it.fest=="mothers" }==6)
        log.append("All fifteen conditions, difficulty chains, catalog coverage and Pride collection passed\n")
    }

    private fun persistence() {
        val old=Store.json.decodeFromString(Profile.serializer(),"""{"achievements":{"first_read":123},"leaves":70,"egg":false}""")
        check(old.unreadAchievements.isEmpty() && old.achievements["first_read"]==123L && old.leaves==70)
        Stats.clear()
        test.runOnMainSync { Profiles.update { Profile(egg=false,counters=mapOf("dict" to 10)) } }
        check("dict_10" in Profiles.value.unreadAchievements)
        val leaves=Profiles.value.leaves
        val workers=(1..6).map { Thread { repeat(10) { Profiles.check() } }.apply { start() } }
        workers.forEach { it.join() }
        check(Profiles.value.leaves==leaves)
        Profiles.markAchievementViewed("dict_100")
        test.runOnMainSync { Profiles.update { it.copy(counters=mapOf("dict" to 100)) } }
        check("dict_100" in Profiles.value.unreadAchievements)
        Profiles.markAchievementViewed("dict_100")
        check("dict_100" !in Profiles.value.unreadAchievements && "dict_10" in Profiles.value.unreadAchievements)
        val saveUntil=SystemClock.uptimeMillis()+3000
        while(SystemClock.uptimeMillis()<saveUntil) {
            val disk=Store.read("profile.json",Profile.serializer())
            if(disk!=null && "dict_100" in disk.achievements && "dict_100" !in disk.unreadAchievements) break
            SystemClock.sleep(30)
        }
        Profiles.load()
        check("dict_100" !in Profiles.value.unreadAchievements)
        test.runOnMainSync { Profiles.update { Profile(egg=false) } }
        repeat(2) { AchievementTracking.lookedUp("one","同一个词") }
        check(Profiles.value.achievementMemory.searchedWords["one"]?.size==1)
        AchievementTracking.lookedUp("one","字")
        check(Profiles.value.achievementMemory.searchedWords["one"]?.size==1)
        repeat(2) { AchievementTracking.wokeCat() }
        check(Profiles.value.achievementMemory.wakeDates.size==1)
        // Sessions that merge past three minutes must create exactly one real visitor.
        val snapshotId="achievement-visitor-snapshot"
        Library.add(Book(snapshotId,"历史快照",chars=1_000_001))
        try {
            Stats.clear()
            test.runOnMainSync { Profiles.update { Profile(egg=false,owned=setOf("cat","dog"),gardenAnimals=listOf("cat","dog")) } }
            val first=session(snapshotId,minutes=1)
            Stats.record(first)
            check(Stats.sessions.value.single().gardenAnimal.isBlank())
            Stats.record(first.copy(start=first.end+30_000,end=first.end+150_000,millis=120_000))
            val visitor=Stats.sessions.value.single()
            check(visitor.millis==180_000L && visitor.gardenAnimal=="cat" && visitor.bookChars==1_000_001L)
            Stats.record(session("another-visitor",day=LocalDate.of(2026,1,2),minutes=3))
            check(Stats.sessions.value.last().gardenAnimal=="dog")
            test.runOnMainSync { Profiles.update { it.copy(gardenAnimals=listOf("dog","cat")) } }
            check(Stats.sessions.value.first().gardenAnimal=="cat")
            val saved=Store.json.decodeFromString(Session.serializer(),Store.json.encodeToString(Session.serializer(),visitor))
            check(saved==visitor)
        } finally { Library.remove(snapshotId);Stats.clear() }
        log.append("Merged-session visitor assignment, stable animal identity and book-length serialization passed\n")
        val fixture=(1..3).map { "achievement-info-$it" }
        try {
            fixture.forEach { Library.add(Book(it,"测试")); Library.updateInfo(it,"只改标题","","",emptyList()) }
            check(Profiles.value.achievementMemory.editedBooks.isEmpty())
            fixture.forEach { Library.updateInfo(it,"测试","测试作者","",emptyList()); Library.updateInfo(it,"测试","另一个作者","",emptyList()) }
            check(Profiles.value.achievementMemory.editedBooks.size==3 && "b6" in Profiles.value.achievements)
        } finally { fixture.forEach { Library.remove(it) } }
        log.append("Legacy compatibility, one-time rewards, per-badge NEW persistence, word/day dedup and real metadata hooks passed\n")
    }

    private fun nodes(): List<SemanticsNode> {
        val found=ArrayList<SemanticsNode>()
        test.runOnMainSync {
            fun add(n: SemanticsNode) { found+=n;n.children.forEach(::add) }
            fun visit(v: View) {
                val getter=v.javaClass.methods.firstOrNull { it.name=="getSemanticsOwner" && it.parameterCount==0 }
                if(getter!=null) add((getter.invoke(v) as SemanticsOwner).rootSemanticsNode)
                else if(v is ViewGroup) for(i in 0 until v.childCount) visit(v.getChildAt(i))
            }
            visit(activity.window.decorView)
        }
        return found.filter { it.boundsInRoot.width>0 && it.boundsInRoot.height>0 }
    }
    private fun labels(n: SemanticsNode)=n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }+n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
    private fun screenshot(name: String) {
        val image=Bitmap.createBitmap(activity.window.decorView.width,activity.window.decorView.height,Bitmap.Config.ARGB_8888)
        val done=CountDownLatch(1);var result=-1
        test.runOnMainSync { PixelCopy.request(activity.window,image,{result=it;done.countDown()},Handler(Looper.getMainLooper())) }
        check(done.await(4,TimeUnit.SECONDS) && result==PixelCopy.SUCCESS)
        File(output,"$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) };image.recycle()
    }
    private fun screens() {
        test.runOnMainSync {
            Settings.update { it.copy(theme=1,resumeLast=false) }
            Profiles.update { Profile(egg=false,achievements=mapOf("first_read" to 1L),unreadAchievements=setOf("first_read")) }
            Nav.home()
        }
        activity=test.startActivitySync(Intent(test.targetContext,MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) }) as MainActivity
        test.runOnMainSync { activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        SystemClock.sleep(3500)
        test.runOnMainSync { Nav.push(Screen.Achievements) };SystemClock.sleep(900)
        check(nodes().any { "未查看的新成就：初次翻开" in labels(it) })
        screenshot("new-before-view")
        val all=nodes();val target=all.last { "初次翻开" in labels(it) }.boundsInRoot.center
        val click=all.filter { it.config.getOrNull(SemanticsActions.OnClick)!=null && it.boundsInRoot.contains(target) }.minBy { it.boundsInRoot.width*it.boundsInRoot.height }.boundsInRoot.center
        val down=SystemClock.uptimeMillis()
        for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
            val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,click.x,click.y,0)
            event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
            test.runOnMainSync { activity.dispatchTouchEvent(event) };event.recycle();SystemClock.sleep(40)
        }
        SystemClock.sleep(600)
        check("first_read" !in Profiles.value.unreadAchievements)
        test.runOnMainSync { activity.onBackPressedDispatcher.onBackPressed() };SystemClock.sleep(500)
        check(nodes().none { "未查看的新成就：初次翻开" in labels(it) })
        screenshot("new-after-view")
        log.append("Real Compose NEW corner marker and detail-click consumption passed\n")
    }

    private fun gallery() {
        for(dark in listOf(false,true)) for(lit in listOf(false,true)) {
            test.runOnMainSync {
                activity.setContent {
                    CompositionLocalProvider(LocalPalette provides if(dark) DarkPalette else LightPalette) {
                        Column(Modifier.fillMaxSize().background(K.c.bg).padding(12.dp)) {
                            T("新增十五枚成就 · ${if(lit) "已点亮" else "未点亮"}",18.sp,K.c.ink)
                            for(row in ids.chunked(3)) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                                for(id in row) Column(Modifier.padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                                    val badge=Achievements.byId(id)!!
                                    Badge(badge,lit,.4f,badgeStyles.first(),74.dp)
                                    T(badge.title,11.sp,K.c.ink)
                                }
                            }
                        }
                    }
                }
            }
            SystemClock.sleep(400);screenshot("fifteen-${if(dark) "dark" else "light"}-${if(lit) "earned" else "locked"}")
        }
        log.append("All fifteen supplied vector badges captured lit/locked in both themes\n")
    }
}
