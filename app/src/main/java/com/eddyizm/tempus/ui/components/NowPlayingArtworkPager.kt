package com.eddyizm.tempus.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.eddyizm.tempus.R
import com.eddyizm.tempus.glide.CustomGlideRequest
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.ui.player.CoverCache
import com.eddyizm.tempus.ui.player.PlayerMotion
import com.eddyizm.tempus.ui.player.VinylClock
import com.eddyizm.tempus.util.Preferences
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

/**
 * The cover as a vinyl record, in the manner of NetEase Cloud Music: a black grooved disc with the
 * artwork as its round label, turning with [VinylClock] while playing. The light sheen stays put
 * while the record turns under it.
 */
@Composable
private fun VinylDisc(spinning: Boolean, hideLabel: Boolean, label: @Composable () -> Unit) {
    var angle by remember { mutableFloatStateOf(VinylClock.angle()) }
    LaunchedEffect(spinning) {
        angle = VinylClock.angle()
        if (spinning) while (true) withFrameNanos { angle = VinylClock.angle() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                shadowElevation = 22.dp.toPx()
                shape = CircleShape
                clip = true
            }
            .background(Color(0xFF111113)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = angle }
                .drawBehind {
                    val radius = size.minDimension / 2f
                    var ring = VINYL_LABEL_FRACTION + 0.03f
                    while (ring < 0.97f) {
                        drawCircle(Color.White.copy(alpha = 0.045f), radius * ring, style = Stroke(width = 1.dp.toPx()))
                        ring += 0.022f
                    }
                    drawCircle(Color.Black.copy(alpha = 0.6f), radius * (VINYL_LABEL_FRACTION + 0.012f), style = Stroke(width = 3.dp.toPx()))
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize(VINYL_LABEL_FRACTION)
                    .clip(CircleShape)
                    .graphicsLayer { alpha = if (hideLabel) 0f else 1f }
            ) { label() }
            Box(
                Modifier
                    .fillMaxSize(0.04f)
                    .clip(CircleShape)
                    .background(Color(0xFF111113))
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.sweepGradient(
                        0f to Color.Transparent, 0.12f to Color.White.copy(alpha = 0.07f), 0.22f to Color.Transparent,
                        0.62f to Color.Transparent, 0.72f to Color.White.copy(alpha = 0.05f), 0.82f to Color.Transparent,
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
    val currentOnCoverClick by rememberUpdatedState(onCoverClick)

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

    // Capture user swipe settlement and trigger track change only when user physically swiped
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settledPage ->
            if (userSwiped && !isRadio && settledPage in currentQueue.indices && settledPage != currentTrackIndex) {
                userSwiped = false
                currentOnPageSelected(settledPage)
            }
        }
    }

    val context = LocalContext.current
    // Decode the covers either side of the current track before a swipe or skip needs them.
    LaunchedEffect(safeIndex, queue) {
        if (!isRadio) CoverCache.preload(context, (safeIndex - 1..safeIndex + 2).mapNotNull { queue.getOrNull(it)?.coverArtId })
    }
    val isPlaying by rememberMotion { PlayerMotion.isPlaying }
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
            VinylDisc(spinning = current && isPlaying, hideLabel = current && coverInFlight) {
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
    // A cover already decoded for the mini player or a neighbouring page shows on the first frame.
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
