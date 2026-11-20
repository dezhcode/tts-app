package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.RecordVoiceOver
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import com.example.ui.components.DockItem
import com.example.ui.components.FloatingStudioDock
import com.example.ui.components.ModelStatusOrb
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.ModelSetupSheetContent
import com.example.ui.screens.StudioScreen
import com.example.ui.screens.VoicesScreen
import com.example.ui.theme.CillaTtsTheme

const val ROUTE_STUDIO = "studio"
const val ROUTE_VOICES = "voices"
const val ROUTE_LIBRARY = "library"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                CillaTtsTheme(darkTheme = settings.darkTheme) {
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
                route = ROUTE_LIBRARY,
                label = "آرشیو",
                selectedIcon = Icons.Rounded.LibraryMusic,
                unselectedIcon = Icons.Outlined.LibraryMusic,
                testTag = "nav_library",
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

    val canvasBg = MaterialTheme.colorScheme.background
    val dotGridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(canvasBg)
            .drawBehind {
                // Subtle architectural paper dot-grid atmosphere
                val step = 24.dp.toPx()
                val radius = 0.9.dp.toPx()
                var y = step / 2f
                while (y < size.height) {
                    var x = step / 2f
                    while (x < size.width) {
                        drawCircle(
                            color = dotGridColor,
                            radius = radius,
                            center = Offset(x, y),
                        )
                        x += step
                    }
                    y += step
                }
            },
    ) {
        val useRail = maxWidth >= 600.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // Bespoke Editorial Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "سیلا",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }

                ModelStatusOrb(
                    label = when (modelState) {
                        is ModelEngineState.Ready -> "مدل آماده"
                        is ModelEngineState.Loading -> "بارگذاری..."
                        else -> "نصب مدل"
                    },
                    isReady = modelState is ModelEngineState.Ready,
                    isLoading = modelState is ModelEngineState.Loading,
                    onClick = { showModelSheet = true },
                    modifier = Modifier.testTag("nav_model"),
                )
            }

            // Main Content + Adaptive Rail/Floating Dock
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
                    }

                    val routeOrder = remember {
                        mapOf(
                            ROUTE_STUDIO to 0,
                            ROUTE_LIBRARY to 1,
                        )
                    }

                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_STUDIO,
                        modifier = Modifier.weight(1f),
                        enterTransition = {
                            val from = routeOrder[initialState.destination.route] ?: 0
                            val to = routeOrder[targetState.destination.route] ?: 0
                            // RTL spatial flow: moving forward (0 -> 1 -> 2) enters from the left (-x)
                            val dir = if (to >= from) -1 else 1
                            slideInHorizontally(
                                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                                initialOffsetX = { fullWidth -> (fullWidth * 0.22f).toInt() * dir },
                            ) + fadeIn(
                                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                            ) + scaleIn(
                                initialScale = 0.96f,
                                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                            )
                        },
                        exitTransition = {
                            val from = routeOrder[initialState.destination.route] ?: 0
                            val to = routeOrder[targetState.destination.route] ?: 0
                            val dir = if (to >= from) 1 else -1
                            slideOutHorizontally(
                                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                                targetOffsetX = { fullWidth -> (fullWidth * 0.14f).toInt() * dir },
                            ) + fadeOut(
                                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                            ) + scaleOut(
                                targetScale = 0.96f,
                                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                            )
                        },
                        popEnterTransition = {
                            val from = routeOrder[initialState.destination.route] ?: 0
                            val to = routeOrder[targetState.destination.route] ?: 0
                            val dir = if (to >= from) -1 else 1
                            slideInHorizontally(
                                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                                initialOffsetX = { fullWidth -> (fullWidth * 0.22f).toInt() * dir },
                            ) + fadeIn(
                                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                            ) + scaleIn(
                                initialScale = 0.96f,
                                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                            )
                        },
                        popExitTransition = {
                            val from = routeOrder[initialState.destination.route] ?: 0
                            val to = routeOrder[targetState.destination.route] ?: 0
                            val dir = if (to >= from) 1 else -1
                            slideOutHorizontally(
                                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                                targetOffsetX = { fullWidth -> (fullWidth * 0.14f).toInt() * dir },
                            ) + fadeOut(
                                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                            ) + scaleOut(
                                targetScale = 0.96f,
                                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
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
                                activeCustomVoice = activeCustomVoice,
                                isPlaying = isPlaying,
                                playingClipId = playingClipId,
                                playProgress = playProgress,
                                onTextChange = viewModel::updateInputText,
                                onSelectNarratorGender = viewModel::selectNarratorGender,
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
                                onNavigateToVoices = {},
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
                    }
                }

                // Floating Sculpted Capsule Dock on handheld screens
                if (!useRail) {
                    FloatingStudioDock(
                        items = destinations,
                        currentRoute = currentRoute,
                        onSelect = ::navigateSingleTop,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (useRail) 16.dp else 88.dp),
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
