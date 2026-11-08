package hu.roncsauto.app.ocr

import hu.roncsauto.app.plate.HungarianPlate

/** Képkivágások és szavazás a több menetes rendszámkereséshez (Android-független). */
object PlateSearch {

    data class Region(val left: Int, val top: Int, val width: Int, val height: Int)

    /** Pontos, elkülönített rendszámnál nincs szükség további menetekre. */
    const val STRONG_SCORE = 35

    /**
     * 2×2 és 3×3 átfedő rács. A kicsi rendszám így nagyítva kerül az OCR elé.
     */
    fun regions(width: Int, height: Int): List<Region> {
        if (width <= 0 || height <= 0) return emptyList()
        val out = mutableListOf<Region>()
        for (n in intArrayOf(2, 3)) {
            val tileW = width * 2 / (n + 1)
            val tileH = height * 2 / (n + 1)
            for (row in 0 until n) for (col in 0 until n) {
                out += Region((width - tileW) * col / (n - 1), (height - tileH) * row / (n - 1), tileW, tileH)
            }
        }
        return out
    }

    /** Nagyítás a kivágáshoz: legfeljebb 2×, a hosszabb oldal legfeljebb [maxEdge]. */
    fun upscale(width: Int, height: Int, maxEdge: Int): Float {
        val longest = maxOf(width, height)
        if (longest <= 0) return 1f
        return minOf(2f, maxEdge.toFloat() / longest).coerceAtLeast(1f)
    }

    /** A több menetben leggyakrabban, majd legjobb pontszámmal olvasott rendszám. */
    fun pick(found: List<HungarianPlate.Match>): HungarianPlate.Match? =
        found.groupBy { it.compact }.values
            .maxWithOrNull(compareBy<List<HungarianPlate.Match>> { group -> group.sumOf { it.score } })
            ?.maxByOrNull { it.score }
}