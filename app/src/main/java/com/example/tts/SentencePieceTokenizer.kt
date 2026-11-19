package com.example.tts

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ln

/**
 * Pure Kotlin SentencePiece `tokenizer.model` (Protobuf ModelProto) parser & encoder.
 *
 * Parses `SentencePiece` repeated pieces (piece string, score float, type enum) and `TrainerSpec`
 * directly from HuggingFace `tokenizer.model` without requiring native C++ bindings.
 * Supports Unigram (Viterbi DP) and Byte-Fallback (<0xXX>) tokenization.
 */
class SentencePieceTokenizer private constructor(
    val pieces: List<Piece>,
    val unkId: Int,
    val bosId: Int,
    val eosId: Int,
    val padId: Int,
    val byteFallback: Boolean,
    val modelType: Int, // 1=UNIGRAM, 2=BPE, 3=WORD, 4=CHAR
) {
    data class Piece(
        val text: String,
        val score: Float,
        val type: Int, // 1=NORMAL, 2=UNKNOWN, 3=CONTROL, 4=USER_DEFINED, 5=UNUSED, 6=BYTE
    )

    private val pieceToId: Map<String, Int>
    private val bytePieceIds: IntArray
    private val maxPieceLen: Int

    init {
        val map = HashMap<String, Int>(pieces.size * 2)
        val bytes = IntArray(256) { unkId }
        var maxLen = 1
        pieces.forEachIndexed { index, p ->
            map[p.text] = index
            if (p.type == 1 || p.type == 4) {
                if (p.text.length > maxLen) maxLen = p.text.length
            }
            if (p.type == 6 && p.text.startsWith("<0x") && p.text.endsWith(">") && p.text.length == 6) {
                val bVal = p.text.substring(3, 5).toIntOrNull(16)
                if (bVal != null && bVal in 0..255) {
                    bytes[bVal] = index
                }
            }
        }
        pieceToId = map
        bytePieceIds = bytes
        maxPieceLen = maxLen
    }

    val vocabSize: Int get() = pieces.size

    fun encode(text: String, addBos: Boolean = false, addEos: Boolean = false): IntArray {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) {
            val list = mutableListOf<Int>()
            if (addBos && bosId >= 0) list.add(bosId)
            if (addEos && eosId >= 0) list.add(eosId)
            return list.toIntArray()
        }

        // SentencePiece replaces spaces with U+2581 ('▁') and prepends '▁'
        val spInput = "\u2581" + cleaned.replace(' ', '\u2581')
        val ids = mutableListOf<Int>()
        if (addBos && bosId >= 0) ids.add(bosId)

        // Viterbi DP segmentation maximizing sum of piece log-probabilities
        val n = spInput.length
        val bestScore = FloatArray(n + 1) { Float.NEGATIVE_INFINITY }
        val bestPrev = IntArray(n + 1) { -1 }
        val bestId = IntArray(n + 1) { unkId }
        bestScore[0] = 0f

        for (i in 0 until n) {
            if (bestScore[i].isInfinite() && bestScore[i] < 0f) continue

            var matchedAny = false
            val maxEnd = minOf(n, i + maxPieceLen)
            for (end in (i + 1)..maxEnd) {
                val sub = spInput.substring(i, end)
                val id = pieceToId[sub]
                if (id != null) {
                    val p = pieces[id]
                    if (p.type == 1 || p.type == 4) {
                        val score = bestScore[i] + p.score
                        if (score > bestScore[end]) {
                            bestScore[end] = score
                            bestPrev[end] = i
                            bestId[end] = id
                            matchedAny = true
                        }
                    }
                }
            }

            // Fallback single character / byte if no piece started at i
            if (!matchedAny || bestScore[i + 1] == Float.NEGATIVE_INFINITY) {
                val fallbackScore = bestScore[i] - 20.0f
                if (fallbackScore > bestScore[i + 1]) {
                    bestScore[i + 1] = fallbackScore
                    bestPrev[i + 1] = i
                    bestId[i + 1] = -2 // marker for byte/unk fallback
                }
            }
        }

        // Backtrack
        val segments = mutableListOf<Pair<Int, Int>>()
        var cur = n
        while (cur > 0) {
            val prev = bestPrev[cur]
            if (prev < 0) {
                segments.add(0, Pair(cur - 1, cur))
                cur--
            } else {
                segments.add(0, Pair(prev, cur))
                cur = prev
            }
        }

        for ((start, end) in segments) {
            val id = bestId[end]
            if (id >= 0) {
                ids.add(id)
            } else {
                val sub = spInput.substring(start, end)
                val direct = pieceToId[sub]
                if (direct != null) {
                    ids.add(direct)
                } else if (byteFallback) {
                    for (b in sub.toByteArray(Charsets.UTF_8)) {
                        ids.add(bytePieceIds[b.toInt() and 0xFF])
                    }
                } else {
                    ids.add(unkId)
                }
            }
        }

        if (addEos && eosId >= 0) ids.add(eosId)
        return ids.toIntArray()
    }

    fun decode(ids: IntArray): String {
        val sb = StringBuilder()
        for (id in ids) {
            if (id in pieces.indices) {
                val p = pieces[id]
                if (p.type == 1 || p.type == 4) {
                    sb.append(p.text.replace('\u2581', ' '))
                }
            }
        }
        return sb.toString().trimStart()
    }

    companion object {
        fun load(file: File): SentencePieceTokenizer {
            val bytes = file.readBytes()
            return parseModelProto(bytes)
        }

        /**
         * Parses `sentencepiece.ModelProto` binary protobuf:
         * - field 1 (length-delimited, repeated): SentencePiece
         * - field 2 (length-delimited): TrainerSpec
         */
        fun parseModelProto(data: ByteArray): SentencePieceTokenizer {
            val pieces = mutableListOf<Piece>()
            var unkId = 0
            var bosId = 1
            var eosId = 2
            var padId = -1
            var byteFallback = false
            var modelType = 1

            val reader = ProtoReader(data, 0, data.size)
            while (reader.hasRemaining()) {
                val tag = reader.readVarint().toInt()
                val fieldNumber = tag ushr 3
                val wireType = tag and 0x07
                when (fieldNumber) {
                    1 if wireType == 2 -> {
                        val len = reader.readVarint().toInt()
                        val end = reader.pos + len
                        pieces.add(parseSentencePiece(data, reader.pos, end))
                        reader.pos = end
                    }
                    2 if wireType == 2 -> {
                        val len = reader.readVarint().toInt()
                        val end = reader.pos + len
                        val tr = ProtoReader(data, reader.pos, end)
                        while (tr.hasRemaining()) {
                            val tTag = tr.readVarint().toInt()
                            val tField = tTag ushr 3
                            val tWire = tTag and 0x07
                            when (tField) {
                                3 if tWire == 0 -> modelType = tr.readVarint().toInt()
                                35 if tWire == 0 -> byteFallback = tr.readVarint() != 0L
                                40 if tWire == 0 -> unkId = tr.readVarint().toInt()
                                41 if tWire == 0 -> bosId = tr.readVarint().toInt()
                                42 if tWire == 0 -> eosId = tr.readVarint().toInt()
                                43 if tWire == 0 -> padId = tr.readVarint().toInt()
                                else -> tr.skipField(tWire)
                            }
                        }
                        reader.pos = end
                    }
                    else -> reader.skipField(wireType)
                }
            }

            require(pieces.isNotEmpty()) { "Invalid tokenizer.model: no SentencePiece entries found." }
            return SentencePieceTokenizer(
                pieces = pieces,
                unkId = unkId,
                bosId = bosId,
                eosId = eosId,
                padId = padId,
                byteFallback = byteFallback,
                modelType = modelType,
            )
        }

        private fun parseSentencePiece(data: ByteArray, start: Int, end: Int): Piece {
            var text = ""
            var score = 0f
            var type = 1
            val reader = ProtoReader(data, start, end)
            while (reader.hasRemaining()) {
                val tag = reader.readVarint().toInt()
                val fieldNumber = tag ushr 3
                val wireType = tag and 0x07
                when (fieldNumber) {
                    1 if wireType == 2 -> {
                        val len = reader.readVarint().toInt()
                        text = String(data, reader.pos, len, Charsets.UTF_8)
                        reader.pos += len
                    }
                    2 if wireType == 5 -> {
                        score = reader.readFixed32Float()
                    }
                    3 if wireType == 0 -> {
                        type = reader.readVarint().toInt()
                    }
                    else -> reader.skipField(wireType)
                }
            }
            return Piece(text = text, score = score, type = type)
        }

        private class ProtoReader(
            private val data: ByteArray,
            var pos: Int,
            private val end: Int,
        ) {
            fun hasRemaining(): Boolean = pos < end

            fun readVarint(): Long {
                var result = 0L
                var shift = 0
                while (pos < end) {
                    val b = data[pos++].toInt() and 0xFF
                    result = result or ((b.toLong() and 0x7FL) shl shift)
                    if ((b and 0x80) == 0) return result
                    shift += 7
                }
                return result
            }

            fun readFixed32Float(): Float {
                val bb = ByteBuffer.wrap(data, pos, 4).order(ByteOrder.LITTLE_ENDIAN)
                pos += 4
                return bb.float
            }

            fun skipField(wireType: Int) {
                when (wireType) {
                    0 -> readVarint()
                    1 -> pos += 8
                    2 -> {
                        val len = readVarint().toInt()
                        pos += len
                    }
                    5 -> pos += 4
                    else -> pos = end
                }
            }
        }
    }
}
