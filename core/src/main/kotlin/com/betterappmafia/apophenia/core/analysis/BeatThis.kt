package com.betterappmafia.apophenia.core.analysis

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.sqrt

/**
 * Beat This! (Foscarin, Schlüter & Widmer, ISMIR 2024; CPJKU/beat_this, MIT, code and weights), the "small0"
 * checkpoint exported to ONNX (`tools/beat-this-export.py`), around the model, which maps a log-mel spectrogram to
 * beat and downbeat logits per frame. Ported from beat_this 1.1.0:
 * - `preprocessing.LogMelSpect`: 22,050 Hz mono, torchaudio's MelSpectrogram with n_fft 1024, hop 441 (50 frames a
 *   second), periodic Hann, centred with reflect padding, magnitude divided by √n_fft ("frame_length"), 128 Slaney
 *   mel bands from 30 to 11,000 Hz with no area norm; then log1p(1000·mel);
 * - `inference.split_predict_aggregate`: 1,500-frame chunks overlapping by a 6-frame border, earlier chunks win;
 * - `Postprocessor("minimal")`: peaks within ±3 frames with logit > 0, adjacent peaks merged, downbeats moved to
 *   the nearest beat.
 */
object BeatThis {
    const val RATE = 22_050
    const val N_FFT = 1024
    const val HOP = 441
    const val MELS = 128
    const val FPS = 50
    const val CHUNK = 1500
    const val BORDER = 6

    /** Runs the model on [frames] × [MELS] log-mel values (row-major); returns beat and downbeat logits per frame. */
    fun interface Net { fun run(spect: FloatArray, frames: Int): Pair<FloatArray, FloatArray> }

    /** Beat and downbeat times in seconds. */
    data class Track(val beats: List<Double>, val downbeats: List<Double>)

    private val window = DoubleArray(N_FFT) { 0.5 - 0.5 * cos(2 * PI * it / N_FFT) }

    /** torchaudio `melscale_fbanks(513, 30, 11000, 128, 22050, norm=None, mel_scale="slaney")`: [bin][mel]. */
    val fbank: Array<DoubleArray> = run {
        fun hzToMel(f: Double): Double = if (f < 1000.0) f / (200.0 / 3) else 15.0 + ln(f / 1000.0) / (ln(6.4) / 27.0)
        fun melToHz(m: Double): Double = if (m < 15.0) m * (200.0 / 3) else 1000.0 * exp((ln(6.4) / 27.0) * (m - 15.0))
        val nFreqs = N_FFT / 2 + 1
        val freqs = DoubleArray(nFreqs) { it * (RATE / 2).toDouble() / (nFreqs - 1) }
        val mMin = hzToMel(30.0); val mMax = hzToMel(11_000.0)
        val fPts = DoubleArray(MELS + 2) { melToHz(mMin + (mMax - mMin) * it / (MELS + 1)) }
        Array(nFreqs) { b ->
            DoubleArray(MELS) { m ->
                val down = (fPts[m] - freqs[b]) * -1.0 / (fPts[m + 1] - fPts[m])
                val up = (fPts[m + 2] - freqs[b]) / (fPts[m + 2] - fPts[m + 1])
                maxOf(0.0, minOf(down, up))
            }
        }
    }

    /** The log-mel spectrogram of [x] (22,050 Hz mono): 1 + x.size / HOP frames of [MELS], row-major. */
    fun spect(x: FloatArray): FloatArray {
        val frames = 1 + x.size / HOP
        val out = FloatArray(frames * MELS)
        if (x.isEmpty()) return out
        val half = N_FFT / 2
        // Reflect padding as torch.stft(center=True, pad_mode="reflect"); x[-i] = x[i].
        fun at(i: Int): Float {
            var j = i
            val n = x.size
            if (n == 1) return x[0]
            while (j < 0 || j >= n) j = if (j < 0) -j else 2 * (n - 1) - j
            return x[j]
        }
        val fft = FftPlan(N_FFT)
        val re = DoubleArray(N_FFT); val im = DoubleArray(N_FFT)
        val mag = DoubleArray(half + 1)
        val norm = 1.0 / sqrt(N_FFT.toDouble())
        for (f in 0 until frames) {
            val start = f * HOP - half
            for (t in 0 until N_FFT) { re[t] = at(start + t) * window[t]; im[t] = 0.0 }
            fft.forward(re, im)
            for (k in 0..half) mag[k] = sqrt(re[k] * re[k] + im[k] * im[k]) * norm
            for (m in 0 until MELS) {
                var s = 0.0
                for (k in 0..half) { val w = fbank[k][m]; if (w != 0.0) s += mag[k] * w }
                out[f * MELS + m] = ln1p(1000.0 * s).toFloat()
            }
        }
        return out
    }

    /** Beat and downbeat logits for the whole piece, chunked as the reference does. */
    fun logits(spect: FloatArray, net: Net): Pair<FloatArray, FloatArray> {
        val n = spect.size / MELS
        val beat = FloatArray(n) { -1000f }; val down = FloatArray(n) { -1000f }
        if (n == 0) return beat to down
        // Chunk starts: from −BORDER in steps of CHUNK − 2·BORDER; the last moved to end at the piece's end.
        val starts = ArrayList<Int>()
        var s = -BORDER
        while (s < n - BORDER) { starts += s; s += CHUNK - 2 * BORDER }
        if (n > CHUNK - 2 * BORDER) starts[starts.size - 1] = n - (CHUNK - BORDER)
        // Earlier chunks win: write the later ones first.
        for (start in starts.asReversed()) {
            val from = maxOf(start, 0); val to = minOf(start + CHUNK, n)
            val left = maxOf(0, -start); val right = maxOf(0, minOf(BORDER, start + CHUNK - n))
            val len = left + (to - from) + right
            val chunk = FloatArray(len * MELS)
            System.arraycopy(spect, from * MELS, chunk, left * MELS, (to - from) * MELS)
            val (b, d) = net.run(chunk, len)
            // Keep chunk frames [BORDER, len − BORDER) at piece frames start + BORDER …
            for (i in BORDER until len - BORDER) {
                val p = start + i
                if (p in 0 until n) { beat[p] = b[i]; down[p] = d[i] }
            }
        }
        return beat to down
    }

    /** The minimal postprocessor. */
    fun track(beat: FloatArray, down: FloatArray): Track {
        fun peaks(l: FloatArray): List<Double> {
            val frames = ArrayList<Int>()
            for (i in l.indices) {
                if (l[i] <= 0f) continue
                var max = l[i]
                for (j in maxOf(0, i - 3)..minOf(l.size - 1, i + 3)) if (l[j] > max) max = l[j]
                if (l[i] == max) frames += i
            }
            // Merge runs of frames at most one apart into their running mean, as deduplicate_peaks does.
            val out = ArrayList<Double>()
            var p = 0.0; var c = 0; var started = false
            for (f in frames) {
                if (!started) { p = f.toDouble(); c = 1; started = true; continue }
                if (f - p <= 1) { c++; p += (f - p) / c } else { out += p; p = f.toDouble(); c = 1 }
            }
            if (started) out += p
            return out.map { it / FPS }
        }
        val b = peaks(beat)
        val d = peaks(down).map { t -> b.minByOrNull { kotlin.math.abs(it - t) } ?: t }.distinct().sorted()
        return Track(b, d)
    }

    fun track(x: FloatArray, net: Net): Track = spect(x).let { s -> logits(s, net).let { (b, d) -> track(b, d) } }
}
