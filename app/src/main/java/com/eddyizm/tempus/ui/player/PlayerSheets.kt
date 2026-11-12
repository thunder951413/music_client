package com.eddyizm.tempus.ui.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.eddyizm.tempus.App
import com.eddyizm.tempus.R
import com.eddyizm.tempus.equalizer.Biquad
import com.eddyizm.tempus.equalizer.EqualizerManager
import com.eddyizm.tempus.equalizer.ParametricFilter
import com.eddyizm.tempus.equalizer.ParametricProfile
import com.eddyizm.tempus.lan.LanDiagnostics
import com.eddyizm.tempus.lan.LanPlaybackActivity
import com.eddyizm.tempus.lan.LanReceiverService
import com.eddyizm.tempus.lan.LanRemoteSession
import com.eddyizm.tempus.lan.LanRemoteState
import com.eddyizm.tempus.lan.LanWire
import com.eddyizm.tempus.service.BaseMediaService
import com.eddyizm.tempus.service.MediaService
import com.eddyizm.tempus.ui.activity.MainActivity
import com.eddyizm.tempus.ui.queue.rememberAppColorScheme
import com.eddyizm.tempus.upnp.UpnpRouteProvider
import com.eddyizm.tempus.util.DownloadUtil
import com.eddyizm.tempus.util.Flavors
import com.eddyizm.tempus.util.MusicUtil
import com.eddyizm.tempus.util.Preferences
import com.eddyizm.tempus.util.ReplayGainBundleUtil
import com.eddyizm.tempus.util.ReplayGainUtil
import com.eddyizm.tempus.util.SleepTimerManager
import com.google.common.util.concurrent.MoreExecutors
import java.text.DecimalFormat
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Editorial Warm Dark ("唱片店夜色") tokens for Player Bottom Sheets
private val SheetBg = Color(0xFF1E1B18)          // surface-2
private val CardBg = Color(0xFF161412)           // surface-1
private val PillBg = Color(0xFF28241F)           // surface-3
private val DeepBg = Color(0xFF0E0D0C)           // bg
private val Hairline = Color(0x14FFF0DC)         // 8% warm border
private val Ivory = Color(0xFFF2ECE3)            // primary text
private val IvorySecondary = Color(0xA3F2ECE3)   // 64% text
private val IvoryTertiary = Color(0x66F2ECE3)    // 40% text
private val StylusBrass = Color(0xFFCFA46A)      // sole primary accent
private val StylusBrassFill = Color(0x24CFA46A)
private val StylusBrassBorder = Color(0x88CFA46A)
private val MossGreen = Color(0xFF8DB596)
private val MossFill = Color(0x228DB596)
private val Terracotta = Color(0xFFD9785F)

private const val UNICODE_MINUS = "\u2212"
private const val DISCOVERY_SCAN_TIMEOUT_MS = 8_000L

private val TabularStyle = TextStyle(fontFeatureSettings = "tnum")

private val SIGNAL_SHEET_PREF_KEYS = setOf(
    "selected_equalizer",
    "equalizer_enabled",
    "equalizer_band_levels",
    "parametric_eq_profile",
    "parametric_eq_name",
    "replay_gain_mode",
    "loudness_preamp",
    "playback_speed",
    "skip_silence",
    Preferences.LAN_REMOTE_ENABLED,
    Preferences.UPNP_ENABLED,
)

private val OUTPUT_SHEET_PREF_KEYS = setOf(
    Preferences.LAN_REMOTE_ENABLED,
    Preferences.UPNP_ENABLED,
)

private data class BuiltinPreset(val id: String, val labelRes: Int, val levelsMilliBel: ShortArray)

private val BUILTIN_PRESETS = listOf(
    BuiltinPreset("vocal", R.string.editorial_eq_preset_vocal, shortArrayOf(-150, 150, 380, 220, -50)),
    BuiltinPreset("bass", R.string.editorial_eq_preset_bass, shortArrayOf(520, 360, 0, 100, 160)),
    BuiltinPreset("classical", R.string.editorial_eq_preset_classical, shortArrayOf(260, 100, -120, 220, 360)),
    BuiltinPreset("live", R.string.editorial_eq_preset_live, shortArrayOf(300, 140, 80, 260, 320)),
)

/**
 * Entry points and state summaries for Tempus's two Editorial Warm Dark player bottom sheets:
 * 1. [SignalAndSoundSheet] (5-node signal chain, interactive EQ curve, ReplayGain, speed, sleep timer)
 * 2. [OutputRouteSheet] (Local DAC/BT, LAN Tempus with Ed25519 verified shield, UPnP/DLNA, Cast)
 */
object PlayerSheets {

    private const val HOST_TAG = "EditorialPlayerSheetHost"

    data class OutputSummary(
        val label: String,
        val detail: String,
        val isRemote: Boolean,
        val iconRes: Int,
    )

    data class EqSummary(
        val badgeText: String,
        val nodeText: String,
        val isActive: Boolean,
        val isParametric: Boolean,
    )

    @JvmStatic
    fun showSignalAndSound(activity: FragmentActivity) {
        if (activity.isFinishing || activity.isDestroyed) return
        PlayerSheetHostFragment.present(activity, PlayerSheetHostFragment.MODE_SIGNAL)
    }

    @JvmStatic
    fun showOutputRoute(activity: FragmentActivity) {
        if (activity.isFinishing || activity.isDestroyed) return
        PlayerSheetHostFragment.present(activity, PlayerSheetHostFragment.MODE_OUTPUT)
    }

    @JvmStatic
    fun isLosslessCodec(codecUpper: String?): Boolean {
        if (codecUpper.isNullOrBlank()) return false
        val clean = codecUpper.uppercase(Locale.ROOT)
        return clean.contains("FLAC") ||
            clean.contains("ALAC") ||
            clean.contains("WAV") ||
            clean.contains("PCM") ||
            clean.contains("AIFF") ||
            clean.contains("APE") ||
            clean.contains("WV") ||
            clean.contains("DSD") ||
            clean.contains("DFF") ||
            clean.contains("DSF")
    }

    @JvmStatic
    fun isHiResAudio(bitDepth: Int, sampleRateHz: Int): Boolean =
        bitDepth > 16 || sampleRateHz > 48_000

    /** Formats signed dB values with typographic Unicode minus (`−4.2 dB` / `+3.0 dB` / `0 dB`). */
    @JvmStatic
    @JvmOverloads
    fun formatSignedDb(gainDb: Float, decimals: Int = 1): String {
        val threshold = if (decimals <= 0) 0.5f else 0.05f
        if (abs(gainDb) < threshold) return "0 dB"
        val pattern = if (decimals <= 0) "%.0f" else "%.${decimals}f"
        val mag = String.format(Locale.US, pattern, abs(gainDb))
        val sign = if (gainDb < 0f) UNICODE_MINUS else "+"
        return "$sign$mag dB"
    }

    @JvmStatic
    fun currentOutputSummary(context: Context): OutputSummary {
        if (LanRemoteSession.isActive()) {
            val state = LanRemoteSession.current()
            val name = state.name.ifBlank { context.getString(R.string.player_remote_output) }
            return OutputSummary(
                label = name,
                detail = if (state.connected) {
                    context.getString(R.string.editorial_output_lan_connected)
                } else {
                    context.getString(R.string.editorial_output_lan_connecting)
                },
                isRemote = true,
                iconRes = R.drawable.ic_lan_remote,
            )
        }
        val selectedRoute = runCatching { MediaRouter.getInstance(context).selectedRoute }.getOrNull()
        if (selectedRoute != null &&
            !selectedRoute.isDefault &&
            selectedRoute.playbackType == MediaRouter.RouteInfo.PLAYBACK_TYPE_REMOTE
        ) {
            val isUpnp = selectedRoute.supportsControlCategory(UpnpRouteProvider.CATEGORY_UPNP)
            return OutputSummary(
                label = selectedRoute.name.ifBlank { context.getString(R.string.player_remote_output) },
                detail = selectedRoute.description ?: "",
                isRemote = true,
                iconRes = if (isUpnp) R.drawable.ic_lan_remote else R.drawable.ic_speaker,
            )
        }
        val localDetail = describeLocalAudioDevice(context)
        return OutputSummary(
            label = context.getString(R.string.editorial_player_output_local),
            detail = localDetail,
            isRemote = false,
            iconRes = R.drawable.ic_speaker,
        )
    }

    @JvmStatic
    fun currentEqSummary(context: Context): EqSummary {
        val selected = Preferences.getSelectedEqualizer()
        val enabled = Preferences.isEqualizerEnabled()
        val peqRaw = Preferences.getParametricEqProfile()
        val peqName = Preferences.getParametricEqName()?.trim()?.takeIf { it.isNotEmpty() }

        return when {
            selected == 3 && !peqRaw.isNullOrBlank() -> {
                val cleanName = (peqName ?: context.getString(R.string.editorial_badge_eq_autoeq))
                    .removeSuffix(".txt")
                EqSummary(
                    badgeText = context.getString(R.string.editorial_badge_eq_active, cleanName),
                    nodeText = cleanName,
                    isActive = true,
                    isParametric = true,
                )
            }
            selected == 1 && enabled -> {
                val saved = Preferences.getEqualizerBandLevels(5)
                val matched = BUILTIN_PRESETS.firstOrNull { preset ->
                    saved.size >= preset.levelsMilliBel.size &&
                        preset.levelsMilliBel.indices.all { idx -> abs(saved[idx] - preset.levelsMilliBel[idx]) <= 40 }
                }
                val name = matched?.let { context.getString(it.labelRes) }
                    ?: context.getString(R.string.editorial_badge_eq_builtin)
                EqSummary(
                    badgeText = context.getString(R.string.editorial_badge_eq_active, name),
                    nodeText = name,
                    isActive = true,
                    isParametric = false,
                )
            }
            selected == 2 && enabled -> {
                val sys = context.getString(R.string.editorial_badge_eq_system)
                EqSummary(
                    badgeText = context.getString(R.string.editorial_badge_eq_active, sys),
                    nodeText = sys,
                    isActive = true,
                    isParametric = false,
                )
            }
            else -> EqSummary(
                badgeText = context.getString(R.string.editorial_badge_eq_flat),
                nodeText = context.getString(R.string.editorial_signal_val_bypass),
                isActive = false,
                isParametric = false,
            )
        }
    }

    /**
     * Queries Android 14+ active media routing (`getAudioDevicesForAttributes`) first so simultaneous
     * peripherals (e.g. wired dongle + paired Bluetooth) reflect the true active audio sink.
     */
    internal fun describeLocalAudioDevice(context: Context): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return context.getString(R.string.editorial_output_local_speaker)
        val mediaAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
        val routed = runCatching { am.getAudioDevicesForAttributes(mediaAttrs) }.getOrNull().orEmpty()
        val activeSink = routed.firstOrNull { isExternalSink(it) } ?: routed.firstOrNull()
        val candidate = activeSink ?: runCatching { am.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }
            .getOrNull()
            .orEmpty()
            .firstOrNull { isExternalSink(it) }

        return when (candidate?.type) {
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> {
                val prod = candidate.productName?.toString()?.takeIf { it.isNotBlank() && it != Build.MODEL }
                prod ?: context.getString(R.string.editorial_output_local_usb)
            }
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST -> {
                val prod = candidate.productName?.toString()?.takeIf { it.isNotBlank() && it != Build.MODEL }
                prod ?: context.getString(R.string.editorial_output_local_bt)
            }
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL -> context.getString(R.string.editorial_output_local_wired)
            else -> context.getString(R.string.editorial_output_local_speaker)
        }
    }

    private fun isExternalSink(dev: AudioDeviceInfo): Boolean =
        dev.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            dev.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            dev.type == AudioDeviceInfo.TYPE_USB_ACCESSORY ||
            dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            dev.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
            dev.type == AudioDeviceInfo.TYPE_BLE_BROADCAST ||
            dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            dev.type == AudioDeviceInfo.TYPE_LINE_ANALOG ||
            dev.type == AudioDeviceInfo.TYPE_LINE_DIGITAL

    @OptIn(UnstableApi::class)
    internal fun openFullEqualizerFromActivity(
        activity: FragmentActivity?,
        binder: BaseMediaService.LocalBinder?,
    ) {
        val hostActivity = activity ?: return
        if (hostActivity.isFinishing || hostActivity.isDestroyed) return
        if (LanRemoteSession.isActive()) {
            Toast.makeText(hostActivity, R.string.lan_receiver_audio_settings, Toast.LENGTH_LONG).show()
            return
        }
        if (Preferences.getSelectedEqualizer() == 0) {
            Preferences.setSelectedEqualizer("1")
            binder?.reloadEqualizer()
        }
        val mainActivity = hostActivity as? MainActivity
        val navController = mainActivity?.navController
            ?: (hostActivity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment)
                ?.navController
        if (navController != null) {
            val navOptions = NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(R.id.equalizerFragment, true)
                .build()
            runCatching { navController.navigate(R.id.equalizerFragment, null, navOptions) }
        }
        mainActivity?.collapseBottomSheetDelayed()
    }

    /** Headless fragment hosting Compose [ModalBottomSheet] with zero retained external references. */
    class PlayerSheetHostFragment : Fragment() {
        private var pendingUriCallback: ((Uri?) -> Unit)? = null
        private lateinit var openDocumentLauncher: ActivityResultLauncher<Array<String>>

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                val cb = pendingUriCallback
                pendingUriCallback = null
                cb?.invoke(uri)
            }
        }

        @OptIn(UnstableApi::class)
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            val initialMode = arguments?.getInt(ARG_MODE, MODE_SIGNAL) ?: MODE_SIGNAL
            return ComposeView(requireContext()).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    var activeMode by remember(initialMode) { mutableIntStateOf(initialMode) }
                    MaterialTheme(colorScheme = rememberAppColorScheme()) {
                        if (activeMode == MODE_OUTPUT) {
                            OutputRouteSheet(onDismiss = { dismissHost() })
                        } else {
                            SignalAndSoundSheet(
                                onDismiss = { dismissHost() },
                                onSwitchToOutputSheet = {
                                    arguments?.putInt(ARG_MODE, MODE_OUTPUT)
                                    activeMode = MODE_OUTPUT
                                },
                                onOpenFullEqualizer = { binder ->
                                    val act = activity
                                    dismissHost()
                                    openFullEqualizerFromActivity(act, binder)
                                },
                                onPickAutoEqDocument = { callback ->
                                    pendingUriCallback = callback
                                    openDocumentLauncher.launch(arrayOf("text/*", "*/*"))
                                }
                            )
                        }
                    }
                }
            }
        }

        private fun dismissHost() {
            if (!isAdded) return
            parentFragmentManager.beginTransaction()
                .remove(this)
                .commitAllowingStateLoss()
        }

        companion object {
            private const val ARG_MODE = "sheet_mode"
            const val MODE_SIGNAL = 1
            const val MODE_OUTPUT = 2

            fun present(activity: FragmentActivity, mode: Int) {
                val fm = activity.supportFragmentManager
                fm.findFragmentByTag(HOST_TAG)?.let { existing ->
                    fm.beginTransaction().remove(existing).commitAllowingStateLoss()
                }
                val host = PlayerSheetHostFragment().apply {
                    arguments = Bundle().apply { putInt(ARG_MODE, mode) }
                }
                fm.beginTransaction()
                    .add(android.R.id.content, host, HOST_TAG)
                    .commitAllowingStateLoss()
            }
        }
    }
}

// ============================================================================
// SHEET A: SIGNAL CHAIN & SOUND
// ============================================================================

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalAndSoundSheet(
    onDismiss: () -> Unit,
    onSwitchToOutputSheet: () -> Unit = {},
    onOpenFullEqualizer: (BaseMediaService.LocalBinder?) -> Unit = {},
    onPickAutoEqDocument: ((Uri?) -> Unit) -> Unit = {},
) {
    val appContext = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    var eqSectionY by remember { mutableIntStateOf(0) }
    var rgSectionY by remember { mutableIntStateOf(0) }

    fun dismissSmoothly(afterHide: () -> Unit = {}) {
        scope.launch {
            runCatching { sheetState.hide() }
            onDismiss()
            afterHide()
        }
    }

    // Bind to MediaService for live EqualizerManager + ExoPlayer + reloadEqualizer()
    var serviceBinder by remember { mutableStateOf<BaseMediaService.LocalBinder?>(null) }
    var browser by remember { mutableStateOf<MediaBrowser?>(null) }
    var mediaTick by remember { mutableIntStateOf(0) }

    DisposableEffect(appContext) {
        var bound = false
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                serviceBinder = service as? BaseMediaService.LocalBinder
                mediaTick++
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                serviceBinder = null
            }
        }
        runCatching {
            val intent = Intent(appContext, MediaService::class.java).apply {
                action = BaseMediaService.ACTION_BIND_EQUALIZER
            }
            bound = appContext.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        }

        val browserFuture = MediaBrowser.Builder(
            appContext,
            SessionToken(appContext, ComponentName(appContext, MediaService::class.java))
        ).buildAsync()
        val playerListener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                mediaTick++
            }

            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                mediaTick++
            }
        }
        browserFuture.addListener({
            runCatching {
                val b = browserFuture.get()
                b.addListener(playerListener)
                browser = b
                mediaTick++
            }
        }, MoreExecutors.directExecutor())

        onDispose {
            browser?.removeListener(playerListener)
            MediaBrowser.releaseFuture(browserFuture)
            if (bound) {
                runCatching { appContext.unbindService(conn) }
            }
        }
    }

    // Observe filtered Preferences + LAN state (no idle 1Hz poller when sleep timer is off!)
    var prefTick by remember { mutableIntStateOf(0) }
    var lanState by remember { mutableStateOf(LanRemoteSession.current()) }
    var sleepTick by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val prefs = App.getInstance().preferences
        val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in SIGNAL_SHEET_PREF_KEYS) {
                prefTick++
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefListener)

        val lanObserver = Observer<LanRemoteState> { next ->
            lanState = next
        }
        LanRemoteSession.state().observeForever(lanObserver)

        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
            LanRemoteSession.state().removeObserver(lanObserver)
        }
    }

    val sleepMgr = SleepTimerManager.getInstance()
    val timerActive = remember(sleepTick, lanState) {
        if (lanState.active) lanState.timerActive else sleepMgr.isActive
    }
    LaunchedEffect(timerActive) {
        while (timerActive) {
            delay(1_000L)
            sleepTick++
        }
    }

    // Live EQ state — decoupled from prefTick during active drag so pointer gestures never reset!
    var isDraggingEq by remember { mutableStateOf(false) }
    val eqManager: EqualizerManager? = remember(serviceBinder, prefTick) {
        serviceBinder?.getEqualizerManager()
    }
    var selectedEq by remember { mutableIntStateOf(Preferences.getSelectedEqualizer()) }
    var eqEnabled by remember { mutableStateOf(Preferences.isEqualizerEnabled()) }
    var peqRaw by remember { mutableStateOf(Preferences.getParametricEqProfile()) }
    var peqName by remember { mutableStateOf(Preferences.getParametricEqName()) }

    val bandCount = remember(eqManager) {
        val n = eqManager?.getNumberOfBands()?.toInt() ?: 0
        if (n > 0) n else 5
    }
    val bandRange = remember(eqManager) {
        eqManager?.getBandLevelRange() ?: shortArrayOf(-1500, 1500)
    }
    val minBandDb = (bandRange[0] / 100f).coerceAtMost(-12f)
    val maxBandDb = (bandRange[1] / 100f).coerceAtLeast(12f)

    val bandFreqsHz = remember(eqManager, bandCount) {
        IntArray(bandCount) { idx ->
            eqManager?.getCenterFreq(idx.toShort()) ?: when (idx) {
                0 -> 60
                1 -> 230
                2 -> 910
                3 -> 3600
                else -> 14000
            }
        }
    }

    val bandLevelsDb = remember(eqManager, bandCount) {
        val saved = Preferences.getEqualizerBandLevels(bandCount.toShort())
        mutableStateListOf<Float>().apply {
            for (i in 0 until bandCount) {
                val mb = saved.getOrNull(i) ?: eqManager?.getBandLevel(i.toShort()) ?: 0
                add((mb / 100f).coerceIn(minBandDb, maxBandDb))
            }
        }
    }

    var peqPreampDb by remember { mutableDoubleStateOf(0.0) }
    val peqFilters = remember { mutableStateListOf<ParametricFilter>() }

    LaunchedEffect(prefTick, eqManager, bandCount) {
        if (isDraggingEq) return@LaunchedEffect
        selectedEq = Preferences.getSelectedEqualizer()
        eqEnabled = Preferences.isEqualizerEnabled()
        val latestRaw = Preferences.getParametricEqProfile()
        peqRaw = latestRaw
        peqName = Preferences.getParametricEqName()

        val saved = Preferences.getEqualizerBandLevels(bandCount.toShort())
        if (bandLevelsDb.size != bandCount) {
            bandLevelsDb.clear()
            for (i in 0 until bandCount) {
                val mb = saved.getOrNull(i) ?: eqManager?.getBandLevel(i.toShort()) ?: 0
                bandLevelsDb.add((mb / 100f).coerceIn(minBandDb, maxBandDb))
            }
        } else {
            for (i in 0 until bandCount) {
                val mb = saved.getOrNull(i) ?: eqManager?.getBandLevel(i.toShort()) ?: 0
                bandLevelsDb[i] = (mb / 100f).coerceIn(minBandDb, maxBandDb)
            }
        }

        val parsed = latestRaw?.let { ParametricProfile.parse(it) }
        peqFilters.clear()
        if (parsed != null) {
            peqPreampDb = parsed.preampDb
            peqFilters.addAll(parsed.filters)
        } else {
            peqPreampDb = 0.0
        }
    }

    val hasParametricProfile = peqFilters.isNotEmpty()
    val defaultAutoEqLabel = stringResource(R.string.editorial_badge_eq_autoeq)

    // Non-blocking IO AutoEq file import
    val handleAutoEqUri: (Uri?) -> Unit = { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    readAutoEqImport(appContext, uri)
                }
                if (outcome == null) {
                    Toast.makeText(appContext, R.string.editorial_eq_import_failed, Toast.LENGTH_LONG).show()
                } else {
                    Preferences.setParametricEqProfile(outcome.rawText, outcome.displayName)
                    Preferences.setSelectedEqualizer("3")
                    Preferences.setEqualizerEnabled(true)
                    selectedEq = 3
                    eqEnabled = true
                    peqRaw = outcome.rawText
                    peqName = outcome.displayName
                    peqPreampDb = outcome.profile.preampDb
                    peqFilters.clear()
                    peqFilters.addAll(outcome.profile.filters)
                    serviceBinder?.reloadEqualizer()
                    showAutoEqImportSuccessToast(appContext, outcome.displayName)
                }
            }
        }
    }

    // Read ReplayGain, Speed, SkipSilence
    var rgMode by remember(prefTick) {
        mutableStateOf(Preferences.getReplayGainMode() ?: "disabled")
    }
    var preampDb by remember(prefTick) {
        mutableFloatStateOf(Preferences.getLoudnessPreamp())
    }
    var playbackSpeed by remember(prefTick, lanState) {
        mutableFloatStateOf(if (lanState.active) lanState.speed else Preferences.getPlaybackSpeed())
    }
    var skipSilence by remember(prefTick) {
        mutableStateOf(Preferences.isSkipSilenceMode())
    }

    // Build honest 5-node signal chain labels from live state
    val sourceNodeInfo = remember(browser, lanState, mediaTick) {
        resolveSourceSignalNode(appContext, browser, lanState)
    }
    val outputSummary = remember(lanState, mediaTick, prefTick) {
        PlayerSheets.currentOutputSummary(appContext)
    }
    val decoderLabel = remember(outputSummary.isRemote, browser, mediaTick) {
        resolveDecoderSignalLabel(appContext, browser, outputSummary.isRemote)
    }
    val rgNodeLabel = remember(rgMode, preampDb, browser, mediaTick) {
        resolveReplayGainShortLabel(appContext, rgMode, browser?.mediaMetadata?.extras)
    }
    val eqSummary = remember(selectedEq, eqEnabled, peqRaw, peqName, prefTick) {
        PlayerSheets.currentEqSummary(appContext)
    }

    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBg,
        contentColor = Ivory,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { EditorialDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(bottom = navBottomPadding + 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Sheet Title
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = stringResource(R.string.editorial_signal_sheet_title),
                    color = Ivory,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.editorial_signal_sheet_subtitle),
                    color = IvorySecondary,
                    fontSize = 13.sp,
                )
            }

            // 1. Clickable 5-Node Horizontal Signal Chain with 1.dp Stylus Brass connectors
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBg)
                    .border(1.dp, Hairline, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.editorial_signal_chain_header),
                    color = IvoryTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.1.sp,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SignalChainNode(
                        stage = stringResource(R.string.editorial_signal_node_source),
                        value = sourceNodeInfo.codec,
                        subValue = sourceNodeInfo.rateDetail,
                        accent = if (sourceNodeInfo.isHiRes) StylusBrass else Ivory,
                        highlighted = sourceNodeInfo.isHiRes || sourceNodeInfo.isLossless,
                        brassBorder = sourceNodeInfo.isHiRes,
                        onClick = { scope.launch { scrollState.animateScrollTo(0) } },
                    )
                    SignalConnector(active = true)
                    SignalChainNode(
                        stage = stringResource(R.string.editorial_signal_node_decoder),
                        value = decoderLabel,
                        subValue = null,
                        accent = Ivory,
                        highlighted = decoderLabel == "FFmpeg" || decoderLabel == "PCM",
                        brassBorder = false,
                        onClick = { scope.launch { scrollState.animateScrollTo(0) } },
                    )
                    val rgActive = rgMode != "disabled"
                    SignalConnector(active = rgActive)
                    SignalChainNode(
                        stage = stringResource(R.string.editorial_signal_node_rg),
                        value = rgNodeLabel,
                        subValue = if (rgActive && preampDb != 0f) {
                            PlayerSheets.formatSignedDb(preampDb, decimals = 0)
                        } else null,
                        accent = if (rgActive) Ivory else IvorySecondary,
                        highlighted = rgActive,
                        brassBorder = false,
                        onClick = { scope.launch { scrollState.animateScrollTo(rgSectionY) } },
                    )
                    SignalConnector(active = eqSummary.isActive)
                    SignalChainNode(
                        stage = stringResource(R.string.editorial_signal_node_eq),
                        value = eqSummary.nodeText,
                        subValue = null,
                        accent = when {
                            eqSummary.isParametric && eqSummary.isActive -> MossGreen
                            eqSummary.isActive -> StylusBrass
                            else -> IvorySecondary
                        },
                        highlighted = eqSummary.isActive,
                        brassBorder = eqSummary.isActive && !eqSummary.isParametric,
                        onClick = { scope.launch { scrollState.animateScrollTo(eqSectionY) } },
                    )
                    SignalConnector(active = outputSummary.isRemote)
                    SignalChainNode(
                        stage = stringResource(R.string.editorial_signal_node_output),
                        value = outputSummary.label,
                        subValue = outputSummary.detail.takeIf { it.isNotBlank() }?.take(14),
                        accent = if (outputSummary.isRemote) StylusBrass else Ivory,
                        highlighted = outputSummary.isRemote,
                        brassBorder = outputSummary.isRemote,
                        onClick = onSwitchToOutputSheet,
                    )
                }
            }

            // 2. Interactive EQ Curve & Presets (Built-in 5-Band + Draggable Parametric AutoEq)
            val isEqSwitchOn = (selectedEq == 3 && hasParametricProfile) ||
                (selectedEq == 1 && eqEnabled) ||
                (selectedEq == 2 && eqEnabled)
            val isParametricMode = selectedEq == 3 && hasParametricProfile

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        eqSectionY = coords.positionInParent().y.roundToInt()
                    }
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBg)
                    .border(1.dp, Hairline, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.editorial_eq_section_title),
                            color = Ivory,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val sub = if (isParametricMode) {
                            stringResource(
                                R.string.editorial_eq_drag_autoeq_hint,
                                peqFilters.size,
                                PlayerSheets.formatSignedDb(peqPreampDb.toFloat(), decimals = 1)
                            )
                        } else {
                            stringResource(R.string.editorial_eq_drag_hint)
                        }
                        Text(
                            text = sub,
                            color = IvorySecondary,
                            fontSize = 11.sp,
                            style = TabularStyle,
                        )
                    }
                    Switch(
                        checked = isEqSwitchOn,
                        onCheckedChange = { turnOn ->
                            if (turnOn) {
                                if (hasParametricProfile && selectedEq == 0) {
                                    Preferences.setSelectedEqualizer("3")
                                    selectedEq = 3
                                } else if (selectedEq == 0) {
                                    Preferences.setSelectedEqualizer("1")
                                    selectedEq = 1
                                }
                                Preferences.setEqualizerEnabled(true)
                                eqEnabled = true
                                eqManager?.setEnabled(true)
                                serviceBinder?.reloadEqualizer()
                            } else {
                                Preferences.setEqualizerEnabled(false)
                                if (selectedEq == 3) {
                                    Preferences.setSelectedEqualizer("0")
                                    selectedEq = 0
                                }
                                eqEnabled = false
                                eqManager?.setEnabled(false)
                                serviceBinder?.reloadEqualizer()
                            }
                        },
                        colors = editorialSwitchColors(),
                    )
                }

                // Frequency Response Canvas (60fps in-memory drag, commit-once on release)
                EqFrequencyResponseCanvas(
                    isActive = isEqSwitchOn,
                    isParametricMode = isParametricMode,
                    peqPreampDb = peqPreampDb,
                    peqFilters = peqFilters,
                    bandLevelsDb = bandLevelsDb,
                    bandFreqsHz = bandFreqsHz,
                    minDb = if (isParametricMode) -18f else minBandDb,
                    maxDb = if (isParametricMode) 18f else maxBandDb,
                    onDragStateChanged = { dragging -> isDraggingEq = dragging },
                    onBuiltinBandPreview = { bandIdx, newDb ->
                        if (bandIdx in bandLevelsDb.indices) {
                            val clamped = newDb.coerceIn(minBandDb, maxBandDb)
                            bandLevelsDb[bandIdx] = clamped
                            if (selectedEq != 1) {
                                selectedEq = 1
                            }
                            if (!eqEnabled) {
                                eqEnabled = true
                                eqManager?.setEnabled(true)
                            }
                            val milliBel = (clamped * 100f).roundToInt().toShort()
                            eqManager?.setBandLevel(bandIdx.toShort(), milliBel)
                        }
                    },
                    onBuiltinBandCommit = {
                        val toSave = ShortArray(bandLevelsDb.size) { i ->
                            (bandLevelsDb[i] * 100f).roundToInt().toShort()
                        }
                        Preferences.setEqualizerBandLevels(toSave)
                        if (Preferences.getSelectedEqualizer() != 1) {
                            Preferences.setSelectedEqualizer("1")
                            serviceBinder?.reloadEqualizer()
                        }
                        if (!Preferences.isEqualizerEnabled()) {
                            Preferences.setEqualizerEnabled(true)
                        }
                    },
                    onParametricFilterPreview = { filterIdx, newCurveDb ->
                        if (filterIdx in peqFilters.indices) {
                            val current = peqFilters[filterIdx]
                            val newGain = (newCurveDb.toDouble() - peqPreampDb)
                                .coerceIn(-ParametricProfile.MAX_GAIN_DB, ParametricProfile.MAX_GAIN_DB)
                            val roundedGain = (newGain * 10.0).roundToInt() / 10.0
                            peqFilters[filterIdx] = current.copy(gainDb = roundedGain)
                        }
                    },
                    onParametricFilterCommit = {
                        if (peqFilters.isNotEmpty()) {
                            val updatedText = ParametricProfile(peqPreampDb, peqFilters.toList()).toText()
                            val cleanName = (peqName ?: defaultAutoEqLabel).removeSuffix(".txt")
                            peqRaw = updatedText
                            Preferences.setParametricEqProfile(updatedText, cleanName)
                            if (Preferences.getSelectedEqualizer() != 3) {
                                Preferences.setSelectedEqualizer("3")
                                selectedEq = 3
                            }
                            Preferences.setEqualizerEnabled(true)
                            eqEnabled = true
                            serviceBinder?.reloadEqualizer()
                        }
                    }
                )

                // Preset Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    item {
                        EditorialChip(
                            label = stringResource(R.string.editorial_eq_preset_flat),
                            selected = !isEqSwitchOn,
                            onClick = {
                                Preferences.setEqualizerEnabled(false)
                                if (selectedEq == 3) {
                                    Preferences.setSelectedEqualizer("0")
                                    selectedEq = 0
                                }
                                eqEnabled = false
                                eqManager?.setEnabled(false)
                                serviceBinder?.reloadEqualizer()
                            }
                        )
                    }
                    if (hasParametricProfile) {
                        val chipTitle = (peqName ?: defaultAutoEqLabel).removeSuffix(".txt")
                        item {
                            EditorialChip(
                                label = stringResource(R.string.editorial_eq_autoeq_chip, chipTitle),
                                selected = selectedEq == 3,
                                onClick = {
                                    Preferences.setSelectedEqualizer("3")
                                    Preferences.setEqualizerEnabled(true)
                                    selectedEq = 3
                                    eqEnabled = true
                                    serviceBinder?.reloadEqualizer()
                                }
                            )
                        }
                    }
                    items(BUILTIN_PRESETS) { preset ->
                        val isPresetSelected = selectedEq == 1 && eqEnabled &&
                            bandLevelsDb.size >= preset.levelsMilliBel.size &&
                            preset.levelsMilliBel.indices.all { idx ->
                                abs(bandLevelsDb[idx] * 100f - preset.levelsMilliBel[idx]) <= 45f
                            }
                        EditorialChip(
                            label = stringResource(preset.labelRes),
                            selected = isPresetSelected,
                            onClick = {
                                val targetCount = bandLevelsDb.size.coerceAtLeast(preset.levelsMilliBel.size)
                                val updated = ShortArray(targetCount) { i ->
                                    preset.levelsMilliBel.getOrElse(i) { 0 }
                                }
                                Preferences.setEqualizerBandLevels(updated)
                                Preferences.setSelectedEqualizer("1")
                                Preferences.setEqualizerEnabled(true)
                                selectedEq = 1
                                eqEnabled = true
                                serviceBinder?.reloadEqualizer()
                                for (i in 0 until bandLevelsDb.size) {
                                    val mb = updated.getOrElse(i) { 0 }
                                    bandLevelsDb[i] = (mb / 100f).coerceIn(minBandDb, maxBandDb)
                                    eqManager?.setBandLevel(i.toShort(), mb)
                                }
                                eqManager?.setEnabled(true)
                            }
                        )
                    }
                }

                // Import AutoEq + Open Full Equalizer buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onPickAutoEqDocument(handleAutoEqUri) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, StylusBrassBorder),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StylusBrass)
                    ) {
                        Text(
                            text = stringResource(R.string.editorial_eq_import_autoeq),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Surface(
                        onClick = {
                            val binderRef = serviceBinder
                            dismissSmoothly { onOpenFullEqualizer(binderRef) }
                        },
                        modifier = Modifier.weight(1.25f),
                        shape = RoundedCornerShape(999.dp),
                        color = PillBg,
                        border = BorderStroke(1.dp, Hairline),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_graphic_eq),
                                contentDescription = null,
                                tint = Ivory,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.editorial_eq_open_full),
                                color = Ivory,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // 3. ReplayGain Mode Selector + Loudness Preamp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        rgSectionY = coords.positionInParent().y.roundToInt()
                    }
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBg)
                    .border(1.dp, Hairline, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.editorial_rg_section_title),
                    color = Ivory,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                val rgOptions = listOf(
                    "disabled" to R.string.editorial_rg_mode_off,
                    "track" to R.string.editorial_rg_mode_track,
                    "album" to R.string.editorial_rg_mode_album,
                    "auto" to R.string.editorial_rg_mode_auto,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rgOptions.forEach { (modeKey, labelRes) ->
                        val active = rgMode == modeKey
                        Surface(
                            onClick = {
                                rgMode = modeKey
                                App.getInstance().preferences.edit {
                                    putString("replay_gain_mode", modeKey)
                                }
                                val livePlayer = serviceBinder?.getPlayer()
                                if (modeKey == "disabled") {
                                    ReplayGainUtil.getAudioProcessor().clearPendingGain()
                                    ReplayGainUtil.getAudioProcessor().setGainImmediate(0f)
                                } else if (livePlayer != null) {
                                    ReplayGainUtil.prefetchQueueGains(livePlayer)
                                    ReplayGainUtil.applyGain(livePlayer, livePlayer.currentMediaItem)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) StylusBrassFill else PillBg,
                            border = BorderStroke(1.dp, if (active) StylusBrass else Hairline),
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(labelRes),
                                    color = if (active) StylusBrass else IvorySecondary,
                                    fontSize = 13.sp,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }

                // Loudness Preamp slider
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.editorial_rg_preamp_label),
                            color = IvorySecondary,
                            fontSize = 13.sp,
                        )
                        Text(
                            text = PlayerSheets.formatSignedDb(preampDb, decimals = 0),
                            color = if (preampDb != 0f) StylusBrass else IvorySecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            style = TabularStyle,
                        )
                    }
                    Slider(
                        value = preampDb,
                        onValueChange = { next ->
                            val snapped = next.roundToInt().toFloat().coerceIn(-15f, 15f)
                            if (snapped != preampDb) {
                                preampDb = snapped
                                Preferences.setLoudnessPreamp(snapped)
                                serviceBinder?.getPlayer()?.let { livePlayer ->
                                    if (rgMode != "disabled") {
                                        ReplayGainUtil.reapplyCurrentTrackGain(livePlayer)
                                    }
                                }
                            }
                        },
                        valueRange = -15f..15f,
                        steps = 29,
                        enabled = rgMode != "disabled",
                        colors = editorialSliderColors(),
                    )
                }
            }

            // 4. Playback Speed + Skip Silence + Sleep Timer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBg)
                    .border(1.dp, Hairline, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.editorial_playback_section_title),
                        color = Ivory,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = String.format(Locale.US, "%.2fx", playbackSpeed).replace(".00x", ".0x"),
                        color = if (abs(playbackSpeed - 1f) > 0.01f) StylusBrass else IvorySecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        style = TabularStyle,
                    )
                }
                val speeds = listOf(0.8f, 1.0f, 1.25f, 1.5f, 2.0f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    speeds.forEach { spd ->
                        val active = abs(playbackSpeed - spd) < 0.02f
                        val label = if (spd == 1.25f) "1.25x" else String.format(Locale.US, "%.1fx", spd)
                        Surface(
                            onClick = {
                                playbackSpeed = spd
                                Preferences.setPlaybackSpeed(spd)
                                val pitch = if (Preferences.isPlaybackSpeedPitchEnabled()) {
                                    if (Preferences.isPlaybackSpeedManualPitchEnabled()) {
                                        Preferences.getPlaybackSpeedManualPitch()
                                    } else spd
                                } else 1.0f
                                if (LanRemoteSession.isActive()) {
                                    LanRemoteSession.playbackParameters(spd, pitch)
                                } else {
                                    val params = PlaybackParameters(spd, pitch)
                                    browser?.playbackParameters = params
                                    serviceBinder?.getPlayer()?.playbackParameters = params
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(999.dp),
                            color = if (active) StylusBrassFill else PillBg,
                            border = BorderStroke(1.dp, if (active) StylusBrass else Hairline),
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (active) StylusBrass else IvorySecondary,
                                    fontSize = 13.sp,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                    style = TabularStyle,
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Hairline)

                // Skip Silence
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.editorial_skip_silence_title),
                            color = Ivory,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(R.string.editorial_skip_silence_subtitle),
                            color = IvorySecondary,
                            fontSize = 11.sp,
                        )
                    }
                    Switch(
                        checked = skipSilence,
                        enabled = !lanState.active,
                        onCheckedChange = { next ->
                            skipSilence = next
                            Preferences.setSkipSilenceMode(next)
                            serviceBinder?.getPlayer()?.skipSilenceEnabled = next
                        },
                        colors = editorialSwitchColors(),
                    )
                }

                HorizontalDivider(color = Hairline)

                // Sleep Timer Quick Pills
                val timerIsEot = remember(sleepTick, lanState) {
                    if (lanState.active) lanState.timerEndOfTrack else sleepMgr.isEndOfTrack
                }
                val timerRemaining = remember(sleepTick, lanState) {
                    if (lanState.active) lanState.timerRemaining else sleepMgr.remainingFormatted
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.editorial_sleep_section_title),
                        color = Ivory,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    if (timerActive) {
                        val badgeText = if (timerIsEot) {
                            stringResource(R.string.editorial_sleep_eot)
                        } else {
                            stringResource(R.string.editorial_sleep_active_countdown, timerRemaining)
                        }
                        Text(
                            text = badgeText,
                            color = StylusBrass,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            style = TabularStyle,
                        )
                    }
                }

                var lastSelectedMinutes by remember { mutableIntStateOf(0) }
                val sleepOptions = listOf(
                    0 to R.string.editorial_sleep_off,
                    15 to R.string.editorial_sleep_15m,
                    30 to R.string.editorial_sleep_30m,
                    45 to R.string.editorial_sleep_45m,
                    60 to R.string.editorial_sleep_60m,
                    -1 to R.string.editorial_sleep_eot,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sleepOptions) { (mins, resId) ->
                        val selected = when (mins) {
                            0 -> !timerActive
                            -1 -> timerActive && timerIsEot
                            else -> timerActive && !timerIsEot && lastSelectedMinutes == mins
                        }
                        EditorialChip(
                            label = stringResource(resId),
                            selected = selected,
                            onClick = {
                                lastSelectedMinutes = mins
                                when (mins) {
                                    0 -> {
                                        if (LanRemoteSession.isActive()) {
                                            LanRemoteSession.sleepTimer("cancel", 0)
                                        } else {
                                            sleepMgr.cancelTimer()
                                        }
                                    }
                                    -1 -> {
                                        if (LanRemoteSession.isActive()) {
                                            LanRemoteSession.sleepTimer("end", 0)
                                        } else {
                                            sleepMgr.startEndOfTrack()
                                        }
                                    }
                                    else -> {
                                        if (LanRemoteSession.isActive()) {
                                            LanRemoteSession.sleepTimer("minutes", mins)
                                        } else {
                                            sleepMgr.startTimer(mins)
                                        }
                                    }
                                }
                                sleepTick++
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalChainNode(
    stage: String,
    value: String,
    subValue: String?,
    accent: Color,
    highlighted: Boolean,
    brassBorder: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = when {
        brassBorder -> StylusBrassBorder
        highlighted -> IvoryTertiary
        else -> Hairline
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlighted) DeepBg else PillBg.copy(alpha = 0.65f))
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = stage,
            color = IvoryTertiary,
            fontSize = 11.sp,
            maxLines = 1,
        )
        Text(
            text = value,
            color = accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            style = TabularStyle,
        )
        if (!subValue.isNullOrBlank()) {
            Text(
                text = subValue,
                color = IvorySecondary,
                fontSize = 11.sp,
                maxLines = 1,
                style = TabularStyle,
            )
        }
    }
}

/** Crisp 1.dp Stylus Brass signal-flow connector line between audio stages. */
@Composable
private fun SignalConnector(active: Boolean) {
    Box(
        modifier = Modifier
            .width(12.dp)
            .height(1.dp)
            .background(if (active) StylusBrass.copy(alpha = 0.65f) else Hairline)
    )
}

@Composable
private fun EqFrequencyResponseCanvas(
    isActive: Boolean,
    isParametricMode: Boolean,
    peqPreampDb: Double,
    peqFilters: List<ParametricFilter>,
    bandLevelsDb: List<Float>,
    bandFreqsHz: IntArray,
    minDb: Float,
    maxDb: Float,
    onDragStateChanged: (Boolean) -> Unit,
    onBuiltinBandPreview: (Int, Float) -> Unit,
    onBuiltinBandCommit: () -> Unit,
    onParametricFilterPreview: (Int, Float) -> Unit,
    onParametricFilterCommit: () -> Unit,
) {
    val curveColor = when {
        !isActive -> IvoryTertiary
        isParametricMode -> MossGreen
        else -> StylusBrass
    }
    var activeDragIndex by remember { mutableIntStateOf(-1) }

    val latestIsParametric by rememberUpdatedState(isParametricMode)
    val latestPeqPreamp by rememberUpdatedState(peqPreampDb)
    val latestPeqFilters by rememberUpdatedState(peqFilters)
    val latestBands by rememberUpdatedState(bandLevelsDb)
    val latestDragState by rememberUpdatedState(onDragStateChanged)
    val latestBuiltinPreview by rememberUpdatedState(onBuiltinBandPreview)
    val latestBuiltinCommit by rememberUpdatedState(onBuiltinBandCommit)
    val latestParametricPreview by rememberUpdatedState(onParametricFilterPreview)
    val latestParametricCommit by rememberUpdatedState(onParametricFilterCommit)

    val handleCount = if (isParametricMode) peqFilters.size else bandLevelsDb.size

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(152.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DeepBg)
                .border(1.dp, Hairline, RoundedCornerShape(16.dp))
                .pointerInput(isParametricMode, handleCount, minDb, maxDb) {
                    if (handleCount <= 0) return@pointerInput
                    detectTapGestures { offset ->
                        if (size.width <= 0 || size.height <= 0) return@detectTapGestures
                        val padX = 20.dp.toPx()
                        val padY = 16.dp.toPx()
                        val usableW = (size.width - padX * 2f).coerceAtLeast(1f)
                        val usableH = (size.height - padY * 2f).coerceAtLeast(1f)
                        val normY = ((offset.y - padY) / usableH).coerceIn(0f, 1f)
                        val targetDb = maxDb - normY * (maxDb - minDb)

                        if (latestIsParametric) {
                            val idx = findNearestParametricHandle(
                                offset = offset,
                                padX = padX,
                                padY = padY,
                                usableW = usableW,
                                usableH = usableH,
                                minDb = minDb,
                                maxDb = maxDb,
                                preampDb = latestPeqPreamp,
                                filters = latestPeqFilters,
                            )
                            if (idx >= 0) {
                                latestParametricPreview(idx, targetDb)
                                latestParametricCommit()
                            }
                        } else {
                            val count = latestBands.size
                            if (count > 0) {
                                val stepX = if (count > 1) usableW / (count - 1) else 1f
                                val idx = ((offset.x - padX) / stepX).roundToInt().coerceIn(0, count - 1)
                                latestBuiltinPreview(idx, targetDb)
                                latestBuiltinCommit()
                            }
                        }
                    }
                }
                .pointerInput(isParametricMode, handleCount, minDb, maxDb) {
                    if (handleCount <= 0) return@pointerInput
                    detectDragGestures(
                        onDragStart = { start ->
                            if (size.width <= 0 || size.height <= 0) return@detectDragGestures
                            val padX = 20.dp.toPx()
                            val padY = 16.dp.toPx()
                            val usableW = (size.width - padX * 2f).coerceAtLeast(1f)
                            val usableH = (size.height - padY * 2f).coerceAtLeast(1f)
                            activeDragIndex = if (latestIsParametric) {
                                findNearestParametricHandle(
                                    offset = start,
                                    padX = padX,
                                    padY = padY,
                                    usableW = usableW,
                                    usableH = usableH,
                                    minDb = minDb,
                                    maxDb = maxDb,
                                    preampDb = latestPeqPreamp,
                                    filters = latestPeqFilters,
                                )
                            } else {
                                val count = latestBands.size
                                val stepX = if (count > 1) usableW / (count - 1) else 1f
                                ((start.x - padX) / stepX).roundToInt().coerceIn(0, (count - 1).coerceAtLeast(0))
                            }
                            if (activeDragIndex >= 0) {
                                latestDragState(true)
                            }
                        },
                        onDragEnd = {
                            val finished = activeDragIndex >= 0
                            activeDragIndex = -1
                            latestDragState(false)
                            if (finished) {
                                if (latestIsParametric) latestParametricCommit() else latestBuiltinCommit()
                            }
                        },
                        onDragCancel = {
                            val finished = activeDragIndex >= 0
                            activeDragIndex = -1
                            latestDragState(false)
                            if (finished) {
                                if (latestIsParametric) latestParametricCommit() else latestBuiltinCommit()
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val idx = activeDragIndex
                            if (idx >= 0 && size.height > 0) {
                                val padY = 16.dp.toPx()
                                val usableH = (size.height - padY * 2f).coerceAtLeast(1f)
                                val normY = ((change.position.y - padY) / usableH).coerceIn(0f, 1f)
                                val targetDb = maxDb - normY * (maxDb - minDb)
                                if (latestIsParametric) {
                                    latestParametricPreview(idx, targetDb)
                                } else {
                                    latestBuiltinPreview(idx, targetDb)
                                }
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val padX = 20.dp.toPx()
                val padY = 16.dp.toPx()
                val w = (size.width - padX * 2f).coerceAtLeast(1f)
                val h = (size.height - padY * 2f).coerceAtLeast(1f)
                val zeroY = padY + h * (maxDb / (maxDb - minDb))

                val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                drawLine(Hairline, Offset(padX, padY), Offset(padX + w, padY), strokeWidth = 1f, pathEffect = dash)
                drawLine(
                    IvoryTertiary.copy(alpha = 0.35f),
                    Offset(padX, zeroY),
                    Offset(padX + w, zeroY),
                    strokeWidth = 1.2f,
                    pathEffect = dash
                )
                drawLine(Hairline, Offset(padX, padY + h), Offset(padX + w, padY + h), strokeWidth = 1f, pathEffect = dash)

                fun dbToY(db: Float): Float {
                    val clamped = db.coerceIn(minDb, maxDb)
                    val ratio = (maxDb - clamped) / (maxDb - minDb)
                    return padY + ratio * h
                }

                if (isParametricMode && isActive) {
                    val sampleRate = 48_000
                    val biquads = peqFilters
                        .filter { it.fc < 0.45 * sampleRate }
                        .map { Biquad.of(it, sampleRate) }
                    val samples = 96
                    val logMin = ln(20.0)
                    val logMax = ln(20_000.0)
                    val points = ArrayList<Offset>(samples)
                    for (i in 0 until samples) {
                        val t = i.toFloat() / (samples - 1)
                        val freq = exp(logMin + t * (logMax - logMin))
                        val w0 = 2.0 * PI * freq / sampleRate
                        val cos1 = cos(w0)
                        val sin1 = sin(w0)
                        val cos2 = cos(2.0 * w0)
                        val sin2 = sin(2.0 * w0)
                        var totalDb = peqPreampDb
                        for (bq in biquads) {
                            val numRe = bq.b0 + bq.b1 * cos1 + bq.b2 * cos2
                            val numIm = -(bq.b1 * sin1 + bq.b2 * sin2)
                            val denRe = 1.0 + bq.a1 * cos1 + bq.a2 * cos2
                            val denIm = -(bq.a1 * sin1 + bq.a2 * sin2)
                            val magSq = ((numRe * numRe + numIm * numIm) /
                                (denRe * denRe + denIm * denIm).coerceAtLeast(1e-12)).coerceAtLeast(1e-12)
                            totalDb += 10.0 * log10(magSq)
                        }
                        points.add(Offset(padX + t * w, dbToY(totalDb.toFloat())))
                    }

                    val strokePath = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            lineTo(points[i].x, points[i].y)
                        }
                    }
                    val fillPath = Path().apply {
                        addPath(strokePath)
                        lineTo(points.last().x, zeroY)
                        lineTo(points.first().x, zeroY)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(MossGreen.copy(alpha = 0.24f), Color.Transparent),
                            startY = padY,
                            endY = padY + h
                        )
                    )
                    drawPath(
                        path = strokePath,
                        color = curveColor,
                        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    peqFilters.forEachIndexed { idx, f ->
                        val normX = ((ln(f.fc.coerceIn(20.0, 20_000.0)) - logMin) / (logMax - logMin)).toFloat()
                        val pt = Offset(padX + normX * w, dbToY((peqPreampDb + f.gainDb).toFloat()))
                        val selectedHandle = idx == activeDragIndex
                        drawCircle(
                            color = DeepBg,
                            radius = (if (selectedHandle) 7.dp else 5.dp).toPx(),
                            center = pt
                        )
                        drawCircle(
                            color = if (selectedHandle) StylusBrass else MossGreen,
                            radius = (if (selectedHandle) 5.5.dp else 3.6.dp).toPx(),
                            center = pt
                        )
                    }
                } else {
                    val count = bandLevelsDb.size.coerceAtLeast(2)
                    val pts = List(count) { i ->
                        val t = i.toFloat() / (count - 1)
                        val db = if (isActive) bandLevelsDb.getOrElse(i) { 0f } else 0f
                        Offset(padX + t * w, dbToY(db))
                    }
                    val strokePath = Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        for (i in 0 until pts.size - 1) {
                            val p0 = pts[i]
                            val p1 = pts[i + 1]
                            val midX = (p0.x + p1.x) / 2f
                            cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                        }
                    }
                    if (isActive) {
                        val fillPath = Path().apply {
                            addPath(strokePath)
                            lineTo(pts.last().x, zeroY)
                            lineTo(pts.first().x, zeroY)
                            close()
                        }
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(StylusBrass.copy(alpha = 0.24f), Color.Transparent),
                                startY = padY,
                                endY = padY + h
                            )
                        )
                    }
                    drawPath(
                        path = strokePath,
                        color = curveColor,
                        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    pts.forEachIndexed { idx, pt ->
                        val selectedHandle = idx == activeDragIndex
                        drawCircle(
                            color = DeepBg,
                            radius = (if (selectedHandle) 7.dp else 5.5.dp).toPx(),
                            center = pt
                        )
                        drawCircle(
                            color = curveColor,
                            radius = (if (selectedHandle) 5.5.dp else 4.dp).toPx(),
                            center = pt
                        )
                    }
                }
            }
        }

        // Frequency axis labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (isParametricMode && isActive) {
                listOf("20Hz", "100Hz", "1kHz", "6kHz", "20kHz").forEach { lbl ->
                    Text(lbl, color = IvoryTertiary, fontSize = 11.sp, style = TabularStyle)
                }
            } else {
                bandFreqsHz.forEachIndexed { idx, freq ->
                    val freqText = if (freq >= 1000) {
                        if (freq % 1000 == 0) "${freq / 1000}k" else String.format(Locale.US, "%.1fk", freq / 1000f)
                    } else {
                        "${freq}Hz"
                    }
                    val dbVal = if (isActive) bandLevelsDb.getOrElse(idx) { 0f }.roundToInt() else 0
                    val dbText = when {
                        dbVal > 0 -> "+$dbVal"
                        dbVal < 0 -> "$UNICODE_MINUS${abs(dbVal)}"
                        else -> "0"
                    }
                    Text(
                        text = "$freqText ($dbText)",
                        color = IvoryTertiary,
                        fontSize = 11.sp,
                        style = TabularStyle,
                    )
                }
            }
        }
    }
}

private fun findNearestParametricHandle(
    offset: Offset,
    padX: Float,
    padY: Float,
    usableW: Float,
    usableH: Float,
    minDb: Float,
    maxDb: Float,
    preampDb: Double,
    filters: List<ParametricFilter>,
): Int {
    if (filters.isEmpty()) return -1
    val logMin = ln(20.0)
    val logMax = ln(20_000.0)
    var bestIdx = 0
    var bestScore = Float.MAX_VALUE
    filters.forEachIndexed { idx, f ->
        val normX = ((ln(f.fc.coerceIn(20.0, 20_000.0)) - logMin) / (logMax - logMin)).toFloat()
        val hx = padX + normX * usableW
        val clampedDb = (preampDb + f.gainDb).toFloat().coerceIn(minDb, maxDb)
        val hy = padY + ((maxDb - clampedDb) / (maxDb - minDb)) * usableH
        // Weight horizontal distance higher so vertically dragging a node tracks the intended frequency band
        val score = hypot((offset.x - hx) * 1.6f, (offset.y - hy) * 0.65f)
        if (score < bestScore) {
            bestScore = score
            bestIdx = idx
        }
    }
    return bestIdx
}

// ============================================================================
// SHEET B: OUTPUT ROUTE
// ============================================================================

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutputRouteSheet(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val activity = context as? FragmentActivity
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var prefTick by remember { mutableIntStateOf(0) }
    var audioDeviceTick by remember { mutableIntStateOf(0) }
    var lanEnabled by remember(prefTick) { mutableStateOf(Preferences.isLanRemoteEnabled()) }
    var upnpEnabled by remember(prefTick) { mutableStateOf(Preferences.isUpnpEnabled()) }
    var lanState by remember { mutableStateOf(LanRemoteSession.current()) }

    DisposableEffect(appContext) {
        val prefs = App.getInstance().preferences
        val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in OUTPUT_SHEET_PREF_KEYS) {
                prefTick++
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefListener)

        val lanObserver = Observer<LanRemoteState> { next ->
            lanState = next
        }
        LanRemoteSession.state().observeForever(lanObserver)

        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val devCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                audioDeviceTick++
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                audioDeviceTick++
            }
        }
        am?.registerAudioDeviceCallback(devCallback, Handler(Looper.getMainLooper()))

        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
            LanRemoteSession.state().removeObserver(lanObserver)
            am?.unregisterAudioDeviceCallback(devCallback)
        }
    }

    // Live NSD Discovery for Tempus LAN (_tempus-lan._tcp.) with 8s empty-network timeout
    val lanPeers = remember { mutableStateMapOf<String, NsdServiceInfo>() }
    var lanScanning by remember { mutableStateOf(false) }
    var lanScanTimedOut by remember { mutableStateOf(false) }
    var lanResolvingName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(lanEnabled) {
        lanScanTimedOut = false
        if (lanEnabled) {
            delay(DISCOVERY_SCAN_TIMEOUT_MS)
            lanScanTimedOut = true
        }
    }

    DisposableEffect(lanEnabled) {
        if (!lanEnabled) {
            lanPeers.clear()
            lanScanning = false
            return@DisposableEffect onDispose { }
        }
        if (Build.VERSION.SDK_INT >= 37 &&
            appContext.checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED
        ) {
            lanScanning = false
            return@DisposableEffect onDispose { }
        }
        val nsd = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
            ?: return@DisposableEffect onDispose { }
        val main = Handler(Looper.getMainLooper())
        val ownReceiverName = runCatching { LanReceiverService.name(appContext) }.getOrNull()
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                main.post { lanScanning = true }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                main.post { lanScanning = false }
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                main.post { lanScanning = false }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                main.post { lanScanning = false }
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                main.post {
                    if (LanReceiverService.running && serviceInfo.serviceName == ownReceiverName) return@post
                    lanPeers[serviceInfo.serviceName] = serviceInfo
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                main.post { lanPeers.remove(serviceInfo.serviceName) }
            }
        }
        runCatching {
            nsd.discoverServices(LanWire.TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            lanScanning = true
        }
        onDispose {
            lanScanning = false
            runCatching { nsd.stopServiceDiscovery(discoveryListener) }
        }
    }

    // Live MediaRouter discovery for UPnP / DLNA + Chromecast via cross-flavor Flavors helper (zero reflection!)
    var upnpScanTimedOut by remember { mutableStateOf(false) }
    LaunchedEffect(upnpEnabled) {
        upnpScanTimedOut = false
        if (upnpEnabled) {
            delay(DISCOVERY_SCAN_TIMEOUT_MS)
            upnpScanTimedOut = true
        }
    }

    val castAvailable = remember(appContext) { !Flavors.routeButtonIsOnlyForUpnp(appContext) }
    val castSelector = remember(appContext, castAvailable) {
        if (castAvailable) Flavors.castRouteSelector(appContext) else null
    }
    var routeRevision by remember { mutableIntStateOf(0) }
    val mediaRouter = remember(appContext) { MediaRouter.getInstance(appContext) }

    DisposableEffect(mediaRouter, upnpEnabled, castSelector) {
        val builder = MediaRouteSelector.Builder()
        var hasCategories = false
        if (upnpEnabled) {
            builder.addControlCategory(UpnpRouteProvider.CATEGORY_UPNP)
            hasCategories = true
        }
        if (castSelector != null) {
            builder.addSelector(castSelector)
            hasCategories = true
        }
        if (!hasCategories) return@DisposableEffect onDispose { }

        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
                routeRevision++
            }

            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
                routeRevision++
            }

            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
                routeRevision++
            }

            override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
                routeRevision++
            }

            override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
                routeRevision++
            }

            override fun onRouteVolumeChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
                routeRevision++
            }
        }
        mediaRouter.addCallback(builder.build(), callback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
        routeRevision++
        onDispose {
            mediaRouter.removeCallback(callback)
        }
    }

    // MediaBrowser for UPnP remote volume
    var browser by remember { mutableStateOf<MediaBrowser?>(null) }
    var browserVolumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(appContext) {
        val future = MediaBrowser.Builder(
            appContext,
            SessionToken(appContext, ComponentName(appContext, MediaService::class.java))
        ).buildAsync()
        val listener = object : Player.Listener {
            override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
                browserVolumeTick++
            }

            override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
                browserVolumeTick++
            }
        }
        future.addListener({
            runCatching {
                val b = future.get()
                b.addListener(listener)
                browser = b
                browserVolumeTick++
            }
        }, MoreExecutors.directExecutor())
        onDispose {
            browser?.removeListener(listener)
            MediaBrowser.releaseFuture(future)
        }
    }

    val allRoutes = remember(routeRevision, upnpEnabled, castSelector) {
        mediaRouter.routes.toList()
    }
    val selectedMediaRoute = remember(routeRevision) {
        mediaRouter.selectedRoute
    }
    val upnpRoutes = remember(allRoutes, upnpEnabled) {
        if (!upnpEnabled) emptyList()
        else allRoutes.filter { route ->
            !route.isDefault &&
                route.isEnabled &&
                route.supportsControlCategory(UpnpRouteProvider.CATEGORY_UPNP)
        }
    }
    val castRoutes = remember(allRoutes, castSelector) {
        val sel = castSelector ?: return@remember emptyList()
        allRoutes.filter { route ->
            !route.isDefault &&
                route.isEnabled &&
                !route.supportsControlCategory(UpnpRouteProvider.CATEGORY_UPNP) &&
                route.matchesSelector(sel)
        }
    }

    val isMediaRouteRemote = !selectedMediaRoute.isDefault &&
        selectedMediaRoute.playbackType == MediaRouter.RouteInfo.PLAYBACK_TYPE_REMOTE
    val isLocalActive = !lanState.active && !isMediaRouteRemote
    val localHardwareDesc = remember(routeRevision, audioDeviceTick) {
        PlayerSheets.describeLocalAudioDevice(appContext)
    }

    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBg,
        contentColor = Ivory,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { EditorialDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = navBottomPadding + 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = stringResource(R.string.editorial_output_sheet_title),
                    color = Ivory,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.editorial_output_sheet_subtitle),
                    color = IvorySecondary,
                    fontSize = 13.sp,
                )
            }

            // 1. THIS DEVICE (Local speaker / wired / USB DAC / Bluetooth)
            RouteGroupHeader(stringResource(R.string.editorial_output_group_local))
            RouteSelectableCard(
                iconRes = R.drawable.ic_speaker,
                title = stringResource(R.string.editorial_player_output_local),
                subtitle = localHardwareDesc,
                selected = isLocalActive,
                badgeText = if (isLocalActive) stringResource(R.string.editorial_output_active_tag) else null,
                badgeColor = StylusBrass,
                onClick = {
                    if (LanRemoteSession.isActive()) {
                        LanRemoteSession.disconnect()
                    }
                    if (!mediaRouter.selectedRoute.isDefault) {
                        mediaRouter.defaultRoute.select()
                        routeRevision++
                    }
                }
            )

            // 2. TEMPUS LAN REMOTE
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RouteGroupHeader(stringResource(R.string.editorial_output_group_lan))
                if (lanEnabled || lanState.active) {
                    Text(
                        text = stringResource(R.string.editorial_output_lan_pair_manage),
                        color = StylusBrass,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                if (!Preferences.isLanRemoteEnabled()) {
                                    App.getInstance().preferences.edit {
                                        putBoolean(Preferences.LAN_REMOTE_ENABLED, true)
                                    }
                                }
                                activity?.startActivity(Intent(activity, LanPlaybackActivity::class.java))
                                onDismiss()
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (!lanEnabled && !lanState.active) {
                InlineFeatureEnableCard(
                    description = stringResource(R.string.editorial_output_lan_disabled_desc),
                    actionLabel = stringResource(R.string.editorial_output_lan_enable_action),
                    onEnable = {
                        App.getInstance().preferences.edit {
                            putBoolean(Preferences.LAN_REMOTE_ENABLED, true)
                        }
                        lanEnabled = true
                    }
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Active LAN session card: show Ed25519 Verified ONLY when live TLS peer verification succeeded!
                    if (lanState.active) {
                        val statusSub = when {
                            lanState.error != 0 -> stringResource(lanState.error)
                            lanState.connected -> stringResource(R.string.editorial_output_lan_connected)
                            else -> stringResource(R.string.editorial_output_lan_connecting)
                        }
                        RouteSelectableCard(
                            iconRes = R.drawable.ic_lan_remote,
                            title = lanState.name.ifBlank { stringResource(R.string.player_remote_output) },
                            subtitle = statusSub,
                            selected = true,
                            verifiedShieldText = if (lanState.connected) {
                                stringResource(R.string.editorial_output_lan_verified)
                            } else null,
                            trailingActionText = stringResource(R.string.editorial_output_lan_disconnect),
                            onTrailingAction = { LanRemoteSession.disconnect() },
                            onClick = { /* already active */ }
                        )
                    }

                    // Discovered LAN peers: unauthenticated NSD broadcasts carry NO Ed25519 shield!
                    val extraPeers = lanPeers.values.filter { peer ->
                        !lanState.active || peer.serviceName != lanState.name
                    }
                    val discoveredSubtitle = stringResource(R.string.editorial_output_lan_discovered_subtitle)
                    extraPeers.forEach { serviceInfo ->
                        val isResolvingThis = lanResolvingName == serviceInfo.serviceName
                        RouteSelectableCard(
                            iconRes = R.drawable.ic_lan_remote,
                            title = serviceInfo.serviceName,
                            subtitle = if (isResolvingThis) {
                                stringResource(R.string.editorial_output_lan_connecting)
                            } else {
                                discoveredSubtitle
                            },
                            selected = false,
                            verifiedShieldText = null,
                            onClick = {
                                if (lanResolvingName != null) return@RouteSelectableCard
                                val nsd = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
                                    ?: return@RouteSelectableCard
                                val main = Handler(Looper.getMainLooper())
                                lanResolvingName = serviceInfo.serviceName
                                if (!mediaRouter.selectedRoute.isDefault) {
                                    mediaRouter.defaultRoute.select()
                                }
                                @Suppress("DEPRECATION")
                                try {
                                    nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                                        override fun onResolveFailed(failedInfo: NsdServiceInfo, errorCode: Int) {
                                            main.post {
                                                lanResolvingName = null
                                                Toast.makeText(appContext, R.string.lan_connection_error, Toast.LENGTH_SHORT).show()
                                            }
                                        }

                                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                                            main.post {
                                                lanResolvingName = null
                                                LanRemoteSession.connect(appContext, resolved)
                                            }
                                        }
                                    })
                                } catch (e: Exception) {
                                    lanResolvingName = null
                                    LanDiagnostics.record("OutputRouteSheet could not resolve LAN device", e)
                                    Toast.makeText(appContext, R.string.lan_connection_error, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    if (!lanState.active && extraPeers.isEmpty()) {
                        ScanningOrEmptyHint(
                            scanning = lanScanning && !lanScanTimedOut,
                            scanningText = stringResource(R.string.editorial_output_lan_searching),
                            emptyText = stringResource(R.string.editorial_output_lan_empty),
                        )
                    }
                }
            }

            // 3. UPnP / DLNA RENDERERS
            RouteGroupHeader(stringResource(R.string.editorial_output_group_upnp))
            if (!upnpEnabled) {
                InlineFeatureEnableCard(
                    description = stringResource(R.string.editorial_output_upnp_disabled_desc),
                    actionLabel = stringResource(R.string.editorial_output_upnp_enable_action),
                    onEnable = {
                        App.getInstance().preferences.edit {
                            putBoolean(Preferences.UPNP_ENABLED, true)
                        }
                        upnpEnabled = true
                    }
                )
            } else if (upnpRoutes.isEmpty()) {
                ScanningOrEmptyHint(
                    scanning = !upnpScanTimedOut,
                    scanningText = stringResource(R.string.editorial_output_upnp_searching),
                    emptyText = stringResource(R.string.editorial_output_upnp_empty),
                )
            } else {
                val upnpDefaultSubtitle = stringResource(R.string.editorial_output_upnp_default_subtitle)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    upnpRoutes.forEach { route ->
                        val isSelected = selectedMediaRoute.id == route.id && !lanState.active
                        RouteSelectableCard(
                            iconRes = R.drawable.ic_lan_remote,
                            title = route.name,
                            subtitle = route.description?.takeIf { it.isNotBlank() } ?: upnpDefaultSubtitle,
                            selected = isSelected,
                            badgeText = if (isSelected) stringResource(R.string.editorial_output_active_tag) else null,
                            badgeColor = MossGreen,
                            trailingActionText = if (isSelected) stringResource(R.string.editorial_output_lan_disconnect) else null,
                            onTrailingAction = if (isSelected) {
                                {
                                    mediaRouter.defaultRoute.select()
                                    routeRevision++
                                }
                            } else null,
                            onClick = {
                                if (isSelected) {
                                    mediaRouter.defaultRoute.select()
                                } else {
                                    if (LanRemoteSession.isActive()) LanRemoteSession.disconnect()
                                    route.select()
                                }
                                routeRevision++
                            }
                        )
                    }
                }
            }

            // 4. CHROMECAST (Shown ONLY on tempus flavor when Google Play Services + CastContext are available)
            if (castAvailable && castSelector != null) {
                RouteGroupHeader(stringResource(R.string.editorial_output_group_cast))
                if (castRoutes.isEmpty()) {
                    ScanningOrEmptyHint(
                        scanning = false,
                        scanningText = "",
                        emptyText = stringResource(R.string.editorial_output_cast_empty),
                    )
                } else {
                    val castDefaultSubtitle = stringResource(R.string.editorial_output_cast_default_subtitle)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        castRoutes.forEach { route ->
                            val isSelected = selectedMediaRoute.id == route.id && !lanState.active
                            RouteSelectableCard(
                                iconRes = R.drawable.ic_speaker,
                                title = route.name,
                                subtitle = route.description?.takeIf { it.isNotBlank() } ?: castDefaultSubtitle,
                                selected = isSelected,
                                badgeText = if (isSelected) stringResource(R.string.editorial_output_active_tag) else null,
                                badgeColor = StylusBrass,
                                trailingActionText = if (isSelected) stringResource(R.string.editorial_output_lan_disconnect) else null,
                                onTrailingAction = if (isSelected) {
                                    {
                                        mediaRouter.defaultRoute.select()
                                        routeRevision++
                                    }
                                } else null,
                                onClick = {
                                    if (isSelected) {
                                        mediaRouter.defaultRoute.select()
                                    } else {
                                        if (LanRemoteSession.isActive()) LanRemoteSession.disconnect()
                                        route.select()
                                    }
                                    routeRevision++
                                }
                            )
                        }
                    }
                }
            }

            // 5. Remote Volume Slider (shown whenever an active remote target supports volume)
            val remoteVolumeModel = remember(lanState, selectedMediaRoute, routeRevision, browser, browserVolumeTick) {
                resolveRemoteVolumeModel(lanState, selectedMediaRoute, browser)
            }
            if (remoteVolumeModel != null) {
                var sliderVol by remember(remoteVolumeModel.current, remoteVolumeModel.max) {
                    mutableFloatStateOf(remoteVolumeModel.current.toFloat())
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardBg)
                        .border(1.dp, StylusBrassBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_speaker),
                                contentDescription = null,
                                tint = StylusBrass,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${stringResource(R.string.editorial_output_remote_volume)} · ${remoteVolumeModel.targetName}",
                                color = Ivory,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        val pct = ((sliderVol / remoteVolumeModel.max.coerceAtLeast(1)) * 100f).roundToInt()
                        Text(
                            text = "$pct%",
                            color = StylusBrass,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            style = TabularStyle,
                        )
                    }
                    Slider(
                        value = sliderVol,
                        onValueChange = { next ->
                            sliderVol = next
                            remoteVolumeModel.onSetVolume(next.roundToInt())
                        },
                        valueRange = 0f..remoteVolumeModel.max.toFloat().coerceAtLeast(1f),
                        colors = editorialSliderColors(),
                    )
                }
            }
        }
    }
}

private data class RemoteVolumeModel(
    val targetName: String,
    val current: Int,
    val max: Int,
    val onSetVolume: (Int) -> Unit,
)

private fun resolveRemoteVolumeModel(
    lanState: LanRemoteState,
    selectedRoute: MediaRouter.RouteInfo,
    browser: MediaBrowser?,
): RemoteVolumeModel? {
    if (lanState.active && lanState.connected) {
        if (lanState.deviceVolumeMax > 0) {
            return RemoteVolumeModel(
                targetName = lanState.name,
                current = lanState.deviceVolume.coerceIn(0, lanState.deviceVolumeMax),
                max = lanState.deviceVolumeMax,
                onSetVolume = { level -> LanRemoteSession.setDeviceVolume(level) }
            )
        }
        return RemoteVolumeModel(
            targetName = lanState.name,
            current = (lanState.volume * 100f).roundToInt().coerceIn(0, 100),
            max = 100,
            onSetVolume = { pct -> LanRemoteSession.command("volume", pct.toLong()) }
        )
    }
    if (!selectedRoute.isDefault && selectedRoute.playbackType == MediaRouter.RouteInfo.PLAYBACK_TYPE_REMOTE) {
        if (selectedRoute.volumeHandling == MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE && selectedRoute.volumeMax > 0) {
            return RemoteVolumeModel(
                targetName = selectedRoute.name,
                current = selectedRoute.volume.coerceIn(0, selectedRoute.volumeMax),
                max = selectedRoute.volumeMax,
                onSetVolume = { level -> selectedRoute.requestSetVolume(level) }
            )
        }
        val devInfo = browser?.deviceInfo
        if (devInfo != null && devInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE && devInfo.maxVolume > 0) {
            return RemoteVolumeModel(
                targetName = selectedRoute.name,
                current = browser.deviceVolume.coerceIn(0, devInfo.maxVolume),
                max = devInfo.maxVolume,
                onSetVolume = { level ->
                    @Suppress("DEPRECATION")
                    browser.setDeviceVolume(level)
                }
            )
        }
    }
    return null
}

// ============================================================================
// SHARED EDITORIAL WARM DARK COMPOSABLES & NON-COMPOSABLE HELPERS
// ============================================================================

@Composable
private fun EditorialDragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color(0x33FFF0DC))
        )
    }
}

@Composable
private fun EditorialChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) StylusBrassFill else PillBg,
        border = BorderStroke(1.dp, if (selected) StylusBrass else Hairline),
    ) {
        Text(
            text = label,
            color = if (selected) StylusBrass else IvorySecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            maxLines = 1,
            style = TabularStyle,
        )
    }
}

@Composable
private fun RouteGroupHeader(title: String) {
    Text(
        text = title,
        color = IvoryTertiary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.0.sp,
    )
}

@Composable
private fun RouteSelectableCard(
    iconRes: Int,
    title: String,
    subtitle: String,
    selected: Boolean,
    badgeText: String? = null,
    badgeColor: Color = StylusBrass,
    verifiedShieldText: String? = null,
    trailingActionText: String? = null,
    onTrailingAction: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) StylusBrassFill else CardBg,
        border = BorderStroke(1.dp, if (selected) StylusBrass else Hairline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (selected) StylusBrass.copy(alpha = 0.22f) else PillBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = if (selected) StylusBrass else IvorySecondary,
                    modifier = Modifier.size(19.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        color = if (selected) StylusBrass else Ivory,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!verifiedShieldText.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MossFill)
                                .border(1.dp, MossGreen.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = verifiedShieldText,
                                color = MossGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = IvorySecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (trailingActionText != null && onTrailingAction != null) {
                Surface(
                    onClick = onTrailingAction,
                    shape = RoundedCornerShape(999.dp),
                    color = PillBg,
                    border = BorderStroke(1.dp, Terracotta.copy(alpha = 0.6f)),
                ) {
                    Text(
                        text = trailingActionText,
                        color = Terracotta,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            } else if (!badgeText.isNullOrBlank()) {
                Text(
                    text = badgeText,
                    color = badgeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun InlineFeatureEnableCard(
    description: String,
    actionLabel: String,
    onEnable: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(1.dp, Hairline, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = description,
            color = IvorySecondary,
            fontSize = 13.sp,
        )
        Surface(
            onClick = onEnable,
            shape = RoundedCornerShape(999.dp),
            color = StylusBrassFill,
            border = BorderStroke(1.dp, StylusBrass),
        ) {
            Text(
                text = actionLabel,
                color = StylusBrass,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun ScanningOrEmptyHint(
    scanning: Boolean,
    scanningText: String,
    emptyText: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(1.dp, Hairline, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (scanning && scanningText.isNotBlank()) {
            CircularProgressIndicator(
                modifier = Modifier.size(15.dp),
                strokeWidth = 2.dp,
                color = StylusBrass,
            )
            Text(
                text = scanningText,
                color = IvorySecondary,
                fontSize = 13.sp,
            )
        } else {
            Text(
                text = emptyText,
                color = IvoryTertiary,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun editorialSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = DeepBg,
    checkedTrackColor = StylusBrass,
    checkedBorderColor = StylusBrass,
    uncheckedThumbColor = IvorySecondary,
    uncheckedTrackColor = PillBg,
    uncheckedBorderColor = Hairline,
)

@Composable
private fun editorialSliderColors() = SliderDefaults.colors(
    thumbColor = StylusBrass,
    activeTrackColor = StylusBrass,
    inactiveTrackColor = PillBg,
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
)

private data class SourceSignalNodeInfo(
    val codec: String,
    val rateDetail: String?,
    val isLossless: Boolean,
    val isHiRes: Boolean,
)

private data class AutoEqImportResult(
    val rawText: String,
    val displayName: String,
    val profile: ParametricProfile,
)

private fun readAutoEqImport(appContext: Context, uri: Uri): AutoEqImportResult? {
    val rawText = runCatching {
        appContext.contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
    }.getOrNull() ?: return null
    val parsed = ParametricProfile.parse(rawText) ?: return null
    val displayName = resolveDocumentName(appContext, uri)
        ?.removeSuffix(".txt")
        ?.takeIf { it.isNotBlank() }
        ?: appContext.getString(R.string.editorial_badge_eq_autoeq)
    return AutoEqImportResult(rawText = rawText, displayName = displayName, profile = parsed)
}

private fun showAutoEqImportSuccessToast(appContext: Context, displayName: String) {
    Toast.makeText(
        appContext,
        appContext.getString(R.string.editorial_eq_import_success, displayName),
        Toast.LENGTH_SHORT
    ).show()
}

@OptIn(UnstableApi::class)
private fun resolveDecoderSignalLabel(
    appContext: Context,
    browser: MediaBrowser?,
    isRemoteOutput: Boolean,
): String {
    if (isRemoteOutput) {
        return appContext.getString(R.string.editorial_signal_val_remote_decode)
    }
    if (browser?.currentMediaItem == null) return "—"
    val format = MusicUtil.getCurrentAudioFormat(browser)
    if (format?.sampleMimeType == MimeTypes.AUDIO_RAW) return "PCM"
    return if (DownloadUtil.useExtensionRenderers()) "FFmpeg" else "MediaCodec"
}

@OptIn(UnstableApi::class)
private fun resolveSourceSignalNode(
    appContext: Context,
    browser: MediaBrowser?,
    lanState: LanRemoteState,
): SourceSignalNodeInfo {
    if (lanState.active) {
        val song = LanRemoteSession.currentSong()
        val ext = song.suffix?.uppercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: "STREAM"
        val br = song.bitrate?.takeIf { it > 0 }?.let { "$it kbps" }
        val lossless = PlayerSheets.isLosslessCodec(ext)
        return SourceSignalNodeInfo(codec = ext, rateDetail = br, isLossless = lossless, isHiRes = false)
    }
    val extras = browser?.mediaMetadata?.extras
    val format: Format? = MusicUtil.getCurrentAudioFormat(browser)
    val mimeLabel = format?.sampleMimeType?.let { MusicUtil.audioFormatLabel(it) }?.takeIf { it.isNotBlank() }
    val rawSuffix = extras?.getString("suffix")?.takeIf { it.isNotBlank() }
    val codec = (mimeLabel ?: rawSuffix ?: appContext.getString(R.string.player_unknown_format)).uppercase(Locale.ROOT)

    val bitDepth = extras?.getInt("bitDepth", 0) ?: 0
    val sampleRateHz = extras?.getInt("samplingRate", 0)?.takeIf { it > 0 }
        ?: format?.sampleRate?.takeIf { it != Format.NO_VALUE && it > 0 }
        ?: 0
    val bitrateKbps = extras?.getInt("bitrate", 0)?.takeIf { it > 0 }
        ?: format?.bitrate?.takeIf { it != Format.NO_VALUE && it > 0 }?.let { it / 1000 }
        ?: 0

    val detail = when {
        bitDepth > 0 && sampleRateHz > 0 -> {
            val khz = DecimalFormat("0.#").format(sampleRateHz / 1000.0)
            "$bitDepth/${khz}kHz"
        }
        sampleRateHz > 0 -> {
            val khz = DecimalFormat("0.#").format(sampleRateHz / 1000.0)
            "${khz}kHz"
        }
        bitrateKbps > 0 -> "${bitrateKbps}kbps"
        else -> null
    }
    val lossless = PlayerSheets.isLosslessCodec(codec)
    val hiRes = PlayerSheets.isHiResAudio(bitDepth, sampleRateHz)
    return SourceSignalNodeInfo(codec = codec, rateDetail = detail, isLossless = lossless, isHiRes = hiRes)
}

private fun resolveReplayGainShortLabel(
    appContext: Context,
    rgMode: String,
    extras: Bundle?,
): String {
    if (rgMode == "disabled") return appContext.getString(R.string.editorial_signal_val_off)
    val info = ReplayGainBundleUtil.fromBundle(extras)
    val gainVal = when (rgMode) {
        "album" -> info?.albumGain ?: info?.trackGain ?: info?.fallbackGain
        else -> info?.trackGain ?: info?.albumGain ?: info?.fallbackGain
    }
    if (gainVal != null && gainVal != 0f) {
        return PlayerSheets.formatSignedDb(gainVal, decimals = 1)
    }
    return when (rgMode) {
        "track" -> appContext.getString(R.string.editorial_rg_mode_track)
        "album" -> appContext.getString(R.string.editorial_rg_mode_album)
        else -> appContext.getString(R.string.editorial_rg_mode_auto)
    }
}

private fun resolveDocumentName(context: Context, uri: Uri): String? {
    var cursor: Cursor? = null
    return try {
        cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        if (cursor != null && cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) cursor.getString(idx) else null
        } else {
            uri.lastPathSegment
        }
    } catch (_: Exception) {
        uri.lastPathSegment
    } finally {
        cursor?.close()
    }
}
