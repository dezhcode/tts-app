package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.VoicePromptItem
import com.example.data.VoiceSource
import com.example.ui.components.AnimatedPlayPauseIcon
import com.example.ui.components.CillaHairline
import com.example.ui.components.TactileRecordOrb
import com.example.ui.theme.CillaMotion
import com.example.ui.theme.CillaRadius
import com.example.ui.theme.CillaSpacing
import com.example.ui.theme.CillaStroke
import java.util.Locale

@Composable
fun VoicesScreen(
    voices: List<VoicePromptItem>,
    activeVoiceId: String,
    isRecording: Boolean,
    micLevel: Float,
    recordedSeconds: Float,
    isPlaying: Boolean,
    playingClipId: String?,
    onStartRecording: (String) -> Unit,
    onStopRecording: () -> Unit,
    onImportVoiceUri: (android.net.Uri) -> Unit,
    onSelectVoice: (String) -> Unit,
    onPlayVoice: (VoicePromptItem) -> Unit,
    onDeleteVoice: (VoicePromptItem) -> Unit,
) {
    val context = LocalContext.current
    var voiceLabel by remember { mutableStateOf("صدای من") }
    var permissionDeniedNote by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDeniedNote = false
            onStartRecording(voiceLabel)
        } else {
            permissionDeniedNote = true
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportVoiceUri(uri)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = CillaSpacing.maxContentWidth)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = CillaSpacing.screenHorizontal,
                end = CillaSpacing.screenHorizontal,
                top = CillaSpacing.md,
                bottom = CillaSpacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Text(
                    text = "صداها",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            // Minimalist Recording Stage — clean archival sheet, zero drop shadow
            item {
                Surface(
                    shape = RoundedCornerShape(CillaRadius.xl),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(CillaStroke.hairline, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        TactileRecordOrb(
                            isRecording = isRecording,
                            micLevel = micLevel,
                            onClick = {
                                if (isRecording) {
                                    onStopRecording()
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO,
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        onStartRecording(voiceLabel)
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                        )

                        if (isRecording) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = String.format(Locale.US, "%04.1fs / 10.0s", recordedSeconds),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        if (permissionDeniedNote) {
                            Text(
                                text = "دسترسی میکروفون رد شد.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = voiceLabel,
                                onValueChange = { voiceLabel = it },
                                placeholder = { Text("نام پروفایل صدا") },
                                singleLine = true,
                                enabled = !isRecording,
                                shape = RoundedCornerShape(CillaRadius.md),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    focusedBorderColor = MaterialTheme.colorScheme.onBackground,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                                    focusedContainerColor = MaterialTheme.colorScheme.background,
                                ),
                                modifier = Modifier.weight(1f),
                            )

                            OutlinedButton(
                                onClick = { audioPickerLauncher.launch(arrayOf("audio/*", "*/*")) },
                                enabled = !isRecording,
                                shape = RoundedCornerShape(CillaRadius.md),
                                border = BorderStroke(CillaStroke.hairline, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .height(54.dp)
                                    .testTag("import_voice_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AudioFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("فایل WAV", color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            if (voices.isNotEmpty()) {
                item {
                    Text(
                        text = "ذخیره‌شده",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                // Clean hairline-separated editorial list (no stacked card clutter)
                itemsIndexed(voices, key = { _, v -> v.id }) { index, voice ->
                    val isSelected = activeVoiceId == voice.id
                    val thisPlaying = isPlaying && playingClipId == voice.id
                    val rowBg by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            Color.Transparent
                        },
                        animationSpec = CillaMotion.quickTween(),
                        label = "voice_row_bg",
                    )

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(CillaRadius.md))
                                .background(rowBg)
                                .clickable { onSelectVoice(voice.id) }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = voice.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    )
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                        )
                                    }
                                }
                                if (!voice.isSafetensorsCache) {
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        Text(
                                            text = String.format(Locale.US, "%.1fs", voice.durationSec),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }

                            if (!voice.isSafetensorsCache) {
                                val iconTint by animateColorAsState(
                                    targetValue = if (thisPlaying) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    animationSpec = CillaMotion.quickTween(),
                                    label = "voice_play_tint",
                                )
                                IconButton(
                                    onClick = { onPlayVoice(voice) },
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                ) {
                                    AnimatedPlayPauseIcon(
                                        isPlaying = thisPlaying,
                                        tint = iconTint,
                                        contentDescription = if (thisPlaying) "توقف" else "پخش نمونه",
                                        modifier = Modifier.size(19.dp),
                                    )
                                }
                            }

                            if (voice.source != VoiceSource.MODEL_PROMPT) {
                                IconButton(
                                    onClick = { onDeleteVoice(voice) },
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "حذف صدا",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }

                        if (index < voices.lastIndex) {
                            CillaHairline(modifier = Modifier.padding(horizontal = 8.dp))
                        }
                    }
                }
            }
        }
    }
}
