package com.eddyizm.tempus.ui.browse

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import androidx.navigation.fragment.findNavController
import com.eddyizm.tempus.R
import com.eddyizm.tempus.helper.ThemeHelper
import com.eddyizm.tempus.ui.activity.MainActivity
import com.eddyizm.tempus.util.Preferences
import com.eddyizm.tempus.weather.WeatherLocationMode
import com.eddyizm.tempus.weather.WeatherMood
import com.eddyizm.tempus.weather.WeatherRepository
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Unified "我的" (`ProfileCenterSheet`) ModalBottomSheet opened from the top-right profile icon:
 * 1) Active Subsonic Account & Server switcher (`切换账号 / 管理服务器` -> `MainActivity.quit()`)
 * 2) 3-Way Appearance Switcher (`唱片店夜色` / `AMOLED 纯黑` / `唱片店晨光`)
 * 3) Live Weather Curation & Atmospheric Theme Coupling (`优先自动定位` -> `手动指定城市` fallback + 4 weather palettes)
 * 4) Quick Tools (`歌单核对与缺歌导表`, `离线下载管理`, `全部应用设置`)
 */
@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileCenterSheet(
    fragment: Fragment,
    onDismiss: () -> Unit,
) {
    BrowseTheme {
        val colors = EditorialTheme.colors
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        val pref by WeatherRepository.preferenceState
        val liveSnap by WeatherRepository.liveSnapshotState
        val isLoading by WeatherRepository.loadingState
        val errorMsg by WeatherRepository.errorState
        val effectiveSnap = WeatherRepository.effectiveSnapshot() ?: liveSnap

        var cityDraft by remember(pref.manualCity) { mutableStateOf(pref.manualCity) }

        // Register runtime coarse location permission without requiring activity-compose
        val registryKey = remember { "weather_coarse_loc_${UUID.randomUUID()}" }
        var permissionLauncher by remember { mutableStateOf<ActivityResultLauncher<String>?>(null) }
        DisposableEffect(context, registryKey) {
            val compActivity = context as? ComponentActivity
            val launcher = compActivity?.activityResultRegistry?.register(
                registryKey,
                ActivityResultContracts.RequestPermission(),
            ) { _ ->
                // Whether granted or denied, WeatherRepository.refresh(true) either uses coarse GPS
                // or transparently falls back to manualCity!
                scope.launch { WeatherRepository.refresh(forceNetwork = true) }
            }
            permissionLauncher = launcher
            onDispose {
                launcher?.unregister()
                permissionLauncher = null
            }
        }

        val serverLabel = rememberActiveServerLabel()
        val userName = remember { Preferences.getUser()?.takeIf { it.isNotBlank() } ?: "音乐库听众" }
        val userInitial = remember(userName, serverLabel) {
            (userName.trim().firstOrNull() ?: serverLabel.trim().firstOrNull() ?: 'M').uppercaseChar().toString()
        }

        val systemDark = isSystemInDarkTheme()
        val themePref = Preferences.getTheme()
        val isDark = when (themePref) {
            "light" -> false
            "dark" -> true
            else -> systemDark
        }
        val isAmoled = isDark && Preferences.isDarkThemeBlack()

        fun switchAppearance(targetTheme: String, amoled: Boolean) {
            Preferences.setDynamicColorAccent(false)
            Preferences.setTheme(targetTheme)
            Preferences.setDarkThemeBlack(amoled)
            ThemeHelper.applyTheme(targetTheme)
            (fragment.activity as? AppCompatActivity)?.let { ActivityCompat.recreate(it) }
        }

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            shape = EditorialRadii.SheetTop,
            containerColor = colors.surface1,
            contentColor = colors.textPrimary,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        EditorialEyebrow(text = "MY STUDIO · 我的空间", serif = true)
                        Text(
                            text = "我的与偏好",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(EditorialRadii.Pill)
                            .background(colors.surface2)
                            .border(1.dp, colors.hairline, EditorialRadii.Pill)
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "完成",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textSecondary,
                        )
                    }
                }

                // 1. Account & Server Card (Replaces the removed top-right ServerChip)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .editorialCard(shape = EditorialRadii.Card, backgroundColor = colors.surface2)
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(EditorialRadii.Pill)
                            .background(colors.primary.copy(alpha = 0.16f))
                            .border(1.dp, colors.primary.copy(alpha = 0.44f), EditorialRadii.Pill),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = userInitial,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "已连接 · $serverLabel",
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    EditorialOutlinePill(
                        text = "切换账号",
                        height = 32.dp,
                        accent = colors.primary,
                        onClick = {
                            onDismiss()
                            (fragment.activity as? MainActivity)?.quit()
                        },
                    )
                }

                // 2. 3-Way Appearance Switcher
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .editorialCard(shape = EditorialRadii.Card, backgroundColor = colors.surface2)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "底色模式",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val modes = listOf(
                            Triple("唱片店夜色", isDark && !isAmoled) { switchAppearance(ThemeHelper.DARK_MODE, false) },
                            Triple("AMOLED 纯黑", isAmoled) { switchAppearance(ThemeHelper.DARK_MODE, true) },
                            Triple("唱片店晨光", !isDark) { switchAppearance(ThemeHelper.LIGHT_MODE, false) },
                        )
                        modes.forEach { (label, selected, action) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(EditorialRadii.Pill)
                                    .background(if (selected) colors.primary.copy(alpha = 0.18f) else colors.surface3)
                                    .border(
                                        width = 1.dp,
                                        color = if (selected) colors.primary else colors.hairline,
                                        shape = EditorialRadii.Pill,
                                    )
                                    .clickable(onClick = action),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (selected) colors.primary else colors.textSecondary,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                // 3. Weather Curation & Atmospheric Theme Coupling
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .editorialCard(shape = EditorialRadii.Card, backgroundColor = colors.surface2)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "当日天气曲风与主题浸染",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                            )
                            val summaryText = when {
                                isLoading -> "正在获取当地实况天气…"
                                effectiveSnap != null -> {
                                    val srcTag = when (effectiveSnap.source) {
                                        "gps" -> "自动定位"
                                        "manual" -> "手动城市"
                                        else -> "城市回退"
                                    }
                                    "${effectiveSnap.city} · ${effectiveSnap.conditionLabel} ${effectiveSnap.tempC}°C ($srcTag · 约30%曲风权重)"
                                }
                                else -> "未同步天气 · 默认按「${pref.manualCity}」匹配"
                            }
                            Text(
                                text = summaryText,
                                fontSize = 12.sp,
                                color = colors.primary,
                                style = LocalTextStyle.current.tnum(),
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(EditorialRadii.Pill)
                                .background(colors.surface3)
                                .border(1.dp, colors.hairline, EditorialRadii.Pill)
                                .clickable {
                                    scope.launch { WeatherRepository.refresh(forceNetwork = true) }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_refresh),
                                contentDescription = "刷新天气",
                                tint = colors.primary,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }

                    // Auto-location vs Manual City segmented control
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val isAuto = pref.mode == WeatherLocationMode.AUTO
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(EditorialRadii.Pill)
                                .background(if (isAuto) colors.primary.copy(alpha = 0.18f) else colors.surface3)
                                .border(1.dp, if (isAuto) colors.primary else colors.hairline, EditorialRadii.Pill)
                                .clickable {
                                    WeatherRepository.updatePreference(pref.copy(mode = WeatherLocationMode.AUTO))
                                    if (WeatherRepository.hasCoarseLocationPermission(context)) {
                                        scope.launch { WeatherRepository.refresh(forceNetwork = true) }
                                    } else {
                                        val launcher = permissionLauncher
                                        if (launcher != null) {
                                            launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                        } else {
                                            scope.launch { WeatherRepository.refresh(forceNetwork = true) }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "优先自动定位",
                                fontSize = 12.sp,
                                fontWeight = if (isAuto) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isAuto) colors.primary else colors.textSecondary,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(EditorialRadii.Pill)
                                .background(if (!isAuto) colors.primary.copy(alpha = 0.18f) else colors.surface3)
                                .border(1.dp, if (!isAuto) colors.primary else colors.hairline, EditorialRadii.Pill)
                                .clickable {
                                    WeatherRepository.updatePreference(pref.copy(mode = WeatherLocationMode.MANUAL))
                                    scope.launch { WeatherRepository.refresh(forceNetwork = true) }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "手动指定城市",
                                fontSize = 12.sp,
                                fontWeight = if (!isAuto) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (!isAuto) colors.primary else colors.textSecondary,
                            )
                        }
                    }

                    // Fallback notice or Manual City input + quick city chips
                    val fallbackNote = effectiveSnap?.fallbackNotice ?: errorMsg
                    if (!fallbackNote.isNullOrBlank() && pref.mode == WeatherLocationMode.AUTO) {
                        Text(
                            text = fallbackNote,
                            fontSize = 11.sp,
                            color = colors.textSecondary,
                        )
                    }

                    if (pref.mode == WeatherLocationMode.MANUAL || effectiveSnap?.source == "fallback") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(EditorialRadii.Pill)
                                    .background(colors.surface3)
                                    .border(1.dp, colors.hairline, EditorialRadii.Pill)
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (cityDraft.isEmpty()) {
                                    Text(
                                        text = "输入城市名（如 上海、成都、哈尔滨）",
                                        fontSize = 12.sp,
                                        color = colors.textTertiary,
                                    )
                                }
                                BasicTextField(
                                    value = cityDraft,
                                    onValueChange = { cityDraft = it },
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(
                                        fontSize = 13.sp,
                                        color = colors.textPrimary,
                                    ),
                                    cursorBrush = SolidColor(colors.primary),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            val target = cityDraft.trim().ifEmpty { "上海" }
                                            WeatherRepository.updatePreference(
                                                pref.copy(mode = WeatherLocationMode.MANUAL, manualCity = target),
                                            )
                                            scope.launch { WeatherRepository.refresh(forceNetwork = true) }
                                        },
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .height(36.dp)
                                    .clip(EditorialRadii.Pill)
                                    .background(colors.primary)
                                    .clickable {
                                        val target = cityDraft.trim().ifEmpty { "上海" }
                                        WeatherRepository.updatePreference(
                                            pref.copy(mode = WeatherLocationMode.MANUAL, manualCity = target),
                                        )
                                        scope.launch { WeatherRepository.refresh(forceNetwork = true) }
                                    }
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "更新",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onPrimary,
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            WeatherRepository.QUICK_CITIES.forEach { city ->
                                val activeCity = pref.manualCity == city
                                Box(
                                    modifier = Modifier
                                        .height(28.dp)
                                        .clip(EditorialRadii.Pill)
                                        .background(if (activeCity) colors.primary.copy(alpha = 0.16f) else colors.surface3)
                                        .border(1.dp, if (activeCity) colors.primary else colors.hairline, EditorialRadii.Pill)
                                        .clickable {
                                            cityDraft = city
                                            WeatherRepository.updatePreference(
                                                pref.copy(mode = WeatherLocationMode.MANUAL, manualCity = city),
                                            )
                                            scope.launch { WeatherRepository.refresh(forceNetwork = true) }
                                        }
                                        .padding(horizontal = 11.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = city,
                                        fontSize = 12.sp,
                                        color = if (activeCity) colors.primary else colors.textSecondary,
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = colors.hairline)

                    // Weather Theme Sync Toggle + 5-chip Palette Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "随天气浸染背景与主题色",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = if (pref.themeSync) {
                                    val activeSpec = WeatherRepository.activePaletteOrNull()
                                    activeSpec?.atmosphereNote ?: "根据实况气候自动切换微调底色与强调色"
                                } else {
                                    "已关闭 · 保持唱片店经典暖黑与铜金主色"
                                },
                                fontSize = 11.sp,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        EditorialOutlinePill(
                            text = if (pref.themeSync) "已开启" else "仅固定铜金",
                            height = 30.dp,
                            active = pref.themeSync,
                            accent = colors.primary,
                            onClick = {
                                Preferences.setDynamicColorAccent(false)
                                WeatherRepository.updatePreference(
                                    pref.copy(
                                        themeSync = !pref.themeSync,
                                        moodOverride = if (pref.themeSync) null else pref.moodOverride,
                                    ),
                                )
                            },
                        )
                    }

                    if (pref.themeSync) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            val followLiveSelected = pref.moodOverride == null
                            Box(
                                modifier = Modifier
                                    .height(30.dp)
                                    .clip(EditorialRadii.Pill)
                                    .background(if (followLiveSelected) colors.primary.copy(alpha = 0.18f) else colors.surface3)
                                    .border(1.dp, if (followLiveSelected) colors.primary else colors.hairline, EditorialRadii.Pill)
                                    .clickable {
                                        Preferences.setDynamicColorAccent(false)
                                        WeatherRepository.updatePreference(pref.copy(moodOverride = null))
                                    }
                                    .padding(horizontal = 11.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "跟随实况",
                                    fontSize = 12.sp,
                                    fontWeight = if (followLiveSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (followLiveSelected) colors.primary else colors.textSecondary,
                                )
                            }

                            WeatherMood.entries.forEach { mood ->
                                val spec = WeatherRepository.paletteFor(mood)
                                val selected = pref.moodOverride == mood
                                Row(
                                    modifier = Modifier
                                        .height(30.dp)
                                        .clip(EditorialRadii.Pill)
                                        .background(if (selected) spec.swatchColor.copy(alpha = 0.20f) else colors.surface3)
                                        .border(1.dp, if (selected) spec.swatchColor else colors.hairline, EditorialRadii.Pill)
                                        .clickable {
                                            Preferences.setDynamicColorAccent(false)
                                            WeatherRepository.updatePreference(pref.copy(moodOverride = mood))
                                        }
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(EditorialRadii.Pill)
                                            .background(spec.swatchColor),
                                    )
                                    Text(
                                        text = spec.paletteName,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (selected) colors.textPrimary else colors.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Quick Tools (Standalone Playlist Check/Export, Offline Downloads, Full Settings)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .editorialCard(shape = EditorialRadii.Card, backgroundColor = colors.surface2),
                ) {
                    ProfileToolRow(
                        iconRes = R.drawable.ic_playlist,
                        title = "歌单核对与缺歌导表",
                        subtitle = "粘贴网易云 / QQ 音乐歌单链接，核对本地曲库并导出缺歌清单",
                        onClick = {
                            onDismiss()
                            if (fragment.isAdded) {
                                fragment.findNavController().navigate(R.id.externalPlaylistFragment)
                            }
                        },
                    )
                    HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(horizontal = 14.dp))
                    ProfileToolRow(
                        iconRes = R.drawable.ic_download,
                        title = "离线下载管理",
                        subtitle = "查看已缓存曲目与离线播放队列",
                        onClick = {
                            onDismiss()
                            if (fragment.isAdded) {
                                fragment.findNavController().navigate(R.id.downloadFragment)
                            }
                        },
                    )
                    HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(horizontal = 14.dp))
                    ProfileToolRow(
                        iconRes = R.drawable.ic_settings,
                        title = "全部应用设置",
                        subtitle = "音质转码、缓存上限、歌词显示与高级选项",
                        onClick = {
                            onDismiss()
                            if (fragment.isAdded) {
                                fragment.findNavController().navigate(R.id.settingsFragment)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileToolRow(
    iconRes: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val colors = EditorialTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(EditorialRadii.ListCover)
                .background(colors.surface3)
                .border(1.dp, colors.hairline, EditorialRadii.ListCover),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = colors.brass,
                modifier = Modifier.size(17.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_navigate_next),
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(16.dp),
        )
    }
}
