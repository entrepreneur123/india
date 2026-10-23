package com.yaari.chat.domain

import com.yaari.chat.model.Links
import java.util.Locale

/** Which kind of server message a translation is for (edge function translate): chats and DMs, or room lines. */
enum class TranslateKind(val code: String) { CHAT("chat"), ROOM("room") }

/** The server's answer: [same] = the message is already in the target language (nothing extra is shown). */
data class Translation(val text: String, val same: Boolean)

/** The languages the translate function can write in, each with its own name. Order: how the picker lists them. */
object TranslateTargets {
    val names: Map<String, String> = linkedMapOf(
        "en" to "English", "hi" to "हिन्दी", "bn" to "বাংলা", "ur" to "اردو", "pa" to "ਪੰਜਾਬੀ", "mr" to "मराठी",
        "gu" to "ગુજરાતી", "ta" to "தமிழ்", "te" to "తెలుగు", "kn" to "ಕನ್ನಡ", "ml" to "മലയാളം", "or" to "ଓଡ଼ିଆ",
        "as" to "অসমীয়া", "ne" to "नेपाली", "si" to "සිංහල", "pt-BR" to "Português (Brasil)", "pt" to "Português (Portugal)",
        "es" to "Español (España)", "es-419" to "Español (Latinoamérica)", "fr" to "Français", "de" to "Deutsch",
        "it" to "Italiano", "nl" to "Nederlands", "pl" to "Polski", "ro" to "Română", "cs" to "Čeština", "sk" to "Slovenčina",
        "hu" to "Magyar", "el" to "Ελληνικά", "sv" to "Svenska", "da" to "Dansk", "no" to "Norsk", "fi" to "Suomi",
        "uk" to "Українська", "ru" to "Русский", "bg" to "Български", "sr" to "Српски", "hr" to "Hrvatski", "tr" to "Türkçe",
        "ar" to "العربية", "fa" to "فارسی", "he" to "עברית", "sw" to "Kiswahili", "am" to "አማርኛ", "ha" to "Hausa",
        "yo" to "Yorùbá", "zu" to "isiZulu", "vi" to "Tiếng Việt", "th" to "ไทย", "id" to "Bahasa Indonesia",
        "ms" to "Bahasa Melayu", "fil" to "Filipino", "my" to "မြန်မာ", "km" to "ខ្មែរ", "lo" to "ລາວ", "ja" to "日本語",
        "ko" to "한국어", "zh-Hans" to "简体中文", "zh-Hant" to "繁體中文", "kk" to "Қазақ тілі", "uz" to "Oʻzbekcha",
        "az" to "Azərbaycanca", "ka" to "ქართული", "hy" to "Հայերեն",
    )
    val codes: List<String> = names.keys.toList()

    fun isValid(code: String?) = code != null && code in names

    /** Portuguese outside Brazil (Portugal and the African countries) gets European Portuguese. */
    private val EUROPEAN_PT = setOf("PT", "AO", "MZ", "CV", "GW", "ST", "TL", "MO", "LU", "CH")
    private val TRADITIONAL_ZH = setOf("TW", "HK", "MO")

    /** The target for a UI or phone locale; English when the language has no target. */
    fun fromLocale(locale: Locale?): String {
        if (locale == null) return "en"
        val region = locale.country.uppercase()
        return when (val lang = locale.language.lowercase()) {
            "pt" -> if (region in EUROPEAN_PT) "pt" else "pt-BR"
            "es" -> if (region.isEmpty() || region == "ES" || region == "GQ") "es" else "es-419"
            "zh" -> if (locale.script == "Hant" || (locale.script.isEmpty() && region in TRADITIONAL_ZH)) "zh-Hant" else "zh-Hans"
            "iw", "he" -> "he"
            "in", "id" -> "id"
            "tl", "fil" -> "fil"
            "nb", "nn", "no" -> "no"
            else -> lang.takeIf { it in names } ?: "en"
        }
    }

    /** [fromLocale] for a BCP-47 tag ("pt-BR", "zh-Hant-TW"). */
    fun fromTag(tag: String?): String = fromLocale(tag?.takeIf { it.isNotBlank() }?.let(Locale::forLanguageTag))
}

private typealias Script = Character.UnicodeScript

/**
 * Whether a chat line looks written in another language than [target] (auto translation). Cheap and on the phone:
 * the script decides first (Devanagari, Arabic, Cyrillic, Han, ...); text in the same script as the target (mostly
 * Latin) goes by common words of the big Latin-script languages and romanised Hindi ("kya haal hai"). Lines that are
 * only emoji, links, numbers or a single letter never count. When unsure, it says no: a wrong "yes" costs a request,
 * and every line can still be translated from its menu.
 */
object ForeignText {
    /** The scripts a target language is normally written in. */
    private fun scriptsOf(target: String): Set<Script> = when (target) {
        "hi", "mr", "ne" -> setOf(Script.DEVANAGARI)
        "bn", "as" -> setOf(Script.BENGALI)
        "ur", "ar", "fa" -> setOf(Script.ARABIC)
        "pa" -> setOf(Script.GURMUKHI)
        "gu" -> setOf(Script.GUJARATI)
        "ta" -> setOf(Script.TAMIL)
        "te" -> setOf(Script.TELUGU)
        "kn" -> setOf(Script.KANNADA)
        "ml" -> setOf(Script.MALAYALAM)
        "or" -> setOf(Script.ORIYA)
        "si" -> setOf(Script.SINHALA)
        "ru", "uk", "bg", "kk" -> setOf(Script.CYRILLIC)
        "sr" -> setOf(Script.CYRILLIC, Script.LATIN)
        "el" -> setOf(Script.GREEK)
        "he" -> setOf(Script.HEBREW)
        "am" -> setOf(Script.ETHIOPIC)
        "th" -> setOf(Script.THAI)
        "my" -> setOf(Script.MYANMAR)
        "km" -> setOf(Script.KHMER)
        "lo" -> setOf(Script.LAO)
        "ka" -> setOf(Script.GEORGIAN)
        "hy" -> setOf(Script.ARMENIAN)
        "ja" -> setOf(Script.HIRAGANA, Script.KATAKANA, Script.HAN)
        "ko" -> setOf(Script.HANGUL, Script.HAN)
        "zh-Hans", "zh-Hant" -> setOf(Script.HAN)
        else -> setOf(Script.LATIN)
    }

    /** The word lists below, per target (Spanish and Portuguese variants share theirs; Malay reads Indonesian's). */
    private fun wordsKey(target: String) = when (target) {
        "es-419" -> "es"
        "pt-BR" -> "pt"
        "ms" -> "id"
        "ur" -> "hi" // romanised Urdu and romanised Hindi are the same words
        else -> target
    }

    /** Frequent short words that tell Latin-script languages apart (ambiguous ones like "a", "no" left out). */
    private val WORDS: Map<String, Set<String>> = mapOf(
        "en" to "the and you are is what how my me this that do for with have not your but was we be can i'm im don't dont it's yes hello thanks where who why when".words(),
        "es" to "que el la los las y es por para con una como qué cómo estás está hola gracias pero muy yo tú eres soy bien también dónde donde porque tengo quiero".words(),
        "pt" to "você voce não nao um uma com tudo bem obrigado obrigada eu meu minha tá está oi olá muito também onde vc né então legal tô".words(),
        "fr" to "le les et est je tu vous une pas qui avec pour bonjour merci oui c'est ça mais très moi toi suis".words(),
        "de" to "der die das und ist ich du nicht ein eine mit wie was hallo danke ja auch mein bist sehr gut".words(),
        "it" to "il che di è sono sei non per come ciao grazie anche molto bene io".words(),
        "nl" to "het een en ik je niet dat wat hoe met van voor hallo dank ook jij mijn".words(),
        "id" to "yang dan ini itu aku saya kamu apa tidak tak ada dengan untuk juga sudah belum bisa mau gak nggak kenapa bagaimana terima kasih selamat".words(),
        "tr" to "ve bir bu ne nasılsın nasıl ben sen değil mi mı çok için ile merhaba teşekkürler evet hayır var yok".words(),
        "pl" to "nie jest się że jak co ja ty cześć dziękuję tak bardzo mam czy".words(),
        "vi" to "không là của và có tôi bạn gì được này rất chào cảm ơn".words(),
        "fil" to "ang ng mga sa ako ikaw ka ko mo hindi oo salamat kumusta po naman lang yung ba".words(),
        "sw" to "ya wa ni kwa mimi wewe habari asante sana ndiyo hapana nini gani za".words(),
        // Romanised Hindi / Urdu ("Hinglish"), the most common chat language in South Asia.
        "hi" to ("hai hain kya nahi nahin haan tum aap mera meri mujhe kaise kaisa kahan kaha acha accha achha yaar bhai kar " +
            "raha rahi rahe ho hu hoon main mai bhi kuch kyun kyu aur toh ka ki ke ko se tha thi bas abhi bahut bohot sab " +
            "ekdum wah arre kal aaj ji hum haal").words(),
    )

    private fun String.words() = split(' ').filter { it.isNotEmpty() }.toSet()

    /** Letters only for Vietnamese (tone marks on vowels, đ). */
    private const val VIETNAMESE = "ăâđơưạảấầẩẫậắằẳẵặẹẻẽếềểễệỉịọỏốồổỗộớờởỡợụủứừửữựỳỵỷỹ"

    /** The letters of [text], links left out. */
    private fun letters(text: String): String {
        var rest = text
        Links.find(text).asReversed().forEach { rest = rest.removeRange(it.start, it.end) }
        return rest.filter(Char::isLetter)
    }

    /** Something to translate at all: at least two letters once links are taken out (not emoji, numbers or a link). */
    fun hasWords(text: String) = letters(text).length >= 2

    fun looksForeign(text: String, target: String): Boolean {
        val letters = letters(text)
        if (letters.length < 2) return false
        val counts = HashMap<Script, Int>()
        var i = 0
        while (i < letters.length) {
            val cp = letters.codePointAt(i)
            val s = Script.of(cp)
            if (s != Script.COMMON && s != Script.INHERITED && s != Script.UNKNOWN) counts[s] = (counts[s] ?: 0) + 1
            i += Character.charCount(cp)
        }
        val main = counts.maxByOrNull { it.value }?.key ?: return false
        val mine = scriptsOf(target)
        if (target == "ja") {
            // Han alone is Chinese (Japanese nearly always has kana); kana present: Japanese.
            val kana = (counts[Script.HIRAGANA] ?: 0) + (counts[Script.KATAKANA] ?: 0)
            if (main == Script.HAN) return kana == 0 && (counts[Script.HAN] ?: 0) >= 4
        }
        if (target.startsWith("zh") && (counts[Script.HIRAGANA] != null || counts[Script.KATAKANA] != null)) return true
        if (main !in mine) {
            // Romanised Hindi / Urdu reads fine to Hindi and Urdu speakers.
            return !(main == Script.LATIN && wordsKey(target) == "hi" && bestLatin(text)?.first == "hi")
        }
        if (main != Script.LATIN) return false
        return latinForeign(text, wordsKey(target))
    }

    /** The Latin-script language whose common words [text] uses most, with its count; null when none. */
    private fun bestLatin(text: String): Pair<String, Int>? = latinScores(tokens(text)).maxByOrNull { it.value }?.toPair()?.takeIf { it.second > 0 }

    private fun tokens(text: String): List<String> {
        var rest = text
        Links.find(text).asReversed().forEach { rest = rest.removeRange(it.start, it.end) }
        return rest.lowercase(Locale.ROOT).replace('’', '\'').split(Regex("[^\\p{L}']+")).map { it.trim('\'') }.filter { it.isNotEmpty() }
    }

    private fun latinScores(words: List<String>): Map<String, Int> = WORDS.mapValues { (lang, set) ->
        words.count { it in set } + if (lang == "vi") words.count { w -> w.any { it in VIETNAMESE } } else 0
    }

    private fun latinForeign(text: String, target: String): Boolean {
        val words = tokens(text)
        if (words.isEmpty()) return false
        val scores = latinScores(words)
        val (best, score) = scores.maxByOrNull { it.value } ?: return false
        if (best == target || score == 0) return false
        val mine = scores[target] ?: 0
        if (score <= mine) return false
        // Two telling words, or a one- or two-word line made only of them ("Obrigado", "Kya scene").
        return score >= 2 || (words.size <= 2 && score == words.size)
    }
}
