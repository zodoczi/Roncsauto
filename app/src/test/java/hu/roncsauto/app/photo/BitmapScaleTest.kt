package hu.roncsauto.app.photo

import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapScaleTest {
    @Test
    fun sampleSizeKeepsSmallImagesAndShrinksLargeOnes() {
        assertEquals(1, BitmapScale.sampleSize(0, 100, 2048))
        assertEquals(1, BitmapScale.sampleSize(100, 100, 2048))
        assertEquals(2, BitmapScale.sampleSize(4096, 3000, 2048))
        assertEquals(2, BitmapScale.sampleSize(5000, 4000, 2048))
        assertEquals(4, BitmapScale.sampleSize(10000, 8000, 2048))
    }
}
