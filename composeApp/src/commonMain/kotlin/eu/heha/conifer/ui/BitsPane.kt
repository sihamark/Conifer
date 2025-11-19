package eu.heha.conifer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.heha.conifer.model.Bit
import eu.heha.conifer.ui.theme.ConiferTheme
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.time.ExperimentalTime

@Composable
fun BitsPane(
    state: BitsPaneState = BitsPaneState()
) {
    Scaffold { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            items(state.bits) {
                BitItem(bit = it)
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
fun BitItem(bit: Bit) {
    Card(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                bit.text,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                "at ${bit.date} ${bit.time}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}


data class BitsPaneState(
    val bits: List<Bit> = emptyList()
)

@OptIn(ExperimentalTime::class)
@Preview
@Composable
private fun BitsPanePreview() {
    ConiferTheme {
        BitsPane(
            state = BitsPaneState(
                bits = listOf(
                    Bit(text = "First bit"),
                    Bit(text = "Second bit"),
                    Bit(text = "Third bit")
                )
            )
        )
    }
}