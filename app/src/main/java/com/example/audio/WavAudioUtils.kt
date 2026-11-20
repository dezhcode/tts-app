package com.example.audio

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

object WavAudioUtils {

    /**
     * Reads a standard PCM WAV file (8/16/24/32-bit, mono/stereo, any sample rate)
     * and resamples it to 24,000 Hz mono float32 in [-1.0, 1.0].
     */
    fun readWavAs24kMono(file: File, targetRate: Int = 24_000): FloatArray {
        val bytes = file.readBytes()
        require(bytes.size >= 44) { "فایل صوتی WAV معتبر نیست." }
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // Parse chunks to locate fmt and data
        var pos = 12
        var channels = 1
        var sampleRate = targetRate
        var bitsPerSample = 16
        var audioFormat = 1
        var dataStart = -1
        var dataSize = 0

        while (pos + 8 <= bytes.size) {
            val chunkId = String(bytes, pos, 4, Charsets.US_ASCII)
            val chunkSize = bb.getInt(pos + 4)
            val payloadPos = pos + 8
            if (chunkId == "fmt " && payloadPos + 16 <= bytes.size) {
                audioFormat = bb.getShort(payloadPos).toInt() and 0xFFFF
                channels = bb.getShort(payloadPos + 2).toInt() and 0xFFFF
                sampleRate = bb.getInt(payloadPos + 4)
                bitsPerSample = bb.getShort(payloadPos + 14).toInt() and 0xFFFF
            } else if (chunkId == "data") {
                dataStart = payloadPos
                dataSize = minOf(chunkSize, bytes.size - payloadPos)
                break
            }
            pos = payloadPos + chunkSize + (chunkSize and 1)
        }

        require(dataStart >= 0 && dataSize > 0) { "داده صوتی در فایل WAV پیدا نشد." }

        val bytesPerSample = (bitsPerSample / 8).coerceAtLeast(1)
        val totalFrames = dataSize / (bytesPerSample * channels.coerceAtLeast(1))
        val mono = FloatArray(totalFrames)

        for (frame in 0 until totalFrames) {
            var chSum = 0f
            for (ch in 0 until channels) {
                val offset = dataStart + (frame * channels + ch) * bytesPerSample
                if (offset + bytesPerSample > bytes.size) break
                val sample = when {
                    audioFormat == 3 && bitsPerSample == 32 -> bb.getFloat(offset)
                    bitsPerSample == 16 -> bb.getShort(offset).toFloat() / 32768f
                    bitsPerSample == 8 -> ((bytes[offset].toInt() and 0xFF) - 128) / 128f
                    bitsPerSample == 24 -> {
                        val b0 = bytes[offset].toInt() and 0xFF
                        val b1 = bytes[offset + 1].toInt() and 0xFF
                        val b2 = bytes[offset + 2].toInt()
                        val v = (b2 shl 16) or (b1 shl 8) or b0
                        v.toFloat() / 8388608f
                    }
                    bitsPerSample == 32 -> bb.getInt(offset).toFloat() / 2147483648f
                    else -> 0f
                }
                chSum += sample
            }
            mono[frame] = chSum / channels.coerceAtLeast(1)
        }

        return if (sampleRate == targetRate) mono else resampleLinear(mono, sampleRate, targetRate)
    }

    fun resampleLinear(input: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
        if (input.isEmpty() || srcRate <= 0 || srcRate == dstRate) return input
        val ratio = srcRate.toDouble() / dstRate.toDouble()
        val outLen = (input.size / ratio).toInt().coerceAtLeast(1)
        val out = FloatArray(outLen)
        for (i in 0 until outLen) {
            val srcPos = i * ratio
            val idx0 = srcPos.toInt().coerceIn(0, input.lastIndex)
            val idx1 = (idx0 + 1).coerceIn(0, input.lastIndex)
            val frac = (srcPos - idx0).toFloat()
            out[i] = input[idx0] * (1f - frac) + input[idx1] * frac
        }
        return out
    }

    /**
     * Trims leading & trailing near-silent frames (matching `speak_fa.py` post-processing).
     */
    fun trimSilence(samples: FloatArray, sampleRate: Int, threshold: Float = 0.006f): FloatArray {
        if (samples.isEmpty()) return samples
        val pad = (sampleRate * 0.03f).toInt()
        var start = 0
        while (start < samples.size && abs(samples[start]) < threshold) start++
        if (start >= samples.size) return samples
        var end = samples.lastIndex
        while (end > start && abs(samples[end]) < threshold) end--

        val safeStart = (start - pad).coerceAtLeast(0)
        val safeEnd = (end + pad).coerceAtMost(samples.lastIndex)
        return samples.copyOfRange(safeStart, safeEnd + 1)
    }

    fun stitchSegmentsWithPause(
        segments: List<FloatArray>,
        sampleRate: Int,
        pauseMs: Int,
    ): FloatArray {
        if (segments.isEmpty()) return FloatArray(0)
        if (segments.size == 1) return segments[0]
        val pauseSamples = ((sampleRate * pauseMs) / 1000).coerceAtLeast(0)
        val totalLen = segments.sumOf { it.size } + pauseSamples * (segments.size - 1)
        val out = FloatArray(totalLen)
        var offset = 0
        for (i in segments.indices) {
            val seg = segments[i]
            System.arraycopy(seg, 0, out, offset, seg.size)
            offset += seg.size
            if (i < segments.lastIndex) {
                offset += pauseSamples
            }
        }
        return out
    }

    fun writeWav16Bit(samples: FloatArray, sampleRate: Int, outFile: File) {
        outFile.parentFile?.mkdirs()
        val numSamples = samples.size
        val dataSize = numSamples * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16) // PCM fmt chunk size
        header.putShort(1) // PCM format
        header.putShort(1) // mono
        header.putInt(sampleRate)
        header.putInt(sampleRate * 2) // byte rate
        header.putShort(2) // block align
        header.putShort(16) // bits per sample
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)

        val pcmBytes = ByteArray(dataSize)
        val pcmBuf = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) {
            val clamped = (s * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()
            pcmBuf.putShort(clamped)
        }

        FileOutputStream(outFile).use { fos ->
            fos.write(header.array())
            fos.write(pcmBytes)
        }
    }

    fun computeWaveformPeaks(samples: FloatArray, barCount: Int = 56): List<Float> {
        if (samples.isEmpty()) return List(barCount) { 0.08f }
        val step = (samples.size / barCount).coerceAtLeast(1)
        val raw = FloatArray(barCount)
        var maxPeak = 0.001f
        for (b in 0 until barCount) {
            val start = b * step
            val end = minOf(samples.size, start + step)
            var peak = 0f
            for (i in start until end) {
                val a = abs(samples[i])
                if (a > peak) peak = a
            }
            raw[b] = peak
            if (peak > maxPeak) maxPeak = peak
        }
        return raw.map { (it / maxPeak).coerceIn(0.08f, 1f) }
    }

    /**
     * Synthesizes a deterministic formant-shaped vocal-tract conditioning prompt at 24 kHz
     * so built-in Female (F0 ~215 Hz) and Male (F0 ~115 Hz) Studio narrators condition the
     * neural encoder with realistic pitch and vocal-tract formant characteristics.
     */
    fun generateVocalReferencePrompt(
        baseFreqHz: Float,
        isFemale: Boolean,
        sampleRate: Int = 24_000,
        durationSec: Float = 1.4f,
    ): FloatArray {
        val totalSamples = (sampleRate * durationSec).toInt().coerceAtLeast(sampleRate / 2)
        val out = FloatArray(totalSamples)
        val formants = if (isFemale) {
            floatArrayOf(520f, 1750f, 2800f)
        } else {
            floatArrayOf(400f, 1150f, 2400f)
        }
        val bandwidths = if (isFemale) {
            floatArrayOf(110f, 160f, 220f)
        } else {
            floatArrayOf(90f, 130f, 190f)
        }
        val twoPi = (2.0 * Math.PI).toFloat()
        val maxHarmonic = if (isFemale) 14 else 18

        for (i in 0 until totalSamples) {
            val t = i.toFloat() / sampleRate.toFloat()
            // Gentle natural prosodic pitch contour
            val f0 = baseFreqHz * (1f + 0.045f * kotlin.math.sin(twoPi * 1.6f * t))
            val env = when {
                t < 0.08f -> t / 0.08f
                t > durationSec - 0.12f -> ((durationSec - t) / 0.12f).coerceAtLeast(0f)
                else -> 0.85f + 0.15f * kotlin.math.sin(twoPi * 2.8f * t)
            }
            var sum = 0f
            for (h in 1..maxHarmonic) {
                val freq = f0 * h
                // Formant resonance gain across F1, F2, F3
                var gain = 0.25f / h
                for (fIdx in formants.indices) {
                    val diff = (freq - formants[fIdx]) / bandwidths[fIdx]
                    gain += 0.55f / (1f + diff * diff)
                }
                sum += gain * kotlin.math.sin(twoPi * freq * t)
            }
            out[i] = (sum * env * 0.16f).coerceIn(-0.92f, 0.92f)
        }
        return out
    }
}
