package com.example.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.R
import com.example.data.AppFontWeight
import com.example.data.FontIds

enum class FontLanguage {
    PERSIAN,
    ENGLISH,
}

enum class FontAvailability {
    BUNDLED,
    LOCAL_IMPORT_REQUIRED,
}

data class FontCatalogEntry(
    val id: String,
    val displayName: String,
    val nativeName: String,
    val language: FontLanguage,
    val styleDescriptionFa: String,
    val styleDescriptionEn: String,
    val licenseLabel: String,
    val availability: FontAvailability,
    val supportedWeights: List<AppFontWeight>,
    val bundledFamily: FontFamily?,
    val recommendedRoleNote: String,
)

object CillaFontCatalog {

    // --- Bundled Multi-Weight Persian Font Families (SIL OFL 1.1) ---

    val VazirmatnFamily = FontFamily(
        Font(R.font.vazirmatn, FontWeight.Normal),
        Font(R.font.vazirmatn_medium, FontWeight.Medium),
        Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
        Font(R.font.vazirmatn_bold, FontWeight.Bold),
    )

    val EstedadFamily = FontFamily(
        Font(R.font.estedad_regular, FontWeight.Normal),
        Font(R.font.estedad_medium, FontWeight.Medium),
        Font(R.font.estedad_semibold, FontWeight.SemiBold),
        Font(R.font.estedad_bold, FontWeight.Bold),
    )

    val NotoNaskhArabicFamily = FontFamily(
        Font(R.font.noto_naskh_arabic, FontWeight.Normal),
        Font(R.font.noto_naskh_arabic_medium, FontWeight.Medium),
        Font(R.font.noto_naskh_arabic_semibold, FontWeight.SemiBold),
        Font(R.font.noto_naskh_arabic_bold, FontWeight.Bold),
    )

    val SahelFamily = FontFamily(
        Font(R.font.sahel_regular, FontWeight.Normal),
        Font(R.font.sahel_semibold, FontWeight.SemiBold),
        Font(R.font.sahel_bold, FontWeight.Bold),
    )

    // --- Bundled Multi-Weight English Font Families (SIL OFL 1.1) ---

    val PlayfairDisplayFamily = FontFamily(
        Font(R.font.playfair_display, FontWeight.Normal),
        Font(R.font.playfair_display_medium, FontWeight.Medium),
        Font(R.font.playfair_display_semibold, FontWeight.SemiBold),
        Font(R.font.playfair_display_bold, FontWeight.Bold),
    )

    val CormorantGaramondFamily = FontFamily(
        Font(R.font.cormorant_garamond, FontWeight.Normal),
        Font(R.font.cormorant_garamond_medium, FontWeight.Medium),
        Font(R.font.cormorant_garamond_semibold, FontWeight.SemiBold),
        Font(R.font.cormorant_garamond_bold, FontWeight.Bold),
    )

    val LibreBaskervilleFamily = FontFamily(
        Font(R.font.libre_baskerville, FontWeight.Normal),
        Font(R.font.libre_baskerville_bold, FontWeight.Bold),
    )

    val LoraFamily = FontFamily(
        Font(R.font.lora, FontWeight.Normal),
        Font(R.font.lora_medium, FontWeight.Medium),
        Font(R.font.lora_semibold, FontWeight.SemiBold),
        Font(R.font.lora_bold, FontWeight.Bold),
    )

    val InterFamily = FontFamily(
        Font(R.font.inter, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )

    // --- Technical Numbers & Telemetry ---

    val JetBrainsMonoFamily = FontFamily(
        Font(R.font.jetbrains_mono, FontWeight.Normal),
        Font(R.font.jetbrains_mono, FontWeight.Medium),
    )

    val persianFonts: List<FontCatalogEntry> = listOf(
        FontCatalogEntry(
            id = FontIds.VAZIRMATN,
            displayName = "Vazirmatn",
            nativeName = "وزیرمتن",
            language = FontLanguage.PERSIAN,
            styleDescriptionFa = "فونت اصلی رابط کاربری؛ مدرن، تمیز و بسیار خوانا",
            styleDescriptionEn = "Primary UI font; clean, modern, and highly readable",
            licenseLabel = "SIL OFL 1.1",
            availability = FontAvailability.BUNDLED,
            supportedWeights = listOf(
                AppFontWeight.REGULAR,
                AppFontWeight.MEDIUM,
                AppFontWeight.SEMIBOLD,
                AppFontWeight.BOLD,
            ),
            bundledFamily = VazirmatnFamily,
            recommendedRoleNote = "پیشنهادی متن فارسی",
        ),
        FontCatalogEntry(
            id = FontIds.ESTEDAD,
            displayName = "Estedad",
            nativeName = "استعداد",
            language = FontLanguage.PERSIAN,
            styleDescriptionFa = "هندسی و معاصر؛ ایده‌آل برای عناوین و تیترها",
            styleDescriptionEn = "Geometric and contemporary; ideal for headings",
            licenseLabel = "SIL OFL 1.1",
            availability = FontAvailability.BUNDLED,
            supportedWeights = listOf(
                AppFontWeight.REGULAR,
                AppFontWeight.MEDIUM,
                AppFontWeight.SEMIBOLD,
                AppFontWeight.BOLD,
            ),
            bundledFamily = EstedadFamily,
            recommendedRoleNote = "پیشنهادی عناوین فارسی",
        ),
        FontCatalogEntry(
            id = FontIds.NOTO_NASKH_ARABIC,
            displayName = "Noto Naskh Arabic",
            nativeName = "نوتو نسخ",
            language = FontLanguage.PERSIAN,
            styleDescriptionFa = "سنتی و ادبی؛ مناسب متون کلاسیک و کتابی",
            styleDescriptionEn = "Traditional and literary; suitable for classical text",
            licenseLabel = "SIL OFL 1.1",
            availability = FontAvailability.BUNDLED,
            supportedWeights = listOf(
                AppFontWeight.REGULAR,
                AppFontWeight.MEDIUM,
                AppFontWeight.SEMIBOLD,
                AppFontWeight.BOLD,
            ),
            bundledFamily = NotoNaskhArabicFamily,
            recommendedRoleNote = "ادبی و کلاسیک",
        ),
        FontCatalogEntry(
            id = FontIds.SAHEL,
            displayName = "Sahel",
            nativeName = "ساحل",
            language = FontLanguage.PERSIAN,
            styleDescriptionFa = "نرم، صمیمی و روان برای خواندن طولانی",
            styleDescriptionEn = "Soft, friendly, and readable",
            licenseLabel = "SIL OFL 1.1",
            availability = FontAvailability.BUNDLED,
            supportedWeights = listOf(
                AppFontWeight.REGULAR,
                AppFontWeight.SEMIBOLD,
                AppFontWeight.BOLD,
            ),
            bundledFamily = SahelFamily,
            recommendedRoleNote = "صمیمی و نرم",
        ),
        FontCatalogEntry(
            id = FontIds.B_NAZANIN,
            displayName = "B Nazanin",
            nativeName = "بی‌نازنین",
            language = FontLanguage.PERSIAN,
            styleDescriptionFa = "تایپوگرافی سنتی فارسی با وقار رسمی و کلاسیک",
            styleDescriptionEn = "Traditional Persian typography with a formal, classic character",
            licenseLabel = "اختصاصی (نیازمند فایل دستگاه)",
            availability = FontAvailability.LOCAL_IMPORT_REQUIRED,
            supportedWeights = listOf(AppFontWeight.REGULAR),
            bundledFamily = null,
            recommendedRoleNote = "رسمی و سنتی",
        ),
    )

    val englishFonts: List<FontCatalogEntry> = listOf(
        FontCatalogEntry(
            id = FontIds.PLAYFAIR_DISPLAY,
            displayName = "Playfair Display",
            nativeName = "Playfair Display",
            language = FontLanguage.ENGLISH,
            styleDescriptionFa = "باوقار، مجله‌ای و لوکس برای تیترهای انگلیسی",
            styleDescriptionEn = "Elegant, luxurious, and editorial",
            licenseLabel = "SIL OFL 1.1",
            availability = FontAvailability.BUNDLED,
            supportedWeights = listOf(
                AppFontWeight.REGULAR,
                AppFontWeight.MEDIUM,
                AppFontWeight.SEMIBOLD,
                AppFontWeight.BOLD,
            ),
            bundledFamily = PlayfairDisplayFamily,
            recommendedRoleNote = "پیشنهادی عناوین انگلیسی",
        ),
        FontCatalogEntry(
            id = FontIds.CORMORANT_GARAMOND,
            displayName = "Cormorant Garamond",
            nativeName = "Cormorant Garamond",
            language = FontLanguage.ENGLISH,
            styleDescriptionFa = "هنری، ظریف و کاملاً کلاسیک",
            styleDescriptionEn = "Refined, artistic, and highly classical",
            licenseLabel = "SIL OFL 1.1",
            availability = FontAvailability.BUNDLED,
            supportedWeights = listOf(
                AppFontWeight.REGULAR,
                AppFontWeight.MEDIUM,
                AppFontWeight.SEMIBOLD,
                AppFontWeight.BOLD,
            ),
            bundledFamily = CormorantGaramondFamily,
            recommendedRoleNote = "کلاسیک و هنری",
        ),
        FontCatalogEntry(
            id = FontIds.LIBRE_BASKERVILLE,
            displayName = "Libre Baskerville",
            nativeName = "Libre Baskerville",
            language = FontLanguage.ENGLISH,
            styleDescriptionFa = "سنتی، متعادل و با خوانایی بالا",
            styleDescriptionEn = "Traditional, balanced, and readable",
            licenseLabel = "SIL OFL 1.1",
            availability = FontAvailability.BUNDLED,
            supportedWeights = listOf(
                AppFontWeight.REGULAR,
                AppFontWeight.BOLD,
            ),
            bundledFamily = LibreBaskervilleFamily,
            recommendedRoleNote = "سنتی و متوازن",
        ),
        FontCatalogEntry(
            id = FontIds.LORA,
            displayName = "Lora",
            nativeName = "Lora",
            language = FontLanguage.ENGLISH,
            styleDescriptionFa = "تعادل هوشمندانه میان کلاسیک و مدرن",
            styleDescriptionEn = "A sophisticated balance between classic and modern",
            licenseLabel = "SIL OFL 1.1",
            availability = FontAvailability.BUNDLED,
            supportedWeights = listOf(
                AppFontWeight.REGULAR,
                AppFontWeight.MEDIUM,
                AppFontWeight.SEMIBOLD,
                AppFontWeight.BOLD,
            ),
            bundledFamily = LoraFamily,
            recommendedRoleNote = "تعادل کلاسیک و مدرن",
        ),
        FontCatalogEntry(
            id = FontIds.INTER,
            displayName = "Inter",
            nativeName = "Inter",
            language = FontLanguage.ENGLISH,
            styleDescriptionFa = "مینیمال، تمیز و استاندارد برای اجزای رابط کاربری",
            styleDescriptionEn = "Minimal, clean, and suitable for interface elements",
            licenseLabel = "SIL OFL 1.1",
            availability = FontAvailability.BUNDLED,
            supportedWeights = listOf(
                AppFontWeight.REGULAR,
                AppFontWeight.MEDIUM,
                AppFontWeight.SEMIBOLD,
                AppFontWeight.BOLD,
            ),
            bundledFamily = InterFamily,
            recommendedRoleNote = "پیشنهادی رابط کاربری انگلیسی",
        ),
    )

    val allFonts: List<FontCatalogEntry> = persianFonts + englishFonts

    fun findById(id: String): FontCatalogEntry? = allFonts.firstOrNull { it.id == id }

    fun isInstalled(entry: FontCatalogEntry, customFonts: Map<String, FontFamily>): Boolean =
        entry.bundledFamily != null || customFonts.containsKey(entry.id)

    fun resolveFontFamily(
        fontId: String,
        customFonts: Map<String, FontFamily>,
        fallback: FontFamily = VazirmatnFamily,
    ): FontFamily {
        customFonts[fontId]?.let { return it }
        return findById(fontId)?.bundledFamily ?: fallback
    }

    /**
     * Resolves the closest natively supported FontWeight for a given font family so
     * weights degrade gracefully without synthetic distortion.
     */
    fun resolveWeightFor(entry: FontCatalogEntry?, requested: AppFontWeight): FontWeight {
        if (entry == null || entry.supportedWeights.isEmpty()) return requested.composeWeight
        if (requested in entry.supportedWeights) return requested.composeWeight
        return entry.supportedWeights.minBy { kotlin.math.abs(it.numericValue - requested.numericValue) }.composeWeight
    }
}
