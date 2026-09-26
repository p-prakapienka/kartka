package pl.restrictor.kartka.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pl.restrictor.kartka.R
import pl.restrictor.kartka.data.RepeatSettings
import pl.restrictor.kartka.domain.DelayAmounts
import pl.restrictor.kartka.domain.DelayUnit
import pl.restrictor.kartka.domain.RepeatDelays

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: RepeatSettings, onBack: () -> Unit) {
    val initial = settings.get()
    var badCount by rememberSaveable { mutableStateOf(amountState(initial.badMs).countText) }
    var badUnit by rememberSaveable { mutableStateOf(amountState(initial.badMs).unit.name) }
    var mediumCount by rememberSaveable { mutableStateOf(amountState(initial.mediumMs).countText) }
    var mediumUnit by rememberSaveable { mutableStateOf(amountState(initial.mediumMs).unit.name) }
    var goodCount by rememberSaveable { mutableStateOf(amountState(initial.goodMs).countText) }
    var goodUnit by rememberSaveable { mutableStateOf(amountState(initial.goodMs).unit.name) }
    val bad = AmountState(badCount, DelayUnit.valueOf(badUnit))
    val medium = AmountState(mediumCount, DelayUnit.valueOf(mediumUnit))
    val good = AmountState(goodCount, DelayUnit.valueOf(goodUnit))
    val valid = bad.isValid() && medium.isValid() && good.isValid()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.repeat_times)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                stringResource(R.string.repeat_times_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DelayEditor(stringResource(R.string.bad), bad, { badCount = it }, { badUnit = it.name })
            DelayEditor(stringResource(R.string.medium), medium, { mediumCount = it }, { mediumUnit = it.name })
            DelayEditor(stringResource(R.string.good), good, { goodCount = it }, { goodUnit = it.name })
            if (!valid) {
                Text(
                    stringResource(R.string.delay_range),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = {
                    settings.set(
                        RepeatDelays(
                            badMs = bad.toMillis(),
                            mediumMs = medium.toMillis(),
                            goodMs = good.toMillis(),
                        ),
                    )
                    onBack()
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

@Composable
private fun DelayEditor(
    label: String,
    value: AmountState,
    onCount: (String) -> Unit,
    onUnit: (DelayUnit) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = value.countText,
                onValueChange = { next ->
                    if (next.length <= 6 && next.all { it.isDigit() }) onCount(next)
                },
                modifier = Modifier.width(112.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DelayUnit.entries.forEach { unit ->
                FilterChip(
                    selected = value.unit == unit,
                    onClick = { onUnit(unit) },
                    label = { Text(unitLabel(unit)) },
                )
            }
        }
    }
}

@Composable
private fun unitLabel(unit: DelayUnit): String = when (unit) {
    DelayUnit.MINUTES -> stringResource(R.string.minutes)
    DelayUnit.HOURS -> stringResource(R.string.hours)
    DelayUnit.DAYS -> stringResource(R.string.days)
}

private data class AmountState(val countText: String, val unit: DelayUnit) {
    fun isValid(): Boolean = countText.toIntOrNull()?.let { DelayAmounts.isAllowed(it, unit) } == true

    fun toMillis(): Long = DelayAmounts.toMillis(countText.toInt(), unit)
}

private fun amountState(ms: Long): AmountState {
    val amount = DelayAmounts.fromMillis(ms)
    return AmountState(amount.count.toString(), amount.unit)
}
