# 歌单导入助手

## hub 模式（新版）

部署与账号配置见 [tempus-hub](../../services/tempus-hub/README.md)。助手默认读取 `~/Library/Application Support/tempus_playlist_import/hub.json`（权限 600），也可通过 `--hub-config /private/path/account.json` 选择账号。各账号单独读取待下载任务；hub 连接失败不会退回 SSH。上传后由 hub 在服务器扫描完成后确认入库、处理多版本并按原顺序回填已登记的歌单，待下载不再随每日歌单过期丢失。

旧 SSH 模式需要显式传入 `--legacy-user NAVIDROME_USERNAME`，查询仅限该用户的歌单。下方旧版流程和 comment 说明供过渡期参考；旧歌单在新版手机手动更新并保存一次后，才能登记完整顺序供 hub 自动回填。

手机负责网易云／QQ 歌单链接、完整曲目校验、曲库匹配和服务器歌单；Mac 负责已登录官方客户端下载后的音频校验与上传。下载需要用户在官方客户端执行，助手不会购买、登录或静默操作下载界面。更新均由用户手动发起。

## 使用

1. 手机打开「发现 → 导入外部歌单」，粘贴歌单／榜单分享链接，读取并匹配曲库。有多个准确版本时先确认，再保存服务器歌单。
2. Mac 双击 `启动歌单导入助手.command`，默认浏览器打开本机 `http://127.0.0.1:8769/`。点击「刷新手机任务」。
3. 在已登录的官方 QQ／网易云客户端打开来源歌单，下载音乐。助手提供缺失曲目列表及 CSV；下载尚未完成、权限不足或没有对应文件时，条目继续待处理。
4. 点击「校验并入库」，只处理此次请求需要的音频。QQ 复用现有 `unlock_music` 工具及全用户锁／冷却记录；网易云 NCM 在本机读取、校验并补标签。不会重新编码音频，也不删除源文件。
5. 上传复用原同步脚本的去重、暂存、SHA-256 校验和封面处理，并再次核对文件确实到达服务器。等待服务器扫描后，在手机手动更新并保存，补齐新增曲目。
6. 手机每日推荐挑出的库外新歌（7 天内）合并为「每日推荐新歌」，按平台各一份，处理方式与歌单相同，只是没有来源歌单链接，需在客户端搜索下载。

歌曲名、歌手、版本或时长不符、无可靠时长、损坏、正在变化的文件及试听片段不作为完整音乐入库。缺失项不会被当成成功。只有此次清单中校验通过的文件交给上传流程。

## 环境

- macOS、Python 3.9+、FFmpeg/ffprobe。
- 现有 `~/tools/unlock_music`，并已配置可用的音乐入库工具、QQ 登录与所需本机文件访问权限。
- 现有 SSH 别名 `mini_server`。默认 Navidrome 数据库为 `/home/daniel/services/qq-music/data/navidrome.db`。
- 默认网易云下载目录 `~/Music/网易云音乐`、`~/Music/CloudMusic`。客户端使用自定义下载位置时通过参数指定。

```sh
python3 companion.py --open
python3 companion.py --netease-dir "$HOME/Music/我的下载" --open
python3 companion.py --unlock-dir /path/to/unlock_music --ssh-host mini_server --server-db /path/to/navidrome.db
python3 companion.py --list
```

SSH／数据库参数控制任务读取。音频上传仍沿用 `unlock_music/sync_music.py` 中已配置的目标；改变服务器时两处配置应一致。

## 保存与边界

- 歌单是正常的 Subsonic 服务器歌单，新建时私有。客户端通过 POST 保存大歌单，避免把大量歌曲编号放入 URL。
- 来源、待下载曲目和任务编号保存在歌单 comment 的 `Tempus external playlist v1` 记录中，不包含音乐平台 Cookie、服务器密码或访问令牌。
- `complete` 表示本次歌单写入结束；是否缺音乐单独由 `missing` 列表表示。中断写入可继续，未完成记录不会交给电脑执行。
- Mac 通过已有 SSH 只读查询明确标记的导入请求，不直接修改 Navidrome 数据库。不增加服务器公网接口，也不传送平台登录信息。
- 浏览器接口仅监听本机，写操作检查 Host、Origin 和自定义请求头。临时入库清单只含本机已验证文件及校验值。
- 分享 API 需要能返回完整歌单；私有、需额外网页登录、被平台限制或部分返回的歌单会报错，不覆盖现有数据。没有伪造下载成功、定时同步或曲库自动删除。
- 匹配沿用现有标题／歌手／版本／时长规则，每个名称最多检查 40 个服务器候选；请求最多三个并发。可在手机核对其他候选并手动选定。
- 待下载请求元数据最多 1 MB，以保证手机歌单缓存可以正常读取；超限时会拒绝保存，需先入库部分音频或选择较小歌单，不会截断清单。

## 测试

```sh
python3 -m unittest discover -s tools/playlist_import -p 'test_*.py' -v
./local-build.sh testDegoogledDebugUnitTest --tests 'com.eddyizm.tempus.discovery.ExternalPlaylist*Test'
```

覆盖完整歌单、重复与顺序、拒绝截断数据、链接白名单、账号切换、写入中断后的草稿恢复、可见性保留、最多三个在途检索、只读任务查询、本机接口来源检查、按清单上传、文件变化检测、试听时长过滤及 NCM 流式解码。使用人工合成音频验证补标签前后压缩音频流不变。NCM 格式参考与 MIT 许可见 `LICENSE.unlock-music`。

开发时实际读取了网易云热歌榜的 200 首及 QQ 精选歌单的 1,038 首。真实的手机导入、服务器写入与两个客户端下载仍需在已连接手机、网易云客户端安装并登录后完成联合验证；合成文件测试不替代这些验证。
