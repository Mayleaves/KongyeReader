package com.kongye.reader

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.kongye.reader.data.Shop
import com.kongye.reader.data.ShopAnimal
import androidx.compose.ui.geometry.Offset
import com.kongye.reader.ui.components.*
import com.kongye.reader.ui.theme.LightPalette
import com.kongye.reader.ui.theme.LocalPalette
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max

/** Device rendering, isolated in com.kongye.reader.debug: no real profile changes. */
private data class FittedAnimal(val animal: ShopAnimal, val body: Painter?, val layers: List<AnimalLayer>, val pieces: List<Piece>)

internal class AnimalMotionChecks(private val test: Instrumentation) {
    fun run() {
        val animals = Shop.catalog.animals.filter { it.id !in Shop.kongyeCats }
        val output = File(test.targetContext.filesDir, "animal-motion-checks").apply { mkdirs() }
        val done = CountDownLatch(1)
        var failure: Throwable? = null
        val log = StringBuilder()
        val activity = test.startActivitySync(Intent(test.targetContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }) as MainActivity
        test.runOnMainSync {
            activity.setContent {
                CompositionLocalProvider(LocalPalette provides LightPalette) {
                    val loaded = animals.map { animal ->
                        val bodyRes = Shop.drawable("${animal.res}_body").takeIf { it != 0 } ?: Shop.drawable(animal.res)
                        Triple(animal, painterResource(bodyRes), animalLayers(animal.id))
                    }
                    val fittings = (listOfNotNull(Shop.wearer(Shop.MASCOT)) + Shop.catalog.animals).map { animal ->
                        val native = animal.id in Shop.kongyeCats
                        val res = Shop.drawable("${animal.res}_body").takeIf { it != 0 } ?: Shop.drawable(animal.res)
                        FittedAnimal(animal, if (native) null else painterResource(res),
                            if (native) emptyList() else animalLayers(animal.id),
                            Shop.catalog.accessories.map { acc -> outfitPieces(animal, mapOf(acc.slot to acc.id)).single() })
                    }
                    LaunchedEffect(Unit) {
                        try {
                            for ((animal, original, layers) in loaded) {
                                check(layers.isNotEmpty()) { "${animal.id}: no layers" }
                                val k = 300f / max(animal.vb[2], animal.vb[3])
                                val ox = (340f - animal.vb[2] * k) / 2
                                val oy = (340f - animal.vb[3] * k) / 2
                                fun render(source: Boolean, seconds: Float, only: String? = null): Bitmap {
                                    val b = Bitmap.createBitmap(340,340,Bitmap.Config.ARGB_8888)
                                    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(b.asImageBitmap()), Size(340f,340f)) {
                                        drawRect(Color(0xFFFAF1E6))
                                        if (source) withTransform({ translate(ox,oy) }) {
                                            with(original) { draw(Size(animal.vb[2]*k,animal.vb[3]*k)) }
                                        } else drawAnimalLayers(animal, if (only == null) layers else layers.map { AnimalLayer(it.painter, it.part?.takeIf { p -> p.name == only }) }, seconds,k,ox,oy)
                                    }
                                    return b
                                }
                                val source=render(true,0f)
                                val rest=render(false,0f)
                                val a=IntArray(340*340);val b=IntArray(a.size)
                                source.getPixels(a,0,340,0,0,340,340);rest.getPixels(b,0,340,0,0,340,340)
                                var severe=0
                                for(i in a.indices) {
                                    val delta=max(abs((a[i] shr 16 and 255)-(b[i] shr 16 and 255)),max(abs((a[i] shr 8 and 255)-(b[i] shr 8 and 255)),abs((a[i] and 255)-(b[i] and 255))))
                                    if(delta>12) severe++
                                }
                                File(output,"${animal.id}-source.png").outputStream().use { source.compress(Bitmap.CompressFormat.PNG,100,it) }
                                File(output,"${animal.id}-rest.png").outputStream().use { rest.compress(Bitmap.CompressFormat.PNG,100,it) }
                                // Splitting intersecting clipped pony groups changes antialiasing on the clip edge.
                                // The recipe verifier separately proves every source path is retained unchanged.
                                check(severe < 512) { "${animal.id}: rest drawing differs at $severe pixels" }
                                rest.getPixels(a,0,340,0,0,340,340)
                                val parts=layers.mapNotNull { it.part }.distinctBy { it.name }
                                for(part in parts) {
                                    check(part.rotation(0f)==0f)
                                    val sample=part.period * when(part.kind) { "blink"->0.97f; "twitch","flutter"->0.78125f; else->0.25f }
                                    val moved=render(false,sample,part.name)
                                    moved.getPixels(b,0,340,0,0,340,340)
                                    check(a.indices.count { a[it]!=b[it] }>3) { "${animal.id}/${part.name}: no visible motion" }
                                    if (animal.id in setOf("goldpig","dog","fennec","crow","flypig","pinkie","luna")) {
                                        File(output,"${animal.id}-${part.name}.png").outputStream().use { moved.compress(Bitmap.CompressFormat.PNG,100,it) }
                                    }
                                    moved.recycle()
                                }
                                File(output,"${animal.id}-rest.png").outputStream().use { rest.compress(Bitmap.CompressFormat.PNG,100,it) }
                                log.append("${animal.id}: rest delta=$severe; ${parts.joinToString { it.name }} PASS\n")
                                rest.recycle();source.recycle()
                            }
                            check(animals.size>=25)
                            log.append("PASS ${animals.size} animals\n")
                            checkFittings(fittings, output, log)
                        } catch(t:Throwable) { failure=t; log.append("FAIL $t\n") }
                        finally { File(output,"result.txt").writeText(log.toString());done.countDown() }
                    }
                }
            }
        }
        check(done.await(120,TimeUnit.SECONDS)) { "animal checks timed out" }
        failure?.let { test.runOnMainSync { activity.finish() }; throw it }
        checkLiveGallery(activity, output)
        test.runOnMainSync { activity.finish() }
    }
    private fun checkFittings(all: List<FittedAnimal>, output: File, log: StringBuilder) {
        var combinations = 0
        var attachments = 0
        val paper = Color(0xFFF4F4F4)
        fun pixels(b: Bitmap) = IntArray(b.width*b.height).also { b.getPixels(it,0,b.width,0,0,b.width,b.height) }
        for (fitted in all) {
            val animal = fitted.animal
            val k = 170f / max(animal.vb[2], animal.vb[3])
            val ox = (220f-animal.vb[2]*k)/2
            val oy = (210f-animal.vb[3]*k)/2 + 5
            val parts = fitted.layers.mapNotNull { it.part }.distinctBy { it.name }
            fun render(piece: Piece, seconds: Float = 0f, body: Boolean = true, only: AnimalPart? = null): Bitmap {
                val bitmap = Bitmap.createBitmap(220,220,Bitmap.Config.ARGB_8888)
                CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(bitmap.asImageBitmap()),Size(220f,220f)) {
                    if (body) drawRect(paper)
                    drawOutfit(animal,listOf(piece),k,ox,oy,behind=true)
                    if (body) {
                        if (fitted.body != null) drawAnimalLayers(animal,fitted.layers,seconds,k,ox,oy)
                        else withTransform({ translate(ox,oy);scale(k,k,pivot=Offset.Zero) }) {
                            when(animal.id) {
                                Shop.MASCOT -> drawImportedFocus(false,0f,shadow=false,plainLeaf=true)
                                Shop.MINCAT -> drawMinimalFocus(false,0f,shadow=false,plainLeaf=true)
                                Shop.GOLDCAT -> drawGoldFocus(false,0f,leaf=true,shadow=false)
                                else -> drawGoldMinFocus(false,0f,leaf=true,shadow=false)
                            }
                        }
                    }
                    drawOutfit(animal,listOf(piece),k,ox,oy,behind=false,parts=if(only == null) parts else listOf(only),seconds=seconds)
                }
                return bitmap
            }
            val sheet=Bitmap.createBitmap(1320,8*250+36,Bitmap.Config.ARGB_8888)
            val canvas=android.graphics.Canvas(sheet)
            canvas.drawColor(android.graphics.Color.WHITE)
            val label=android.graphics.Paint().apply { color=android.graphics.Color.DKGRAY;textSize=13f;isAntiAlias=true }
            canvas.drawText(animal.id,8f,23f,label)
            for ((i,piece) in fitted.pieces.withIndex()) {
                val rest=render(piece,body=false)
                val base=pixels(rest)
                check(base.count { (it ushr 24)>0 }>3) { "${animal.id}/${piece.acc.id}: invisible accessory" }
                val part=parts.firstOrNull { piece.slot in it.attachments && (piece.slot!="F" || piece.acc.id in setOf("acc-F-wings","acc-F-batwings")) }
                val sample=part?.let { it.period * if (it.kind in setOf("twitch","flutter")) .78125f else .25f } ?: 1.37f
                val moved=render(piece,sample,body=false,only=part)
                val shifted=pixels(moved)
                val changed=base.indices.count { base[it]!=shifted[it] }
                if(part!=null && part.kind!="blink" && part.angle!=0f) {
                    check(changed>3) { "${animal.id}/${piece.acc.id}: accessory did not follow ${part.name}" }
                    attachments++
                } else check(changed==0) { "${animal.id}/${piece.acc.id}: stationary accessory moved" }
                if(part!=null && animal.id in setOf("goldpig","dog","fennec","crow","flypig","pinkie","luna")) {
                    val pose=render(piece,sample)
                    File(output,"${animal.id}-${piece.acc.id}-moving.png").outputStream().use { pose.compress(Bitmap.CompressFormat.PNG,100,it) }
                    pose.recycle()
                }
                val frame=render(piece)
                val x=(i%6)*220f;val y=36f+(i/6)*250f
                canvas.drawBitmap(frame,x,y,null)
                canvas.drawText(piece.acc.id,x+5,y+236,label)
                frame.recycle();moved.recycle();rest.recycle()
                combinations++
            }
            File(output,"${animal.id}-fittings.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG,100,it) }
            sheet.recycle()
            log.append("${animal.id}: ${fitted.pieces.size} accessory fits PASS\n")
        }
        check(all.size==39 && combinations==1794)
        log.append("PASS $combinations accessory combinations; $attachments moving attachments\n")
    }
    /** Exercise the real Creature Canvas, including breathing, clocks, mirroring and outfits. */
    private fun checkLiveGallery(activity: MainActivity, output: File) {
        val examples = listOf(
            "dog" to mapOf("B" to "acc-B-bow", "G" to "acc-G-tailbow", "H" to "acc-H-grass"),
            "fennec" to mapOf("B" to "acc-B-clover", "G" to "acc-G-starring"),
            "flypig" to mapOf("F" to "acc-F-batwings", "G" to "acc-G-balloon"),
            "crow" to mapOf("F" to "acc-F-wings", "G" to "acc-G-hearttail"),
            "chicken" to mapOf("A" to "acc-A-crown", "F" to "acc-F-batwings", "H" to "acc-H-cloud"),
            "pinkie" to mapOf("B" to "acc-B-bow", "G" to "acc-G-tailbow"),
            "goldpig" to mapOf("B" to "acc-B-jade-earring", "G" to "acc-G-jade-tassel"),
            "sheep" to mapOf("C" to "ach_c11", "E" to "acc-E-bowtie", "H" to "acc-H-magic"),
            "luna" to mapOf("F" to "acc-F-wings", "G" to "acc-G-jade-tassel"),
        )
        test.runOnMainSync {
            activity.setContent {
                CompositionLocalProvider(LocalPalette provides LightPalette) {
                    Column(Modifier.fillMaxSize().background(Color.White).padding(top=36.dp,start=12.dp,end=12.dp)) {
                        BasicText("Animal outfit motion preview",style=TextStyle(color=Color.Black,fontSize=15.sp))
                        for ((row,cells) in examples.chunked(3).withIndex()) Row(Modifier.fillMaxWidth()) {
                            for ((col,example) in cells.withIndex()) Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                                Creature(example.first,Modifier.fillMaxWidth().height(145.dp),outfit=example.second,faceLeft=(row+col)%2==0,animate=true)
                                BasicText(example.first,style=TextStyle(color=Color.Black,fontSize=12.sp))
                            }
                        }
                    }
                }
            }
        }
        var first: IntArray? = null
        for ((step,delay) in listOf(900L,3500L,1100L).withIndex()) {
            SystemClock.sleep(delay)
            val ready=CountDownLatch(1)
            lateinit var shot: Bitmap
            var copied=-1
            test.runOnMainSync {
                val view=activity.window.decorView
                shot=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
                PixelCopy.request(activity.window,shot,{ copied=it;ready.countDown() },Handler(Looper.getMainLooper()))
            }
            check(ready.await(8,TimeUnit.SECONDS) && copied==PixelCopy.SUCCESS)
            val actual=IntArray(shot.width*shot.height).also { shot.getPixels(it,0,shot.width,0,0,shot.width,shot.height) }
            first?.let { baseline -> check(actual.indices.count { actual[it]!=baseline[it] }>100) { "Live Creature animation did not advance" } }
            if(first==null) first=actual
            File(output,"live-gallery-$step.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG,100,it) }
            shot.recycle()
        }
        File(output,"result.txt").appendText("PASS live Creature clocks, breathing, mirroring and outfits\n")
    }
}
