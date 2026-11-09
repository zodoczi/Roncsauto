package hu.roncsauto.app.wizard

enum class WizardStep(val number: Int, val title: String) {
    PLATE(1, "Rendszám"),
    JSZP(2, "JSZP"),
    JAROKELO(3, "Járókelő");

    fun next(): WizardStep? = when (this) {
        PLATE -> JSZP
        JSZP -> JAROKELO
        JAROKELO -> null
    }

    fun previous(): WizardStep? = when (this) {
        PLATE -> null
        JSZP -> PLATE
        JAROKELO -> JSZP
    }
}
