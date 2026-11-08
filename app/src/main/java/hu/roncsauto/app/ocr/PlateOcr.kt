package hu.roncsauto.app.ocr

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import hu.roncsauto.app.plate.HungarianPlate
import java.util.concurrent.TimeUnit

/**
 * Fotóról több menetben keres rendszámot: teljes kép, nagyított átfedő kivágások,
 * majd elforgatott változatok. Háttérszálon kell hívni (Tasks.await).
 */
class PlateOcr(private val recognizer: TextRecognizer) {

    fun find(bitmap: Bitmap, cancelled: () -> Boolean): HungarianPlate.Match? {
        val found = mutableListOf<HungarianPlate.Match>()
        fun strong() = found.any { it.score >= PlateSearch.STRONG_SCORE }

        pass(bitmap, "full", found)
        if (strong()) return PlateSearch.pick(found)

        for (region in PlateSearch.regions(bitmap.width, bitmap.height)) {
            if (cancelled()) return null
            val crop = Bitmap.createBitmap(bitmap, region.left, region.top, region.width, region.height)
            val factor = PlateSearch.upscale(region.width, region.height, MAX_EDGE)
            val scaled = if (factor > 1f) {
                Bitmap.createScaledBitmap(crop, (region.width * factor).toInt(), (region.height * factor).toInt(), true)
            } else crop
            pass(scaled, "tile ${region.left},${region.top} x$factor", found)
            if (scaled !== crop) scaled.recycle()
            crop.recycle()
            if (strong()) return PlateSearch.pick(found)
        }

        for (degrees in floatArrayOf(-20f, 20f)) {
            if (cancelled()) return null
            val matrix = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            pass(rotated, "rot $degrees", found)
            rotated.recycle()
            if (strong()) break
        }
        return PlateSearch.pick(found)
    }

    private fun pass(bitmap: Bitmap, label: String, found: MutableList<HungarianPlate.Match>) {
        val text = try {
            Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)), 20, TimeUnit.SECONDS).text
        } catch (e: Exception) {
            Log.w(TAG, "OCR $label failed", e)
            return
        }
        val match = HungarianPlate.findBest(text)
        Log.d(TAG, "OCR $label: '${text.replace('\n', '|')}' -> ${match?.canonical} (${match?.score})")
        if (match != null) found += match
    }

    companion object {
        private const val TAG = "RoncsAuto"
        private const val MAX_EDGE = 2048
    }
}