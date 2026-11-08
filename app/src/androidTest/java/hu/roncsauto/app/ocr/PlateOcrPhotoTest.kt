package hu.roncsauto.app.ocr

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import hu.roncsauto.app.photo.PhotoDecoder
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Valódi ML Kit futás egy helyi tesztfotón (androidTest/assets/twingo.jpg).
 * A fotó valódi rendszámot mutat, ezért nincs a repóban; hiányában a teszt kimarad.
 */
@RunWith(AndroidJUnit4::class)
class PlateOcrPhotoTest {
    @Test
    fun twingoPhotoGivesIux868() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        assumeTrue(assets.list("")?.contains("twingo.jpg") == true)
        val full = assets.open("twingo.jpg").use { BitmapFactory.decodeStream(it) }
        Log.i("RoncsAuto", "photo ${full.width}x${full.height}")
        // Ugyanaz a korlát, mint az appban.
        val bitmap = PhotoDecoder.scaleCopy(full, PlateOcr.OCR_EDGE)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val match = PlateOcr(recognizer).find(bitmap) { false }
        Log.i("RoncsAuto", "RESULT ${match?.canonical} score=${match?.score}")
        assertEquals("IUX-868", match?.canonical)
    }
}