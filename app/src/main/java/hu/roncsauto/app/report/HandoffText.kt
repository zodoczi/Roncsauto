package hu.roncsauto.app.report

object HandoffText {
    const val JSZP_URL = "https://magyarorszag.hu/jszp_szuf"
    const val JAROKELO_URL = "https://jarokelo.hu/bejelentest-teszek"

    /** A JSZP űrlap a kötőjel nélküli alakot várja (pl. IUX868). */
    fun jszpClipboardPlate(canonicalPlate: String): String =
        hu.roncsauto.app.plate.HungarianPlate.findBest(canonicalPlate)?.compact
            ?: canonicalPlate.replace(Regex("[^A-Za-z0-9]"), "").uppercase()

    const val JSZP_PASTE_HINT =
        "A rendszám a vágólapon van. Illeszd be a 'Gépjármű rendszáma' mezőbe, pipáld ki a kívánt adatokat, majd nyomj a KERESÉS-re."

    fun jarokeloInstructions(hasPhoto: Boolean, photoSaved: Boolean): String {
        val photoLine = when {
            !hasPhoto ->
                "Nincs kiválasztott fotó, a képet a Járókelőn külön kell csatolni."
            photoSaved ->
                "A fotó a galériában (Pictures/RoncsAuto) van. A Járókelő Képek mezőjében onnan válaszd ki. Az app nem tölti fel helyetted."
            else ->
                "A fotót az app nem tölti fel. Mentheted a galériába vagy megoszthatod, majd a Járókelő Képek mezőjében tallózd be."
        }
        return "A leírás a vágólapon van. Illeszd a „Probléma leírása” mezőbe " +
            "(a Járókelő oldala nem fogad előtöltött URL-paramétert). $photoLine " +
            "Bejelentkezés és küldés a Járókelőn történik, az app nem küldi el helyetted."
    }
}
