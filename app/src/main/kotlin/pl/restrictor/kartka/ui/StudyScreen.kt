package pl.restrictor.kartka.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import pl.restrictor.kartka.R
import pl.restrictor.kartka.data.DeckRepository
import pl.restrictor.kartka.data.StudyCard
import pl.restrictor.kartka.data.StudyTarget
import pl.restrictor.kartka.domain.DueLabel
import pl.restrictor.kartka.domain.Rating
import pl.restrictor.kartka.domain.SessionQueue

data class StudyUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val title: String = "",
    val frontLabel: String = "",
    val backLabel: String = "",
    val color: String = "teal",
    val current: StudyCard? = null,
    val revealed: Boolean = false,
    val remaining: Int = 0,
    val nextDueAt: Long? = null,
    val hasAnyCards: Boolean = false,
    val busy: Boolean = false,
)

class StudyViewModel(private val repository: DeckRepository, private val target: StudyTarget) : ViewModel() {
    var state by mutableStateOf(StudyUiState())
        private set
    private var queue: List<StudyCard> = emptyList()

    init {
        load(early = false)
    }

    fun load(early: Boolean) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val loaded = repository.loadStudy(target, now, early)
            if (loaded == null) {
                state = StudyUiState(loading = false, missing = true)
                return@launch
            }
            queue = loaded.cards
            state = StudyUiState(
                loading = false,
                title = loaded.title,
                frontLabel = loaded.frontLabel,
                backLabel = loaded.backLabel,
                color = loaded.color,
                current = queue.firstOrNull(),
                remaining = queue.size,
                nextDueAt = loaded.nextDueAt,
                hasAnyCards = loaded.hasAnyCards,
            )
        }
    }

    fun reveal() {
        if (state.current != null && !state.revealed) state = state.copy(revealed = true)
    }

    fun rate(rating: Rating) {
        val card = state.current ?: return
        if (!state.revealed || state.busy) return
        state = state.copy(busy = true)
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                repository.review(card.id, rating, now)
                val ids = queue.map { it.id }
                val nextIds = if (rating == Rating.AGAIN) {
                    SessionQueue.afterAgain(ids, card.id)
                } else {
                    SessionQueue.afterPass(ids, card.id)
                }
                val byId = queue.associateBy { it.id }
                queue = nextIds.mapNotNull { byId[it] }
                val finished = queue.isEmpty()
                state = state.copy(
                    current = queue.firstOrNull(),
                    revealed = false,
                    remaining = queue.size,
                    nextDueAt = if (finished) repository.nextDue(target, now) else state.nextDueAt,
                    busy = false,
                )
            } catch (_: Exception) {
                state = state.copy(busy = false)
            }
        }
    }

    companion object {
        fun factory(repository: DeckRepository, target: StudyTarget): ViewModelProvider.Factory = viewModelFactory {
            initializer { StudyViewModel(repository, target) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(repository: DeckRepository, target: StudyTarget, onBack: () -> Unit) {
    val viewModel = viewModel<StudyViewModel>(
        key = target.key(),
        factory = StudyViewModel.factory(repository, target),
    )
    val state = viewModel.state
    val haptic = LocalHapticFeedback.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.title.ifBlank { stringResource(R.string.study) })
                        if (!state.loading && state.current != null) {
                            Text(
                                pluralStringResource(R.plurals.left, state.remaining, state.remaining),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        bottomBar = {
            when {
                state.current != null && state.revealed -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .navigationBarsPadding(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RatingButton(stringResource(R.string.again), Rating.AGAIN, state.busy) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.rate(Rating.AGAIN)
                        }
                        RatingButton(stringResource(R.string.good), Rating.GOOD, state.busy) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.rate(Rating.GOOD)
                        }
                        RatingButton(stringResource(R.string.easy), Rating.EASY, state.busy) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.rate(Rating.EASY)
                        }
                    }
                }
                state.current != null -> {
                    Button(
                        onClick = viewModel::reveal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .navigationBarsPadding(),
                    ) {
                        Text(stringResource(R.string.show_answer))
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> Unit
            state.missing -> EmptyMessage(stringResource(R.string.missing), Modifier.padding(padding))
            state.current == null && !state.hasAnyCards -> {
                EmptyMessage(stringResource(R.string.no_cards), Modifier.padding(padding))
            }
            state.current == null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.nothing_due), style = MaterialTheme.typography.headlineSmall)
                    state.nextDueAt?.let { due ->
                        Text(
                            stringResource(R.string.next_prefix, dueLabel(DueLabel.of(due, System.currentTimeMillis()))),
                            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.nextDueAt != null) {
                        Button(onClick = { viewModel.load(early = true) }) {
                            Text(stringResource(R.string.review_early))
                        }
                    }
                }
            }
            else -> {
                val card = state.current
                val face = if (state.revealed) card.back else card.front
                val label = if (state.revealed) state.backLabel else state.frontLabel
                Card(
                    modifier = Modifier
                        .padding(padding)
                        .padding(20.dp)
                        .fillMaxSize()
                        .clickable(onClick = viewModel::reveal)
                        .semantics {
                            stateDescription = if (state.revealed) "Answer shown" else "Question shown"
                        },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    AnimatedContent(
                        targetState = state.revealed to card.id,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "card-face",
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .heightIn(min = 320.dp)
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(label, style = MaterialTheme.typography.labelLarge, color = topicColor(state.color, androidx.compose.foundation.isSystemInDarkTheme()))
                            Text(
                                face,
                                modifier = Modifier.padding(top = 12.dp),
                                style = if (face.length > 160) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                            )
                            if (state.revealed && !card.note.isNullOrBlank()) {
                                Text(
                                    stringResource(R.string.note),
                                    modifier = Modifier.padding(top = 24.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(card.note, modifier = Modifier.padding(top = 4.dp))
                            }
                            if (!state.revealed) {
                                Text(
                                    stringResource(R.string.tap_to_reveal),
                                    modifier = Modifier.padding(top = 28.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.RatingButton(label: String, rating: Rating, busy: Boolean, onClick: () -> Unit) {
    val colors = when (rating) {
        Rating.AGAIN -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        )
        Rating.GOOD -> ButtonDefaults.buttonColors()
        Rating.EASY -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
        )
    }
    Button(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
        colors = colors,
    ) {
        Text(label, textAlign = TextAlign.Center)
    }
}

@Composable
private fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    }
}

private fun StudyTarget.key(): String = when (this) {
    is StudyTarget.Topic -> "study-topic-$id"
    is StudyTarget.Collection -> "study-collection-$id"
}
