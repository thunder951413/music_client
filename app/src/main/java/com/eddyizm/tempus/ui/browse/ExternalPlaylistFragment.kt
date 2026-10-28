package com.eddyizm.tempus.ui.browse

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.eddyizm.tempus.R

class ExternalPlaylistFragment : Fragment() {
    private val model: ExternalPlaylistViewModel by viewModels()
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BrowseTheme {
                    val state by model.state.collectAsState()
                    var choosing by remember { mutableStateOf<Int?>(null) }
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
                        LazyColumn(
                            Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp,
                                bottom = dimensionResource(R.dimen.global_padding_bottom) + 20.dp),
                        ) {
                            item { Row {
                                IconButton(onClick = { findNavController().popBackStack() }) {
                                    Icon(painterResource(R.drawable.ic_arrow_back), "返回")
                                }
                                Text("导入外部歌单", fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 9.dp))
                            } }
                            item { Text("网易云音乐 · QQ 音乐\n支持榜单、精选歌单和可分享的个人歌单。歌单保存在服务器，之后由你手动更新。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            item { OutlinedTextField(value = state.link, onValueChange = model::setLink, enabled = !state.busy,
                                label = { Text("粘贴歌单分享链接") }, modifier = Modifier.fillMaxWidth(), maxLines = 4) }
                            item { Button(onClick = { model.load() }, enabled = !state.busy && state.link.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("读取歌单并匹配曲库") } }
                            if (state.busy) item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(state.phase + if (state.total > 0) " ${state.done} / ${state.total}" else "")
                                    LinearProgressIndicator(Modifier.fillMaxWidth())
                                    TextButton(onClick = model::cancel) { Text("取消") }
                                }
                            }
                            state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
                            state.message?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.primary) } }
                            state.source?.takeIf { !state.busy && state.rows.size == it.tracks.size }?.let { source ->
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(source.name, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                        Text("共 ${state.rows.size} 首 · 已匹配 ${state.matched} · 缺失 ${state.missing} · 待确认版本 ${state.ambiguous}")
                                        Text("缺失歌曲通过电脑上已登录的官方客户端下载，再校验、上传到服务器。更新会按外部歌单重排此导入歌单，不会删除音频文件。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Button(onClick = model::save, enabled = state.ambiguous == 0, modifier = Modifier.fillMaxWidth()) { Text("保存／更新服务器歌单") }
                                    }
                                }
                                itemsIndexed(state.rows, key = { index, row -> "$index:${row.id}" }) { index, row ->
                                    val chosen = state.choices[index] ?: row.matches.singleOrNull()
                                    Column(Modifier.fillMaxWidth().clickable(enabled = row.alternatives.isNotEmpty()) { choosing = index }.padding(vertical = 8.dp)) {
                                        Text("${index + 1}. ${row.title}", fontWeight = FontWeight.Medium)
                                        Text(row.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(when {
                                            chosen != null -> "已匹配 · ${chosen.title}"
                                            row.matches.size > 1 -> "有多个版本，点击确认"
                                            row.alternatives.isNotEmpty() -> "未找到准确版本 · 点击核对候选或在客户端下载"
                                            else -> "待客户端下载并入库"
                                        }, fontSize = 12.sp, color = if (chosen != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            item { HorizontalDivider(); Text("已导入的服务器歌单", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp)) }
                            if (state.imports.isEmpty()) item { Text("尚无导入歌单", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            itemsIndexed(state.imports, key = { _, entry -> entry.playlist.id }) { _, entry ->
                                Column {
                                    Text(entry.playlist.name.orEmpty(), fontWeight = FontWeight.Bold)
                                    Text(if (!entry.binding.complete) "上次保存未完成，请继续更新" else if (entry.binding.missing.isEmpty()) "全部曲目已就绪" else "${entry.binding.missing.size} 首待下载入库", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    TextButton(onClick = { model.load(entry.binding.url) }, enabled = !state.busy) { Text("手动更新／入库后重新匹配") }
                                }
                            }
                        }
                    }
                    choosing?.let { index ->
                        val row = state.rows.getOrNull(index)
                        if (row != null) AlertDialog(onDismissRequest = { choosing = null }, title = { Text("确认歌曲版本") },
                            text = { LazyColumn(Modifier.heightIn(max = 380.dp)) {
                                itemsIndexed(row.alternatives) { _, song -> Text(
                                    "${song.title.orEmpty()}\n${song.artist.orEmpty()} · ${song.album.orEmpty()}",
                                    modifier = Modifier.fillMaxWidth().clickable { model.choose(index, song); choosing = null }.padding(vertical = 12.dp),
                                ) }
                            } }, confirmButton = { TextButton(onClick = { choosing = null }) { Text("返回") } })
                    }
                }
            }
        }
}
