package com.yaari.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every shipped language has every string (and plural) of the English default, with the same format placeholders,
 * in each source set (main, demo, live). Runs on the resource files themselves, so it also covers flavor strings.
 */
class TranslationsTest {
    private val sourceSets = listOf("main", "demo", "live")
    private val src = File("src").takeIf { it.isDirectory } ?: File("app/src")

    /**
     * The resource folder of each UI language (AppLocale.languages): what a translator creates as
     * src/{main,live,demo}/res/values-<qualifier>/strings.xml. Hebrew and Indonesian use Android's old codes.
     */
    private val qualifiers = mapOf(
        "hi" to "hi", "bn" to "bn", "ur" to "ur", "pa" to "pa", "mr" to "mr", "gu" to "gu", "ta" to "ta", "te" to "te",
        "kn" to "kn", "ml" to "ml", "ne" to "ne", "pt-BR" to "pt-rBR", "es" to "es", "fr" to "fr", "de" to "de", "it" to "it",
        "nl" to "nl", "pl" to "pl", "ro" to "ro", "tr" to "tr", "ru" to "ru", "uk" to "uk", "ar" to "ar", "fa" to "fa",
        "he" to "iw", "sw" to "sw", "vi" to "vi", "th" to "th", "id" to "in", "ms" to "ms", "fil" to "fil", "ja" to "ja",
        "ko" to "ko", "zh-Hans" to "b+zh+Hans", "zh-Hant" to "b+zh+Hant",
    )

    /** Every translated language in the app: each must be complete (a new folder is checked as soon as it exists). */
    private val locales: List<String> by lazy {
        File(src, "main/res").listFiles()!!.map { it.name }
            .filter { it.startsWith("values-") && File(src, "main/res/$it/strings.xml").exists() }
            .map { it.removePrefix("values-") }.sorted()
    }

    private data class Res(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>)

    private fun load(file: File): Res {
        if (!file.exists()) return Res(emptyMap(), emptyMap())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val strings = mutableMapOf<String, String>()
        val plurals = mutableMapOf<String, Map<String, String>>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as? Element ?: continue
            if (e.getAttribute("translatable") == "false") continue
            when (e.tagName) {
                "string" -> strings[e.getAttribute("name")] = e.textContent
                "plurals" -> {
                    val items = e.getElementsByTagName("item")
                    plurals[e.getAttribute("name")] = (0 until items.length).associate {
                        val item = items.item(it) as Element
                        item.getAttribute("quantity") to item.textContent
                    }
                }
            }
        }
        return Res(strings, plurals)
    }

    /** "%1$s", "%2$d", "%d", "%s" (not "%%"), as a sorted list so "%1$s %2$s" equals "%2$s ... %1$s". */
    private fun placeholders(text: String) =
        Regex("%(\\d+\\$)?[-#+ 0,(]*\\d*(\\.\\d+)?[sdfx]").findAll(text.replace("%%", "")).map { it.value }.sorted().toList()

    @Test fun everyLocaleHasEveryKeyWithMatchingPlaceholders() {
        val problems = mutableListOf<String>()
        var checked = 0
        for (set in sourceSets) {
            val base = load(File(src, "$set/res/values/strings.xml"))
            for (locale in locales) {
                val file = File(src, "$set/res/values-$locale/strings.xml")
                val tr = load(file)
                for ((key, en) in base.strings) {
                    checked++
                    val t = tr.strings[key]
                    when {
                        t == null -> problems += "$set/values-$locale: missing string '$key'"
                        t.isBlank() -> problems += "$set/values-$locale: empty string '$key'"
                        placeholders(t) != placeholders(en) ->
                            problems += "$set/values-$locale: '$key' placeholders ${placeholders(t)} != English ${placeholders(en)}"
                    }
                }
                for ((key, enItems) in base.plurals) {
                    checked++
                    val items = tr.plurals[key]
                    if (items == null) {
                        problems += "$set/values-$locale: missing plurals '$key'"
                        continue
                    }
                    if ("other" !in items) problems += "$set/values-$locale: plurals '$key' has no 'other'"
                    val enPh = placeholders(enItems.getValue("other"))
                    items.forEach { (q, text) ->
                        if (placeholders(text) != enPh) problems += "$set/values-$locale: plurals '$key' [$q] placeholders ${placeholders(text)} != $enPh"
                    }
                }
                // No leftovers: a key the English file no longer has is dead weight (or a typo).
                (tr.strings.keys - base.strings.keys).forEach { problems += "$set/values-$locale: unknown string '$it'" }
                (tr.plurals.keys - base.plurals.keys).forEach { problems += "$set/values-$locale: unknown plurals '$it'" }
            }
        }
        assertTrue("checked nothing: resources not found from ${File(".").absolutePath}", checked > 1000)
        assertEquals("Translation problems:\n" + problems.joinToString("\n"), 0, problems.size)
    }

    @Test fun placeholderParserSeesOrderAndTypes() {
        assertEquals(listOf("%1\$s", "%2\$d"), placeholders("Room %2\$d by %1\$s, 100%% sure"))
        assertEquals(listOf("%d"), placeholders("%d online"))
    }

    /**
     * Translated folders are UI languages, the four first ones are there, and every UI language is offered: in the
     * app's picker, to Android 13+'s per-app language setting (xml/locales_config.xml) and kept by localeFilters.
     */
    @Test fun shippedLanguagesMatchTheResourceFolders() {
        assertTrue("unknown language folders: ${locales - qualifiers.values.toSet()}", qualifiers.values.containsAll(locales))
        assertTrue(locales.containsAll(listOf("hi", "bn", "pt-rBR", "vi")))
        assertEquals(setOf("en") + qualifiers.keys, AppLocale.languages.map { it.first }.toSet())
        val config = File(src, "main/res/xml/locales_config.xml").readText()
        (listOf("en") + qualifiers.keys).forEach { assertTrue("locales_config lacks $it", "android:name=\"$it\"" in config) }
        val gradle = File(src.parentFile, "build.gradle.kts").readText()
        (listOf("en") + qualifiers.values).forEach { assertTrue("localeFilters lacks $it", "\"$it\"" in gradle) }
        // The flavors' strings come with the language too (a folder in main without live's would show English there).
        for (set in listOf("live")) locales.forEach { assertTrue("$set lacks values-$it", File(src, "$set/res/values-$it/strings.xml").exists()) }
    }
}
