package pl.restrictor.kartka.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.restrictor.kartka.R
import pl.restrictor.kartka.data.DeckFiles
import pl.restrictor.kartka.data.DeckRepository
import pl.restrictor.kartka.data.ExportedDeck
import pl.restrictor.kartka.data.db.CardEntity
import pl.restrictor.kartka.data.db.CollectionEntity
import pl.restrictor.kartka.data.db.TopicEntity
import pl.restrictor.kartka.domain.DueLabel

class CardsViewModel(private val repository: DeckRepository, private val collectionId: Long) : ViewModel() {
    val cards = repository.observeCards(collectionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    var collection by mutableStateOf<CollectionEntity?>(null)
        private set
    var topic by mutableStateOf<TopicEntity?>(null)
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val current = repository.collection(collectionId)
            collection = current
            topic = current?.let { repository.topic(it.topicId) }
        }
    }

    fun saveCard(id: Long?, front: String, back: String, note: String) {
        viewModelScope.launch {
            val time = System.currentTimeMillis()
            if (id == null) repository.createCard(collectionId, front, back, note, time)
            else repository.updateCard(id, front, back, note, time)
        }
    }

    fun deleteCard(id: Long) {
        viewModelScope.launch { repository.deleteCard(id) }
    }

    fun saveCollection(name: String) {
        viewModelScope.launch {
            repository.updateCollection(collectionId, name, System.currentTimeMillis())
            refresh()
        }
    }

    fun deleteCollection() {
        viewModelScope.launch { repository.deleteCollection(collectionId) }
    }

    companion object {
        fun factory(repository: DeckRepository, collectionId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { CardsViewModel(repository, collectionId) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(
    repository: DeckRepository,
    collectionId: Long,
    onBack: () -> Unit,
    onStudy: () -> Unit,
    onDeleted: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val viewModel = viewModel<CardsViewModel>(
        key = "cards-$collectionId",
        factory = CardsViewModel.factory(repository, collectionId),
    )
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val collection = viewModel.collection
    val topic = viewModel.topic
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CardEntity?>(null) }
    var editingCollection by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<CardEntity?>(null) }
    var deleteCollection by remember { mutableStateOf(false) }
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
    val now = remember(cards) { System.currentTimeMillis() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(collection?.name ?: stringResource(R.string.app_name)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    RowMenu(
                        onEdit = { editingCollection = true },
                        onExport = {
                            scope.launch {
                                val exported = repository.exportCollection(collectionId) ?: return@launch
                                pendingExport = exported
                                exportLauncher.launch(exported.fileName)
                            }
                        },
                        onDelete = { deleteCollection = true },
                    )
                },
            )
        },
        floatingActionButton = {
            if (cards.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { creating = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_card)) },
                )
            }
        },
        bottomBar = {
            if (cards.isNotEmpty()) {
                Button(
                    onClick = onStudy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                ) {
                    Text(stringResource(R.string.study))
                }
            }
        },
    ) { padding ->
        if (cards.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.cards_empty_title),
                body = stringResource(R.string.cards_empty_body),
                action = stringResource(R.string.add_card),
                onAction = { creating = true },
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(cards, key = { it.id }) { card ->
                    DeckRow(
                        title = card.front,
                        subtitle = "",
                        meta = dueLabel(DueLabel.of(card.dueAt, now)),
                        colorKey = topic?.color ?: "teal",
                        onClick = { editing = card },
                        onEdit = { editing = card },
                        onExport = null,
                        onDelete = { pendingDelete = card },
                    )
                }
            }
        }
    }

    if (creating || editing != null) {
        val current = editing
        CardEditorSheet(
            title = stringResource(if (current == null) R.string.add_card else R.string.edit_card),
            frontLabel = topic?.frontLabel ?: stringResource(R.string.front),
            backLabel = topic?.backLabel ?: stringResource(R.string.back_side),
            initialFront = current?.front.orEmpty(),
            initialBack = current?.back.orEmpty(),
            initialNote = current?.note.orEmpty(),
            onDismiss = { creating = false; editing = null },
            onSave = { front, back, note ->
                viewModel.saveCard(current?.id, front, back, note)
                creating = false
                editing = null
            },
        )
    }
    if (editingCollection && collection != null) {
        CollectionEditorSheet(
            title = stringResource(R.string.edit_collection),
            initialName = collection.name,
            onDismiss = { editingCollection = false },
            onSave = { name ->
                viewModel.saveCollection(name)
                editingCollection = false
            },
        )
    }
    pendingDelete?.let { card ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_card_title),
            body = stringResource(R.string.delete_card_body),
            onConfirm = {
                viewModel.deleteCard(card.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
    if (deleteCollection) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_collection_title),
            body = stringResource(R.string.delete_collection_body),
            onConfirm = {
                viewModel.deleteCollection()
                deleteCollection = false
                onDeleted()
            },
            onDismiss = { deleteCollection = false },
        )
    }
    LaunchedEffect(collectionId) { viewModel.refresh() }
}
