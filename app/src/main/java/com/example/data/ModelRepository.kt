package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.tts.PhononPocketTtsEngine
import com.example.tts.OnnxPocketTtsEngine
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

data class ModelFileStatus(
    val name: String,
    val exists: Boolean,
    val sizeBytes: Long,
    val required: Boolean,
) {
    val sizeMb: Float get() = sizeBytes / (1024f * 1024f)
}

data class ModelInventory(
    val safetensorsReady: Boolean,
    val onnxReady: Boolean,
    val tokenizerReady: Boolean,
    val configReady: Boolean,
    val files: List<ModelFileStatus>,
    val totalSizeMb: Float,
) {
    val canRunOffline: Boolean
        get() = tokenizerReady && (safetensorsReady || onnxReady)
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
    private val onnxDir = File(modelDir, "onnx").apply { mkdirs() }
    private val promptsDir = File(modelDir, "prompts").apply { mkdirs() }

    /** Copies the model shipped in the APK into private storage once, before model inspection. */
    private fun installBundledModelIfNeeded() {
        val root = "cilla-mini"
        fun copyDirectory(assetPath: String, relativePath: String = "") {
            val children = context.assets.list(assetPath).orEmpty()
            for (child in children) {
                val source = "$assetPath/$child"
                val relative = if (relativePath.isEmpty()) child else "$relativePath/$child"
                val nested = context.assets.list(source).orEmpty()
                if (nested.isNotEmpty()) {
                    copyDirectory(source, relative)
                } else {
                    val dest = resolveTargetFile(relative)
                    if (dest.exists() && dest.length() > 1024L) continue
                    dest.parentFile?.mkdirs()
                    val temp = File(dest.parentFile, "${dest.name}.bundled-part")
                    context.assets.open(source).use { input ->
                        FileOutputStream(temp).use { output -> input.copyTo(output) }
                    }
                    if (!temp.renameTo(dest)) {
                        temp.copyTo(dest, overwrite = true)
                        temp.delete()
                    }
                }
            }
        }
        // Phonon reads the bundled checkpoint directly via WebViewAssetLoader.\n        // Do not copy its ~100MB weights to private storage.\n        if (hasPhononBundle()) return\n        copyDirectory(root)\n    }

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

    fun inspectInventory(): ModelInventory {
        installBundledModelIfNeeded()
        val stFile = getSafetensorsFile()
        val phononReady = hasPhononBundle()
        val tokFile = getTokenizerFile()
        val cfgFile = getConfigFile()
        val onnxFiles = onnxDir.listFiles()?.filter { it.isFile && it.extension.equals("onnx", true) } ?: emptyList()
        val hasMainOnnx = onnxFiles.any { it.name.startsWith("flow_lm_main", true) }
        val hasDecOnnx = onnxFiles.any { it.name.startsWith("mimi_decoder", true) }

        val statuses = mutableListOf(
            ModelFileStatus(
                name = stFile?.name ?: "model.safetensors",
                exists = stFile != null,
                sizeBytes = stFile?.length() ?: 0L,
                required = !hasMainOnnx,
            ),
            ModelFileStatus(
                name = "tokenizer.model",
                exists = tokFile != null,
                sizeBytes = tokFile?.length() ?: 0L,
                required = true,
            ),
            ModelFileStatus(
                name = "config.yaml",
                exists = cfgFile != null,
                sizeBytes = cfgFile?.length() ?: 0L,
                required = false,
            ),
        )
        for (ox in onnxFiles) {
            statuses.add(
                ModelFileStatus(
                    name = "onnx/${ox.name}",
                    exists = true,
                    sizeBytes = ox.length(),
                    required = false,
                )
            )
        }

        val totalBytes = statuses.sumOf { it.sizeBytes } + getPromptFiles().sumOf { it.length() }
        return ModelInventory(
            safetensorsReady = stFile != null || phononReady,
            onnxReady = hasMainOnnx && hasDecOnnx,
            tokenizerReady = tokFile != null || phononReady,
            configReady = cfgFile != null || phononReady,
            files = statuses,
            totalSizeMb = totalBytes / (1024f * 1024f),
        )
    }

    private fun hasPhononBundle(): Boolean = runCatching {
        context.assets.open("phonon-runtime/index.js").close()
        context.assets.open("cilla-mini/phonon/model/config.json").close()
        true
    }.getOrDefault(false)

    suspend fun loadEngine(): TtsEngine? = withContext(Dispatchers.IO) {
        if (hasPhononBundle()) return@withContext PhononPocketTtsEngine.load(context)
        val tok = getTokenizerFile() ?: return@withContext null
        val cfg = getConfigFile()
        val inv = inspectInventory()
        return@withContext when {
            hasPhononBundle() -> PhononPocketTtsEngine.load(context)
            inv.onnxReady -> OnnxPocketTtsEngine.load(onnxDir, tok, cfg)
            inv.safetensorsReady -> {
                val st = getSafetensorsFile() ?: return@withContext null
                SafetensorsPocketTtsEngine.load(st, tok, cfg)
            }
            else -> null
        }
    }

    /**
     * Downloads `mehdi-hf/cilla-mini` files directly from Hugging Face Hub.
     * Supports gated repository authentication via user's Hugging Face Read Token (`hf_...`).
     */
    suspend fun downloadFromHuggingFace(
        hfToken: String,
        onProgress: (DownloadProgress) -> Unit,
    ): DownloadOutcome = withContext(Dispatchers.IO) {
        try {
            val repoId = "mehdi-hf/cilla-mini"
            val treeFiles = mutableListOf<Pair<String, Long>>()

            // Query HuggingFace API tree to discover exact file paths & sizes
            val apiUrl = "https://huggingface.co/api/models/$repoId/tree/main"
            val treeReq = Request.Builder().url(apiUrl).apply {
                if (hfToken.isNotBlank()) header("Authorization", "Bearer ${hfToken.trim()}")
            }.build()

            client.newCall(treeReq).execute().use { resp ->
                if (resp.code == 401 || resp.code == 403) {
                    return@withContext DownloadOutcome.GatedAuthRequired(
                        "مخزن mehdi-hf/cilla-mini محدود (Gated) است. ابتدا در صفحه مدل روی Hugging Face قوانین CC BY-NC 4.0 را بپذیرید و یک توکن خواندن (hf_...) وارد کنید."
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
                    if (type == "file" && (
                            path.endsWith(".safetensors", true) ||
                                path == "tokenizer.model" ||
                                path == "config.yaml" ||
                                path.endsWith(".onnx", true)
                            )
                    ) {
                        treeFiles.add(path to size)
                    } else if (type == "directory" && (path == "prompts" || path == "phonon")) {
                        fetchSubdirFiles(repoId, path, hfToken, treeFiles)
                    }
                }
            }

            // Fallback canonical list if tree returned empty
            if (treeFiles.isEmpty()) {
                treeFiles.add("config.yaml" to 0L)
                treeFiles.add("tokenizer.model" to 0L)
                treeFiles.add("model.safetensors" to 0L)
            }

            // Prefer root model.safetensors; avoid downloading duplicate phonon safetensors if root exists
            val hasRootSafetensors = treeFiles.any { it.first == "model.safetensors" }
            val filtered = treeFiles.filter { (path, _) ->
                if (hasRootSafetensors && path.startsWith("phonon/")) false else true
            }

            for ((idx, pair) in filtered.withIndex()) {
                val (remotePath, expectedSize) = pair
                val targetFile = resolveTargetFile(remotePath)
                if (targetFile.exists() && expectedSize > 0L && targetFile.length() == expectedSize) {
                    onProgress(DownloadProgress(remotePath, idx + 1, filtered.size, expectedSize, expectedSize))
                    continue
                }

                val url = "https://huggingface.co/$repoId/resolve/main/$remotePath"
                val dlReq = Request.Builder().url(url).apply {
                    if (hfToken.isNotBlank()) header("Authorization", "Bearer ${hfToken.trim()}")
                }.build()

                client.newCall(dlReq).execute().use { dlResp ->
                    if (dlResp.code == 401 || dlResp.code == 403) {
                        return@withContext DownloadOutcome.GatedAuthRequired(
                            "دسترسی به فایل $remotePath نیازمند پذیرش مجوز در Hugging Face و وارد کردن توکن معتبر است."
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
                                onProgress(DownloadProgress(remotePath, idx + 1, filtered.size, readTotal, total))
                            }
                            output.flush()
                        }
                    }
                    if (targetFile.exists()) targetFile.delete()
                    partFile.renameTo(targetFile)
                }
            }

            DownloadOutcome.Success
        } catch (e: Exception) {
            DownloadOutcome.Error(e.localizedMessage ?: "خطا در دانلود فایل‌های مدل")
        }
    }

    private fun fetchSubdirFiles(
        repoId: String,
        subdir: String,
        hfToken: String,
        out: MutableList<Pair<String, Long>>,
    ) {
        val req = Request.Builder()
            .url("https://huggingface.co/api/models/$repoId/tree/main/$subdir")
            .apply { if (hfToken.isNotBlank()) header("Authorization", "Bearer ${hfToken.trim()}") }
            .build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return
                val arr = JSONArray(resp.body?.string() ?: "[]")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val path = obj.optString("path")
                    val type = obj.optString("type")
                    val size = obj.optLong("size", 0L)
                    if (type == "file" && (
                            path.endsWith(".wav", true) ||
                                path.endsWith(".safetensors", true)
                            )
                    ) {
                        out.add(path to size)
                    } else if (type == "directory" && path == "phonon/model") {
                        fetchSubdirFiles(repoId, path, hfToken, out)
                    }
                }
            }
        }
    }

    private fun resolveTargetFile(remotePath: String): File {
        val fileName = remotePath.substringAfterLast('/')
        return when {
            remotePath.startsWith("prompts/") -> File(promptsDir, fileName)
            fileName.endsWith(".onnx", true) -> File(onnxDir, fileName)
            else -> File(modelDir, fileName)
        }
    }

    /**
     * Zero-permission Storage Access Framework importer for local `.safetensors`, `tokenizer.model`,
     * `config.yaml`, and `.onnx` files.
     */
    suspend fun importLocalFiles(uris: List<Uri>): List<String> = withContext(Dispatchers.IO) {
        val imported = mutableListOf<String>()
        for (uri in uris) {
            val fileName = queryDisplayName(uri) ?: continue
            val dest = when {
                fileName.endsWith(".onnx", true) -> File(onnxDir, fileName)
                fileName.endsWith(".wav", true) -> File(promptsDir, fileName)
                fileName.endsWith(".safetensors", true) -> {
                    // Rename arbitrary safetensors checkpoint to model.safetensors if none exists, or keep name
                    if (fileName.contains("voice", true)) File(promptsDir, fileName)
                    else File(modelDir, "model.safetensors")
                }
                fileName.endsWith(".model", true) -> File(modelDir, "tokenizer.model")
                fileName.endsWith(".yaml", true) || fileName.endsWith(".yml", true) -> File(modelDir, "config.yaml")
                else -> File(modelDir, fileName)
            }
            dest.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output, bufferSize = 32 * 1024)
                }
                imported.add(dest.name)
            }
        }
        imported
    }

    suspend fun clearAllModels() = withContext(Dispatchers.IO) {
        modelDir.listFiles()?.forEach { it.deleteRecursively() }
        onnxDir.mkdirs()
        promptsDir.mkdirs()
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
}
