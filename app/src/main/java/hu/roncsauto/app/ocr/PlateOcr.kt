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

    fun find(input: Bitmap, cancelled: () -> Boolean): HungarianPlate.Match? {
        refineBudget = REFINE_BUDGET
        val (top, height) = PlateSearch.chromeBands(input.height)
        val bitmap = Bitmap.createBitmap(input, 0, top, input.width, height)
        try {
            return findIn(bitmap, cancelled)
        } finally {
            if (bitmap !== input) bitmap.recycle()
        }
    }

    private fun findIn(bitmap: Bitmap, cancelled: () -> Boolean): HungarianPlate.Match? {
        val found = mutableListOf<HungarianPlate.Match>()
        fun strong() = PlateSearch.confident(found)

        pass(bitmap, "full", found)
        if (strong()) return PlateSearch.pick(found)

        if (tiles(bitmap, "0", intArrayOf(2, 3), found, cancelled)) return PlateSearch.pick(found)

        for (warp in PlateSearch.WARPS) {
            if (cancelled()) return null
            val matrix = Matrix().apply {
                if (warp.skewY != 0f) postSkew(0f, warp.skewY)
                if (warp.degrees != 0f) postRotate(warp.degrees)
                // A torzított kép hosszabb oldala se legyen nagyobb a forrásnál.
                val w = bitmap.width.toFloat()
                val h = bitmap.height.toFloat()
                val longest = maxOf(w, h + kotlin.math.abs(warp.skewY) * w)
                val shrink = maxOf(w, h) / longest
                if (shrink < 1f) postScale(shrink, shrink)
            }
            val warped = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            try {
                pass(warped, warp.label, found)
                if (strong()) break
                if (tiles(warped, warp.label, intArrayOf(2, 3), found, cancelled)) break
            } finally {
                warped.recycle()
            }
        }
        return PlateSearch.pick(found)
    }

    /** Nagyított átfedő kivágások; true, ha erős találat lett. */
    private fun tiles(
        source: Bitmap,
        label: String,
        grids: IntArray,
        found: MutableList<HungarianPlate.Match>,
        cancelled: () -> Boolean
    ): Boolean {
        for (region in PlateSearch.regions(source.width, source.height, grids)) {
            if (cancelled()) return false
            val crop = Bitmap.createBitmap(source, region.left, region.top, region.width, region.height)
            val factor = PlateSearch.upscale(region.width, region.height, MAX_EDGE)
            val scaled = if (factor > 1f) {
                Bitmap.createScaledBitmap(crop, (region.width * factor).toInt(), (region.height * factor).toInt(), true)
            } else crop
            pass(scaled, "$label tile ${region.left},${region.top} x$factor", found)
            if (scaled !== crop) scaled.recycle()
            crop.recycle()
        }
        // Egy torzítás összes kivágását végigolvassuk, csak utána döntünk:
        // egyetlen kivágás félreolvasása (pl. "FIU-868") ne zárja le a keresést.
        return PlateSearch.confident(found)
    }
    private var refineBudget = 0

    private fun pass(
        bitmap: Bitmap,
        label: String,
        found: MutableList<HungarianPlate.Match>,
        refine: Boolean = true
    ) {
        val result = try {
            Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)), 60, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "OCR $label failed", e)
            return
        }
        val text = result.text
        val matches = HungarianPlate.candidates(text)
        Log.d(TAG, "OCR $label: '${text.replace('\n', '|')}' -> ${matches.joinToString { "${it.canonical} (${it.score})" }}")
        found += matches
        if (!refine) return
        // Második lépés: a rendszámszerű szövegblokkot szorosan kivágjuk, felnagyítjuk és
        // kis nyírás-eltérésekkel újraolvassuk. A tesztfotón a nagy képből "FIU-868" /
        // "EIUK 869" jött, a szoros kivágásból viszont "IUX868".
        val boxes = result.textBlocks
            .filter { PlateSearch.plateLike(it.text) }
            .mapNotNull { it.boundingBox }
            .take(2)
        for (box in boxes) {
            if (refineBudget <= 0) return
            val region = PlateSearch.expand(box.left, box.top, box.width(), box.height(), bitmap.width, bitmap.height)
                ?: continue
            val crop = Bitmap.createBitmap(bitmap, region.left, region.top, region.width, region.height)
            val factor = PlateSearch.refineScale(region.height)
            for (extra in PlateSearch.REFINE_SKEWS) {
                if (refineBudget-- <= 0) break
                val matrix = Matrix().apply {
                    if (extra != 0f) postSkew(0f, extra)
                    postScale(factor, factor)
                }
                val refined = Bitmap.createBitmap(crop, 0, 0, crop.width, crop.height, matrix, true)
                pass(refined, "$label refine skew+$extra x$factor", found, refine = false)
                refined.recycle()
            }
            crop.recycle()
        }
    }
    companion object {
        private const val TAG = "RoncsAuto"
        private const val MAX_EDGE = 2048
        private const val REFINE_BUDGET = 18
        /** OCR-hez nagyobb felbontás kell, mint a mentett fotóhoz (kis, ferde tábla). */
        const val OCR_EDGE = 3072
    }
}