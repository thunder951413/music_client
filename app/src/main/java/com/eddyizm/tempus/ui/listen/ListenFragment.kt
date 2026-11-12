package com.eddyizm.tempus.ui.listen

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.media3.common.util.UnstableApi
import com.eddyizm.tempus.App
import com.eddyizm.tempus.R
import com.eddyizm.tempus.ui.activity.MainActivity
import com.eddyizm.tempus.ui.dialog.PodcastChannelEditorDialog
import com.eddyizm.tempus.ui.dialog.RadioEditorDialog
import com.eddyizm.tempus.ui.fragment.HomeTabPodcastFragment
import com.eddyizm.tempus.ui.fragment.HomeTabRadioFragment
import com.eddyizm.tempus.ui.queue.rememberAppColorScheme
import com.eddyizm.tempus.util.Preferences

@OptIn(UnstableApi::class)
class ListenFragment : Fragment() {

    companion object {
        private const val STATE_SELECTED_TAB = "listen_selected_tab"
        private const val TAG_PODCAST = "listen_child_podcast"
        private const val TAG_RADIO = "listen_child_radio"
        private const val KEY_PODCAST_VISIBILITY = "podcast_section_visibility"
        private const val KEY_RADIO_VISIBILITY = "radio_section_visibility"
    }

    private var selectedTab by mutableIntStateOf(0)
    private var showPodcast by mutableStateOf(true)
    private var showRadio by mutableStateOf(true)

    private var podcastContainer: FragmentContainerView? = null
    private var radioContainer: FragmentContainerView? = null

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_PODCAST_VISIBILITY || key == KEY_RADIO_VISIBILITY) {
            view?.post { refreshSectionVisibility() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            selectedTab = savedInstanceState.getInt(STATE_SELECTED_TAB, 0)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_SELECTED_TAB, selectedTab)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(
                com.google.android.material.color.MaterialColors.getColor(
                    this,
                    com.google.android.material.R.attr.colorSurface,
                    0xFF0E0D0C.toInt()
                )
            )
        }

        val headerView = ComposeView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val colorScheme = rememberAppColorScheme()
                MaterialTheme(colorScheme = colorScheme) {
                    val effectiveTab = resolveEffectiveTab(selectedTab, showPodcast, showRadio)
                    ListenHeader(
                        selectedTab = effectiveTab,
                        showSegmentedControl = showPodcast && showRadio,
                        onSelectTab = { newTab ->
                            selectedTab = newTab
                            applyActiveContainer()
                        },
                        onQuickAdd = { openQuickAddDialog(effectiveTab) }
                    )
                }
            }
        }
        root.addView(headerView)

        val contentFrame = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val podcastView = FragmentContainerView(context).apply {
            id = R.id.listen_podcast_container
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val radioView = FragmentContainerView(context).apply {
            id = R.id.listen_radio_container
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        podcastContainer = podcastView
        radioContainer = radioView
        contentFrame.addView(podcastView)
        contentFrame.addView(radioView)
        root.addView(contentFrame)

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        refreshSectionVisibility()
    }

    override fun onStart() {
        super.onStart()
        (activity as? MainActivity)?.let {
            it.toggleBottomNavigationBarVisibilityOnOrientationChange()
            it.setBottomSheetVisibility(true)
        }
        App.getInstance().preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        refreshSectionVisibility()
    }

    override fun onResume() {
        super.onResume()
        refreshSectionVisibility()
    }

    override fun onStop() {
        App.getInstance().preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        super.onStop()
    }

    override fun onDestroyView() {
        podcastContainer = null
        radioContainer = null
        super.onDestroyView()
    }

    private fun refreshSectionVisibility() {
        if (!isAdded) return
        val podcastVisible = Preferences.isPodcastSectionVisible()
        val radioVisible = Preferences.isRadioSectionVisible()
        showPodcast = podcastVisible
        showRadio = radioVisible

        if (!podcastVisible && !radioVisible) {
            (activity as? MainActivity)?.syncListenTabVisibility()
            return
        }

        selectedTab = resolveEffectiveTab(selectedTab, podcastVisible, radioVisible)
        ensureChildFragments(podcastVisible, radioVisible)
        applyActiveContainer()
    }

    private fun resolveEffectiveTab(preferred: Int, podcastVisible: Boolean, radioVisible: Boolean): Int {
        return when {
            podcastVisible && !radioVisible -> 0
            !podcastVisible && radioVisible -> 1
            else -> preferred.coerceIn(0, 1)
        }
    }

    private fun ensureChildFragments(podcastVisible: Boolean, radioVisible: Boolean) {
        if (childFragmentManager.isStateSaved) return
        val tx = childFragmentManager.beginTransaction().setReorderingAllowed(true)
        var changed = false

        if (podcastVisible && childFragmentManager.findFragmentByTag(TAG_PODCAST) == null) {
            tx.replace(R.id.listen_podcast_container, HomeTabPodcastFragment(), TAG_PODCAST)
            changed = true
        }
        if (radioVisible && childFragmentManager.findFragmentByTag(TAG_RADIO) == null) {
            tx.replace(R.id.listen_radio_container, HomeTabRadioFragment(), TAG_RADIO)
            changed = true
        }

        if (changed) {
            tx.commitNowAllowingStateLoss()
        }
    }

    private fun applyActiveContainer() {
        val effectiveTab = resolveEffectiveTab(selectedTab, showPodcast, showRadio)
        podcastContainer?.visibility = if (showPodcast && effectiveTab == 0) View.VISIBLE else View.GONE
        radioContainer?.visibility = if (showRadio && effectiveTab == 1) View.VISIBLE else View.GONE
    }

    private fun openQuickAddDialog(effectiveTab: Int) {
        val hostActivity = activity ?: return
        if (effectiveTab == 0) {
            val podcastChild = childFragmentManager.findFragmentByTag(TAG_PODCAST) as? HomeTabPodcastFragment
            PodcastChannelEditorDialog(podcastChild ?: object : com.eddyizm.tempus.interfaces.PodcastCallback {}).show(
                hostActivity.supportFragmentManager,
                null
            )
        } else {
            val radioChild = childFragmentManager.findFragmentByTag(TAG_RADIO) as? HomeTabRadioFragment
            RadioEditorDialog(radioChild ?: object : com.eddyizm.tempus.interfaces.RadioCallback {}).show(
                hostActivity.supportFragmentManager,
                null
            )
        }
    }
}

@Composable
private fun ListenHeader(
    selectedTab: Int,
    showSegmentedControl: Boolean,
    onSelectTab: (Int) -> Unit,
    onQuickAdd: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val hairline = scheme.outlineVariant.copy(alpha = 0.65f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surface)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.editorial_listen_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(scheme.surfaceContainerHighest)
                    .border(1.dp, hairline, CircleShape)
                    .clickable(onClick = onQuickAdd)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(
                        if (selectedTab == 0) R.string.editorial_listen_add_podcast
                        else R.string.editorial_listen_add_radio
                    ),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = scheme.primary,
                    maxLines = 1
                )
            }
        }

        if (showSegmentedControl) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(scheme.surfaceContainerHigh)
                    .border(1.dp, hairline, CircleShape)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ListenSegmentOption(
                    label = stringResource(R.string.editorial_listen_tab_podcast),
                    selected = selectedTab == 0,
                    onClick = { onSelectTab(0) },
                    modifier = Modifier.weight(1f)
                )
                ListenSegmentOption(
                    label = stringResource(R.string.editorial_listen_tab_radio),
                    selected = selectedTab == 1,
                    onClick = { onSelectTab(1) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ListenSegmentOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val backgroundColor = if (selected) scheme.surfaceContainerHighest else Color.Transparent
    val borderModifier = if (selected) {
        Modifier.border(1.dp, scheme.primary.copy(alpha = 0.42f), CircleShape)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .then(borderModifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
