package com.example

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppFontWeight
import com.example.data.AppPreferences
import com.example.data.FontIds
import com.example.data.TypographySettings
import com.example.ui.screens.FontLibraryScreen
import com.example.ui.theme.CillaFontCatalog
import com.example.ui.theme.CillaTtsTheme
import com.example.ui.theme.FontAvailability
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FontLibraryRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun catalogMatchesRequestedPersianAndEnglishFontsAndDefaults() {
        val defaults = TypographySettings()
        assertEquals(FontIds.VAZIRMATN, defaults.persianUiFontId)
        assertEquals(FontIds.ESTEDAD, defaults.persianHeadingFontId)
        assertEquals(FontIds.INTER, defaults.englishUiFontId)
        assertEquals(FontIds.PLAYFAIR_DISPLAY, defaults.englishHeadingFontId)
        assertEquals(AppFontWeight.REGULAR, defaults.persianWeight)
        assertEquals(AppFontWeight.REGULAR, defaults.englishWeight)

        assertEquals(5, CillaFontCatalog.persianFonts.size)
        assertEquals(5, CillaFontCatalog.englishFonts.size)

        // All open-source SIL OFL fonts are bundled; proprietary B Nazanin requires local import
        val bundledCount = CillaFontCatalog.allFonts.count { it.availability == FontAvailability.BUNDLED }
        assertEquals(9, bundledCount)
        val nazanin = CillaFontCatalog.findById(FontIds.B_NAZANIN)!!
        assertEquals(FontAvailability.LOCAL_IMPORT_REQUIRED, nazanin.availability)
        assertFalse(CillaFontCatalog.isInstalled(nazanin, emptyMap()))
        assertTrue(CillaFontCatalog.isInstalled(CillaFontCatalog.findById(FontIds.ESTEDAD)!!, emptyMap()))
    }

    @Test
    fun typographyPreferencesPersistAcrossInstancesAndResetCleanly() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsFirst = AppPreferences(context)

        prefsFirst.setPersianUiFont(FontIds.SAHEL)
        prefsFirst.setPersianHeadingFont(FontIds.NOTO_NASKH_ARABIC)
        prefsFirst.setPersianWeight(AppFontWeight.SEMIBOLD)
        prefsFirst.setEnglishUiFont(FontIds.LORA)
        prefsFirst.setEnglishHeadingFont(FontIds.CORMORANT_GARAMOND)
        prefsFirst.setEnglishWeight(AppFontWeight.MEDIUM)

        // Create a fresh AppPreferences instance simulating app restart
        val prefsReloaded = AppPreferences(context)
        val restored = prefsReloaded.settings.first().typography
        assertEquals(FontIds.SAHEL, restored.persianUiFontId)
        assertEquals(FontIds.NOTO_NASKH_ARABIC, restored.persianHeadingFontId)
        assertEquals(AppFontWeight.SEMIBOLD, restored.persianWeight)
        assertEquals(FontIds.LORA, restored.englishUiFontId)
        assertEquals(FontIds.CORMORANT_GARAMOND, restored.englishHeadingFontId)
        assertEquals(AppFontWeight.MEDIUM, restored.englishWeight)
        assertFalse(restored.isDefault)

        prefsReloaded.resetTypographyToDefaults()
        val afterReset = prefsReloaded.settings.first().typography
        assertTrue(afterReset.isDefault)
        assertEquals(FontIds.VAZIRMATN, afterReset.persianUiFontId)
        assertEquals(FontIds.ESTEDAD, afterReset.persianHeadingFontId)
        assertEquals(FontIds.INTER, afterReset.englishUiFontId)
        assertEquals(FontIds.PLAYFAIR_DISPLAY, afterReset.englishHeadingFontId)
    }

    @Test
    fun fontLibraryScreenRendersBilingualPreviewsAndSwitchesCategories() {
        var selectedFaUi = FontIds.VAZIRMATN
        var selectedEnUi = FontIds.INTER
        var selectedWeight = AppFontWeight.REGULAR

        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                CillaTtsTheme {
                    FontLibraryScreen(
                        typographySettings = TypographySettings(
                            persianUiFontId = selectedFaUi,
                            englishUiFontId = selectedEnUi,
                            persianWeight = selectedWeight,
                        ),
                        customFonts = emptyMap(),
                        onSelectPersianUiFont = { selectedFaUi = it },
                        onSelectPersianHeadingFont = {},
                        onSelectPersianWeight = { selectedWeight = it },
                        onSelectEnglishUiFont = { selectedEnUi = it },
                        onSelectEnglishHeadingFont = {},
                        onSelectEnglishWeight = {},
                        onImportCustomFontFile = { _, _, _ -> },
                        onRemoveCustomFontFile = {},
                        onResetDefaults = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()

        // Verify live bilingual preview renders both Persian RTL and English LTR sample sentences
        composeRule.onNodeWithTag("live_bilingual_preview").assertIsDisplayed()
        composeRule.onAllNodesWithText("زیبایی در سادگی است.", substring = true).onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("صدای خود را با کیفیتی متفاوت تجربه کنید.", substring = true).onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("Elegance in every detail.", substring = true).onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("Your voice, your style.", substring = true).onFirst().assertIsDisplayed()

        // Switch to English fonts tab and verify English font cards appear
        composeRule.onNodeWithTag("font_library_list").performScrollToNode(hasTestTag("tab_english_fonts"))
        composeRule.onNodeWithTag("tab_english_fonts").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("font_library_list").performScrollToNode(hasTestTag("font_card_playfair_display"))
        composeRule.onNodeWithTag("font_card_playfair_display").assertIsDisplayed()

        // Switch back to Persian fonts and select Medium weight
        composeRule.onNodeWithTag("font_library_list").performScrollToNode(hasTestTag("tab_persian_fonts"))
        composeRule.onNodeWithTag("tab_persian_fonts").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("font_library_list").performScrollToNode(hasTestTag("weight_medium"))
        composeRule.onNodeWithTag("weight_medium").performClick()
        composeRule.waitForIdle()
        assertEquals(AppFontWeight.MEDIUM, selectedWeight)
    }
}
