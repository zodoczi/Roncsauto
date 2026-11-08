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
        return listOfNotNull(bestIn(text), if (spaced != text) bestIn(spaced) else null)
            .maxByOrNull { it.score }
    }

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
                '\u00A0' -> builder.append(' ')
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
