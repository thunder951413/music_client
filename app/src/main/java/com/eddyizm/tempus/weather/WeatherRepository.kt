package com.eddyizm.tempus.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.annotation.Keep
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.eddyizm.tempus.App
import com.eddyizm.tempus.subsonic.models.Child
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToInt

enum class WeatherMood {
    CLEAR,
    CLOUDY,
    RAIN,
    COLD;

    companion object {
        fun fromRaw(raw: String?): WeatherMood? = when (raw?.trim()?.uppercase()) {
            "CLEAR" -> CLEAR
            "CLOUDY" -> CLOUDY
            "RAIN" -> RAIN
            "COLD" -> COLD
            else -> null
        }
    }
}

enum class WeatherLocationMode {
    AUTO,
    MANUAL;

    companion object {
        fun fromRaw(raw: String?): WeatherLocationMode =
            if (raw?.trim()?.uppercase() == "MANUAL") MANUAL else AUTO
    }
}

@Keep
data class WeatherSnapshot(
    @SerializedName("mood") val mood: WeatherMood,
    @SerializedName("conditionLabel") val conditionLabel: String,
    @SerializedName("tempC") val tempC: Int,
    @SerializedName("city") val city: String,
    @SerializedName("source") val source: String, // "gps" | "manual" | "fallback"
    @SerializedName("fallbackNotice") val fallbackNotice: String? = null,
    @SerializedName("updatedAtMs") val updatedAtMs: Long = System.currentTimeMillis(),
)

data class WeatherPreference(
    val mode: WeatherLocationMode = WeatherLocationMode.AUTO,
    val manualCity: String = "上海",
    val themeSync: Boolean = true,
    val moodOverride: WeatherMood? = null,
)

data class WeatherPaletteSpec(
    val mood: WeatherMood,
    val paletteName: String,
    val defaultCondition: String,
    val defaultTempC: Int,
    val atmosphereNote: String,
    val seedHex: String,
    val swatchColor: Color,
    // Editorial Dark
    val darkBg: Color,
    val darkSurface1: Color,
    val darkSurface2: Color,
    val darkSurface3: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkTextPrimary: Color,
    val darkTextSecondary: Color,
    val darkHairline: Color,
    val darkHeroWash: Color,
    val darkCanopyStart: Color,
    val darkCanopySecondary: Color,
    // AMOLED Pure Black (#000000 bg preserved)
    val amoledSurface1: Color,
    val amoledSurface2: Color,
    val amoledSurface3: Color,
    // Warm Light
    val lightBg: Color,
    val lightSurface1: Color,
    val lightSurface2: Color,
    val lightSurface3: Color,
    val lightPrimary: Color,
    val lightOnPrimary: Color,
    val lightTextPrimary: Color,
    val lightTextSecondary: Color,
    val lightHairline: Color,
    val lightHeroWash: Color,
    val lightCanopyStart: Color,
    val lightCanopySecondary: Color,
)

/**
 * Keyless Open-Meteo Weather & Atmospheric Theme Repository (100% compatible with `degoogled` builds).
 * Strategy: Auto-Location First (coarse `LocationManager`) -> Transparent Manual City Fallback.
 */
object WeatherRepository {
    private const val PREF_MODE = "editorial_weather_mode"
    private const val PREF_CITY = "editorial_weather_city"
    private const val PREF_THEME_SYNC = "editorial_weather_theme_sync"
    private const val PREF_MOOD_OVERRIDE = "editorial_weather_mood_override"
    private const val PREF_LAST_SNAPSHOT = "editorial_weather_last_snapshot"
    private const val TTL_MS = 30L * 60_000L

    val QUICK_CITIES = listOf("上海", "北京", "广州", "深圳", "成都", "哈尔滨", "三亚")

    private val PRESET_COORDS = mapOf(
        "上海" to Triple(31.2304, 121.4737, "上海"),
        "shanghai" to Triple(31.2304, 121.4737, "上海"),
        "北京" to Triple(39.9042, 116.4074, "北京"),
        "beijing" to Triple(39.9042, 116.4074, "北京"),
        "广州" to Triple(23.1291, 113.2644, "广州"),
        "guangzhou" to Triple(23.1291, 113.2644, "广州"),
        "深圳" to Triple(22.5431, 114.0579, "深圳"),
        "shenzhen" to Triple(22.5431, 114.0579, "深圳"),
        "杭州" to Triple(30.2741, 120.1551, "杭州"),
        "hangzhou" to Triple(30.2741, 120.1551, "杭州"),
        "成都" to Triple(30.5728, 104.0668, "成都"),
        "chengdu" to Triple(30.5728, 104.0668, "成都"),
        "武汉" to Triple(30.5928, 114.3055, "武汉"),
        "南京" to Triple(32.0603, 118.7969, "南京"),
        "重庆" to Triple(29.5630, 106.5516, "重庆"),
        "西安" to Triple(34.3416, 108.9398, "西安"),
        "香港" to Triple(22.3193, 114.1694, "香港"),
        "台北" to Triple(25.0330, 121.5654, "台北"),
        "东京" to Triple(35.6762, 139.6503, "东京"),
        "哈尔滨" to Triple(45.8038, 126.5349, "哈尔滨"),
        "harbin" to Triple(45.8038, 126.5349, "哈尔滨"),
        "三亚" to Triple(18.2528, 109.5119, "三亚"),
        "sanya" to Triple(18.2528, 109.5119, "三亚"),
        "贵阳" to Triple(26.6470, 106.6302, "贵阳"),
        "guiyang" to Triple(26.6470, 106.6302, "贵阳"),
    )

    private val PALETTES: Map<WeatherMood, WeatherPaletteSpec> = mapOf(
        WeatherMood.CLEAR to WeatherPaletteSpec(
            mood = WeatherMood.CLEAR,
            paletteName = "晴光琥珀",
            defaultCondition = "晴朗",
            defaultTempC = 24,
            atmosphereNote = "暖金斜阳与胡桃木底色，偏重明快律动与都会/华语流行",
            seedHex = "#DDA959",
            swatchColor = Color(0xFFDDA959),
            darkBg = Color(0xFF110D09),
            darkSurface1 = Color(0xFF1A1510),
            darkSurface2 = Color(0xFF241D16),
            darkSurface3 = Color(0xFF30271D),
            darkPrimary = Color(0xFFDDA959),
            darkOnPrimary = Color(0xFF1C1306),
            darkTextPrimary = Color(0xFFF5EFE4),
            darkTextSecondary = Color(0xFFAEA394),
            darkHairline = Color(0x17FFECC8),
            darkHeroWash = Color(0xFF3A2916),
            darkCanopyStart = Color(0x40DDA959),
            darkCanopySecondary = Color(0x22C47A34),
            amoledSurface1 = Color(0xFF110D09),
            amoledSurface2 = Color(0xFF1A140E),
            amoledSurface3 = Color(0xFF251D14),
            lightBg = Color(0xFFF8F3E7),
            lightSurface1 = Color(0xFFF1E9D7),
            lightSurface2 = Color(0xFFE8DEC6),
            lightSurface3 = Color(0xFFDDD0B4),
            lightPrimary = Color(0xFF9C6621),
            lightOnPrimary = Color(0xFFFFFDF8),
            lightTextPrimary = Color(0xFF1F1912),
            lightTextSecondary = Color(0xFF615546),
            lightHairline = Color(0x182D2010),
            lightHeroWash = Color(0xFFEDE0C6),
            lightCanopyStart = Color(0x3BDA9E44),
            lightCanopySecondary = Color(0x1AD6923E),
        ),
        WeatherMood.CLOUDY to WeatherPaletteSpec(
            mood = WeatherMood.CLOUDY,
            paletteName = "云霭暖锡",
            defaultCondition = "多云",
            defaultTempC = 19,
            atmosphereNote = "柔雾香槟锡与羊绒灰底，均衡铺陈耐听爵士与R&B",
            seedHex = "#C2B097",
            swatchColor = Color(0xFFC2B097),
            darkBg = Color(0xFF0E0E10),
            darkSurface1 = Color(0xFF16171A),
            darkSurface2 = Color(0xFF1E2024),
            darkSurface3 = Color(0xFF282B30),
            darkPrimary = Color(0xFFC2B097),
            darkOnPrimary = Color(0xFF17140F),
            darkTextPrimary = Color(0xFFEEECE8),
            darkTextSecondary = Color(0xFF9D9C98),
            darkHairline = Color(0x14EEECE6),
            darkHeroWash = Color(0xFF262629),
            darkCanopyStart = Color(0x32B8AE9E),
            darkCanopySecondary = Color(0x247C8696),
            amoledSurface1 = Color(0xFF0D0E10),
            amoledSurface2 = Color(0xFF16171A),
            amoledSurface3 = Color(0xFF202226),
            lightBg = Color(0xFFF2F1EE),
            lightSurface1 = Color(0xFFE8E7E3),
            lightSurface2 = Color(0xFFDDDCD6),
            lightSurface3 = Color(0xFFD0CEC7),
            lightPrimary = Color(0xFF665947),
            lightOnPrimary = Color(0xFFFAF9F6),
            lightTextPrimary = Color(0xFF1B1B19),
            lightTextSecondary = Color(0xFF585753),
            lightHairline = Color(0x171C1C1A),
            lightHeroWash = Color(0xFFE2E0DA),
            lightCanopyStart = Color(0x2E918A7E),
            lightCanopySecondary = Color(0x1F747C88),
        ),
        WeatherMood.RAIN to WeatherPaletteSpec(
            mood = WeatherMood.RAIN,
            paletteName = "听雨青松",
            defaultCondition = "小雨",
            defaultTempC = 16,
            atmosphereNote = "雨窗青瓷色与深松石板底，偏重慢板人声、萨克斯与氛围曲",
            seedHex = "#7FB3A6",
            swatchColor = Color(0xFF7FB3A6),
            darkBg = Color(0xFF0B0F11),
            darkSurface1 = Color(0xFF12181B),
            darkSurface2 = Color(0xFF192126),
            darkSurface3 = Color(0xFF222D33),
            darkPrimary = Color(0xFF7FB3A6),
            darkOnPrimary = Color(0xFF081714),
            darkTextPrimary = Color(0xFFEAF0F0),
            darkTextSecondary = Color(0xFF94A4A7),
            darkHairline = Color(0x17CEEBE8),
            darkHeroWash = Color(0xFF172B2D),
            darkCanopyStart = Color(0x405C9A92),
            darkCanopySecondary = Color(0x33386076),
            amoledSurface1 = Color(0xFF0A1012),
            amoledSurface2 = Color(0xFF121A1E),
            amoledSurface3 = Color(0xFF1A252A),
            lightBg = Color(0xFFECF2F1),
            lightSurface1 = Color(0xFFE1EAE9),
            lightSurface2 = Color(0xFFD4E0DF),
            lightSurface3 = Color(0xFFC5D4D3),
            lightPrimary = Color(0xFF29665D),
            lightOnPrimary = Color(0xFFF6FCFB),
            lightTextPrimary = Color(0xFF142022),
            lightTextSecondary = Color(0xFF495B5E),
            lightHairline = Color(0x17142A2C),
            lightHeroWash = Color(0xFFD5E5E3),
            lightCanopyStart = Color(0x33387C74),
            lightCanopySecondary = Color(0x24365C72),
        ),
        WeatherMood.COLD to WeatherPaletteSpec(
            mood = WeatherMood.COLD,
            paletteName = "霜雪银杉",
            defaultCondition = "晴冷",
            defaultTempC = 4,
            atmosphereNote = "冷杉冰蓝与霜银高光，偏重钢琴独奏、古典室内乐与高解析母带",
            seedHex = "#92B4D8",
            swatchColor = Color(0xFF92B4D8),
            darkBg = Color(0xFF0A0D13),
            darkSurface1 = Color(0xFF111620),
            darkSurface2 = Color(0xFF18202D),
            darkSurface3 = Color(0xFF212B3C),
            darkPrimary = Color(0xFF92B4D8),
            darkOnPrimary = Color(0xFF091320),
            darkTextPrimary = Color(0xFFECF1F9),
            darkTextSecondary = Color(0xFF94A2B6),
            darkHairline = Color(0x17D2E2F8),
            darkHeroWash = Color(0xFF182538),
            darkCanopyStart = Color(0x3D8AAED6),
            darkCanopySecondary = Color(0x22B0CCEE),
            amoledSurface1 = Color(0xFF0A0E15),
            amoledSurface2 = Color(0xFF121824),
            amoledSurface3 = Color(0xFF1B2333),
            lightBg = Color(0xFFEEF2F8),
            lightSurface1 = Color(0xFFE3E9F3),
            lightSurface2 = Color(0xFFD5DFED),
            lightSurface3 = Color(0xFFC5D2E4),
            lightPrimary = Color(0xFF315983),
            lightOnPrimary = Color(0xFFF6FAFF),
            lightTextPrimary = Color(0xFF141C27),
            lightTextSecondary = Color(0xFF4A586B),
            lightHairline = Color(0x17162438),
            lightHeroWash = Color(0xFFD7E3F2),
            lightCanopyStart = Color(0x334C7CB0),
            lightCanopySecondary = Color(0x24789CC6),
        ),
    )

    private val mutex = Mutex()
    private val gson = Gson()
    private val coordCache = HashMap<String, Pair<Long, WeatherSnapshot>>()

    private val _preferenceState = mutableStateOf(readStoredPreference())
    val preferenceState: State<WeatherPreference> get() = _preferenceState

    private val _liveSnapshotState = mutableStateOf(readStoredSnapshot())
    val liveSnapshotState: State<WeatherSnapshot?> get() = _liveSnapshotState

    private val _loadingState = mutableStateOf(false)
    val loadingState: State<Boolean> get() = _loadingState

    private val _errorState = mutableStateOf<String?>(null)
    val errorState: State<String?> get() = _errorState

    fun paletteFor(mood: WeatherMood): WeatherPaletteSpec =
        PALETTES[mood] ?: PALETTES.getValue(WeatherMood.CLOUDY)

    fun effectiveSnapshot(): WeatherSnapshot? {
        val pref = _preferenceState.value
        val live = _liveSnapshotState.value
        val override = pref.moodOverride
        if (override != null) {
            val spec = paletteFor(override)
            return WeatherSnapshot(
                mood = override,
                conditionLabel = spec.defaultCondition,
                tempC = live?.tempC ?: spec.defaultTempC,
                city = live?.city ?: pref.manualCity,
                source = live?.source ?: "manual",
                fallbackNotice = live?.fallbackNotice,
                updatedAtMs = System.currentTimeMillis(),
            )
        }
        return live
    }

    @JvmStatic
    fun activePaletteOrNull(): WeatherPaletteSpec? {
        val pref = _preferenceState.value
        if (!pref.themeSync) return null
        val snap = effectiveSnapshot() ?: return null
        return paletteFor(snap.mood)
    }

    @JvmStatic
    fun themeSignatureSuffix(): String {
        val spec = activePaletteOrNull() ?: return "WEATHER_OFF"
        return "WEATHER_${spec.mood.name}"
    }

    fun hasCoarseLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    fun updatePreference(next: WeatherPreference) {
        val normalizedCity = next.manualCity.trim().ifEmpty { "上海" }
        val clean = next.copy(manualCity = normalizedCity)
        _preferenceState.value = clean
        runCatching {
            App.getInstance().preferences.edit {
                putString(PREF_MODE, clean.mode.name)
                putString(PREF_CITY, clean.manualCity)
                putBoolean(PREF_THEME_SYNC, clean.themeSync)
                if (clean.moodOverride != null) {
                    putString(PREF_MOOD_OVERRIDE, clean.moodOverride.name)
                } else {
                    remove(PREF_MOOD_OVERRIDE)
                }
            }
        }
    }

    suspend fun refresh(forceNetwork: Boolean = false): WeatherSnapshot = withContext(Dispatchers.IO) {
        mutex.withLock {
            val pref = _preferenceState.value
            val cached = _liveSnapshotState.value
            if (!forceNetwork && cached != null && System.currentTimeMillis() - cached.updatedAtMs < TTL_MS) {
                if ((pref.mode == WeatherLocationMode.AUTO && cached.source == "gps") ||
                    (pref.mode == WeatherLocationMode.MANUAL && cached.city == pref.manualCity)
                ) {
                    return@withLock cached
                }
            }

            _loadingState.value = true
            _errorState.value = null
            try {
                val context = App.getContext()
                val resolved = if (pref.mode == WeatherLocationMode.AUTO) {
                    val loc = obtainCoarseLocation(context)
                    if (loc != null) {
                        runCatching {
                            fetchByCoords(loc.latitude, loc.longitude, cityHint = null, source = "gps")
                        }.getOrNull() ?: fetchManualFallback(pref.manualCity)
                    } else {
                        fetchManualFallback(pref.manualCity)
                    }
                } else {
                    fetchByCity(pref.manualCity, source = "manual", fallbackNotice = null)
                }
                _liveSnapshotState.value = resolved
                persistSnapshot(resolved)
                resolved
            } catch (e: Exception) {
                _errorState.value = e.message ?: "无法更新天气"
                val fallback = cached ?: WeatherSnapshot(
                    mood = WeatherMood.CLOUDY,
                    conditionLabel = "多云",
                    tempC = 18,
                    city = pref.manualCity,
                    source = "fallback",
                    fallbackNotice = "已启用离线气候声景",
                )
                _liveSnapshotState.value = fallback
                fallback
            } finally {
                _loadingState.value = false
            }
        }
    }

    private fun fetchManualFallback(manualCity: String): WeatherSnapshot {
        val notice = "定位未授权或不可用，已自动按「$manualCity」匹配天气"
        return runCatching {
            fetchByCity(manualCity, source = "fallback", fallbackNotice = notice)
        }.getOrElse {
            WeatherSnapshot(
                mood = WeatherMood.CLOUDY,
                conditionLabel = "多云",
                tempC = 18,
                city = manualCity,
                source = "fallback",
                fallbackNotice = "已启用离线默认气候声景",
            )
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun obtainCoarseLocation(context: Context): Location? {
        if (!hasCoarseLocationPermission(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
            LocationManager.GPS_PROVIDER,
        )
        val now = System.currentTimeMillis()
        var newestLastKnown: Location? = null
        for (p in providers) {
            val candidate = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
            if (newestLastKnown == null || candidate.time > newestLastKnown.time) {
                newestLastKnown = candidate
            }
        }
        if (newestLastKnown != null && now - newestLastKnown.time < 2L * 3600_000L) {
            return newestLastKnown
        }

        val activeProvider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { p -> runCatching { lm.isProviderEnabled(p) }.getOrDefault(false) }
            ?: return newestLastKnown

        val fresh = withTimeoutOrNull(4_500L) {
            suspendCancellableCoroutine<Location?> { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                runCatching {
                    lm.getCurrentLocation(
                        activeProvider,
                        signal,
                        ContextCompat.getMainExecutor(context),
                    ) { location ->
                        if (cont.isActive) cont.resume(location)
                    }
                }.onFailure {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
        return fresh ?: newestLastKnown
    }

    private fun fetchByCity(
        rawCity: String,
        source: String,
        fallbackNotice: String?,
    ): WeatherSnapshot {
        val clean = rawCity.trim().removeSuffix("市").ifEmpty { "上海" }
        val preset = PRESET_COORDS[clean] ?: PRESET_COORDS[clean.lowercase()]
        if (preset != null) {
            return fetchByCoords(
                lat = preset.first,
                lon = preset.second,
                cityHint = preset.third,
                source = source,
                fallbackNotice = fallbackNotice,
            )
        }

        val encoded = URLEncoder.encode(clean, "UTF-8")
        val geoJson = httpGet("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=zh&format=json")
        val root = JsonParser.parseString(geoJson).asJsonObject
        val results = root.getAsJsonArray("results")
        if (results == null || results.size() == 0) {
            throw IllegalArgumentException("未找到城市「$clean」，请尝试输入如 上海、北京、成都、哈尔滨")
        }
        val first = results[0].asJsonObject
        val lat = first.get("latitude").asDouble
        val lon = first.get("longitude").asDouble
        val resolvedName = first.get("name")?.takeIf { !it.isJsonNull }?.asString?.removeSuffix("市")?.takeIf { it.isNotBlank() } ?: clean
        return fetchByCoords(
            lat = lat,
            lon = lon,
            cityHint = resolvedName,
            source = source,
            fallbackNotice = fallbackNotice,
        )
    }

    private fun fetchByCoords(
        lat: Double,
        lon: Double,
        cityHint: String?,
        source: String,
        fallbackNotice: String? = null,
    ): WeatherSnapshot {
        val key = String.format(java.util.Locale.US, "%.2f,%.2f,%s", lat, lon, cityHint.orEmpty())
        val hit = coordCache[key]
        if (hit != null && hit.first > System.currentTimeMillis()) {
            return hit.second.copy(source = source, fallbackNotice = fallbackNotice)
        }

        val city = cityHint ?: runCatching {
            val revJson = httpGet(
                "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$lat&longitude=$lon&localityLanguage=zh",
            )
            val revObj = JsonParser.parseString(revJson).asJsonObject
            listOf("city", "locality", "principalSubdivision")
                .firstNotNullOfOrNull { field ->
                    revObj.get(field)?.takeIf { !it.isJsonNull }?.asString?.removeSuffix("市")?.trim()?.takeIf { it.isNotEmpty() }
                }
        }.getOrNull() ?: "当前位置"

        val snap = runCatching {
            val wxJson = httpGet(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&timezone=auto&forecast_days=1",
            )
            val current = JsonParser.parseString(wxJson).asJsonObject.getAsJsonObject("current")
            val tempC = current?.get("temperature_2m")?.takeIf { !it.isJsonNull }?.asDouble?.roundToInt() ?: 18
            val code = current?.get("weather_code")?.takeIf { !it.isJsonNull }?.asInt ?: 2
            val (mood, label) = classifyWmo(code, tempC)
            WeatherSnapshot(
                mood = mood,
                conditionLabel = label,
                tempC = tempC,
                city = city,
                source = source,
                fallbackNotice = fallbackNotice,
                updatedAtMs = System.currentTimeMillis(),
            )
        }.getOrElse {
            val fallbackTemp = when {
                lat > 36.0 -> 6
                lat < 24.0 -> 24
                else -> 18
            }
            val fallbackCode = if (abs((lat + lon).roundToInt()) % 3 == 0) 61 else if (lat > 36.0) 2 else 0
            val (mood, label) = classifyWmo(fallbackCode, fallbackTemp)
            WeatherSnapshot(
                mood = mood,
                conditionLabel = label,
                tempC = fallbackTemp,
                city = city,
                source = source,
                fallbackNotice = fallbackNotice,
                updatedAtMs = System.currentTimeMillis(),
            )
        }

        coordCache[key] = (System.currentTimeMillis() + TTL_MS) to snap
        return snap
    }

    private fun classifyWmo(code: Int, tempC: Int): Pair<WeatherMood, String> {
        if (code in 71..77 || code == 85 || code == 86) {
            return WeatherMood.COLD to "降雪"
        }
        if (code in 51..67 || code in 80..82 || code >= 95) {
            val label = when {
                code >= 95 -> "雷阵雨"
                code >= 63 || code >= 81 -> "中雨"
                else -> "小雨"
            }
            return WeatherMood.RAIN to label
        }
        if (code == 45 || code == 48) {
            return WeatherMood.RAIN to "薄雾"
        }
        if (tempC <= 8) {
            return WeatherMood.COLD to if (code <= 1) "晴冷" else "阴冷"
        }
        if (code == 0 || code == 1) {
            return WeatherMood.CLEAR to "晴朗"
        }
        return WeatherMood.CLOUDY to if (code == 2) "多云" else "阴天"
    }

    private fun httpGet(urlString: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3_800
            readTimeout = 3_800
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "TempusAndroid/1.3")
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code")
            }
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun readStoredPreference(): WeatherPreference {
        return runCatching {
            val prefs = App.getInstance().preferences
            val mode = WeatherLocationMode.fromRaw(prefs.getString(PREF_MODE, "AUTO"))
            val city = prefs.getString(PREF_CITY, "上海")?.trim()?.ifEmpty { "上海" } ?: "上海"
            val sync = prefs.getBoolean(PREF_THEME_SYNC, true)
            val override = WeatherMood.fromRaw(prefs.getString(PREF_MOOD_OVERRIDE, null))
            WeatherPreference(mode = mode, manualCity = city, themeSync = sync, moodOverride = override)
        }.getOrDefault(WeatherPreference())
    }

    private fun readStoredSnapshot(): WeatherSnapshot? {
        return runCatching {
            val raw = App.getInstance().preferences.getString(PREF_LAST_SNAPSHOT, null) ?: return null
            gson.fromJson(raw, WeatherSnapshot::class.java)
        }.getOrNull()
    }

    private fun persistSnapshot(snap: WeatherSnapshot) {
        runCatching {
            App.getInstance().preferences.edit {
                putString(PREF_LAST_SNAPSHOT, gson.toJson(snap))
            }
        }
    }

    /**
     * ~30% soft weather affinity bonus for both Daily Mix and Personal Roam.
     */
    fun scoreTrackForWeather(song: Child, mood: WeatherMood?): Int {
        if (mood == null) return 0
        val haystack = buildString {
            append(song.genre.orEmpty()).append(' ')
            append(song.title.orEmpty()).append(' ')
            append(song.album.orEmpty()).append(' ')
            append(song.artist.orEmpty())
        }.lowercase()
        val year = song.year ?: 2018
        val bitrate = song.bitrate ?: 320
        val duration = song.duration ?: 210

        return when (mood) {
            WeatherMood.RAIN -> {
                var bonus = 0
                if (haystack.containsAny("jazz", "爵士", "blues", "r&b", "soul", "bossa", "ballad", "慢", "雨", "夜", "rain", "night")) bonus += 26
                if (haystack.containsAny("ambient", "acoustic", "民谣", "folk", "piano", "钢琴", "soundtrack", "ost")) bonus += 18
                if (duration >= 240) bonus += 8
                bonus
            }
            WeatherMood.CLEAR -> {
                var bonus = 0
                if (haystack.containsAny("pop", "流行", "city pop", "funk", "disco", "indie", "rock", "摇滚", "dance", "晴", "夏", "sun")) bonus += 26
                if (year >= 2010 && duration in 150..275) bonus += 12
                bonus
            }
            WeatherMood.COLD -> {
                var bonus = 0
                if (haystack.containsAny("classical", "古典", "piano", "钢琴", "instrumental", "纯音乐", "ambient", "post-rock", "cello", "冬", "雪")) bonus += 28
                if (bitrate >= 850 || song.suffix?.equals("flac", ignoreCase = true) == true) bonus += 12
                if (year in 1960..2008) bonus += 10
                bonus
            }
            WeatherMood.CLOUDY -> {
                var bonus = 0
                if (haystack.containsAny("jazz", "r&b", "lo-fi", "indie", "独立", "soul", "folk", "民谣", "alternative")) bonus += 22
                if ((song.playCount ?: 0L) == 0L) bonus += 10
                bonus
            }
        }
    }

    private fun String.containsAny(vararg needles: String): Boolean =
        needles.any { this.contains(it) }

    /**
     * Deterministically orders a song list by natural calendar day (`LocalDate`) + ~30% weather style affinity,
     * while avoiding repeating yesterday's lead song.
     */
    fun applyDailyWeatherOrder(
        songs: List<Child>,
        date: LocalDate = LocalDate.now(),
        mood: WeatherMood? = effectiveSnapshot()?.mood,
    ): List<Child> {
        if (songs.size <= 1) return songs
        val yesterdaySeed = "${date.minusDays(1)}|lead".hashCode()
        val yesterdayLeadId = songs.minByOrNull { s ->
            abs((s.id.hashCode() xor yesterdaySeed))
        }?.id

        val daySeed = "${date}|${mood?.name ?: "BASE"}".hashCode()
        return songs.withIndex().sortedByDescending { (idx, song) ->
            val baseRankScore = (songs.size - idx) * 2
            val dailyJitter = Math.floorMod(song.id.hashCode() xor daySeed, 48)
            val weatherBonus = scoreTrackForWeather(song, mood)
            val cooldownPenalty = if (songs.size > 2 && song.id == yesterdayLeadId) -35 else 0
            baseRankScore + dailyJitter + weatherBonus + cooldownPenalty
        }.map { it.value }
    }

    /**
     * Daily + Weather coupled subtitle for Personal Roam (`私人漫游`) on Home.
     */
    fun dailyRoamSubtitle(
        date: LocalDate = LocalDate.now(),
        snap: WeatherSnapshot? = effectiveSnapshot(),
    ): String {
        val dayThemes = arrayOf(
            "红心与未拆封专辑",
            "常听热曲与跨年代相似",
            "近期余温与深藏曲目",
            "熟稔旋律与冷门宝藏",
        )
        val dayTheme = dayThemes[Math.floorMod(date.toEpochDay().toInt(), dayThemes.size)]
        if (snap == null) return dayTheme
        val weatherPrefix = when (snap.mood) {
            WeatherMood.RAIN -> "${snap.conditionLabel}慢板"
            WeatherMood.CLEAR -> "${snap.conditionLabel}律动"
            WeatherMood.COLD -> "${snap.conditionLabel}器乐"
            WeatherMood.CLOUDY -> "${snap.conditionLabel}漫步"
        }
        return "$weatherPrefix · $dayTheme"
    }
}
