package com.eddyizm.tempus.ui.browse

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eddyizm.tempus.R
import com.eddyizm.tempus.ui.queue.rememberAppColorScheme

/** Shared building blocks for the Home, Discover and Search pages. */

/** Solid tile colours that carry white text in both themes. */
object BrowsePalette {
    private val tiles = longArrayOf(
        0xFFE13D56, 0xFF2F6BD8, 0xFF1E9E74, 0xFFE07B2E, 0xFF8A4FD8, 0xFF0F8FA8,
        0xFFC2457A, 0xFF5E7A2C, 0xFFB5651D, 0xFF3D4A9E, 0xFFBF8510, 0xFF4B5563,
    )

    /** A stable colour for a name, so a genre keeps its tile colour between visits. */
    fun colorFor(key: String): Color {
        val index = Math.floorMod(key.lowercase().hashCode(), tiles.size)
        return Color(tiles[index])
    }

    fun colorAt(index: Int): Color = Color(tiles[Math.floorMod(index, tiles.size)])
}

@Composable
fun BrowseTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = rememberAppColorScheme(), content = content)
}

/** A slight squeeze while pressed, shared by every tappable card. */
@Composable
fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.5).sp,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (action != null && onAction != null) {
            Text(
                text = "$action ›",
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A Spotify-style category tile: a bold title over a solid colour, with a tilted accent shape. */
@Composable
fun CategoryTile(
    title: String,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 92.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(height)
            .pressable(onClick)
            .clip(RoundedCornerShape(14.dp))
            .background(color),
    ) {
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 16.dp, y = 14.dp)
                .size(height * 0.72f)
                .rotate(24f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.18f)),
        )
        Text(
            text = title,
            modifier = Modifier.padding(12.dp),
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A chart card in the manner of NetEase's chart wall: label, title, subtitle and bar glyph. */
@Composable
fun ChartCard(
    title: String,
    subtitle: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(128.dp)
            .pressable(onClick)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.78f).compositeOverBlack()))),
    ) {
        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(14.dp)
                .height(30.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            for (fraction in floatArrayOf(0.45f, 1f, 0.7f)) {
                Box(
                    Modifier
                        .width(6.dp)
                        .fillMaxHeight(fraction)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.35f)),
                )
            }
        }
        Column(Modifier.padding(14.dp)) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
            )
            Text(
                text = title,
                modifier = Modifier.padding(top = 4.dp),
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                modifier = Modifier.padding(top = 2.dp, end = 40.dp),
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A compact shortcut: coloured icon square plus a label, as at the top of Spotify's home. */
@Composable
fun QuickTile(
    title: String,
    color: Color,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .height(56.dp)
            .pressable(onClick)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 10.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A large feature card with a play button, used for the daily mix and personal radio. */
@Composable
fun HeroCard(
    title: String,
    subtitle: String,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    bigText: String? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(168.dp)
            .pressable(onClick)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(colors)),
    ) {
        if (icon != null) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.16f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 18.dp, y = (-10).dp)
                    .size(118.dp)
                    .rotate(-12f),
            )
        }
        if (bigText != null) {
            // A calendar-style day number, as on NetEase's daily recommendation card.
            Text(
                bigText,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 54.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-2).sp,
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 16.dp, end = 64.dp),
        ) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(14.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_play), contentDescription = null, tint = colors.first(), modifier = Modifier.size(22.dp))
        }
    }
}

private fun Color.compositeOverBlack(): Color = Color(red * alpha, green * alpha, blue * alpha, 1f)
