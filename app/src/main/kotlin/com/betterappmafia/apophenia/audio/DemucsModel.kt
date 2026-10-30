package com.betterappmafia.apophenia.audio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.betterappmafia.apophenia.Breadcrumbs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.security.MessageDigest

/**
 * The optional HT-Demucs stems model (Christopher, 30 Oct 2026: "the clean way", with a link "so it can be as easy as
 * possible"). Apophenia never ships or downloads the weights: the link opens the browser on the file, the user picks the
 * download, and it is copied into the app's storage only if it is byte for byte the pinned file.
 *
 * [Claude] The file is ferrite-stems/htdemucs-onnx-dml (MIT, Meta's htdemucs weights): the STFT is a convolution and
 * there's no ScatterND, so it loads in seconds and runs in about 1 GB with constant folding off (measured on this PC,
 * 30 Oct 2026). On 40 s of a song it matches demucs's own `apply_model` to below −120 dB through [com.betterappmafia.apophenia.core.analysis.Demucs].
 */
class DemucsModel(private val context: Context) {
    sealed interface State {
        data object Missing : State
        data class Adding(val percent: Int) : State
        data object Added : State
    }

    val file = File(context.filesDir, "models/$FILE_NAME")
    private val _state = MutableStateFlow<State>(if (file.isFile) State.Added else State.Missing)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Copies the picked file in if it is the pinned one; returns why not otherwise. Call off the main thread. */
    fun add(uri: Uri): String? {
        if (_state.value is State.Adding) return "It's already being added."
        val resolver = context.contentResolver
        val size = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }
        }.getOrNull()
        if (size != null && size != SIZE) return "That isn't the Demucs file: it should be $FILE_NAME, $MB MB."
        val part = File(file.path + ".part")
        _state.value = State.Adding(0)
        try {
            file.parentFile?.mkdirs()
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            (resolver.openInputStream(uri) ?: return "The file couldn't be opened.").use { input ->
                part.outputStream().buffered(1 shl 20).use { out ->
                    val buf = ByteArray(1 shl 20)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        copied += n
                        if (copied > SIZE) return "That isn't the Demucs file: it's bigger than $FILE_NAME."
                        digest.update(buf, 0, n); out.write(buf, 0, n)
                        _state.value = State.Adding((copied * 100 / SIZE).toInt())
                    }
                }
            }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            if (copied != SIZE || sha != SHA256) return if (copied < SIZE) "The file is incomplete (${copied / 1_000_000} of $MB MB): let the download finish, then pick it again."
                else "That file isn't the one Apophenia checks for (its SHA-256 differs), so it wasn't added."
            if (!part.renameTo(file)) return "It couldn't be saved in the app's storage."
            Breadcrumbs.add("demucs model added")
            return null
        } catch (t: Throwable) {
            return "It couldn't be copied: ${t.message ?: t.javaClass.simpleName}"
        } finally {
            part.delete()
            _state.value = if (file.isFile) State.Added else State.Missing
        }
    }

    fun remove() {
        file.delete()
        _state.value = State.Missing
    }

    companion object {
        const val FILE_NAME = "htdemucs-dml.onnx"
        const val SIZE = 371_576_554L
        /** Its size in MB as the browser and the file picker round it (372). */
        const val MB = ((SIZE + 500_000) / 1_000_000).toInt()
        const val SHA256 = "9e06fef2b23e0f772c6a0cedc4da3aeacb066fa2fa7b6bf70ba08bb1db6220ab"
        /** The file at a pinned revision: opening it in the browser downloads it. */
        const val DOWNLOAD_URL = "https://huggingface.co/ferrite-stems/htdemucs-onnx-dml/resolve/9061a3ab29c7e4b0025d4970c1077b6d5c2f951b/htdemucs-dml.onnx?download=true"
        /** Its model card, for anyone who wants to see where it comes from. */
        const val PAGE_URL = "https://huggingface.co/ferrite-stems/htdemucs-onnx-dml"
    }
}
