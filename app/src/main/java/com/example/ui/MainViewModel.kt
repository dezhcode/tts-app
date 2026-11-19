package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioPlayer
import com.example.audio.VoicePromptRecorder
import com.example.audio.WavAudioUtils
import com.example.data.AppPreferences
import com.example.data.ClipAndVoiceRepository
import com.example.data.ClipRecord
import com.example.data.DownloadOutcome
import com.example.data.DownloadProgress
import com.example.data.ModelInventory
import com.example.data.ModelRepository
import com.example.data.UserSettings
import com.example.data.VoicePromptItem
import com.example.tts.EngineDiagnostics
import com.example.tts.PersianTextNormalizer
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

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPreferences(application)
    private val modelRepo = ModelRepository(application)
    private val clipVoiceRepo = ClipAndVoiceRepository(application, modelRepo)

    val audioPlayer = AudioPlayer(viewModelScope)
    val voiceRecorder = VoicePromptRecorder(viewModelScope)

    private var activeEngine: TtsEngine? = null

    val settings: StateFlow<UserSettings> = prefs.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserSettings(),
    )

    private val _inputText = MutableStateFlow(
        "سلام! امروز چهاردهم آبان سال ۱۴۰۵ است. با مدل سیلا مینی می‌توانم متن فارسی را کاملاً آفلاین به گفتار طبیعی تبدیل کنم."
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

    init {
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
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
            val missing = modelRepo.missingPhononAssets()
            _modelState.value = if (missing.isNotEmpty()) {
                ModelEngineState.Error("فایل‌های Phonon داخل APK پیدا نشدند: " + missing.joinToString(", "), inv)
            } else {
                ModelEngineState.Missing(inv)
            }
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
                err.localizedMessage ?: "خطا در بارگذاری وزن‌های مدل",
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
                when (val state = _modelState.value) {
                    is ModelEngineState.Error -> state.message
                    is ModelEngineState.Loading -> "موتور گفتار هنوز در حال بارگذاری است."
                    else -> "مدل آماده نیست؛ وضعیت موتور را در بخش «مدل آفلاین» بررسی کنید."
                }
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
                val selectedPreset = cfg.activeVoiceId.takeIf { it in setOf("parisa", "voice1", "voice2") } ?: "parisa"
                val activeVoice = null as VoicePromptItem?

                val voiceEmbedding = if (activeVoice != null) {
                    val audio = if (activeVoice.isSafetensorsCache) null else {
                        withContext(Dispatchers.IO) {
                            WavAudioUtils.readWavAs24kMono(activeVoice.file)
                        }
                    }
                    val stateFile = if (activeVoice.isSafetensorsCache) activeVoice.file else null
                    engine.encodeVoicePrompt(activeVoice.id, activeVoice.label, audio, stateFile)
                } else {
                    engine.encodeVoicePrompt(selectedPreset, selectedPreset, null, null)
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
                    voiceLabel = when (selectedPreset) { "voice1" -> "صدای زن"; "voice2" -> "صدای مرد"; else -> "پریسا" },
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
                    err.localizedMessage ?: "خطا در اجرای استنتاج عصبی مدل"
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
                _statusBanner.value = "نمونه صدای «${saved.label}» ذخیره و فعال شد."
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
        viewModelScope.launch { prefs.setActiveVoiceId(voiceId) }
    }

    fun deleteVoice(voice: VoicePromptItem) {
        viewModelScope.launch {
            clipVoiceRepo.deleteVoice(voice)
            if (settings.value.activeVoiceId == voice.id) {
                prefs.setActiveVoiceId("")
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
                    _statusBanner.value = "مدل cilla-mini با موفقیت دانلود و بارگذاری شد."
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
            _statusBanner.value = "فایل‌های مدل پاک شدند."
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

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stop()
        voiceRecorder.stopRecording()
        activeEngine?.close()
    }
}
