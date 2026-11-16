package com.kongye.reader

import android.app.Instrumentation
import com.kongye.reader.data.UpdateCheck
import com.kongye.reader.data.Updates
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException

/** Exercise the production checker with deterministic releases and network failures. */
internal class UpdateChecks(private val test: Instrumentation) {
    fun run() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val state = MutableStateFlow(Updates.State())
        var requests = 0
        var response = CompletableDeferred<Updates.Release>()
        val checker = UpdateCheck(scope, state, latest = { requests++;response.await() },
            resolve = { Updates.releaseState(it, installed = "2.1.2") })
        val newer = Updates.Release(tag = "v999.0.1", name = "Future release")
        val latest = Updates.Release(tag = "v2.1.2")
        val cache = File(test.targetContext.cacheDir, "update/KongyeReader-999.0.1.apk")
        try {
            val first = checkNotNull(checker.check(automatic = true))
            check(state.value.phase == Updates.Phase.CHECKING)
            check(checker.check(automatic = false) == null && requests == 1) { "Checks must share the in-flight request" }
            response.complete(newer);first.join()
            check(state.value.phase == Updates.Phase.FOUND && state.value.release == newer) { "New releases without an APK must still be announced" }
            val discovered = state.value
            response = CompletableDeferred()
            val offline = checkNotNull(checker.check(automatic = true))
            response.completeExceptionally(IOException("offline"));offline.join()
            check(state.value == discovered) { "Offline automatic check erased the update hint" }
            response = CompletableDeferred()
            val manual = checkNotNull(checker.check(automatic = false))
            response.completeExceptionally(IOException("offline"));manual.join()
            check(state.value.phase == Updates.Phase.FAILED && state.value.error != null)
            state.value = Updates.State()
            response = CompletableDeferred()
            val emptyOffline = checkNotNull(checker.check(automatic = true))
            response.completeExceptionally(IOException("offline"));emptyOffline.join()
            check(state.value.phase == Updates.Phase.IDLE) { "An offline startup should remain quiet" }
            response = CompletableDeferred(latest)
            checkNotNull(checker.check(automatic = true)).join()
            check(state.value.phase == Updates.Phase.LATEST)
            response = CompletableDeferred(Updates.Release(tag = "v2.0.10"))
            checkNotNull(checker.check(automatic = true)).join()
            check(state.value.phase == Updates.Phase.LATEST) { "An older release must never trigger an update" }
            for (phase in listOf(Updates.Phase.DOWNLOADING,Updates.Phase.READY)) {
                val saved = Updates.State(phase, newer, 4, 8)
                state.value = saved
                val count = requests
                check(checker.check(automatic = true) == null && state.value == saved && count == requests)
                check(checker.check(automatic = false) == null && state.value == saved && count == requests)
            }
            cache.parentFile?.mkdirs();cache.writeBytes(ByteArray(8))
            val withApk = newer.copy(assets = listOf(Updates.Asset("reader.apk",8,"https://example.invalid/reader.apk")))
            check(Updates.releaseState(withApk,"2.1.2").phase == Updates.Phase.READY)
            cache.writeBytes(ByteArray(3))
            check(Updates.releaseState(withApk,"2.1.2").phase == Updates.Phase.FOUND) { "An incomplete cached download cannot be installed" }
            val file = File(test.targetContext.filesDir,"update-check-result.txt")
            file.writeText("PASS: new/current/older releases; quiet offline startup; retained update hints; duplicate checks; active downloads; complete and incomplete cached APKs")
        } finally { scope.cancel();cache.delete() }
    }
}
