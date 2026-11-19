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


private val OUTPUT_SHEET_PREF_KEYS = setOf(
    Preferences.LAN_REMOTE_ENABLED,
    Preferences.UPNP_ENABLED,
)


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

