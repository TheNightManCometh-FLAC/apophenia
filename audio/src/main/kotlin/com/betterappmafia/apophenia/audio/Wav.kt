package com.betterappmafia.apophenia.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

/**
 * Reading the engine's takes (PCM WAV, 16- or 24-bit, mono or stereo) and repairing ones a crash
 * left unfinished. Pure JVM, so it is tested on the desktop.
 */
object Wav {
    data class Info(val rate: Int, val channels: Int, val bits: Int, val dataOffset: Long, val frames: Long)

    /** Walks the RIFF chunks. A header that still says 0 data bytes (a crashed take) trusts the file length. */
    fun info(file: File): Info? = RandomAccessFile(file, "r").use { f ->
        val b = ByteArray(12)
        if (f.length() < 12) return null
        f.readFully(b)
        if (String(b, 0, 4, Charsets.US_ASCII) != "RIFF" || String(b, 8, 4, Charsets.US_ASCII) != "WAVE") return null
        var rate = 0; var channels = 0; var bits = 0
        val hdr = ByteArray(8)
        while (f.filePointer + 8 <= f.length()) {
            f.readFully(hdr)
            val id = String(hdr, 0, 4, Charsets.US_ASCII)
            val size = ByteBuffer.wrap(hdr, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
            when (id) {
                "fmt " -> {
                    val fmt = ByteArray(16); f.readFully(fmt)
                    val bb = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                    if (bb.getShort(0).toInt() != 1) return null
                    channels = bb.getShort(2).toInt(); rate = bb.getInt(4); bits = bb.getShort(14).toInt()
                    if (bits != 16 && bits != 24 || channels !in 1..2) return null
                    f.seek(f.filePointer + size - 16 + (size and 1))
                }
                "data" -> {
                    if (rate == 0) return null
                    val offset = f.filePointer
                    val avail = f.length() - offset
                    val bytes = if (size == 0L || size > avail) avail else size
                    return Info(rate, channels, bits, offset, bytes / (channels * bits / 8))
                }
                else -> f.seek(f.filePointer + size + (size and 1))
            }
        }
        null
    }

    /**
     * Peak per column: [columns] values in 0..1, each the loudest sample in its share of the take.
     * Columns are half-open frame spans, so neighbours never share a boundary sample.
     */
    fun peaks(file: File, columns: Int): FloatArray {
        val info = info(file) ?: return FloatArray(columns)
        val out = FloatArray(columns)
        if (info.frames == 0L || columns <= 0) return out
        val bytesPer = info.bits / 8
        val frameBytes = info.channels * bytesPer
        RandomAccessFile(file, "r").use { f ->
            f.seek(info.dataOffset)
            val buf = ByteArray(frameBytes * 8192)
            var frame = 0L
            while (frame < info.frames) {
                val n = minOf(8192L, info.frames - frame).toInt()
                f.readFully(buf, 0, n * frameBytes)
                for (i in 0 until n) {
                    var v = 0f
                    for (c in 0 until info.channels) {
                        val p = i * frameBytes + c * bytesPer
                        val s = if (bytesPer == 3) {
                            ((buf[p].toInt() and 0xFF) or ((buf[p + 1].toInt() and 0xFF) shl 8) or (buf[p + 2].toInt() shl 16)) / 8388608f
                        } else {
                            ((buf[p].toInt() and 0xFF) or (buf[p + 1].toInt() shl 8)) / 32768f
                        }
                        v = max(v, abs(s))
                    }
                    val col = ((frame + i) * columns / info.frames).toInt()
                    if (v > out[col]) out[col] = v
                }
                frame += n
            }
        }
        return out
    }

    /**
     * The take mixed to mono for analysis, at most [maxSeconds]. When [rateOut] divides the file's rate
     * it is reduced on the way in (block average), so a long take never sits in memory at full rate.
     * Returns the samples and their rate.
     */
    fun readMono(file: File, rateOut: Int, maxSeconds: Int): Pair<FloatArray, Int>? {
        val info = info(file) ?: return null
        val k = if (info.rate % rateOut == 0) info.rate / rateOut else 1
        val frames = minOf(info.frames, info.rate.toLong() * maxSeconds)
        val out = FloatArray((frames / k).toInt())
        val bytesPer = info.bits / 8
        val frameBytes = info.channels * bytesPer
        RandomAccessFile(file, "r").use { f ->
            f.seek(info.dataOffset)
            val block = 8192 * k
            val buf = ByteArray(frameBytes * block)
            var frame = 0L
            var acc = 0f
            var n = 0
            var o = 0
            while (frame < frames && o < out.size) {
                val m = minOf(block.toLong(), frames - frame).toInt()
                f.readFully(buf, 0, m * frameBytes)
                for (i in 0 until m) {
                    var v = 0f
                    for (c in 0 until info.channels) {
                        val p = i * frameBytes + c * bytesPer
                        v += if (bytesPer == 3) {
                            ((buf[p].toInt() and 0xFF) or ((buf[p + 1].toInt() and 0xFF) shl 8) or (buf[p + 2].toInt() shl 16)) / 8388608f
                        } else {
                            ((buf[p].toInt() and 0xFF) or (buf[p + 1].toInt() shl 8)) / 32768f
                        }
                    }
                    acc += v / info.channels
                    if (++n == k) { if (o < out.size) out[o++] = acc / k; acc = 0f; n = 0 }
                }
                frame += m
            }
        }
        return out to info.rate / k
    }

    /**
     * The take as stereo blocks at its own rate, at most [maxFrames]: [emit] gets left, right and a count
     * (a mono take gives the same samples to both). Nothing but one block is in memory.
     */
    fun stream(file: File, maxFrames: Long, emit: (FloatArray, FloatArray, Int) -> Unit): Info? {
        val info = info(file) ?: return null
        val frames = minOf(info.frames, maxFrames)
        val bytesPer = info.bits / 8
        val frameBytes = info.channels * bytesPer
        val block = 8192
        val buf = ByteArray(frameBytes * block)
        val l = FloatArray(block); val r = FloatArray(block)
        RandomAccessFile(file, "r").use { f ->
            f.seek(info.dataOffset)
            var frame = 0L
            while (frame < frames) {
                val m = minOf(block.toLong(), frames - frame).toInt()
                f.readFully(buf, 0, m * frameBytes)
                for (i in 0 until m) {
                    for (c in 0 until info.channels) {
                        val p = i * frameBytes + c * bytesPer
                        val v = if (bytesPer == 3) {
                            ((buf[p].toInt() and 0xFF) or ((buf[p + 1].toInt() and 0xFF) shl 8) or (buf[p + 2].toInt() shl 16)) / 8388608f
                        } else {
                            ((buf[p].toInt() and 0xFF) or (buf[p + 1].toInt() shl 8)) / 32768f
                        }
                        if (c == 0) { l[i] = v; r[i] = v } else r[i] = v
                    }
                }
                emit(l, r, m)
                frame += m
            }
        }
        return info
    }

    /**
     * Sums [inputs] (same rate, mono or stereo) into a new stereo file [out] as long as the longest, a block
     * at a time. For playing a few stems together; the inputs are only read.
     */
    fun mix(inputs: List<File>, out: File) {
        val infos = inputs.map { info(it) ?: error("couldn't read ${it.name}") }
        val rate = infos.firstOrNull()?.rate ?: 48_000
        require(infos.all { it.rate == rate }) { "the files differ in rate" }
        val total = infos.maxOfOrNull { it.frames } ?: 0L
        val block = 8192
        val files = inputs.mapIndexed { i, f -> RandomAccessFile(f, "r").also { it.seek(infos[i].dataOffset) } }
        try {
            val buf = ByteArray(block * 6)
            val sum = FloatArray(block * 2)
            WavWriter(out, rate, 2).use { w ->
                var frame = 0L
                while (frame < total) {
                    val m = minOf(block.toLong(), total - frame).toInt()
                    sum.fill(0f, 0, 2 * m)
                    for ((i, f) in files.withIndex()) {
                        val info = infos[i]
                        val k = minOf(m.toLong(), info.frames - frame).toInt()
                        if (k <= 0) continue
                        val bytesPer = info.bits / 8
                        val frameBytes = info.channels * bytesPer
                        f.readFully(buf, 0, k * frameBytes)
                        for (j in 0 until k) for (c in 0 until info.channels) {
                            val p = j * frameBytes + c * bytesPer
                            val v = if (bytesPer == 3) {
                                ((buf[p].toInt() and 0xFF) or ((buf[p + 1].toInt() and 0xFF) shl 8) or (buf[p + 2].toInt() shl 16)) / 8388608f
                            } else {
                                ((buf[p].toInt() and 0xFF) or (buf[p + 1].toInt() shl 8)) / 32768f
                            }
                            if (info.channels == 1) { sum[2 * j] += v; sum[2 * j + 1] += v } else sum[2 * j + c] += v
                        }
                    }
                    w.write(sum, 2 * m)
                    frame += m
                }
            }
        } finally {
            files.forEach { it.close() }
        }
    }

    /**
     * Finishes a take a crash left as `.part`: sets the header sizes from the file length (dropping a
     * torn last frame) and renames it to [target]. The audio bytes are not touched.
     * Returns the frame count, or null if it isn't one of the engine's files.
     */
    fun repairPart(part: File, target: File): Long? {
        val info = info(part) ?: return null
        val dataBytes = info.frames * info.channels * (info.bits / 8)
        RandomAccessFile(part, "rw").use { f ->
            f.setLength(info.dataOffset + dataBytes)
            val le = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            f.seek(4); f.write(le.putInt(0, (info.dataOffset - 8 + dataBytes).toInt()).array())
            f.seek(info.dataOffset - 4); f.write(le.putInt(0, dataBytes.toInt()).array())
            f.fd.sync()
        }
        if (!part.renameTo(target)) return null
        return info.frames
    }
}
