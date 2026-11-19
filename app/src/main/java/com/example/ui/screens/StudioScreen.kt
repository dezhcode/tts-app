package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
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
    "خوشامد" to "سلام! با Avina می‌توانم متن‌های فارسی را کاملاً آفلاین به گفتار طبیعی تبدیل کنم.",
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
                                "فایل‌های مدل آفلاین نصب نشده‌اند"
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

            // Lightweight animated neon waveform; animations run on the UI thread without network assets.
            val infiniteTransition = rememberInfiniteTransition(label = "avina_wave")
            val pulse by infiniteTransition.animateFloat(
                initialValue = 0.75f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "wave_pulse",
            )
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF111A41), Color(0xFF2C174E), Color(0xFF101D42))
                            )
                        )
                        .padding(vertical = 22.dp, horizontal = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("✦ آوینا", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "صدای فارسی، حتی بدون اینترنت",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(78.dp),
                    ) {
                        val heights = listOf(16, 28, 48, 70, 40, 26, 60, 74, 42, 22, 38, 58, 28, 16)
                        heights.forEachIndexed { index, height ->
                            Box(
                                Modifier
                                    .width(7.dp)
                                    .height((height * (if (index % 2 == 0) pulse else 1.75f - pulse)).dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0xFF43D9FF), Color(0xFFB16BFF), Color(0xFFF16CFF))
                                        )
                                    )
                            )
                        }
                    }
                    Text("متن را بنویسید، گوینده را انتخاب کنید و بشنوید", style = MaterialTheme.typography.labelMedium)
                }
            }

            Text("انتخاب گوینده", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                listOf(
                    Triple("voice1", "صدای زن", Color(0xFFE66CFA)),
                    Triple("voice2", "صدای مرد", Color(0xFF4BC9FF)),
                ).forEach { (id, title, accent) ->
                    val selected = settings.activeVoiceId == id
                    val scale by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (selected) 1f else 0.97f,
                        animationSpec = tween(250),
                        label = "voice_card_scale",
                    )
                    Surface(
                        onClick = { onSelectPresetVoice(id) },
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) accent else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.weight(1f).graphicsLayer { scaleX = scale; scaleY = scale }
                            .testTag("voice_preset_$id"),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                            modifier = Modifier.padding(12.dp),
                        ) {
                            AvinaVoicePortrait(isMale = id == "voice2", accent = accent, modifier = Modifier.fillMaxWidth().height(110.dp))
                            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(if (selected) "✓ انتخاب شده" else "برای انتخاب لمس کنید",
                                color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Text(
                "توجه: تفاوت واقعی صداها به مدل نصب‌شده بستگی دارد.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )

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
            val buttonGlow by infiniteTransition.animateFloat(
                initialValue = 0.25f, targetValue = 0.85f,
                animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
                label = "button_glow",
            )
            Button(
                onClick = onSynthesize,
                enabled = !isSynthesizing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .border(1.dp, Color(0xFF58DFFF).copy(alpha = buttonGlow), RoundedCornerShape(50))
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

/** Portraits are drawn locally as vector shapes; no remote assets or network access. */
@Composable
private fun AvinaVoicePortrait(isMale: Boolean, accent: Color, modifier: Modifier = Modifier) {
    val floatAnim = rememberInfiniteTransition(label = "avatar_glow")
    val glow by floatAnim.animateFloat(
        initialValue = 0.18f, targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(1900), RepeatMode.Reverse),
        label = "avatar_pulse",
    )
    Canvas(modifier = modifier.clip(RoundedCornerShape(18.dp))
        .background(Brush.verticalGradient(listOf(Color(0xFF1B234F), Color(0xFF29173E))))) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2, h * 0.47f)
        drawCircle(accent.copy(alpha = glow), radius = w * 0.44f, center = center)
        // shoulders and jacket
        drawOval(Color(0xFF11182F), topLeft = Offset(w * 0.15f, h * 0.76f),
            size = androidx.compose.ui.geometry.Size(w * 0.70f, h * 0.43f))
        if (!isMale) {
            drawOval(Color(0xFF1C1432), topLeft = Offset(w * 0.27f, h * 0.15f),
                size = androidx.compose.ui.geometry.Size(w * 0.46f, h * 0.73f))
        }
        drawOval(Color(0xFFE4AC92), topLeft = Offset(w * 0.35f, h * 0.26f),
            size = androidx.compose.ui.geometry.Size(w * 0.30f, h * 0.48f))
        // hair silhouette
        drawArc(Color(0xFF151325), 180f, 180f, useCenter = true,
            topLeft = Offset(w * 0.32f, h * 0.16f),
            size = androidx.compose.ui.geometry.Size(w * 0.36f, h * 0.32f))
        if (isMale) {
            drawArc(Color(0xFF312124), 0f, 180f, useCenter = true,
                topLeft = Offset(w * 0.38f, h * 0.53f),
                size = androidx.compose.ui.geometry.Size(w * 0.24f, h * 0.19f))
        }
        // headphones
        drawArc(accent, 190f, 160f, useCenter = false,
            topLeft = Offset(w * 0.28f, h * 0.19f),
            size = androidx.compose.ui.geometry.Size(w * 0.44f, h * 0.52f),
            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
        drawRoundRect(accent, topLeft = Offset(w * 0.27f, h * 0.43f),
            size = androidx.compose.ui.geometry.Size(w * 0.075f, h * 0.19f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
        drawRoundRect(accent, topLeft = Offset(w * 0.65f, h * 0.43f),
            size = androidx.compose.ui.geometry.Size(w * 0.075f, h * 0.19f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
        drawCircle(Color(0xFF10152F), radius = w * 0.012f, center = Offset(w * 0.44f, h * 0.48f))
        drawCircle(Color(0xFF10152F), radius = w * 0.012f, center = Offset(w * 0.56f, h * 0.48f))
    }
}
