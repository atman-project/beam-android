package sh.atman.beam

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/**
 * Where a staged file ends up after we hand it to the system. Drives the
 * post-receive alert: a non-zero `photos` shows "Open Photos", non-zero
 * `files` shows "Open Files", both shown when both > 0. The two URIs
 * deep-link "Open Photos" / "Open Files" straight to the latest item.
 */
data class SaveOutcome(
    val photos: Int,
    val files: Int,
    val errors: List<String>,
    val lastPhotoUri: Uri? = null,
    val lastFileUri: Uri? = null,
)

object Saver {

    /**
     * Write each staged file to its final user-visible location: images and
     * videos go into the MediaStore (visible in Gallery), everything else
     * goes into the public Downloads collection (visible in the Files app).
     */
    fun save(context: Context, stagedFiles: List<File>): SaveOutcome {
        var photos = 0
        var files = 0
        var lastPhotoUri: Uri? = null
        var lastFileUri: Uri? = null
        val errors = mutableListOf<String>()
        for (f in stagedFiles) {
            val mime = mimeOf(f) ?: "application/octet-stream"
            try {
                if (mime.startsWith("image/") || mime.startsWith("video/")) {
                    lastPhotoUri = saveToGallery(context, f, mime)
                    photos++
                } else {
                    lastFileUri = saveToDownloads(context, f, mime)
                    files++
                }
            } catch (e: Exception) {
                errors += "${f.name}: ${e.message ?: "unknown error"}"
            }
        }
        return SaveOutcome(photos, files, errors, lastPhotoUri, lastFileUri)
    }

    private fun mimeOf(file: File): String? =
        MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase())

    /**
     * Write into MediaStore.Images/Videos under Pictures/Beam (or Movies/Beam).
     * The system surfaces the result in the user's default Gallery.
     */
    private fun saveToGallery(context: Context, src: File, mime: String): Uri {
        val isVideo = mime.startsWith("video/")
        val collection = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else ->
                if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, src.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val rel = if (isVideo) "${Environment.DIRECTORY_MOVIES}/Beam"
                else "${Environment.DIRECTORY_PICTURES}/Beam"
                put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(collection, values)
            ?: throw IOException("MediaStore insert returned null")
        context.contentResolver.openOutputStream(uri)?.use { out ->
            src.inputStream().use { it.copyTo(out) }
        } ?: throw IOException("openOutputStream returned null")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        }
        return uri
    }

    /**
     * Write into the public Downloads collection (Downloads/Beam/<name>).
     * Visible from the Files app's Downloads section and any third-party
     * file manager.
     */
    private fun saveToDownloads(context: Context, src: File, mime: String): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, src.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}/Beam"
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver
                .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Downloads insert returned null")
            context.contentResolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            } ?: throw IOException("openOutputStream returned null")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            return uri
        }
        // Pre-Q: WRITE_EXTERNAL_STORAGE + direct file write. No content URI.
        val dir = File(
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            ),
            "Beam"
        ).apply { mkdirs() }
        val dest = uniqueFile(dir, src.name)
        src.inputStream().use { input ->
            dest.outputStream().use { input.copyTo(it) }
        }
        return null
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        var i = 1
        while (candidate.exists()) {
            val base = name.substringBeforeLast('.', name)
            val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
            candidate = File(dir, "$base ($i)$ext")
            i++
        }
        return candidate
    }

    /**
     * Opens the gallery directly on the photo we just saved. From there the
     * user can swipe through neighbouring (i.e. recent) items. Without a
     * URI we fall back to the gallery's default listing.
     */
    fun openPhotos(context: Context, lastPhotoUri: Uri? = null) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            if (lastPhotoUri != null) {
                setDataAndType(lastPhotoUri, "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    /**
     * Opens the system file viewer at the file we just saved. Falls back to
     * the Downloads root if no URI is available.
     */
    fun openFiles(context: Context, lastFileUri: Uri? = null) {
        if (lastFileUri != null) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(lastFileUri, context.contentResolver.getType(lastFileUri) ?: "*/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return
            }
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("content://com.android.externalstorage.documents/root/downloads"), "*/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            return
        }
        // Last-resort fallback: the documents picker, which at least surfaces
        // "Recent" with newest writes.
        val fallback = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(fallback) }
    }

    // Kept for future use (e.g. share-sheet on the receive screen).
    @Suppress("unused")
    fun fileProviderUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
