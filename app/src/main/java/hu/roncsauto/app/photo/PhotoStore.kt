package hu.roncsauto.app.photo

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import hu.roncsauto.app.plate.HungarianPlate
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Fotó mentése a galériába (Pictures/RoncsAuto). */
object PhotoStore {

    private val stampFormat = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")

    fun fileName(plate: String?, when_: LocalDateTime): String {
        val tag = plate
            ?.let { HungarianPlate.findBest(it)?.compact }
            ?.takeIf { it.isNotBlank() }
            ?: "RONCS"
        return "${tag}_${stampFormat.format(when_)}.jpg"
    }

    /**
     * A [source] JPEG-et a képek közé másolja. API 29+ engedély nélkül,
     * régebben WRITE_EXTERNAL_STORAGE kell.
     */
    fun saveJpeg(context: Context, source: File, plate: String?, when_: LocalDateTime = LocalDateTime.now()): Uri {
        if (!source.exists()) throw IOException("missing photo")
        val name = fileName(plate, when_)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/RoncsAuto")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: throw IOException("insert")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException("stream")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }
}
