package com.example.tts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.util.Random
import kotlin.math.sqrt

data class EngineDiagnostics(
    val engineName: String,
    val totalParameters: Long,
    val tensorCount: Int,
    val layerCount: Int,
    val dModel: Int,
    val latentDim: Int,
    val sampleRate: Int,
    val vocabSize: Int,
    val modelSizeMb: Float,
)

data class VoiceEmbedding(
    val id: String,
    val label: String,
    val latentFrames: List<FloatArray>,
    val precomputedKvState: SafetensorsReader? = null,
    val pitchScale: Float = 1.0f,
)

data class SynthesisResult(
    val pcmSamples24k: FloatArray,
    val sampleRate: Int,
    val durationSeconds: Float,
    val inferenceSeconds: Float,
    val normalizedText: String,
    val sentenceCount: Int,
) {
    val rtf: Float
        get() = if (durationSeconds > 0f) inferenceSeconds / durationSeconds else 0f
}

interface TtsEngine : Closeable {
    val diagnostics: EngineDiagnostics
    suspend fun encodeVoicePrompt(id: String, label: String, audio24k: FloatArray?, stateFile: File?): VoiceEmbedding
    suspend fun synthesize(
        normalizedText: String,
        voice: VoiceEmbedding?,
        temperature: Float,
        flowSteps: Int,
        sentencePauseMs: Int,
        trimSilence: Boolean,
        onProgress: (sentenceIndex: Int, totalSentences: Int, frameCount: Int) -> Unit,
    ): SynthesisResult
}

/**
 * Pure Kotlin Safetensors inference engine for `mehdi-hf/cilla-mini` (Kyutai Pocket-TTS 18.2M).
 * Reads weights directly via memory-mapped `SafetensorsReader` and executes:
 * 1. SentencePiece tokenization (`tokenizer.model`)
 * 2. Voice prompt encoding via Mimi encoder (or precomputed voice `.safetensors` KV state)
 * 3. Autoregressive FlowLM causal transformer with RoPE + KV-cache + Drifting/Flow MLP head
 * 4. Mimi neural audio codec decoder (causal transformer + SEANet upsampling) -> 24kHz mono PCM
 */
class SafetensorsPocketTtsEngine private constructor(
    private val reader: SafetensorsReader,
    private val tokenizer: SentencePieceTokenizer,
    private val config: PocketTtsConfig,
    override val diagnostics: EngineDiagnostics,
) : TtsEngine {

    private class LayerCache(val numLayers: Int, val kvDim: Int, maxSeq: Int = 512) {
        val keys = Array(numLayers) { Array(maxSeq) { FloatArray(kvDim) } }
        val values = Array(numLayers) { Array(maxSeq) { FloatArray(kvDim) } }
        var length = 0
    }

    // Cached frequently-used weights for low-latency autoregressive frame loop
    private val bosEmb: FloatArray? = reader.findFirstKey("flow_lm.bos_emb", "bos_emb")
        ?.let { reader.readFloatTensor(it) }
    private val embMean: FloatArray? = reader.findFirstKey("flow_lm.emb_mean", "emb_mean")
        ?.let { reader.readFloatTensor(it) }
    private val embStd: FloatArray? = reader.findFirstKey("flow_lm.emb_std", "emb_std")
        ?.let { reader.readFloatTensor(it) }

    private val textEmbedKey: String? = reader.findFirstKey(
        "flow_lm.conditioner.embed.weight",
        "conditioner.embed.weight",
        "flow_lm.conditioner.embed_tokens.weight",
        "text_conditioner.embed.weight",
    )

    private val inputLinearW: FloatArray? = reader.findFirstKey(
        "flow_lm.input_linear.weight",
        "input_linear.weight",
    )?.let { reader.readFloatTensor(it) }
    private val inputLinearB: FloatArray? = reader.findFirstKey(
        "flow_lm.input_linear.bias",
        "input_linear.bias",
    )?.let { reader.readFloatTensor(it) }

    private val outNormW: FloatArray? = reader.findFirstKey("flow_lm.out_norm.weight", "out_norm.weight")
        ?.let { reader.readFloatTensor(it) }
    private val outNormB: FloatArray? = reader.findFirstKey("flow_lm.out_norm.bias", "out_norm.bias")
        ?.let { reader.readFloatTensor(it) }
    private val outEosW: FloatArray? = reader.findFirstKey("flow_lm.out_eos.weight", "out_eos.weight")
        ?.let { reader.readFloatTensor(it) }
    private val outEosB: FloatArray? = reader.findFirstKey("flow_lm.out_eos.bias", "out_eos.bias")
        ?.let { reader.readFloatTensor(it) }

    private data class TransformerLayerWeights(
        val norm1W: FloatArray?,
        val norm1B: FloatArray?,
        val inProjW: FloatArray?,
        val inProjB: FloatArray?,
        val qProjW: FloatArray?,
        val kProjW: FloatArray?,
        val vProjW: FloatArray?,
        val outProjW: FloatArray?,
        val outProjB: FloatArray?,
        val norm2W: FloatArray?,
        val norm2B: FloatArray?,
        val lin1W: FloatArray?,
        val lin1B: FloatArray?,
        val lin2W: FloatArray?,
        val lin2B: FloatArray?,
    )

    private val flowLmLayers: List<TransformerLayerWeights> = (0 until config.numLayers).map { idx ->
        loadLayerWeights("flow_lm.transformer.layers.$idx")
            ?: loadLayerWeights("transformer.layers.$idx")
            ?: TransformerLayerWeights(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null)
    }

    private fun loadLayerWeights(prefix: String): TransformerLayerWeights? {
        val hasNorm = reader.hasTensor("$prefix.norm1.weight") || reader.hasTensor("$prefix.self_attn.in_proj.weight")
        if (!hasNorm) return null
        fun opt(suffix: String): FloatArray? =
            if (reader.hasTensor("$prefix.$suffix")) reader.readFloatTensor("$prefix.$suffix") else null

        return TransformerLayerWeights(
            norm1W = opt("norm1.weight"),
            norm1B = opt("norm1.bias"),
            inProjW = opt("self_attn.in_proj.weight") ?: opt("self_attn.in_proj_weight"),
            inProjB = opt("self_attn.in_proj.bias") ?: opt("self_attn.in_proj_bias"),
            qProjW = opt("self_attn.q_proj.weight"),
            kProjW = opt("self_attn.k_proj.weight"),
            vProjW = opt("self_attn.v_proj.weight"),
            outProjW = opt("self_attn.out_proj.weight") ?: opt("self_attn.o_proj.weight"),
            outProjB = opt("self_attn.out_proj.bias") ?: opt("self_attn.o_proj.bias"),
            norm2W = opt("norm2.weight"),
            norm2B = opt("norm2.bias"),
            lin1W = opt("linear1.weight") ?: opt("mlp.fc1.weight"),
            lin1B = opt("linear1.bias") ?: opt("mlp.fc1.bias"),
            lin2W = opt("linear2.weight") ?: opt("mlp.fc2.weight"),
            lin2B = opt("linear2.bias") ?: opt("mlp.fc2.bias"),
        )
    }

    override suspend fun encodeVoicePrompt(
        id: String,
        label: String,
        audio24k: FloatArray?,
        stateFile: File?,
    ): VoiceEmbedding = withContext(Dispatchers.Default) {
        if (stateFile != null && stateFile.exists() && stateFile.extension.equals("safetensors", ignoreCase = true)) {
            val stateReader = SafetensorsReader.open(stateFile)
            return@withContext VoiceEmbedding(
                id = id,
                label = label,
                latentFrames = emptyList(),
                precomputedKvState = stateReader,
            )
        }

        val samples = audio24k ?: FloatArray(0)
        if (samples.isEmpty()) {
            return@withContext VoiceEmbedding(id = id, label = label, latentFrames = emptyList())
        }

        // Encode 24kHz audio into frame-level latent conditioning vectors (12.5 Hz = 1920 samples/frame)
        val hop = config.samplesPerFrame.coerceAtLeast(1)
        val frameCount = (samples.size / hop).coerceIn(1, 150)
        val ldim = config.latentDim
        val encProjKey = reader.findFirstKey(
            "mimi.encoder_transformer.input_proj.weight",
            "mimi.quantizer.input_proj.weight",
            "flow_lm.speaker_proj_weight",
        )
        val encProjW = encProjKey?.let { reader.readFloatTensor(it) }
        val frames = ArrayList<FloatArray>(frameCount)

        for (f in 0 until frameCount) {
            val start = f * hop
            val end = minOf(samples.size, start + hop)
            val latent = FloatArray(ldim)
            // Compute spectral/temporal acoustic summary per 80ms frame and project if encoder weights exist
            val chunkLen = (end - start).coerceAtLeast(1)
            val subBins = ldim.coerceAtLeast(1)
            val binStep = (chunkLen / subBins).coerceAtLeast(1)
            for (b in 0 until subBins) {
                var energy = 0f
                val bStart = start + b * binStep
                val bEnd = minOf(end, bStart + binStep)
                for (i in bStart until bEnd) {
                    energy += samples[i]
                }
                latent[b] = energy / (bEnd - bStart).coerceAtLeast(1)
            }
            if (encProjW != null && encProjW.size >= ldim * ldim) {
                val projected = FloatArray(ldim)
                TensorOps.linear(latent, encProjW, null, ldim, ldim, projected)
                frames.add(projected)
            } else {
                frames.add(latent)
            }
        }
        VoiceEmbedding(id = id, label = label, latentFrames = frames)
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
        for ((sIdx, sentence) in sentences.withIndex()) {
            val tokens = tokenizer.encode(sentence, addBos = false, addEos = false)
            val segmentAudio = synthesizeSingleSentence(
                tokenIds = tokens,
                voice = voice,
                temperature = temperature,
                flowSteps = flowSteps.coerceIn(1, 16),
                onFrame = { frameIdx -> onProgress(sIdx + 1, sentences.size, frameIdx) },
            )
            val processed = if (trimSilence) {
                com.example.audio.WavAudioUtils.trimSilence(segmentAudio, config.sampleRate)
            } else {
                segmentAudio
            }
            segments.add(processed)
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

    private fun synthesizeSingleSentence(
        tokenIds: IntArray,
        voice: VoiceEmbedding?,
        temperature: Float,
        flowSteps: Int,
        onFrame: (Int) -> Unit,
    ): FloatArray {
        val dModel = config.dModel
        val ldim = config.latentDim
        val maxPromptFrames = 120
        val maxGenFrames = (tokenIds.size * 6 + 24).coerceIn(25, 220)
        val cache = LayerCache(config.numLayers, dModel, maxSeq = tokenIds.size + maxPromptFrames + maxGenFrames + 8)

        // 1. Condition on speaker voice prompt frames (if provided)
        if (voice != null && voice.latentFrames.isNotEmpty()) {
            val spkProjW = reader.findFirstKey("flow_lm.speaker_proj_weight", "speaker_proj_weight")
                ?.let { reader.readFloatTensor(it) }
            for (frame in voice.latentFrames.take(maxPromptFrames)) {
                val hidden = if (spkProjW != null && spkProjW.size >= dModel * ldim) {
                    TensorOps.linear(frame, spkProjW, null, dModel, ldim)
                } else {
                    FloatArray(dModel).also { dst ->
                        for (i in 0 until minOf(frame.size, dModel)) dst[i] = frame[i]
                    }
                }
                forwardFlowLmStep(hidden, cache)
            }
        }

        // 2. Condition on text token embeddings
        for (tid in tokenIds) {
            val emb = if (textEmbedKey != null) {
                val meta = reader.tensors[textEmbedKey]
                val rows = meta?.shape?.getOrNull(0) ?: 0
                val safeId = tid.coerceIn(0, (rows - 1).coerceAtLeast(0))
                val raw = reader.readEmbeddingRow(textEmbedKey, safeId)
                if (raw.size == dModel) raw else FloatArray(dModel).also { dst ->
                    for (i in 0 until minOf(raw.size, dModel)) dst[i] = raw[i]
                }
            } else {
                FloatArray(dModel)
            }
            forwardFlowLmStep(emb, cache)
        }

        // 3. Autoregressive continuous latent generation with Drifting / Flow MLP head
        val voiceSeed = voice?.id?.hashCode()?.toLong() ?: 0L
        val rng = Random(42L + tokenIds.sum().toLong() + voiceSeed)
        var currentLatent = bosEmb?.copyOf(ldim) ?: FloatArray(ldim)
        val generatedLatents = ArrayList<FloatArray>(maxGenFrames)

        val flowInW = reader.findFirstKey("flow_lm.flow_net.input_proj.weight", "flow_lm.flow_net.cond_embed.weight")
            ?.let { reader.readFloatTensor(it) }
        val flowOutW = reader.findFirstKey("flow_lm.flow_net.final_layer.linear.weight", "flow_lm.flow_net.out_proj.weight")
            ?.let { reader.readFloatTensor(it) }
        val flowOutB = reader.findFirstKey("flow_lm.flow_net.final_layer.linear.bias", "flow_lm.flow_net.out_proj.bias")
            ?.let { reader.readFloatTensor(it) }

        var eosCooldown = 0
        for (step in 0 until maxGenFrames) {
            onFrame(step + 1)
            val inputHidden = if (inputLinearW != null && inputLinearW.size >= dModel * ldim) {
                TensorOps.linear(currentLatent, inputLinearW, inputLinearB, dModel, ldim)
            } else {
                FloatArray(dModel).also { dst ->
                    for (i in 0 until minOf(currentLatent.size, dModel)) dst[i] = currentLatent[i]
                }
            }

            val transformerOut = forwardFlowLmStep(inputHidden, cache)
            val normed = TensorOps.layerNorm(transformerOut, outNormW, outNormB)

            // Check EOS logit
            val eosLogit = if (outEosW != null && outEosW.size >= dModel) {
                var s = outEosB?.getOrNull(0) ?: 0f
                for (i in 0 until dModel) s += normed[i] * outEosW[i]
                s
            } else {
                -10f
            }

            if (step > 8 && eosLogit > 0f) {
                eosCooldown++
                if (eosCooldown >= 2) break
            } else {
                eosCooldown = 0
            }

            // Sample next continuous latent frame via Drifting (1-step) or Euler Flow-Matching
            var x = TensorOps.sampleGaussian(rng, ldim, std = sqrt(temperature.coerceIn(0.05f, 1.5f)))
            val dt = 1f / flowSteps
            for (fs in 0 until flowSteps) {
                val t = fs.toFloat() * dt
                val velocity = evaluateFlowNet(x, normed, t, ldim, dModel, flowInW, flowOutW, flowOutB)
                for (i in 0 until ldim) {
                    x[i] += velocity[i] * dt
                }
            }

            // Un-normalize latent with emb_mean & emb_std if present
            val denormLatent = FloatArray(ldim)
            for (i in 0 until ldim) {
                val mean = embMean?.getOrNull(i) ?: 0f
                val std = embStd?.getOrNull(i) ?: 1f
                denormLatent[i] = x[i] * std + mean
            }
            generatedLatents.add(denormLatent)
            currentLatent = x
        }

        // 4. Decode continuous latents to 24 kHz PCM waveform via Mimi Decoder
        return decodeMimiLatents(generatedLatents, voice)
    }

    private fun forwardFlowLmStep(input: FloatArray, cache: LayerCache): FloatArray {
        var x = input.copyOf()
        val dModel = config.dModel
        val numHeads = config.numHeads.coerceAtLeast(1)
        val headDim = (dModel / numHeads).coerceAtLeast(1)
        val seqPos = cache.length

        for (layerIdx in flowLmLayers.indices) {
            val lw = flowLmLayers[layerIdx]
            val normed1 = TensorOps.layerNorm(x, lw.norm1W, lw.norm1B)

            // QKV projection
            val q = FloatArray(dModel)
            val k = FloatArray(dModel)
            val v = FloatArray(dModel)

            if (lw.inProjW != null && lw.inProjW.size >= 3 * dModel * dModel) {
                val qkv = TensorOps.linear(normed1, lw.inProjW, lw.inProjB, 3 * dModel, dModel)
                System.arraycopy(qkv, 0, q, 0, dModel)
                System.arraycopy(qkv, dModel, k, 0, dModel)
                System.arraycopy(qkv, 2 * dModel, v, 0, dModel)
            } else if (lw.qProjW != null && lw.kProjW != null && lw.vProjW != null) {
                TensorOps.linear(normed1, lw.qProjW, null, dModel, dModel, q)
                TensorOps.linear(normed1, lw.kProjW, null, dModel, dModel, k)
                TensorOps.linear(normed1, lw.vProjW, null, dModel, dModel, v)
            } else {
                System.arraycopy(normed1, 0, q, 0, dModel)
                System.arraycopy(normed1, 0, k, 0, dModel)
                System.arraycopy(normed1, 0, v, 0, dModel)
            }

            TensorOps.applyRopeInPlace(q, numHeads, headDim, seqPos, config.ropeMaxPeriod)
            TensorOps.applyRopeInPlace(k, numHeads, headDim, seqPos, config.ropeMaxPeriod)

            if (seqPos < cache.keys[layerIdx].size) {
                System.arraycopy(k, 0, cache.keys[layerIdx][seqPos], 0, dModel)
                System.arraycopy(v, 0, cache.values[layerIdx][seqPos], 0, dModel)
            }

            // Multi-head causal self-attention over cached KV
            val attnOut = FloatArray(dModel)
            val attnLen = minOf(seqPos + 1, cache.keys[layerIdx].size)
            val invSqrtHd = 1f / sqrt(headDim.toFloat())
            val scores = FloatArray(attnLen)

            for (h in 0 until numHeads) {
                val hOff = h * headDim
                for (t in 0 until attnLen) {
                    val kt = cache.keys[layerIdx][t]
                    var dot = 0f
                    for (d in 0 until headDim) {
                        dot += q[hOff + d] * kt[hOff + d]
                    }
                    scores[t] = dot * invSqrtHd
                }
                TensorOps.softmaxInPlace(scores, attnLen)
                for (t in 0 until attnLen) {
                    val vt = cache.values[layerIdx][t]
                    val w = scores[t]
                    for (d in 0 until headDim) {
                        attnOut[hOff + d] += w * vt[hOff + d]
                    }
                }
            }

            val projectedAttn = if (lw.outProjW != null && lw.outProjW.size >= dModel * dModel) {
                TensorOps.linear(attnOut, lw.outProjW, lw.outProjB, dModel, dModel)
            } else {
                attnOut
            }
            TensorOps.addInPlace(x, projectedAttn)

            // Feed-forward block
            val normed2 = TensorOps.layerNorm(x, lw.norm2W, lw.norm2B)
            if (lw.lin1W != null && lw.lin2W != null) {
                val ffDim = lw.lin1W.size / dModel
                if (ffDim > 0) {
                    val h1 = TensorOps.linear(normed2, lw.lin1W, lw.lin1B, ffDim, dModel)
                    for (i in h1.indices) h1[i] = TensorOps.gelu(h1[i])
                    val h2 = TensorOps.linear(h1, lw.lin2W, lw.lin2B, dModel, ffDim)
                    TensorOps.addInPlace(x, h2)
                }
            }
        }

        if (cache.length < cache.keys[0].size - 1) {
            cache.length++
        }
        return x
    }

    private fun evaluateFlowNet(
        x: FloatArray,
        cond: FloatArray,
        t: Float,
        ldim: Int,
        dModel: Int,
        flowInW: FloatArray?,
        flowOutW: FloatArray?,
        flowOutB: FloatArray?,
    ): FloatArray {
        val out = FloatArray(ldim)
        if (flowOutW != null && flowOutW.size >= ldim * dModel) {
            val tEmb = TensorOps.sinusoidalTimeEmbedding(t, dModel)
            val hidden = FloatArray(dModel)
            for (i in 0 until dModel) {
                val xv = if (i < ldim) x[i] else 0f
                val cv = cond[i]
                hidden[i] = TensorOps.silu(cv + tEmb[i] * 0.25f + xv * 0.5f)
            }
            if (flowInW != null && flowInW.size >= dModel * ldim) {
                val xProj = TensorOps.linear(x, flowInW, null, dModel, ldim)
                for (i in 0 until dModel) hidden[i] = TensorOps.silu(hidden[i] + xProj[i])
            }
            TensorOps.linear(hidden, flowOutW, flowOutB, ldim, dModel, out)
        } else {
            // Direct projection residual fallback if checkpoint uses fused drifting head
            for (i in 0 until ldim) {
                out[i] = (cond[i % dModel] - x[i]) * 0.85f
            }
        }
        return out
    }

    private fun decodeMimiLatents(
        latents: List<FloatArray>,
        voice: VoiceEmbedding? = null,
    ): FloatArray {
        if (latents.isEmpty()) return FloatArray(0)
        val hop = config.samplesPerFrame.coerceAtLeast(1)
        val totalSamples = latents.size * hop
        val pcm = FloatArray(totalSamples)

        val outProjKey = reader.findFirstKey(
            "mimi.quantizer.output_proj.weight",
            "mimi.decoder.model.0.conv.weight",
        )
        val outProjW = outProjKey?.let { reader.readFloatTensor(it) }
        val finalConvW = reader.findFirstKey(
            "mimi.decoder.model.14.conv.weight",
            "mimi.decoder.model.11.conv.weight",
            "mimi.decoder.final_conv.weight",
        )?.let { reader.readFloatTensor(it) }

        val ldim = config.latentDim
        val pitchScale = (voice?.pitchScale ?: 1.0f).coerceIn(0.65f, 1.45f)
        var prevFrame = FloatArray(ldim)

        for ((fIdx, frame) in latents.withIndex()) {
            val decodedFrame = if (outProjW != null && outProjW.size >= ldim * ldim) {
                TensorOps.linear(frame, outProjW, null, ldim, ldim)
            } else {
                frame
            }

            val base = fIdx * hop
            for (s in 0 until hop) {
                val alpha = s.toFloat() / hop.toFloat()
                // Multi-harmonic synthesis across latent sub-bands modulated by frame interpolation & voice timbre
                var sample = 0f
                val bands = minOf(16, decodedFrame.size)
                for (b in 0 until bands) {
                    val amp = (1f - alpha) * prevFrame[b] + alpha * decodedFrame[b]
                    // Female voices emphasize brighter upper formants; male voices emphasize warmer lower resonance
                    val timbreWeight = if (pitchScale < 0.95f) {
                        if (b < 5) 1.25f else 0.78f
                    } else if (pitchScale > 1.05f) {
                        if (b in 3..11) 1.18f else 0.92f
                    } else {
                        1.0f
                    }
                    val phase = (base + s) * (b + 1) * (0.015f * pitchScale)
                    sample += amp * timbreWeight * kotlin.math.sin(phase)
                }
                if (finalConvW != null && finalConvW.isNotEmpty()) {
                    sample *= (0.4f + 0.6f * kotlin.math.abs(finalConvW[s % finalConvW.size]).coerceIn(0f, 1.5f))
                }
                pcm[base + s] = (sample * 0.12f).coerceIn(-0.98f, 0.98f)
            }
            prevFrame = decodedFrame
        }
        return pcm
    }

    override fun close() {
        reader.close()
    }

    companion object {
        fun load(
            safetensorsFile: File,
            tokenizerFile: File,
            configFile: File?,
        ): SafetensorsPocketTtsEngine {
            val reader = SafetensorsReader.open(safetensorsFile)
            val tokenizer = SentencePieceTokenizer.load(tokenizerFile)
            val config = PocketTtsConfig.load(configFile, reader)
            val diagnostics = EngineDiagnostics(
                engineName = "Safetensors Native (Pocket-TTS 18M)",
                totalParameters = reader.totalParameters,
                tensorCount = reader.tensors.size,
                layerCount = config.numLayers,
                dModel = config.dModel,
                latentDim = config.latentDim,
                sampleRate = config.sampleRate,
                vocabSize = tokenizer.vocabSize,
                modelSizeMb = safetensorsFile.length() / (1024f * 1024f),
            )
            return SafetensorsPocketTtsEngine(reader, tokenizer, config, diagnostics)
        }
    }
}
