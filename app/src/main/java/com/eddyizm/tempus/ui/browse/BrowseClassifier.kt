package com.eddyizm.tempus.ui.browse

import androidx.annotation.StringRes
import com.eddyizm.tempus.R

/** The language groups offered on the Discover page, guessed from the writing system of a name. */
enum class Language(@StringRes val label: Int) {
    CHINESE(R.string.browse_language_chinese),
    WESTERN(R.string.browse_language_western),
    JAPANESE(R.string.browse_language_japanese),
    KOREAN(R.string.browse_language_korean),
    OTHER(R.string.browse_language_other),
}

/**
 * Pure helpers that sort artists into language groups and alphabet sections.
 *
 * Servers carry no language tag, so the writing system of the artist name stands in for it:
 * kana means Japanese, Hangul means Korean, Han characters mean Chinese and Latin letters
 * Western. A Japanese name written only in kanji therefore lands in Chinese.
 */
object BrowseClassifier {
    @JvmStatic
    fun languageOf(name: String?): Language {
        if (name.isNullOrBlank()) return Language.OTHER
        var han = false
        var latin = false
        for (ch in name) {
            when (Character.UnicodeScript.of(ch.code)) {
                Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> return Language.JAPANESE
                Character.UnicodeScript.HANGUL -> return Language.KOREAN
                Character.UnicodeScript.HAN -> han = true
                Character.UnicodeScript.LATIN -> latin = true
                else -> Unit
            }
        }
        return when {
            han -> Language.CHINESE
            latin -> Language.WESTERN
            else -> Language.OTHER
        }
    }

    /**
     * The A–Z section for a name, with "#" for digits and anything else. [romanize] turns a
     * non-Latin name into Latin letters (pinyin on Android); it is a parameter so tests can run
     * without the platform transliterator.
     */
    @JvmStatic
    fun initialOf(name: String?, romanize: (String) -> String): Char {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isEmpty()) return '#'
        val first = trimmed.first()
        val latin = if (first.isAsciiLetter()) first else romanize(trimmed).trim().firstOrNull() ?: '#'
        val upper = latin.uppercaseChar()
        return if (upper in 'A'..'Z') upper else '#'
    }

    private fun Char.isAsciiLetter() = this in 'A'..'Z' || this in 'a'..'z'
}
