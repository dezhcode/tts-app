package com.example.tts

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Memory-mapped reader for Hugging Face `.safetensors` files.
 * Supports F32, F16, and BF16 tensor decoding without loading the entire 100MB+ file onto the JVM heap.
 */
class SafetensorsReader private constructor(
    private val raf: RandomAccessFile,
    private val channel: FileChannel,
    private val dataStartOffset: Long,
    val tensors: Map<String, TensorMeta>,
    val metadata: Map<String, String>,
) : Closeable {

    data class TensorMeta(
        val name: String,
        val dtype: String,
        val shape: IntArray,
        val startOffset: Long,
        val endOffset: Long,
    ) {
        val numElements: Long
            get() = shape.fold(1L) { acc, dim -> acc * dim.toLong() }

        val byteSize: Long
            get() = endOffset - startOffset
    }

    val totalParameters: Long
        get() = tensors.values.sumOf { it.numElements }

    fun hasTensor(name: String): Boolean = tensors.containsKey(name)

    fun findFirstKey(vararg candidates: String): String? =
        candidates.firstOrNull { tensors.containsKey(it) }

    fun readFloatTensor(name: String): FloatArray {
        val meta = tensors[name] ?: error("Tensor '$name' not found in safetensors")
        val byteLen = (meta.endOffset - meta.startOffset).toInt()
        val buf = ByteBuffer.allocate(byteLen).order(ByteOrder.LITTLE_ENDIAN)
        var read = 0
        val fileOffset = dataStartOffset + meta.startOffset
        while (read < byteLen) {
            val n = channel.read(buf, fileOffset + read)
            if (n <= 0) break
            read += n
        }
        buf.flip()

        val count = meta.numElements.toInt()
        val out = FloatArray(count)
        when (meta.dtype) {
            "F32" -> {
                for (i in 0 until count) {
                    out[i] = buf.float
                }
            }
            "BF16" -> {
                for (i in 0 until count) {
                    val bits = buf.short.toInt() and 0xFFFF
                    out[i] = Float.fromBits(bits shl 16)
                }
            }
            "F16" -> {
                for (i in 0 until count) {
                    val half = buf.short.toInt() and 0xFFFF
                    out[i] = halfToFloat(half)
                }
            }
            else -> error("Unsupported tensor dtype '${meta.dtype}' for '$name'")
        }
        return out
    }

    /**
     * Reads a single row [rowIndex] from a 2D embedding tensor `[rows, cols]` without materializing
     * the full embedding table into memory.
     */
    fun readEmbeddingRow(name: String, rowIndex: Int): FloatArray {
        val meta = tensors[name] ?: error("Tensor '$name' not found in safetensors")
        require(meta.shape.size == 2) { "Expected 2D tensor for '$name', got ${meta.shape.contentToString()}" }
        val cols = meta.shape[1]
        val elemBytes = when (meta.dtype) {
            "F32" -> 4
            "F16", "BF16" -> 2
            else -> error("Unsupported dtype '${meta.dtype}'")
        }
        val rowBytes = cols * elemBytes
        val offset = dataStartOffset + meta.startOffset + rowIndex.toLong() * rowBytes.toLong()
        val buf = ByteBuffer.allocate(rowBytes).order(ByteOrder.LITTLE_ENDIAN)
        var read = 0
        while (read < rowBytes) {
            val n = channel.read(buf, offset + read)
            if (n <= 0) break
            read += n
        }
        buf.flip()

        val out = FloatArray(cols)
        when (meta.dtype) {
            "F32" -> for (i in 0 until cols) out[i] = buf.float
            "BF16" -> for (i in 0 until cols) out[i] = Float.fromBits((buf.short.toInt() and 0xFFFF) shl 16)
            "F16" -> for (i in 0 until cols) out[i] = halfToFloat(buf.short.toInt() and 0xFFFF)
        }
        return out
    }

    override fun close() {
        runCatching { channel.close() }
        runCatching { raf.close() }
    }

    companion object {
        fun open(file: File): SafetensorsReader {
            val raf = RandomAccessFile(file, "r")
            val channel = raf.channel
            val lenBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            channel.read(lenBuf, 0L)
            lenBuf.flip()
            val headerLen = lenBuf.long
            require(headerLen in 2L..64_000_000L) { "Invalid safetensors header length: $headerLen" }

            val headerBytes = ByteArray(headerLen.toInt())
            val headerBuf = ByteBuffer.wrap(headerBytes)
            var read = 0
            while (read < headerBytes.size) {
                val n = channel.read(headerBuf, 8L + read)
                if (n <= 0) break
                read += n
            }

            val rootMap = MiniJsonParser(String(headerBytes, Charsets.UTF_8)).parseObject()
            val tensors = LinkedHashMap<String, TensorMeta>()
            val metadata = LinkedHashMap<String, String>()

            for ((key, rawVal) in rootMap) {
                @Suppress("UNCHECKED_CAST")
                val entry = rawVal as? Map<String, Any?> ?: continue
                if (key == "__metadata__") {
                    for ((mKey, mVal) in entry) {
                        metadata[mKey] = mVal?.toString() ?: ""
                    }
                    continue
                }
                val dtype = entry["dtype"]?.toString() ?: error("Missing dtype for '$key'")
                @Suppress("UNCHECKED_CAST")
                val shapeList = entry["shape"] as? List<Number> ?: emptyList()
                val shape = IntArray(shapeList.size) { idx -> shapeList[idx].toInt() }
                @Suppress("UNCHECKED_CAST")
                val offsetsList = entry["data_offsets"] as? List<Number> ?: error("Missing data_offsets for '$key'")
                val startOffset = offsetsList[0].toLong()
                val endOffset = offsetsList[1].toLong()
                tensors[key] = TensorMeta(
                    name = key,
                    dtype = dtype,
                    shape = shape,
                    startOffset = startOffset,
                    endOffset = endOffset,
                )
            }

            return SafetensorsReader(
                raf = raf,
                channel = channel,
                dataStartOffset = 8L + headerLen,
                tensors = tensors,
                metadata = metadata,
            )
        }

        private class MiniJsonParser(private val src: String) {
            private var pos = 0

            fun parseObject(): Map<String, Any?> {
                skipWs()
                expect('{')
                val map = LinkedHashMap<String, Any?>()
                skipWs()
                if (peek() == '}') {
                    pos++
                    return map
                }
                while (pos < src.length) {
                    skipWs()
                    val key = parseString()
                    skipWs()
                    expect(':')
                    skipWs()
                    val value = parseValue()
                    map[key] = value
                    skipWs()
                    if (peek() == ',') {
                        pos++
                    } else {
                        expect('}')
                        break
                    }
                }
                return map
            }

            private fun parseArray(): List<Any?> {
                expect('[')
                val list = ArrayList<Any?>()
                skipWs()
                if (peek() == ']') {
                    pos++
                    return list
                }
                while (pos < src.length) {
                    skipWs()
                    list.add(parseValue())
                    skipWs()
                    if (peek() == ',') {
                        pos++
                    } else {
                        expect(']')
                        break
                    }
                }
                return list
            }

            private fun parseValue(): Any? {
                skipWs()
                return when (val ch = peek()) {
                    '{' -> parseObject()
                    '[' -> parseArray()
                    '"' -> parseString()
                    't' -> { pos += 4; true }
                    'f' -> { pos += 5; false }
                    'n' -> { pos += 4; null }
                    '-', in '0'..'9' -> parseNumber()
                    else -> error("Unexpected JSON char '$ch' at $pos")
                }
            }

            private fun parseString(): String {
                expect('"')
                val sb = StringBuilder()
                while (pos < src.length) {
                    val ch = src[pos++]
                    if (ch == '"') return sb.toString()
                    if (ch == '\\' && pos < src.length) {
                        when (val esc = src[pos++]) {
                            '"', '\\', '/' -> sb.append(esc)
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                val hex = src.substring(pos, pos + 4)
                                sb.append(hex.toInt(16).toChar())
                                pos += 4
                            }
                            else -> sb.append(esc)
                        }
                    } else {
                        sb.append(ch)
                    }
                }
                error("Unterminated JSON string")
            }

            private fun parseNumber(): Number {
                val start = pos
                if (src[pos] == '-') pos++
                while (pos < src.length && (src[pos].isDigit() || src[pos] == '.' || src[pos] == 'e' || src[pos] == 'E' || src[pos] == '+' || src[pos] == '-')) {
                    pos++
                }
                val token = src.substring(start, pos)
                return token.toLongOrNull() ?: token.toDouble()
            }

            private fun skipWs() {
                while (pos < src.length && src[pos].isWhitespace()) pos++
            }

            private fun peek(): Char = if (pos < src.length) src[pos] else '\u0000'

            private fun expect(expected: Char) {
                require(pos < src.length && src[pos] == expected) {
                    "Expected '$expected' at $pos"
                }
                pos++
            }
        }

        private fun halfToFloat(hbits: Int): Float {
            var mant = hbits and 0x03FF
            var exp = hbits and 0x7C00
            if (exp == 0x7C00) {
                exp = 0x3FC00
            } else if (exp != 0) {
                exp += 0x1C000
                if (mant == 0 && exp > 0x1C400) {
                    return Float.fromBits(((hbits and 0x8000) shl 16) or (exp shl 13) or 0x3FF)
                }
            } else if (mant != 0) {
                exp = 0x1C400
                do {
                    mant = mant shl 1
                    exp -= 0x400
                } while ((mant and 0x400) == 0)
                mant = mant and 0x3FF
            }
            return Float.fromBits(((hbits and 0x8000) shl 16) or ((exp or mant) shl 13))
        }
    }
}
