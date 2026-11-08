package hu.roncsauto.app.report

object HandoffText {
    const val JSZP_URL = "https://magyarorszag.hu/jszp_szuf"
    const val JAROKELO_URL = "https://jarokelo.hu/bejelentest-teszek"

    fun jarokeloInstructions(hasPhoto: Boolean): String {
        val photoLine = if (hasPhoto) {
            "A fotót az app nem tölti fel. A Járókelő Képek mezőjében kézzel kell kiválasztani. Megoszthatod vagy elmentheted, majd onnan tallózod be."
        } else {
            "Nincs kiválasztott fotó, a képet a Járókelőn külön kell csatolni."
        }
        return "A szöveg a vágólapon van. Illeszd a Probléma leírása mezőbe. $photoLine Bejelentkezés és küldés a Járókelőn történik, az app nem küldi el helyetted."
    }
}
