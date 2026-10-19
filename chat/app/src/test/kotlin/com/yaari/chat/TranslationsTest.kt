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
    private val locales = listOf("hi", "pt-rBR", "vi", "bn")
    private val sourceSets = listOf("main", "demo", "live")
    private val src = File("src").takeIf { it.isDirectory } ?: File("app/src")

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

    /** The languages offered in the app and to Android's per-app language setting are the ones translated. */
    @Test fun shippedLanguagesMatchTheResourceFolders() {
        val folders = File(src, "main/res").listFiles()!!.map { it.name }
            .filter { it.startsWith("values-") && File(it.let { n -> File(src, "main/res/$n") }, "strings.xml").exists() }
            .map { it.removePrefix("values-") }.toSet()
        assertEquals(locales.toSet(), folders)
        val gradle = File(src.parentFile, "build.gradle.kts").readText()
        listOf("en", "hi", "pt-rBR", "vi", "bn").forEach { assertTrue("localeFilters lacks $it", "\"$it\"" in gradle) }
    }
}
