package com.eddyizm.tempus.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
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
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay

/**
 * Ratio of the upright square record sleeve width to the combined `(sleeve + peeking vinyl)` width
 * (`1f / 1.34f` ≈ `0.746f`), shared with [com.eddyizm.tempus.ui.player.CoverMorph].
 */
const val VINYL_LABEL_FRACTION = 0.746f

private const val PEEK_RATIO = 0.34f
private const val SPIN_STEP_DELAY_MS = 34L // <= 30fps (~29.4Hz)
private const val MIN_ANGLE_DELTA_DEG = 0.5f

private val SleeveShape = RoundedCornerShape(16.dp)
private val LacquerDark = Color(0xFF12100E)
private val DeepCharcoal = Color(0xFF0E0D0C)
private val SleeveSurface = Color(0xFF161412)
private val HairlineWarm = Color(0x14FFF0DC)
private val IvoryLight = Color(0xFFF2ECE3)
private val StylusBrass = Color(0xFFCFA46A)

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
 * Signature Editorial Warm Dark ("唱片店夜色") artwork stage:
 * - **Left / Front**: Crisp `16.dp` upright square record sleeve (`sleeveContent`), stationary at all times,
 *   with a warm `1.dp` hairline border and subtle `3.dp` left spine highlight.
 * - **Right / Behind**: Dark lacquer grooved vinyl disc peeking ~1/3 out to the right behind the sleeve.
 * - **Low-Power Rotation**: ONLY the peeking vinyl disc's cached offscreen RenderNode (`rotationZ`) rotates,
 *   throttled to `<= 30fps` (`34ms` / `>= 0.5°` angular delta) instead of 120Hz frame callbacks, and halting
 *   with **0 callbacks** whenever paused, collapsed, occluded by lyrics/queue, dragged, in Battery Saver, or stopped.
 */
@Composable
private fun RecordSleeveAndVinyl(
    spinning: Boolean,
    hideSleeve: Boolean,
    sleeveContent: @Composable () -> Unit,
) {
    val lifecycleStarted = rememberLifecycleStarted()
    val activeSpin = spinning && lifecycleStarted
    val angleState = remember { mutableFloatStateOf(VinylClock.angle()) }

    LaunchedEffect(activeSpin) {
        angleState.floatValue = VinylClock.angle()
        if (activeSpin) {
            while (true) {
                delay(SPIN_STEP_DELAY_MS)
                val next = VinylClock.angle()
                val diff = abs(next - angleState.floatValue)
                if (diff >= MIN_ANGLE_DELTA_DEG || diff > 350f) {
                    angleState.floatValue = next
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val maxSleeveByWidth = maxWidth / (1f + PEEK_RATIO)
        val maxSleeveByHeight = maxHeight * 0.94f
        val sleeveSize = minOf(maxSleeveByWidth, maxSleeveByHeight)
        val discSize = sleeveSize * 0.93f
        val unitWidth = sleeveSize * (1f + PEEK_RATIO)

        Box(
            modifier = Modifier.size(width = unitWidth, height = sleeveSize),
            contentAlignment = Alignment.CenterStart,
        ) {
            // 1. Peeking Vinyl Disc on the right behind the upright sleeve
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(discSize)
                    .graphicsLayer {
                        shadowElevation = 18.dp.toPx()
                        shape = CircleShape
                        clip = true
                    }
                    .background(LacquerDark),
                contentAlignment = Alignment.Center,
            ) {
                // Offscreen cached groove + brass center label RenderNode; only rotationZ updates at <=30fps
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
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val grooveStroke = Stroke(width = 1.dp.toPx())
                            val rimStroke = Stroke(width = 1.dp.toPx())
                            val labelRadius = radius * 0.34f
                            val spindleRadius = radius * 0.055f
                            val grooveColor = IvoryLight.copy(alpha = 0.055f)
                            val accentGrooveColor = IvoryLight.copy(alpha = 0.09f)
                            val rimColor = Color(0xFFFFF0DC).copy(alpha = 0.14f)
                            val labelRingColor = DeepCharcoal.copy(alpha = 0.55f)

                            onDrawBehind {
                                drawCircle(rimColor, radius - 0.5.dp.toPx(), style = rimStroke)
                                var step = 0
                                var rFrac = 0.38f
                                while (rFrac < 0.96f) {
                                    val c = if (step % 5 == 0) accentGrooveColor else grooveColor
                                    drawCircle(c, radius * rFrac, style = grooveStroke)
                                    rFrac += 0.028f
                                    step++
                                }
                                // Warm Stylus Brass center record label + subtleradial index ticks
                                drawCircle(StylusBrass.copy(alpha = 0.88f), labelRadius)
                                drawCircle(labelRingColor, labelRadius * 0.82f, style = grooveStroke)
                                // Asymmetrical catalog mark on the brass label so rotation is perceptibly tactile
                                drawCircle(
                                    color = DeepCharcoal.copy(alpha = 0.42f),
                                    radius = radius * 0.024f,
                                    center = Offset(center.x + labelRadius * 0.48f, center.y)
                                )
                                drawCircle(DeepCharcoal, spindleRadius)
                            }
                        }
                )

                // Stationary studio light sheen + sleeve cast shadow on the vinyl
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.sweepGradient(
                                0f to Color.Transparent,
                                0.10f to Color(0xFFFFF4E4).copy(alpha = 0.08f),
                                0.22f to Color.Transparent,
                                0.60f to Color.Transparent,
                                0.72f to StylusBrass.copy(alpha = 0.05f),
                                0.84f to Color.Transparent,
                            )
                        )
                )
            }

            // 2. Upright 16.dp Square Record Sleeve on the left/front
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(sleeveSize)
                    .graphicsLayer {
                        alpha = if (hideSleeve) 0f else 1f
                        shadowElevation = 24.dp.toPx()
                        shape = SleeveShape
                        clip = true
                    }
                    .background(SleeveSurface)
                    .border(1.dp, HairlineWarm, SleeveShape)
            ) {
                sleeveContent()

                // Subtle 3dp editorial record-sleeve spine highlight along the left edge
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(3.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.14f),
                                    Color.Black.copy(alpha = 0.18f),
                                )
                            )
                        )
                )
            }
        }
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
        RecordSleeveAndVinyl(spinning = false, hideSleeve = false) {
            SongArtwork(coverArtId = null, title = null)
        }
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
            RecordSleeveAndVinyl(
                spinning = current && canSpin && !isDragged,
                hideSleeve = current && coverInFlight
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
