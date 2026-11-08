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

    @Test
    fun targetSizeKeepsAspectAndLimitsLongestEdge() {
        assertEquals(null, BitmapScale.targetSize(1000, 800, 2048))
        assertEquals(null, BitmapScale.targetSize(0, 800, 2048))
        assertEquals(2048 to 1536, BitmapScale.targetSize(4096, 3072, 2048))
        assertEquals(1536 to 2048, BitmapScale.targetSize(3072, 4096, 2048))
        // Xiaomi 14 főkamera, 50 MP: 8192x6144
        assertEquals(2048 to 1536, BitmapScale.targetSize(8192, 6144, 2048))
        assertEquals(2048 to 1, BitmapScale.targetSize(100000, 10, 2048))
    }
}