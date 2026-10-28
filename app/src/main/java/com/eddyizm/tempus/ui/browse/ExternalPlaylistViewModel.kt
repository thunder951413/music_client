package com.eddyizm.tempus.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eddyizm.tempus.discovery.ChartTrack
import com.eddyizm.tempus.discovery.ExternalPlaylist
import com.eddyizm.tempus.discovery.ExternalPlaylistRepository
import com.eddyizm.tempus.discovery.ImportedPlaylist
import com.eddyizm.tempus.subsonic.models.Child
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExternalPlaylistViewModel : ViewModel() {
    data class State(
        val link: String = "", val busy: Boolean = false, val phase: String = "",
        val done: Int = 0, val total: Int = 0,
        val source: ExternalPlaylist? = null, val rows: List<ChartTrack> = emptyList(),
        val choices: Map<Int, Child> = emptyMap(), val imports: List<ImportedPlaylist> = emptyList(),
        val error: String? = null, val message: String? = null,
    ) {
        val matched: Int get() = rows.indices.count { it in choices || rows[it].matches.size == 1 }
        val ambiguous: Int get() = rows.indices.count { it !in choices && rows[it].matches.size > 1 }
        val missing: Int get() = rows.size - matched - ambiguous
    }
    private val repository = ExternalPlaylistRepository()
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private var operation: Job? = null

    init { refresh() }
    fun setLink(value: String) { mutable.update { it.copy(link = value, source = null, rows = emptyList(), choices = emptyMap()) } }
    fun choose(index: Int, song: Child) { mutable.update { it.copy(choices = it.choices + (index to song)) } }
    fun cancel() { operation?.cancel(); mutable.update { it.copy(busy = false, phase = "") } }
    fun refresh() = viewModelScope.launch {
        try { val saved = repository.imports(); mutable.update { it.copy(imports = saved) } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { mutable.update { it.copy(error = e.message ?: "无法读取服务器导入歌单") } }
    }
    fun load(link: String = state.value.link) {
        operation?.cancel()
        operation = viewModelScope.launch {
            mutable.update { it.copy(link = link, busy = true, phase = "正在读取完整歌单…", source = null, rows = emptyList(), choices = emptyMap(), done = 0, total = 0, error = null, message = null) }
            try {
                val source = repository.load(link)
                mutable.update { it.copy(source = source, phase = "正在匹配服务器曲库…") }
                val rows = repository.match(source) { done, total -> mutable.update { it.copy(done = done, total = total) } }
                mutable.update { it.copy(rows = rows, busy = false, phase = "") }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(busy = false, phase = "", error = e.message ?: "读取或匹配未完成，请重试") } }
        }
    }
    fun save() {
        val preview = state.value
        val source = preview.source ?: return
        if (preview.busy || preview.ambiguous > 0) return
        operation = viewModelScope.launch {
            mutable.update { it.copy(busy = true, phase = "正在保存到音乐服务器…", error = null, message = null) }
            try {
                val saved = repository.save(source, preview.rows, preview.choices)
                mutable.update { it.copy(busy = false, phase = "", message = if (saved.binding.missing.isEmpty()) "歌单和全部歌曲已在服务器就绪" else if (com.eddyizm.tempus.hub.HubRepository.isEnabled()) "歌单已保存，${saved.binding.missing.size} 首等待电脑客户端下载、校验并入库，完成后会自动补回歌单。" else "歌单已保存，${saved.binding.missing.size} 首等待电脑客户端下载、校验并入库。完成后点手动更新继续匹配。") }
                refresh()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                mutable.update { it.copy(busy = false, phase = "", error = e.message ?: "服务器保存未完成，请刷新后重试") }
                refresh()
            }
        }
    }
}
