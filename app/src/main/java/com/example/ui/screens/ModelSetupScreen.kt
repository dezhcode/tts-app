package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.ModelInventory
import com.example.data.UserSettings
import com.example.ui.DownloadUiState
import com.example.ui.ModelEngineState
import java.util.Locale

@Composable
fun ModelSetupScreen(
    settings: UserSettings,
    inventory: ModelInventory,
    modelState: ModelEngineState,
    downloadState: DownloadUiState,
    onDownloadHuggingFace: (String) -> Unit,
    onImportLocalFiles: (List<Uri>) -> Unit,
    onClearModels: () -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var tokenInput by remember(settings.hfToken) { mutableStateOf(settings.hfToken) }

    val multiFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) onImportLocalFiles(uris)
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
            Text(
                text = "مدیریت مدل آفلاین Cilla Mini",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "مدل mehdi-hf/cilla-mini شامل وزن‌های Pocket-TTS (۱۸.۲ میلیون پارامتر) به همراه توکنایزر SentencePiece است که پس از یک بار دریافت، بدون اینترنت اجرا می‌شود.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // File Checklist Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                        Text(
                            text = "وضعیت فایل‌های محلی",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f MB", inventory.totalSizeMb),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    inventory.files.forEach { f ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = if (f.exists) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (f.exists) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = f.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Text(
                                text = if (f.exists) {
                                    String.format(Locale.US, "%.2f MB", f.sizeMb)
                                } else if (f.required) {
                                    "ضروری"
                                } else {
                                    "اختیاری"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (f.exists) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Download from HuggingFace Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LockOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                        Text(
                            text = "روش ۱: دانلود مستقیم از Hugging Face",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    Text(
                        text = "این مدل تحت مجوز CC BY-NC 4.0 محدود (Gated) است. ابتدا در صفحه مدل دکمه پذیرش قوانین را بزنید و سپس توکن Read حساب Hugging Face خود را وارد کنید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedButton(
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://huggingface.co/mehdi-hf/cilla-mini")
                            )
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Rounded.OpenInBrowser, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("باز کردن صفحه mehdi-hf/cilla-mini")
                    }

                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            label = { Text("Hugging Face Token (hf_...)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("hf_token_input"),
                            shape = RoundedCornerShape(12.dp),
                        )
                    }

                    val isDownloading = downloadState is DownloadUiState.Downloading
                    Button(
                        onClick = { onDownloadHuggingFace(tokenInput) },
                        enabled = !isDownloading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("download_model_button"),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Rounded.CloudDownload, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (isDownloading) "در حال دانلود..." else "بررسی و دانلود فایل‌های مدل")
                    }

                    when (downloadState) {
                        is DownloadUiState.Downloading -> {
                            val p = downloadState.progress
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (p.totalBytes > 0L) {
                                    LinearProgressIndicator(
                                        progress = { p.fraction },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp)),
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                                Text(
                                    text = "${p.currentFile} (${p.fileIndex}/${p.totalFiles}) • ${
                                        String.format(
                                            Locale.US,
                                            "%.1f / %.1f MB",
                                            p.bytesRead / (1024f * 1024f),
                                            p.totalBytes / (1024f * 1024f),
                                        )
                                    }",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        is DownloadUiState.GatedAuthRequired -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    text = downloadState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }
                        is DownloadUiState.Error -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                            ) {
                                Text(
                                    text = downloadState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }
                        DownloadUiState.Completed -> {
                            Text(
                                text = "تمام فایل‌های مدل دریافت و آماده استفاده آفلاین شدند.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        DownloadUiState.Idle -> Unit
                    }
                }
            }

            // Local SAF Import Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "روش ۲: وارد کردن فایل‌های محلی (بدون اینترنت)",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "اگر فایل‌های model.safetensors، tokenizer.model و config.yaml (یا باندل ONNX شامل flow_lm_main.onnx و mimi_decoder.onnx) را روی گوشی دارید، مستقیماً انتخاب کنید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(
                        onClick = { multiFilePicker.launch(arrayOf("*/*")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("import_files_button"),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("انتخاب فایل‌های مدل از حافظه دستگاه")
                    }
                }
            }

            // Diagnostics / Model Inspector Card
            if (modelState is ModelEngineState.Ready) {
                val d = modelState.diagnostics
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Memory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "مشخصات موتور فعال (Inspector)",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = "Engine: ${d.engineName}\n" +
                                    "Parameters: ${String.format(Locale.US, "%,d", d.totalParameters)} (${d.tensorCount} tensors)\n" +
                                    "Architecture: ${d.layerCount} layers • d_model=${d.dModel} • latent=${d.latentDim}\n" +
                                    "Audio: ${d.sampleRate} Hz mono • SentencePiece vocab=${d.vocabSize}\n" +
                                    "Footprint: ${String.format(Locale.US, "%.2f", d.modelSizeMb)} MB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Appearance & Storage Cleanup
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                        Text(
                            text = "تم استودیوی تیره (Dark Studio)",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Switch(
                            checked = settings.darkTheme,
                            onCheckedChange = onToggleDarkTheme,
                        )
                    }

                    if (inventory.totalSizeMb > 0f) {
                        OutlinedButton(
                            onClick = onClearModels,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(6.dp))
                            Text("حذف فایل‌های کش مدل از حافظه", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
