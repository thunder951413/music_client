package com.eddyizm.tempus.ui.browse

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowseClassifierTest {
    @Test
    fun writingSystemPicksTheLanguage() {
        assertEquals(Language.CHINESE, BrowseClassifier.languageOf("周杰伦"))
        assertEquals(Language.JAPANESE, BrowseClassifier.languageOf("あいみょん"))
        assertEquals(Language.JAPANESE, BrowseClassifier.languageOf("米津玄師 ヨネヅ"))
        assertEquals(Language.KOREAN, BrowseClassifier.languageOf("아이유"))
        assertEquals(Language.WESTERN, BrowseClassifier.languageOf("A Fine Frenzy"))
        assertEquals(Language.CHINESE, BrowseClassifier.languageOf("S.H.E 组合"))
        assertEquals(Language.OTHER, BrowseClassifier.languageOf("123"))
        assertEquals(Language.OTHER, BrowseClassifier.languageOf(null))
    }

    @Test
    fun initialsUseLettersAndRomanizeOtherNames() {
        val pinyin = mapOf("周杰伦" to "zhou jie lun", "陈奕迅" to "chen yi xun")
        val romanize: (String) -> String = { pinyin[it] ?: it }
        assertEquals('A', BrowseClassifier.initialOf("adele", romanize))
        assertEquals('Z', BrowseClassifier.initialOf("周杰伦", romanize))
        assertEquals('C', BrowseClassifier.initialOf(" 陈奕迅", romanize))
        assertEquals('#', BrowseClassifier.initialOf("2Cellos", romanize))
        assertEquals('#', BrowseClassifier.initialOf("", romanize))
    }
}
