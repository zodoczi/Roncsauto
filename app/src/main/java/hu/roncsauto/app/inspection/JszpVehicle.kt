package hu.roncsauto.app.inspection

import java.text.Normalizer
import java.util.Locale

/** Gyártmány, kereskedelmi leírás és gyártási év a bemásolt JSZP-szövegből. */
object JszpVehicle {

    private val labels = listOf(
        "nyilvantartott rendszam", "muszaki ervenyesseg", "jarmutipusra vonatkozo",
        "gyartmany", "kereskedelmi leiras", "tipusjel", "ek tipus", "gyartas eve"
    )

    fun describe(text: String): String? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        fun value(label: String): String? {
            val index = lines.indexOfFirst { fold(it).startsWith(label) }
            if (index < 0) return null
            val line = lines[index]
            val inline = line.substringAfter(':', "").trim()
            val raw = if (inline.isNotEmpty()) inline else lines.getOrNull(index + 1) ?: return null
            if (labels.any { fold(raw).startsWith(it) }) return null
            return raw.take(40).trim().ifEmpty { null }
        }
        val name = listOfNotNull(value("gyartmany"), value("kereskedelmi leiras"))
            .joinToString(" ").ifBlank { null }
        val year = value("gyartas eve")?.let { Regex("""(?<!\d)(19|20)\d{2}(?!\d)""").find(it)?.value }
        return when {
            name != null && year != null -> "$name, $year"
            name != null -> name
            year != null -> "gyártás éve $year"
            else -> null
        }
    }

    private fun fold(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
}