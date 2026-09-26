package pl.restrictor.kartka.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import pl.restrictor.kartka.data.DeckFiles
import pl.restrictor.kartka.data.DeckRepository
import pl.restrictor.kartka.data.ExportedDeck
import pl.restrictor.kartka.data.db.CollectionSummary
import pl.restrictor.kartka.data.db.TopicEntity

class CollectionsViewModel(private val repository: DeckRepository, private val topicId: Long) : ViewModel() {
    private val now = MutableStateFlow(System.currentTimeMillis())
    val collections = now.flatMapLatest { repository.observeCollections(topicId, it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    var topic by mutableStateOf<TopicEntity?>(null)
        private set

    init {
        refresh()
    }

    fun refresh() {
        now.value = System.currentTimeMillis()
        viewModelScope.launch { topic = repository.topic(topicId) }
    }

    fun saveCollection(id: Long?, name: String) {
        viewModelScope.launch {
            val time = System.currentTimeMillis()
            if (id == null) repository.createCollection(topicId, name, time)
            else repository.updateCollection(id, name, time)
        }
    }

    fun deleteCollection(id: Long) {
        viewModelScope.launch { repository.deleteCollection(id) }
    }

    fun saveTopic(name: String, front: String, back: String, color: String) {
        viewModelScope.launch {
            repository.updateTopic(topicId, name, front, back, color, System.currentTimeMillis())
            topic = repository.topic(topicId)
        }
    }

    fun deleteTopic() {
        viewModelScope.launch { repository.deleteTopic(topicId) }
    }

    companion object {
        fun factory(repository: DeckRepository, topicId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { CollectionsViewModel(repository, topicId) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    repository: DeckRepository,
    topicId: Long,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onStudy: () -> Unit,
    onImport: () -> Unit,
    onDeleted: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val viewModel = viewModel<CollectionsViewModel>(
        key = "collections-$topicId",
        factory = CollectionsViewModel.factory(repository, topicId),
    )
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val topic = viewModel.topic
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CollectionSummary?>(null) }
    var editingTopic by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<CollectionSummary?>(null) }
    var deleteTopic by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pendingExport by remember { mutableStateOf<ExportedDeck?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        val export = pendingExport
        pendingExport = null
        if (uri == null || export == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { DeckFiles.write(context.contentResolver, uri, export.json) }
                .onFailure { onMessage(it.message ?: context.getString(R.string.could_not_save)) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(topic?.name ?: stringResource(R.string.app_name))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = onImport) { Text(stringResource(R.string.import_deck)) }
                    RowMenu(
                        onEdit = { editingTopic = true },
                        onExport = {
                            scope.launch {
                                val exported = repository.exportTopic(topicId) ?: return@launch
                                pendingExport = exported
                                exportLauncher.launch(exported.fileName)
                            }
                        },
                        onDelete = { deleteTopic = true },
                    )
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_collection)) },
            )
        },
    ) { padding ->
        if (collections.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.collections_empty_title),
                body = stringResource(R.string.collections_empty_body),
                action = stringResource(R.string.add_collection),
                onAction = { creating = true },
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    topic?.let {
                        Text(
                            stringResource(R.string.side_arrow, it.frontLabel, it.backLabel),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onStudy, modifier = Modifier.padding(top = 4.dp)) {
                        Text(stringResource(R.string.study_all), style = MaterialTheme.typography.titleMedium)
                    }
                }
                items(collections, key = { it.id }) { collection ->
                    DeckRow(
                        title = collection.name,
                        subtitle = "",
                        meta = dueAndCards(collection.dueCount, collection.cardCount),
                        colorKey = topic?.color ?: "teal",
                        onClick = { onOpen(collection.id) },
                        onEdit = { editing = collection },
                        onExport = {
                            scope.launch {
                                val exported = repository.exportCollection(collection.id) ?: return@launch
                                pendingExport = exported
                                exportLauncher.launch(exported.fileName)
                            }
                        },
                        onDelete = { pendingDelete = collection },
                    )
                }
            }
        }
    }

    if (creating || editing != null) {
        val current = editing
        CollectionEditorSheet(
            title = stringResource(if (current == null) R.string.add_collection else R.string.edit_collection),
            initialName = current?.name.orEmpty(),
            onDismiss = { creating = false; editing = null },
            onSave = { name ->
                viewModel.saveCollection(current?.id, name)
                creating = false
                editing = null
            },
        )
    }
    if (editingTopic && topic != null) {
        TopicEditorSheet(
            title = stringResource(R.string.edit_topic),
            initialName = topic.name,
            initialFront = topic.frontLabel,
            initialBack = topic.backLabel,
            initialColor = topic.color,
            onDismiss = { editingTopic = false },
            onSave = { name, front, back, color ->
                viewModel.saveTopic(name, front, back, color)
                editingTopic = false
            },
        )
    }
    pendingDelete?.let { collection ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_collection_title),
            body = stringResource(R.string.delete_collection_body),
            onConfirm = {
                viewModel.deleteCollection(collection.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
    if (deleteTopic) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_topic_title),
            body = stringResource(R.string.delete_topic_body),
            onConfirm = {
                viewModel.deleteTopic()
                deleteTopic = false
                onDeleted()
            },
            onDismiss = { deleteTopic = false },
        )
    }
}
