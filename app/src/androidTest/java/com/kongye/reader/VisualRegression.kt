package com.kongye.reader

import android.app.Instrumentation
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.kongye.reader.ui.components.drawImportedFocus
import com.kongye.reader.ui.components.drawImportedHappy
import com.kongye.reader.ui.components.drawImportedSleep
import com.kongye.reader.ui.components.drawImportedLoaf
import com.kongye.reader.ui.components.drawReadingCatBar
import com.kongye.reader.ui.components.catGazeAt
import com.kongye.reader.data.CatRest
import com.kongye.reader.data.CatRestPolicy
import com.kongye.reader.data.CatWakeMemory
import com.kongye.reader.data.Stats
import java.time.LocalDateTime
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.PI

/** Dependency-free device checks for the artwork's edge cases and actual splash handoff. */
class VisualRegression : Instrumentation() {
    private lateinit var output: File
    private var skipOnly = false
    private var gazeOnly = false
    private var controlsOnly = false
    private var loafOnly = false

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        skipOnly = arguments?.getString("check") == "splash-skip"
        gazeOnly = arguments?.getString("check") == "gaze-distance"
        controlsOnly = arguments?.getString("check") == "home-controls"
        loafOnly = arguments?.getString("check") == "loaf"
        start()
    }

    override fun onStart() {
        output = File(targetContext.filesDir, "visual-regression").apply { mkdirs() }
        try {
            if (loafOnly) {
                checkLoaf()
                finish(0, Bundle().apply { putString("result", "PASS: supplied loaf artwork renders in both themes, full tail stays in bounds throughout breathing; empty shelf/stats/timeline captured") })
                return
            }
            if (controlsOnly) {
                checkHomeControls()
                finish(0, Bundle().apply { putString("result", "PASS: five homepage controls do not move pupils; drag beginning on a button is excluded; blank-area tracking and navigation still work; updated cat captured in settings") })
                return
            }
            if (gazeOnly) {
                checkGazeLimits()
                checkGazeDistance()
                finish(0, Bundle().apply { putString("result", "PASS: pupil travel increases with finger distance, follows direction, stays in eye whites and resets on release") })
                return
            }
            if (skipOnly) {
                checkSplashSkip()
                finish(0, Bundle().apply { putString("result", "PASS: one tap skips splash immediately without activating the button underneath") })
                return
            }
            checkPaws()
            checkGrayEars()
            checkGazeLimits()
            checkRestPolicy()
            checkSplashHandoff()
            finish(0, Bundle().apply { putString("result", "PASS: visual regressions, rest/wake reading cycles, overnight persistence, and speech above cat") })
        } catch (error: Throwable) {
            finish(1, Bundle().apply { putString("error", error.stackTraceToString()) })
        }
    }

    private fun render(width: Int, height: Int, block: CanvasDrawScope.() -> Unit): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        val scope = CanvasDrawScope()
        scope.draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), Size(width.toFloat(), height.toFloat())) {
            scope.block()
        }
        return bitmap
    }

    private fun save(bitmap: Bitmap, name: String) {
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun checkLoaf() {
        for (dark in listOf(false,true)) for (step in 0..16) {
            val background=if (dark) android.graphics.Color.rgb(16,16,17) else android.graphics.Color.WHITE
            // Render into a larger canvas so any tail crossing the existing
            // 200×200 mascot slot remains visible to this bounds check.
            val bitmap=render(720,720) {
                drawRect(androidx.compose.ui.graphics.Color(background))
                drawContext.transform.translate(60f,60f)
                drawContext.transform.scale(3f,3f,Offset.Zero)
                drawContext.transform.translate(-32f,-55f)
                drawImportedLoaf(dark,step*.25f)
            }
            for (y in 0 until 720) for (x in 0 until 720) {
                if (x<60 || y<60 || x>=660 || y>=660) {
                    check(bitmap.getPixel(x,y)==background) { "Loaf tail or ear clips: dark=$dark phase=$step at $x,$y" }
                }
            }
            if (step==0 || step==8) save(bitmap,"loaf-${if(dark) "dark" else "light"}-$step")
            bitmap.recycle()
        }
        File(targetContext.filesDir,"settings.json").writeText("""{"theme":1,"resumeLast":false}""")
        com.kongye.reader.data.Settings.load()
        val activity=startActivitySync(Intent(targetContext,MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        SystemClock.sleep(3300)
        for ((name,page) in listOf("shelf" to com.kongye.reader.ui.Screen.Shelf,
            "stats" to com.kongye.reader.ui.Screen.Stats, "timeline" to com.kongye.reader.ui.Screen.Timeline)) {
            runOnMainSync { com.kongye.reader.ui.Nav.push(page) }
            SystemClock.sleep(420)
            val screenshot=captureWindow(activity)
            save(screenshot,"loaf-$name"); screenshot.recycle()
            runOnMainSync { com.kongye.reader.ui.Nav.pop() }
            SystemClock.sleep(350)
        }
        runOnMainSync { activity.finish() }
        File(output,"loaf-result.txt").writeText("Both themes: 34 breathing frames fit mascot bounds\nEmpty shelf, stats, timeline: captured\n")
    }

    private fun checkPaws() {
        // Check every mood through the complete breathing cycle, including its extrema.
        for (mood in 0..2) for (step in 0..16) {
            val time = step * 3.8f / 16f
            val bitmap = render(720, 780) {
                drawContext.transform.scale(3f, 3f, androidx.compose.ui.geometry.Offset.Zero)
                when (mood) {
                    0 -> drawImportedFocus(false, time)
                    1 -> drawImportedHappy(false, time)
                    else -> drawImportedSleep(false, time)
                }
            }
            val breath = ((1.0 - cos(2.0 * PI * time / 3.8)) / 2.0).toFloat()
            val bottom = 231f - 1.818f * breath
            for (center in intArrayOf(106 * 3, 134 * 3)) {
                for (x in center - 7..center + 7) for (y in ceil(bottom * 3).toInt()..711) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = android.graphics.Color.red(pixel)
                    val g = android.graphics.Color.green(pixel)
                    val b = android.graphics.Color.blue(pixel)
                    check(!(abs(r - 71) < 8 && abs(g - 71) < 8 && abs(b - 78) < 8)) {
                        "Paw stroke protrudes: mood=$mood time=$time pixel=($x,$y)"
                    }
                }
            }
            if (step == 0 || step == 8) save(bitmap, "paw-$mood-$step")
            bitmap.recycle()
        }
    }

    private fun checkGrayEars() {
        // Includes the short-average case where preserving the face requires shrinking.
        for (earY in floatArrayOf(180f, 340f, 395f)) {
            val bitmap = render(400, 430) {
                drawReadingCatBar(150f, 300f, 400f, 90f, true, false, earY)
            }
            var highest = bitmap.height
            for (y in 0 until 400) for (x in 140..250) {
                val pixel = bitmap.getPixel(x, y)
                if (android.graphics.Color.red(pixel) < 240 && android.graphics.Color.blue(pixel) < 245) highest = minOf(highest, y)
            }
            check(abs(highest - earY) <= 1f) { "Gray ear misses average line: expected=$earY actual=$highest" }
            save(bitmap, "ears-${earY.toInt()}")
            bitmap.recycle()
        }
    }

    private fun checkSplashHandoff() {
        // This is a separate debug install. Window PixelCopy excludes other apps' overlays.
        File(targetContext.filesDir, "settings.json").writeText("""{"theme":1,"resumeLast":false}""")
        com.kongye.reader.data.Settings.load()
        CatRest.wake(0L)
        val activity = startActivitySync(Intent(targetContext, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) })
        waitForIdleSync()
        var overlapFrames = 0
        var finalHeader = 0
        val frames = ArrayList<Bitmap>()
        val start = SystemClock.uptimeMillis()
        for (frame in 0..46) {
            val wait = start + frame * 85L - SystemClock.uptimeMillis()
            if (wait > 0) SystemClock.sleep(wait)
            val full = captureWindow(activity)
            val bitmap = Bitmap.createScaledBitmap(full, 310, full.height * 310 / full.width, true)
            full.recycle()
            frames += bitmap
            fun ink(left: Float, top: Float, right: Float, bottom: Float): Int {
                var count = 0
                for (y in (bitmap.height*top).toInt() until (bitmap.height*bottom).toInt() step 2)
                    for (x in (bitmap.width*left).toInt() until (bitmap.width*right).toInt() step 2) {
                        val color = bitmap.getPixel(x,y)
                        if (android.graphics.Color.red(color) < 220 && android.graphics.Color.green(color) < 220) count++
                    }
                return count
            }
            val header = ink(0.04f, 0.06f, 0.35f, 0.12f)
            val brand = ink(0.32f, 0.825f, 0.68f, 0.87f)
            if (header > 50 && brand > 30) overlapFrames++
            finalHeader = header
        }
        // PNG compression must not slow down sampling the 480 ms handoff.
        frames.forEachIndexed { i, bitmap ->
            save(bitmap, "handoff-${i.toString().padStart(2,'0')}")
            bitmap.recycle()
        }
        check(overlapFrames >= 1) { "No gradual splash-to-app overlap: $overlapFrames frames" }
        check(finalHeader > 50) { "Home page was not visible after splash" }
        File(output, "result.txt").writeText("Paw checks: 51 frames\nGray ears: 3 heights\nSplash overlap: $overlapFrames frames\nFinal header ink: $finalHeader\n")
        checkGazeGesture(activity)
        checkRestUi(activity)
        runOnMainSync { activity.finish() }
    }

    private fun captureWindow(activity: Activity): Bitmap {
        val bitmap = Bitmap.createBitmap(activity.window.decorView.width, activity.window.decorView.height, Bitmap.Config.ARGB_8888)
        val latch = CountDownLatch(1)
        var result = -1
        runOnMainSync {
            PixelCopy.request(activity.window, bitmap, { result = it; latch.countDown() }, Handler(Looper.getMainLooper()))
        }
        check(latch.await(3, TimeUnit.SECONDS) && result == PixelCopy.SUCCESS) { "Window capture failed: $result" }
        return bitmap
    }

    private fun checkSplashSkip() {
        File(targetContext.filesDir, "settings.json").writeText("""{"theme":1,"resumeLast":false}""")
        com.kongye.reader.data.Settings.load()
        val activity = startActivitySync(Intent(targetContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        waitForIdleSync()
        val before = captureWindow(activity)
        fun headerInk(bitmap: Bitmap): Int {
            val x = (bitmap.width*.04f).toInt(); val y = (bitmap.height*.06f).toInt()
            val w = (bitmap.width*.31f).toInt(); val h = (bitmap.height*.06f).toInt()
            val pixels = IntArray(w*h)
            bitmap.getPixels(pixels, 0, w, x, y, w, h)
            return pixels.count { android.graphics.Color.red(it)<200 && android.graphics.Color.green(it)<200 }
        }
        check(headerInk(before)<50) { "Splash had already ended before skip test" }
        var picks = 0
        runOnMainSync { com.kongye.reader.ui.Nav.pickFiles = { picks++ } }
        val downTime = SystemClock.uptimeMillis()
        // Tap over the underlying import button to detect click-through as well.
        for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, before.width*.5f, before.height*.745f, 0)
            sendPointerSync(event); event.recycle()
            SystemClock.sleep(30)
        }
        SystemClock.sleep(120)
        val after = captureWindow(activity)
        val elapsed = SystemClock.uptimeMillis()-downTime
        check(headerInk(after)>500) { "Home content was not immediately revealed after tap" }
        check(elapsed<1000) { "Skipping waited for the animation: ${elapsed}ms" }
        runOnMainSync { check(picks==0) { "Skip tap activated the import button" } }
        save(before, "skip-before"); save(after, "skip-after")
        before.recycle(); after.recycle()
        File(output,"skip-result.txt").writeText("Tap-to-home: ${elapsed}ms\nUnderlying button activations: $picks\n")
        runOnMainSync { activity.finish() }
    }

    private fun checkHomeControls() {
        File(targetContext.filesDir,"settings.json").writeText("""{"theme":1,"resumeLast":false}""")
        com.kongye.reader.data.Settings.load()
        CatRest.wake(Stats.sessions.value.sumOf { it.millis })
        val activity = startActivitySync(Intent(targetContext,MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        SystemClock.sleep(3200)
        waitForIdleSync()
        val initial = captureWindow(activity)
        val width=initial.width.toFloat(); val height=initial.height.toFloat()
        val neutral=pupilCenter(initial)
        save(initial,"controls-neutral"); initial.recycle()
        fun touch(action:Int, point:Offset, down:Long) {
            val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,point.x,point.y,0)
            // Route only through this test Activity; never inject into another app
            // if a system overlay or the user changes the foreground window.
            runOnMainSync { activity.dispatchTouchEvent(event) }
            event.recycle()
        }
        val controls=listOf("today" to Offset(width*.5f,height*.533f),
            "read" to Offset(width*.5f,height*.745f), "garden" to Offset(width*.15f,height*.94f),
            "shelf" to Offset(width*.818f,height*.088f), "prefs" to Offset(width*.924f,height*.088f))
        for ((name,point) in controls) {
            val down=SystemClock.uptimeMillis()
            touch(MotionEvent.ACTION_DOWN,point,down)
            SystemClock.sleep(65)
            var image=captureWindow(activity)
            var center=pupilCenter(image)
            check(abs(center.x-neutral.x)<2 && abs(center.y-neutral.y)<12) { "Button $name moved the pupils: $center vs $neutral" }
            save(image,"control-$name"); image.recycle()
            // This gesture must remain ignored even after dragging away from the button.
            touch(MotionEvent.ACTION_MOVE,Offset(width*.15f,neutral.y),down)
            SystemClock.sleep(45)
            image=captureWindow(activity); center=pupilCenter(image); image.recycle()
            check(abs(center.x-neutral.x)<2) { "Dragging off $name re-enabled gaze" }
            touch(MotionEvent.ACTION_CANCEL,point,down)
            SystemClock.sleep(40)
        }
        val blank=Offset(width*.15f,neutral.y)
        val down=SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN,blank,down)
        SystemClock.sleep(60)
        val tracking=captureWindow(activity)
        check(pupilCenter(tracking).x<neutral.x-3) { "Blank-area tracking stopped working" }
        save(tracking,"controls-blank-tracking"); tracking.recycle()
        touch(MotionEvent.ACTION_CANCEL,blank,down)
        for ((name,point) in controls.filter { it.first!="read" }) {
            val time=SystemClock.uptimeMillis()
            touch(MotionEvent.ACTION_DOWN,point,time)
            touch(MotionEvent.ACTION_UP,point,time)
            SystemClock.sleep(380)
            runOnMainSync { check(com.kongye.reader.ui.Nav.top!=com.kongye.reader.ui.Screen.Home) { "$name no longer navigates" } }
            if (name=="prefs") {
                val scrollTime=SystemClock.uptimeMillis()
                touch(MotionEvent.ACTION_DOWN,Offset(width*.5f,height*.82f),scrollTime)
                for (step in 1..16) {
                    touch(MotionEvent.ACTION_MOVE,Offset(width*.5f,height*(.82f-.038f*step)),scrollTime)
                    SystemClock.sleep(16)
                }
                touch(MotionEvent.ACTION_UP,Offset(width*.5f,height*.212f),scrollTime)
                SystemClock.sleep(450)
                val settings=captureWindow(activity); save(settings,"settings-board-cat"); settings.recycle()
            }
            runOnMainSync { com.kongye.reader.ui.Nav.pop() }
            SystemClock.sleep(380)
        }
        var imports=0
        runOnMainSync { com.kongye.reader.ui.Nav.pickFiles={ imports++ } }
        val point=controls.first { it.first=="read" }.second
        val time=SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN,point,time); touch(MotionEvent.ACTION_UP,point,time)
        SystemClock.sleep(100)
        runOnMainSync { check(imports==1) { "Import button no longer works" }; activity.finish() }
        File(output,"controls-result.txt").writeText("Five excluded buttons + drag out: passed\nBlank tracking: passed\nButton navigation/import: passed\n")
    }

    private fun checkGazeLimits() {
        val center = Offset(250f, 300f)
        check(catGazeAt(null, center, 100f) == Offset.Zero)
        check(catGazeAt(center, center, 100f) == Offset.Zero)
        for (point in listOf(Offset.Zero, Offset(10000f, 10000f), Offset(-1000f, 300f))) {
            check(catGazeAt(point, center, 100f).getDistance() <= 1.00001f) { "Pupil can leave the eye white" }
        }
    }

    private fun checkGazeDistance() {
        val center = Offset(250f, 300f)
        var previousTravel = 0f
        var previousX = 0f
        val result = StringBuilder()
        for (distance in listOf(25f, 100f, 250f, 500f, 5000f)) {
            val gaze = catGazeAt(center + Offset(distance, 0f), center, 100f)
            check(gaze.x > previousTravel && gaze.x < 1f && gaze.y == 0f)
            check(catGazeAt(center - Offset(distance, 0f), center, 100f).x == -gaze.x)
            val bitmap = render(720,780) {
                drawContext.transform.scale(3f, 3f, Offset.Zero)
                drawImportedFocus(false, 0f, gaze=gaze)
            }
            var count = 0; var sum = 0L
            for (y in 350..405) for (x in 265..450) {
                if (bitmap.getPixel(x,y) == android.graphics.Color.rgb(24,24,27)) { count++; sum+=x }
            }
            check(count>50)
            val pupilX = sum.toFloat()/count
            check(pupilX > previousX) { "Rendered pupils did not move farther at distance=$distance" }
            result.append("distance=$distance normalizedTravel=${gaze.x} pupilX=$pupilX\n")
            save(bitmap,"gaze-distance-${distance.toInt()}"); bitmap.recycle()
            previousTravel=gaze.x; previousX=pupilX
        }
        check(catGazeAt(null,center,100f)==Offset.Zero)
        File(output,"gaze-distance-result.txt").writeText(result.toString())
    }

    private fun checkRestPolicy() {
        val day = LocalDateTime.of(2026, 11, 5, 12, 0)
        val fresh = CatWakeMemory()
        fun asleep(minutes: Long, now: LocalDateTime = day, wake: CatWakeMemory = fresh, total: Long = minutes): Boolean =
            CatRestPolicy.sleeping(minutes*60_000, total*60_000, now, wake)
        check(!asleep(59) && asleep(60)) { "First rest must start at 60 reading minutes" }
        val wake = CatWakeMemory(CatRestPolicy.context(day), 60*60_000)
        check(!asleep(60, wake=wake) && !asleep(89, wake=wake) && asleep(90, wake=wake))
        val nextWake = CatWakeMemory(CatRestPolicy.context(day), 90*60_000)
        check(!asleep(119, wake=nextWake) && asleep(120, wake=nextWake))
        check(!asleep(0, day.withHour(21).withMinute(59)) && asleep(0, day.withHour(22)))
        val night = day.withHour(23)
        val nightWake = CatWakeMemory(CatRestPolicy.context(night), 120*60_000)
        val afterMidnight = day.plusDays(1).withHour(0)
        check(!asleep(10, afterMidnight, nightWake, 149) && asleep(10, afterMidnight, nightWake, 150))
        check(!asleep(0, day.plusDays(1).withHour(7)))
        check(asleep(0, night.plusDays(1), nightWake, 120)) { "A new night must sleep by default" }
        val restored = com.kongye.reader.data.Store.json.decodeFromString(CatWakeMemory.serializer(),
            com.kongye.reader.data.Store.json.encodeToString(CatWakeMemory.serializer(), nightWake))
        check(restored == nightWake && !asleep(0, afterMidnight, restored, 130))
    }

    private fun checkRestUi(activity: Activity) {
        val readingFile = File(targetContext.filesDir, "sessions.json")
        val start = System.currentTimeMillis()
        fun reading(minutes: Long) {
            readingFile.writeText("""[{"bookId":"cat-rest-fixture","title":"小憩测试","start":$start,"end":${start+minutes*60_000},"millis":${minutes*60_000}}]""")
            runOnMainSync { Stats.load() }
            SystemClock.sleep(220)
        }
        fun pupilPixels(bitmap: Bitmap): Int {
            var count = 0
            for (y in (bitmap.height*.3f).toInt()..(bitmap.height*.43f).toInt())
                for (x in (bitmap.width*.38f).toInt()..(bitmap.width*.62f).toInt())
                    if (bitmap.getPixel(x,y) == android.graphics.Color.rgb(24,24,27)) count++
            return count
        }
        fun expectSleeping(sleep: Boolean, name: String) {
            val bitmap = captureWindow(activity)
            save(bitmap, name)
            val count = pupilPixels(bitmap)
            check(if (sleep) count == 0 else count > 50) { "Wrong cat mood: $name pupils=$count" }
            bitmap.recycle()
        }
        runOnMainSync { CatRest.reset() }
        reading(60)
        expectSleeping(true, "rest-60min")
        val width = activity.window.decorView.width.toFloat()
        val height = activity.window.decorView.height.toFloat()
        val down = SystemClock.uptimeMillis()
        for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, width*.5f, height*.39f, 0)
            sendPointerSync(event); event.recycle()
            SystemClock.sleep(70)
        }
        SystemClock.sleep(260)
        expectSleeping(false, "rest-woken")
        val bubble = captureWindow(activity)
        var ink = 0
        for (y in (height*.18f).toInt()..(height*.285f).toInt()) for (x in (width*.25f).toInt()..(width*.75f).toInt()) {
            val color = bubble.getPixel(x,y)
            if (android.graphics.Color.red(color)<150 && android.graphics.Color.green(color)<150) ink++
        }
        check(ink > 100) { "Speech bubble was not placed above cat: ink=$ink" }
        save(bubble, "rest-head-bubble"); bubble.recycle()
        SystemClock.sleep(100)
        val remembered = CatRest.state.value
        runOnMainSync { CatRest.load() }
        check(CatRest.state.value == remembered) { "Wake-up was not persisted" }
        reading(89)
        expectSleeping(false, "rest-89min")
        reading(90)
        expectSleeping(true, "rest-90min")
        runOnMainSync { CatRest.wake(90*60_000L) }
        reading(119)
        expectSleeping(false, "rest-119min")
        reading(120)
        expectSleeping(true, "rest-120min")
        File(output, "result.txt").appendText("Rest: 60/wake/89/90/wake/119/120 minutes, persistence, head bubble passed\n")
    }

    private fun pupilCenter(bitmap: Bitmap): Offset {
        var count = 0; var xsum = 0L; var ysum = 0L
        for (y in (bitmap.height*0.3f).toInt()..(bitmap.height*0.43f).toInt()) {
            for (x in (bitmap.width*0.38f).toInt()..(bitmap.width*0.62f).toInt()) {
                if (bitmap.getPixel(x,y) == android.graphics.Color.rgb(24,24,27)) {
                    count++; xsum += x; ysum += y
                }
            }
        }
        check(count > 50) { "Pupils not found in homepage capture" }
        return Offset(xsum.toFloat()/count, ysum.toFloat()/count)
    }

    private fun checkGazeGesture(activity: Activity) {
        var bitmap = captureWindow(activity)
        val neutral = pupilCenter(bitmap)
        val width = bitmap.width.toFloat()
        val radius = bitmap.height*0.13f
        bitmap.recycle()
        val downTime = SystemClock.uptimeMillis()
        fun touch(action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            sendPointerSync(event); event.recycle()
        }
        fun sample(name: String): Offset {
            SystemClock.sleep(180)
            bitmap = captureWindow(activity)
            save(bitmap, "gaze-$name")
            return pupilCenter(bitmap).also { bitmap.recycle() }
        }
        touch(MotionEvent.ACTION_DOWN, width*0.15f, neutral.y)
        try {
            val left = sample("left")
            touch(MotionEvent.ACTION_MOVE, width*0.85f, neutral.y)
            val right = sample("right")
            touch(MotionEvent.ACTION_MOVE, width/2, neutral.y-radius)
            val up = sample("up")
            touch(MotionEvent.ACTION_MOVE, width/2, neutral.y+radius)
            val down = sample("down")
            check(right.x - left.x > 12f) { "Pupils did not follow horizontal touch: left=$left right=$right" }
            check(down.y - up.y > 12f) { "Pupils did not follow vertical touch: up=$up down=$down" }
        } finally {
            touch(MotionEvent.ACTION_UP, width/2, neutral.y+radius)
        }
        val reset = sample("released")
        check(abs(reset.x-neutral.x) < 2f) { "Pupils did not return to center: neutral=$neutral reset=$reset" }
        File(output, "result.txt").appendText("Gaze: real pointer left/right/up/down and release passed\n")
    }
}
