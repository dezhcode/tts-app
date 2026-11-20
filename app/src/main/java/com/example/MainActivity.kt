package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.MainViewModel
import com.example.ui.ModelEngineState
import com.example.ui.components.CillaBottomBar
import com.example.ui.components.CillaHairline
import com.example.ui.components.DockItem
import com.example.ui.components.ModelStatusOrb
import com.example.ui.screens.FontLibraryScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.ModelSetupSheetContent
import com.example.ui.screens.StudioScreen
import com.example.ui.screens.VoicesScreen
import com.example.ui.theme.CillaMotion
import com.example.ui.theme.CillaSpacing
import com.example.ui.theme.CillaStroke
import com.example.ui.theme.CillaTtsTheme
import com.example.ui.theme.LocalCillaFonts

const val ROUTE_STUDIO = "studio"
const val ROUTE_VOICES = "voices"
const val ROUTE_LIBRARY = "library"
const val ROUTE_FONTS = "fonts"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val customFonts by viewModel.customFonts.collectAsStateWithLifecycle()

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                CillaTtsTheme(
                    darkTheme = settings.darkTheme,
                    typographySettings = settings.typography,
                    customFonts = customFonts,
                ) {
                    CillaTtsApp(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CillaTtsApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    var showModelSheet by remember { mutableStateOf(false) }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val normalizedPreview by viewModel.normalizedPreview.collectAsStateWithLifecycle()
    val modelState by viewModel.modelState.collectAsStateWithLifecycle()
    val inventory by viewModel.inventory.collectAsStateWithLifecycle()
    val synthesisState by viewModel.synthesisState.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val voices by viewModel.voices.collectAsStateWithLifecycle()
    val clips by viewModel.clips.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val customFonts by viewModel.customFonts.collectAsStateWithLifecycle()
    val voiceAvailability by viewModel.voiceAvailability.collectAsStateWithLifecycle()

    val isPlaying by viewModel.audioPlayer.isPlaying.collectAsStateWithLifecycle()
    val playingClipId by viewModel.audioPlayer.playingClipId.collectAsStateWithLifecycle()
    val playProgress by viewModel.audioPlayer.progress.collectAsStateWithLifecycle()

    val isRecording by viewModel.voiceRecorder.isRecording.collectAsStateWithLifecycle()
    val micLevel by viewModel.voiceRecorder.micLevel.collectAsStateWithLifecycle()
    val recordedSeconds by viewModel.voiceRecorder.elapsedSeconds.collectAsStateWithLifecycle()

    val activeCustomVoice = remember(voices, settings.activeVoiceId) {
        voices.firstOrNull { it.id == settings.activeVoiceId }
    }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissBanner()
        }
    }

    val destinations = remember {
        listOf(
            DockItem(
                route = ROUTE_STUDIO,
                label = "استودیو",
                selectedIcon = Icons.Rounded.GraphicEq,
                unselectedIcon = Icons.Outlined.GraphicEq,
                testTag = "nav_studio",
            ),
            DockItem(
                route = ROUTE_VOICES,
                label = "صداها",
                selectedIcon = Icons.Rounded.RecordVoiceOver,
                unselectedIcon = Icons.Outlined.RecordVoiceOver,
                testTag = "nav_voices",
            ),
            DockItem(
                route = ROUTE_LIBRARY,
                label = "آرشیو",
                selectedIcon = Icons.Rounded.LibraryMusic,
                unselectedIcon = Icons.Outlined.LibraryMusic,
                testTag = "nav_library",
            ),
            DockItem(
                route = ROUTE_FONTS,
                label = "فونت‌ها",
                selectedIcon = Icons.Rounded.TextFields,
                unselectedIcon = Icons.Outlined.TextFields,
                testTag = "nav_fonts",
            ),
        )
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_STUDIO

    fun navigateSingleTop(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val useRail = maxWidth >= 600.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // Minimal Avina Header — Brand name + essential model status only
            val avinaFonts = LocalCillaFonts.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CillaSpacing.screenHorizontal, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Avina",
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = avinaFonts.englishHeading,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }

                ModelStatusOrb(
                    label = when (modelState) {
                        is ModelEngineState.Ready -> "آماده"
                        is ModelEngineState.Loading -> "بارگذاری..."
                        else -> "نصب مدل"
                    },
                    isReady = modelState is ModelEngineState.Ready,
                    isLoading = modelState is ModelEngineState.Loading,
                    onClick = { showModelSheet = true },
                    modifier = Modifier.testTag("nav_model"),
                )
            }

            CillaHairline()

            // Main Content Region + Adaptive Rail / Bottom Bar
            Box(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (useRail) {
                        NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                            destinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateSingleTop(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label,
                                        )
                                    },
                                    label = { Text(dest.label) },
                                    modifier = Modifier.testTag(dest.testTag),
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(CillaStroke.hairline)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                    }

                    val routeOrder = remember {
                        mapOf(
                            ROUTE_STUDIO to 0,
                            ROUTE_VOICES to 1,
                            ROUTE_LIBRARY to 2,
                            ROUTE_FONTS to 3,
                        )
                    }

                    // Restrained RTL directional transitions — smooth glide + fade, zero visual jitter
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_STUDIO,
                        modifier = Modifier.weight(1f),
                        enterTransition = {
                            val from = routeOrder[initialState.destination.route] ?: 0
                            val to = routeOrder[targetState.destination.route] ?: 0
                            val dir = if (to >= from) -1 else 1
                            slideInHorizontally(
                                animationSpec = tween(durationMillis = CillaMotion.PageMs, easing = CillaMotion.EaseOut),
                                initialOffsetX = { fullWidth -> (fullWidth * 0.12f).toInt() * dir },
                            ) + fadeIn(
                                animationSpec = tween(durationMillis = CillaMotion.StandardMs, easing = CillaMotion.EaseOut),
                            )
                        },
                        exitTransition = {
                            val from = routeOrder[initialState.destination.route] ?: 0
                            val to = routeOrder[targetState.destination.route] ?: 0
                            val dir = if (to >= from) 1 else -1
                            slideOutHorizontally(
                                animationSpec = tween(durationMillis = CillaMotion.StandardMs, easing = CillaMotion.EaseOut),
                                targetOffsetX = { fullWidth -> (fullWidth * 0.08f).toInt() * dir },
                            ) + fadeOut(
                                animationSpec = tween(durationMillis = CillaMotion.QuickMs, easing = CillaMotion.EaseOut),
                            )
                        },
                        popEnterTransition = {
                            val from = routeOrder[initialState.destination.route] ?: 0
                            val to = routeOrder[targetState.destination.route] ?: 0
                            val dir = if (to >= from) -1 else 1
                            slideInHorizontally(
                                animationSpec = tween(durationMillis = CillaMotion.PageMs, easing = CillaMotion.EaseOut),
                                initialOffsetX = { fullWidth -> (fullWidth * 0.12f).toInt() * dir },
                            ) + fadeIn(
                                animationSpec = tween(durationMillis = CillaMotion.StandardMs, easing = CillaMotion.EaseOut),
                            )
                        },
                        popExitTransition = {
                            val from = routeOrder[initialState.destination.route] ?: 0
                            val to = routeOrder[targetState.destination.route] ?: 0
                            val dir = if (to >= from) 1 else -1
                            slideOutHorizontally(
                                animationSpec = tween(durationMillis = CillaMotion.StandardMs, easing = CillaMotion.EaseOut),
                                targetOffsetX = { fullWidth -> (fullWidth * 0.08f).toInt() * dir },
                            ) + fadeOut(
                                animationSpec = tween(durationMillis = CillaMotion.QuickMs, easing = CillaMotion.EaseOut),
                            )
                        },
                    ) {
                        composable(ROUTE_STUDIO) {
                            StudioScreen(
                                inputText = inputText,
                                normalizedPreview = normalizedPreview,
                                settings = settings,
                                modelState = modelState,
                                synthesisState = synthesisState,
                                voices = voices,
                                activeCustomVoice = activeCustomVoice,
                                voiceAvailability = voiceAvailability,
                                isPlaying = isPlaying,
                                playingClipId = playingClipId,
                                playProgress = playProgress,
                                onTextChange = viewModel::updateInputText,
                                onSelectNarratorGender = viewModel::selectNarratorGender,
                                onSelectVoice = viewModel::selectVoice,
                                onSynthesize = viewModel::synthesizeCurrentText,
                                onPlayClip = viewModel::togglePlayClip,
                                onShareClip = { clip ->
                                    viewModel.createShareIntent(clip)?.let { shareIntent ->
                                        context.startActivity(Intent.createChooser(shareIntent, "اشتراک‌گذاری فایل صوتی"))
                                    }
                                },
                                onExportClip = viewModel::exportClipToUri,
                                onUpdateTemperature = viewModel::updateTemperature,
                                onUpdateFlowSteps = viewModel::updateFlowSteps,
                                onUpdatePauseMs = viewModel::updateSentencePause,
                                onUpdateTrimSilence = viewModel::updateTrimSilence,
                                onUpdateAutoNormalize = viewModel::updateAutoNormalize,
                                onOpenModelSetup = { showModelSheet = true },
                                onNavigateToVoices = { navigateSingleTop(ROUTE_VOICES) },
                            )
                        }
                        composable(ROUTE_VOICES) {
                            VoicesScreen(
                                voices = voices,
                                activeVoiceId = settings.activeVoiceId,
                                isRecording = isRecording,
                                micLevel = micLevel,
                                recordedSeconds = recordedSeconds,
                                isPlaying = isPlaying,
                                playingClipId = playingClipId,
                                onStartRecording = viewModel::startVoiceRecording,
                                onStopRecording = viewModel::stopVoiceRecording,
                                onImportVoiceUri = viewModel::importVoiceFromUri,
                                onSelectVoice = viewModel::selectVoice,
                                onPlayVoice = viewModel::togglePlayVoice,
                                onDeleteVoice = viewModel::deleteVoice,
                            )
                        }
                        composable(ROUTE_LIBRARY) {
                            LibraryScreen(
                                clips = clips,
                                isPlaying = isPlaying,
                                playingClipId = playingClipId,
                                playProgress = playProgress,
                                onPlayClip = viewModel::togglePlayClip,
                                onShareClip = { clip ->
                                    viewModel.createShareIntent(clip)?.let { shareIntent ->
                                        context.startActivity(Intent.createChooser(shareIntent, "اشتراک‌گذاری فایل صوتی"))
                                    }
                                },
                                onExportClip = viewModel::exportClipToUri,
                                onDeleteClip = viewModel::deleteClip,
                            )
                        }
                        composable(ROUTE_FONTS) {
                            FontLibraryScreen(
                                typographySettings = settings.typography,
                                customFonts = customFonts,
                                onSelectPersianUiFont = viewModel::selectPersianUiFont,
                                onSelectPersianHeadingFont = viewModel::selectPersianHeadingFont,
                                onSelectPersianWeight = viewModel::selectPersianWeight,
                                onSelectEnglishUiFont = viewModel::selectEnglishUiFont,
                                onSelectEnglishHeadingFont = viewModel::selectEnglishHeadingFont,
                                onSelectEnglishWeight = viewModel::selectEnglishWeight,
                                onImportCustomFontFile = viewModel::importCustomFontFile,
                                onRemoveCustomFontFile = viewModel::removeCustomFontFile,
                                onResetDefaults = viewModel::resetTypographyDefaults,
                            )
                        }
                    }
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                )
            }

            // Seamless architectural bottom navigation bar on handheld widths
            if (!useRail) {
                CillaBottomBar(
                    items = destinations,
                    currentRoute = currentRoute,
                    onSelect = ::navigateSingleTop,
                )
            }
        }

        if (showModelSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showModelSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                ModelSetupSheetContent(
                    settings = settings,
                    inventory = inventory,
                    modelState = modelState,
                    downloadState = downloadState,
                    onDownloadHuggingFace = viewModel::downloadModelFromHuggingFace,
                    onImportLocalFiles = viewModel::importLocalModelFiles,
                    onClearModels = viewModel::clearModelFiles,
                    onToggleDarkTheme = viewModel::updateDarkTheme,
                )
            }
        }
    }
}
