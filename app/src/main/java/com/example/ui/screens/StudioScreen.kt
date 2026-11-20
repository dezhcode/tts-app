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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.example.ui.VoiceAvailabilityState
import com.example.ui.components.AnimatedPlaybackButton
import com.example.ui.components.CillaHairline
import com.example.ui.components.CompactGenderSelector
import com.example.ui.components.CompactVoiceSelector
import com.example.ui.components.InteractiveWaveformView
import com.example.ui.theme.CillaMotion
import com.example.ui.theme.CillaRadius
import com.example.ui.theme.CillaSpacing
import com.example.ui.theme.CillaStroke
import com.example.ui.theme.LocalCillaFonts
import java.util.Locale

private val studioPresets = listOf(
    "معرفی" to "سلام! با آوینا می‌توانید متن فارسی را آفلاین به گفتار طبیعی تبدیل کنید.",
    "شعر" to "الا یا ایها الساقی ادر کاسا و ناولها؛ که عشق آسان نمود اول ولی افتاد مشکل‌ها.",
    "خبر" to "امروز بیستم آبان ۱۴۰۵ است و تبدیل متن به گفتار روی دستگاه اجرا می‌شود.",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    inputText: String,
    normalizedPreview: String,
    settings: UserSettings,
    modelState: ModelEngineState,
    synthesisState: SynthesisUiState,
    voices: List<VoicePromptItem>,
    activeCustomVoice: VoicePromptItem?,
    voiceAvailability: VoiceAvailabilityState = VoiceAvailabilityState(),
    isPlaying: Boolean,
    playingClipId: String?,
    playProgress: Float,
    onTextChange: (String) -> Unit,
    onSelectNarratorGender: (NarratorGender) -> Unit,
    onSelectVoice: (String) -> Unit,
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
    var showVoicePicker by remember { mutableStateOf(false) }
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
    val activeVoiceDisplayName = voiceAvailability.activeVoiceLabel
    val studioFonts = LocalCillaFonts.current

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = CillaSpacing.maxContentWidth)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CillaSpacing.screenHorizontal)
                .padding(top = CillaSpacing.md, bottom = CillaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Essential model notice only when offline weights are missing or failed
            AnimatedVisibility(
                visible = modelState is ModelEngineState.Missing || modelState is ModelEngineState.Error,
                enter = fadeIn(CillaMotion.standardTween()) + expandVertically(),
                exit = fadeOut(CillaMotion.quickTween()) + shrinkVertically(),
            ) {
                Surface(
                    onClick = onOpenModelSetup,
                    shape = RoundedCornerShape(CillaRadius.md),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("go_to_model_setup_button"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = if (modelState is ModelEngineState.Error) {
                                modelState.message
                            } else {
                                "مدل آفلاین نصب نشده است"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "نصب",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }

            // 1. Primary Text Editor — generous usable space, clean subtle border
            Surface(
                shape = RoundedCornerShape(CillaRadius.lg),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(CillaStroke.hairline, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextField(
                    value = inputText,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                        .testTag("text_input_field"),
                    minLines = 7,
                    maxLines = 12,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = studioFonts.persianUi),
                    placeholder = {
                        Text(
                            text = "متن را بنویسید...",
                            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = studioFonts.persianUi),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f),
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
            }

            // 2. Compact Text Presets & Editor Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    studioPresets.forEach { (title, sample) ->
                        val selected = inputText == sample
                        Surface(
                            onClick = { onTextChange(sample) },
                            shape = RoundedCornerShape(CillaRadius.sm),
                            color = if (selected) {
                                MaterialTheme.colorScheme.onBackground
                            } else {
                                Color.Transparent
                            },
                            border = if (selected) {
                                null
                            } else {
                                BorderStroke(CillaStroke.hairline, MaterialTheme.colorScheme.outlineVariant)
                            },
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.surface
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

                    IconButton(
                        onClick = { showFineTune = !showFineTune },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toggle_tuning_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "تنظیمات صدا",
                            tint = if (showFineTune) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // Collapsible Acoustic Settings (hidden by default)
            AnimatedVisibility(
                visible = showFineTune,
                enter = fadeIn(CillaMotion.standardTween()) + expandVertically(),
                exit = fadeOut(CillaMotion.quickTween()) + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CillaRadius.md))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("نرمال‌سازی فارسی", style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = settings.autoNormalizePersian,
                            onCheckedChange = onUpdateAutoNormalize,
                        )
                    }

                    if (settings.autoNormalizePersian && normalizedPreview.isNotBlank()) {
                        Text(
                            text = normalizedPreview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(CillaRadius.sm))
                                .background(MaterialTheme.colorScheme.background)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }

                    CillaHairline()

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("تنوع لحن", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = String.format(Locale.US, "%.2f", settings.temperature),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Slider(
                            value = settings.temperature,
                            onValueChange = onUpdateTemperature,
                            valueRange = 0.1f..1.4f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.onBackground,
                                activeTrackColor = MaterialTheme.colorScheme.onBackground,
                            ),
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("مکث جملات", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = "${settings.sentencePauseMs} ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Slider(
                            value = settings.sentencePauseMs.toFloat(),
                            onValueChange = { onUpdatePauseMs(it.toInt()) },
                            valueRange = 0f..600f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.onBackground,
                                activeTrackColor = MaterialTheme.colorScheme.onBackground,
                            ),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("حذف سکوت", style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = settings.trimSilence,
                            onCheckedChange = onUpdateTrimSilence,
                        )
                    }
                }
            }

            // 3. Compact Voice & Gender Controls (single calm horizontal bar)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactVoiceSelector(
                    voiceName = activeVoiceDisplayName,
                    isCustomVoiceActive = voiceAvailability.isUsingCustomRecordedVoice,
                    onClick = { showVoicePicker = true },
                    modifier = Modifier.weight(1f),
                )

                CompactGenderSelector(
                    selectedGender = voiceAvailability.activeGender ?: settings.narratorGender,
                    isCustomVoiceActive = voiceAvailability.activeGender == null,
                    femaleEnabled = voiceAvailability.isFemaleAvailable,
                    maleEnabled = voiceAvailability.isMaleAvailable,
                    onSelectGender = onSelectNarratorGender,
                )
            }

            // 4. Single Prominent Primary Action — "تبدیل به گفتار"
            Button(
                onClick = onSynthesize,
                enabled = !isSynthesizing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("synthesize_button"),
                shape = RoundedCornerShape(CillaRadius.lg),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.surface,
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                ),
            ) {
                if (isSynthesizing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.surface,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "در حال تبدیل...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "تبدیل به گفتار",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (synthesisState is SynthesisUiState.Error) {
                Surface(
                    shape = RoundedCornerShape(CillaRadius.md),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = synthesisState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }

            // 5. Minimal Audio Output Player (shown only after synthesis)
            AnimatedVisibility(
                visible = synthesisState is SynthesisUiState.Success,
                enter = fadeIn(CillaMotion.standardTween()) + expandVertically(),
                exit = fadeOut(CillaMotion.quickTween()) + shrinkVertically(),
            ) {
                val success = synthesisState as? SynthesisUiState.Success ?: return@AnimatedVisibility
                val clip = success.clip
                val thisPlaying = isPlaying && playingClipId == clip.id

                Surface(
                    shape = RoundedCornerShape(CillaRadius.lg),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(CillaStroke.hairline, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AnimatedPlaybackButton(
                            isPlaying = thisPlaying,
                            onClick = { onPlayClip(clip) },
                            buttonSize = 42.dp,
                            iconSize = 17.dp,
                            modifier = Modifier.testTag("play_audio_button"),
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            InteractiveWaveformView(
                                peaks = clip.waveformPeaks,
                                progress = if (thisPlaying) playProgress else 0f,
                                isPlaying = thisPlaying,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = clip.voiceLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(
                                        text = String.format(Locale.US, "%.1fs", clip.durationSec),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
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
                                contentDescription = "ذخیره فایل صوتی",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp),
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
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showVoicePicker) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showVoicePicker = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            CompactVoicePickerSheet(
                voiceAvailability = voiceAvailability,
                voices = voices,
                onSelectGender = { gender ->
                    onSelectNarratorGender(gender)
                    showVoicePicker = false
                },
                onSelectVoice = { id ->
                    onSelectVoice(id)
                    showVoicePicker = false
                },
                onManageVoices = {
                    showVoicePicker = false
                    onNavigateToVoices()
                },
            )
        }
    }
}

@Composable
private fun CompactVoicePickerSheet(
    voiceAvailability: VoiceAvailabilityState,
    voices: List<VoicePromptItem>,
    onSelectGender: (NarratorGender) -> Unit,
    onSelectVoice: (String) -> Unit,
    onManageVoices: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = CillaSpacing.screenHorizontal, vertical = CillaSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "انتخاب صدا",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!voiceAvailability.isPhononBackend) {
                TextButton(
                    onClick = onManageVoices,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "صدای جدید",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        CillaHairline()

        if (voiceAvailability.availablePhononVoices.isNotEmpty()) {
            voiceAvailability.availablePhononVoices.forEach { asset ->
                val selected = voiceAvailability.activeVoiceId == asset.id
                VoicePickerRow(
                    title = asset.label,
                    subtitle = asset.gender?.roleLabel,
                    selected = selected,
                    enabled = true,
                    onClick = { onSelectVoice(asset.id) },
                )
            }
            if (!voiceAvailability.isMaleAvailable) {
                VoicePickerRow(
                    title = "صدای مرد",
                    subtitle = "ناموجود در مدل",
                    selected = false,
                    enabled = false,
                    onClick = {},
                )
            }
        } else {
            VoicePickerRow(
                title = NarratorGender.FEMALE.persianName,
                subtitle = NarratorGender.FEMALE.roleLabel,
                selected = voiceAvailability.activeGender == NarratorGender.FEMALE,
                enabled = voiceAvailability.isFemaleAvailable,
                onClick = { onSelectGender(NarratorGender.FEMALE) },
            )
            VoicePickerRow(
                title = NarratorGender.MALE.persianName,
                subtitle = if (voiceAvailability.isMaleAvailable) NarratorGender.MALE.roleLabel else "ناموجود در مدل",
                selected = voiceAvailability.activeGender == NarratorGender.MALE,
                enabled = voiceAvailability.isMaleAvailable,
                onClick = { onSelectGender(NarratorGender.MALE) },
            )
        }

        if (!voiceAvailability.isPhononBackend && voices.isNotEmpty()) {
            CillaHairline(modifier = Modifier.padding(vertical = 4.dp))
            voices.forEach { voice ->
                val selected = voiceAvailability.activeVoiceId == voice.id
                VoicePickerRow(
                    title = voice.label,
                    subtitle = if (voice.isSafetensorsCache) null else String.format(Locale.US, "%.1fs", voice.durationSec),
                    selected = selected,
                    enabled = true,
                    onClick = { onSelectVoice(voice.id) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun VoicePickerRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CillaRadius.md))
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
            )
            .clickable(enabled = enabled, onClick = onClick)
            .minimumInteractiveComponentSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                    ),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                },
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    },
                )
            }
        }

        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "انتخاب‌شده",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
