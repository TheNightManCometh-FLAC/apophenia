package com.betterappmafia.apophenia.audio

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Writes the app's take format (24-bit PCM, [channels] 1 or 2, [rate]) a block at a time, then fixes the header
 * sizes on [close]. Used for imports (stereo songs stay stereo) and rendered reference audio; the engine
 * writes its own takes (mono) in C++ the same way.
 */
class WavWriter(private val file: File, private val rate: Int = 48_000, private val channels: Int = 1) : AutoCloseable {
    private val out = BufferedOutputStream(FileOutputStream(file), 1 shl 16)
    private val bytes = ByteArray(3 * 4096)
    init { require(channels in 1..2) { "1 or 2 channels" } }
    private var written = 0L   // samples, all channels
    val frames: Long get() = written / channels

    init {
        out.write(header(0))
    }

    /** Writes [count] samples, interleaved L R L R when stereo. */
    fun write(samples: FloatArray, count: Int = samples.size) {
        var i = 0
        while (i < count) {
            val n = minOf(4096, count - i)
            for (k in 0 until n) {
                val v = (samples[i + k].coerceIn(-1f, 1f) * 8_388_607f).roundToInt()
                bytes[k * 3] = (v and 0xFF).toByte()
                bytes[k * 3 + 1] = ((v shr 8) and 0xFF).toByte()
                bytes[k * 3 + 2] = ((v shr 16) and 0xFF).toByte()
            }
            out.write(bytes, 0, n * 3)
            written += n
            i += n
        }
    }

    override fun close() {
        out.flush()
        out.close()
        RandomAccessFile(file, "rw").use { f ->
            f.seek(0)
            f.write(header(written * 3))
            f.fd.sync()
        }
    }

    private fun header(dataBytes: Long): ByteArray = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt((36 + dataBytes).toInt()); put("WAVE".toByteArray())
        put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(channels.toShort()); putInt(rate); putInt(rate * 3 * channels); putShort((3 * channels).toShort()); putShort(24)
        put("data".toByteArray()); putInt(dataBytes.toInt())
    }.array()
}

/**
 * Streaming sample-rate conversion to the app's rate, for imports. [Claude] Cubic Hermite
 * (Catmull-Rom) interpolation: good enough for working fragments (plan §3.1 imports are for
 * structure, not mastering); a downward conversion (e.g. 96 kHz files) is low-passed first by a
 * simple averaging of the input over each output step so it doesn't alias badly.
 */
class Resampler(private val inRate: Int, private val outRate: Int = 48_000) {
    private val step = inRate.toDouble() / outRate
    private var pos = 0.0          // read position, in input frames, relative to hist[1]
    private val hist = FloatArray(4) // the last 4 input frames seen: x[-1], x[0], x[1], x[2]
    private var primed = 0
    private val box = maxOf(1, step.toInt())    // averaging width when going down
    private val boxBuf = FloatArray(box)
    private var boxIdx = 0
    private var boxSum = 0f

    /** Feeds [count] input frames; calls [emit] with output frames as they become available. */
    fun process(input: FloatArray, count: Int, emit: (FloatArray, Int) -> Unit) {
        if (inRate == outRate) { emit(input, count); return }
        val out = FloatArray((count / step).toInt() + 4)
        var n = 0
        for (i in 0 until count) {
            var x = input[i]
            if (box > 1) {
                boxSum += x - boxBuf[boxIdx]; boxBuf[boxIdx] = x; boxIdx = (boxIdx + 1) % box
                x = boxSum / box
            }
            hist[0] = hist[1]; hist[1] = hist[2]; hist[2] = hist[3]; hist[3] = x
            if (primed < 3) { primed++; continue }
            // Interpolate every output point that falls between hist[1] and hist[2].
            while (pos < 1.0) {
                val t = pos.toFloat()
                val y0 = hist[0]; val y1 = hist[1]; val y2 = hist[2]; val y3 = hist[3]
                val a = -0.5f * y0 + 1.5f * y1 - 1.5f * y2 + 0.5f * y3
                val b = y0 - 2.5f * y1 + 2f * y2 - 0.5f * y3
                val c = -0.5f * y0 + 0.5f * y2
                if (n == out.size) { emit(out, n); n = 0 }
                out[n++] = ((a * t + b) * t + c) * t + y1
                pos += step
            }
            pos -= 1.0
        }
        if (n > 0) emit(out, n)
    }
}
