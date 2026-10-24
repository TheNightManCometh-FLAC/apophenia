package com.betterappmafia.apophenia.audio

import com.betterappmafia.apophenia.Breadcrumbs
import com.betterappmafia.apophenia.core.catalog.Conform
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Conformed layers (plan §5.3, §12): rendered in the background, cached, never touching the original.
 * The cache is evictable (the app's cache folder, oldest-used deleted past [capBytes]); a missing
 * render is simply made again. Keyed by source file, amounts and stretcher version.
 */
class Renders(private val dir: File, private val capBytes: Long = 512L * 1024 * 1024) {
    private val scope = CoroutineScope(SupervisorJob() + Executors.newSingleThreadExecutor { Thread(it, "render") }.asCoroutineDispatcher())
    private val pending = ConcurrentHashMap.newKeySet<String>()
    private val failed = ConcurrentHashMap.newKeySet<String>()
    private val _finished = MutableStateFlow(0)
    /** Goes up each time a render finishes, so whoever shows renders can look again. */
    val finished: StateFlow<Int> = _finished.asStateFlow()

    private fun key(source: File, c: Conform): String {
        val text = "${source.name}|${c.lengthFactor}|${c.semitones}|$STRETCHER"
        return MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
    }

    /** The finished render, or null (then call [request]). */
    fun ready(source: File, c: Conform): File? = File(dir, key(source, c) + ".wav").takeIf { it.isFile }?.also { it.setLastModified(System.currentTimeMillis()) }

    fun isPending(source: File, c: Conform) = key(source, c) in pending

    /** Did rendering this fail (too short for the stretcher, unreadable)? Then the layer plays as recorded. */
    fun hasFailed(source: File, c: Conform) = key(source, c) in failed

    fun request(source: File, c: Conform) {
        val k = key(source, c)
        if (k in failed || !pending.add(k)) return
        scope.launch {
            dir.mkdirs()
            val final = File(dir, "$k.wav")
            val part = File(dir, "$k.wav.part")
            val t0 = System.nanoTime()
            val n = NativeEngine.render(source.absolutePath, part.absolutePath, final.absolutePath, c.lengthFactor, c.semitones)
            Breadcrumbs.add("render ${source.name} x${"%.3f".format(c.lengthFactor)} ${c.semitones}st -> $n in ${(System.nanoTime() - t0) / 1_000_000} ms")
            if (n < 0) failed += k
            pending -= k
            evict()
            _finished.value++
        }
    }

    private fun evict() {
        val files = dir.listFiles { f -> f.name.endsWith(".wav") }.orEmpty().sortedBy { it.lastModified() }
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= capBytes) break
            total -= f.length()
            f.delete()
        }
    }

    companion object {
        /** Part of every cache key: a new stretcher version renders afresh. */
        const val STRETCHER = "signalsmith-stretch-1.4.0"
    }
}
