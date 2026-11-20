package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.ModelInventory
import com.example.data.UserSettings
import com.example.ui.DownloadUiState
import com.example.ui.ModelEngineState
import com.example.ui.components.CillaHairline
import com.example.ui.theme.CillaMotion
import com.example.ui.theme.CillaRadius
import com.example.ui.theme.CillaSpacing
import com.example.ui.theme.LocalCillaFonts
import java.util.Locale

@Composable
fun ModelSetupSheetContent(
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
    var selectedTab by remember { mutableIntStateOf(0) }

    val multiFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) onImportLocalFiles(uris)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = CillaSpacing.screenHorizontal, vertical = CillaSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Minimal Sheet Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "مدل آفلاین",
                style = MaterialTheme.typography.headlineSmall,
            )
            if (inventory.totalSizeMb > 0f) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = String.format(Locale.US, "%.1f MB", inventory.totalSizeMb),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(CillaRadius.sm))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }

        CillaHairline()

        // Clean File Inventory Checklist (no redundant inner card)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            inventory.files.forEach { f ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (f.exists) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.outline
                            ),
                    )
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = f.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        text = if (f.exists) String.format(Locale.US, "%.1f MB", f.sizeMb)
                        else if (f.required) "ضروری" else "اختیاری",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (f.exists) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }

        // Clean Segmented Switcher: Hugging Face vs Local Files
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CillaRadius.md))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp),
        ) {
            listOf("دانلود از Hugging Face", "فایل محلی").forEachIndexed { idx, title ->
                val active = selectedTab == idx
                val tabBg by animateColorAsState(
                    targetValue = if (active) MaterialTheme.colorScheme.surface else Color.Transparent,
                    animationSpec = CillaMotion.quickTween(),
                    label = "setup_tab_bg",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(CillaRadius.sm))
                        .background(tabBg)
                        .clickable { selectedTab = idx }
                        .minimumInteractiveComponentSize()
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }

        if (selectedTab == 0) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "توکن Hugging Face",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://huggingface.co/mehdi-hf/cilla-mini"))
                            )
                        }
                    ) {
                        Icon(
                            Icons.Rounded.OpenInBrowser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "صفحه مدل",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        placeholder = { Text("hf_...") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(CillaRadius.md),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.onBackground,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hf_token_input"),
                    )
                }

                val isDownloading = downloadState is DownloadUiState.Downloading
                Button(
                    onClick = { onDownloadHuggingFace(tokenInput) },
                    enabled = !isDownloading,
                    shape = RoundedCornerShape(CillaRadius.lg),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onBackground,
                        contentColor = MaterialTheme.colorScheme.surface,
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("download_model_button"),
                ) {
                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isDownloading) "در حال دانلود..." else "دانلود مدل")
                }

                when (downloadState) {
                    is DownloadUiState.Downloading -> {
                        val p = downloadState.progress
                        if (p.totalBytes > 0L) {
                            LinearProgressIndicator(
                                progress = { p.fraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.5.dp)
                                    .clip(RoundedCornerShape(50)),
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.5.dp),
                            )
                        }
                        Text(
                            text = "${p.currentFile} (${p.fileIndex}/${p.totalFiles})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is DownloadUiState.GatedAuthRequired -> {
                        Text(
                            text = downloadState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    is DownloadUiState.Error -> {
                        Text(
                            text = downloadState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    DownloadUiState.Completed -> {
                        Text(
                            text = "مدل با موفقیت نصب و فعال شد.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    DownloadUiState.Idle -> Unit
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { multiFilePicker.launch(arrayOf("*/*")) },
                    shape = RoundedCornerShape(CillaRadius.lg),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onBackground,
                        contentColor = MaterialTheme.colorScheme.surface,
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("import_files_button"),
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("انتخاب فایل‌های مدل")
                }
            }
        }

        if (modelState is ModelEngineState.Ready) {
            Surface(
                shape = RoundedCornerShape(CillaRadius.md),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "مدل آماده است",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }

        CillaHairline()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("حالت تاریک", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = settings.darkTheme,
                onCheckedChange = onToggleDarkTheme,
            )
        }

        if (inventory.totalSizeMb > 0f) {
            TextButton(
                onClick = onClearModels,
                modifier = Modifier
                    .align(Alignment.End)
                    .minimumInteractiveComponentSize(),
            ) {
                Icon(
                    Icons.Rounded.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("پاکسازی حافظه مدل", color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}
