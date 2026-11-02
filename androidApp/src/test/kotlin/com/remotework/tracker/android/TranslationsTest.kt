package com.remotework.tracker.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TranslationsTest {

    private val de = translations.getValue("de")
    private val en = translations.getValue("en")

    /** All t("key", ...) keys used in the app sources. Unit tests run in the module directory. */
    private fun usedKeys(): Set<String> {
        val dir = File("src/main/kotlin/com/remotework/tracker/android")
        assertTrue("source dir not found: ${dir.absolutePath}", dir.isDirectory)
        val pattern = Regex("""\bt\("([a-z_]+)"""")
        return dir.listFiles { f -> f.extension == "kt" }!!
            .flatMap { pattern.findAll(it.readText()).map { m -> m.groupValues[1] }.toList() }
            .toSet()
    }

    @Test
    fun bothLanguagesHaveExactlyTheSameKeys() {
        assertEquals("only in de", emptySet<String>(), de.keys - en.keys)
        assertEquals("only in en", emptySet<String>(), en.keys - de.keys)
    }

    @Test
    fun noTranslationIsBlank() {
        for ((lang, map) in translations) {
            for ((key, value) in map) {
                assertTrue("blank value for $lang/$key", value.isNotBlank())
            }
        }
    }

    @Test
    fun everyKeyUsedInTheCodeExistsInBothLanguages() {
        val used = usedKeys()
        assertTrue("no t(...) calls found, regex or path broken", used.size > 30)
        assertEquals("used but missing in de", emptySet<String>(), used - de.keys)
        assertEquals("used but missing in en", emptySet<String>(), used - en.keys)
    }

    @Test
    fun noTranslationKeyIsUnused() {
        assertEquals("unused keys", emptySet<String>(), de.keys - usedKeys())
    }

    @Test
    fun germanTextUsesRealUmlautsNotAsciiTransliterations() {
        val ascii = Regex("(Eintraege|loeschen|Loeschen|geloescht|zurueck|hinzufuegen|Verfuegbare|abschliessen)")
        for ((key, value) in de) {
            assertTrue("ascii transliteration in de/$key: $value", !ascii.containsMatchIn(value))
        }
    }

    @Test
    fun germanAndEnglishDifferForTheMainLabels() {
        for (key in listOf("settings", "save", "cancel", "clear_all", "close_month", "remaining", "history")) {
            assertTrue("$key identical in de and en", de.getValue(key) != en.getValue(key))
        }
    }

    @Test
    fun lookupFallsBackToTheKeyForUnknownKeysAndLanguages() {
        assertEquals("gibt_es_nicht", t("gibt_es_nicht", "de"))
        assertEquals("save", t("save", "fr"))
        assertEquals("Speichern", t("save", "de"))
        assertEquals("Save", t("save", "en"))
    }

    @Test
    fun clearConfirmationTextIsPresentInBothLanguages() {
        assertTrue(t("clear_confirm", "de").startsWith("Wirklich alles löschen"))
        assertTrue(t("clear_confirm", "en").startsWith("Really delete everything"))
    }
}
