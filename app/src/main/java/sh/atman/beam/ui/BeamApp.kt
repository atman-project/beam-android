package sh.atman.beam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import sh.atman.beam.Atman

private enum class Tab(val label: String) {
    Send("Send"), Receive("Receive")
}

private sealed class BootstrapState {
    data object Starting : BootstrapState()
    data object Ready : BootstrapState()
    data class Failed(val message: String) : BootstrapState()
}

@Composable
fun BeamApp() {
    var state by remember { mutableStateOf<BootstrapState>(BootstrapState.Starting) }
    LaunchedEffect(Unit) {
        try {
            Atman.initializeIfNeeded()
            state = BootstrapState.Ready
        } catch (t: Throwable) {
            state = BootstrapState.Failed(t.message ?: t.toString())
        }
    }
    when (val s = state) {
        BootstrapState.Starting -> StartingView()
        BootstrapState.Ready -> ReadyContent()
        is BootstrapState.Failed -> FailedView(s.message)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadyContent() {
    var tab by remember { mutableStateOf(Tab.Send) }
    Scaffold(
        topBar = { TopAppBar(title = { Text(tab.label) }) }
    ) { inner ->
        Column(modifier = Modifier.padding(inner).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                Tab.entries.forEachIndexed { i, t ->
                    Tab(
                        selected = tab.ordinal == i,
                        onClick = { tab = t },
                        text = { Text(t.label) },
                    )
                }
            }
            when (tab) {
                Tab.Send -> SendScreen()
                Tab.Receive -> ReceiveScreen()
            }
        }
    }
}

@Composable
private fun StartingView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text("Starting…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FailedView(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Text("Couldn't start Beam", style = MaterialTheme.typography.titleMedium)
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
