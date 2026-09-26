package pl.restrictor.kartka.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.restrictor.kartka.R
import pl.restrictor.kartka.data.DeckRepository
import pl.restrictor.kartka.data.ExportedDeck
import pl.restrictor.kartka.data.db.TopicSummary

class TopicsViewModel(private val repository: DeckRepository) : ViewModel() {
    private val now = MutableStateFlow(System.currentTimeMillis())
    val topics = now.flatMapLatest { repository.observeTopics(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refreshClock() {
        now.value = System.currentTimeMillis()
    }

    fun save(id: Long?, name: String, front: String, back: String, color: String) {
        viewModelScope.launch {
            val time = System.currentTimeMillis()
            if (id == null) repository.createTopic(name, front, back, color, time)
            else repository.updateTopic(id, name, front, back, color, time)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteTopic(id) }
    }

    companion object {
        fun factory(repository: DeckRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { TopicsViewModel(repository) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicsScreen(
    repository: DeckRepository,
    onOpen: (Long) -> Unit,
    onImport: () -> Unit,
    onSettings: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val viewModel = viewModel<TopicsViewModel>(factory = TopicsViewModel.factory(repository))
    val topics by viewModel.topics.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshClock()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var editor by remember { mutableStateOf<TopicSummary?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<TopicSummary?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pendingExport by remember { mutableStateOf<ExportedDeck?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        val export = pendingExport
        pendingExport = null
        if (uri == null || export == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { pl.restrictor.kartka.data.DeckFiles.write(context.contentResolver, uri, export.json) }
                .onFailure { onMessage(it.message ?: context.getString(R.string.could_not_save)) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                        Text(
                            stringResource(R.string.on_this_phone),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.repeat_times))
                    }
                    TextButton(onClick = onImport) { Text(stringResource(R.string.import_deck)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_topic)) },
            )
        },
    ) { padding ->
        if (topics.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.topics_empty_title),
                body = stringResource(R.string.topics_empty_body),
                action = stringResource(R.string.create_topic),
                onAction = { creating = true },
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 112.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            ) {
                items(topics, key = { it.id }) { topic ->
                    DeckRow(
                        title = topic.name,
                        subtitle = stringResource(R.string.side_arrow, topic.frontLabel, topic.backLabel),
                        meta = dueAndCards(topic.dueCount, topic.cardCount),
                        colorKey = topic.color,
                        onClick = { onOpen(topic.id) },
                        onEdit = { editor = topic },
                        onExport = {
                            scope.launch {
                                val exported = repository.exportTopic(topic.id) ?: return@launch
                                pendingExport = exported
                                exportLauncher.launch(exported.fileName)
                            }
                        },
                        onDelete = { pendingDelete = topic },
                    )
                }
            }
        }
    }

    if (creating || editor != null) {
        val current = editor
        TopicEditorSheet(
            title = stringResource(if (current == null) R.string.add_topic else R.string.edit_topic),
            initialName = current?.name.orEmpty(),
            initialFront = current?.frontLabel.orEmpty(),
            initialBack = current?.backLabel.orEmpty(),
            initialColor = current?.color ?: "teal",
            onDismiss = { creating = false; editor = null },
            onSave = { name, front, back, color ->
                viewModel.save(current?.id, name, front, back, color)
                creating = false
                editor = null
            },
        )
    }
    pendingDelete?.let { topic ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_topic_title),
            body = stringResource(R.string.delete_topic_body),
            onConfirm = {
                viewModel.delete(topic.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}
