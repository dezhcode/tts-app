package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.ModelSetupScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.screens.VoicesScreen
import com.example.ui.theme.CillaTtsTheme

const val ROUTE_STUDIO = "studio"
const val ROUTE_VOICES = "voices"
const val ROUTE_MODEL_SETUP = "model_setup"
const val ROUTE_LIBRARY = "library"

private data class NavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
)

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

@Composable
fun CillaTtsApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

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

    val activeVoice = remember(voices, settings.activeVoiceId) {
        voices.firstOrNull { it.id == settings.activeVoiceId } ?: voices.firstOrNull()
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
            NavDestination(
                route = ROUTE_STUDIO,
                label = "استودیو",
                selectedIcon = Icons.Rounded.GraphicEq,
                unselectedIcon = Icons.Outlined.GraphicEq,
                testTag = "nav_studio",
            ),
            NavDestination(
                route = ROUTE_VOICES,
                label = "صداها",
                selectedIcon = Icons.Rounded.RecordVoiceOver,
                unselectedIcon = Icons.Outlined.RecordVoiceOver,
                testTag = "nav_voices",
            ),
            NavDestination(
                route = ROUTE_MODEL_SETUP,
                label = "مدل آفلاین",
                selectedIcon = Icons.Rounded.Memory,
                unselectedIcon = Icons.Outlined.Memory,
                testTag = "nav_model",
            ),
            NavDestination(
                route = ROUTE_LIBRARY,
                label = "بایگانی",
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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
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
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useRail) {
                    NavigationRail {
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

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_STUDIO,
                    modifier = Modifier.weight(1f),
                ) {
                    composable(ROUTE_STUDIO) {
                        StudioScreen(
                            inputText = inputText,
                            normalizedPreview = normalizedPreview,
                            settings = settings,
                            modelState = modelState,
                            synthesisState = synthesisState,
                            activeVoice = activeVoice,
                            isPlaying = isPlaying,
                            playingClipId = playingClipId,
                            playProgress = playProgress,
                            onTextChange = viewModel::updateInputText,
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
                            onNavigateToModelSetup = { navigateSingleTop(ROUTE_MODEL_SETUP) },
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
                    composable(ROUTE_MODEL_SETUP) {
                        ModelSetupScreen(
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
        }
    }
}
