package sh.atman.beam.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import android.text.format.Formatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.atman.beam.Atman
import sh.atman.beam.SaveOutcome
import sh.atman.beam.Saver
import java.io.File

@Composable
fun ReceiveScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var manualTicket by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var bytesReceived by remember { mutableStateOf(0L) }
    var transferStart by remember { mutableStateOf(0L) }
    var receiveJob by remember { mutableStateOf<Job?>(null) }
    var result by remember { mutableStateOf<SaveOutcome?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun receive(ticket: String) {
        val trimmed = ticket.trim()
        if (trimmed.isEmpty()) return
        errorMessage = null
        result = null
        bytesReceived = 0
        transferStart = System.currentTimeMillis()
        working = true
        receiveJob = scope.launch {
            try {
                val outcome = withContext(Dispatchers.IO) {
                    val staging = File(context.cacheDir, "beam-staging").apply { mkdirs() }
                    val staged = Atman.downloadFiles(trimmed, staging) { bytes ->
                        // Callback runs on a tokio thread; hop to Main.
                        scope.launch { bytesReceived = bytes }
                    }
                    Saver.save(context, staged)
                }
                if (outcome.photos == 0 && outcome.files == 0 && outcome.errors.isNotEmpty()) {
                    errorMessage = outcome.errors.first()
                } else {
                    result = outcome
                    if (outcome.errors.isNotEmpty()) {
                        errorMessage = "Some files failed: ${outcome.errors.first()}"
                    }
                    manualTicket = ""
                }
            } catch (_: CancellationException) {
                // User cancelled — leave UI clean.
            } catch (t: Throwable) {
                errorMessage = t.localizedMessage ?: t.toString()
            } finally {
                working = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(horizontal = 20.dp, vertical = 24.dp)),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Button(
            onClick = { scanning = true },
            enabled = !working,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Scan QR")
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Or paste a ticket", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = manualTicket,
                onValueChange = { manualTicket = it },
                placeholder = { Text("atman-blob1…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { receive(manualTicket) }),
            )
            OutlinedButton(
                onClick = { pasteAndReceive(context) { receive(it) } },
                enabled = !working,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Outlined.ContentPaste, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Paste & Receive")
            }
        }

        val msg = errorMessage
        if (msg != null) {
            Text(
                "⚠︎ $msg",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    if (scanning) {
        QrScannerScreen(
            onResult = { scanned ->
                scanning = false
                receive(scanned)
            },
            onClose = { scanning = false },
        )
    }

    if (working) {
        TransferDialog(
            bytesReceived = bytesReceived,
            transferStart = transferStart,
            onCancel = { receiveJob?.cancel() },
        )
    }

    val outcome = result
    if (outcome != null) {
        val asPhotos = outcome.photos >= outcome.files
        AlertDialog(
            onDismissRequest = { result = null },
            title = { Text(resultTitle(outcome, asPhotos)) },
            text = null,
            confirmButton = {
                TextButton(onClick = {
                    result = null
                    if (asPhotos) Saver.openPhotos(context, outcome.lastPhotoUri)
                    else Saver.openFiles(context, outcome.lastFileUri)
                }) { Text(if (asPhotos) "Open Photos" else "Open Files") }
            },
            dismissButton = {
                TextButton(onClick = { result = null }) { Text("Done") }
            },
        )
    }
}

@Composable
private fun TransferDialog(
    bytesReceived: Long,
    transferStart: Long,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val label = remember(bytesReceived, transferStart) {
        val bytes = Formatter.formatShortFileSize(context, bytesReceived)
        val elapsed = System.currentTimeMillis() - transferStart
        if (bytesReceived == 0L || elapsed < 500) {
            bytes
        } else {
            val rate = Formatter.formatShortFileSize(context, bytesReceived * 1000 / elapsed)
            "$bytes ($rate/s)"
        }
    }
    AlertDialog(
        onDismissRequest = {},
        title = null,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator()
                Text(
                    "Receiving",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        },
    )
}

private fun resultTitle(o: SaveOutcome, asPhotos: Boolean): String {
    val total = o.photos + o.files
    val label = if (asPhotos) "photo" else "file"
    val plural = if (total == 1) "" else "s"
    return "Received $total $label$plural"
}

private fun pasteAndReceive(context: Context, onTicket: (String) -> Unit) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as
        android.content.ClipboardManager
    val text = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty().trim()
    if (text.isNotEmpty()) onTicket(text)
}
