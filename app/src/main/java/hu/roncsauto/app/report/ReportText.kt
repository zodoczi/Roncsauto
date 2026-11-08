package hu.roncsauto.app.report

import java.time.LocalDate
import java.util.Locale

object ReportText {

    fun formatHu(date: LocalDate): String =
        String.format(Locale.ROOT, "%04d.%02d.%02d.", date.year, date.monthValue, date.dayOfMonth)

    /**
     * A lejárat csak akkor kerül a szövegbe, ha a hívó átadta, és a mai napnál korábbi.
     * Rendszámból nem számol dátumot.
     */
    fun build(plate: String, expiry: LocalDate?, today: LocalDate): String {
        val base = "Elhagyott / roncs autó. Rendszám: $plate."
        if (expiry != null && expiry.isBefore(today)) {
            return "$base A műszaki vizsga lejárata: ${formatHu(expiry)} (lejárt)."
        }
        return base
    }
}
