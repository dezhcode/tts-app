package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ClipRecord
import com.example.data.UserSettings
import com.example.data.VoicePromptItem
import com.example.tts.PersianTextNormalizer
import com.example.ui.ModelEngineState
import com.example.ui.SynthesisUiState
import com.example.ui.components.InteractiveWaveformView
import com.example.ui.components.StatusPill
import java.util.Locale

private val samplePresets = listOf(
    "معرفی سیلا" to "سلام! امروز چهاردهم آبان سال ۱۴۰۵ است. با مدل سیلا مینی می‌توانم متن فارسی را کاملاً آفلاین به گفتار طبیعی تبدیل کنم.",
    "شعر حافظ" to "الا یا ایها الساقی ادر کاسا و ناولها؛ که عشق آسان نمود اول ولی افتاد مشکل‌ها.",
    "خبر و اعداد" to "در گزارش سال ۱۴۰۵، نرخ رشد پردازش صوتی روی گوشی‌های هوشمند ۲۴ درصد افزایش یافت و تاخیر به ۳۵ میلی‌ثانیه رسید.",
    "گفتگوی روزمره" to "وقت بخیر! لطفاً یک فنجان چای تازه دم بیاورید تا در آرامش درباره پروژه جدید گفتگو کنیم.",
)

@Composable
fun StudioScreen(
    inputText: String,
    normalizedPreview: String,
    settings: UserSettings,
    modelState: ModelEngineState,
    synthesisState: SynthesisUiState,
    activeVoice: VoicePromptItem?,
    isPlaying: Boolean,
    playingClipId: String?,
    playProgress: Float,
    onTextChange: (String) -> Unit,
    onSynthesize: () -> Unit,
    onPlayClip: (ClipRecord) -> Unit,
    onShareClip: (ClipRecord) -> Unit,
    onExportClip: (ClipRecord, android.net.Uri) -> Unit,
    onUpdateTemperature: (Float) -> Unit,
    onUpdateFlowSteps: (Int) -> Unit,
    onUpdatePauseMs: (Int) -> Unit,
    onUpdateTrimSilence: (Boolean) -> Unit,
    onUpdateAutoNormalize: (Boolean) -> Unit,
    onNavigateToModelSetup: () -> Unit,
    onNavigateToVoices: () -> Unit,
) {
    var showNormalizerPreview by remember { mutableStateOf(false) }
    var showTuningPanel by remember { mutableStateOf(false) }
    var pendingExportClip by remember { mutableStateOf<ClipRecord?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/wav")
    ) { uri ->
        val clip = pendingExportClip
        if (uri != null && clip != null) {
            onExportClip(clip, uri)
        }
        pendingExportClip = null
    }

    val sentenceCount = remember(normalizedPreview) {
        PersianTextNormalizer.splitSentences(normalizedPreview).size
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Hero Studio Banner Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(144.dp),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_studio_wave_1795024818051),
                        contentDescription = "تصویر موج صوتی استودیو سیلا",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.25f),
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            when (modelState) {
                                is ModelEngineState.Ready -> StatusPill(
                                    text = "آفلاین آماده • ۲۴ kHz",
                                    dotColor = MaterialTheme.colorScheme.primary,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                is ModelEngineState.Loading -> StatusPill(
                                    text = "در حال بارگذاری وزن‌ها...",
                                    dotColor = MaterialTheme.colorScheme.secondary,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                else -> StatusPill(
                                    text = "نیازمند بارگذاری مدل",
                                    dotColor = MaterialTheme.colorScheme.secondary,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.clickable(onClick = onNavigateToModelSetup),
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                            ) {
                                Text(
                                    text = "18.2M Pocket-TTS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "استودیو گفتار آفلاین سیلا",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = "مبتنی بر معماری mehdi-hf/cilla-mini با شبیه‌سازی صدا",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Missing Model Callout
            if (modelState is ModelEngineState.Missing || modelState is ModelEngineState.Error) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = "هشدار نبود مدل",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (modelState is ModelEngineState.Error) {
                                    modelState.message
                                } else {
                                    "فایل‌های مدل cilla-mini (~100 MB) هنوز روی دستگاه نصب نشده‌اند."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        FilledTonalButton(
                            onClick = onNavigateToModelSetup,
                            modifier = Modifier.testTag("go_to_model_setup_button"),
                        ) {
                            Text("نصب مدل")
                        }
                    }
                }
            }

            // Active Voice Selector Strip
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToVoices)
                    .testTag("active_voice_selector"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RecordVoiceOver,
                            contentDescription = "صدای فعال",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "پروفایل صوتی مرجع (Zero-Shot)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = activeVoice?.label ?: "بدون نمونه مرجع (وزن پایه مدل)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = "تغییر صدا",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Quick Persian Sample Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                samplePresets.forEach { (title, sample) ->
                    FilterChip(
                        selected = inputText == sample,
                        onClick = { onTextChange(sample) },
                        label = { Text(title) },
                    )
                }
            }

            // Main Persian Text Input Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "متن فارسی ورودی",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (inputText.isNotEmpty()) {
                            IconButton(
                                onClick = { onTextChange("") },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("clear_text_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = "پاک کردن متن",
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = onTextChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("text_input_field"),
                        minLines = 4,
                        maxLines = 8,
                        placeholder = {
                            Text("متن فارسی خود را اینجا بنویسید...")
                        },
                        shape = RoundedCornerShape(14.dp),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${inputText.length} کاراکتر • $sentenceCount بخش",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = { showNormalizerPreview = !showNormalizerPreview },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoFixHigh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("normalize_fa", style = MaterialTheme.typography.labelMedium)
                                Icon(
                                    imageVector = if (showNormalizerPreview) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }

                            FilledTonalIconButton(
                                onClick = { showTuningPanel = !showTuningPanel },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("toggle_tuning_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = "تنظیمات استنتاج",
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = showNormalizerPreview) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "خروجی نرمال‌ساز فارسی (تبدیل اعداد و حروف)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Switch(
                                        checked = settings.autoNormalizePersian,
                                        onCheckedChange = onUpdateAutoNormalize,
                                    )
                                }
                                Text(
                                    text = normalizedPreview.ifBlank { "—" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = showTuningPanel) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "تنظیمات موتور Flow-LM و Mimi",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "دمای نمونه‌برداری (Temperature): ${String.format(Locale.US, "%.2f", settings.temperature)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Slider(
                                    value = settings.temperature,
                                    onValueChange = onUpdateTemperature,
                                    valueRange = 0.1f..1.4f,
                                )
                                Text(
                                    text = "گام‌های Flow / Drifting Head: ${settings.flowSteps} (پیشنهادی سیلا: ۱ گام)",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Slider(
                                    value = settings.flowSteps.toFloat(),
                                    onValueChange = { onUpdateFlowSteps(it.toInt()) },
                                    valueRange = 1f..8f,
                                    steps = 6,
                                )
                                Text(
                                    text = "مکث بین جملات: ${settings.sentencePauseMs} میلی‌ثانیه",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Slider(
                                    value = settings.sentencePauseMs.toFloat(),
                                    onValueChange = { onUpdatePauseMs(it.toInt()) },
                                    valueRange = 0f..600f,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "حذف سکوت ابتدا و انتهای جملات",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Switch(
                                        checked = settings.trimSilence,
                                        onCheckedChange = onUpdateTrimSilence,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Primary Synthesize Action
            val isSynthesizing = synthesisState is SynthesisUiState.Synthesizing
            Button(
                onClick = onSynthesize,
                enabled = !isSynthesizing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("synthesize_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isSynthesizing) {
                        val s = synthesisState as SynthesisUiState.Synthesizing
                        "در حال تولید گفتار (بخش ${s.sentenceIdx} از ${s.totalSentences} • فریم ${s.frames})"
                    } else {
                        "تبدیل متن به گفتار آفلاین"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (isSynthesizing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)),
                )
            }

            // Synthesis Error Banner
            if (synthesisState is SynthesisUiState.Error) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Text(
                        text = synthesisState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            // Active Output Audio Card
            if (synthesisState is SynthesisUiState.Success) {
                val clip = synthesisState.clip
                val thisPlaying = isPlaying && playingClipId == clip.id
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "خروجی صوتی ۲۴ kHz",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "%.1fs • %.2fs CPU (RTF %.2f)",
                                    clip.durationSec,
                                    synthesisState.inferenceSeconds,
                                    clip.rtf,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        InteractiveWaveformView(
                            peaks = clip.waveformPeaks,
                            progress = if (thisPlaying) playProgress else 0f,
                            isPlaying = thisPlaying,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(
                                onClick = { onPlayClip(clip) },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("play_audio_button"),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(
                                    imageVector = if (thisPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                    contentDescription = if (thisPlaying) "توقف پخش" else "پخش صدا",
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (thisPlaying) "توقف" else "پخش")
                            }

                            FilledTonalButton(
                                onClick = {
                                    pendingExportClip = clip
                                    exportLauncher.launch(clip.fileName)
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("export_wav_button"),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = "ذخیره فایل WAV")
                                Spacer(Modifier.width(4.dp))
                                Text("WAV")
                            }

                            FilledTonalIconButton(
                                onClick = { onShareClip(clip) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("share_wav_button"),
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = "اشتراک‌گذاری فایل صوتی")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
