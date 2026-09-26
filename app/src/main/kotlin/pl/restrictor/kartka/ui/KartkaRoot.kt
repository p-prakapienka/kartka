package pl.restrictor.kartka.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.restrictor.kartka.R
import pl.restrictor.kartka.data.DeckFiles
import pl.restrictor.kartka.data.DeckRepository
import pl.restrictor.kartka.data.RepeatSettings
import pl.restrictor.kartka.data.StudyTarget

private data class ImportRequest(val collectionOnly: Boolean, val preferredTopicId: Long?)

@Composable
fun KartkaRoot(repository: DeckRepository, repeatSettings: RepeatSettings) {
    val navController = rememberNavController()
    val importViewModel = viewModel<ImportViewModel>(factory = ImportViewModel.factory(repository))
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var importRequest by remember { mutableStateOf(ImportRequest(false, null)) }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val request = importRequest
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { DeckFiles.read(context.contentResolver, uri) }
            }
            text.fold(
                onSuccess = { importViewModel.offer(it, request.collectionOnly, request.preferredTopicId) },
                onFailure = {
                    snackbar.showSnackbar(it.message ?: context.getString(R.string.could_not_read))
                },
            )
        }
    }
    fun launchImport(collectionOnly: Boolean, preferredTopicId: Long?) {
        importRequest = ImportRequest(collectionOnly, preferredTopicId)
        openFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
    }

    LaunchedEffect(importViewModel) {
        importViewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = "topics", modifier = Modifier.fillMaxSize()) {
            composable("topics") {
                TopicsScreen(
                    repository = repository,
                    onOpen = { navController.navigate("topics/$it") },
                    onImport = { launchImport(false, null) },
                    onSettings = { navController.navigate("settings") },
                    onMessage = { message -> scope.launch { snackbar.showSnackbar(message) } },
                )
            }
            composable("topics/{topicId}") { entry ->
                val topicId = entry.arguments?.getString("topicId")?.toLongOrNull() ?: return@composable
                CollectionsScreen(
                    repository = repository,
                    topicId = topicId,
                    onBack = { navController.popBackStack() },
                    onOpen = { navController.navigate("topics/$topicId/collections/$it") },
                    onStudy = { navController.navigate("study/topic/$topicId") },
                    onImport = { launchImport(true, topicId) },
                    onDeleted = {
                        navController.popBackStack()
                    },
                    onMessage = { message -> scope.launch { snackbar.showSnackbar(message) } },
                )
            }
            composable("topics/{topicId}/collections/{collectionId}") { entry ->
                val collectionId = entry.arguments?.getString("collectionId")?.toLongOrNull() ?: return@composable
                CardsScreen(
                    repository = repository,
                    collectionId = collectionId,
                    onBack = { navController.popBackStack() },
                    onStudy = { navController.navigate("study/collection/$collectionId") },
                    onDeleted = { navController.popBackStack() },
                    onMessage = { message -> scope.launch { snackbar.showSnackbar(message) } },
                )
            }
            composable("settings") {
                SettingsScreen(settings = repeatSettings, onBack = { navController.popBackStack() })
            }
            composable("study/topic/{topicId}") { entry ->
                val topicId = entry.arguments?.getString("topicId")?.toLongOrNull() ?: return@composable
                StudyScreen(
                    repository = repository,
                    repeatSettings = repeatSettings,
                    target = StudyTarget.Topic(topicId),
                    onBack = { navController.popBackStack() },
                )
            }
            composable("study/collection/{collectionId}") { entry ->
                val collectionId = entry.arguments?.getString("collectionId")?.toLongOrNull() ?: return@composable
                StudyScreen(
                    repository = repository,
                    repeatSettings = repeatSettings,
                    target = StudyTarget.Collection(collectionId),
                    onBack = { navController.popBackStack() },
                )
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        )
    }

    importViewModel.ui?.let { ui ->
        ImportSheet(
            ui = ui,
            onSelectTopic = importViewModel::selectTopic,
            onConfirm = importViewModel::confirm,
            onDismiss = importViewModel::dismiss,
        )
    }
}
