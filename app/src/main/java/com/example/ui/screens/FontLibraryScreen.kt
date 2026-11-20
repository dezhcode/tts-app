package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppFontWeight
import com.example.data.TypographySettings
import com.example.ui.components.FontSpecimenCard
import com.example.ui.components.FontWeightSelectorRow
import com.example.ui.components.LiveBilingualSpecimenCard
import com.example.ui.theme.CillaFontCatalog
import com.example.ui.theme.CillaMotion
import com.example.ui.theme.CillaRadius
import com.example.ui.theme.CillaSpacing
import com.example.ui.theme.LocalCillaFonts

@Composable
fun FontLibraryScreen(
    typographySettings: TypographySettings,
    customFonts: Map<String, FontFamily>,
    onSelectPersianUiFont: (String) -> Unit,
    onSelectPersianHeadingFont: (String) -> Unit,
    onSelectPersianWeight: (AppFontWeight) -> Unit,
    onSelectEnglishUiFont: (String) -> Unit,
    onSelectEnglishHeadingFont: (String) -> Unit,
    onSelectEnglishWeight: (AppFontWeight) -> Unit,
    onImportCustomFontFile: (fontId: String, uri: Uri, applyAsHeading: Boolean) -> Unit,
    onRemoveCustomFontFile: (fontId: String) -> Unit,
    onResetDefaults: () -> Unit,
) {
    var languageTab by remember { mutableIntStateOf(0) } // 0 = Persian, 1 = English
    var targetRoleTab by remember { mutableIntStateOf(0) } // 0 = UI Text, 1 = Headings
    var pendingImportFontId by remember { mutableStateOf<String?>(null) }

    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val id = pendingImportFontId
        if (uri != null && id != null) {
            onImportCustomFontFile(id, uri, targetRoleTab == 1)
        }
        pendingImportFontId = null
    }

    val isPersianTab = languageTab == 0
    val isHeadingRole = targetRoleTab == 1

    val displayedFonts = if (isPersianTab) CillaFontCatalog.persianFonts else CillaFontCatalog.englishFonts
    val activeFontId = when {
        isPersianTab && !isHeadingRole -> typographySettings.persianUiFontId
        isPersianTab && isHeadingRole -> typographySettings.persianHeadingFontId
        !isPersianTab && !isHeadingRole -> typographySettings.englishUiFontId
        else -> typographySettings.englishHeadingFontId
    }
    val activeWeight = if (isPersianTab) typographySettings.persianWeight else typographySettings.englishWeight
    val activeEntry = CillaFontCatalog.findById(activeFontId)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = CillaSpacing.maxContentWidth)
                .fillMaxSize()
                .testTag("font_library_list"),
            contentPadding = PaddingValues(
                start = CillaSpacing.screenHorizontal,
                end = CillaSpacing.screenHorizontal,
                top = CillaSpacing.md,
                bottom = CillaSpacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Editorial Typography Studio Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "فونت‌ها",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )

                    TextButton(
                        onClick = onResetDefaults,
                        enabled = !typographySettings.isDefault,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("reset_fonts_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = "بازنشانی به فونت‌های پیش‌فرض",
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "پیش‌فرض",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            // Live Bilingual Specimen Preview Stage
            item {
                LiveBilingualSpecimenCard(typographySettings = typographySettings)
            }

            // Language Category Switcher (Persian vs English)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(CillaRadius.md))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        listOf(
                            "فونت‌های فارسی" to "tab_persian_fonts",
                            "فونت‌های انگلیسی" to "tab_english_fonts",
                        ).forEachIndexed { idx, (label, tag) ->
                            val active = languageTab == idx
                            val bg by animateColorAsState(
                                targetValue = if (active) MaterialTheme.colorScheme.surface else Color.Transparent,
                                animationSpec = CillaMotion.quickTween(),
                                label = "lang_tab_bg",
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(CillaRadius.sm))
                                    .background(bg)
                                    .clickable { languageTab = idx }
                                    .minimumInteractiveComponentSize()
                                    .padding(vertical = 9.dp)
                                    .testTag(tag),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
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

                    // Role Selector: UI Body vs Headings (independent assignment per language)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            "متن رابط کاربری" to "role_ui_text",
                            "عناوین و تیترها" to "role_headings",
                        ).forEachIndexed { idx, (label, tag) ->
                            val active = targetRoleTab == idx
                            val bg by animateColorAsState(
                                targetValue = if (active) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                animationSpec = CillaMotion.quickTween(),
                                label = "role_tab_bg",
                            )
                            val fg by animateColorAsState(
                                targetValue = if (active) {
                                    MaterialTheme.colorScheme.onSecondary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                animationSpec = CillaMotion.quickTween(),
                                label = "role_tab_fg",
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(CillaRadius.md))
                                    .background(bg)
                                    .clickable { targetRoleTab = idx }
                                    .minimumInteractiveComponentSize()
                                    .padding(vertical = 10.dp)
                                    .testTag(tag),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = fg,
                                )
                            }
                        }
                    }
                }
            }

            // Font Weight Selector
            item {
                FontWeightSelectorRow(
                    selectedWeight = activeWeight,
                    supportedWeights = activeEntry?.supportedWeights ?: AppFontWeight.entries,
                    onSelectWeight = { weight ->
                        if (isPersianTab) onSelectPersianWeight(weight) else onSelectEnglishWeight(weight)
                    },
                )
            }

            // Curated Font Specimen Cards
            items(displayedFonts, key = { it.id }) { entry ->
                val isInstalled = CillaFontCatalog.isInstalled(entry, customFonts)
                val isSelected = activeFontId == entry.id

                FontSpecimenCard(
                    entry = entry,
                    isInstalled = isInstalled,
                    isSelected = isSelected,
                    previewWeight = activeWeight,
                    customFonts = customFonts,
                    onSelect = {
                        when {
                            isPersianTab && !isHeadingRole -> onSelectPersianUiFont(entry.id)
                            isPersianTab && isHeadingRole -> onSelectPersianHeadingFont(entry.id)
                            !isPersianTab && !isHeadingRole -> onSelectEnglishUiFont(entry.id)
                            else -> onSelectEnglishHeadingFont(entry.id)
                        }
                    },
                    onRequestImportFile = {
                        pendingImportFontId = entry.id
                        fontPickerLauncher.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream", "*/*"))
                    },
                    onRemoveImportedFile = {
                        onRemoveCustomFontFile(entry.id)
                    },
                )
            }
        }
    }
}
