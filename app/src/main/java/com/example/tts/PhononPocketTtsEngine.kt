package com.example.tts

import android.content.Context
import android.util.Base64
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.example.audio.WavAudioUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Runs the real Cilla Mini Phonon WebAssembly runtime completely offline inside an Android WebView
 * served via `WebViewAssetLoader` (supporting both bundled APK assets and local private storage).
 */
class PhononPocketTtsEngine private constructor(
    private val webView: WebView,
    private val bridge: Bridge,
    val verifiedVoiceIds: Set<String>,
    val defaultVoiceId: String,
    val sampleRate: Int,
    modelSizeMb: Float,
) : TtsEngine {

    private val synthMutex = Mutex()

    override val diagnostics = EngineDiagnostics(
        engineName = "Cilla Mini / Phonon WASM",
        totalParameters = 18_200_000,
        tensorCount = 0,
        layerCount = 8,
        dModel = 0,
        latentDim = 64,
        sampleRate = sampleRate,
        vocabSize = 0,
        modelSizeMb = modelSizeMb,
    )

    override suspend fun encodeVoicePrompt(
        id: String,
        label: String,
        audio24k: FloatArray?,
        stateFile: File?,
    ): VoiceEmbedding {
        // Phonon's browser/WASM runtime uses verified precomputed `.safetensors` voice states only.
        val resolvedId = when {
            id in verifiedVoiceIds -> id
            defaultVoiceId in verifiedVoiceIds -> defaultVoiceId
            else -> verifiedVoiceIds.firstOrNull() ?: DEFAULT_VOICE_ID
        }
        return VoiceEmbedding(
            id = resolvedId,
            label = label.ifBlank { resolvedId },
            latentFrames = emptyList(),
        )
    }

    override suspend fun synthesize(
        normalizedText: String,
        voice: VoiceEmbedding?,
        temperature: Float,
        flowSteps: Int,
        sentencePauseMs: Int,
        trimSilence: Boolean,
        onProgress: (sentenceIndex: Int, totalSentences: Int, frameCount: Int) -> Unit,
    ): SynthesisResult = synthMutex.withLock {
        val started = System.nanoTime()
        val voiceId = voice?.id?.takeIf { it in verifiedVoiceIds } ?: defaultVoiceId
        val clampedTemp = temperature.coerceIn(0.1f, 1.5f)

        val sentences = PersianTextNormalizer.splitSentences(normalizedText, maxChunkChars = 140)
            .ifEmpty { listOf(normalizedText.trim()).filter { it.isNotEmpty() } }

        val segments = mutableListOf<FloatArray>()
        var accumulatedFrames = 0

        for ((index, sentence) in sentences.withIndex()) {
            onProgress(index + 1, sentences.size, accumulatedFrames)

            val deferred = CompletableDeferred<FloatArray>()
            bridge.pending = deferred
            val js = "window.speak(" +
                JSONObject.quote(sentence) + "," +
                JSONObject.quote(voiceId) + "," +
                clampedTemp + ")"
            withContext(Dispatchers.Main) {
                webView.evaluateJavascript(js, null)
            }

            val rawPcm = withTimeout(180_000) { deferred.await() }
            val processed = if (trimSilence && rawPcm.isNotEmpty()) {
                WavAudioUtils.trimSilence(rawPcm, sampleRate)
            } else {
                rawPcm
            }
            if (processed.isNotEmpty()) {
                segments.add(processed)
                accumulatedFrames += (processed.size / 1920).coerceAtLeast(1)
            }
            onProgress(index + 1, sentences.size, accumulatedFrames)
        }

        val combinedPcm = WavAudioUtils.stitchSegmentsWithPause(
            segments = segments,
            sampleRate = sampleRate,
            pauseMs = sentencePauseMs,
        )
        val seconds = (System.nanoTime() - started) / 1_000_000_000f
        SynthesisResult(
            pcmSamples24k = combinedPcm,
            sampleRate = sampleRate,
            durationSeconds = if (sampleRate > 0) combinedPcm.size / sampleRate.toFloat() else 0f,
            inferenceSeconds = seconds,
            normalizedText = normalizedText,
            sentenceCount = sentences.size,
        )
    }

    override fun close() {
        webView.post {
            runCatching { webView.evaluateJavascript("window.disposePhonon && window.disposePhonon();", null) }
            webView.destroy()
        }
    }

    data class ReadyPayload(
        val sampleRate: Int,
        val voices: Set<String>,
    )

    class Bridge {
        val bridgeLoaded = CompletableDeferred<Unit>()
        val initialized = CompletableDeferred<ReadyPayload>()
        @Volatile
        var pending: CompletableDeferred<FloatArray>? = null

        @JavascriptInterface
        fun bridgeLoaded() {
            if (!bridgeLoaded.isCompleted) bridgeLoaded.complete(Unit)
        }

        @JavascriptInterface
        fun ready(payloadJson: String?) {
            if (initialized.isCompleted) return
            val parsed = runCatching {
                val obj = JSONObject(payloadJson ?: "{}")
                val sr = obj.optInt("sampleRate", 24_000).takeIf { it > 0 } ?: 24_000
                val arr = obj.optJSONArray("voices")
                val voices = buildSet {
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val name = arr.optString(i).trim()
                            if (name.isNotEmpty()) add(name)
                        }
                    }
                }
                ReadyPayload(sampleRate = sr, voices = voices)
            }.getOrDefault(ReadyPayload(sampleRate = 24_000, voices = emptySet()))
            initialized.complete(parsed)
        }

        @JavascriptInterface
        fun failed(message: String) {
            val error = IllegalStateException("Phonon: $message")
            if (!bridgeLoaded.isCompleted) bridgeLoaded.completeExceptionally(error)
            if (!initialized.isCompleted) initialized.completeExceptionally(error)
            pending?.completeExceptionally(error)
            pending = null
        }

        @JavascriptInterface
        fun audio(encoded: String) {
            try {
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                require(bytes.size % 4 == 0) { "Invalid Phonon float PCM" }
                val floats = FloatArray(bytes.size / 4)
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floats)
                pending?.complete(floats)
            } catch (e: Exception) {
                pending?.completeExceptionally(e)
            } finally {
                pending = null
            }
        }
    }

    companion object {
        const val DEFAULT_VOICE_ID = "parisa"
        private const val BRIDGE_ORIGIN = "https://appassets.androidplatform.net"
        private const val BRIDGE_URL = "$BRIDGE_ORIGIN/assets/phonon-bridge/index.html"

        suspend fun load(
            context: Context,
            modelBaseUrl: String,
            voicesMap: Map<String, String>,
            defaultVoiceId: String,
            localModelRootDir: File? = null,
            modelSizeMb: Float = 107f,
        ): PhononPocketTtsEngine {
            require(voicesMap.isNotEmpty()) { "حداقل یک فایل صدای Phonon (.safetensors) لازم است." }
            val resolvedDefault = defaultVoiceId.takeIf { it in voicesMap } ?: voicesMap.keys.first()
            val bridge = Bridge()

            val view = withContext(Dispatchers.Main) {
                val loaderBuilder = WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                if (localModelRootDir != null && localModelRootDir.exists()) {
                    loaderBuilder.addPathHandler(
                        "/local-model/",
                        WebViewAssetLoader.InternalStoragePathHandler(context, localModelRootDir),
                    )
                }
                val loader = loaderBuilder.build()

                WebView(context.applicationContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean = true
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): WebResourceResponse? {
                            val uri = request?.url ?: return null
                            // Enforce strict offline execution: only serve local appassets origin
                            if (uri.host != "appassets.androidplatform.net") {
                                return WebResourceResponse("text/plain", "utf-8", 403, "Offline Only", emptyMap(), null)
                            }
                            val response = loader.shouldInterceptRequest(uri) ?: return null
                            val path = uri.path.orEmpty().lowercase()
                            val normalizedMime = when {
                                path.endsWith(".wasm") -> "application/wasm"
                                path.endsWith(".js") || path.endsWith(".mjs") -> "application/javascript"
                                path.endsWith(".json") -> "application/json"
                                path.endsWith(".safetensors") -> "application/octet-stream"
                                else -> response.mimeType
                            }
                            if (!normalizedMime.isNullOrBlank() && normalizedMime != response.mimeType) {
                                response.mimeType = normalizedMime
                            }
                            return response
                        }
                    }
                    addJavascriptInterface(bridge, "NativeBridge")
                    loadUrl(BRIDGE_URL)
                }
            }

            try {
                withTimeout(30_000) { bridge.bridgeLoaded.await() }

                val initConfig = JSONObject().apply {
                    put("modelBase", modelBaseUrl)
                    put("defaultVoice", resolvedDefault)
                    put("voices", JSONObject(voicesMap))
                }
                val initJs = "window.initPhonon(${JSONObject.quote(initConfig.toString())});"
                withContext(Dispatchers.Main) {
                    view.evaluateJavascript(initJs, null)
                }

                val payload = withTimeout(120_000) { bridge.initialized.await() }
                val activeVoices = payload.voices.ifEmpty { voicesMap.keys }
                return PhononPocketTtsEngine(
                    webView = view,
                    bridge = bridge,
                    verifiedVoiceIds = activeVoices,
                    defaultVoiceId = resolvedDefault,
                    sampleRate = payload.sampleRate,
                    modelSizeMb = modelSizeMb,
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    runCatching { view.evaluateJavascript("window.disposePhonon && window.disposePhonon();", null) }
                    view.destroy()
                }
                throw e
            }
        }
    }
}
