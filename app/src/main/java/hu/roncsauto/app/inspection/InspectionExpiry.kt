package hu.roncsauto.app.inspection

import hu.roncsauto.app.plate.HungarianPlate
import hu.roncsauto.app.report.ReportText
import java.text.Normalizer
import java.time.DateTimeException
import java.time.LocalDate
import java.util.Locale

/**
 * Kézi JSZP-szövegből vagy képernyőképből kiolvasott műszaki lejárat.
 * Nem kérdez le semmilyen nyilvántartást, és nem talál ki dátumot.
 */
object InspectionExpiry {

    enum class Status {
        MATCHED,
        NEEDS_PLATE_CONFIRMATION,
        PLATE_MISMATCH,
        NO_DATE,
        AMBIGUOUS
    }

    data class Result(
        val status: Status,
        val date: LocalDate?,
        val expired: Boolean,
        val plates: List<String>,
        val canApply: Boolean,
        val summary: String
    )

    fun isExpired(date: LocalDate, today: LocalDate): Boolean = date.isBefore(today)

    fun retainForPlate(previousPlate: String?, nextPlate: String?, expiry: LocalDate?): LocalDate? {
        if (expiry == null || previousPlate == null || nextPlate == null) return null
        return if (HungarianPlate.same(previousPlate, nextPlate)) expiry else null
    }

    fun analyze(text: String, selectedPlate: String, today: LocalDate): Result {
        val selected = HungarianPlate.findBest(selectedPlate)?.canonical
            ?: return Result(
                status = Status.PLATE_MISMATCH,
                date = null,
                expired = false,
                plates = emptyList(),
                canApply = false,
                summary = "A kiválasztott rendszám nem értelmezhető, ezért nem veszek át dátumot."
            )
        val foundDates = extractDates(text, today)
        val chosen = chooseDate(text, foundDates)
        if (chosen == null) {
            return if (foundDates.isEmpty()) {
                Result(
                    Status.NO_DATE,
                    null,
                    false,
                    platesIn(text),
                    false,
                    "Nem találtam dátumot. Példa: 2020.03.15."
                )
            } else {
                Result(
                    Status.AMBIGUOUS,
                    null,
                    false,
                    platesIn(text),
                    false,
                    "Több dátum van, nem egyértelmű a műszaki lejárat. Írd be csak azt, például: 2020.03.15."
                )
            }
        }
        val plates = platesIn(text)
        val selectedCompact = HungarianPlate.findBest(selected)?.compact
        val matchesSelected = plates.any { HungarianPlate.findBest(it)?.compact == selectedCompact }
        val others = plates.filter { HungarianPlate.findBest(it)?.compact != selectedCompact }
        val expired = isExpired(chosen, today)
        val formatted = ReportText.formatHu(chosen)
        return when {
            plates.isEmpty() -> confirmed(
                status = Status.NEEDS_PLATE_CONFIRMATION,
                date = chosen,
                expired = expired,
                plates = plates,
                summary = "Dátum: $formatted. Nincs rendszám a szövegben. Csak akkor használom, ha megerősíted, hogy a(z) $selected járműhöz tartozik. ${expirySentence(expired)}"
            )
            matchesSelected && others.isEmpty() -> confirmed(
                status = Status.MATCHED,
                date = chosen,
                expired = expired,
                plates = plates,
                summary = "Dátum: $formatted. Rendszám egyezik: $selected. ${expirySentence(expired)}"
            )
            matchesSelected -> confirmed(
                status = Status.NEEDS_PLATE_CONFIRMATION,
                date = chosen,
                expired = expired,
                plates = plates,
                summary = "Dátum: $formatted. Több rendszám szerepel (${plates.joinToString()}). Erősítsd meg, hogy a kiválasztott $selected járműé. ${expirySentence(expired)}"
            )
            else -> Result(
                status = Status.PLATE_MISMATCH,
                date = null,
                expired = false,
                plates = plates,
                canApply = false,
                summary = "A szöveg rendszáma nem a kiválasztott ($selected), hanem: ${plates.joinToString()}. A dátumot nem veszem át."
            )
        }
    }

    private fun confirmed(
        status: Status,
        date: LocalDate,
        expired: Boolean,
        plates: List<String>,
        summary: String
    ) = Result(status, date, expired, plates, canApply = true, summary = summary)

    private fun expirySentence(expired: Boolean): String =
        if (expired) "Korábbi, mint a mai nap, tehát lejárt."
        else "A mai nap vagy későbbi, ezért nem kerül be lejártként."

    private fun platesIn(text: String): List<String> =
        HungarianPlate.findAll(text).map { it.canonical }.distinct()

    private data class FoundDate(val date: LocalDate, val start: Int, val end: Int)

    private fun chooseDate(text: String, dates: List<FoundDate>): LocalDate? {
        val unique = dates.distinctBy { it.date }
        if (unique.isEmpty()) return null
        if (unique.size == 1) return unique.first().date
        val scored = unique.map { it to score(text, it.start, it.end) }
        val positive = scored.filter { it.second > 0 }
        val positiveDates = positive.map { it.first.date }.distinct()
        if (positiveDates.size == 1) return positiveDates.first()
        if (positiveDates.size > 1) {
            val best = positive.maxBy { it.second }
            val second = positive.filter { it.first.date != best.first.date }.maxOf { it.second }
            if (best.second >= second + 2) return best.first.date
        }
        return null
    }

    private fun score(text: String, start: Int, end: Int): Int {
        val from = (start - 40).coerceAtLeast(0)
        val to = (end + 12).coerceAtMost(text.length)
        val folded = fold(text.substring(from, to))
        var value = 0
        if (folded.contains("muszaki") && (folded.contains("erveny") || folded.contains("lejar"))) {
            value += 6
        } else if (folded.contains("lejar")) {
            value += 3
        } else if (folded.contains("ervenyesse") || folded.contains("ervenyes")) {
            value += 2
        }
        if (folded.contains("eredetiseg")) value -= 4
        if (folded.contains("karesemeny")) value -= 4
        if (folded.contains("forgalomba helyez")) value -= 4
        if (folded.contains("kilometer")) value -= 3
        if (folded.contains("vizsgalat idopont") || folded.contains("vizsga idopont")) value -= 4
        return value
    }

    private fun extractDates(text: String, today: LocalDate): List<FoundDate> {
        val found = mutableListOf<FoundDate>()
        val ymd = Regex("""(?<!\d)(\d{4})\s*[.\-/]\s*(\d{1,2})\s*[.\-/]\s*(\d{1,2})\.?(?!\d)""")
        val dmy = Regex("""(?<!\d)(\d{1,2})\s*[.\-]\s*(\d{1,2})\s*[.\-]\s*(\d{4})(?!\d)""")
        for (match in ymd.findAll(text)) {
            val date = dateOrNull(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt(), today)
                ?: continue
            found += FoundDate(date, match.range.first, match.range.last + 1)
        }
        for (match in dmy.findAll(text)) {
            val rangeStart = match.range.first
            val rangeEnd = match.range.last + 1
            if (found.any { it.start < rangeEnd && rangeStart < it.end }) continue
            val date = dateOrNull(match.groupValues[3].toInt(), match.groupValues[2].toInt(), match.groupValues[1].toInt(), today)
                ?: continue
            found += FoundDate(date, rangeStart, rangeEnd)
        }
        return found
    }

    private fun dateOrNull(year: Int, month: Int, day: Int, today: LocalDate): LocalDate? {
        if (year < 1970 || year > today.year + 6) return null
        return try {
            LocalDate.of(year, month, day)
        } catch (_: DateTimeException) {
            null
        }
    }

    private fun fold(value: String): String {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
    }
}
