package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.ClipRecord
import com.example.ui.components.AnimatedPlaybackButton
import com.example.ui.components.CillaHairline
import com.example.ui.components.InteractiveWaveformView
import com.example.ui.theme.CillaMotion
import com.example.ui.theme.CillaRadius
import com.example.ui.theme.CillaSpacing
import java.util.Locale

@Composable
fun LibraryScreen(
    clips: List<ClipRecord>,
    isPlaying: Boolean,
    playingClipId: String?,
    playProgress: Float,
    onPlayClip: (ClipRecord) -> Unit,
    onShareClip: (ClipRecord) -> Unit,
    onExportClip: (ClipRecord, android.net.Uri) -> Unit,
    onDeleteClip: (ClipRecord) -> Unit,
) {
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
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Text(
                    text = "آرشیو",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            if (clips.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(28.dp),
                        )
                        Text(
                            text = "آرشیو خالی است",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                itemsIndexed(clips, key = { _, c -> c.id }) { index, clip ->
                    val thisPlaying = isPlaying && playingClipId == clip.id
                    val rowBg by animateColorAsState(
                        targetValue = if (thisPlaying) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            Color.Transparent
                        },
                        animationSpec = CillaMotion.standardTween(),
                        label = "clip_row_bg",
                    )

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(CillaRadius.lg))
                                .background(rowBg)
                                .padding(horizontal = 10.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = clip.voiceLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (thisPlaying) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(
                                        text = String.format(Locale.US, "%.1fs", clip.durationSec),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            Text(
                                text = clip.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                AnimatedPlaybackButton(
                                    isPlaying = thisPlaying,
                                    onClick = { onPlayClip(clip) },
                                    buttonSize = 42.dp,
                                    iconSize = 18.dp,
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
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                ) {
                                    Icon(
                                        Icons.Rounded.Download,
                                        contentDescription = "ذخیره WAV",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(19.dp),
                                    )
                                }
                                IconButton(
                                    onClick = { onShareClip(clip) },
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                ) {
                                    Icon(
                                        Icons.Rounded.Share,
                                        contentDescription = "اشتراک‌گذاری",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(19.dp),
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteClip(clip) },
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                ) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = "حذف",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(19.dp),
                                    )
                                }
                            }
                        }

                        if (index < clips.lastIndex) {
                            CillaHairline()
                        }
                    }
                }
            }
        }
    }
}
