package pl.restrictor.kartka.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import pl.restrictor.kartka.R
import pl.restrictor.kartka.data.DeckRepository
import pl.restrictor.kartka.data.db.TopicEntity
import pl.restrictor.kartka.domain.DeckCodec
import pl.restrictor.kartka.domain.DeckFile
import pl.restrictor.kartka.domain.DeckKind
import pl.restrictor.kartka.domain.ImportMode
import pl.restrictor.kartka.domain.ImportPlanner
import pl.restrictor.kartka.domain.ImportPreview
import pl.restrictor.kartka.domain.ImportRejected
import pl.restrictor.kartka.domain.ParseResult
import pl.restrictor.kartka.domain.TopicUpsert

data class TopicChoice(
    val id: Long,
    val uid: String,
    val name: String,
    val frontLabel: String,
    val backLabel: String,
)

data class ImportUi(
    val file: DeckFile,
    val preview: ImportPreview,
    val topics: List<TopicChoice>,
    val selectedTopicId: Long?,
    val warning: String?,
    val busy: Boolean,
)

class ImportViewModel(private val repository: DeckRepository) : ViewModel() {
    var ui by mutableStateOf<ImportUi?>(null)
        private set

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    fun offer(text: String, collectionOnly: Boolean, preferredTopicId: Long?) {
        viewModelScope.launch {
            when (val parsed = DeckCodec.parse(text, System.currentTimeMillis())) {
                is ParseResult.Err -> _messages.emit(parsed.message)
                is ParseResult.Ok -> {
                    if (collectionOnly && parsed.file is DeckFile.Topic) {
                        _messages.emit("This is a topic file. Import it from the topics screen.")
                        return@launch
                    }
                    val topics = repository.topics().map { it.toChoice() }
                    val selected = when {
                        parsed.file is DeckFile.Topic -> null
                        preferredTopicId != null && topics.any { it.id == preferredTopicId } -> preferredTopicId
                        topics.isNotEmpty() -> topics.first().id
                        else -> null
                    }
                    ui = ImportUi(
                        file = parsed.file,
                        preview = ImportPlanner.preview(parsed.file, repository.knownIds()),
                        topics = topics,
                        selectedTopicId = selected,
                        warning = labelWarning(parsed.file, selected, topics),
                        busy = false,
                    )
                }
            }
        }
    }

    fun selectTopic(id: Long?) {
        val current = ui ?: return
        ui = current.copy(selectedTopicId = id, warning = labelWarning(current.file, id, current.topics))
    }

    fun dismiss() {
        if (ui?.busy != true) ui = null
    }

    fun confirm(mode: ImportMode) {
        val current = ui ?: return
        if (current.busy) return
        ui = current.copy(busy = true)
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val file = current.file
                val creatingNew = file is DeckFile.Collection && current.selectedTopicId == null
                val chosen = current.topics.firstOrNull { it.id == current.selectedTopicId }
                if (file is DeckFile.Collection && !creatingNew && chosen == null) {
                    _messages.emit("That topic is no longer on the phone.")
                    ui = current.copy(busy = false)
                    return@launch
                }
                val destinationUid = when {
                    file !is DeckFile.Collection -> null
                    creatingNew -> UUID.randomUUID().toString()
                    else -> chosen?.uid
                }
                val plan = ImportPlanner.plan(
                    file = file,
                    known = repository.knownIds(),
                    mode = mode,
                    destinationTopicUid = destinationUid,
                    destinationFrontLabel = if (creatingNew) null else chosen?.frontLabel,
                    destinationBackLabel = if (creatingNew) null else chosen?.backLabel,
                    newUid = { UUID.randomUUID().toString() },
                )
                val finalPlan = if (creatingNew && file is DeckFile.Collection && destinationUid != null) {
                    plan.copy(
                        topics = listOf(
                            TopicUpsert(
                                uid = destinationUid,
                                name = file.topicName,
                                frontLabel = file.frontLabel,
                                backLabel = file.backLabel,
                                color = "teal",
                            ),
                        ),
                    )
                } else {
                    plan
                }
                repository.apply(finalPlan, now)
                ui = null
                _messages.emit("Imported ${current.preview.title}")
            } catch (_: ImportRejected) {
                _messages.emit("Choose a topic first.")
                ui = ui?.copy(busy = false)
            } catch (_: Exception) {
                _messages.emit("Could not import that file.")
                ui = ui?.copy(busy = false)
            }
        }
    }

    private fun labelWarning(file: DeckFile, selectedId: Long?, topics: List<TopicChoice>): String? {
        if (file !is DeckFile.Collection) return null
        val topic = topics.firstOrNull { it.id == selectedId } ?: return null
        val same = file.frontLabel.equals(topic.frontLabel, ignoreCase = true) &&
            file.backLabel.equals(topic.backLabel, ignoreCase = true)
        if (same) return null
        return "This file is labeled ${file.frontLabel} → ${file.backLabel}. " +
            "The topic you picked is ${topic.frontLabel} → ${topic.backLabel}."
    }

    companion object {
        fun factory(repository: DeckRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ImportViewModel(repository) }
        }
    }
}

private fun TopicEntity.toChoice() = TopicChoice(id, uid, name, frontLabel, backLabel)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSheet(ui: ImportUi, onSelectTopic: (Long?) -> Unit, onConfirm: (ImportMode) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Text(ui.preview.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.side_arrow, ui.preview.frontLabel, ui.preview.backLabel),
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                importCounts(ui),
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (ui.preview.collidingCount == 0) {
                    stringResource(R.string.import_new_body)
                } else {
                    stringResource(R.string.import_conflict_body)
                },
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (ui.file is DeckFile.Collection) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.add_to_topic), style = MaterialTheme.typography.titleMedium)
                ui.topics.forEach { topic ->
                    TopicOption(
                        label = topic.name,
                        selected = ui.selectedTopicId == topic.id,
                        onClick = { onSelectTopic(topic.id) },
                    )
                }
                TopicOption(
                    label = stringResource(R.string.new_topic) + ": " +
                        (ui.file as DeckFile.Collection).topicName,
                    selected = ui.selectedTopicId == null,
                    onClick = { onSelectTopic(null) },
                )
            }
            ui.warning?.let { warning ->
                Text(
                    warning,
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(20.dp))
            if (ui.preview.collidingCount == 0) {
                Button(
                    onClick = { onConfirm(ImportMode.UPDATE) },
                    enabled = !ui.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.import_action))
                }
            } else {
                Button(
                    onClick = { onConfirm(ImportMode.UPDATE) },
                    enabled = !ui.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.update_existing))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onConfirm(ImportMode.COPY) },
                    enabled = !ui.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.import_copy))
                }
            }
        }
    }
}

@Composable
private fun importCounts(ui: ImportUi): String {
    val cards = pluralStringResource(R.plurals.cards, ui.preview.cardCount, ui.preview.cardCount)
    return if (ui.preview.kind == DeckKind.TOPIC) {
        pluralStringResource(R.plurals.collections, ui.preview.collectionCount, ui.preview.collectionCount) + " · " + cards
    } else {
        cards
    }
}

@Composable
private fun TopicOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
