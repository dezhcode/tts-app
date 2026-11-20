package com.example.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.Random
import kotlin.math.sqrt

/**
 * ONNX Runtime inference engine (`com.microsoft.onnxruntime:onnxruntime-android`)
 * for Pocket-TTS / Cilla ONNX-exported bundles (`text_conditioner.onnx`, `flow_lm_main*.onnx`,
 * `flow_lm_flow*.onnx`, `mimi_decoder*.onnx`, optional `mimi_encoder*.onnx`).
 */
class OnnxPocketTtsEngine private constructor(
    private val env: OrtEnvironment,
    private val textConditioner: OrtSession?,
    private val flowLmMain: OrtSession,
    private val flowLmFlow: OrtSession?,
    private val mimiDecoder: OrtSession,
    private val mimiEncoder: OrtSession?,
    private val tokenizer: SentencePieceTokenizer,
    private val config: PocketTtsConfig,
    override val diagnostics: EngineDiagnostics,
) : TtsEngine {

    override suspend fun encodeVoicePrompt(
        id: String,
        label: String,
        audio24k: FloatArray?,
        stateFile: File?,
    ): VoiceEmbedding = withContext(Dispatchers.Default) {
        val samples = audio24k ?: return@withContext VoiceEmbedding(id, label, emptyList())
        if (mimiEncoder != null && samples.isNotEmpty()) {
            val inputName = mimiEncoder.inputNames.firstOrNull() ?: "audio"
            val shape = longArrayOf(1L, 1L, samples.size.toLong())
            OnnxTensor.createTensor(env, FloatBuffer.wrap(samples), shape).use { tensor ->
                mimiEncoder.run(mapOf(inputName to tensor)).use { result ->
                    val outVal = result[0].value
                    if (outVal is Array<*>) {
                        val frames = extractFramesFromOnnxOutput(outVal)
                        return@withContext VoiceEmbedding(id, label, frames)
                    }
                }
            }
        }
        VoiceEmbedding(id, label, emptyList())
    }

    override suspend fun synthesize(
        normalizedText: String,
        voice: VoiceEmbedding?,
        temperature: Float,
        flowSteps: Int,
        sentencePauseMs: Int,
        trimSilence: Boolean,
        onProgress: (sentenceIndex: Int, totalSentences: Int, frameCount: Int) -> Unit,
    ): SynthesisResult = withContext(Dispatchers.Default) {
        val t0 = System.nanoTime()
        val sentences = PersianTextNormalizer.splitSentences(normalizedText)
        require(sentences.isNotEmpty()) { "متن قابل پردازش یافت نشد." }

        val segments = ArrayList<FloatArray>(sentences.size)
        for ((idx, sentence) in sentences.withIndex()) {
            val tokenIds = tokenizer.encode(sentence)
            val audio = runOnnxSentence(
                tokenIds = tokenIds,
                voice = voice,
                temperature = temperature,
                flowSteps = flowSteps,
                onFrame = { f -> onProgress(idx + 1, sentences.size, f) },
            )
            val clean = if (trimSilence) {
                com.example.audio.WavAudioUtils.trimSilence(audio, config.sampleRate)
            } else {
                audio
            }
            segments.add(clean)
        }

        val combined = com.example.audio.WavAudioUtils.stitchSegmentsWithPause(
            segments = segments,
            sampleRate = config.sampleRate,
            pauseMs = sentencePauseMs,
        )
        val elapsedSec = (System.nanoTime() - t0) / 1_000_000_000f
        val durationSec = combined.size.toFloat() / config.sampleRate.toFloat()

        SynthesisResult(
            pcmSamples24k = combined,
            sampleRate = config.sampleRate,
            durationSeconds = durationSec,
            inferenceSeconds = elapsedSec,
            normalizedText = normalizedText,
            sentenceCount = sentences.size,
        )
    }

    private fun runOnnxSentence(
        tokenIds: IntArray,
        voice: VoiceEmbedding?,
        temperature: Float,
        flowSteps: Int,
        onFrame: (Int) -> Unit,
    ): FloatArray {
        val longIds = LongArray(tokenIds.size) { tokenIds[it].toLong() }
        var textEmbeddings: FloatArray? = null
        var textSeqLen = tokenIds.size
        var embDim = config.dModel

        if (textConditioner != null) {
            val inputName = textConditioner.inputNames.firstOrNull() ?: "token_ids"
            val shape = longArrayOf(1L, longIds.size.toLong())
            OnnxTensor.createTensor(env, LongBuffer.wrap(longIds), shape).use { t ->
                textConditioner.run(mapOf(inputName to t)).use { res ->
                    val arr = flattenOnnxFloatArray(res[0].value)
                    if (arr.isNotEmpty()) {
                        textEmbeddings = arr
                        embDim = arr.size / tokenIds.size.coerceAtLeast(1)
                    }
                }
            }
        }

        val ldim = config.latentDim
        val voiceSeed = voice?.id?.hashCode()?.toLong() ?: 0L
        val rng = Random(42L + tokenIds.sum().toLong() + voiceSeed)
        val maxFrames = (tokenIds.size * 6 + 24).coerceIn(24, 200)
        val latents = ArrayList<FloatArray>(maxFrames)
        var currentLatent = FloatArray(ldim) { Float.NaN }
        val stateTensors = mutableMapOf<String, OnnxTensor>()

        try {
            val mainInputs = flowLmMain.inputNames.toList()
            for (step in 0 until maxFrames) {
                onFrame(step + 1)
                val feeds = mutableMapOf<String, OnnxTensor>()
                val seqTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(currentLatent), longArrayOf(1L, 1L, ldim.toLong()))
                val textTensor = textEmbeddings?.let {
                    OnnxTensor.createTensor(env, FloatBuffer.wrap(it), longArrayOf(1L, textSeqLen.toLong(), embDim.toLong()))
                }

                for (name in mainInputs) {
                    when {
                        name.contains("sequence", ignoreCase = true) || name == "x" -> feeds[name] = seqTensor
                        name.contains("text", ignoreCase = true) && textTensor != null -> feeds[name] = textTensor
                        stateTensors.containsKey(name) -> feeds[name] = stateTensors[name]!!
                    }
                }

                var condVec = FloatArray(embDim)
                var eosLogit = -10f

                if (feeds.isNotEmpty()) {
                    flowLmMain.run(feeds).use { res ->
                        for (i in 0 until res.size()) {
                            val outName = flowLmMain.outputNames.elementAtOrNull(i) ?: continue
                            val flat = flattenOnnxFloatArray(res[i].value)
                            when {
                                outName.contains("eos", ignoreCase = true) -> eosLogit = flat.firstOrNull() ?: -10f
                                outName.contains("cond", ignoreCase = true) || i == 0 -> {
                                    if (flat.isNotEmpty()) condVec = flat
                                }
                            }
                        }
                    }
                }
                seqTensor.close()
                textTensor?.close()

                val nextLatent = if (flowLmFlow != null) {
                    runFlowMatchingStep(flowLmFlow, condVec, ldim, temperature, flowSteps, rng)
                } else {
                    FloatArray(ldim) { idx -> condVec.getOrElse(idx) { 0f } }
                }
                latents.add(nextLatent)
                currentLatent = nextLatent

                if (step > 8 && eosLogit > 0f) break
            }
        } finally {
            stateTensors.values.forEach { it.close() }
        }

        // Decode with Mimi Decoder ONNX
        if (latents.isEmpty()) return FloatArray(0)
        val stacked = FloatArray(latents.size * ldim)
        for (f in latents.indices) {
            System.arraycopy(latents[f], 0, stacked, f * ldim, ldim)
        }
        val decInputName = mimiDecoder.inputNames.firstOrNull() ?: "latent"
        val decShape = longArrayOf(1L, latents.size.toLong(), ldim.toLong())
        OnnxTensor.createTensor(env, FloatBuffer.wrap(stacked), decShape).use { latTensor ->
            mimiDecoder.run(mapOf(decInputName to latTensor)).use { res ->
                return flattenOnnxFloatArray(res[0].value)
            }
        }
    }

    private fun runFlowMatchingStep(
        flowSession: OrtSession,
        condVec: FloatArray,
        ldim: Int,
        temperature: Float,
        steps: Int,
        rng: Random,
    ): FloatArray {
        val x = TensorOps.sampleGaussian(rng, ldim, sqrt(temperature.coerceIn(0.05f, 1.5f)))
        val dt = 1f / steps.coerceAtLeast(1)
        val inputNames = flowSession.inputNames.toList()

        for (s in 0 until steps.coerceAtLeast(1)) {
            val tVal = s.toFloat() * dt
            val feeds = mutableMapOf<String, OnnxTensor>()
            val cTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(condVec), longArrayOf(1L, condVec.size.toLong()))
            val xTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(x), longArrayOf(1L, ldim.toLong()))
            val sTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(s.toFloat())), longArrayOf(1L, 1L))
            val tTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(tVal)), longArrayOf(1L, 1L))

            for (name in inputNames) {
                when {
                    name.contains("c", ignoreCase = true) && name.length <= 4 -> feeds[name] = cTensor
                    name == "x" || name.contains("latent", ignoreCase = true) -> feeds[name] = xTensor
                    name == "s" -> feeds[name] = sTensor
                    name == "t" -> feeds[name] = tTensor
                }
            }

            if (feeds.isNotEmpty()) {
                flowSession.run(feeds).use { res ->
                    val vel = flattenOnnxFloatArray(res[0].value)
                    for (i in 0 until minOf(ldim, vel.size)) {
                        x[i] += vel[i] * dt
                    }
                }
            }
            cTensor.close()
            xTensor.close()
            sTensor.close()
            tTensor.close()
        }
        return x
    }

    private fun flattenOnnxFloatArray(obj: Any?): FloatArray {
        val out = mutableListOf<Float>()
        fun walk(item: Any?) {
            when (item) {
                is FloatArray -> for (v in item) out.add(v)
                is Array<*> -> for (child in item) walk(child)
                is Float -> out.add(item)
            }
        }
        walk(obj)
        return out.toFloatArray()
    }

    private fun extractFramesFromOnnxOutput(obj: Array<*>): List<FloatArray> {
        val flat = flattenOnnxFloatArray(obj)
        val ldim = config.latentDim.coerceAtLeast(1)
        val count = flat.size / ldim
        return (0 until count).map { idx ->
            flat.copyOfRange(idx * ldim, (idx + 1) * ldim)
        }
    }

    override fun close() {
        runCatching { textConditioner?.close() }
        runCatching { flowLmMain.close() }
        runCatching { flowLmFlow?.close() }
        runCatching { mimiDecoder.close() }
        runCatching { mimiEncoder?.close() }
    }

    companion object {
        fun load(onnxDir: File, tokenizerFile: File, configFile: File?): OnnxPocketTtsEngine {
            val env = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 4))
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }

            fun findOnnx(vararg prefixes: String): File? {
                val files = onnxDir.listFiles()?.filter { it.extension.equals("onnx", ignoreCase = true) } ?: return null
                for (prefix in prefixes) {
                    files.firstOrNull { it.name.startsWith(prefix, ignoreCase = true) }?.let { return it }
                }
                return null
            }

            val textFile = findOnnx("text_conditioner")
            val mainFile = findOnnx("flow_lm_main") ?: error("flow_lm_main.onnx یافت نشد")
            val flowFile = findOnnx("flow_lm_flow")
            val decFile = findOnnx("mimi_decoder") ?: error("mimi_decoder.onnx یافت نشد")
            val encFile = findOnnx("mimi_encoder")

            val tokenizer = SentencePieceTokenizer.load(tokenizerFile)
            val config = PocketTtsConfig.load(configFile, null)
            val totalBytes = listOfNotNull(textFile, mainFile, flowFile, decFile, encFile).sumOf { it.length() }

            val diagnostics = EngineDiagnostics(
                engineName = "ONNX Runtime (${OrtEnvironment.getEnvironment().version})",
                totalParameters = totalBytes / 4L,
                tensorCount = listOfNotNull(textFile, mainFile, flowFile, decFile, encFile).size,
                layerCount = config.numLayers,
                dModel = config.dModel,
                latentDim = config.latentDim,
                sampleRate = config.sampleRate,
                vocabSize = tokenizer.vocabSize,
                modelSizeMb = totalBytes / (1024f * 1024f),
            )

            return OnnxPocketTtsEngine(
                env = env,
                textConditioner = textFile?.let { env.createSession(it.absolutePath, opts) },
                flowLmMain = env.createSession(mainFile.absolutePath, opts),
                flowLmFlow = flowFile?.let { env.createSession(it.absolutePath, opts) },
                mimiDecoder = env.createSession(decFile.absolutePath, opts),
                mimiEncoder = encFile?.let { env.createSession(it.absolutePath, opts) },
                tokenizer = tokenizer,
                config = config,
                diagnostics = diagnostics,
            )
        }
    }
}
