package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ClipRecord
import com.example.data.UserSettings
import com.example.data.VoicePromptItem
import com.example.ui.ModelEngineState
import com.example.ui.SynthesisUiState
import com.example.ui.components.InteractiveWaveformView
import java.util.Locale

private val minimalPresets = listOf(
    "خوشامد" to "سلام! با مدل سیلا مینی می‌توانم متن‌های فارسی را کاملاً آفلاین به گفتار طبیعی تبدیل کنم.",
    "شعر" to "الا یا ایها الساقی ادر کاسا و ناولها؛ که عشق آسان نمود اول ولی افتاد مشکل‌ها.",
    "اعداد" to "امروز چهاردهم آبان ۱۴۰۵ است و پردازش صوتی با نرخ ۲۴ کیلوهرتز انجام می‌شود.",
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
    onOpenModelSetup: () -> Unit,
    onSelectPresetVoice: (String) -> Unit,
) {
    var showOptions by remember { mutableStateOf(false) }
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

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Subtle missing-model notice (only shown when model is not yet loaded)
            if (modelState is ModelEngineState.Missing || modelState is ModelEngineState.Error) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenModelSetup)
                        .testTag("go_to_model_setup_button"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = if (modelState is ModelEngineState.Error) {
                                modelState.message
                            } else {
                                "فایل‌های مدل cilla-mini نصب نشده‌اند"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "نصب مدل ←",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }

            // Compact voice picker: all available presets visible at a glance.
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("انتخاب گوینده", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            "voice1" to "زن",
                            "voice2" to "مرد",
                            "parisa" to "پریسا",
                        ).forEach { (id, label) ->
                            val selected = settings.activeVoiceId.ifBlank { "parisa" } == id
                            Surface(
                                onClick = { onSelectPresetVoice(id) },
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                ),
                                modifier = Modifier.weight(1f).testTag("voice_preset_$id"),
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 13.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(label, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        if (selected) "● انتخاب‌شده" else "انتخاب",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("متن‌های نمونه", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    minimalPresets.forEach { (title, sample) ->
                        FilterChip(
                            selected = inputText == sample,
                            onClick = { onTextChange(sample) },
                            label = { Text(title) },
                            shape = RoundedCornerShape(50),
                        )
                    }
                }
            }

            // Clean Editorial Text Canvas Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    TextField(
                        value = inputText,
                        onValueChange = onTextChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("text_input_field"),
                        minLines = 5,
                        maxLines = 10,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        placeholder = {
                            Text(
                                text = "متن فارسی خود را اینجا بنویسید...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${inputText.length} کاراکتر",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                            TextButton(
                                onClick = { showOptions = !showOptions },
                                modifier = Modifier.testTag("toggle_tuning_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (showOptions) "بستن تنظیمات" else "تنظیمات",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }

            // Collapsible Minimal Tuning & Normalizer Preview
            AnimatedVisibility(visible = showOptions) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
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
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "نرمال‌سازی خودکار اعداد و حروف",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Switch(
                                checked = settings.autoNormalizePersian,
                                onCheckedChange = onUpdateAutoNormalize,
                            )
                        }

                        if (settings.autoNormalizePersian && normalizedPreview.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
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

                        Text(
                            text = "تنوع لحن (Temperature): ${String.format(Locale.US, "%.2f", settings.temperature)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Slider(
                            value = settings.temperature,
                            onValueChange = onUpdateTemperature,
                            valueRange = 0.1f..1.4f,
                        )

                    }
                }
            }

            // Primary Pill CTA
            val isSynthesizing = synthesisState is SynthesisUiState.Synthesizing
            Button(
                onClick = onSynthesize,
                enabled = !isSynthesizing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("synthesize_button"),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isSynthesizing) {
                        val s = synthesisState as SynthesisUiState.Synthesizing
                        "در حال تولید (${s.sentenceIdx}/${s.totalSentences})..."
                    } else {
                        "تبدیل به گفتار"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (isSynthesizing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)),
                )
            }

            if (synthesisState is SynthesisUiState.Error) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
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

            // Minimal Result Audio Player Card
            if (synthesisState is SynthesisUiState.Success) {
                val clip = synthesisState.clip
                val thisPlaying = isPlaying && playingClipId == clip.id
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FilledIconButton(
                            onClick = { onPlayClip(clip) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("play_audio_button"),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Icon(
                                imageVector = if (thisPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                contentDescription = if (thisPlaying) "توقف" else "پخش",
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            InteractiveWaveformView(
                                peaks = clip.waveformPeaks,
                                progress = if (thisPlaying) playProgress else 0f,
                                isPlaying = thisPlaying,
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f ثانیه • ۲۴ kHz", clip.durationSec),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

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

            Spacer(Modifier.height(12.dp))
        }
    }
}
