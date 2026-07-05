package sh.atman.beam

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uniffi.atman.AtmanClient
import uniffi.atman.DownloadProgress
import uniffi.atman.DownloadProgressListener
import uniffi.atman.createAtmanClient
import java.io.File
import java.security.SecureRandom
import java.util.UUID

object Atman {
    private val initLock = Mutex()
    @Volatile private var client: AtmanClient? = null

    suspend fun initializeIfNeeded() {
        if (client != null) return
        initLock.withLock {
            if (client != null) return
            client = createAtmanClient(
                identityHex = randomHex32(),
                networkKeyHex = randomHex32(),
                customRelayUrl = null,
                // `sync` feature is off for beam; ignored on the Rust side.
                syncmanDir = "",
                syncIntervalSecs = 0UL,
            )
        }
    }

    /**
     * Atman wants real filesystem paths; content URIs from SAF / the photo
     * picker don't have one, so we stream each URI into the cache dir first.
     */
    suspend fun sendFiles(context: Context, uris: List<Uri>): String {
        val paths = stageUrisToCache(context, uris)
        return requireClient().sendFiles(paths)
    }

    suspend fun transferCount(ticket: String): Long =
        requireClient().transferCount(ticket).toLong()

    /**
     * `onProgress` fires with the cumulative bytes received so far. The
     * callback runs on the tokio thread that produced the event; consumers
     * that touch UI state must hop to the main dispatcher themselves.
     */
    suspend fun downloadFiles(
        ticket: String,
        stagingDir: File,
        onProgress: (Long) -> Unit = {},
    ): List<File> {
        stagingDir.mkdirs()
        val listener = ProgressCallbackListener(onProgress)
        return requireClient()
            .downloadFiles(ticket, stagingDir.absolutePath, listener)
            .map(::File)
    }

    private fun requireClient(): AtmanClient =
        client ?: throw IllegalStateException("Atman.initializeIfNeeded() not called yet")

    private fun stageUrisToCache(context: Context, uris: List<Uri>): List<String> {
        val outDir = File(context.cacheDir, "beam-send-staging").apply { mkdirs() }
        return uris.map { uri ->
            val name = displayName(context, uri) ?: "file-${UUID.randomUUID()}"
            val dest = File(outDir, "${UUID.randomUUID()}-$name")
            context.contentResolver.openInputStream(uri)!!.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
            dest.absolutePath
        }
    }

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private fun randomHex32(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

/**
 * Bridges the UniFFI `DownloadProgressListener` foreign trait to a plain
 * callback. Only `Bytes` events are forwarded — terminal events aren't
 * needed for the UI since the awaited `downloadFiles` call resolves on
 * completion.
 */
private class ProgressCallbackListener(
    private val onBytes: (Long) -> Unit,
) : DownloadProgressListener {
    override fun onProgress(progress: DownloadProgress) {
        if (progress is DownloadProgress.Bytes) {
            onBytes(progress.downloaded.toLong())
        }
    }
}
