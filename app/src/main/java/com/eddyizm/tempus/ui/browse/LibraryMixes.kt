package com.eddyizm.tempus.ui.browse

import com.eddyizm.tempus.subsonic.models.Child
import kotlin.random.Random

/** One generated playlist: what it groups and the songs in it. */
data class LibraryMix(val kind: Kind, val songIds: List<String>) {
    sealed interface Kind {
        data class ByLanguage(val language: Language) : Kind
        data class ByDecade(val decade: Int) : Kind
        data class ByGenre(val genre: String) : Kind
        data object Newest : Kind
        data object Unplayed : Kind
        data object MostPlayed : Kind
    }
}

/**
 * Sorts the whole library into playlists that need no tagging work: language (from the writing
 * system of the title and artist), decade, the main genres, newest additions, songs never played
 * and the most played. Groups smaller than [MIN_SONGS] are dropped.
 */
object LibraryMixes {
    const val MIN_SONGS = 8
    const val MAX_SONGS = 1000
    private const val MAX_GENRES = 8
    private const val NEWEST = 200
    private const val MOST_PLAYED = 100

    /**
     * The artist name usually tells the language best, but a title in kana or Hangul wins, so a
     * Japanese song by an artist written in kanji still lands in Japanese.
     */
    fun languageOf(song: Child): Language {
        val title = BrowseClassifier.languageOf(song.title)
        if (title == Language.JAPANESE || title == Language.KOREAN) return title
        val artist = BrowseClassifier.languageOf(song.artist)
        return if (artist != Language.OTHER) artist else title
    }

    fun genresOf(song: Child): List<String> =
        song.genre.orEmpty().split(';', '/', ',').map { it.trim() }.filter { it.isNotEmpty() }

    fun plan(songs: List<Child>): List<LibraryMix> {
        val unique = songs.filter { !it.isDir }.distinctBy { it.id }
        val mixes = ArrayList<LibraryMix>()
        fun add(kind: LibraryMix.Kind, group: List<Child>, shuffle: Boolean = true) {
            if (group.size < MIN_SONGS) return
            // A fixed seed per playlist keeps the order stable between runs.
            val ordered = if (shuffle) group.shuffled(Random(kind.toString().hashCode())) else group
            mixes += LibraryMix(kind, ordered.take(MAX_SONGS).map { it.id })
        }

        val byLanguage = unique.groupBy { languageOf(it) }
        Language.entries.filter { it != Language.OTHER }.forEach { add(LibraryMix.Kind.ByLanguage(it), byLanguage[it].orEmpty()) }

        unique.filter { (it.year ?: 0) >= 1900 }
            .groupBy { it.year!! / 10 * 10 }
            .toSortedMap(compareByDescending { it })
            .forEach { (decade, group) -> add(LibraryMix.Kind.ByDecade(decade), group) }

        val byGenre = LinkedHashMap<String, MutableList<Child>>()
        unique.forEach { song -> genresOf(song).forEach { byGenre.getOrPut(it) { ArrayList() } += song } }
        byGenre.entries.sortedByDescending { it.value.size }.take(MAX_GENRES)
            .forEach { (genre, group) -> add(LibraryMix.Kind.ByGenre(genre), group) }

        add(LibraryMix.Kind.Newest, unique.filter { it.created != null }.sortedByDescending { it.created }.take(NEWEST), shuffle = false)
        add(LibraryMix.Kind.Unplayed, unique.filter { (it.playCount ?: 0L) == 0L })
        add(LibraryMix.Kind.MostPlayed, unique.filter { (it.playCount ?: 0L) > 0L }.sortedByDescending { it.playCount }.take(MOST_PLAYED), shuffle = false)
        return mixes
    }
}
