package com.example.tts

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * High-performance scalar/array neural network primitives in pure Kotlin `FloatArray` math
 * tailored for Kyutai Pocket-TTS (FlowLM + Mimi) layers.
 */
object TensorOps {

    fun linear(
        input: FloatArray,
        weight: FloatArray, // [outDim, inDim] row-major
        bias: FloatArray?,
        outDim: Int,
        inDim: Int,
        out: FloatArray = FloatArray(outDim),
    ): FloatArray {
        for (o in 0 until outDim) {
            val rowOffset = o * inDim
            var acc = if (bias != null) bias[o] else 0f
            var i = 0
            val limit = inDim - 3
            while (i < limit) {
                acc += input[i] * weight[rowOffset + i] +
                    input[i + 1] * weight[rowOffset + i + 1] +
                    input[i + 2] * weight[rowOffset + i + 2] +
                    input[i + 3] * weight[rowOffset + i + 3]
                i += 4
            }
            while (i < inDim) {
                acc += input[i] * weight[rowOffset + i]
                i++
            }
            out[o] = acc
        }
        return out
    }

    fun layerNorm(
        x: FloatArray,
        weight: FloatArray?,
        bias: FloatArray?,
        eps: Float = 1e-5f,
        out: FloatArray = FloatArray(x.size),
    ): FloatArray {
        val n = x.size
        var sum = 0f
        for (v in x) sum += v
        val mean = sum / n
        var varSum = 0f
        for (v in x) {
            val d = v - mean
            varSum += d * d
        }
        val invStd = 1f / sqrt(varSum / n + eps)
        for (i in 0 until n) {
            var normed = (x[i] - mean) * invStd
            if (weight != null) normed *= weight[i]
            if (bias != null) normed += bias[i]
            out[i] = normed
        }
        return out
    }

    fun rmsNorm(
        x: FloatArray,
        weight: FloatArray?,
        eps: Float = 1e-6f,
        out: FloatArray = FloatArray(x.size),
    ): FloatArray {
        val n = x.size
        var sqSum = 0f
        for (v in x) sqSum += v * v
        val invRms = 1f / sqrt(sqSum / n + eps)
        for (i in 0 until n) {
            val scaled = x[i] * invRms
            out[i] = if (weight != null) scaled * weight[i] else scaled
        }
        return out
    }

    fun applyRopeInPlace(
        vec: FloatArray,
        numHeads: Int,
        headDim: Int,
        position: Int,
        maxPeriod: Float = 10_000f,
    ) {
        val half = headDim / 2
        val logPeriod = kotlin.math.ln(maxPeriod.toDouble())
        for (h in 0 until numHeads) {
            val base = h * headDim
            for (i in 0 until half) {
                val freq = exp(-logPeriod * (2.0 * i / headDim))
                val angle = position * freq
                val c = cos(angle).toFloat()
                val s = sin(angle).toFloat()
                val x0 = vec[base + i]
                val x1 = vec[base + i + half]
                vec[base + i] = x0 * c - x1 * s
                vec[base + i + half] = x0 * s + x1 * c
            }
        }
    }

    fun gelu(x: Float): Float {
        val c = 0.7978845608f // sqrt(2/pi)
        val inner = c * (x + 0.044715f * x * x * x)
        return 0.5f * x * (1f + tanh(inner.toDouble()).toFloat())
    }

    fun silu(x: Float): Float = x / (1f + exp(-x))

    fun elu(x: Float, alpha: Float = 1f): Float = if (x >= 0f) x else alpha * (exp(x) - 1f)

    fun softmaxInPlace(logits: FloatArray, length: Int = logits.size) {
        if (length <= 0) return
        var maxVal = logits[0]
        for (i in 1 until length) {
            if (logits[i] > maxVal) maxVal = logits[i]
        }
        var sum = 0f
        for (i in 0 until length) {
            val e = exp((logits[i] - maxVal).coerceIn(-60f, 0f))
            logits[i] = e
            sum += e
        }
        val inv = if (sum > 0f) 1f / sum else 1f / length
        for (i in 0 until length) logits[i] *= inv
    }

    fun addInPlace(dst: FloatArray, src: FloatArray) {
        for (i in dst.indices) dst[i] += src[i]
    }

    fun sampleGaussian(rng: java.util.Random, size: Int, std: Float = 1f): FloatArray {
        val out = FloatArray(size)
        for (i in 0 until size) {
            out[i] = (rng.nextGaussian() * std).toFloat()
        }
        return out
    }

    fun sinusoidalTimeEmbedding(t: Float, dim: Int, maxPeriod: Float = 10_000f): FloatArray {
        val half = dim / 2
        val out = FloatArray(dim)
        val logPeriod = kotlin.math.ln(maxPeriod.toDouble())
        for (i in 0 until half) {
            val freq = exp(-logPeriod * i / half.coerceAtLeast(1))
            val arg = t * freq * 2.0 * PI
            out[i] = cos(arg).toFloat()
            out[i + half] = sin(arg).toFloat()
        }
        return out
    }
}
