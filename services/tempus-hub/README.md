# tempus-hub

Rust 服务，集中管理设备播放事件、每日推荐和待下载任务；曲库和歌单仍通过 Subsonic API 交给 Navidrome。源码、SQL 迁移、部署和回归测试均在本目录，运行数据不进入 Git。

## 已确认的第一阶段口径

- 从第一阶段支持同一 Navidrome 下的多个账号。设备、事件、推荐、导入和待下载任务均按账号隔离。
- 各设备独立积累偏好和生成推荐。种子及常听次数只使用该设备上传的完整播放事件；账号级红心和播放次数不参与画像。首次使用从可访问的曲库随机探索。
- 一个账号下的待下载歌曲跨设备、跨平台按规范化标题和主歌手合并；保留版本词。忽略和完成状态持久保留，不跟随七天歌单清理而消失。
- 手机保留版本选择；Mac 保留官方客户端下载、音频校验和已有上传流程。hub 确认歌曲已进入 Navidrome 后才标记完成并回填。

## 数据和授权

手机和 Mac 使用 `X-Tempus-Auth` 请求头：标准 Base64 编码的 UTF-8 JSON，字段为 Subsonic 的 `u`、`t`、`s`。它是可复用的访问凭证，不能放在 URL、日志、工单或 Git 中。每次请求都向固定配置的 Navidrome `ping` 验证，不接受请求方指定上游地址，也不跟随重定向。

手机登记设备时，hub 将验证通过的令牌和 salt 加密保存，供手机离线时的定时任务使用。采用 ChaCha20-Poly1305，账号名称绑定为附加认证数据；随机主密钥位于 `state/master.key`，数据库位于 `state/hub.sqlite`。目录权限 700、文件权限 600，systemd 的 UMask 为 0077。不保存账号密码或 QQ／网易云凭证。更改 Navidrome 密码后，在手机重新登录并打开应用，更新后台授权。

备份应包含整个私有 `state/` 目录；丢失密钥后，旧凭证无法解密。运行中的 SQLite 应使用 SQLite backup API 或停服务后备份，不能只复制主数据库而忽略 WAL。Navidrome 的曲库和账号数据库不由 hub 修改。

`migrations/001.sql` 创建独立表和索引。播放事件的主键为 `(账号,设备,事件编号)`；同一事件重复上传只计一次，重复编号携带不同内容会拒绝。歌曲多次播放保留多条事件。

## 运行

```sh
cargo build --release --locked
NAVIDROME_URL=http://127.0.0.1:4533 \
HUB_STATE_DIR=/absolute/private/path \
HUB_TIMEZONE=Asia/Singapore \
HUB_LISTEN=127.0.0.1:4534 \
./target/release/tempus-hub
```

默认每天 05:00 后生成当天推荐，启动时补跑，手机请求当天推荐时也能触发。每分钟重新匹配待下载任务，每六小时刷新八个榜单。超过三十天未出现的设备暂停生成，已有设备信息和事件保留。推荐歌单保留当天及前六天；只清理由 hub 记录的歌单。曲库不足或歌手上限限制时可能少于三十首。

服务启动时尝试刷新榜单，失败保留上次成功缓存；榜单失败不影响库内推荐。`HUB_CHARTS_ENABLED=false` 用于离线测试或暂停外部榜单请求。

手机每次播放结束或主动切歌时持久化事件，在应用进程存活期间每分钟重试，并在播放和进入首页时尝试同步。离线队列保留到成功确认；进程被关闭时不会声称仍在后台上传，下次打开继续。hub 已被识别后，连接失败显示缓存，不再启动另一套手机生成流程。没有 hub 的旧服务器继续使用原有手机推荐。

## 接口

健康检查无需认证，其他接口均需要请求头认证。

| 方法与路径 | 行为 |
| --- | --- |
| `GET /hub/v1/health` | 服务标识和协议版本 |
| `POST /hub/v1/devices` | `{id,name}` 登记当前账号的设备及后台授权 |
| `POST /hub/v1/plays` | `{device,events:[{event_id,song_id,at,completed,position_ms,duration_ms}]}`；毫秒时间戳，每批最多 200 条 |
| `GET /hub/v1/daily?device=...` | 当前账号和设备最近可用的推荐；`stale` 表示非当天结果，空 `playlist_id` 表示等待生成 |
| `GET /hub/v1/wishes` | 当前账号的待下载、完成、忽略及版本不明确条目 |
| `POST /hub/v1/wishes/{wish_id}` | `{state}` 忽略／重新排队；手动完成还必须提交匹配的 `song_id` |
| `POST /hub/v1/imports` | 手机提交已保存歌单的完整顺序、每个位置的平台歌曲和用户选择的服务器歌曲 |
| `POST /hub/v1/refresh` | 请求后台重新匹配和生成；返回排队状态，不表示入库完成 |

导入保留重复歌曲的位置，先持久化预期歌单和待写入结果，再通过 Subsonic 替换曲目并回读校验。响应丢失后可重试。如果发现人工增删或顺序变化，停止自动回填，在手机手动更新确认后重新登记。多个准确版本不会自动挑选；仍保留为 `ambiguous`。

旧版 comment 中属于当前账号的未完成下载会迁入 wishes。旧 comment 没有完整位置，需在升级后的手机对旧导入歌单执行一次手动更新并保存，才可使用保持顺序的自动回填。

## 部署与回退

先运行全部测试，再把源码包传到 `~/services/tempus-hub/releases/<版本或提交>/`。服务运行版本通过 `current` 链接确定，`previous` 保留上一版。不要把临时修改只留在服务器。

- `deploy/build-server.sh`：在 Linux 使用构建容器，或通过 `HUB_CARGO` 指定专用 Cargo。网络受限时可用 `HUB_VENDOR_DIR` 指向经 Cargo 校验的离线依赖目录。使用 lockfile，不将工具链或构建缓存写入 Git。
- `deploy/install-server.sh`：安装 systemd 用户服务、启用并启动。默认只监听本机 4534。
- `deploy/nginx-location.conf`：已有同机／跨主机代理时可直接加入 `/hub/` 路由。
- `deploy/install-gateway.py`：适用于当前 mini_server。备份 Navidrome 配置，将 Navidrome 改为只监听本机 4536，使用独立 nginx 用户服务接管原 4533，分别转发音乐和 hub。公网代理继续访问原 4533。语法和切换后健康检查失败时恢复原配置。
- `deploy/rollback-gateway.sh`：停统一代理，恢复备份的 Navidrome 配置和原 4533。

查看状态：

```sh
systemctl --user status tempus-hub.service tempus-gateway.service qq-music.service
journalctl --user -u tempus-hub.service -n 50 --no-pager
```

Mac 使用账号专用配置。手机首次打开并登记成功后，可由服务器管理员直接导出到私有文件，**不要把输出显示到终端或聊天**：

```sh
umask 077
mkdir -p "$HOME/Library/Application Support/tempus_playlist_import"
ssh mini_server 'HUB_STATE_DIR="$HOME/services/tempus-hub/state" "$HOME/services/tempus-hub/current/target/release/tempus-hub" export-client YOUR_USER https://YOUR_MUSIC_SERVER' \
  > "$HOME/Library/Application Support/tempus_playlist_import/hub.json"
```

助手默认读取上述配置。多个账号使用各自的文件，通过 `--hub-config` 选择；hub 不可用时不会退回 SSH 混读任务。配置默认指向现有 `mini_server` 音频上传目标，其他部署需要同步调整 `ssh_host` 和原上传脚本。

## 验证

```sh
cargo fmt --check
cargo clippy --all-targets -- -D warnings
cargo test --locked
cargo build --locked
python3 -m unittest discover -s tests -v
```

Rust 测试覆盖设备画像、冷启动、歌手上限、匹配、重复播放和凭证加密恢复；HTTP 集成测试使用本地模拟 Navidrome，覆盖账号／设备隔离、重传、完整顺序和重复项回填、版本不明确及人工编辑保护。真实设备和真实客户端下载另行验证，不能用模拟测试替代。

管理员可使用 `tests/live_smoke.py --hub-config /private/account.json --library-fixture` 验证真实服务的回填和重复顺序。该模式使用库内音频与明确的合成平台编号，仅验证联动机制；不表示已从平台获取或下载新音乐。它会创建私有临时歌单，并通过管理员 SSH 仅清理自己的测试记录。实际客户端下载和新文件扫描需单独验收。

2026-10-28 已在 mini_server 部署，局域网和 HTTPS 入口健康检查通过，PMX110 上展示了三十首推荐和五首待下载；真机播放、切歌、结束事件及断网重传通过。Linux release 二进制通过四项 HTTP 回归，Rust 六项单元测试、Mac 十七项测试通过。完整验证范围及真机前后截图见 [MUSIC_DISCOVERY.md](../../MUSIC_DISCOVERY.md)。
