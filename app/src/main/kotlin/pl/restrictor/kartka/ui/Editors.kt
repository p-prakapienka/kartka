package pl.restrictor.kartka.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.restrictor.kartka.R
import pl.restrictor.kartka.domain.DeckCodec

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicEditorSheet(
    title: String,
    initialName: String,
    initialFront: String,
    initialBack: String,
    initialColor: String,
    onDismiss: () -> Unit,
    onSave: (name: String, front: String, back: String, color: String) -> Unit,
) {
    EditorSheet(onDismiss) {
        var name by remember { mutableStateOf(initialName) }
        var front by remember { mutableStateOf(initialFront) }
        var back by remember { mutableStateOf(initialBack) }
        var color by remember { mutableStateOf(initialColor) }
        var tried by remember { mutableStateOf(false) }
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        NameField(name, { name = it }, tried, DeckCodec.MAX_NAME, stringResource(R.string.name))
        Spacer(Modifier.height(12.dp))
        NameField(front, { front = it }, tried, DeckCodec.MAX_LABEL, stringResource(R.string.front_label))
        Spacer(Modifier.height(12.dp))
        NameField(back, { back = it }, tried, DeckCodec.MAX_LABEL, stringResource(R.string.back_label))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.color), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        ColorDots(color) { color = it }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                tried = true
                if (validName(name, DeckCodec.MAX_NAME) && validName(front, DeckCodec.MAX_LABEL) && validName(back, DeckCodec.MAX_LABEL)) {
                    onSave(name, front, back, color)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.save))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionEditorSheet(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    EditorSheet(onDismiss) {
        var name by remember { mutableStateOf(initialName) }
        var tried by remember { mutableStateOf(false) }
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        NameField(name, { name = it }, tried, DeckCodec.MAX_NAME, stringResource(R.string.name))
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                tried = true
                if (validName(name, DeckCodec.MAX_NAME)) onSave(name)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.save))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardEditorSheet(
    title: String,
    frontLabel: String,
    backLabel: String,
    initialFront: String,
    initialBack: String,
    initialNote: String,
    onDismiss: () -> Unit,
    onSave: (front: String, back: String, note: String) -> Unit,
) {
    EditorSheet(onDismiss) {
        var front by remember { mutableStateOf(initialFront) }
        var back by remember { mutableStateOf(initialBack) }
        var note by remember { mutableStateOf(initialNote) }
        var tried by remember { mutableStateOf(false) }
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        BodyField(front, { front = it }, tried, frontLabel)
        Spacer(Modifier.height(12.dp))
        BodyField(back, { back = it }, tried, backLabel)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.note)) },
            supportingText = {
                when {
                    note.trim().length > DeckCodec.MAX_TEXT -> Text(stringResource(R.string.too_long))
                    else -> Text(stringResource(R.string.note_optional))
                }
            },
            isError = note.trim().length > DeckCodec.MAX_TEXT,
            minLines = 2,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                tried = true
                if (validBody(front) && validBody(back) && note.trim().length <= DeckCodec.MAX_TEXT) {
                    onSave(front, back, note)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.save))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            content()
        }
    }
}

@Composable
private fun NameField(value: String, onValue: (String) -> Unit, tried: Boolean, max: Int, label: String) {
    val blank = value.isBlank()
    val tooLong = value.trim().length > max
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        isError = tried && (blank || tooLong),
        supportingText = {
            when {
                tried && blank -> Text(stringResource(R.string.required))
                tooLong -> Text(stringResource(R.string.too_long))
                else -> Unit
            }
        },
    )
}

@Composable
private fun BodyField(value: String, onValue: (String) -> Unit, tried: Boolean, label: String) {
    val blank = value.isBlank()
    val tooLong = value.trim().length > DeckCodec.MAX_TEXT
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        minLines = 3,
        isError = tried && (blank || tooLong),
        supportingText = {
            when {
                tried && blank -> Text(stringResource(R.string.required))
                tooLong -> Text(stringResource(R.string.too_long))
                else -> Unit
            }
        },
    )
}

private fun validName(value: String, max: Int): Boolean = value.isNotBlank() && value.trim().length <= max

private fun validBody(value: String): Boolean = value.isNotBlank() && value.trim().length <= DeckCodec.MAX_TEXT
