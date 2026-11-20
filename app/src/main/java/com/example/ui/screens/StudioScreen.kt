package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.ClipRecord
import com.example.data.NarratorGender
import com.example.data.UserSettings
import com.example.data.VoicePromptItem
import com.example.ui.ModelEngineState
import com.example.ui.SynthesisUiState
import com.example.ui.components.AcousticRibbonVisualizer
import com.example.ui.components.InteractiveWaveformView
import com.example.ui.components.StudioNarratorSelector
import java.util.Locale

private val bespokePresets = listOf(
    "معرفی" to "سلام! با موتور آفلاین سیلا مینی می‌توانم متن فارسی را با کیفیتی طبیعی و بدون نیاز به اینترنت بخوانم.",
    "شعر حافظ" to "الا یا ایها الساقی ادر کاسا و ناولها؛ که عشق آسان نمود اول ولی افتاد مشکل‌ها.",
    "خبر و عدد" to "امروز بیستم آبان ۱۴۰۵ است و پردازش صوتی با نرخ ۲۴ کیلوهرتز روی دستگاه اجرا می‌شود.",
)

@Composable
fun StudioScreen(
    inputText: String,
    normalizedPreview: String,
    settings: UserSettings,
    modelState: ModelEngineState,
    synthesisState: SynthesisUiState,
    activeCustomVoice: VoicePromptItem?,
    isPlaying: Boolean,
    playingClipId: String?,
    playProgress: Float,
    onTextChange: (String) -> Unit,
    onSelectNarratorGender: (NarratorGender) -> Unit,
    onSynthesize: () -> Unit,
    onPlayClip: (ClipRecord) -> Unit,
    onShareClip: (ClipRecord) -> Unit,
    onExportClip: (ClipRecord, android.net.Uri) -> Unit,
    onUpdateTemperature: (Float) -> Unit,
    onUpdateFlowSteps: (Int) -> Unit,
    onUpdatePauseMs: (Int) -> Unit,
    onUpdateTrimSilence: (Boolean) -> Unit,
    onUpdateAutoNormalize: (Boolean) -> Unit,
    onOpenModelSetup: () -> Unit,
    onNavigateToVoices: () -> Unit,
) {
    var showFineTune by remember { mutableStateOf(false) }
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

    val isSynthesizing = synthesisState is SynthesisUiState.Synthesizing

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 6.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Subtle tactile setup notice when model files aren't installed yet
            if (modelState is ModelEngineState.Missing || modelState is ModelEngineState.Error) {
                Surface(
                    onClick = onOpenModelSetup,
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("go_to_model_setup_button"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = if (modelState is ModelEngineState.Error) {
                                modelState.message
                            } else {
                                "وزن‌های آفلاین cilla-mini هنوز نصب نشده‌اند"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "نصب ←",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }

            // Signature Sculpted Paper Writing Sheet with Integrated Acoustic Oscilloscope
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Top Acoustic Ribbon Strip inside the Paper Sheet
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    ) {
                        AcousticRibbonVisualizer(
                            isActive = isPlaying,
                            isSynthesizing = isSynthesizing,
                        )
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            val narratorTag = when {
                                activeCustomVoice != null -> "CUSTOM"
                                settings.narratorGender == NarratorGender.FEMALE -> "FEMALE"
                                else -> "MALE"
                            }
                            Text(
                                text = when {
                                    isSynthesizing -> {
                                        val s = synthesisState as SynthesisUiState.Synthesizing
                                        "SYNTH ${s.sentenceIdx}/${s.totalSentences} · F${s.frames}"
                                    }
                                    isPlaying -> "PLAYING · 24kHz"
                                    else -> "CILLA-MINI · $narratorTag"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.align(Alignment.TopStart),
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))

                    // Editorial Text Field
                    TextField(
                        value = inputText,
                        onValueChange = onTextChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("text_input_field"),
                        minLines = 5,
                        maxLines = 9,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        placeholder = {
                            Text(
                                text = "متن فارسی را اینجا بنویسید تا به صدا تبدیل شود...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )

                    // Bottom Sheet Utility Bar: Presets + Character Mono Counter + Clear + Fine-tune
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            bespokePresets.forEach { (title, sample) ->
                                val selected = inputText == sample
                                Surface(
                                    onClick = { onTextChange(sample) },
                                    shape = RoundedCornerShape(50),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                    },
                                ) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            if (inputText.isNotEmpty()) {
                                IconButton(
                                    onClick = { onTextChange("") },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("clear_text_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "پاک کردن متن",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }

                            Surface(
                                onClick = { showFineTune = !showFineTune },
                                shape = CircleShape,
                                color = if (showFineTune) {
                                    MaterialTheme.colorScheme.onBackground
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("toggle_tuning_button"),
                            ) {
                                Box(
                                    modifier = Modifier.size(40.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Tune,
                                        contentDescription = "تنظیمات دقیق",
                                        tint = if (showFineTune) {
                                            MaterialTheme.colorScheme.surface
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Collapsible Fine-Tune Acoustic Drawer
            AnimatedVisibility(
                visible = showFineTune,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoFixHigh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text("نرمال‌ساز هوشمند فارسی", style = MaterialTheme.typography.titleSmall)
                            }
                            Switch(
                                checked = settings.autoNormalizePersian,
                                onCheckedChange = onUpdateAutoNormalize,
                            )
                        }

                        if (settings.autoNormalizePersian && normalizedPreview.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = normalizedPreview,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("دمای لحن (Temperature)", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = String.format(Locale.US, "%.2f", settings.temperature),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Slider(
                            value = settings.temperature,
                            onValueChange = onUpdateTemperature,
                            valueRange = 0.1f..1.4f,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("مکث بین جملات", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = "${settings.sentencePauseMs} ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
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
                            Text("برش خودکار سکوت ابتدا و انتها", style = MaterialTheme.typography.bodySmall)
                            Switch(
                                checked = settings.trimSilence,
                                onCheckedChange = onUpdateTrimSilence,
                            )
                        }
                    }
                }
            }

            // Direct Female / Male Narrator Switcher inside Studio
            StudioNarratorSelector(
                selectedGender = settings.narratorGender,
                isCustomVoiceActive = false,
                customVoiceLabel = null,
                onSelectGender = onSelectNarratorGender,
                onOpenCustomVoices = {},
            )

            // Asymmetric One-Thumb Hardware Action Bar (Primary Vermilion Pill + Custom Voice Cloning Capsule)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onSynthesize,
                    enabled = !isSynthesizing,
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp)
                        .testTag("synthesize_button"),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 3.dp,
                        pressedElevation = 0.dp,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(21.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isSynthesizing) "در حال ساخت صدا..." else "تبدیل به گفتار",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }


            }

            if (isSynthesizing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            if (synthesisState is SynthesisUiState.Error) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = synthesisState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            // Bespoke Analog Tape Deck Audio Player Card
            if (synthesisState is SynthesisUiState.Success) {
                val clip = synthesisState.clip
                val thisPlaying = isPlaying && playingClipId == clip.id

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth(),
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondary),
                                )
                                Text(
                                    text = clip.voiceLabel.ifBlank { "نوار خروجی آماده" },
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "%.1fs · RTF %.2f",
                                        clip.durationSec,
                                        clip.rtf,
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            com.example.ui.components.AnimatedPlaybackButton(
                                isPlaying = thisPlaying,
                                onClick = { onPlayClip(clip) },
                                buttonSize = 50.dp,
                                iconSize = 22.dp,
                                modifier = Modifier.testTag("play_audio_button"),
                            )

                            InteractiveWaveformView(
                                peaks = clip.waveformPeaks,
                                progress = if (thisPlaying) playProgress else 0f,
                                isPlaying = thisPlaying,
                                modifier = Modifier.weight(1f),
                            )

                            IconButton(
                                onClick = {
                                    pendingExportClip = clip
                                    exportLauncher.launch(clip.fileName)
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("export_wav_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = "ذخیره فایل WAV",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            IconButton(
                                onClick = { onShareClip(clip) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("share_wav_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = "اشتراک‌گذاری",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
