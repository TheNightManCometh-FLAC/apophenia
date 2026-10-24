package com.betterappmafia.apophenia.core.analysis

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Speech → text (plan §10.4): a voice memo cut into pieces at its quietest moments, each piece read by
 * Moonshine Base (English, MIT) and given the time it starts and ends in the take. The take is never
 * changed; the transcript is its own text, editable.
 */
object Speech {
    const val RATE = 16_000
    const val MODEL_ID = "moonshine-base"
    /** [Claude] Pieces are cut at the quietest moment between 8 and 20 seconds in: Moonshine reads at most 194 tokens at once. */
    const val MAX_PIECE = 20.0
    const val MIN_CUT = 8.0
    private const val FRAME = 0.03
    /** Below this RMS (about −46 dBFS) a piece is silence and isn't read. */
    const val SILENCE = 0.005
    /** The decoder's position limit (config `max_position_embeddings`), the start token included. */
    const val MAX_POSITIONS = 194
    /** Tokens allowed per second of audio (the Hugging Face Moonshine example's limit). */
    const val TOKENS_PER_SECOND = 6.5

    /** Where a piece is in the audio, in samples: [start, end). */
    data class Piece(val start: Int, val end: Int)

    /** A line of the transcript: where it is in the take, in seconds, and what was said. */
    data class Line(val start: Double, val end: Double, val text: String)

    fun maxTokens(samples: Int): Int = minOf(MAX_POSITIONS - 1, maxOf(8, ceil(samples.toDouble() / RATE * TOKENS_PER_SECOND).toInt()))

    /**
     * The audio cut into pieces of at most [MAX_PIECE] seconds at the quietest 30 ms frame between [MIN_CUT]
     * and [MAX_PIECE] seconds into each, trimmed to where there's sound (30 dB under the piece's loudest
     * frame, with a frame of margin); silent pieces are left out.
     */
    fun pieces(x: FloatArray, rate: Int = RATE): List<Piece> {
        val frame = (rate * FRAME).toInt()
        val frames = (x.size + frame - 1) / frame
        if (frames == 0) return emptyList()
        val rms = DoubleArray(frames) { f ->
            val a = f * frame; val b = minOf(x.size, a + frame)
            var s = 0.0
            for (i in a until b) s += x[i].toDouble() * x[i]
            sqrt(s / (b - a))
        }
        val maxFrames = (MAX_PIECE / FRAME).toInt()
        val minFrames = (MIN_CUT / FRAME).toInt()
        val cuts = ArrayList<Int>()
        var at = 0
        while (frames - at > maxFrames) {
            var best = at + minFrames
            for (f in at + minFrames until at + maxFrames) if (rms[f] <= rms[best]) best = f
            cuts += best
            at = best + 1
        }
        val out = ArrayList<Piece>()
        var from = 0
        for (end in cuts + frames) {
            var peak = 0.0
            for (f in from until end) if (rms[f] > peak) peak = rms[f]
            if (peak >= SILENCE) {
                val floor = maxOf(SILENCE, peak * 0.0316)
                var a = from; while (rms[a] < floor) a++
                var b = end - 1; while (rms[b] < floor) b--
                a = maxOf(from, a - 1); b = minOf(end - 1, b + 1)
                out += Piece(a * frame, minOf(x.size, (b + 1) * frame))
            }
            from = end
        }
        return out
    }
}

/** Moonshine's tokenizer, decoding only: SentencePiece-style BPE pieces with ▁ for spaces and byte fallback. */
class SpeechTokenizer(private val pieces: Array<String?>) {
    fun decode(ids: List<Int>): String {
        val bytes = java.io.ByteArrayOutputStream()
        for (id in ids) {
            if (id <= 2 || id >= pieces.size) continue      // <unk> <s> </s> and the added special tokens
            val p = pieces[id] ?: continue
            if (p.length == 6 && p.startsWith("<0x") && p.endsWith(">")) bytes.write(p.substring(3, 5).toInt(16))
            else bytes.write(p.replace('▁', ' ').toByteArray(Charsets.UTF_8))
        }
        val s = bytes.toString(Charsets.UTF_8)
        return if (s.startsWith(" ")) s.substring(1) else s
    }

    companion object {
        /** Reads the `model.vocab` object of a Hugging Face `tokenizer.json`. */
        fun fromJson(json: String): SpeechTokenizer {
            val key = json.indexOf("\"vocab\"", json.indexOf("\"model\"").coerceAtLeast(0))
            require(key >= 0) { "no vocab in the tokenizer" }
            var i = json.indexOf('{', key) + 1
            val map = HashMap<Int, String>()
            fun ws() { while (json[i].isWhitespace()) i++ }
            while (true) {
                ws()
                if (json[i] == '}') break
                require(json[i] == '"') { "bad vocab at $i" }
                i++
                val sb = StringBuilder()
                while (json[i] != '"') {
                    val c = json[i++]
                    if (c != '\\') { sb.append(c); continue }
                    when (val e = json[i++]) {
                        'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r'); 'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                        'u' -> { sb.append(json.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                        else -> sb.append(e)
                    }
                }
                i++
                ws(); require(json[i] == ':'); i++; ws()
                val start = i
                while (json[i].isDigit()) i++
                map[json.substring(start, i).toInt()] = sb.toString()
                ws()
                if (json[i] == ',') i++
            }
            val size = (map.keys.maxOrNull() ?: -1) + 1
            return SpeechTokenizer(Array(size) { map[it] })
        }
    }
}

/**
 * Moonshine Base on ONNX Runtime: the encoder once per piece, then the merged decoder one token at a time
 * with its key/value cache (Hugging Face Optimum's merged layout: a first pass without the cache, then
 * `use_cache_branch`), greedy. Not thread-safe; one per job.
 */
class Moonshine(encoder: ByteArray, decoder: ByteArray, threads: Int = 4) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val opts = OrtSession.SessionOptions().apply { setIntraOpNumThreads(threads) }
    private val enc = env.createSession(encoder, opts)
    private val dec = env.createSession(decoder, opts)
    private val layers = dec.inputNames.count { it.startsWith("past_key_values.") && it.endsWith(".decoder.key") }
    private val pastShape = (dec.inputInfo["past_key_values.0.decoder.key"]?.info as? ai.onnxruntime.TensorInfo)?.shape
    private val heads = pastShape?.getOrNull(1) ?: 8L
    private val headDim = pastShape?.getOrNull(3) ?: 52L

    init {
        require(enc.inputNames == setOf("input_values")) { "unexpected encoder inputs ${enc.inputNames}" }
        require(layers > 0 && "use_cache_branch" in dec.inputNames && "logits" in dec.outputNames) { "unexpected decoder ${dec.inputNames}" }
    }

    /** The tokens read from one piece of 16 kHz audio, without the start and end tokens. */
    fun read(audio: FloatArray, maxTokens: Int = Speech.maxTokens(audio.size)): List<Int> {
        val tokens = ArrayList<Int>()
        OnnxTensor.createTensor(env, FloatBuffer.wrap(audio), longArrayOf(1, audio.size.toLong())).use { x ->
            enc.run(mapOf("input_values" to x)).use { encoded ->
                val hidden = encoded.get("last_hidden_state").get() as OnnxTensor
                val empty = OnnxTensor.createTensor(env, FloatBuffer.allocate(0), longArrayOf(1, heads, 0, headDim))
                val noCache = OnnxTensor.createTensor(env, booleanArrayOf(false))
                val withCache = OnnxTensor.createTensor(env, booleanArrayOf(true))
                var first: OrtSession.Result? = null
                var last: OrtSession.Result? = null
                try {
                    var token = 1L      // decoder_start_token_id
                    while (tokens.size < maxTokens) {
                        val ids = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(token)), longArrayOf(1, 1))
                        val feeds = HashMap<String, OnnxTensor>()
                        feeds["input_ids"] = ids
                        feeds["encoder_hidden_states"] = hidden
                        for (l in 0 until layers) for (kind in listOf("decoder", "encoder")) for (kv in listOf("key", "value")) {
                            val from = if (kind == "decoder") last else first
                            feeds["past_key_values.$l.$kind.$kv"] = (from?.get("present.$l.$kind.$kv")?.get() as? OnnxTensor) ?: empty
                        }
                        feeds["use_cache_branch"] = if (first == null) noCache else withCache
                        val result = try { dec.run(feeds) } finally { ids.close() }
                        val logits = (result.get("logits").get() as OnnxTensor).floatBuffer
                        val vocab = (result.get("logits").get() as OnnxTensor).info.shape.last().toInt()
                        val base = logits.limit() - vocab
                        var best = 0; var bestV = Float.NEGATIVE_INFINITY
                        for (k in 0 until vocab) { val v = logits.get(base + k); if (v > bestV) { bestV = v; best = k } }
                        // The encoder's cache comes from the first pass and is kept; the decoder's grows each step.
                        if (first == null) first = result else if (last !== first) last?.close()
                        last = result
                        if (best == EOS) break
                        tokens += best
                        token = best.toLong()
                    }
                } finally {
                    if (last !== first) last?.close()
                    first?.close()
                    empty.close(); noCache.close(); withCache.close()
                }
            }
        }
        return tokens
    }

    /** A whole memo at 16 kHz: its pieces, read in order, as time-stamped lines (empty readings left out). */
    fun transcribe(x: FloatArray, tokenizer: SpeechTokenizer, progress: (done: Int, total: Int) -> Unit = { _, _ -> }): List<Speech.Line> {
        val pieces = Speech.pieces(x)
        progress(0, pieces.size)
        return pieces.mapIndexedNotNull { i, p ->
            val text = tokenizer.decode(read(x.copyOfRange(p.start, p.end))).trim()
            progress(i + 1, pieces.size)
            if (text.isEmpty()) null else Speech.Line(p.start.toDouble() / Speech.RATE, p.end.toDouble() / Speech.RATE, text)
        }
    }

    override fun close() {
        dec.close(); enc.close(); opts.close()
    }

    companion object {
        const val EOS = 2
    }
}

/**
 * The stored form of a transcript, tab-separated lines like the app's other derived text:
 * `speech  <model>`, an optional `edited`, then `line  <start s>  <end s>  <text>` per line.
 */
object TranscriptText {
    fun encode(model: String, lines: List<Speech.Line>, edited: Boolean = false): String = buildString {
        append("speech\t").append(model).append('\n')
        if (edited) append("edited\n")
        for (l in lines) append("line\t").append("%.3f".format(java.util.Locale.ROOT, l.start)).append('\t').append("%.3f".format(java.util.Locale.ROOT, l.end)).append('\t').append(l.text.replace(Regex("[\\t\\r\\n]+"), " ")).append('\n')
    }

    fun decode(s: String?): List<Speech.Line>? {
        if (s == null || !s.startsWith("speech\t")) return null
        return s.lineSequence().filter { it.startsWith("line\t") }.mapNotNull { l ->
            val p = l.split('\t', limit = 4)
            if (p.size < 4) null else Speech.Line(p[1].toDoubleOrNull() ?: return@mapNotNull null, p[2].toDoubleOrNull() ?: return@mapNotNull null, p[3])
        }.toList()
    }

    fun edited(s: String?): Boolean = s?.lineSequence()?.any { it == "edited" } == true
    fun model(s: String?): String? = s?.lineSequence()?.firstOrNull()?.takeIf { it.startsWith("speech\t") }?.substringAfter('\t')
}
