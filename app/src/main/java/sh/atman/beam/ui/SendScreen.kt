package sh.atman.beam.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Share
import android.content.Intent
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.atman.beam.Atman

@Composable
fun SendScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ticket by remember { mutableStateOf<String?>(null) }
    var pickedLabel by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var receivedCount by remember { mutableLongStateOf(0L) }

    fun startShare(label: String, uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        pickedLabel = label
        errorMessage = null
        working = true
        scope.launch {
            try {
                ticket = Atman.sendFiles(context, uris)
            } catch (t: Throwable) {
                errorMessage = t.localizedMessage ?: t.toString()
            } finally {
                working = false
            }
        }
    }

    val pickFiles = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        startShare(if (uris.size == 1) uris[0].lastPathSegment ?: "1 file" else "${uris.size} files", uris)
    }

    val pickPhotos = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        startShare(if (uris.size == 1) "1 photo" else "${uris.size} photos", uris)
    }

    // Poll receiver count while a ticket is on screen.
    LaunchedEffect(ticket) {
        val t = ticket ?: return@LaunchedEffect
        receivedCount = 0
        while (true) {
            receivedCount = Atman.transferCount(t)
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(horizontal = 20.dp, vertical = 24.dp)),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        val activeTicket = ticket
        if (activeTicket == null) {
            Button(
                onClick = { pickFiles.launch(arrayOf("*/*")) },
                enabled = !working,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Outlined.Folder, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Pick files")
            }
            Button(
                onClick = {
                    pickPhotos.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageAndVideo
                        )
                    )
                },
                enabled = !working,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Outlined.Photo, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Pick photos")
            }
            if (working) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator()
                    Text(
                        "Hashing ${pickedLabel ?: ""}…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            TicketBlock(
                ticket = activeTicket,
                pickedLabel = pickedLabel,
                receivedCount = receivedCount,
                onReset = {
                    ticket = null
                    pickedLabel = null
                    errorMessage = null
                    receivedCount = 0
                },
            )
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
}

@Composable
private fun TicketBlock(
    ticket: String,
    pickedLabel: String?,
    receivedCount: Long,
    onReset: () -> Unit,
) {
    val context = LocalContext.current
    val qr = remember(ticket) { generateQrBitmap(ticket, 720) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (qr != null) {
            Image(
                bitmap = qr.asImageBitmap(),
                contentDescription = "Ticket QR code",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(androidx.compose.ui.graphics.Color.White)
                    .padding(8.dp),
            )
        }
        if (!pickedLabel.isNullOrBlank()) {
            Text(
                pickedLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val friendWord = if (receivedCount >= 2L) "friends" else "friend"
        Text(
            "Received by $receivedCount $friendWord",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, ticket)
                }
                context.startActivity(Intent.createChooser(send, null))
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(Icons.Outlined.Share, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Share ticket")
        }
        OutlinedButton(onClick = onReset) { Text("Pick another file") }
        Text(
            "Keep this screen open until your friend has finished receiving.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun generateQrBitmap(content: String, size: Int): Bitmap? = runCatching {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 1,
    )
    val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val w = matrix.width
    val h = matrix.height
    val pixels = IntArray(w * h)
    for (y in 0 until h) {
        val off = y * w
        for (x in 0 until w) {
            pixels[off + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
        }
    }
    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, w, 0, 0, w, h)
    }
}.getOrNull()
