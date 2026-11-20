package com.example

import android.content.Context
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
import androidx.test.core.app.ApplicationProvider
import com.example.data.BundledVoiceAsset
import com.example.data.ModelInventory
import com.example.data.ModelRepository
import com.example.data.NarratorGender
import com.example.data.PhononModelSource
import com.example.data.UserSettings
import com.example.tts.PersianTextNormalizer
import com.example.ui.MainViewModel
import com.example.ui.ModelEngineState
import com.example.ui.SynthesisUiState
import com.example.ui.VoiceAvailabilityState
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.CillaTtsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AvinaStudioRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun studioMinimalLayoutPreservesAvinaUiAndHonestVoiceAvailability() {
        var text by mutableStateOf("سلام آوینا")
        var gender by mutableStateOf(NarratorGender.FEMALE)
        var synthesizeClicked = false

        val parisaVoice = BundledVoiceAsset(
            id = "parisa",
            label = "پریسا",
            gender = NarratorGender.FEMALE,
            urlPath = "https://appassets.androidplatform.net/assets/cilla-mini/phonon/model/voices/parisa.safetensors",
            isBundledAsset = true,
        )
        val availability = VoiceAvailabilityState(
            activeVoiceId = parisaVoice.id,
            activeVoiceLabel = parisaVoice.label,
            activeGender = NarratorGender.FEMALE,
            isFemaleAvailable = true,
            isMaleAvailable = false,
            availablePhononVoices = listOf(parisaVoice),
            isUsingCustomRecordedVoice = false,
            isPhononBackend = true,
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                CillaTtsTheme {
                    StudioScreen(
                        inputText = text,
                        normalizedPreview = text,
                        settings = UserSettings(narratorGender = gender),
                        modelState = ModelEngineState.Uninitialized,
                        synthesisState = SynthesisUiState.Idle,
                        voiceAvailability = availability,
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

        // Preserved Avina UI: editor, compact voice selector, compact gender selector, primary TTS button
        composeRule.onNodeWithTag("text_input_field").assertIsDisplayed()
        composeRule.onNodeWithTag("active_voice_selector").assertIsDisplayed()
        composeRule.onNodeWithText("پریسا").assertIsDisplayed()
        composeRule.onNodeWithTag("gender_segmented_selector").assertIsDisplayed()
        composeRule.onNodeWithTag("select_female_narrator").assertIsDisplayed()
        composeRule.onNodeWithTag("select_male_narrator").assertIsDisplayed()
        composeRule.onNodeWithTag("synthesize_button").assertIsDisplayed()
        composeRule.onNodeWithText("تبدیل به گفتار").assertIsDisplayed()

        // Male option has no verified voice asset -> clicking it must NOT switch gender
        composeRule.onNodeWithTag("select_male_narrator").performClick()
        composeRule.waitForIdle()
        assertEquals(NarratorGender.FEMALE, gender)

        // Primary TTS button triggers synthesis
        composeRule.onNodeWithTag("synthesize_button").performClick()
        composeRule.waitForIdle()
        assertTrue(synthesizeClicked)
    }

    @Test
    fun studioAllowsSwitchingGenderWhenVerifiedMaleVoiceAssetExists() {
        var gender by mutableStateOf(NarratorGender.FEMALE)
        val parisa = BundledVoiceAsset(
            id = "parisa",
            label = "پریسا",
            gender = NarratorGender.FEMALE,
            urlPath = "https://appassets.androidplatform.net/assets/cilla-mini/phonon/model/voices/parisa.safetensors",
            isBundledAsset = true,
        )
        val maleVoice = BundledVoiceAsset(
            id = "voice2",
            label = "صدای مرد",
            gender = NarratorGender.MALE,
            urlPath = "https://appassets.androidplatform.net/assets/cilla-mini/phonon/model/voices/voice2.safetensors",
            isBundledAsset = true,
        )

        composeRule.setContent {
            val activeVoice = if (gender == NarratorGender.MALE) maleVoice else parisa
            val availability = VoiceAvailabilityState(
                activeVoiceId = activeVoice.id,
                activeVoiceLabel = activeVoice.label,
                activeGender = gender,
                isFemaleAvailable = true,
                isMaleAvailable = true,
                availablePhononVoices = listOf(parisa, maleVoice),
                isUsingCustomRecordedVoice = false,
                isPhononBackend = true,
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                CillaTtsTheme {
                    StudioScreen(
                        inputText = "سلام آوینا",
                        normalizedPreview = "سلام آوینا",
                        settings = UserSettings(narratorGender = gender, activeVoiceId = activeVoice.id),
                        modelState = ModelEngineState.Uninitialized,
                        synthesisState = SynthesisUiState.Idle,
                        voiceAvailability = availability,
                        voices = emptyList(),
                        activeCustomVoice = null,
                        isPlaying = false,
                        playingClipId = null,
                        playProgress = 0f,
                        onTextChange = {},
                        onSelectNarratorGender = { gender = it },
                        onSelectVoice = {},
                        onSynthesize = {},
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
        composeRule.onNodeWithText("پریسا").assertIsDisplayed()

        composeRule.onNodeWithTag("select_male_narrator").performClick()
        composeRule.waitForIdle()
        assertEquals(NarratorGender.MALE, gender)
        composeRule.onNodeWithText("صدای مرد").assertIsDisplayed()
    }

    @Test
    fun modelRepositoryDetectsBundledPhononRuntimeAndLocalPhononModelAssets() {
        kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ModelRepository(context)

        // Real phonon-tts WASM runtime is packaged in APK assets
        assertTrue(repo.hasPhononRuntime())

        // Without gated model weights, inventory accurately reports missing Phonon files
        repo.clearAllModels()
        val initialInventory = repo.inspectInventory()
        assertTrue(initialInventory.phononRuntimeReady)
        if (!initialInventory.phononReady) {
            assertEquals(PhononModelSource.NONE, initialInventory.phononSource)
            assertTrue(initialInventory.missingPhononAssets.contains("cilla-mini/phonon/model/model.safetensors"))
            assertTrue(initialInventory.missingPhononAssets.contains("cilla-mini/phonon/model/voices/parisa.safetensors"))
        }

        // Placing valid Phonon model files into local storage activates LOCAL_STORAGE offline mode
        val phononDir = File(context.filesDir, "models/cilla-mini/phonon/model").apply { mkdirs() }
        val voicesDir = File(phononDir, "voices").apply { mkdirs() }
        File(phononDir, "config.json").writeText("""{"architecture":"pocket_tts"}""")
        File(phononDir, "tokenizer.json").writeText("""{"version":"1.0","model":{"vocab":{}}}""" + " ".repeat(200))
        File(phononDir, "model.safetensors").writeBytes(ByteArray(4096) { 1 })
        File(voicesDir, "parisa.safetensors").writeBytes(ByteArray(2048) { 2 })

        val readyInventory = repo.inspectInventory()
        assertTrue(readyInventory.phononReady)
        assertTrue(readyInventory.canRunOffline)
        assertEquals(PhononModelSource.LOCAL_STORAGE, readyInventory.phononSource)
        assertEquals(1, readyInventory.availablePhononVoices.size)
        assertEquals("parisa", readyInventory.availablePhononVoices.first().id)
        assertEquals(NarratorGender.FEMALE, readyInventory.availablePhononVoices.first().gender)

        // Honest voice availability: only female Parisa is enabled, male stays disabled
        val availability = MainViewModel.computeVoiceAvailability(
            inventory = readyInventory,
            userVoices = emptyList(),
            settings = UserSettings(narratorGender = NarratorGender.MALE, activeVoiceId = "voice2"),
        )
        assertTrue(availability.isFemaleAvailable)
        assertFalse(availability.isMaleAvailable)
        assertEquals("parisa", availability.activeVoiceId)
        assertEquals("پریسا", availability.activeVoiceLabel)

        // Adding a verified male voice safetensors enables male gender selection
        File(voicesDir, "voice2.safetensors").writeBytes(ByteArray(2048) { 3 })
        val dualVoiceInventory = repo.inspectInventory()
        val dualAvailability = MainViewModel.computeVoiceAvailability(
            inventory = dualVoiceInventory,
            userVoices = emptyList(),
            settings = UserSettings(narratorGender = NarratorGender.MALE, activeVoiceId = "voice2"),
        )
        assertTrue(dualAvailability.isFemaleAvailable)
        assertTrue(dualAvailability.isMaleAvailable)
        assertEquals("voice2", dualAvailability.activeVoiceId)
        assertEquals(NarratorGender.MALE, dualAvailability.activeGender)

        repo.clearAllModels()
        }
    }

    @Test
    fun persianTextNormalizerNormalizesNumbersAndSplitsSentences() {
        val normalized = PersianTextNormalizer.normalize("امروز ۱۴۰۵ است. ساعت ۱۲:۳۰!")
        assertTrue(normalized.contains("هزار"))
        val sentences = PersianTextNormalizer.splitSentences("سلام دنیا. چطورید؟ خوبم!")
        assertEquals(3, sentences.size)
    }
}
