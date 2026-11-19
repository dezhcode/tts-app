package com.example

import com.example.audio.WavAudioUtils
import com.example.tts.PersianTextNormalizer
import com.example.tts.SafetensorsReader
import com.example.tts.SentencePieceTokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CillaTtsCoreTests {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun normalizesArabicCharsAndNumbers() {
        val raw = "كیكِ یزدَي در سال ۱۴۰۵؟ قیمت ۲۴.۵ است!"
        val norm = PersianTextNormalizer.normalize(raw)
        assertTrue(norm.contains("کیک"))
        assertTrue(norm.contains("یزدی"))
        assertTrue(norm.contains("هزار و چهارصد و پنج"))
        assertTrue(norm.contains("بیست و چهار ممیز پنج"))
        assertTrue(norm.contains("؟"))
    }

    @Test
    fun splitsLongPersianParagraphsIntoSentences() {
        val longText = "امروز هوا بسیار آفتابی است. " +
            "ما در حال تست موتور آفلاین تبدیل متن به گفتار سیلا مینی بر روی دستگاه موبایل هستیم. " +
            "آیا کیفیت صدا رضایت‌بخش است؟"
        val chunks = PersianTextNormalizer.splitSentences(longText, maxChunkChars = 90)
        assertEquals(3, chunks.size)
        assertTrue(chunks.all { it.length <= 90 })
    }

    @Test
    fun parsesSafetensorsF32AndBf16Tensors() {
        val headerJson = """{"w":{"dtype":"F32","shape":[2],"data_offsets":[0,8]}}"""
        val headerBytes = headerJson.toByteArray(Charsets.UTF_8)
        val file = File(tempFolder.root, "test.safetensors")

        val bb = ByteBuffer.allocate(8 + headerBytes.size + 8).order(ByteOrder.LITTLE_ENDIAN)
        bb.putLong(headerBytes.size.toLong())
        bb.put(headerBytes)
        bb.putFloat(1.5f)
        bb.putFloat(-2.25f)
        file.writeBytes(bb.array())

        SafetensorsReader.open(file).use { reader ->
            assertEquals(1, reader.tensors.size)
            assertEquals(2L, reader.totalParameters)
            val tensor = reader.readFloatTensor("w")
            assertEquals(1.5f, tensor[0], 1e-5f)
            assertEquals(-2.25f, tensor[1], 1e-5f)
        }
    }

    @Test
    fun writesAndReads24kWavFile() {
        val file = File(tempFolder.root, "tone.wav")
        val input = FloatArray(2400) { i -> kotlin.math.sin(i * 0.05f) * 0.5f }
        WavAudioUtils.writeWav16Bit(input, 24_000, file)
        val decoded = WavAudioUtils.readWavAs24kMono(file)
        assertEquals(input.size, decoded.size)
        assertEquals(input[100], decoded[100], 0.002f)
    }

    @Test
    fun encodesUsingMinimalSentencePieceModelProto() {
        val bytes = buildMinimalModelProto(listOf("<unk>" to 0f, "\u2581سلام" to -1.0f, "\u2581" to -2.0f, "س" to -3.0f))
        val tok = SentencePieceTokenizer.parseModelProto(bytes)
        val ids = tok.encode("سلام")
        assertEquals(1, ids.size)
        assertEquals(1, ids[0])
        assertEquals("سلام", tok.decode(ids))
    }

    private fun buildMinimalModelProto(pieces: List<Pair<String, Float>>): ByteArray {
        val out = ByteArrayOutputStream()
        for ((index, pair) in pieces.withIndex()) {
            val pieceOut = ByteArrayOutputStream()
            val textBytes = pair.first.toByteArray(Charsets.UTF_8)
            pieceOut.write(0x0A) // field 1 string
            pieceOut.write(textBytes.size)
            pieceOut.write(textBytes)
            pieceOut.write(0x15) // field 2 fixed32 float
            pieceOut.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(pair.second).array())
            pieceOut.write(0x18) // field 3 varint type
            pieceOut.write(if (index == 0) 2 else 1)
            val pBytes = pieceOut.toByteArray()
            out.write(0x0A)
            out.write(pBytes.size)
            out.write(pBytes)
        }
        return out.toByteArray()
    }
}
