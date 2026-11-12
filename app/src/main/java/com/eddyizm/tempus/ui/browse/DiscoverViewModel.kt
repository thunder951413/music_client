package com.eddyizm.tempus.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eddyizm.tempus.App
import com.eddyizm.tempus.discovery.ExternalPlaylistRepository
import com.eddyizm.tempus.discovery.ImportedPlaylist
import com.eddyizm.tempus.subsonic.models.ArtistID3
import com.eddyizm.tempus.subsonic.models.Genre
import com.eddyizm.tempus.util.Preferences
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DiscoverViewModel : ViewModel() {
    data class State(
        val loading: Boolean = true,
        val failed: Boolean = false,
        val genres: List<Genre> = emptyList(),
        val decades: List<Int> = emptyList(),
        val languages: Map<Language, List<ArtistID3>> = emptyMap(),
        val cantoneseArtists: List<ArtistID3> = emptyList(),
        val sections: List<Pair<Char, List<ArtistID3>>> = emptyList(),
        val totalArtists: Int = 0,
        /** Bumped when chart matches change, so the chart cards re-read their library counts. */
        val chartsVersion: Int = 0,
        val dailyWishesCount: Int = 0,
        val dailySongsCount: Int = 0,
        val imports: List<ImportedPlaylist> = emptyList(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    init {
        readCachedDailySnapshot()
        load()
        refreshSyncCenter()
    }

    private fun readCachedDailySnapshot() {
        runCatching {
            val json = App.getInstance().preferences.getString("browse_daily_mix_today", null) ?: return
            val today = Gson().fromJson(json, DailyMixRepository.Today::class.java) ?: return
            _state.update {
                it.copy(
                    dailyWishesCount = today.wishes?.size ?: 0,
                    dailySongsCount = today.songs?.size ?: 0,
                )
            }
        }
    }

    /** Refreshes Sync Center counts (Daily Wishes + Imported External Playlists) in background. */
    fun refreshSyncCenter() {
        readCachedDailySnapshot()
        viewModelScope.launch {
            val importsDeferred = async(Dispatchers.IO) {
                runCatching { ExternalPlaylistRepository().imports() }.getOrNull()
            }
            val dailyDeferred = async(Dispatchers.IO) {
                if (_state.value.dailySongsCount > 0) null
                else runCatching { DailyMixRepository.today() }.getOrNull()
            }
            val loadedImports = importsDeferred.await()
            val loadedDaily = dailyDeferred.await()
            _state.update { current ->
                current.copy(
                    imports = loadedImports ?: current.imports,
                    dailyWishesCount = loadedDaily?.wishes?.size ?: current.dailyWishesCount,
                    dailySongsCount = loadedDaily?.songs?.size ?: current.dailySongsCount,
                )
            }
        }
    }

    /** Reuses the last result for this server for a while; [force] (pull to refresh) reloads. */
    fun load(force: Boolean = false) {
        if (force) refreshSyncCenter()
        val cached = if (force) null else Cache.fresh()
        if (cached != null) {
            _state.update { cur ->
                cached.copy(
                    decades = cur.decades,
                    chartsVersion = cur.chartsVersion,
                    dailyWishesCount = cur.dailyWishesCount,
                    dailySongsCount = cur.dailySongsCount,
                    imports = cur.imports,
                )
            }
            return
        }
        _state.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            runCatching {
                val genres = async { BrowseRepository.genres() }
                val artists = async { BrowseRepository.artists() }
                val list = artists.await()
                val (languages, cantonese, sections) = withContext(Dispatchers.Default) {
                    val byLanguage = list.groupBy { BrowseClassifier.languageOf(it.name) }
                    val chinesePool = byLanguage[Language.CHINESE].orEmpty()
                    val matchedCanto = list.filter { isLikelyCantoneseArtist(it.name) }
                    val cantoFinal = matchedCanto.ifEmpty { chinesePool }
                    val byInitial = list
                        .groupBy { BrowseRepository.initialOf(it.name) }
                        .toSortedMap(compareBy { if (it == '#') '~' else it })
                        .map { (letter, group) -> letter to group.sortedBy { it.name?.lowercase() } }
                    Triple(byLanguage, cantoFinal, byInitial)
                }
                val loadedGenres = genres.await()
                _state.update { cur ->
                    val next = cur.copy(
                        loading = false,
                        failed = false,
                        genres = loadedGenres,
                        languages = languages,
                        cantoneseArtists = cantonese,
                        sections = sections,
                        totalArtists = list.size,
                    )
                    Cache.store(next)
                    next
                }
            }.onFailure {
                if (it is CancellationException) throw it
                _state.update { cur -> cur.copy(loading = false, failed = true) }
            }
        }
    }

    /** Survives tab switches, which recreate this view model; keyed by server and music folder. */
    private object Cache {
        private const val TTL_MS = 15 * 60 * 1000L
        private var key: String? = null
        private var at = 0L
        private var state: State? = null

        private fun scope() = "${Preferences.getServerId()}|${Preferences.getActiveMusicFolderId()}"

        fun fresh(): State? =
            state?.takeIf { key == scope() && System.currentTimeMillis() - at < TTL_MS }

        fun store(value: State) {
            key = scope()
            at = System.currentTimeMillis()
            state = value
        }
    }

    fun chartsMatched() {
        _state.update { it.copy(chartsVersion = it.chartsVersion + 1) }
    }

    fun setDecades(decades: List<Int>?) {
        _state.update { it.copy(decades = decades.orEmpty().sortedDescending()) }
    }

    companion object {
        private val CANTONESE_CUES = setOf(
            "陈奕迅", "张国荣", "梅艳芳", "王菲", "Beyond", "BEYOND", "黄家驹", "谭咏麟", "张学友",
            "刘德华", "黎明", "郭富城", "林子祥", "叶蒨文", "陈慧娴", "郑秀文", "杨千嬅", "容祖儿",
            "李克勤", "张敬轩", "谢安琪", "古巨基", "麦浚龙", "林家谦", "方皓玟", "卫兰", "侧田",
            "许冠杰", "陈百强", "罗文", "关淑怡", "周慧敏", "彭羚", "卢冠廷", "达明一派", "黄耀明",
            "草蜢", "Twins", "TWINS", "谢霆锋", "陈小春", "郑伊健", "梁汉文", "苏永康", "许志安",
            "薛凯琪", "周柏豪", "连诗雅", "郑欣宜", "陈蕾", "岑宁儿", "林奕匡", "冯允谦", "张天赋",
            "MC 张天赋", "MC 張天賦", "姜涛", "柳应廷", "吕爵安", "卢瀚霆", "MIRROR", "Dear Jane",
            "RubberBand", "Supper Moment", "C AllStar", "ToNick", "Kolor", "LMF", "软硬天师",
            "陈慧琳", "梁咏琪", "莫文蔚", "林忆莲", "叶德娴", "甄妮", "徐小凤", "汪明荃", "郑少秋",
            "关正杰", "区瑞强", "蔡枫华", "吕方", "杜德伟", "王杰", "太极乐队", "温拿乐队",
            "陳奕迅", "張國榮", "梅艷芳", "譚詠麟", "張學友", "劉德華", "鄭秀文", "楊千嬅", "容祖兒",
            "陳慧嫻", "張敬軒", "謝安琪", "麥浚龍", "林家謙", "衛蘭", "許冠傑", "陳百強", "羅文",
            "關淑怡", "盧冠廷", "達明一派", "黃耀明", "謝霆鋒", "陳小春", "鄭伊健", "梁漢文", "蘇永康",
            "許志安", "薛凱琪", "連詩雅", "鄭欣宜", "陳蕾", "林憶蓮", "陳慧琳", "梁詠琪",
            "Eason Chan", "Leslie Cheung", "Anita Mui", "Jacky Cheung", "Andy Lau", "Leon Lai",
            "Aaron Kwok", "Sammi Cheng", "Miriam Yeung", "Joey Yung", "Hacken Lee", "Hins Cheung",
            "Kay Tse", "Terence Lam", "Juno Mak", "Janice Vidal", "Justin Lo", "Priscilla Chan",
            "Danny Chan", "George Lam", "Sally Yeh", "Alan Tam", "Samuel Hui",
        )

        private fun isLikelyCantoneseArtist(name: String?): Boolean {
            if (name.isNullOrBlank()) return false
            val trimmed = name.trim()
            if (trimmed in CANTONESE_CUES) return true
            return CANTONESE_CUES.any { cue ->
                cue.length >= 2 && trimmed.contains(cue, ignoreCase = true)
            }
        }
    }
}
