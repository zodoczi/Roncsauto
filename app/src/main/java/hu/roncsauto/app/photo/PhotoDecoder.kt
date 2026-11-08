package hu.roncsauto.app.photo

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.BufferedInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

object PhotoDecoder {

    /**
     * Megnyitja a kiválasztott képet. Hibánál kivételt dob (soha nem ad vissza null-t),
     * hogy a hívó a valódi okot naplózhassa.
     */
    fun decode(resolver: ContentResolver, uri: Uri, maxEdge: Int): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                return decodeWithImageDecoder(resolver, uri, maxEdge)
            } catch (e: OutOfMemoryError) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "ImageDecoder failed for $uri, falling back to BitmapFactory", e)
            }
        }
        return decodeWithBitmapFactory(resolver, uri, maxEdge)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(resolver: ContentResolver, uri: Uri, maxEdge: Int): Bitmap {
        val source = ImageDecoder.createSource(resolver, uri)
        // Az ImageDecoder a HEIC/HEIF képeket és az EXIF-forgatást is kezeli.
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            val target = BitmapScale.targetSize(info.size.width, info.size.height, maxEdge)
            if (target != null) decoder.setTargetSize(target.first, target.second)
        }
        return limitEdge(bitmap, maxEdge)
    }

    private fun decodeWithBitmapFactory(resolver: ContentResolver, uri: Uri, maxEdge: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // Méretlekérdezésnél a decodeStream mindig null-t ad, ezért az eredményét nem szabad vizsgálni.
        val opened = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(BufferedInputStream(stream), null, bounds)
            true
        } ?: throw FileNotFoundException("openInputStream returned null: $uri")
        check(opened)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("Unsupported image format (mime=${bounds.outMimeType}): $uri")
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = BitmapScale.sampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(BufferedInputStream(stream), null, options)
        } ?: throw IOException("BitmapFactory could not decode: $uri")
        val orientation = try {
            resolver.openInputStream(uri)?.use { stream ->
                ExifInterface(BufferedInputStream(stream)).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            Log.w(TAG, "EXIF read failed for $uri", e)
            ExifInterface.ORIENTATION_NORMAL
        }
        return limitEdge(rotate(decoded, orientation), maxEdge)
    }

    private const val TAG = "RoncsAuto"

    fun decodeFile(file: File, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = BitmapScale.sampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        return limitEdge(decoded, maxEdge)
    }

    fun scaleCopy(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxEdge || longest <= 0) return bitmap
        val scale = maxEdge.toFloat() / longest.toFloat()
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    fun saveJpeg(bitmap: Bitmap, file: File, quality: Int) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                throw IllegalStateException("jpeg")
            }
        }
    }

    private fun limitEdge(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxEdge || longest <= 0) return bitmap
        val scaled = scaleCopy(bitmap, maxEdge)
        if (scaled != bitmap) bitmap.recycle()
        return scaled
    }

    private fun rotate(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }
}
