package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
import com.example.ui.components.StatusPill
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.ModelSetupSheetContent
import com.example.ui.screens.StudioScreen
import com.example.ui.screens.VoicesScreen
import com.example.ui.theme.CillaTtsTheme

const val ROUTE_STUDIO = "studio"
const val ROUTE_VOICES = "voices"
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
                CillaTtsTheme(darkTheme = true) {
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
                label = "گفتار",
                selectedIcon = Icons.Rounded.GraphicEq,
                unselectedIcon = Icons.Outlined.GraphicEq,
                testTag = "nav_studio",
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
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 22.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "آوینا ✦",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = "استودیوی صدای فارسی • آفلاین",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        when (modelState) {
                            is ModelEngineState.Ready -> StatusPill(
                                text = "مدل آماده",
                                dotColor = MaterialTheme.colorScheme.primary,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { showModelSheet = true }
                                    .testTag("nav_model"),
                            )
                            is ModelEngineState.Loading -> StatusPill(
                                text = "در حال بارگذاری...",
                                dotColor = MaterialTheme.colorScheme.secondary,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { showModelSheet = true }
                                    .testTag("nav_model"),
                            )
                            else -> StatusPill(
                                text = "نصب مدل",
                                dotColor = MaterialTheme.colorScheme.secondary,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { showModelSheet = true }
                                    .testTag("nav_model"),
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

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_STUDIO,
                    modifier = Modifier.weight(1f).padding(bottom = if (useRail) 0.dp else 92.dp),
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
                            onOpenModelSetup = { showModelSheet = true },
                            onSelectPresetVoice = viewModel::selectVoice,
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
                }
            }
        }

        if (!useRail) {
                    Box(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .windowInsetsPadding(WindowInsets.navigationBars),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(40.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 12.dp,
                            tonalElevation = 0.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                destinations.forEach { dest ->
                                    val selected = currentRoute == dest.route
                                    Surface(
                                        onClick = { navigateSingleTop(dest.route) },
                                        shape = RoundedCornerShape(32.dp),
                                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.weight(1f).testTag(dest.testTag),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 13.dp, horizontal = 10.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = dest.label,
                                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            if (selected) {
                                                Spacer(Modifier.width(8.dp))
                                                Text(dest.label, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
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
                    inventory = inventory,
                    modelState = modelState,
                )
            }
        }
    }
}
