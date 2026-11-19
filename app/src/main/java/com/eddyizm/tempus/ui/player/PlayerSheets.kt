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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
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
internal val SheetBg = Color(0xFF1E1B18)          // surface-2
internal val CardBg = Color(0xFF161412)           // surface-1
internal val PillBg = Color(0xFF28241F)           // surface-3
internal val DeepBg = Color(0xFF0E0D0C)           // bg
internal val Hairline = Color(0x14FFF0DC)         // 8% warm border
internal val Ivory = Color(0xFFF2ECE3)            // primary text
internal val IvorySecondary = Color(0xA3F2ECE3)   // 64% text
internal val IvoryTertiary = Color(0x66F2ECE3)    // 40% text
internal val StylusBrass = Color(0xFFCFA46A)      // sole primary accent
internal val StylusBrassFill = Color(0x24CFA46A)
internal val StylusBrassBorder = Color(0x88CFA46A)
internal val MossGreen = Color(0xFF8DB596)
internal val MossFill = Color(0x228DB596)
internal val Terracotta = Color(0xFFD9785F)

internal const val UNICODE_MINUS = "\u2212"
internal const val DISCOVERY_SCAN_TIMEOUT_MS = 8_000L

internal val TabularStyle = TextStyle(fontFeatureSettings = "tnum")


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
// SHARED EDITORIAL WARM DARK COMPOSABLES & NON-COMPOSABLE HELPERS
// ============================================================================

@Composable
internal fun EditorialDragHandle() {
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
internal fun EditorialChip(
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
internal fun editorialSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = DeepBg,
    checkedTrackColor = StylusBrass,
    checkedBorderColor = StylusBrass,
    uncheckedThumbColor = IvorySecondary,
    uncheckedTrackColor = PillBg,
    uncheckedBorderColor = Hairline,
)

@Composable
internal fun editorialSliderColors() = SliderDefaults.colors(
    thumbColor = StylusBrass,
    activeTrackColor = StylusBrass,
    inactiveTrackColor = PillBg,
    disabledThumbColor = IvoryTertiary,
    disabledActiveTrackColor = IvoryTertiary.copy(alpha = 0.45f),
    disabledInactiveTrackColor = PillBg.copy(alpha = 0.65f),
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
    disabledActiveTickColor = Color.Transparent,
    disabledInactiveTickColor = Color.Transparent,
)

