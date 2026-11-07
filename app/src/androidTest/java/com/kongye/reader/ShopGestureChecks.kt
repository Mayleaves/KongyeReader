package com.kongye.reader

import android.app.Instrumentation
import android.content.Intent
import android.content.pm.ActivityInfo
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import com.kongye.reader.data.Profile
import com.kongye.reader.data.Profiles
import com.kongye.reader.data.Settings
import com.kongye.reader.data.Shop
import com.kongye.reader.ui.Nav
import com.kongye.reader.ui.Screen
import com.kongye.reader.ui.components.Creature
import com.kongye.reader.ui.components.DashboardCat
import com.kongye.reader.ui.theme.LightPalette
import com.kongye.reader.ui.theme.LocalPalette
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Real pointer gestures, portrait/landscape geometry, and rendered layer invariants. */
internal class ShopGestureChecks(private val test: Instrumentation, private val layersOnly: Boolean = false) {
    private lateinit var activity: MainActivity
    private val output = File(test.targetContext.filesDir, "shop-gesture-checks").apply { mkdirs() }
    private val log = StringBuilder()
    private val origin = IntArray(2)

    fun run() {
        try {
            test.runOnMainSync {
                Settings.update { it.copy(theme=1, resumeLast=false) }
                Profiles.update { Profile(egg=true) }
                Nav.home()
            }
            activity = test.startActivitySync(Intent(test.targetContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }) as MainActivity
            test.runOnMainSync {
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            SystemClock.sleep(3000)
            orient(false)
            if(!layersOnly) {
                navigation()
                responsiveCats()
            }
            layers()
        } finally {
            File(output, if(layersOnly) "layers-result.txt" else "result.txt").writeText(log.toString())
            if (::activity.isInitialized) test.runOnMainSync {
                activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                activity.finish()
            }
        }
    }

    private fun nodes(): List<SemanticsNode> {
        val result=ArrayList<SemanticsNode>()
        test.runOnMainSync {
            fun add(n: SemanticsNode) { result+=n; n.children.forEach(::add) }
            fun visit(v: View) {
                val get=v.javaClass.methods.firstOrNull { it.name=="getSemanticsOwner" && it.parameterCount==0 }
                if(get!=null) { v.getLocationInWindow(origin); add((get.invoke(v) as SemanticsOwner).rootSemanticsNode) }
                else if(v is ViewGroup) for(i in 0 until v.childCount) visit(v.getChildAt(i))
            }
            visit(activity.window.decorView)
        }
        // Pager prefetch can retain off-screen pages in the semantics tree.
        // Only interact with controls that actually occupy the visible viewport.
        return result.filter { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }
    }

    private fun labels(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
        n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()

    private fun selected(label: String) {
        val all=nodes()
        val targets=all.filter { label in labels(it) }
        val selected=all.filter { it.config.getOrNull(SemanticsProperties.Selected)==true }
        val correct=targets.any { target -> selected.any { it.boundsInRoot.contains(target.boundsInRoot.center) } }
        if(!correct) {
            log.append("Expected $label; targets=${targets.map { it.boundsInRoot }}; selected=${selected.map { labels(it) to it.boundsInRoot }}\n")
            screenshot("failed-selection").recycle()
        }
        check(correct) { "Swipe did not select $label" }
        log.append("Selected tab: $label\n")
    }

    private fun touch(action: Int, x: Float, y: Float, down: Long) {
        val ev=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x+origin[0],y+origin[1],0)
        ev.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
        test.runOnMainSync { activity.dispatchTouchEvent(ev) }
        ev.recycle()
    }

    private fun click(label: String) {
        val all=nodes()
        val target=all.last { label in labels(it) }
        val center=target.boundsInRoot.center
        val clickable=all.filter { it.config.getOrNull(SemanticsActions.OnClick)!=null && it.boundsInRoot.contains(center) }
            .minBy { it.boundsInRoot.width*it.boundsInRoot.height }
        val p=clickable.boundsInRoot.center
        val time=SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN,p.x,p.y,time); SystemClock.sleep(40)
        touch(MotionEvent.ACTION_UP,p.x,p.y,time); SystemClock.sleep(800)
    }

    private fun swipe(left: Boolean) {
        nodes()
        val w=activity.window.decorView.width.toFloat()
        val h=activity.window.decorView.height.toFloat()
        val x0=w*if(left) .85f else .15f
        val x1=w*if(left) .15f else .85f
        val time=SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN,x0,h*.55f,time)
        for(i in 1..20) {
            SystemClock.sleep(16)
            touch(MotionEvent.ACTION_MOVE,x0+(x1-x0)*i/20,h*.55f,time)
        }
        touch(MotionEvent.ACTION_UP,x1,h*.55f,time)
        SystemClock.sleep(900)
    }

    private fun back() { test.runOnMainSync { activity.onBackPressedDispatcher.onBackPressed() }; SystemClock.sleep(750) }

    private fun navigation() {
        test.runOnMainSync { Nav.push(Screen.Shop) }; SystemClock.sleep(900)
        val shop=listOf("动物","背景","饰品","挂件","图标")
        selected(shop.first()); swipe(false); selected(shop.first())
        for(tab in shop.drop(1)) { swipe(true); selected(tab) }
        swipe(true); selected(shop.last())
        for(tab in shop.dropLast(1).asReversed()) { swipe(false); selected(tab) }
        click("挂件"); selected("挂件")
        test.runOnMainSync { Nav.dress(1) }; SystemClock.sleep(800)
        selected("猫园"); back(); selected("挂件")
        click("动物")
        val grid=nodes().last { it.config.getOrNull(SemanticsActions.ScrollBy)!=null }
        test.runOnMainSync { grid.config[SemanticsActions.ScrollBy].action!!.invoke(0f,400f) }
        SystemClock.sleep(500)
        fun scroll()=nodes().last { it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)!=null }
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        val before=scroll()
        swipe(true); swipe(false)
        check(abs(scroll()-before)<.05f) { "Category swipe lost vertical scroll: $before -> ${scroll()}" }
        screenshot("shop-scroll-return").recycle()
        log.append("Shop: all five tabs swipe both ways, stop at ends, tap tabs, retain vertical position and return from dress\n")

        test.runOnMainSync { Nav.dress(0) }; SystemClock.sleep(800)
        val dress=listOf("首页","猫园","饰品","图标")
        selected(dress.first()); swipe(false); selected(dress.first())
        for(tab in dress.drop(1)) { swipe(true); selected(tab) }
        swipe(true); selected(dress.last())
        for(tab in dress.dropLast(1).asReversed()) { swipe(false); selected(tab) }
        click("图标"); selected("图标"); click("首页"); selected("首页")
        log.append("Dress: all four tabs swipe both ways, stop at ends, and remain selectable by tap\n")
    }

    private fun orient(landscape: Boolean) {
        test.runOnMainSync { activity.requestedOrientation=if(landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        SystemClock.sleep(1300)
        check((activity.window.decorView.width>activity.window.decorView.height)==landscape)
    }

    private fun catBounds(name: String): Rect = nodes().last { "动物预览：$name" in labels(it) }.boundsInRoot

    private fun responsiveCats() {
        val names=listOf("看板猫","极简猫","鎏金看板猫")
        test.runOnMainSync { Nav.home(); Nav.push(Screen.Shop) }; SystemClock.sleep(800)
        // The remembered shop list may be scrolled. Bring its first row into view.
        val grid=nodes().last { it.config.getOrNull(SemanticsActions.ScrollBy)!=null }
        test.runOnMainSync { grid.config[SemanticsActions.ScrollBy].action!!.invoke(0f,-100000f) }; SystemClock.sleep(500)
        click("动物")
        val shopPortrait=names.map { catBounds(it).width }
        screenshot("shop-portrait").recycle()
        test.runOnMainSync { Nav.dress(0) }; SystemClock.sleep(800)
        val dressPortrait=names.map { catBounds(it).width }
        screenshot("dress-portrait").recycle()
        orient(true)
        val dressLandscape=names.map { catBounds(it).width }
        for(i in names.indices) check(dressLandscape[i]>dressPortrait[i]*1.4f) { "${names[i]} remained tiny in landscape dress: $dressPortrait -> $dressLandscape" }
        screenshot("dress-landscape").recycle()
        back()
        val shopLandscape=names.map { catBounds(it).width }
        for(i in names.indices) check(shopLandscape[i]>shopPortrait[i]*1.4f) { "${names[i]} remained tiny in landscape shop: $shopPortrait -> $shopLandscape" }
        screenshot("shop-landscape").recycle()
        log.append("Native cat widths: shop $shopPortrait -> $shopLandscape; dress $dressPortrait -> $dressLandscape\n")
        orient(false)
    }

    private fun screenshot(name: String): Bitmap {
        val bitmap=Bitmap.createBitmap(activity.window.decorView.width,activity.window.decorView.height,Bitmap.Config.ARGB_8888)
        val done=CountDownLatch(1); var status=-1
        test.runOnMainSync { PixelCopy.request(activity.window,bitmap,{status=it;done.countDown()},Handler(Looper.getMainLooper())) }
        check(done.await(3,TimeUnit.SECONDS) && status==PixelCopy.SUCCESS)
        File(output,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        return bitmap
    }

    private fun stage(id: String, shadow: Boolean, pad: Boolean): Bitmap {
        test.runOnMainSync {
            activity.setContent {
                CompositionLocalProvider(LocalPalette provides LightPalette) {
                    Box(Modifier.fillMaxSize().background(Color(0xFFEFDAFF)),contentAlignment=Alignment.Center) {
                        Creature(id,size=240.dp,outfit=if(pad) mapOf("H" to "acc-H-cloud") else emptyMap(),shadow=shadow)
                    }
                }
            }
        }
        SystemClock.sleep(350)
        val bounds=catBounds(Shop.nameOf(id))
        val full=screenshot("$id-${if(shadow) "shadow" else "clear"}-${if(pad) "pad" else "bare"}")
        val crop=Bitmap.createBitmap(full,bounds.left.toInt(),bounds.top.toInt(),bounds.width.toInt(),bounds.height.toInt())
        full.recycle()
        return crop
    }

    private fun layers() {
        val background=0xFFEFDAFF.toInt()
        for(id in listOf("goldpig","cat",Shop.MASCOT,Shop.MINCAT,Shop.GOLDCAT)) {
            val bare=stage(id,true,false)
            val clear=stage(id,false,false)
            val pad=stage(id,false,true)
            val combined=stage(id,true,true)
            fun pixels(bitmap: Bitmap) = IntArray(bitmap.width*bitmap.height).also {
                bitmap.getPixels(it,0,bitmap.width,0,0,bitmap.width,bitmap.height)
            }
            val before=pixels(bare)
            val withoutShadow=pixels(clear)
            check(pixels(pad).contentEquals(pixels(combined))) { "$id retained its original shadow after equipping a foot pad" }
            val shadowPixels=before.indices.count { before[it]!=withoutShadow[it] }
            check(shadowPixels>20) { "$id did not have a visible default shadow" }
            val restored=stage(id,true,false)
            check(pixels(restored).contentEquals(before)) { "$id failed to restore its shadow after removing the foot pad" }
            var aboveShadow=0; var belowFeet=0
            val interior=listOf(-3 to -3,0 to -3,3 to -3,-3 to 0,3 to 0,-3 to 3,0 to 3,3 to 3)
            for(y in (bare.height*.55f).toInt() until bare.height-3) for(x in 3 until bare.width-3) {
                val indexColor=bare.getPixel(x,y)
                if(clear.getPixel(x,y)==background && indexColor!=background && pad.getPixel(x,y)==android.graphics.Color.WHITE) {
                    check(combined.getPixel(x,y)==android.graphics.Color.WHITE) { "$id shadow covered the white foot pad at $x,$y" }
                    aboveShadow++
                }
                val body=clear.getPixel(x,y)
                if(body!=background && body!=android.graphics.Color.WHITE &&
                    interior.all { (dx,dy)->clear.getPixel(x+dx,y+dy)!=background }) {
                    check(combined.getPixel(x,y)==body) { "$id pad covered the animal's feet at $x,$y" }
                    belowFeet++
                }
            }
            check(aboveShadow>20 && belowFeet>100) { "Insufficient visible layer overlap for $id: $aboveShadow/$belowFeet" }
            log.append("$id: $shadowPixels default shadow pixels removed by foot pad and restored on removal; $belowFeet body/foot pixels above pad\n")
            listOf(bare,clear,pad,combined,restored).forEach { it.recycle() }
        }
        for(id in listOf(Shop.MASCOT,Shop.MINCAT,Shop.GOLDCAT)) for(sleep in listOf(false,true)) {
            test.runOnMainSync {
                Profiles.update { it.copy(outfits=mapOf(id to mapOf("H" to "acc-H-cloud"))) }
                activity.setContent {
                    CompositionLocalProvider(LocalPalette provides LightPalette) {
                        Box(Modifier.fillMaxSize().background(Color(0xFFEFDAFF)),contentAlignment=Alignment.Center) {
                            DashboardCat(size=240.dp,minimal=id==Shop.MINCAT,gold=id==Shop.GOLDCAT,sleeping=sleep,petSignal=if(sleep) 0 else 1)
                        }
                    }
                }
            }
            SystemClock.sleep(700)
            screenshot("dashboard-$id-${if(sleep) "sleep" else "happy"}").recycle()
        }
    }
}
