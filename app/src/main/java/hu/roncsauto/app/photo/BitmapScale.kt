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
}
