package com.example.tts

import android.content.Context
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Runs the actual Cilla Mini Phonon WASM runtime in an offline Android WebView. */
class PhononPocketTtsEngine private constructor(
    private val webView: WebView,
    private val bridge: Bridge,
) : TtsEngine {
    override val diagnostics = EngineDiagnostics(
        engineName = "Cilla Mini / Phonon WASM", totalParameters = 18_200_000,
        tensorCount = 0, layerCount = 8, dModel = 0, latentDim = 64,
        sampleRate = 24_000, vocabSize = 0, modelSizeMb = 107f,
    )

    override suspend fun encodeVoicePrompt(
        id: String, label: String, audio24k: FloatArray?, stateFile: File?,
    ): VoiceEmbedding {
        // Phonon's browser runtime supports precomputed voice states only.
        val voice = when {
            id.contains("voice2", true) || label.contains("مرد") -> "voice2"
            id.contains("voice1", true) || label.contains("زن") -> "voice1"
            else -> "parisa"
        }
        return VoiceEmbedding(voice, label, emptyList())
    }

    override suspend fun synthesize(
        normalizedText: String, voice: VoiceEmbedding?, temperature: Float,
        flowSteps: Int, sentencePauseMs: Int, trimSilence: Boolean,
        onProgress: (Int, Int, Int) -> Unit,
    ): SynthesisResult {
        val started = System.nanoTime()
        val result = CompletableDeferred<FloatArray>()
        bridge.pending = result
        val js = "window.speak(" +
            JSONObject.quote(normalizedText) + "," +
            JSONObject.quote(voice?.id?.takeIf { it in setOf("parisa", "voice1", "voice2") } ?: "parisa") +
            "," + temperature.coerceIn(0.1f, 1.5f) + ")"
        withContext(Dispatchers.Main) { webView.evaluateJavascript(js, null) }
        val pcm = result.await()
        val seconds = (System.nanoTime() - started) / 1_000_000_000f
        return SynthesisResult(pcm, 24_000, pcm.size / 24_000f, seconds, normalizedText, 1)
    }

    override fun close() {
        webView.post { webView.destroy() }
    }

    class Bridge {
        val initialized = CompletableDeferred<Unit>()
        @Volatile var pending: CompletableDeferred<FloatArray>? = null

        @JavascriptInterface fun ready() { initialized.complete(Unit) }
        @JavascriptInterface fun failed(message: String) {
            val error = IllegalStateException("Phonon: $message")
            if (!initialized.isCompleted) initialized.completeExceptionally(error)
            pending?.completeExceptionally(error)
            pending = null
        }
        @JavascriptInterface fun audio(encoded: String) {
            try {
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                require(bytes.size % 4 == 0) { "Invalid Phonon float PCM" }
                val floats = FloatArray(bytes.size / 4)
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floats)
                pending?.complete(floats)
            } catch (e: Exception) {
                pending?.completeExceptionally(e)
            } finally { pending = null }
        }
    }

    companion object {
        suspend fun load(context: Context): PhononPocketTtsEngine {
            val bridge = Bridge()
            val view = withContext(Dispatchers.Main) {
                val loader = WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                    .build()
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?, request: android.webkit.WebResourceRequest?,
                        ): android.webkit.WebResourceResponse? {
                            return request?.url?.let(loader::shouldInterceptRequest)
                        }
                    }
                    addJavascriptInterface(bridge, "NativeBridge")
                    loadUrl("https://appassets.androidplatform.net/assets/phonon-bridge/index.html")
                }
            }
            try {
                withTimeout(120_000) { bridge.initialized.await() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { view.destroy() }
                throw e
            }
            return PhononPocketTtsEngine(view, bridge)
        }
    }
}
