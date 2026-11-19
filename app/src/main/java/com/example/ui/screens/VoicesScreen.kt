package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.VoicePromptItem
import com.example.data.VoiceSource
import com.example.ui.components.MicLevelMeter
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
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "شبیه‌سازی صدا (Voice Cloning)",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "مدل cilla-mini با دریافت ۳ تا ۱۰ ثانیه صدای تمیز و بدون موسیقی پس‌زمینه، لحن و تن صدا را به‌صورت آفلاین شبیه‌سازی می‌کند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Record Voice Prompt Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "ضبط نمونه صدای مرجع (۲۴ kHz Mono)",
                            style = MaterialTheme.typography.titleMedium,
                        )

                        OutlinedTextField(
                            value = voiceLabel,
                            onValueChange = { voiceLabel = it },
                            label = { Text("نام پروفایل صدا") },
                            singleLine = true,
                            enabled = !isRecording,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        )

                        if (isRecording) {
                            MicLevelMeter(level = micLevel)
                            Text(
                                text = String.format(Locale.US, "در حال ضبط... %.1f / 10.0 ثانیه", recordedSeconds),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }

                        if (permissionDeniedNote) {
                            Text(
                                text = "دسترسی میکروفون رد شد. برای ضبط صدا دسترسی میکروفون را بدهید یا یک فایل WAV آماده وارد کنید.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = {
                                    if (isRecording) {
                                        onStopRecording()
                                    } else {
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) {
                                            onStartRecording(voiceLabel)
                                        } else {
                                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("record_voice_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = if (isRecording) {
                                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                } else {
                                    ButtonDefaults.buttonColors()
                                },
                            ) {
                                Icon(
                                    imageVector = if (isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                    contentDescription = null,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (isRecording) "توقف و ذخیره" else "ضبط صدا")
                            }

                            FilledTonalButton(
                                onClick = { audioPickerLauncher.launch(arrayOf("audio/*", "*/*")) },
                                enabled = !isRecording,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("import_voice_button"),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(Icons.Rounded.AudioFile, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("انتخاب WAV")
                            }
                        }
                    }
                }
            }

            if (voices.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RecordVoiceOver,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp),
                            )
                            Text(
                                text = "هنوز نمونه صدایی ثبت نشده است",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "یک نمونه ۳ تا ۱۰ ثانیه‌ای ضبط کنید، فایل WAV وارد کنید، یا پس از دانلود مدل از نمونه‌های prompts/ استفاده کنید.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(voices, key = { it.id }) { voice ->
                    val isSelected = activeVoiceId == voice.id || (activeVoiceId.isEmpty() && voices.firstOrNull()?.id == voice.id)
                    val thisPlaying = isPlaying && playingClipId == voice.id
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            IconButton(
                                onClick = { onSelectVoice(voice.id) },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                    contentDescription = "انتخاب صدا",
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = voice.label,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                val sourceTag = when (voice.source) {
                                    VoiceSource.RECORDED -> "ضبط‌شده"
                                    VoiceSource.IMPORTED -> "واردشده"
                                    VoiceSource.MODEL_PROMPT -> "مخزن cilla-mini"
                                }
                                val durText = if (voice.isSafetensorsCache) {
                                    "KV Cache (.safetensors)"
                                } else {
                                    String.format(Locale.US, "%.1fs • WAV", voice.durationSec)
                                }
                                Text(
                                    text = "$sourceTag • $durText",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            if (!voice.isSafetensorsCache) {
                                IconButton(
                                    onClick = { onPlayVoice(voice) },
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                ) {
                                    Icon(
                                        imageVector = if (thisPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                        contentDescription = if (thisPlaying) "توقف" else "پخش نمونه",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }

                            if (voice.source != VoiceSource.MODEL_PROMPT) {
                                IconButton(
                                    onClick = { onDeleteVoice(voice) },
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "حذف صدا",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
