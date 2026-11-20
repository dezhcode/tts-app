package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioPlayer
import com.example.audio.VoicePromptRecorder
import com.example.audio.WavAudioUtils
import com.example.data.AppFontWeight
import com.example.data.AppPreferences
import com.example.data.BundledVoiceAsset
import com.example.data.ClipAndVoiceRepository
import com.example.data.ClipRecord
import com.example.data.DownloadOutcome
import com.example.data.DownloadProgress
import com.example.data.FontIds
import com.example.data.FontRepository
import com.example.data.ModelInventory
import com.example.data.ModelRepository
import com.example.data.NarratorGender
import com.example.data.UserSettings
import com.example.data.VoicePromptItem
import com.example.tts.EngineDiagnostics
import com.example.tts.PersianTextNormalizer
import com.example.tts.PhononPocketTtsEngine
import com.example.tts.TtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream

sealed interface ModelEngineState {
    data object Uninitialized : ModelEngineState
    data object Loading : ModelEngineState
    data class Ready(val diagnostics: EngineDiagnostics) : ModelEngineState
    data class Missing(val inventory: ModelInventory) : ModelEngineState
    data class Error(val message: String, val inventory: ModelInventory) : ModelEngineState
}

sealed interface SynthesisUiState {
    data object Idle : SynthesisUiState
    data class Synthesizing(val sentenceIdx: Int, val totalSentences: Int, val frames: Int) : SynthesisUiState
    data class Success(val clip: ClipRecord, val inferenceSeconds: Float) : SynthesisUiState
    data class Error(val message: String) : SynthesisUiState
}

sealed interface DownloadUiState {
    data object Idle : DownloadUiState
    data class Downloading(val progress: DownloadProgress) : DownloadUiState
    data class GatedAuthRequired(val message: String) : DownloadUiState
    data class Error(val message: String) : DownloadUiState
    data object Completed : DownloadUiState
}

/**
 * Synchronized view of real, verified voices exposed by the active model backend.
 * Never simulates voices or claims male/female voices that lack actual voice assets.
 */
data class VoiceAvailabilityState(
    val activeVoiceId: String = PhononPocketTtsEngine.DEFAULT_VOICE_ID,
    val activeVoiceLabel: String = "پریسا",
    val activeGender: NarratorGender? = NarratorGender.FEMALE,
    val isFemaleAvailable: Boolean = true,
    val isMaleAvailable: Boolean = false,
    val availablePhononVoices: List<BundledVoiceAsset> = emptyList(),
    val isUsingCustomRecordedVoice: Boolean = false,
    val isPhononBackend: Boolean = true,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPreferences(application)
    private val modelRepo = ModelRepository(application)
    private val clipVoiceRepo = ClipAndVoiceRepository(application, modelRepo)
    private val fontRepo = FontRepository(application)

    val audioPlayer = AudioPlayer(viewModelScope)
    val voiceRecorder = VoicePromptRecorder(viewModelScope)

    private var activeEngine: TtsEngine? = null

    val settings: StateFlow<UserSettings> = prefs.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserSettings(),
    )

    private val _inputText = MutableStateFlow(
        "سلام! امروز چهاردهم آبان ۱۴۰۵ است. با آوینا می‌توانید متن فارسی را آفلاین به گفتار طبیعی تبدیل کنید."
    )
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    val normalizedPreview: StateFlow<String> = combine(_inputText, settings) { txt, cfg ->
        if (cfg.autoNormalizePersian) PersianTextNormalizer.normalize(txt) else txt.trim()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _modelState = MutableStateFlow<ModelEngineState>(ModelEngineState.Uninitialized)
    val modelState: StateFlow<ModelEngineState> = _modelState.asStateFlow()

    private val _inventory = MutableStateFlow(modelRepo.inspectInventory())
    val inventory: StateFlow<ModelInventory> = _inventory.asStateFlow()

    private val _synthesisState = MutableStateFlow<SynthesisUiState>(SynthesisUiState.Idle)
    val synthesisState: StateFlow<SynthesisUiState> = _synthesisState.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadUiState>(DownloadUiState.Idle)
    val downloadState: StateFlow<DownloadUiState> = _downloadState.asStateFlow()

    private val _voices = MutableStateFlow<List<VoicePromptItem>>(emptyList())
    val voices: StateFlow<List<VoicePromptItem>> = _voices.asStateFlow()

    private val _clips = MutableStateFlow<List<ClipRecord>>(emptyList())
    val clips: StateFlow<List<ClipRecord>> = _clips.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _customFonts = MutableStateFlow<Map<String, FontFamily>>(emptyMap())
    val customFonts: StateFlow<Map<String, FontFamily>> = _customFonts.asStateFlow()

    /**
     * Authoritative voice availability computed directly from verified model assets.
     */
    val voiceAvailability: StateFlow<VoiceAvailabilityState> = combine(
        _inventory,
        _voices,
        settings,
    ) { inv, userVoices, cfg ->
        computeVoiceAvailability(inv, userVoices, cfg)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VoiceAvailabilityState(),
    )

    init {
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
            _customFonts.value = fontRepo.loadInstalledCustomFontFamilies()
            reloadInventoryAndEngine()
            _voices.value = clipVoiceRepo.listVoicePrompts()
            _clips.value = clipVoiceRepo.loadClipHistory()
        }
    }

    private suspend fun reloadInventoryAndEngine() {
        val inv = withContext(Dispatchers.IO) { modelRepo.inspectInventory() }
        _inventory.value = inv
        if (!inv.canRunOffline) {
            activeEngine?.close()
            activeEngine = null
            _modelState.value = ModelEngineState.Missing(inv)
            return
        }

        _modelState.value = ModelEngineState.Loading
        runCatching {
            activeEngine?.close()
            activeEngine = modelRepo.loadEngine()
            val loaded = activeEngine
            if (loaded != null) {
                _modelState.value = ModelEngineState.Ready(loaded.diagnostics)
            } else {
                _modelState.value = ModelEngineState.Missing(inv)
            }
        }.onFailure { err ->
            activeEngine = null
            _modelState.value = ModelEngineState.Error(
                err.localizedMessage ?: "خطا در آماده‌سازی موتور گفتار",
                inv,
            )
        }
    }

    fun updateInputText(newText: String) {
        _inputText.value = newText
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }

    fun synthesizeCurrentText() {
        val engine = activeEngine
        if (engine == null) {
            _synthesisState.value = SynthesisUiState.Error(
                "ابتدا مدل آفلاین را نصب کنید."
            )
            return
        }

        val raw = _inputText.value.trim()
        if (raw.isEmpty()) {
            _synthesisState.value = SynthesisUiState.Error("لطفاً متنی برای تبدیل به گفتار وارد کنید.")
            return
        }

        val cfg = settings.value
        val textForModel = if (cfg.autoNormalizePersian) {
            PersianTextNormalizer.normalize(raw)
        } else {
            raw
        }

        if (textForModel.isBlank()) {
            _synthesisState.value = SynthesisUiState.Error("متن ورودی پس از نرمال‌سازی خالی است.")
            return
        }

        viewModelScope.launch {
            _synthesisState.value = SynthesisUiState.Synthesizing(1, 1, 0)
            runCatching {
                val availability = computeVoiceAvailability(_inventory.value, _voices.value, cfg)
                val resolvedVoiceLabel: String

                val voiceEmbedding = if (engine is PhononPocketTtsEngine) {
                    val chosenAsset = availability.availablePhononVoices.firstOrNull {
                        it.id == availability.activeVoiceId
                    } ?: availability.availablePhononVoices.firstOrNull()

                    val voiceId = chosenAsset?.id ?: engine.defaultVoiceId
                    resolvedVoiceLabel = chosenAsset?.label ?: ModelRepository.displayLabelForVoiceId(voiceId)
                    engine.encodeVoicePrompt(
                        id = voiceId,
                        label = resolvedVoiceLabel,
                        audio24k = null,
                        stateFile = chosenAsset?.localFile,
                    )
                } else {
                    // Legacy non-Phonon engine: use real recorded/imported voice prompt if present; never simulate.
                    val customVoice = _voices.value.firstOrNull { it.id == cfg.activeVoiceId }
                        ?: _voices.value.firstOrNull()
                    if (customVoice != null) {
                        resolvedVoiceLabel = customVoice.label
                        val audio = if (customVoice.isSafetensorsCache) null else {
                            withContext(Dispatchers.IO) { WavAudioUtils.readWavAs24kMono(customVoice.file) }
                        }
                        val stateFile = if (customVoice.isSafetensorsCache) customVoice.file else null
                        engine.encodeVoicePrompt(customVoice.id, customVoice.label, audio, stateFile)
                    } else {
                        resolvedVoiceLabel = availability.activeVoiceLabel
                        engine.encodeVoicePrompt(
                            id = availability.activeVoiceId,
                            label = resolvedVoiceLabel,
                            audio24k = null,
                            stateFile = null,
                        )
                    }
                }

                val res = engine.synthesize(
                    normalizedText = textForModel,
                    voice = voiceEmbedding,
                    temperature = cfg.temperature,
                    flowSteps = cfg.flowSteps,
                    sentencePauseMs = cfg.sentencePauseMs,
                    trimSilence = cfg.trimSilence,
                    onProgress = { sIdx, totalS, frames ->
                        _synthesisState.value = SynthesisUiState.Synthesizing(sIdx, totalS, frames)
                    },
                )

                val savedClip = clipVoiceRepo.saveClip(
                    text = raw,
                    normalizedText = textForModel,
                    voiceLabel = resolvedVoiceLabel,
                    pcm24k = res.pcmSamples24k,
                    sampleRate = res.sampleRate,
                    durationSec = res.durationSeconds,
                    rtf = res.rtf,
                )
                _clips.value = clipVoiceRepo.loadClipHistory()
                _synthesisState.value = SynthesisUiState.Success(savedClip, res.inferenceSeconds)
                audioPlayer.play(savedClip.id, res.pcmSamples24k, res.sampleRate)
            }.onFailure { err ->
                _synthesisState.value = SynthesisUiState.Error(
                    err.localizedMessage ?: "خطا در تولید گفتار"
                )
            }
        }
    }

    fun togglePlayClip(clip: ClipRecord) {
        if (audioPlayer.isPlaying.value && audioPlayer.playingClipId.value == clip.id) {
            audioPlayer.stop()
            return
        }
        viewModelScope.launch {
            val file = clipVoiceRepo.getClipFile(clip)
            if (!file.exists()) {
                _statusBanner.value = "فایل صوتی یافت نشد."
                return@launch
            }
            val samples = withContext(Dispatchers.IO) { WavAudioUtils.readWavAs24kMono(file) }
            audioPlayer.play(clip.id, samples, 24_000)
        }
    }

    fun togglePlayVoice(voice: VoicePromptItem) {
        if (voice.isSafetensorsCache) {
            _statusBanner.value = "فایل وضعیت از پیش محاسبه‌شده (.safetensors) فاقد موج صوتی قابل پخش است."
            return
        }
        if (audioPlayer.isPlaying.value && audioPlayer.playingClipId.value == voice.id) {
            audioPlayer.stop()
            return
        }
        viewModelScope.launch {
            val samples = withContext(Dispatchers.IO) { WavAudioUtils.readWavAs24kMono(voice.file) }
            audioPlayer.play(voice.id, samples, 24_000)
        }
    }

    fun startVoiceRecording(label: String) {
        voiceRecorder.startRecording(sampleRate = 24_000, maxSeconds = 10) { captured ->
            viewModelScope.launch {
                val saved = clipVoiceRepo.saveRecordedVoice(label, captured)
                prefs.setActiveVoiceId(saved.id)
                _voices.value = clipVoiceRepo.listVoicePrompts()
                _statusBanner.value = "نمونه صدای «${saved.label}» ذخیره شد."
            }
        }
    }

    fun stopVoiceRecording() {
        voiceRecorder.stopRecording()
    }

    fun importVoiceFromUri(uri: Uri) {
        viewModelScope.launch {
            val imported = clipVoiceRepo.importVoiceUri(uri)
            if (imported != null) {
                prefs.setActiveVoiceId(imported.id)
                _voices.value = clipVoiceRepo.listVoicePrompts()
                _statusBanner.value = "نمونه صدای «${imported.label}» اضافه شد."
            } else {
                _statusBanner.value = "وارد کردن فایل صوتی با خطا مواجه شد."
            }
        }
    }

    fun selectVoice(voiceId: String) {
        viewModelScope.launch {
            val inv = _inventory.value
            val matchingPhonon = inv.availablePhononVoices.firstOrNull { it.id == voiceId }
            if (matchingPhonon != null) {
                matchingPhonon.gender?.let { prefs.setNarratorGender(it) }
                prefs.setActiveVoiceId(matchingPhonon.id)
            } else {
                prefs.setActiveVoiceId(voiceId)
            }
        }
    }

    fun selectNarratorGender(gender: NarratorGender) {
        viewModelScope.launch {
            val inv = _inventory.value
            val phononVoices = inv.availablePhononVoices
            if (inv.phononReady || phononVoices.isNotEmpty() || !inv.canRunOffline) {
                val matched = phononVoices.firstOrNull { it.gender == gender }
                if (matched == null) {
                    if (gender == NarratorGender.MALE) {
                        _statusBanner.value = "صدای مرد در فایل‌های مدل موجود نیست."
                    }
                    return@launch
                }
                prefs.setNarratorGender(gender)
                prefs.setActiveVoiceId(matched.id)
            } else {
                prefs.setNarratorGender(gender)
            }
        }
    }

    fun deleteVoice(voice: VoicePromptItem) {
        viewModelScope.launch {
            clipVoiceRepo.deleteVoice(voice)
            if (settings.value.activeVoiceId == voice.id) {
                prefs.setActiveVoiceId(PhononPocketTtsEngine.DEFAULT_VOICE_ID)
            }
            _voices.value = clipVoiceRepo.listVoicePrompts()
        }
    }

    fun deleteClip(clip: ClipRecord) {
        viewModelScope.launch {
            if (audioPlayer.playingClipId.value == clip.id) audioPlayer.stop()
            clipVoiceRepo.deleteClip(clip)
            _clips.value = clipVoiceRepo.loadClipHistory()
            val cur = _synthesisState.value
            if (cur is SynthesisUiState.Success && cur.clip.id == clip.id) {
                _synthesisState.value = SynthesisUiState.Idle
            }
        }
    }

    fun exportClipToUri(clip: ClipRecord, targetUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = clipVoiceRepo.getClipFile(clip)
            if (!file.exists()) return@launch
            runCatching {
                getApplication<Application>().contentResolver.openOutputStream(targetUri)?.use { out ->
                    FileInputStream(file).use { input -> input.copyTo(out) }
                }
                _statusBanner.value = "فایل WAV با موفقیت ذخیره شد."
            }.onFailure {
                _statusBanner.value = "خطا در ذخیره فایل صوتی."
            }
        }
    }

    fun createShareIntent(clip: ClipRecord): Intent? {
        val app = getApplication<Application>()
        val file = clipVoiceRepo.getClipFile(clip)
        if (!file.exists()) return null
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, clip.text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun downloadModelFromHuggingFace(token: String) {
        viewModelScope.launch {
            prefs.setHfToken(token)
            _downloadState.value = DownloadUiState.Downloading(
                DownloadProgress("در حال اتصال به Hugging Face...", 0, 1, 0L, 0L)
            )
            when (val outcome = modelRepo.downloadFromHuggingFace(token) { prog ->
                _downloadState.value = DownloadUiState.Downloading(prog)
            }) {
                is DownloadOutcome.Success -> {
                    _downloadState.value = DownloadUiState.Completed
                    reloadInventoryAndEngine()
                    _voices.value = clipVoiceRepo.listVoicePrompts()
                    _statusBanner.value = "مدل آفلاین نصب و آماده شد."
                }
                is DownloadOutcome.GatedAuthRequired -> {
                    _downloadState.value = DownloadUiState.GatedAuthRequired(outcome.message)
                }
                is DownloadOutcome.Error -> {
                    _downloadState.value = DownloadUiState.Error(outcome.message)
                }
            }
        }
    }

    fun importLocalModelFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val imported = modelRepo.importLocalFiles(uris)
            reloadInventoryAndEngine()
            _voices.value = clipVoiceRepo.listVoicePrompts()
            _statusBanner.value = if (imported.isNotEmpty()) {
                "${imported.size} فایل وارد شد: ${imported.joinToString("، ")}"
            } else {
                "فایلی وارد نشد."
            }
        }
    }

    fun clearModelFiles() {
        viewModelScope.launch {
            activeEngine?.close()
            activeEngine = null
            modelRepo.clearAllModels()
            _downloadState.value = DownloadUiState.Idle
            reloadInventoryAndEngine()
            _voices.value = clipVoiceRepo.listVoicePrompts()
            _statusBanner.value = "فایل‌های محلی مدل پاک شدند."
        }
    }

    fun updateHfToken(token: String) {
        viewModelScope.launch { prefs.setHfToken(token) }
    }

    fun updateTemperature(v: Float) {
        viewModelScope.launch { prefs.setTemperature(v) }
    }

    fun updateFlowSteps(s: Int) {
        viewModelScope.launch { prefs.setFlowSteps(s) }
    }

    fun updateSentencePause(ms: Int) {
        viewModelScope.launch { prefs.setSentencePauseMs(ms) }
    }

    fun updateTrimSilence(enabled: Boolean) {
        viewModelScope.launch { prefs.setTrimSilence(enabled) }
    }

    fun updateAutoNormalize(enabled: Boolean) {
        viewModelScope.launch { prefs.setAutoNormalizePersian(enabled) }
    }

    fun updateDarkTheme(enabled: Boolean) {
        viewModelScope.launch { prefs.setDarkTheme(enabled) }
    }

    fun selectPersianUiFont(fontId: String) {
        viewModelScope.launch { prefs.setPersianUiFont(fontId) }
    }

    fun selectPersianHeadingFont(fontId: String) {
        viewModelScope.launch { prefs.setPersianHeadingFont(fontId) }
    }

    fun selectPersianWeight(weight: AppFontWeight) {
        viewModelScope.launch { prefs.setPersianWeight(weight) }
    }

    fun selectEnglishUiFont(fontId: String) {
        viewModelScope.launch { prefs.setEnglishUiFont(fontId) }
    }

    fun selectEnglishHeadingFont(fontId: String) {
        viewModelScope.launch { prefs.setEnglishHeadingFont(fontId) }
    }

    fun selectEnglishWeight(weight: AppFontWeight) {
        viewModelScope.launch { prefs.setEnglishWeight(weight) }
    }

    fun importCustomFontFile(fontId: String, uri: Uri, applyAsHeading: Boolean) {
        viewModelScope.launch {
            fontRepo.importCustomFont(fontId, uri)
                .onSuccess {
                    _customFonts.value = fontRepo.loadInstalledCustomFontFamilies()
                    if (applyAsHeading) {
                        prefs.setPersianHeadingFont(fontId)
                    } else {
                        prefs.setPersianUiFont(fontId)
                    }
                    _statusBanner.value = "فونت با موفقیت نصب و فعال شد."
                }
                .onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "خطا در نصب فایل فونت."
                }
        }
    }

    fun removeCustomFontFile(fontId: String) {
        viewModelScope.launch {
            fontRepo.removeCustomFont(fontId)
            _customFonts.value = fontRepo.loadInstalledCustomFontFamilies()
            val cur = settings.value.typography
            if (cur.persianUiFontId == fontId) prefs.setPersianUiFont(FontIds.VAZIRMATN)
            if (cur.persianHeadingFontId == fontId) prefs.setPersianHeadingFont(FontIds.ESTEDAD)
            _statusBanner.value = "فایل فونت محلی حذف شد."
        }
    }

    fun resetTypographyDefaults() {
        viewModelScope.launch {
            prefs.resetTypographyToDefaults()
            _statusBanner.value = "تنظیمات تایپوگرافی به حالت پیش‌فرض بازگشت."
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stop()
        voiceRecorder.stopRecording()
        activeEngine?.close()
    }

    companion object {
        fun computeVoiceAvailability(
            inventory: ModelInventory,
            userVoices: List<VoicePromptItem>,
            settings: UserSettings,
        ): VoiceAvailabilityState {
            val phononVoices = inventory.availablePhononVoices
            val isPhonon = inventory.phononReady || !inventory.canRunOffline || phononVoices.isNotEmpty()

            if (isPhonon) {
                val hasFemale = phononVoices.any { it.gender == NarratorGender.FEMALE }
                val hasMale = phononVoices.any { it.gender == NarratorGender.MALE }
                val selected = phononVoices.firstOrNull { it.id == settings.activeVoiceId }
                    ?: phononVoices.firstOrNull { it.gender == settings.narratorGender }
                    ?: phononVoices.firstOrNull { it.id == PhononPocketTtsEngine.DEFAULT_VOICE_ID }
                    ?: phononVoices.firstOrNull()

                return if (selected != null) {
                    VoiceAvailabilityState(
                        activeVoiceId = selected.id,
                        activeVoiceLabel = selected.label,
                        activeGender = selected.gender,
                        isFemaleAvailable = hasFemale,
                        isMaleAvailable = hasMale,
                        availablePhononVoices = phononVoices,
                        isUsingCustomRecordedVoice = false,
                        isPhononBackend = true,
                    )
                } else {
                    // Model weights not yet installed: honestly reflect Cilla Mini default (Parisa female only)
                    VoiceAvailabilityState(
                        activeVoiceId = PhononPocketTtsEngine.DEFAULT_VOICE_ID,
                        activeVoiceLabel = "پریسا",
                        activeGender = NarratorGender.FEMALE,
                        isFemaleAvailable = true,
                        isMaleAvailable = false,
                        availablePhononVoices = emptyList(),
                        isUsingCustomRecordedVoice = false,
                        isPhononBackend = true,
                    )
                }
            }

            // Legacy non-Phonon backend fallback (only when user manually loaded ONNX/legacy weights)
            val customVoice = userVoices.firstOrNull { it.id == settings.activeVoiceId }
            return VoiceAvailabilityState(
                activeVoiceId = customVoice?.id ?: settings.narratorGender.id,
                activeVoiceLabel = customVoice?.label ?: settings.narratorGender.persianName,
                activeGender = if (customVoice == null) settings.narratorGender else null,
                isFemaleAvailable = true,
                isMaleAvailable = userVoices.any {
                    ModelRepository.inferGenderForVoiceId(it.file.nameWithoutExtension) == NarratorGender.MALE
                },
                availablePhononVoices = emptyList(),
                isUsingCustomRecordedVoice = customVoice != null,
                isPhononBackend = false,
            )
        }
    }
}
