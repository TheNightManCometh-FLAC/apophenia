package com.betterappmafia.apophenia.core.analysis

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Spleeter 4-stem separation (plan §10.6) around the model, which only maps magnitudes to magnitudes:
 * the STFT going in, the ratio masks, and the inverse STFT coming out. Ported from the reference pipeline
 * on the model card (Best-Practice/spleeter-4stems-onnx, kept in the app's licence notices):
 * 44.1 kHz stereo, periodic Hann, frame 4096, hop 1024, 3072 zeros in front; the net sees 1024 of the 2049
 * bins in 512-frame splits; each stem's mask is its square over the sum of all four squares; masks are
 * carried above bin 1024 by their per-frame mean ("average"); the masks multiply the original complex
 * spectrum (so the phase is the mix's own); weighted overlap-add brings it back.
 *
 * Splits are independent inputs to the net (the split axis is a batch axis), so a song goes through one
 * split (about 12 seconds) at a time and never sits in memory whole, and the stems stream out to the sink.
 * The four masks sum to 1, so the four stems sum back to the mix (the card's own check, and the test's).
 */
object Spleeter {
    const val RATE = 44_100
    const val N_FFT = 4096
    const val HOP = 1024
    /** Frames per split, and the bins the net sees. */
    const val FRAMES = 512
    const val NET_BINS = 1024
    const val BINS = N_FFT / 2 + 1
    const val PAD = N_FFT - HOP
    val STEMS = listOf("vocals", "drums", "bass", "other")
    private const val EPS = 1e-10

    /** The net's input and each output: `[2 channels][FRAMES][NET_BINS]`, i.e. `[2, 1, 512, 1024]`. */
    const val NET_SIZE = 2 * FRAMES * NET_BINS

    fun frameCount(length: Long): Int = ((PAD + length + HOP - 1) / HOP).toInt()
    fun splitCount(length: Long): Int = (frameCount(length) + FRAMES - 1) / FRAMES

    /** Stereo samples [start, start + count) of the song, inside 0 until its length. */
    fun interface Source { fun read(start: Long, left: FloatArray, right: FloatArray, offset: Int, count: Int) }

    /** Runs all four stem models on one split of magnitudes; returns their estimates in [STEMS] order. */
    fun interface Net { fun run(magnitudes: FloatArray): List<FloatArray> }

    /** Receives the next [count] samples of stem [stem], in order from the start. */
    fun interface Sink { fun write(stem: Int, left: FloatArray, right: FloatArray, count: Int) }

    /** The periodic Hann window (denominator N, not N − 1: the card's `np.hanning(N + 1)[:-1]`). */
    val window: DoubleArray = DoubleArray(N_FFT) { 0.5 - 0.5 * cos(2 * PI * it / N_FFT) }

    fun separate(length: Long, source: Source, net: Net, sink: Sink, progress: (done: Int, total: Int) -> Unit = { _, _ -> }) {
        if (length <= 0) return
        val n = frameCount(length)
        val splits = splitCount(length)
        val stems = STEMS.size
        val fft = FftPlan(N_FFT)
        val span = (FRAMES - 1) * HOP + N_FFT
        val inL = FloatArray(span); val inR = FloatArray(span)
        // The split's spectrum, both channels, bins 0..N/2.
        val lRe = FloatArray(FRAMES * BINS); val lIm = FloatArray(FRAMES * BINS)
        val rRe = FloatArray(FRAMES * BINS); val rIm = FloatArray(FRAMES * BINS)
        val mags = FloatArray(NET_SIZE)
        val re = DoubleArray(N_FFT); val im = DoubleArray(N_FFT)
        val maskL = DoubleArray(BINS); val maskR = DoubleArray(BINS)
        // Overlap-add: per stem and channel, padded positions [frame·HOP, frame·HOP + N).
        val accL = Array(stems) { DoubleArray(N_FFT) }; val accR = Array(stems) { DoubleArray(N_FFT) }
        // Every emitted sample has all four frames over it, so the summed squared window depends only on its phase in the hop.
        val recip = DoubleArray(HOP) { p ->
            var s = 0.0; var i = p
            while (i < N_FFT) { s += window[i] * window[i]; i += HOP }
            1.0 / s
        }
        val outL = FloatArray(HOP); val outR = FloatArray(HOP)
        var emitted = 0L
        progress(0, splits)
        for (split in 0 until splits) {
            val f0 = split * FRAMES
            val nf = minOf(FRAMES, n - f0)
            // Original sample of padded position f0·HOP.
            val start = f0.toLong() * HOP - PAD
            inL.fill(0f); inR.fill(0f)
            val from = maxOf(0L, start); val to = minOf(length, start + span)
            if (to > from) source.read(from, inL, inR, (from - start).toInt(), (to - from).toInt())
            mags.fill(0f)
            for (j in 0 until nf) {
                val o = j * HOP
                // Both channels in one transform: z = l + i·r.
                for (t in 0 until N_FFT) { re[t] = inL[o + t] * window[t]; im[t] = inR[o + t] * window[t] }
                fft.forward(re, im)
                val b = j * BINS
                for (k in 0 until BINS) {
                    val m = (N_FFT - k) and (N_FFT - 1)
                    // L[k] = (Z[k] + conj Z[N−k]) / 2,  R[k] = (Z[k] − conj Z[N−k]) / 2i
                    val lr = 0.5 * (re[k] + re[m]); val li = 0.5 * (im[k] - im[m])
                    val rr = 0.5 * (im[k] + im[m]); val ri = -0.5 * (re[k] - re[m])
                    lRe[b + k] = lr.toFloat(); lIm[b + k] = li.toFloat(); rRe[b + k] = rr.toFloat(); rIm[b + k] = ri.toFloat()
                    if (k < NET_BINS) {
                        mags[j * NET_BINS + k] = sqrt(lr * lr + li * li).toFloat()
                        mags[(FRAMES + j) * NET_BINS + k] = sqrt(rr * rr + ri * ri).toFloat()
                    }
                }
            }
            val est = net.run(mags)
            require(est.size == stems && est.all { it.size == NET_SIZE }) { "the stem models gave ${est.size} outputs" }
            for (j in 0 until nf) {
                val b = j * BINS
                for (s in 0 until stems) {
                    mask(est, s, j, 0, maskL); mask(est, s, j, 1, maskR)
                    // Y = Lm + i·Rm, Hermitian halves mirrored, so its inverse is l + i·r.
                    for (k in 0 until BINS) {
                        val a = lRe[b + k] * maskL[k]; val bb = lIm[b + k] * maskL[k]
                        val c = rRe[b + k] * maskR[k]; val d = rIm[b + k] * maskR[k]
                        re[k] = a - d; im[k] = bb + c
                        if (k in 1 until N_FFT / 2) { re[N_FFT - k] = a + d; im[N_FFT - k] = c - bb }
                    }
                    fft.inverse(re, im)
                    val al = accL[s]; val ar = accR[s]
                    for (t in 0 until N_FFT) { al[t] += re[t] * window[t]; ar[t] += im[t] * window[t] }
                }
                // Padded positions [frame·HOP, frame·HOP + HOP) are now final.
                val pos = (f0 + j).toLong() * HOP
                val lo = maxOf(pos, PAD.toLong()); val hi = minOf(pos + HOP, PAD + length)
                for (s in 0 until stems) {
                    val al = accL[s]; val ar = accR[s]
                    if (hi > lo) {
                        val c = (hi - lo).toInt(); val off = (lo - pos).toInt()
                        for (i in 0 until c) { val r = recip[off + i]; outL[i] = (al[off + i] * r).toFloat(); outR[i] = (ar[off + i] * r).toFloat() }
                        sink.write(s, outL, outR, c)
                    }
                    System.arraycopy(al, HOP, al, 0, N_FFT - HOP); al.fill(0.0, N_FFT - HOP, N_FFT)
                    System.arraycopy(ar, HOP, ar, 0, N_FFT - HOP); ar.fill(0.0, N_FFT - HOP, N_FFT)
                }
                if (hi > lo) emitted += hi - lo
            }
            progress(split + 1, splits)
        }
        check(emitted == length) { "separated $emitted of $length samples" }
    }

    /** Stem [s]'s soft mask for frame [j], channel [ch]: bins below NET_BINS from the estimates, the rest their mean. */
    private fun mask(est: List<FloatArray>, s: Int, j: Int, ch: Int, out: DoubleArray) {
        val base = (ch * FRAMES + j) * NET_BINS
        var sum = 0.0
        for (k in 0 until NET_BINS) {
            var total = EPS
            for (e in est) { val v = e[base + k].toDouble(); total += v * v }
            val v = est[s][base + k].toDouble()
            val m = (v * v + EPS / est.size) / total
            out[k] = m; sum += m
        }
        out.fill(sum / NET_BINS, NET_BINS, BINS)
    }
}

/** A complex radix-2 FFT of one size with its tables made once. [inverse] includes the 1/N. */
class FftPlan(val n: Int) {
    private val cosT = DoubleArray(n / 2) { cos(2 * PI * it / n) }
    private val sinT = DoubleArray(n / 2) { sin(2 * PI * it / n) }
    private val rev = IntArray(n)

    init {
        require(n > 1 && n and (n - 1) == 0) { "FFT size must be a power of two" }
        var bits = 0
        while (1 shl bits < n) bits++
        for (i in 0 until n) rev[i] = Integer.reverse(i) ushr (32 - bits)
    }

    fun forward(re: DoubleArray, im: DoubleArray) = run(re, im, -1.0)

    fun inverse(re: DoubleArray, im: DoubleArray) {
        run(re, im, 1.0)
        val s = 1.0 / n
        for (i in 0 until n) { re[i] *= s; im[i] *= s }
    }

    private fun run(re: DoubleArray, im: DoubleArray, sign: Double) {
        for (i in 0 until n) {
            val j = rev[i]
            if (i < j) { var t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t }
        }
        var len = 2
        while (len <= n) {
            val half = len / 2
            val step = n / len
            var i = 0
            while (i < n) {
                for (k in 0 until half) {
                    val wr = cosT[k * step]; val wi = sign * sinT[k * step]
                    val a = i + k; val b = a + half
                    val vr = re[b] * wr - im[b] * wi; val vi = re[b] * wi + im[b] * wr
                    re[b] = re[a] - vr; im[b] = im[a] - vi
                    re[a] += vr; im[a] += vi
                }
                i += len
            }
            len = len shl 1
        }
    }
}
