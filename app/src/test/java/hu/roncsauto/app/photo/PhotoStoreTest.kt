package hu.roncsauto.app.photo

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class PhotoStoreTest {
    @Test
    fun fileNameUsesCompactPlateAndTimestamp() {
        val when_ = LocalDateTime.of(2026, 11, 9, 9, 30)
        assertEquals("IUX868_20261109_0930.jpg", PhotoStore.fileName("IUX-868", when_))
        assertEquals("AABC123_20261109_0930.jpg", PhotoStore.fileName("AA BC-123", when_))
        assertEquals("RONCS_20261109_0930.jpg", PhotoStore.fileName(null, when_))
    }
}
