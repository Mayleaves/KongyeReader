package com.kongye.reader

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kongye.reader.data.Shop
import com.kongye.reader.ui.components.Creature
import com.kongye.reader.ui.theme.LightPalette
import com.kongye.reader.ui.theme.LocalPalette
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Pixel-check the public Creature defaults: thumbnails stay still while explicit previews animate. */
internal class AnimationScopeChecks(private val test: Instrumentation) {
    fun run() {
        val output=File(test.targetContext.filesDir,"animation-scope").apply { mkdirs() }
        val ids=listOf(Shop.MASCOT)+Shop.catalog.animals.map { it.id }
        val previews=listOf("dog","fennec","flypig")
        val bounds=mutableMapOf<String,Rect>()
        val activity=test.startActivitySync(Intent(test.targetContext,MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }) as MainActivity
        try {
            test.runOnMainSync {
                activity.setContent {
                    CompositionLocalProvider(LocalPalette provides LightPalette) {
                        Column(Modifier.fillMaxSize().background(Color.White).padding(top=36.dp,start=8.dp,end=8.dp)) {
                            BasicText("Static animal choices",style=TextStyle(color=Color.Black,fontSize=14.sp))
                            for (row in ids.chunked(6)) Row(Modifier.fillMaxWidth()) {
                                for (id in row) Column(Modifier.weight(1f)) {
                                    Creature(id,Modifier.fillMaxWidth().height(52.dp).onGloballyPositioned { bounds["small-$id"]=it.boundsInWindow() },
                                        outfit=mapOf("G" to "acc-G-tailbow"),leaf=id in Shop.kongyeCats,faceLeft=true)
                                    BasicText(id,style=TextStyle(color=Color.Black,fontSize=8.sp))
                                }
                                repeat(6-row.size) { Spacer(Modifier.weight(1f)) }
                            }
                            Spacer(Modifier.height(24.dp))
                            BasicText("Animated large previews",style=TextStyle(color=Color.Black,fontSize=14.sp))
                            Row(Modifier.fillMaxWidth()) {
                                for (id in previews) Creature(id,Modifier.weight(1f).height(180.dp).onGloballyPositioned { bounds["large-$id"]=it.boundsInWindow() },
                                    outfit=mapOf("B" to "acc-B-bow","G" to "acc-G-tailbow"),faceLeft=true,animate=true)
                            }
                        }
                    }
                }
            }
            fun capture(): Bitmap {
                val ready=CountDownLatch(1)
                lateinit var bitmap: Bitmap
                var result=-1
                test.runOnMainSync {
                    val view=activity.window.decorView
                    bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
                    PixelCopy.request(activity.window,bitmap,{ result=it;ready.countDown() },Handler(Looper.getMainLooper()))
                }
                check(ready.await(8,TimeUnit.SECONDS) && result==PixelCopy.SUCCESS)
                return bitmap
            }
            fun crop(bitmap: Bitmap,rect: Rect): IntArray {
                val left=rect.left.roundToInt();val top=rect.top.roundToInt()
                val w=rect.right.roundToInt()-left;val h=rect.bottom.roundToInt()-top
                return IntArray(w*h).also { bitmap.getPixels(it,0,w,left,top,w,h) }
            }
            var baseline: Map<String,IntArray>?=null
            val moved=mutableSetOf<String>()
            for ((step,delay) in listOf(1000L,1700L,2400L).withIndex()) {
                SystemClock.sleep(delay)
                val shot=capture()
                check(bounds.size==ids.size+previews.size)
                val current=bounds.mapValues { crop(shot,it.value) }
                baseline?.let { original ->
                    for (id in ids) check(current.getValue("small-$id").contentEquals(original.getValue("small-$id"))) { "Thumbnail moved: $id" }
                    for (id in previews) if (!current.getValue("large-$id").contentEquals(original.getValue("large-$id"))) moved+=id
                }
                if(baseline==null) baseline=current
                File(output,"scope-$step.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG,100,it) }
                shot.recycle()
            }
            check(moved.containsAll(previews)) { "Large preview did not animate: ${previews-moved}" }
            File(output,"result.txt").writeText("PASS ${ids.size} stationary thumbnails and ${previews.size} animated large previews across 3 frames")
        } finally { test.runOnMainSync { activity.finish() } }
    }
}
