package hu.roncsauto.app.plate

import java.util.Locale

/**
 * Magyar rendszám felismerése a kamerás és a saját fotós szövegből.
 * Kanonikus alak: ABC-123, AB CD-123, GT-12-34.
 */
object HungarianPlate {

    enum class Kind { NEW, CLASSIC, OLD }

    data class Match(
        val canonical: String,
        val compact: String,
        val kind: Kind,
        val start: Int,
        val end: Int,
        val score: Int
    )

    private val vowels = setOf('A', 'E', 'I', 'O', 'U')
    private val forbiddenFirstPairs = setOf("CS", "GY", "LY", "NY", "SZ", "TY", "ZS")

    private val newRegex =
        Regex("""(?<![A-Z0-9])([A-Z]{2})[ \t.\-]*([A-Z]{2})[ \t.\-]*(\d{3})(?!\d)""")
    private val classicRegex =
        Regex("""(?<![A-Z0-9])([A-Z]{3})[ \t.\-]*(\d{3})(?!\d)""")
    private val oldRegex =
        Regex("""(?<![A-Z0-9])([A-Z]{2})[ \t.\-]*(\d{2})[ \t.\-]*(\d{2})(?!\d)""")

    fun findBest(text: String): Match? {
        if (text.isBlank()) return null
        val spaced = text.replace('\n', ' ')
        val exact = listOfNotNull(bestIn(text), if (spaced != text) bestIn(spaced) else null)
            .maxByOrNull { it.score }
        if (exact != null && exact.score >= EXACT_TRUSTED) return exact
        val tolerant = findTolerant(text)
        return listOfNotNull(exact, tolerant).maxByOrNull { it.score }
    }

    /**
     * Tűrő keresés gyenge OCR-hez: összefűzi a szomszédos szövegdarabokat
     * ("IUX" + "-868"), levágja az EU-sáv "H" betűjét, és pozíció szerint javítja
     * a betű/szám keveredést (0/O, 1/I, 8/B, 5/S, 2/Z, 6/G). Betűt betűre
     * (pl. I/L) nem cserél. Pontszáma mindig kisebb a pontos találatokénál.
     */
    fun findTolerant(text: String): Match? {
        val prepared = normalize(text)
        val tokens = tokenRegex.findAll(prepared).toList()
        var best: Match? = null
        for (i in tokens.indices) {
            val builder = StringBuilder()
            for (j in i until minOf(tokens.size, i + MAX_TOKENS)) {
                builder.append(tokens[j].value)
                if (builder.length > 8) break
                val joined = builder.toString()
                val variants = mutableListOf(joined)
                if (joined.length in 7..8 && joined[0] == 'H') variants += joined.substring(1)
                for (candidate in variants) {
                    val match = tolerantMatch(
                        candidate,
                        tokens[i].range.first,
                        tokens[j].range.last + 1,
                        j - i
                    ) ?: continue
                    if (best == null || match.score > best.score) best = match
                }
            }
        }
        return best
    }

    private fun tolerantMatch(raw: String, start: Int, end: Int, penalty: Int): Match? {
        return when (raw.length) {
            6 -> fixPattern(raw, "LLLDDD")?.let { (fixed, corrections) ->
                Match("${fixed.substring(0, 3)}-${fixed.substring(3)}", fixed, Kind.CLASSIC, start, end,
                    TOLERANT_BASE - CORRECTION_COST * corrections - penalty)
            }
            7 -> fixPattern(raw, "LLLLDDD")?.let { (fixed, corrections) ->
                val first = fixed.substring(0, 2)
                if (!validNew(first) || first[0] == 'H') return null
                Match("$first ${fixed.substring(2, 4)}-${fixed.substring(4)}", fixed, Kind.NEW, start, end,
                    TOLERANT_BASE - CORRECTION_COST * corrections - penalty)
            }
            else -> null
        }
    }

    /** L = betű, D = számjegy; legfeljebb [MAX_CORRECTIONS] betű/szám csere. */
    internal fun fixPattern(raw: String, pattern: String): Pair<String, Int>? {
        if (raw.length != pattern.length) return null
        val out = StringBuilder(raw.length)
        var corrections = 0
        for (k in raw.indices) {
            val ch = raw[k]
            val wantLetter = pattern[k] == 'L'
            val fixed = when {
                wantLetter && ch in 'A'..'Z' -> ch
                !wantLetter && ch in '0'..'9' -> ch
                wantLetter -> letterFix[ch]?.also { corrections++ } ?: return null
                else -> digitFix[ch]?.also { corrections++ } ?: return null
            }
            out.append(fixed)
        }
        if (corrections > MAX_CORRECTIONS) return null
        return out.toString() to corrections
    }

    private val tokenRegex = Regex("[A-Z0-9]+")
    private val letterFix = mapOf(
        '0' to 'O', '1' to 'I', '2' to 'Z', '4' to 'A', '5' to 'S', '6' to 'G', '7' to 'T', '8' to 'B'
    )
    private val digitFix = mapOf(
        'O' to '0', 'Q' to '0', 'I' to '1', 'L' to '1', 'J' to '1', 'T' to '7',
        'Z' to '2', 'S' to '5', 'B' to '8', 'G' to '6', 'A' to '4'
    )
    private const val EXACT_TRUSTED = 30
    private const val TOLERANT_BASE = 20
    private const val CORRECTION_COST = 5
    private const val MAX_CORRECTIONS = 2
    private const val MAX_TOKENS = 4

    fun findAll(text: String): List<Match> = collect(normalize(text))

    fun same(a: String, b: String): Boolean {
        val left = findBest(a)?.compact ?: return false
        val right = findBest(b)?.compact ?: return false
        return left == right
    }

    private fun bestIn(text: String): Match? {
        val all = collect(normalize(text))
        if (all.isEmpty()) return null
        return all.maxWith(compareBy<Match> { it.score }.thenByDescending { it.end - it.start })
    }

    private fun collect(text: String): List<Match> {
        val raws = mutableListOf<Raw>()
        for (m in newRegex.findAll(text)) {
            val first = m.groupValues[1]
            if (!validNew(first)) continue
            val second = m.groupValues[2]
            val digits = m.groupValues[3]
            raws += Raw(
                canonical = "$first $second-$digits",
                compact = first + second + digits,
                kind = Kind.NEW,
                start = m.range.first,
                end = m.range.last + 1,
                separated = hasSeparator(text.substring(m.range.first, m.range.last + 1))
            )
        }
        for (m in classicRegex.findAll(text)) {
            val letters = m.groupValues[1]
            val digits = m.groupValues[2]
            raws += Raw(
                canonical = "$letters-$digits",
                compact = letters + digits,
                kind = Kind.CLASSIC,
                start = m.range.first,
                end = m.range.last + 1,
                separated = hasSeparator(text.substring(m.range.first, m.range.last + 1))
            )
        }
        for (m in oldRegex.findAll(text)) {
            val letters = m.groupValues[1]
            val left = m.groupValues[2]
            val right = m.groupValues[3]
            raws += Raw(
                canonical = "$letters-$left-$right",
                compact = letters + left + right,
                kind = Kind.OLD,
                start = m.range.first,
                end = m.range.last + 1,
                separated = hasSeparator(text.substring(m.range.first, m.range.last + 1))
            )
        }
        val ordered = raws.sortedWith(
            compareByDescending<Raw> { it.end - it.start }
                .thenByDescending { kindPriority(it.kind) }
                .thenBy { it.start }
        )
        val kept = mutableListOf<Raw>()
        for (candidate in ordered) {
            if (kept.any { overlaps(it, candidate) }) continue
            kept += candidate
        }
        return kept.sortedBy { it.start }.map { it.toMatch(text) }
    }

    private fun validNew(firstPair: String): Boolean {
        if (firstPair == "OT") return true
        if (firstPair in forbiddenFirstPairs) return false
        val firstIsVowel = firstPair[0] in vowels
        val secondIsVowel = firstPair[1] in vowels
        return firstIsVowel == secondIsVowel
    }

    private fun kindPriority(kind: Kind): Int = when (kind) {
        Kind.NEW, Kind.CLASSIC -> 3
        Kind.OLD -> 2
    }

    private fun overlaps(a: Raw, b: Raw): Boolean = a.start < b.end && b.start < a.end

    private fun hasSeparator(slice: String): Boolean =
        slice.any { it == '-' || it == ' ' || it == '\t' || it == '.' }

    private fun Raw.toMatch(text: String): Match {
        var score = when (kind) {
            Kind.NEW, Kind.CLASSIC -> 30
            Kind.OLD -> 22
        }
        if (separated) score += 5
        // Az új sorozat messze van a H-tól: a "HIUX-868"-féle olvasat az EU-sáv H betűje.
        if (kind == Kind.NEW && canonical[0] == 'H') score -= 40
        val lineStart = text.lastIndexOf('\n', start - 1).let { if (it < 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', end).let { if (it < 0) text.length else it }
        val line = text.substring(lineStart, lineEnd).trim()
        val slice = text.substring(start, end).trim()
        if (line == slice) score += 15
        return Match(canonical, compact, kind, start, end, score)
    }

    private fun normalize(text: String): String {
        val builder = StringBuilder(text.length)
        for (ch in text) {
            when (ch) {
                '–', '—', '−', '‐' -> builder.append('-')
                '\u00A0', '\u00B7', '\u2022', '|' -> builder.append(' ')
                else -> builder.append(ch.toString().uppercase(Locale.ROOT))
            }
        }
        return builder.toString()
    }

    private data class Raw(
        val canonical: String,
        val compact: String,
        val kind: Kind,
        val start: Int,
        val end: Int,
        val separated: Boolean
    )
}
