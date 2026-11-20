package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.tts.OnnxPocketTtsEngine
import com.example.tts.PhononPocketTtsEngine
import com.example.tts.SafetensorsPocketTtsEngine
import com.example.tts.TtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

enum class PhononModelSource {
    BUNDLED_APK,
    LOCAL_STORAGE,
    NONE,
}

data class BundledVoiceAsset(
    val id: String,
    val label: String,
    val gender: NarratorGender?,
    val urlPath: String,
    val isBundledAsset: Boolean,
    val localFile: File? = null,
)

data class ModelFileStatus(
    val name: String,
    val exists: Boolean,
    val sizeBytes: Long,
    val required: Boolean,
) {
    val sizeMb: Float get() = sizeBytes / (1024f * 1024f)
}

data class ModelInventory(
    val phononReady: Boolean = false,
    val phononRuntimeReady: Boolean = false,
    val phononSource: PhononModelSource = PhononModelSource.NONE,
    val availablePhononVoices: List<BundledVoiceAsset> = emptyList(),
    val missingPhononAssets: List<String> = emptyList(),
    val safetensorsReady: Boolean = false,
    val onnxReady: Boolean = false,
    val tokenizerReady: Boolean = false,
    val configReady: Boolean = false,
    val files: List<ModelFileStatus> = emptyList(),
    val totalSizeMb: Float = 0f,
) {
    val canRunOffline: Boolean
        get() = phononReady || (tokenizerReady && (safetensorsReady || onnxReady))
}

data class DownloadProgress(
    val currentFile: String,
    val fileIndex: Int,
    val totalFiles: Int,
    val bytesRead: Long,
    val totalBytes: Long,
) {
    val fraction: Float
        get() = if (totalBytes > 0L) (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

sealed interface DownloadOutcome {
    data object Success : DownloadOutcome
    data class GatedAuthRequired(val message: String) : DownloadOutcome
    data class Error(val message: String) : DownloadOutcome
}

class ModelRepository(private val context: Context) {

    private val modelDir = File(context.filesDir, "models/cilla-mini").apply { mkdirs() }
    private val localPhononModelDir = File(modelDir, "phonon/model").apply { mkdirs() }
    private val localPhononVoicesDir = File(localPhononModelDir, "voices").apply { mkdirs() }
    private val onnxDir = File(modelDir, "onnx").apply { mkdirs() }
    private val promptsDir = File(modelDir, "prompts").apply { mkdirs() }

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun getSafetensorsFile(): File? {
        val primary = File(modelDir, "model.safetensors")
        if (primary.exists() && primary.length() > 1024) return primary
        return modelDir.listFiles()?.firstOrNull {
            it.isFile && it.extension.equals("safetensors", ignoreCase = true) && it.length() > 1024
        }
    }

    fun getTokenizerFile(): File? {
        val f = File(modelDir, "tokenizer.model")
        return if (f.exists() && f.length() > 128) f else null
    }

    fun getConfigFile(): File? {
        val f = File(modelDir, "config.yaml")
        return if (f.exists() && f.length() > 8) f else null
    }

    fun getPromptFiles(): List<File> =
        promptsDir.listFiles()
            ?.filter { it.isFile && (it.extension.equals("wav", true) || it.extension.equals("safetensors", true)) }
            ?.sortedBy { it.name }
            ?: emptyList()

    private fun assetExistsNonEmpty(path: String): Boolean =
        runCatching { context.assets.open(path).use { it.read() != -1 } }.getOrDefault(false)

    private fun assetByteSize(path: String): Long =
        runCatching {
            context.assets.openFd(path).use { it.length }
        }.getOrElse {
            runCatching {
                context.assets.open(path).use { input ->
                    var total = 0L
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        total += n
                    }
                    total
                }
            }.getOrDefault(0L)
        }

    fun missingPhononRuntimeAssets(): List<String> = PHONON_RUNTIME_REQUIRED.filterNot(::assetExistsNonEmpty)

    fun hasPhononRuntime(): Boolean = missingPhononRuntimeAssets().isEmpty()

    fun missingBundledPhononModelAssets(): List<String> =
        PHONON_MODEL_REQUIRED.filter { rel -> !assetExistsNonEmpty("$BUNDLED_MODEL_ASSET_ROOT/$rel") }

    fun hasBundledPhononModel(): Boolean = hasPhononRuntime() && missingBundledPhononModelAssets().isEmpty()

    fun missingLocalPhononModelAssets(): List<String> =
        PHONON_MODEL_REQUIRED.filter { rel ->
            val file = File(localPhononModelDir, rel)
            val minBytes = if (rel.endsWith(".json", ignoreCase = true)) 16L else 1024L
            !file.isFile || file.length() < minBytes
        }

    fun hasLocalPhononModel(): Boolean = hasPhononRuntime() && missingLocalPhononModelAssets().isEmpty()

    fun missingPhononAssets(): List<String> {
        val runtimeMissing = missingPhononRuntimeAssets()
        val modelMissing = if (missingBundledPhononModelAssets().isEmpty()) {
            emptyList()
        } else if (missingLocalPhononModelAssets().isEmpty()) {
            emptyList()
        } else {
            missingBundledPhononModelAssets().map { "cilla-mini/phonon/model/$it" }
        }
        return runtimeMissing + modelMissing
    }

    fun discoverBundledVoices(): List<BundledVoiceAsset> {
        val voicesAssetDir = "$BUNDLED_MODEL_ASSET_ROOT/voices"
        val entries = runCatching { context.assets.list(voicesAssetDir).orEmpty() }.getOrDefault(emptyArray())
        return entries
            .filter { it.endsWith(".safetensors", ignoreCase = true) }
            .filter { assetExistsNonEmpty("$voicesAssetDir/$it") }
            .sorted()
            .map { fileName ->
                val id = fileName.substringBeforeLast('.')
                BundledVoiceAsset(
                    id = id,
                    label = displayLabelForVoiceId(id),
                    gender = inferGenderForVoiceId(id),
                    urlPath = "../cilla-mini/phonon/model/voices/$fileName",
                    isBundledAsset = true,
                    localFile = null,
                )
            }
    }

    fun discoverLocalPhononVoices(): List<BundledVoiceAsset> {
        val files = localPhononVoicesDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("safetensors", ignoreCase = true) && it.length() >= 128L }
            ?.sortedBy { it.name }
            ?: emptyList()
        return files.map { file ->
            val id = file.nameWithoutExtension
            BundledVoiceAsset(
                id = id,
                label = displayLabelForVoiceId(id),
                gender = inferGenderForVoiceId(id),
                urlPath = "/local-model/voices/${file.name}",
                isBundledAsset = false,
                localFile = file,
            )
        }
    }

    fun inspectInventory(): ModelInventory {
        val runtimeReady = hasPhononRuntime()
        val bundledReady = hasBundledPhononModel()
        val localPhononReady = hasLocalPhononModel()
        val phononSource = when {
            bundledReady -> PhononModelSource.BUNDLED_APK
            localPhononReady -> PhononModelSource.LOCAL_STORAGE
            else -> PhononModelSource.NONE
        }
        val phononReady = phononSource != PhononModelSource.NONE
        val availableVoices = when (phononSource) {
            PhononModelSource.BUNDLED_APK -> discoverBundledVoices()
            PhononModelSource.LOCAL_STORAGE -> discoverLocalPhononVoices()
            PhononModelSource.NONE -> discoverBundledVoices().ifEmpty { discoverLocalPhononVoices() }
        }

        // Build clean, honest Phonon file status list
        val statuses = mutableListOf<ModelFileStatus>()
        statuses.add(
            ModelFileStatus(
                name = "phonon-runtime (WASM)",
                exists = runtimeReady,
                sizeBytes = if (runtimeReady) assetByteSize("phonon-runtime/wasm/phonon_tts_bg.wasm") else 0L,
                required = true,
            )
        )
        for (rel in PHONON_MODEL_REQUIRED) {
            val bundledExists = assetExistsNonEmpty("$BUNDLED_MODEL_ASSET_ROOT/$rel")
            val localFile = File(localPhononModelDir, rel)
            val localExists = localFile.isFile && localFile.length() >= 128L
            val exists = bundledExists || localExists
            val sizeBytes = when {
                bundledExists -> assetByteSize("$BUNDLED_MODEL_ASSET_ROOT/$rel")
                localExists -> localFile.length()
                else -> 0L
            }
            statuses.add(
                ModelFileStatus(
                    name = rel,
                    exists = exists,
                    sizeBytes = sizeBytes,
                    required = true,
                )
            )
        }

        // Legacy fallback inspection (only used if user manually imported legacy root/ONNX weights)
        val stFile = getSafetensorsFile()
        val tokFile = getTokenizerFile()
        val cfgFile = getConfigFile()
        val onnxFiles = onnxDir.listFiles()?.filter { it.isFile && it.extension.equals("onnx", true) } ?: emptyList()
        val hasMainOnnx = onnxFiles.any { it.name.startsWith("flow_lm_main", true) }
        val hasDecOnnx = onnxFiles.any { it.name.startsWith("mimi_decoder", true) }

        val totalBytes = statuses.sumOf { it.sizeBytes } +
            (stFile?.length() ?: 0L) +
            (tokFile?.length() ?: 0L) +
            onnxFiles.sumOf { it.length() } +
            getPromptFiles().sumOf { it.length() }

        return ModelInventory(
            phononReady = phononReady,
            phononRuntimeReady = runtimeReady,
            phononSource = phononSource,
            availablePhononVoices = availableVoices,
            missingPhononAssets = missingPhononAssets(),
            safetensorsReady = stFile != null || phononReady,
            onnxReady = hasMainOnnx && hasDecOnnx,
            tokenizerReady = tokFile != null || phononReady,
            configReady = cfgFile != null || phononReady,
            files = statuses,
            totalSizeMb = totalBytes / (1024f * 1024f),
        )
    }

    suspend fun loadEngine(): TtsEngine? = withContext(Dispatchers.IO) {
        // Priority 1: Bundled APK Phonon WASM checkpoint (zero copy, zero network)
        if (hasBundledPhononModel()) {
            val voices = discoverBundledVoices()
            val voiceMap = voices.associate { it.id to it.urlPath }
            val defaultVoice = voiceMap.keys.firstOrNull { it == PhononPocketTtsEngine.DEFAULT_VOICE_ID }
                ?: voiceMap.keys.first()
            return@withContext PhononPocketTtsEngine.load(
                context = context,
                modelBaseUrl = "../cilla-mini/phonon/model/",
                voicesMap = voiceMap,
                defaultVoiceId = defaultVoice,
                localModelRootDir = null,
                modelSizeMb = inspectInventory().totalSizeMb.takeIf { it > 1f } ?: 107f,
            )
        }

        // Priority 2: Locally downloaded / imported Phonon WASM checkpoint (zero network after install)
        if (hasLocalPhononModel()) {
            val voices = discoverLocalPhononVoices()
            val voiceMap = voices.associate { it.id to it.urlPath }
            val defaultVoice = voiceMap.keys.firstOrNull { it == PhononPocketTtsEngine.DEFAULT_VOICE_ID }
                ?: voiceMap.keys.first()
            return@withContext PhononPocketTtsEngine.load(
                context = context,
                modelBaseUrl = "/local-model/",
                voicesMap = voiceMap,
                defaultVoiceId = defaultVoice,
                localModelRootDir = localPhononModelDir,
                modelSizeMb = inspectInventory().totalSizeMb.takeIf { it > 1f } ?: 107f,
            )
        }

        // Priority 3: Legacy ONNX / Safetensors fallback if manually imported
        val tok = getTokenizerFile() ?: return@withContext null
        val cfg = getConfigFile()
        val inv = inspectInventory()
        return@withContext when {
            inv.onnxReady -> OnnxPocketTtsEngine.load(onnxDir, tok, cfg)
            inv.safetensorsReady -> {
                val st = getSafetensorsFile() ?: return@withContext null
                SafetensorsPocketTtsEngine.load(st, tok, cfg)
            }
            else -> null
        }
    }

    /**
     * Downloads the Phonon checkpoint (JSON, safetensors, and voice safetensors files under phonon/model)
     * from `mehdi-hf/cilla-mini` using the user's Hugging Face read token (`hf_...`).
     * Never used during ordinary offline speech synthesis once bundled or installed.
     */
    suspend fun downloadFromHuggingFace(
        hfToken: String,
        onProgress: (DownloadProgress) -> Unit,
    ): DownloadOutcome = withContext(Dispatchers.IO) {
        try {
            val repoId = "mehdi-hf/cilla-mini"
            val treeFiles = mutableListOf<Pair<String, Long>>()

            val apiUrl = "https://huggingface.co/api/models/$repoId/tree/main/phonon/model?recursive=true&expand=false"
            val treeReq = Request.Builder().url(apiUrl).apply {
                if (hfToken.isNotBlank()) header("Authorization", "Bearer ${hfToken.trim()}")
            }.build()

            client.newCall(treeReq).execute().use { resp ->
                if (resp.code == 401 || resp.code == 403) {
                    return@withContext DownloadOutcome.GatedAuthRequired(
                        "دسترسی به مخزن mehdi-hf/cilla-mini نیازمند پذیرش مجوز در Hugging Face و وارد کردن توکن خواندن (hf_...) است."
                    )
                }
                if (!resp.isSuccessful) {
                    return@withContext DownloadOutcome.Error("خطا در ارتباط با Hugging Face (کد ${resp.code})")
                }
                val arr = JSONArray(resp.body?.string() ?: "[]")
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val path = item.optString("path")
                    val type = item.optString("type")
                    val size = item.optLong("size", 0L)
                    if (type == "file" &&
                        path.startsWith("phonon/model/") &&
                        (path.endsWith(".json", true) || path.endsWith(".safetensors", true))
                    ) {
                        treeFiles.add(path to size)
                    }
                }
            }

            if (treeFiles.isEmpty()) {
                PHONON_MODEL_REQUIRED.forEach { rel ->
                    treeFiles.add("phonon/model/$rel" to 0L)
                }
            }

            for ((idx, pair) in treeFiles.withIndex()) {
                val (remotePath, expectedSize) = pair
                val targetFile = resolveTargetFile(remotePath)
                if (targetFile.exists() && expectedSize > 0L && targetFile.length() == expectedSize) {
                    onProgress(DownloadProgress(remotePath, idx + 1, treeFiles.size, expectedSize, expectedSize))
                    continue
                }

                val url = "https://huggingface.co/$repoId/resolve/main/$remotePath"
                val dlReq = Request.Builder().url(url).apply {
                    if (hfToken.isNotBlank()) header("Authorization", "Bearer ${hfToken.trim()}")
                }.build()

                client.newCall(dlReq).execute().use { dlResp ->
                    if (dlResp.code == 401 || dlResp.code == 403) {
                        return@withContext DownloadOutcome.GatedAuthRequired(
                            "دسترسی به فایل $remotePath نیازمند پذیرش مجوز و توکن معتبر است."
                        )
                    }
                    if (!dlResp.isSuccessful) {
                        return@withContext DownloadOutcome.Error("خطا در دانلود $remotePath (کد ${dlResp.code})")
                    }
                    val body = dlResp.body ?: return@withContext DownloadOutcome.Error("پاسخ خالی برای $remotePath")
                    val total = body.contentLength().takeIf { it > 0 } ?: expectedSize
                    val partFile = File(targetFile.parentFile, "${targetFile.name}.part")
                    targetFile.parentFile?.mkdirs()

                    body.byteStream().use { input ->
                        FileOutputStream(partFile).use { output ->
                            val buf = ByteArray(32 * 1024)
                            var readTotal = 0L
                            while (true) {
                                val read = input.read(buf)
                                if (read <= 0) break
                                output.write(buf, 0, read)
                                readTotal += read
                                onProgress(DownloadProgress(remotePath, idx + 1, treeFiles.size, readTotal, total))
                            }
                            output.flush()
                        }
                    }
                    if (targetFile.exists()) targetFile.delete()
                    if (!partFile.renameTo(targetFile)) {
                        partFile.copyTo(targetFile, overwrite = true)
                        partFile.delete()
                    }
                }
            }

            DownloadOutcome.Success
        } catch (e: Exception) {
            DownloadOutcome.Error(e.localizedMessage ?: "خطا در دانلود فایل‌های مدل")
        }
    }

    private fun resolveTargetFile(remotePath: String): File {
        return when {
            remotePath.startsWith("phonon/model/") -> {
                val rel = remotePath.removePrefix("phonon/model/")
                File(localPhononModelDir, rel)
            }
            remotePath.startsWith("prompts/") -> File(promptsDir, remotePath.substringAfterLast('/'))
            remotePath.endsWith(".onnx", true) -> File(onnxDir, remotePath.substringAfterLast('/'))
            else -> File(modelDir, remotePath.substringAfterLast('/'))
        }
    }

    /**
     * Zero-permission Storage Access Framework importer supporting Phonon model files
     * (`config.json`, `tokenizer.json`, `model.safetensors`, and voice safetensors) as well as
     * legacy `.model` / `.yaml` / `.onnx` files.
     */
    suspend fun importLocalFiles(uris: List<Uri>): List<String> = withContext(Dispatchers.IO) {
        val imported = mutableListOf<String>()
        for (uri in uris) {
            val fileName = queryDisplayName(uri) ?: continue
            val lower = fileName.lowercase()
            val temp = File(modelDir, "${fileName}.import-part")
            temp.parentFile?.mkdirs()
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { output ->
                    input.copyTo(output, bufferSize = 32 * 1024)
                }
                true
            } ?: false
            if (!copied) {
                temp.delete()
                continue
            }

            val dest = when {
                lower == "config.json" -> File(localPhononModelDir, "config.json")
                lower == "tokenizer.json" -> File(localPhononModelDir, "tokenizer.json")
                lower == "model.safetensors" -> File(localPhononModelDir, "model.safetensors")
                lower.endsWith(".safetensors") -> {
                    // Voice state safetensors are small (< 15MB) or named parisa/voice*; weights are > 15MB
                    val isVoiceAsset = lower.contains("parisa") ||
                        lower.contains("voice") ||
                        temp.length() < 15L * 1024L * 1024L
                    if (isVoiceAsset) {
                        File(localPhononVoicesDir, fileName)
                    } else {
                        File(localPhononModelDir, "model.safetensors")
                    }
                }
                lower.endsWith(".onnx") -> File(onnxDir, fileName)
                lower.endsWith(".wav") -> File(promptsDir, fileName)
                lower.endsWith(".model") -> File(modelDir, "tokenizer.model")
                lower.endsWith(".yaml") || lower.endsWith(".yml") -> File(modelDir, "config.yaml")
                else -> File(modelDir, fileName)
            }
            dest.parentFile?.mkdirs()
            if (dest.exists()) dest.delete()
            if (!temp.renameTo(dest)) {
                temp.copyTo(dest, overwrite = true)
                temp.delete()
            }
            imported.add(dest.name)
        }
        imported
    }

    suspend fun clearAllModels(): Unit = withContext(Dispatchers.IO) {
        modelDir.listFiles()?.forEach { it.deleteRecursively() }
        localPhononVoicesDir.mkdirs()
        onnxDir.mkdirs()
        promptsDir.mkdirs()
        Unit
    }

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    companion object {
        const val BUNDLED_MODEL_ASSET_ROOT = "cilla-mini/phonon/model"

        val PHONON_RUNTIME_REQUIRED = listOf(
            "phonon-runtime/index.js",
            "phonon-runtime/worker.js",
            "phonon-runtime/wasm/phonon_tts_bg.wasm",
        )

        val PHONON_MODEL_REQUIRED = listOf(
            "config.json",
            "tokenizer.json",
            "model.safetensors",
            "voices/parisa.safetensors",
        )

        fun displayLabelForVoiceId(id: String): String = when (id.lowercase()) {
            "parisa" -> "پریسا"
            "voice1" -> "صدای ۱"
            "voice2" -> "صدای ۲"
            else -> id.replace('_', ' ').replace('-', ' ')
        }

        fun inferGenderForVoiceId(id: String): NarratorGender? {
            val lower = id.lowercase()
            return when {
                lower == "parisa" || lower == "voice1" ||
                    lower.contains("female") || lower.contains("woman") ||
                    lower.contains("zan") || lower.contains("sara") -> NarratorGender.FEMALE
                lower == "voice2" ||
                    lower.contains("male") || lower.contains("man") ||
                    lower.contains("mard") || lower.contains("arash") ||
                    lower.contains("ali") -> NarratorGender.MALE
                else -> null
            }
        }
    }
}
