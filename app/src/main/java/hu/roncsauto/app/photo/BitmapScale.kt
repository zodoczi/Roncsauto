package hu.roncsauto.app.photo

object BitmapScale {
    /**
     * 2 hatványa, amellyel a hosszabb oldal a [maxEdge] közelébe kerül,
     * és a képpontszám sem marad a cél sokszorosa.
     */
    fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        if (width <= 0 || height <= 0 || maxEdge <= 0) return 1
        var size = 1
        val longest = maxOf(width, height)
        while (longest / (size * 2) >= maxEdge) size *= 2
        while (width.toLong() * height.toLong() / (size.toLong() * size.toLong()) > maxEdge.toLong() * maxEdge.toLong() * 2L) {
            size *= 2
        }
        return size
    }

    /**
     * Célméret az ImageDecoderhez: a hosszabb oldal legfeljebb [maxEdge] lesz, az arány megmarad.
     * Null, ha nem kell kicsinyíteni.
     */
    fun targetSize(width: Int, height: Int, maxEdge: Int): Pair<Int, Int>? {
        if (width <= 0 || height <= 0 || maxEdge <= 0) return null
        val longest = maxOf(width, height)
        if (longest <= maxEdge) return null
        val scale = maxEdge.toDouble() / longest.toDouble()
        val w = Math.round(width * scale).toInt().coerceIn(1, maxEdge)
        val h = Math.round(height * scale).toInt().coerceIn(1, maxEdge)
        return w to h
    }
}