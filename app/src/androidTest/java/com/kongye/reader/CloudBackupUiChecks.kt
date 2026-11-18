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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kongye.reader.ui.screens.CloudBackupsSheet
import com.kongye.reader.ui.screens.BackupScreen
import com.kongye.reader.data.Backup
import com.kongye.reader.ui.theme.DarkPalette
import com.kongye.reader.ui.theme.LightPalette
import com.kongye.reader.ui.theme.LocalPalette
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class CloudBackupUiChecks(private val test: Instrumentation) {
    private lateinit var activity: MainActivity
    private val origin=IntArray(2)
    private fun nodes(): List<SemanticsNode> {
        val result=mutableListOf<SemanticsNode>()
        test.runOnMainSync {
            fun add(n: SemanticsNode) { result+=n;n.children.forEach(::add) }
            fun visit(view: View) {
                val getter=view.javaClass.methods.firstOrNull { it.name=="getSemanticsOwner" && it.parameterCount==0 }
                if(getter!=null) { view.getLocationInWindow(origin);add((getter.invoke(view) as SemanticsOwner).rootSemanticsNode) }
                else if(view is ViewGroup) for(i in 0 until view.childCount) visit(view.getChildAt(i))
            }
            visit(activity.window.decorView)
        }
        return result.filter { it.boundsInRoot.width>0 && it.boundsInRoot.height>0 }
    }
    private fun labels(node: SemanticsNode)=node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
    private fun waitFor(text: String): SemanticsNode {
        val end=SystemClock.uptimeMillis()+8000
        while(SystemClock.uptimeMillis()<end) {
            nodes().filter { text in labels(it) }.minByOrNull { it.boundsInRoot.width*it.boundsInRoot.height }?.let { return it }
            SystemClock.sleep(100)
        }
        error("Cloud UI missing: $text")
    }
    private fun tap(text: String) {
        val target=waitFor(text)
        check(activity.hasWindowFocus()) { "Test app is not foreground; stopping input" }
        val all=nodes()
        val center=target.boundsInRoot.center
        val clickable=all.filter { it.config.getOrNull(SemanticsActions.OnClick)!=null && it.boundsInRoot.contains(center) }
            .minBy { it.boundsInRoot.width*it.boundsInRoot.height }
        val point=clickable.boundsInRoot.center
        val time=SystemClock.uptimeMillis()
        for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
            val event=MotionEvent.obtain(time,SystemClock.uptimeMillis(),action,point.x+origin[0],point.y+origin[1],0)
            event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
            test.runOnMainSync { activity.dispatchTouchEvent(event) };event.recycle()
            SystemClock.sleep(40)
        }
        SystemClock.sleep(350)
    }
    fun run() {
        check(test.targetContext.packageName.endsWith(".debug"))
        val output=File(test.targetContext.filesDir,"cloud-backup-review").apply { mkdirs() }
        MockDav().use { dav ->
            val restored=AtomicBoolean(false)
            activity=test.startActivitySync(Intent(test.targetContext,MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }) as MainActivity

            fun capture(name: String) {
                val ready=CountDownLatch(1);lateinit var shot:Bitmap;var result=-1
                test.runOnMainSync {
                    val view=activity.window.decorView
                    shot=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
                    PixelCopy.request(activity.window,shot,{ result=it;ready.countDown() },Handler(Looper.getMainLooper()))
                }
                check(ready.await(8,TimeUnit.SECONDS) && result==PixelCopy.SUCCESS)
                File(output,"$name.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG,100,it) };shot.recycle()
            }
            val originalConfig=Backup.cloud()
            try {
                Backup.saveCloud(dav.config.copy(lastBackup=System.currentTimeMillis(),auto=false))
                for ((name,palette) in listOf("backup-layout-light" to LightPalette,"backup-layout-dark" to DarkPalette)) {
                    test.runOnMainSync { activity.setContent {
                        CompositionLocalProvider(LocalPalette provides palette) { BackupScreen() }
                    } }
                    waitFor("备份到云端");SystemClock.sleep(600);capture(name)
                }
                test.runOnMainSync { activity.setContent {
                    CompositionLocalProvider(LocalPalette provides LightPalette) {
                        Box(Modifier.fillMaxSize().background(Color(0xFFF5F5F6))) {
                            CloudBackupsSheet(dav.config,false,onDismiss={},onRestored={ restored.set(true) })
                        }
                    }
                } }
                waitFor("共 5 份备份");capture("list")
                tap("2026.11.17");waitFor("恢复 2026.11.17？");waitFor("拉取并恢复");capture("restore-confirm");tap("取消")
                tap("选择删除");tap("2026.11.17");tap("2026.11.16");tap("删除所选（2）")
                waitFor("删除所选的 2 份云备份？");capture("delete-confirm");tap("删除所选备份")
                waitFor("共 3 份备份");capture("after-delete")
                check(dav.files.keys.none { it.endsWith("20261117.zip") || it.endsWith("20261116.zip") })
                check(dav.files.containsKey("/dav/KongyeReader/kongye-reader-backup-20261118.zip"))
                check(dav.files.containsKey("/dav/private-notes.txt"))
                tap("完成");tap("2026.11.18");tap("拉取并恢复")
                val end=SystemClock.uptimeMillis()+8000
                while(!restored.get() && SystemClock.uptimeMillis()<end)SystemClock.sleep(100)
                check(restored.get())
                check(File(test.targetContext.filesDir,"cloud-restore-probe.txt").readText()=="day18")
                File(test.targetContext.filesDir,"cloud-backup-test-result.txt").appendText("\nPASS UI: choose historical date, cancel restore, select exactly two backups, confirm deletion, preserve unselected files, restore selected date")
            } catch(t: Throwable) {
                runCatching { capture("failure") }
                runCatching {
                    test.uiAutomation.takeScreenshot()?.let { bitmap ->
                        File(output,"system-overlay.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
                    }
                    val text=StringBuilder()
                    fun dump(node: android.view.accessibility.AccessibilityNodeInfo) {
                        if(node.packageName?.toString() in listOf("android",test.targetContext.packageName)) text.append(node.text.toString()+"\n")
                        for(i in 0 until node.childCount) node.getChild(i)?.let(::dump)
                    }
                    test.uiAutomation.rootInActiveWindow?.let(::dump)
                    File(output,"system-overlay.txt").writeText(text.toString())
                }
                File(output,"failure-tree.txt").writeText(nodes().joinToString("\n") { labels(it).toString()+" "+it.boundsInRoot })
                throw t
            } finally { Backup.saveCloud(originalConfig);test.runOnMainSync { activity.finish() } }
        }
    }
}
