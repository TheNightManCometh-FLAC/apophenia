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

    /**
     * [wiener]: the stems come from Spleeter's multichannel Wiener filter (its "MWF" option, norbert's `wiener` with
     * one EM iteration) instead of the plain ratio masks. Each stem's spatial covariance is estimated over one split.
     */
    fun separate(length: Long, source: Source, net: Net, sink: Sink, wiener: Boolean = false, progress: (done: Int, total: Int) -> Unit = { _, _ -> }) {
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
        val mwf = if (wiener) Wiener(stems) else null
        // One stem's filtered frame, both channels, interleaved re/im.
        val yL = DoubleArray(2 * BINS); val yR = DoubleArray(2 * BINS)
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
            mwf?.fit(est, lRe, lIm, rRe, rIm, nf)
            for (j in 0 until nf) {
                val b = j * BINS
                for (s in 0 until stems) {
                    if (mwf != null) mwf.filter(s, j, lRe, lIm, rRe, rIm, yL, yR)
                    else { mask(est, s, j, 0, maskL); mask(est, s, j, 1, maskR) }
                    // Y = L' + i·R', Hermitian halves mirrored, so its inverse is l' + i·r'.
                    for (k in 0 until BINS) {
                        val a: Double; val bb: Double; val c: Double; val d: Double
                        if (mwf != null) { a = yL[2 * k]; bb = yL[2 * k + 1]; c = yR[2 * k]; d = yR[2 * k + 1] }
                        else { a = lRe[b + k] * maskL[k]; bb = lIm[b + k] * maskL[k]; c = rRe[b + k] * maskR[k]; d = rIm[b + k] * maskR[k] }
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

    /**
     * norbert's `wiener(v, x, iterations = 1)` (sigsep/norbert, MIT), which Spleeter's MWF option calls with the four
     * models' magnitudes as v and the mix's complex spectrum as x, over one split:
     * - start: each stem's soft mask v / (eps + Σv), per channel, times the mix;
     * - from that start, each stem's power v_j (the mean over channels of |y|²) and its 2×2 spatial covariance
     *   R_j = Σ_t y·yᴴ / (eps + Σ_t v_j) per bin;
     * - y_j = v_j·R_j·(Σ_k v_k·R_k + √eps·I)⁻¹ · x.
     * As norbert does, the mix is scaled by max(1, max|x| / 10) first, which only sets how strong √eps·I is.
     * Above the net's bins, v is the frame's mean magnitude ratio times the mix's magnitude [Claude], the Wiener
     * counterpart of carrying the masks up by their mean.
     */
    private class Wiener(private val stems: Int) {
        private val eps = Math.ulp(1.0f).toDouble()
        private val reg = sqrt(eps)
        /** Per stem, frame and bin: v_j (scaled). */
        private val v = Array(stems) { FloatArray(FRAMES * BINS) }
        /** Per stem and bin: R_j as r00, r11, Re r01, Im r01 (Hermitian). */
        private val r = Array(stems) { DoubleArray(4 * BINS) }
        private val ratio = DoubleArray(2 * stems)
        private var scale = 1.0

        /** Magnitude estimate v of stem [s], channel [ch], frame [j], bin [k] (unscaled). */
        private fun est(e: List<FloatArray>, s: Int, ch: Int, j: Int, k: Int, mag: Double): Double =
            if (k < NET_BINS) e[s][(ch * FRAMES + j) * NET_BINS + k].toDouble() else ratio[2 * s + ch] * mag

        fun fit(e: List<FloatArray>, lRe: FloatArray, lIm: FloatArray, rRe: FloatArray, rIm: FloatArray, nf: Int) {
            scale = maxOf(1.0, hypotMax(lRe, lIm, rRe, rIm, nf) / 10)
            val sumV = Array(stems) { DoubleArray(BINS) }
            r.forEach { it.fill(0.0) }
            val vl = DoubleArray(stems); val vr = DoubleArray(stems)
            for (j in 0 until nf) {
                // The frame's mean magnitude ratio per stem and channel, for the bins above the net's.
                ratio.fill(0.0)
                for (ch in 0..1) for (k in 0 until NET_BINS) {
                    val at = (ch * FRAMES + j) * NET_BINS + k
                    var tot = 1e-10
                    for (q in 0 until stems) tot += e[q][at]
                    for (s in 0 until stems) ratio[2 * s + ch] += e[s][at] / tot / NET_BINS
                }
                val b = j * BINS
                for (k in 0 until BINS) {
                    val xlr = lRe[b + k] / scale; val xli = lIm[b + k] / scale; val xrr = rRe[b + k] / scale; val xri = rIm[b + k] / scale
                    val ml = sqrt(lRe[b + k].toDouble() * lRe[b + k] + lIm[b + k].toDouble() * lIm[b + k])
                    val mr = sqrt(rRe[b + k].toDouble() * rRe[b + k] + rIm[b + k].toDouble() * rIm[b + k])
                    var tl = eps; var tr = eps
                    for (s in 0 until stems) { vl[s] = est(e, s, 0, j, k, ml); vr[s] = est(e, s, 1, j, k, mr); tl += vl[s]; tr += vr[s] }
                    for (s in 0 until stems) {
                        val gl = vl[s] / tl; val gr = vr[s] / tr
                        val ylr = gl * xlr; val yli = gl * xli; val yrr = gr * xrr; val yri = gr * xri
                        val pl = ylr * ylr + yli * yli; val pr = yrr * yrr + yri * yri
                        val vj = 0.5 * (pl + pr)
                        v[s][b + k] = vj.toFloat()
                        sumV[s][k] += vj
                        val rs = r[s]; val o = 4 * k
                        rs[o] += pl; rs[o + 1] += pr
                        // y_l · conj(y_r)
                        rs[o + 2] += ylr * yrr + yli * yri; rs[o + 3] += yli * yrr - ylr * yri
                    }
                }
            }
            for (s in 0 until stems) for (k in 0 until BINS) { val d = eps + sumV[s][k]; for (q in 0..3) r[s][4 * k + q] /= d }
        }

        private fun hypotMax(lRe: FloatArray, lIm: FloatArray, rRe: FloatArray, rIm: FloatArray, nf: Int): Double {
            var m = 0.0
            for (i in 0 until nf * BINS) m = maxOf(m, lRe[i].toDouble() * lRe[i] + lIm[i].toDouble() * lIm[i], rRe[i].toDouble() * rRe[i] + rIm[i].toDouble() * rIm[i])
            return sqrt(m)
        }

        /** Stem [s] in frame [j]: both channels' spectra (unscaled), interleaved re/im, into [yl] and [yr]. */
        fun filter(s: Int, j: Int, lRe: FloatArray, lIm: FloatArray, rRe: FloatArray, rIm: FloatArray, yl: DoubleArray, yr: DoubleArray) {
            val b = j * BINS
            for (k in 0 until BINS) {
                val o = 4 * k
                // C = Σ v_q R_q + reg·I (Hermitian: c00, c11 real; c01 = a + ib; c10 = conj c01).
                var c00 = reg; var c11 = reg; var ca = 0.0; var cb = 0.0
                for (q in 0 until stems) { val vq = v[q][b + k].toDouble(); val rq = r[q]; c00 += vq * rq[o]; c11 += vq * rq[o + 1]; ca += vq * rq[o + 2]; cb += vq * rq[o + 3] }
                val det = c00 * c11 - (ca * ca + cb * cb)
                // inv C = [[c11, −c01], [−c10, c00]] / det
                val i00 = c11 / det; val i11 = c00 / det; val i01a = -ca / det; val i01b = -cb / det   // i10 = conj i01
                // G = v_s · R_s · inv C, R_s = [[p, u], [conj u, q]], u = ua + i·ub
                val vs = v[s][b + k].toDouble(); val rs = r[s]
                val p = rs[o]; val q2 = rs[o + 1]; val ua = rs[o + 2]; val ub = rs[o + 3]
                // Row 0: [p, u] · invC
                val g00r = vs * (p * i00 + (ua * i01a + ub * i01b)); val g00i = vs * (ua * (-i01b) + ub * i01a)
                val g01r = vs * (p * i01a + ua * i11); val g01i = vs * (p * i01b + ub * i11)
                // Row 1: [conj u, q] · invC
                val g10r = vs * (ua * i00 + q2 * i01a); val g10i = vs * (-ub * i00 - q2 * i01b)
                val g11r = vs * ((ua * i01a + ub * i01b) + q2 * i11); val g11i = vs * (ua * i01b - ub * i01a)
                val xlr = lRe[b + k].toDouble(); val xli = lIm[b + k].toDouble(); val xrr = rRe[b + k].toDouble(); val xri = rIm[b + k].toDouble()
                yl[2 * k] = g00r * xlr - g00i * xli + g01r * xrr - g01i * xri
                yl[2 * k + 1] = g00r * xli + g00i * xlr + g01r * xri + g01i * xrr
                yr[2 * k] = g10r * xlr - g10i * xli + g11r * xrr - g11i * xri
                yr[2 * k + 1] = g10r * xli + g10i * xlr + g11r * xri + g11i * xrr
            }
        }
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
