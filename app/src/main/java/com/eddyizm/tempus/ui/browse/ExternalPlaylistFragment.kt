package com.eddyizm.tempus.ui.browse

import android.content.ClipboardManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.eddyizm.tempus.R
import com.eddyizm.tempus.util.Constants
import java.util.Locale

class ExternalPlaylistFragment : Fragment() {
    private val model: ExternalPlaylistViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val initialUrl = arguments?.getString(ARG_INITIAL_URL)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BrowseTheme {
                    val colors = EditorialTheme.colors
                    val state by model.state.collectAsState()
                    var choosing by remember { mutableStateOf<Int?>(null) }

                    LaunchedEffect(initialUrl) {
                        if (!initialUrl.isNullOrBlank() && state.link.isBlank()) {
                            model.setLink(initialUrl)
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = colors.bg,
                        contentColor = colors.textPrimary,
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.statusBars),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(
                                start = 20.dp,
                                end = 20.dp,
                                top = 8.dp,
                                bottom = dimensionResource(R.dimen.global_padding_bottom) + 24.dp,
                            ),
                        ) {
                            // Top Bar
                            item(key = "ext_top_bar") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    IconButton(
                                        onClick = { findNavController().popBackStack() },
                                        modifier = Modifier.padding(end = 4.dp),
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_arrow_back),
                                            contentDescription = stringResource(R.string.player_collapse),
                                            tint = colors.textPrimary,
                                        )
                                    }
                                    Text(
                                        text = stringResource(R.string.editorial_ext_title),
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary,
                                    )
                                }
                            }

                            // Input Card (16dp surface1 + hairline)
                            item(key = "ext_input_card") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .editorialCard(shape = EditorialRadii.Card, backgroundColor = colors.surface1)
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.editorial_ext_subtitle),
                                        fontSize = 12.5.sp,
                                        lineHeight = 18.sp,
                                        color = colors.textSecondary,
                                    )

                                    OutlinedTextField(
                                        value = state.link,
                                        onValueChange = model::setLink,
                                        enabled = !state.busy,
                                        label = { Text(stringResource(R.string.editorial_ext_input_label)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                        shape = EditorialRadii.Card,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = colors.brass,
                                            unfocusedBorderColor = colors.hairline,
                                            focusedLabelColor = colors.brass,
                                            unfocusedLabelColor = colors.textTertiary,
                                            cursorColor = colors.brass,
                                            focusedContainerColor = colors.surface2,
                                            unfocusedContainerColor = colors.surface2,
                                        ),
                                        trailingIcon = {
                                            if (state.link.isNotEmpty()) {
                                                TextButton(
                                                    onClick = { model.setLink("") },
                                                    enabled = !state.busy,
                                                ) {
                                                    Text(
                                                        stringResource(R.string.editorial_ext_clear),
                                                        color = colors.textSecondary,
                                                    )
                                                }
                                            } else {
                                                TextButton(
                                                    onClick = {
                                                        val clip = requireContext().getSystemService(ClipboardManager::class.java)
                                                            ?.primaryClip?.getItemAt(0)?.coerceToText(requireContext())?.toString()
                                                        if (!clip.isNullOrBlank()) model.setLink(clip)
                                                    },
                                                    enabled = !state.busy,
                                                ) {
                                                    Text(
                                                        stringResource(R.string.editorial_ext_paste),
                                                        color = colors.brass,
                                                        fontWeight = FontWeight.SemiBold,
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 4,
                                    )

                                    val canFetch = !state.busy && state.link.isNotBlank()
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .pressable(enabled = canFetch) { model.load() }
                                            .clip(EditorialRadii.Pill)
                                            .background(if (canFetch) colors.brass else colors.surface3),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.editorial_ext_load_btn),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (canFetch) colors.onPrimary else colors.textTertiary,
                                        )
                                    }
                                }
                            }

                            // Busy Progress Card
                            if (state.busy) {
                                item(key = "ext_busy_card") {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .editorialCard(shape = EditorialRadii.Card, backgroundColor = colors.surface1)
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = state.phase + if (state.total > 0) " ${state.done} / ${state.total}" else "",
                                                modifier = Modifier.weight(1f),
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = colors.textPrimary,
                                                style = LocalTextStyle.current.tnum(),
                                            )
                                            EditorialOutlinePill(
                                                text = stringResource(R.string.editorial_ext_cancel),
                                                height = 28.dp,
                                                accent = colors.terracotta,
                                                onClick = model::cancel,
                                            )
                                        }
                                        if (state.total > 0) {
                                            LinearProgressIndicator(
                                                progress = { (state.done.toFloat() / state.total.toFloat()).coerceIn(0f, 1f) },
                                                modifier = Modifier.fillMaxWidth().clip(EditorialRadii.Pill),
                                                color = colors.brass,
                                                trackColor = colors.surface3,
                                            )
                                        } else {
                                            LinearProgressIndicator(
                                                modifier = Modifier.fillMaxWidth().clip(EditorialRadii.Pill),
                                                color = colors.brass,
                                                trackColor = colors.surface3,
                                            )
                                        }
                                    }
                                }
                            }

                            // Error / Status Banner
                            state.error?.let { message ->
                                item(key = "ext_error") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(EditorialRadii.Card)
                                            .background(colors.terracotta.copy(alpha = 0.14f))
                                            .border(1.dp, colors.terracotta.copy(alpha = 0.45f), EditorialRadii.Card)
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = message,
                                            fontSize = 13.sp,
                                            color = colors.terracotta,
                                        )
                                    }
                                }
                            }

                            state.message?.let { message ->
                                item(key = "ext_message") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(EditorialRadii.Card)
                                            .background(colors.moss.copy(alpha = 0.14f))
                                            .border(1.dp, colors.moss.copy(alpha = 0.45f), EditorialRadii.Card)
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = message,
                                            fontSize = 13.sp,
                                            color = colors.moss,
                                        )
                                    }
                                }
                            }

                            // Loaded Playlist Match Summary + Track Rows
                            state.source?.takeIf { !state.busy && state.rows.size == it.tracks.size }?.let { source ->
                                item(key = "ext_match_summary") {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .editorialCard(shape = EditorialRadii.Card, backgroundColor = colors.surface1)
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                                Text(
                                                    text = source.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp,
                                                    color = colors.textPrimary,
                                                )
                                                Text(
                                                    text = stringResource(R.string.editorial_ext_stat_total, state.rows.size),
                                                    modifier = Modifier.padding(top = 2.dp),
                                                    fontSize = 12.sp,
                                                    color = colors.textSecondary,
                                                    style = LocalTextStyle.current.tnum(),
                                                )
                                            }
                                            MatchRateRing(
                                                matched = state.matched,
                                                total = state.rows.size,
                                                size = 44.dp,
                                            )
                                        }

                                        ThreeSegmentBar(
                                            matched = state.matched,
                                            pending = state.ambiguous,
                                            missing = state.missing,
                                            height = 6.dp,
                                        )

                                        // Semantic Legend Pills (<5% screen area)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            StatusStatDot(
                                                label = stringResource(R.string.editorial_ext_stat_matched, state.matched),
                                                dotColor = colors.moss,
                                            )
                                            StatusStatDot(
                                                label = stringResource(R.string.editorial_ext_stat_ambiguous, state.ambiguous),
                                                dotColor = colors.brass,
                                            )
                                            StatusStatDot(
                                                label = stringResource(R.string.editorial_ext_stat_missing, state.missing),
                                                dotColor = colors.terracotta,
                                            )
                                        }

                                        Text(
                                            text = stringResource(R.string.editorial_ext_save_hint),
                                            fontSize = 12.sp,
                                            lineHeight = 17.sp,
                                            color = colors.textTertiary,
                                        )

                                        val canSave = state.ambiguous == 0
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(44.dp)
                                                .pressable(enabled = canSave, onClick = model::save)
                                                .clip(EditorialRadii.Pill)
                                                .background(if (canSave) colors.brass else colors.surface3)
                                                .border(
                                                    width = 1.dp,
                                                    color = if (canSave) colors.brass else colors.brass.copy(alpha = 0.4f),
                                                    shape = EditorialRadii.Pill,
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = if (canSave) {
                                                    stringResource(R.string.editorial_ext_save_btn)
                                                } else {
                                                    stringResource(R.string.editorial_ext_save_blocked, state.ambiguous)
                                                },
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (canSave) colors.onPrimary else colors.brass,
                                                style = LocalTextStyle.current.tnum(),
                                            )
                                        }
                                    }
                                }

                                itemsIndexed(state.rows, key = { index, row -> "$index:${row.id}" }) { index, row ->
                                    val chosen = state.choices[index] ?: row.matches.singleOrNull()
                                    val isAmbiguous = chosen == null && row.matches.size > 1
                                    val hasAlternatives = chosen == null && !isAmbiguous && row.alternatives.isNotEmpty()
                                    val selectable = row.matches.size > 1 || row.alternatives.isNotEmpty() || index in state.choices

                                    val badgeLabel = when {
                                        chosen != null && selectable -> stringResource(R.string.editorial_ext_row_switch)
                                        chosen != null -> stringResource(R.string.editorial_ext_row_matched)
                                        isAmbiguous -> stringResource(R.string.editorial_ext_row_confirm)
                                        hasAlternatives -> stringResource(R.string.editorial_ext_row_candidates)
                                        else -> stringResource(R.string.editorial_ext_row_missing)
                                    }
                                    val badgeColor = when {
                                        chosen != null -> colors.moss
                                        isAmbiguous || hasAlternatives -> colors.brass
                                        else -> colors.terracotta
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .editorialCard(
                                                shape = EditorialRadii.Card,
                                                backgroundColor = colors.surface1,
                                                borderColor = if (isAmbiguous) colors.brass.copy(alpha = 0.50f) else colors.hairline,
                                            )
                                            .clickable(enabled = selectable) { choosing = index }
                                            .padding(horizontal = 14.dp, vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "%02d".format(Locale.US, index + 1),
                                            modifier = Modifier.width(30.dp),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.textTertiary,
                                            style = LocalTextStyle.current.tnum(),
                                        )
                                        Column(Modifier.weight(1f).padding(end = 10.dp)) {
                                            Text(
                                                text = row.title,
                                                fontSize = 14.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = colors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = if (chosen != null && chosen.album?.isNotBlank() == true) {
                                                    "${row.artist} · ${chosen.album}"
                                                } else {
                                                    row.artist
                                                },
                                                modifier = Modifier.padding(top = 2.dp),
                                                fontSize = 12.sp,
                                                color = colors.textSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        if (selectable) {
                                            EditorialOutlinePill(
                                                text = badgeLabel,
                                                height = 26.dp,
                                                accent = badgeColor,
                                                active = isAmbiguous,
                                                onClick = { choosing = index },
                                            )
                                        } else {
                                            Text(
                                                text = badgeLabel,
                                                modifier = Modifier
                                                    .clip(EditorialRadii.Badge)
                                                    .background(badgeColor.copy(alpha = 0.14f))
                                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = badgeColor,
                                            )
                                        }
                                    }
                                }
                            }

                            // Saved Server Playlists Section
                            item(key = "ext_saved_header") {
                                Column(Modifier.padding(top = 12.dp)) {
                                    HorizontalDivider(color = colors.hairline)
                                    Text(
                                        text = stringResource(R.string.editorial_ext_saved_header),
                                        modifier = Modifier.padding(top = 16.dp),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary,
                                    )
                                }
                            }

                            if (state.imports.isEmpty()) {
                                item(key = "ext_saved_empty") {
                                    Text(
                                        text = stringResource(R.string.editorial_ext_saved_empty),
                                        fontSize = 13.5.sp,
                                        color = colors.textTertiary,
                                    )
                                }
                            }

                            itemsIndexed(state.imports, key = { _, entry -> entry.playlist.id }) { _, entry ->
                                val total = entry.binding.total.coerceAtLeast(entry.playlist.songCount)
                                val missingCount = entry.binding.missing.size
                                val matchedCount = (total - missingCount).coerceAtLeast(0)
                                val incomplete = !entry.binding.complete

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .editorialCard(shape = EditorialRadii.Card, backgroundColor = colors.surface1)
                                        .clickable {
                                            findNavController().navigate(
                                                R.id.playlistPageFragment,
                                                bundleOf(Constants.PLAYLIST_OBJECT to entry.playlist),
                                            )
                                        }
                                        .padding(15.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f).padding(end = 10.dp)) {
                                            Text(
                                                text = entry.playlist.name.orEmpty(),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.5.sp,
                                                color = colors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = when {
                                                    incomplete -> stringResource(R.string.editorial_ext_saved_incomplete)
                                                    missingCount == 0 -> stringResource(R.string.editorial_ext_saved_all_ready, total)
                                                    else -> stringResource(R.string.editorial_ext_saved_pending, matchedCount, total, missingCount)
                                                },
                                                modifier = Modifier.padding(top = 2.dp),
                                                fontSize = 12.sp,
                                                color = if (incomplete) colors.brass else colors.textSecondary,
                                                style = LocalTextStyle.current.tnum(),
                                            )
                                        }
                                        EditorialOutlinePill(
                                            text = stringResource(R.string.editorial_ext_saved_rematch),
                                            height = 28.dp,
                                            accent = colors.brass,
                                            enabled = !state.busy,
                                            onClick = { model.load(entry.binding.url) },
                                        )
                                    }

                                    ThreeSegmentBar(
                                        matched = matchedCount,
                                        pending = if (incomplete) missingCount.coerceAtLeast(1) else 0,
                                        missing = if (incomplete) 0 else missingCount,
                                        height = 5.dp,
                                    )
                                }
                            }
                        }
                    }

                    // Warm-Dark Version Picker Dialog
                    choosing?.let { index ->
                        val row = state.rows.getOrNull(index)
                        if (row != null) {
                            val candidates = if (row.matches.size > 1) row.matches else row.alternatives
                            AlertDialog(
                                onDismissRequest = { choosing = null },
                                containerColor = colors.surface2,
                                titleContentColor = colors.textPrimary,
                                textContentColor = colors.textPrimary,
                                shape = EditorialRadii.Hero,
                                title = {
                                    Column {
                                        Text(
                                            text = stringResource(R.string.editorial_ext_dialog_title),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                        )
                                        Text(
                                            text = "${row.title} · ${row.artist}",
                                            modifier = Modifier.padding(top = 2.dp),
                                            fontSize = 12.5.sp,
                                            color = colors.textSecondary,
                                        )
                                    }
                                },
                                text = {
                                    LazyColumn(
                                        modifier = Modifier.heightIn(max = 380.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        itemsIndexed(candidates) { _, song ->
                                            val isCurrentChoice = state.choices[index]?.id == song.id
                                            val dur = song.duration?.takeIf { it > 0 }?.let {
                                                " · %d:%02d".format(Locale.US, it / 60, it % 60)
                                            }.orEmpty()
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(EditorialRadii.Card)
                                                    .background(if (isCurrentChoice) colors.brass.copy(alpha = 0.16f) else colors.surface1)
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (isCurrentChoice) colors.brass else colors.hairline,
                                                        shape = EditorialRadii.Card,
                                                    )
                                                    .clickable {
                                                        model.choose(index, song)
                                                        choosing = null
                                                    }
                                                    .padding(12.dp),
                                            ) {
                                                Text(
                                                    text = song.title.orEmpty(),
                                                    fontSize = 14.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = colors.textPrimary,
                                                )
                                                Text(
                                                    text = "${song.artist.orEmpty()} · ${song.album.orEmpty()}$dur",
                                                    modifier = Modifier.padding(top = 2.dp),
                                                    fontSize = 12.sp,
                                                    color = colors.textSecondary,
                                                    style = LocalTextStyle.current.tnum(),
                                                )
                                            }
                                        }
                                    }
                                },
                                dismissButton = if (index in state.choices) ({
                                    TextButton(onClick = { model.clearChoice(index); choosing = null }) {
                                        Text(
                                            stringResource(R.string.editorial_ext_dialog_clear),
                                            color = colors.terracotta,
                                        )
                                    }
                                }) else null,
                                confirmButton = {
                                    TextButton(onClick = { choosing = null }) {
                                        Text(
                                            stringResource(R.string.editorial_ext_dialog_close),
                                            color = colors.brass,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val ARG_INITIAL_URL = "initial_url"
    }
}

@Composable
private fun StatusStatDot(
    label: String,
    dotColor: androidx.compose.ui.graphics.Color,
) {
    val colors = EditorialTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .clip(EditorialRadii.Pill)
                .background(dotColor),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textSecondary,
            style = LocalTextStyle.current.tnum(),
        )
    }
}
