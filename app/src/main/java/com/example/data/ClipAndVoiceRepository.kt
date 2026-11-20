package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.audio.WavAudioUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class VoicePromptItem(
    val id: String,
    val label: String,
    val source: VoiceSource,
    val durationSec: Float,
    val file: File,
    val isSafetensorsCache: Boolean = false,
)

enum class VoiceSource {
    RECORDED,
    IMPORTED,
    MODEL_PROMPT,
}

data class ClipRecord(
    val id: String,
    val text: String,
    val normalizedText: String,
    val voiceLabel: String,
    val createdAtMs: Long,
    val durationSec: Float,
    val rtf: Float,
    val fileName: String,
    val waveformPeaks: List<Float>,
)

class ClipAndVoiceRepository(
    private val context: Context,
    private val modelRepository: ModelRepository,
) {
    private val clipsDir = File(context.filesDir, "clips").apply { mkdirs() }
    private val voicesDir = File(context.filesDir, "voices").apply { mkdirs() }
    private val historyFile = File(clipsDir, "history.json")

    suspend fun listVoicePrompts(): List<VoicePromptItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<VoicePromptItem>()

        // 1. User recorded & imported voices
        voicesDir.listFiles()?.sortedByDescending { it.lastModified() }?.forEach { file ->
            if (file.isFile && (file.extension.equals("wav", true) || file.extension.equals("safetensors", true))) {
                val isSt = file.extension.equals("safetensors", true)
                val dur = if (isSt) 0f else runCatching {
                    WavAudioUtils.readWavAs24kMono(file).size / 24_000f
                }.getOrDefault(0f)
                val src = if (file.name.startsWith("rec_")) VoiceSource.RECORDED else VoiceSource.IMPORTED
                items.add(
                    VoicePromptItem(
                        id = file.name,
                        label = file.nameWithoutExtension.removePrefix("rec_").replace('_', ' '),
                        source = src,
                        durationSec = dur,
                        file = file,
                        isSafetensorsCache = isSt,
                    )
                )
            }
        }

        // 2. Prompts bundled/downloaded from mehdi-hf/cilla-mini/prompts/
        modelRepository.getPromptFiles().forEach { file ->
            val isSt = file.extension.equals("safetensors", true)
            val dur = if (isSt) 0f else runCatching {
                WavAudioUtils.readWavAs24kMono(file).size / 24_000f
            }.getOrDefault(0f)
            items.add(
                VoicePromptItem(
                    id = "model_${file.name}",
                    label = file.nameWithoutExtension.replace('_', ' '),
                    source = VoiceSource.MODEL_PROMPT,
                    durationSec = dur,
                    file = file,
                    isSafetensorsCache = isSt,
                )
            )
        }
        items
    }

    suspend fun saveRecordedVoice(label: String, samples24k: FloatArray): VoicePromptItem = withContext(Dispatchers.IO) {
        val safeName = label.trim().ifEmpty { "صدای من" }.replace(Regex("""[^\p{L}\p{N}_-]"""), "_")
        val fileName = "rec_${safeName}_${System.currentTimeMillis() % 10000}.wav"
        val outFile = File(voicesDir, fileName)
        WavAudioUtils.writeWav16Bit(samples24k, 24_000, outFile)
        VoicePromptItem(
            id = outFile.name,
            label = label.trim().ifEmpty { "صدای من" },
            source = VoiceSource.RECORDED,
            durationSec = samples24k.size / 24_000f,
            file = outFile,
        )
    }

    suspend fun importVoiceUri(uri: Uri): VoicePromptItem? = withContext(Dispatchers.IO) {
        val name = queryDisplayName(uri) ?: "imported_voice_${System.currentTimeMillis() % 10000}.wav"
        val safeName = name.replace(Regex("""[^\p{L}\p{N}._-]"""), "_")
        val dest = File(voicesDir, safeName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        } ?: return@withContext null

        val isSt = dest.extension.equals("safetensors", true)
        val dur = if (isSt) 0f else runCatching {
            WavAudioUtils.readWavAs24kMono(dest).size / 24_000f
        }.getOrDefault(0f)

        VoicePromptItem(
            id = dest.name,
            label = dest.nameWithoutExtension.replace('_', ' '),
            source = VoiceSource.IMPORTED,
            durationSec = dur,
            file = dest,
            isSafetensorsCache = isSt,
        )
    }

    suspend fun deleteVoice(item: VoicePromptItem) = withContext(Dispatchers.IO) {
        if (item.source != VoiceSource.MODEL_PROMPT) {
            item.file.delete()
        }
    }

    suspend fun saveClip(
        text: String,
        normalizedText: String,
        voiceLabel: String,
        pcm24k: FloatArray,
        sampleRate: Int,
        durationSec: Float,
        rtf: Float,
    ): ClipRecord = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val fileName = "avina_${System.currentTimeMillis()}.wav"
        val outFile = File(clipsDir, fileName)
        WavAudioUtils.writeWav16Bit(pcm24k, sampleRate, outFile)
        val peaks = WavAudioUtils.computeWaveformPeaks(pcm24k)

        val record = ClipRecord(
            id = id,
            text = text,
            normalizedText = normalizedText,
            voiceLabel = voiceLabel,
            createdAtMs = System.currentTimeMillis(),
            durationSec = durationSec,
            rtf = rtf,
            fileName = fileName,
            waveformPeaks = peaks,
        )

        val current = loadClipHistory().toMutableList()
        current.add(0, record)
        writeClipHistory(current)
        record
    }

    suspend fun loadClipHistory(): List<ClipRecord> = withContext(Dispatchers.IO) {
        if (!historyFile.exists()) return@withContext emptyList()
        runCatching {
            val arr = JSONArray(historyFile.readText())
            (0 until arr.length()).mapNotNull { idx ->
                val o = arr.getJSONObject(idx)
                val fileName = o.optString("fileName")
                if (!File(clipsDir, fileName).exists()) return@mapNotNull null
                val peaksArr = o.optJSONArray("peaks")
                val peaks = if (peaksArr != null) {
                    List(peaksArr.length()) { p -> peaksArr.optDouble(p, 0.1).toFloat() }
                } else emptyList()
                ClipRecord(
                    id = o.getString("id"),
                    text = o.getString("text"),
                    normalizedText = o.optString("normalizedText", ""),
                    voiceLabel = o.optString("voiceLabel", "پیش‌فرض"),
                    createdAtMs = o.optLong("createdAtMs", 0L),
                    durationSec = o.optDouble("durationSec", 0.0).toFloat(),
                    rtf = o.optDouble("rtf", 0.0).toFloat(),
                    fileName = fileName,
                    waveformPeaks = peaks,
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun deleteClip(record: ClipRecord) = withContext(Dispatchers.IO) {
        File(clipsDir, record.fileName).delete()
        val updated = loadClipHistory().filterNot { it.id == record.id }
        writeClipHistory(updated)
    }

    fun getClipFile(record: ClipRecord): File = File(clipsDir, record.fileName)

    private fun writeClipHistory(list: List<ClipRecord>) {
        val arr = JSONArray()
        for (item in list.take(100)) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("text", item.text)
                put("normalizedText", item.normalizedText)
                put("voiceLabel", item.voiceLabel)
                put("createdAtMs", item.createdAtMs)
                put("durationSec", item.durationSec.toDouble())
                put("rtf", item.rtf.toDouble())
                put("fileName", item.fileName)
                val pArr = JSONArray()
                item.waveformPeaks.forEach { pArr.put(it.toDouble()) }
                put("peaks", pArr)
            }
            arr.put(obj)
        }
        historyFile.writeText(arr.toString())
    }

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return c.getString(idx)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }
}
