package com.eddyizm.tempus.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.eddyizm.tempus.R
import com.eddyizm.tempus.glide.CustomGlideRequest
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.ui.player.CoverCache
import com.eddyizm.tempus.ui.player.PlayerMotion
import com.eddyizm.tempus.ui.player.VinylClock
import com.eddyizm.tempus.util.RadioCoverArtDownloader
import kotlin.math.absoluteValue

/** Share of the record taken by the artwork label in its centre. */
const val VINYL_LABEL_FRACTION = 0.66f

@Composable
private fun <T> rememberMotion(read: () -> T): State<T> {
    val value = remember { mutableStateOf(read()) }
    DisposableEffect(Unit) {
        val listener = PlayerMotion.Listener { value.value = read() }
        PlayerMotion.addListener(listener)
        onDispose { PlayerMotion.removeListener(listener) }
    }
    return value
}

@Composable
private fun rememberLifecycleStarted(): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var started by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return started
}

/**
 * Editorial Warm Dark vinyl disc: a warm charcoal-lacquer record turning via RenderNode `rotationZ`
 * (zero Compose recompositions and zero groove redraws per frame), halting immediately with 0 frame
 * callbacks whenever paused, collapsed to mini-player, occluded by lyrics, or stopped.
 */
@Composable
private fun VinylDisc(spinning: Boolean, hideLabel: Boolean, label: @Composable () -> Unit) {
    val lifecycleStarted = rememberLifecycleStarted()
    val activeSpin = spinning && lifecycleStarted
    val angleState = remember { mutableFloatStateOf(VinylClock.angle()) }

    LaunchedEffect(activeSpin) {
        angleState.floatValue = VinylClock.angle()
        if (activeSpin) {
            while (true) {
                withFrameNanos {
                    angleState.floatValue = VinylClock.angle()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                shadowElevation = 24.dp.toPx()
                shape = CircleShape
                clip = true
            }
            .background(Color(0xFF12100E)),
        contentAlignment = Alignment.Center
    ) {
        // Rotating disc layer: offscreen RenderNode cached while spinning so RenderThread only rotates it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = if (activeSpin) {
                        CompositingStrategy.Offscreen
                    } else {
                        CompositingStrategy.Auto
                    }
                    rotationZ = angleState.floatValue
                }
                .drawWithCache {
                    val radius = size.minDimension / 2f
                    val grooveStroke = Stroke(width = 1.dp.toPx())
                    val rimStroke = Stroke(width = 1.dp.toPx())
                    val innerCollarStroke = Stroke(width = 3.dp.toPx())
                    val brassRingStroke = Stroke(width = 1.2.dp.toPx())
                    val grooveColor = Color(0xFFF2ECE3).copy(alpha = 0.042f)
                    val rimColor = Color(0xFFFFF0DC).copy(alpha = 0.10f)
                    val collarColor = Color(0xFF090807).copy(alpha = 0.72f)
                    val brassRingColor = Color(0xFFCFA46A).copy(alpha = 0.42f)

                    onDrawBehind {
                        drawCircle(rimColor, radius - 0.5.dp.toPx(), style = rimStroke)
                        var ring = VINYL_LABEL_FRACTION + 0.03f
                        while (ring < 0.965f) {
                            drawCircle(grooveColor, radius * ring, style = grooveStroke)
                            ring += 0.024f
                        }
                        drawCircle(
                            collarColor,
                            radius * (VINYL_LABEL_FRACTION + 0.012f),
                            style = innerCollarStroke
                        )
                        drawCircle(
                            brassRingColor,
                            radius * (VINYL_LABEL_FRACTION + 0.004f),
                            style = brassRingStroke
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize(VINYL_LABEL_FRACTION)
                    .clip(CircleShape)
                    .graphicsLayer { alpha = if (hideLabel) 0f else 1f }
            ) { label() }
            // Warm brass spindle collar + dark center hole
            Box(
                Modifier
                    .fillMaxSize(0.052f)
                    .clip(CircleShape)
                    .background(Color(0xFFCFA46A).copy(alpha = 0.78f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .fillMaxSize(0.68f)
                        .clip(CircleShape)
                        .background(Color(0xFF0E0D0C))
                )
            }
        }
        // Stationary studio light sheen above the rotating vinyl
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.sweepGradient(
                        0f to Color.Transparent,
                        0.11f to Color(0xFFFFF4E4).copy(alpha = 0.065f),
                        0.22f to Color.Transparent,
                        0.61f to Color.Transparent,
                        0.72f to Color(0xFFCFA46A).copy(alpha = 0.045f),
                        0.83f to Color.Transparent,
                    )
                )
        )
    }
}

@Composable
fun NowPlayingArtworkPager(
    queue: List<Child>,
    currentIndex: Int,
    isRadio: Boolean = false,
    radioArtworkUri: Uri? = null,
    radioCoverArtId: String? = null,
    onPageSelected: (Int) -> Unit = {},
    onCoverClick: () -> Unit = {}
) {
    val currentQueue by rememberUpdatedState(queue)
    val currentTrackIndex by rememberUpdatedState(currentIndex)
    val currentOnPageSelected by rememberUpdatedState(onPageSelected)

    val count = if (isRadio || queue.isEmpty()) 1 else queue.size
    val safeIndex = if (isRadio) 0 else currentIndex.coerceIn(0, (count - 1).coerceAtLeast(0))

    val pagerState = rememberPagerState(
        initialPage = safeIndex,
        pageCount = { count }
    )
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    var userSwiped by remember { mutableStateOf(false) }

    LaunchedEffect(isDragged) {
        if (isDragged) {
            userSwiped = true
        }
    }

    var firstSelectionDone by remember { mutableStateOf(false) }
    LaunchedEffect(safeIndex, count) {
        if (!isDragged && safeIndex in 0 until count && safeIndex != pagerState.targetPage) {
            if (!firstSelectionDone || (safeIndex - pagerState.currentPage).absoluteValue > 1) {
                pagerState.scrollToPage(safeIndex)
                firstSelectionDone = true
            } else {
                pagerState.animateScrollToPage(
                    page = safeIndex,
                    animationSpec = tween(durationMillis = 300)
                )
            }
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settledPage ->
            if (userSwiped && !isRadio && settledPage in currentQueue.indices && settledPage != currentTrackIndex) {
                userSwiped = false
                currentOnPageSelected(settledPage)
            }
        }
    }

    val context = LocalContext.current
    LaunchedEffect(safeIndex, queue) {
        if (!isRadio) CoverCache.preload(context, (safeIndex - 1..safeIndex + 2).mapNotNull { queue.getOrNull(it)?.coverArtId })
    }
    val canSpin by rememberMotion {
        PlayerMotion.isPlaying &&
            PlayerMotion.motionEnabled &&
            PlayerMotion.sheetExpanded &&
            !PlayerMotion.lyricsShowing
    }
    val coverInFlight by rememberMotion { PlayerMotion.coverInFlight }

    if (!isRadio && queue.isEmpty()) {
        VinylDisc(spinning = false, hideLabel = false) { SongArtwork(coverArtId = null, title = null) }
        return
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 0.dp),
        pageSpacing = 16.dp,
        userScrollEnabled = !isRadio && count > 1,
        key = { page ->
            if (isRadio) "radio" else "${queue.getOrNull(page)?.id ?: "track"}_$page"
        }
    ) { page ->
        val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
        val scale = lerp(0.92f, 1.0f, 1f - pageOffset.coerceIn(0f, 1f))
        val alpha = lerp(0.7f, 1.0f, 1f - pageOffset.coerceIn(0f, 1f))

        val current = page == pagerState.currentPage
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .clickable(interactionSource = null, indication = null) {
                    if (current) onCoverClick()
                },
            contentAlignment = Alignment.Center
        ) {
            VinylDisc(
                spinning = current && canSpin && !isDragged,
                hideLabel = current && coverInFlight
            ) {
                if (isRadio) {
                    RadioArtwork(artworkUri = radioArtworkUri, coverArtId = radioCoverArtId)
                } else {
                    val song = queue.getOrNull(page)
                    SongArtwork(coverArtId = song?.coverArtId, title = song?.title)
                }
            }
        }
    }
}

@Composable
private fun SongArtwork(
    coverArtId: String?,
    title: String?
) {
    val appContext = LocalContext.current.applicationContext
    var bitmap by remember(coverArtId) { mutableStateOf(CoverCache.get(coverArtId)) }

    DisposableEffect(coverArtId) {
        var active = true
        if (bitmap == null) CoverCache.load(appContext, coverArtId) { if (active) bitmap = it }
        onDispose { active = false }
    }

    val currentBitmap = bitmap
    if (currentBitmap != null) {
        Image(
            bitmap = currentBitmap.asImageBitmap(),
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        Image(
            painter = painterResource(id = R.drawable.ic_placeholder_song),
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun RadioArtwork(
    artworkUri: Uri?,
    coverArtId: String?
) {
    val appContext = LocalContext.current.applicationContext
    var bitmap by remember(artworkUri, coverArtId) { mutableStateOf<Bitmap?>(null) }

    DisposableEffect(artworkUri, coverArtId) {
        val target = object : CustomTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                bitmap = resource
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                bitmap = null
            }
        }

        var request = Glide.with(appContext)
            .asBitmap()
            .load(artworkUri)
            .apply(CustomGlideRequest.createRequestOptions(appContext, coverArtId, CustomGlideRequest.ResourceType.Radio))

        request = RadioCoverArtDownloader.applyLocalFileSignature(request, artworkUri)
        request.into(target)

        onDispose {
            Glide.with(appContext).clear(target)
        }
    }

    val currentBitmap = bitmap
    if (currentBitmap != null) {
        Image(
            bitmap = currentBitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        Image(
            painter = painterResource(id = R.drawable.ic_placeholder_radio),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}
