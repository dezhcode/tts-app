package com.example.data

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Manages locally imported third-party/proprietary font files (such as B Nazanin `.ttf`/`.otf`)
 * stored privately inside app storage via zero-permission Storage Access Framework.
 */
class FontRepository(private val context: Context) {

    private val customFontsDir = File(context.filesDir, "fonts").apply { mkdirs() }

    fun getCustomFontFile(fontId: String): File = File(customFontsDir, "${fontId}.ttf")

    fun isCustomFontInstalled(fontId: String): Boolean {
        val f = getCustomFontFile(fontId)
        return f.exists() && f.length() > 4096L
    }

    suspend fun loadInstalledCustomFontFamilies(): Map<String, FontFamily> = withContext(Dispatchers.IO) {
        val loaded = mutableMapOf<String, FontFamily>()
        customFontsDir.listFiles()?.forEach { file ->
            if (file.isFile && (file.extension.equals("ttf", true) || file.extension.equals("otf", true)) && file.length() > 4096L) {
                val id = file.nameWithoutExtension
                runCatching {
                    val tf = Typeface.createFromFile(file)
                    if (tf != null) {
                        loaded[id] = FontFamily(tf)
                    }
                }
            }
        }
        loaded
    }

    suspend fun importCustomFont(fontId: String, uri: Uri): Result<FontFamily> = withContext(Dispatchers.IO) {
        runCatching {
            val tempFile = File(customFontsDir, "${fontId}.tmp")
            val targetFile = getCustomFontFile(fontId)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output, bufferSize = 16 * 1024)
                }
            } ?: error("امکان خواندن فایل فونت وجود ندارد.")

            require(tempFile.length() > 4096L) { "فایل انتخاب‌شده یک فونت معتبر نیست." }
            val header = ByteArray(4)
            tempFile.inputStream().use { it.read(header) }
            val isTtf = (header[0] == 0x00.toByte() && header[1] == 0x01.toByte() && header[2] == 0x00.toByte() && header[3] == 0x00.toByte())
            val isOtf = (header[0] == 'O'.code.toByte() && header[1] == 'T'.code.toByte() && header[2] == 'T'.code.toByte() && header[3] == 'O'.code.toByte())
            val isTrue = (header[0] == 't'.code.toByte() && header[1] == 'r'.code.toByte() && header[2] == 'u'.code.toByte() && header[3] == 'e'.code.toByte())
            require(isTtf || isOtf || isTrue) { "لطفاً یک فایل فونت با فرمت استاندارد TTF یا OTF انتخاب کنید." }

            val tf = Typeface.createFromFile(tempFile) ?: error("بارگذاری فایل فونت با خطا مواجه شد.")
            if (targetFile.exists()) targetFile.delete()
            check(tempFile.renameTo(targetFile)) { "خطا در ذخیره فایل فونت." }
            FontFamily(Typeface.createFromFile(targetFile))
        }
    }

    suspend fun removeCustomFont(fontId: String) = withContext(Dispatchers.IO) {
        val f = getCustomFontFile(fontId)
        if (f.exists()) f.delete()
    }
}
