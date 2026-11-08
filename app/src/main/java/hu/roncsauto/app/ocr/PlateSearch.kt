package hu.roncsauto.app.ocr

import hu.roncsauto.app.plate.HungarianPlate

/** Képkivágások és szavazás a több menetes rendszámkereséshez (Android-független). */
object PlateSearch {

    data class Region(val left: Int, val top: Int, val width: Int, val height: Int)

    /** Pontos, elkülönített olvasat. */
    const val STRONG_SCORE = 35

    /** Szavazáskor minden olvasat ennyit ér, a pontszám csak döntetlent bont (max. 50 < 100). */
    private const val VOTE_WEIGHT = 100

    /**
     * Akkor állunk meg, ha ugyanazt a rendszámot legalább két menet olvasta, és
     * legalább az egyik erős. Egyetlen olvasatban megállni hibás: az emulátoron a
     * tesztfotóról egy kivágás "IUX869"-et adott, egy másik "FIUX 868"-at.
     */
    fun confident(found: List<HungarianPlate.Match>): Boolean {
        val sizes = clusters(found).map { cluster -> cluster to cluster.size }
            .sortedByDescending { it.second }
        val top = sizes.firstOrNull() ?: return false
        val runnerUp = sizes.firstOrNull { other -> other.first.none { it in top.first } }?.second ?: 0
        return top.second >= 4 && top.second >= 2 * runnerUp &&
            top.first.any { it.score >= STRONG_SCORE }
    }

    private fun clusters(found: List<HungarianPlate.Match>): List<List<HungarianPlate.Match>> =
        found.map { seed -> found.filter { similar(it.compact, seed.compact) } }

    /** Torzítás a kivágások előtt: függőleges nyírás (ky) vagy elforgatás (fok). */
    data class Warp(val skewY: Float = 0f, val degrees: Float = 0f) {
        val label: String get() = if (skewY != 0f) "skew $skewY" else "rot $degrees"
    }

    /**
     * Oldalról fotózott táblánál a betűk függőlegesek maradnak, csak az alapvonal lejt
     * (perspektíva = nyírás, nem forgatás). Az ML Kit ezt nem olvassa, elforgatva sem;
     * függőleges nyírással kiegyenesítve igen (IUX-868: ky ≈ 0,85–1,0).
     */
    val WARPS = listOf(
        Warp(skewY = 0.85f), Warp(skewY = -0.85f), Warp(skewY = 1.0f), Warp(skewY = -1.0f),
        Warp(skewY = 0.6f), Warp(skewY = -0.6f), Warp(skewY = 0.35f), Warp(skewY = -0.35f),
        Warp(degrees = 20f), Warp(degrees = -20f)
    )

    /**
     * 2×2 és 3×3 átfedő rács. A kicsi rendszám így nagyítva kerül az OCR elé.
     */
    fun regions(width: Int, height: Int, grids: IntArray = intArrayOf(2, 3)): List<Region> {
        if (width <= 0 || height <= 0) return emptyList()
        val out = mutableListOf<Region>()
        for (n in grids) {
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

    /**
     * Konszenzus a menetek olvasataiból. Az egy karakterben eltérő azonos hosszú olvasatok
     * (pl. "IUX868" / "IUX869") egy csoportba kerülnek; a legnagyobb csoportban
     * pozíciónként pontszámmal súlyozott többségi szavazás dönt.
     */
    fun pick(found: List<HungarianPlate.Match>): HungarianPlate.Match? {
        if (found.isEmpty()) return null
        val best = clusters(found).maxWith(
            compareBy<List<HungarianPlate.Match>> { it.size }.thenBy { c -> c.sumOf { it.score } }
        )
        val length = best.first().compact.length
        val consensus = buildString {
            for (k in 0 until length) {
                val votes = HashMap<Char, Int>()
                for (m in best) votes[m.compact[k]] = (votes[m.compact[k]] ?: 0) + VOTE_WEIGHT + m.score.coerceAtLeast(0)
                append(votes.maxBy { it.value }.key)
            }
        }
        return best.filter { it.compact == consensus }.maxByOrNull { it.score }
            ?: HungarianPlate.findBest(consensus)
            ?: best.maxBy { it.score }
    }

    private fun similar(a: String, b: String): Boolean =
        a.length == b.length && a.indices.count { a[it] != b[it] } <= 1

    /** Telefonos képernyőképnél a felső állapotsor és az alsó navigációs sáv zaj. */
    fun chromeBands(height: Int): Pair<Int, Int> {
        val top = height * 4 / 100
        val bottom = height * 3 / 100
        return top to (height - top - bottom).coerceAtLeast(1)
    }

    /** A szoros kivágás újraolvasásának nyírás-eltérései. */
    val REFINE_SKEWS = floatArrayOf(0f, 0.15f, -0.15f)

    /** Rendszámszerű blokk: legalább 5 betű/szám, betű és számjegy (vagy & jel) is van benne. */
    fun plateLike(text: String): Boolean {
        val chars = text.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' || it == '&' }
        return chars.length in 5..12 && chars.any { it in 'A'..'Z' } && chars.any { it in '0'..'9' || it == '&' }
    }

    /** Blokk köré 35%-os margó, a képen belül. */
    fun expand(left: Int, top: Int, width: Int, height: Int, imageW: Int, imageH: Int): Region? {
        if (width <= 0 || height <= 0) return null
        val mx = width * 35 / 100
        val my = height * 35 / 100
        val l = (left - mx).coerceIn(0, imageW - 1)
        val t = (top - my).coerceIn(0, imageH - 1)
        val r = (left + width + mx).coerceIn(l + 1, imageW)
        val b = (top + height + my).coerceIn(t + 1, imageH)
        return Region(l, t, r - l, b - t)
    }

    /** A kivágás magassága legalább ~360 px legyen, legfeljebb 3× nagyítással. */
    fun refineScale(height: Int): Float =
        if (height <= 0) 1f else (360f / height).coerceIn(1f, 3f)
}