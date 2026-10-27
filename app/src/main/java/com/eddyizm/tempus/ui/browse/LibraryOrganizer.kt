package com.eddyizm.tempus.ui.browse

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.eddyizm.tempus.App
import com.eddyizm.tempus.R
import com.eddyizm.tempus.subsonic.base.ApiResponse
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.util.Constants
import com.eddyizm.tempus.util.Preferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import androidx.core.os.bundleOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call

/**
 * Turns the library into real playlists on the server ([LibraryMixes]), so they also show up in
 * the playlist pages, Android Auto and other clients. The playlists it made are remembered per
 * server; a later run rewrites them in place and deletes those that no longer qualify.
 */
object LibraryOrganizer {
    data class Result(val playlists: Int, val songs: Int)

    private const val PAGE = 500
    private const val SCAN_LIMIT = 50_000
    private const val CHUNK = 100

    private fun idsKey() = "library_mix_ids|${Preferences.getServerId()}"
    private fun countKey() = "library_mix_songs|${Preferences.getServerId()}"

    /** How many playlists the last run made on this server, 0 when it never ran. */
    fun playlistCount(): Int = App.getInstance().preferences.getStringSet(idsKey(), null)?.size ?: 0

    private fun execute(call: Call<ApiResponse>) =
        call.execute().takeIf { it.isSuccessful }?.body()?.subsonicResponse
            ?.takeIf { it.error == null } ?: throw IllegalStateException("request failed")

    private fun scanPage(query: String, offset: Int): List<Child> =
        execute(App.getSubsonicClientInstance(false).searchingClient.search3(query, PAGE, offset, 0, 0, 0, 0))
            .searchResult3?.songs.orEmpty()

    /** Every song, paged through search3 with an empty query; some servers want `""` instead. */
    private fun scanAll(onProgress: (Int) -> Unit): List<Child> {
        val query = if (scanPage("", 0).isNotEmpty()) "" else "\"\""
        val songs = ArrayList<Child>()
        while (songs.size < SCAN_LIMIT) {
            val page = scanPage(query, songs.size)
            songs += page
            onProgress(songs.size)
            if (page.size < PAGE) break
        }
        return songs
    }

    fun name(context: Context, kind: LibraryMix.Kind): String {
        val label = when (kind) {
            is LibraryMix.Kind.ByLanguage -> context.getString(kind.language.label)
            is LibraryMix.Kind.ByDecade -> context.getString(R.string.browse_decade_label, kind.decade)
            is LibraryMix.Kind.ByGenre -> kind.genre
            LibraryMix.Kind.Newest -> context.getString(R.string.library_mix_newest)
            LibraryMix.Kind.Unplayed -> context.getString(R.string.library_mix_unplayed)
            LibraryMix.Kind.MostPlayed -> context.getString(R.string.library_mix_most_played)
        }
        return context.getString(R.string.library_mix_name, label)
    }

    /** [onProgress] gets (phase, done, total): phase 0 counts scanned songs, 1 written playlists. */
    suspend fun run(context: Context, onProgress: (Int, Int, Int) -> Unit): Result = withContext(Dispatchers.IO) {
        val songs = scanAll { onProgress(0, it, 0) }
        val mixes = LibraryMixes.plan(songs)
        val client = App.getSubsonicClientInstance(false).playlistClient
        val managed = App.getInstance().preferences.getStringSet(idsKey(), null).orEmpty()
        val existing = execute(client.playlists).playlists?.playlists.orEmpty()
        val names = mixes.map { name(context, it.kind) }
        // Reuse a playlist this app made, or one with the same name left by an earlier install.
        val byName = existing.filter { it.id in managed || it.name in names }.associateBy { it.name }
        val written = LinkedHashSet<String>()
        mixes.forEachIndexed { index, mix ->
            ensureActive()
            onProgress(1, index, mixes.size)
            val name = names[index]
            val first = ArrayList(mix.songIds.take(CHUNK))
            var id = byName[name]?.id
            // createPlaylist with an id replaces the songs of that playlist.
            val created = execute(client.createPlaylist(id, name, first))
            if (id == null) {
                id = created.playlist?.id
                    ?: execute(client.playlists).playlists?.playlists?.lastOrNull { it.name == name }?.id
                    ?: return@forEachIndexed
            }
            mix.songIds.drop(CHUNK).chunked(CHUNK).forEach { execute(client.updatePlaylist(id, null, null, ArrayList(it), null)) }
            written += id
        }
        // Groups that shrank below the minimum lose their playlist.
        (managed - written).forEach { stale -> runCatching { execute(client.deletePlaylist(stale)) } }
        App.getInstance().preferences.edit {
            putStringSet(idsKey(), written)
            putInt(countKey(), songs.size)
        }
        Result(written.size, songs.size)
    }

    /** Asks first, then runs with a progress dialog and opens the playlists when done. */
    @JvmStatic
    fun confirmAndRun(fragment: Fragment, onDone: () -> Unit = {}) {
        val context = fragment.requireContext()
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.library_mix_title)
            .setMessage(R.string.library_mix_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.library_mix_start) { _, _ -> start(fragment, onDone) }
            .show()
    }

    private fun start(fragment: Fragment, onDone: () -> Unit) {
        val context = fragment.requireContext()
        val label = TextView(context).apply { setText(R.string.library_mix_scanning_start) }
        val bar = LinearProgressIndicator(context).apply { isIndeterminate = true }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(label)
            addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = pad / 2
            })
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.library_mix_title)
            .setView(content)
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        val job = fragment.viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                run(context) { phase, done, total ->
                    label.post {
                        if (phase == 0) {
                            label.text = context.getString(R.string.library_mix_scanning, done)
                        } else {
                            label.text = context.getString(R.string.library_mix_writing, done + 1, total)
                            if (bar.isIndeterminate) {
                                bar.visibility = View.INVISIBLE
                                bar.isIndeterminate = false
                                bar.visibility = View.VISIBLE
                            }
                            bar.setProgressCompat(done * 100 / total.coerceAtLeast(1), true)
                        }
                    }
                }
            }
            if (!isActive) return@launch
            dialog.dismiss()
            val done = result.getOrNull()
            when {
                done == null -> Toast.makeText(context, R.string.library_mix_failed, Toast.LENGTH_LONG).show()
                done.playlists == 0 -> Toast.makeText(context, R.string.library_mix_none, Toast.LENGTH_LONG).show()
                else -> {
                    Toast.makeText(context, context.getString(R.string.library_mix_done, done.playlists, done.songs), Toast.LENGTH_LONG).show()
                    onDone()
                    if (fragment.isAdded) {
                        fragment.findNavController().navigate(R.id.playlistCatalogueFragment, bundleOf(Constants.PLAYLIST_ALL to Constants.PLAYLIST_ALL))
                    }
                }
            }
        }
        dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).setOnClickListener {
            job.cancel()
            dialog.dismiss()
        }
    }
}
