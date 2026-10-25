package com.eddyizm.tempus.ui.player.lyrics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.eddyizm.tempus.ui.player.PlayerMotion
import java.util.function.LongConsumer
import java.util.function.LongSupplier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/** One timed lyric line; a blank [text] is an instrumental interlude shown as breathing dots. */
data class LyricLine(val startMs: Long, val endMs: Long, val text: String) {
    val isInterlude: Boolean get() = text.isBlank()
}

/** Lyrics for the current track, set from the Java lyrics fragment. */
class LyricsState {
    var lines by mutableStateOf<List<LyricLine>>(emptyList())
        private set
    var plainText by mutableStateOf<String?>(null)
        private set

    /** When false the view stops following playback, like the old sync lock button. */
    var follow by mutableStateOf(true)

    fun showSynced(lines: List<LyricLine>) {
        plainText = null
        this.lines = lines
    }

    fun showPlain(text: String?) {
        lines = emptyList()
        plainText = text?.takeIf { it.isNotBlank() }
    }

    companion object {
        private const val LEADING_INTERLUDE_MS = 4_000L
        private const val MIN_INTERLUDE_MS = 3_000L
        private const val LAST_LINE_MS = 5_000L

        /** Orders timed lines, adds an opening interlude and drops blank lines too short to show. */
        @JvmStatic
        fun buildLines(starts: List<Long>, texts: List<String>): List<LyricLine> {
            val sorted = starts.indices.sortedBy { starts[it] }
            val result = ArrayList<LyricLine>(sorted.size + 1)
            sorted.forEachIndexed { order, index ->
                val start = starts[index]
                val end = sorted.getOrNull(order + 1)?.let { starts[it] } ?: (start + LAST_LINE_MS)
                val text = texts[index].trim()
                if (text.isNotEmpty() || end - start >= MIN_INTERLUDE_MS) result.add(LyricLine(start, end, text))
            }
            val first = result.firstOrNull()
            if (first != null && !first.isInterlude && first.startMs >= LEADING_INTERLUDE_MS) {
                result.add(0, LyricLine(0L, first.startMs, ""))
            }
            return result
        }
    }
}

@Composable
fun LyricsPane(state: LyricsState, position: () -> Long, onSeek: (Long) -> Unit) {
    val fade = Modifier
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent, 0.12f to Color.Black, 0.82f to Color.Black, 1f to Color.Transparent,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    val lines = state.lines
    val plain = state.plainText
    when {
        lines.isNotEmpty() -> SyncedLyrics(lines, state.follow, position, onSeek, fade)
        plain != null -> PlainLyrics(plain, fade)
    }
}

@Composable
private fun PlainLyrics(text: String, modifier: Modifier) {
    Box(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 22.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 48.dp, bottom = 96.dp),
        )
    }
}

/** Positions measured in the last layout pass; [version] changes only when they do. */
private class LineLayout {
    var tops = FloatArray(0)
    var height = 0f
}

@Composable
private fun SyncedLyrics(
    lines: List<LyricLine>,
    follow: Boolean,
    position: () -> Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val motion = PlayerMotion.motionEnabled

    var positionMs by remember { mutableLongStateOf(position()) }
    // The pager keeps this page alive behind the cover, so the clock only runs while it is shown.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) withFrameMillis { positionMs = position() }
        }
    }
    val active by remember(lines) {
        derivedStateOf { lines.indexOfLast { it.startMs <= positionMs + LEAD_MS } }
    }

    val layout = remember(lines) { LineLayout() }
    var layoutVersion by remember(lines) { mutableIntStateOf(0) }
    var viewport by remember { mutableIntStateOf(0) }
    val follows = remember(lines) { List(lines.size) { Animatable(0f) } }
    var placed by remember(lines) { mutableStateOf(false) }

    var manual by remember(lines) { mutableStateOf(false) }
    var manualOffset by remember(lines) { mutableFloatStateOf(0f) }
    var resumeJob by remember { mutableStateOf<Job?>(null) }

    // Each line glides to its new place with a small delay that grows down the list, so the text
    // below the current line follows like a ripple instead of scrolling as one block.
    LaunchedEffect(active, layoutVersion, viewport, manual, follow) {
        if (manual || !follow || viewport == 0 || layout.tops.size != lines.size) return@LaunchedEffect
        val anchor = viewport * ANCHOR
        val target = anchor - layout.tops[active.coerceAtLeast(0)]
        if (!placed || !motion) {
            follows.forEach { it.snapTo(target) }
            placed = true
            return@LaunchedEffect
        }
        follows.forEachIndexed { index, animatable ->
            launch {
                val distance = index - active
                if (distance > -2) delay((distance + 1).coerceIn(0, 8) * STAGGER_MS)
                animatable.animateTo(target, spring(dampingRatio = 0.86f, stiffness = 95f))
            }
        }
    }

    fun resumeLater() {
        resumeJob?.cancel()
        resumeJob = scope.launch {
            delay(RESUME_MS)
            animate(manualOffset, 0f, animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow)) { value, _ ->
                manualOffset = value
            }
            manual = false
        }
    }

    fun clampManual(value: Float): Float {
        val follow0 = follows.getOrNull(active.coerceAtLeast(0))?.value ?: 0f
        val min = viewport * 0.5f - layout.height - follow0
        val max = viewport * 0.5f - follow0
        return value.coerceIn(min.coerceAtMost(max), max)
    }

    val dragState = rememberDraggableState { delta -> manualOffset = clampManual(manualOffset + delta) }
    val content: @Composable () -> Unit = {
        lines.forEachIndexed { index, line ->
            val distance = if (active < 0) index + 1 else abs(index - active)
            val isActive = index == active
            if (line.isInterlude) {
                // Progress is read while drawing, so the dots fill without recomposing every line.
                Interlude(isActive) {
                    ((positionMs - line.startMs).toFloat() / (line.endMs - line.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
                }
            } else {
                LyricText(
                    text = line.text,
                    isActive = isActive,
                    distance = distance,
                    relaxed = manual || !follow,
                    blur = motion,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        resumeJob?.cancel()
                        manualOffset = 0f
                        manual = false
                        onSeek(line.startMs + 1)
                    },
                )
            }
        }
    }

    Layout(
        content = content,
        modifier = modifier
            .fillMaxSize()
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStarted = {
                    resumeJob?.cancel()
                    manual = true
                },
                onDragStopped = { velocity ->
                    resumeJob?.cancel()
                    resumeJob = scope.launch {
                        animateDecay(manualOffset, velocity, FloatExponentialDecaySpec()) { value, _ ->
                            manualOffset = clampManual(value)
                        }
                    }
                    resumeJob?.invokeOnCompletion { if (it == null) resumeLater() }
                },
            ),
    ) { measurables, constraints ->
        val placeables = measurables.map {
            it.measure(Constraints(minWidth = 0, maxWidth = constraints.maxWidth))
        }
        val tops = FloatArray(placeables.size)
        var y = 0f
        placeables.forEachIndexed { index, placeable ->
            tops[index] = y
            y += placeable.height
        }
        if (!tops.contentEquals(layout.tops)) {
            layout.tops = tops
            layout.height = y
            layoutVersion++
        }
        if (viewport != constraints.maxHeight) viewport = constraints.maxHeight
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { index, placeable ->
                val shift = follows.getOrNull(index)?.value ?: 0f
                val top = tops[index] + shift + manualOffset
                if (top + placeable.height > -OVERSCAN && top < constraints.maxHeight + OVERSCAN) {
                    placeable.place(0, top.toInt())
                }
            }
        }
    }
}

@Composable
private fun LyricText(
    text: String,
    isActive: Boolean,
    distance: Int,
    relaxed: Boolean,
    blur: Boolean,
    onClick: () -> Unit,
) {
    val alpha by animateFloatAsState(
        targetValue = when {
            isActive -> 1f
            relaxed -> 0.55f
            else -> (0.46f - 0.05f * min(distance, 4)).coerceAtLeast(0.24f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "lyricAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.95f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "lyricScale",
    )
    val blurRadius by animateFloatAsState(
        targetValue = if (!blur || relaxed || isActive) 0f else min(distance, 5) * 1.2f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "lyricBlur",
    )
    Text(
        text = text,
        color = Color.White,
        fontSize = 27.sp,
        lineHeight = 35.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 11.dp)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .blur(blurRadius.dp, BlurredEdgeTreatment.Unbounded),
    )
}

@Composable
private fun Interlude(isActive: Boolean, progress: () -> Float) {
    val visible by animateFloatAsState(if (isActive) 1f else 0f, tween(400), label = "interludeAlpha")
    val breathing = rememberInfiniteTransition(label = "interlude")
    val pulse by breathing.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1_400), RepeatMode.Reverse),
        label = "interludePulse",
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(56.dp)
            .padding(start = 4.dp)
            .graphicsLayer {
                alpha = visible
                // Near its end the interlude swells and fades, handing over to the next line.
                val ending = ((progress() - 0.9f) / 0.1f).coerceIn(0f, 1f)
                val s = if (isActive) pulse * (1f + ending * 0.25f) else 0.8f
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0f, 0.5f)
            },
    ) {
        repeat(3) { dot ->
            Box(
                Modifier
                    .size(11.dp)
                    .drawBehind {
                        val fill = ((progress() * 3f) - dot).coerceIn(0f, 1f)
                        drawCircle(Color.White.copy(alpha = 0.3f + 0.7f * fill))
                    },
            )
        }
    }
}

/** Highlights a line slightly before its timestamp, as the voice usually starts on it. */
private const val LEAD_MS = 180L
private const val ANCHOR = 0.28f
private const val STAGGER_MS = 42L
private const val RESUME_MS = 2_500L
private const val OVERSCAN = 400

/** Hosts [LyricsPane] in the Java lyrics fragment's ComposeView. */
object LyricsComposeBridge {
    @JvmStatic
    fun bind(view: ComposeView, state: LyricsState, position: LongSupplier, seek: LongConsumer) {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            LyricsPane(state, position = { position.asLong }, onSeek = { seek.accept(it) })
        }
    }
}
