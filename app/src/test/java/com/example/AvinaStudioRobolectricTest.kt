package com.example

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import com.example.data.NarratorGender
import com.example.data.UserSettings
import com.example.ui.ModelEngineState
import com.example.ui.SynthesisUiState
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.CillaTtsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AvinaStudioRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun studioMinimalLayoutDisplaysEditorCompactGenderVoiceControlsAndPrimaryAction() {
        var text by mutableStateOf("سلام آوینا")
        var gender by mutableStateOf(NarratorGender.FEMALE)
        var synthesizeClicked = false

        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                CillaTtsTheme {
                    StudioScreen(
                        inputText = text,
                        normalizedPreview = text,
                        settings = UserSettings(narratorGender = gender),
                        modelState = ModelEngineState.Uninitialized,
                        synthesisState = SynthesisUiState.Idle,
                        voices = emptyList(),
                        activeCustomVoice = null,
                        isPlaying = false,
                        playingClipId = null,
                        playProgress = 0f,
                        onTextChange = { text = it },
                        onSelectNarratorGender = { gender = it },
                        onSelectVoice = {},
                        onSynthesize = { synthesizeClicked = true },
                        onPlayClip = {},
                        onShareClip = {},
                        onExportClip = { _, _ -> },
                        onUpdateTemperature = {},
                        onUpdateFlowSteps = {},
                        onUpdatePauseMs = {},
                        onUpdateTrimSilence = {},
                        onUpdateAutoNormalize = {},
                        onOpenModelSetup = {},
                        onNavigateToVoices = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()

        // Primary editor, compact voice selector, compact gender segmented selector, and primary TTS CTA
        composeRule.onNodeWithTag("text_input_field").assertIsDisplayed()
        composeRule.onNodeWithTag("active_voice_selector").assertIsDisplayed()
        composeRule.onNodeWithText("سارا").assertIsDisplayed()
        composeRule.onNodeWithTag("gender_segmented_selector").assertIsDisplayed()
        composeRule.onNodeWithTag("select_female_narrator").assertIsDisplayed()
        composeRule.onNodeWithTag("select_male_narrator").assertIsDisplayed()
        composeRule.onNodeWithTag("synthesize_button").assertIsDisplayed()
        composeRule.onNodeWithText("تبدیل به گفتار").assertIsDisplayed()

        // Switch gender to Male via compact segmented selector and verify voice name updates to Arash
        composeRule.onNodeWithTag("select_male_narrator").performClick()
        composeRule.waitForIdle()
        assertEquals(NarratorGender.MALE, gender)
        composeRule.onNodeWithText("آرش").assertIsDisplayed()

        // Trigger primary TTS CTA
        composeRule.onNodeWithTag("synthesize_button").performClick()
        composeRule.waitForIdle()
        assertTrue(synthesizeClicked)
    }
}
