#!/usr/bin/env python3
"""Manual desktop-client download -> validated audio -> existing Navidrome upload pipeline.

No music-account cookies leave the Mac. The phone writes source bindings/download requests
in its own server playlists; this helper reads only those explicit requests over existing SSH.
It does not scrape streaming URLs or silently drive the official clients' download UI.
"""
import argparse
import copy
import csv
import fcntl
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import importlib
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import unicodedata
from checked_worker import check_duration

import ncm
from hub_client import HubClient

PREFIX = 'Tempus external playlist v1\n'
DAILY_PREFIX = 'Tempus daily mix v1\n'
CONTROL = Path.home() / 'Library/Application Support/tempus_playlist_import'
UNLOCK = Path.home() / 'tools/unlock_music'
NETEASE_DIRS = [Path.home() / 'Music/网易云音乐', Path.home() / 'Music/CloudMusic']
MAX_TRACKS = 5000


def normalize(value):
    return ''.join(c for c in unicodedata.normalize('NFKC', str(value or '')).lower()
                   if not c.isspace() and unicodedata.category(c)[0] not in 'PZ')


def check_track(provider, track):
    pattern = r'[0-9]{1,20}' if provider == 'NETEASE' else r'[A-Za-z0-9]{1,64}'
    if (not isinstance(track, dict) or not re.fullmatch(pattern, str(track.get('id', ''))) or not isinstance(track.get('title'), str)
            or not isinstance(track.get('artists'), list) or not track['artists']
            or not all(isinstance(a, str) for a in track['artists'])):
        raise ValueError('下载曲目信息无效')
    if track.get('duration') is not None and (not isinstance(track['duration'], int) or not 0 <= track['duration'] <= 86400):
        raise ValueError('歌曲时长无效')
    # Derive links ourselves; untrusted comments cannot inject a browser target or path.
    track['url'] = (f"https://music.163.com/song?id={track['id']}" if provider == 'NETEASE'
                    else f"https://y.qq.com/n/ryqq_v2/songDetail/{track['id']}")


def validate_daily(row):
    """New chart songs picked for one day's mix on the phone; each names its own platform."""
    comment = row.get('comment', '')
    if not comment.startswith(DAILY_PREFIX) or len(comment) > 200_000:
        raise ValueError('不是有效的每日推荐')
    data = json.loads(comment[len(DAILY_PREFIX):])
    if data.get('version') != 1 or data.get('complete') is not True or not re.fullmatch(r'\d{4}-\d{2}-\d{2}', str(data.get('date', ''))):
        raise ValueError('每日推荐记录无效')
    missing = data.get('missing')
    if not isinstance(missing, list) or len(missing) > 50:
        raise ValueError('每日推荐曲目清单无效')
    for track in missing:
        if not isinstance(track, dict) or track.get('provider') not in ('QQ', 'NETEASE'):
            raise ValueError('每日推荐来源无效')
        check_track(track['provider'], track)
    return data['date'], missing


def daily_jobs(rows):
    """All days' wishes, one job per platform, each song once."""
    tracks = {'QQ': {}, 'NETEASE': {}}
    for _, missing in sorted(rows, key=lambda row: row[0], reverse=True):
        for track in missing:
            tracks[track['provider']].setdefault(track['id'], {k: track[k] for k in ('id', 'title', 'artists', 'duration', 'url') if k in track})
    return [{'id': 'daily-' + provider, 'name': '每日推荐新歌', 'provider': provider, 'url': '',
             'missing': list(found.values()), 'request_id': 'daily'} for provider, found in tracks.items() if found]


def validate_request(row):
    comment = row.get('comment', '')
    if not comment.startswith(PREFIX) or len(comment) > 2_000_000:
        raise ValueError('不是有效的导入请求')
    data = json.loads(comment[len(PREFIX):])
    if data.get('version') != 1 or data.get('complete') is not True:
        raise ValueError('手机上的歌单尚未保存完成，请先在手机继续更新')
    provider = data.get('provider')
    identifier = str(data.get('sourceId', ''))
    if provider not in ('QQ', 'NETEASE') or not re.fullmatch(r'[1-9][0-9]{0,19}', identifier):
        raise ValueError('歌单来源无效')
    kind = 'toplist' if data.get('chart') else 'playlist'
    canonical = f'https://music.163.com/playlist?id={identifier}' if provider == 'NETEASE' else f'https://y.qq.com/n/ryqq_v2/{kind}/{identifier}'
    if data.get('url') != canonical:
        raise ValueError('歌单链接与来源编号不一致')
    missing = data.get('missing')
    total = data.get('total')
    if not isinstance(total, int) or not 0 <= total <= MAX_TRACKS or not isinstance(missing, list) or len(missing) > total:
        raise ValueError('歌单曲目清单无效')
    for track in missing:
        check_track(provider, track)
    return {'id': str(row['id']), 'name': str(row['name']), 'provider': provider,
            'url': canonical, 'missing': missing, 'request_id': str(data.get('requestId', ''))}


def read_requests(host='mini_server', database='/home/daniel/services/qq-music/data/navidrome.db', user=None):
    script = '''import sqlite3,json
c=sqlite3.connect('file:'+''' + repr(database) + '''+'?mode=ro',uri=True)
c.row_factory=sqlite3.Row
rows=c.execute("SELECT id,name,comment FROM playlist WHERE (comment LIKE ? OR comment LIKE ?) AND length(comment)<2000000"+''' + repr(' AND owner_id=(SELECT id FROM user WHERE user_name=?)' if user else '') + ''',('Tempus external playlist v1\\n%','Tempus daily mix v1\\n%')+''' + repr((user,) if user else ()) + ''')
print(json.dumps([dict(r) for r in rows],ensure_ascii=False))'''
    # argv and a quoted fixed Python script, never shell interpolation of provider data.
    result = subprocess.run(['ssh', '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=8', host,
                             'python3 -c ' + shlex.quote(script)], capture_output=True, text=True, timeout=35)
    if result.returncode:
        raise RuntimeError('无法读取音乐服务器的导入请求，请检查 mini_server 的 SSH 连接')
    rows = json.loads(result.stdout)
    if not isinstance(rows, list):
        raise ValueError('服务器返回的数据无效')
    jobs, issues, daily = [], [], []
    for row in rows:
        if str(row.get('comment', '')).startswith(DAILY_PREFIX):
            try:
                daily.append(validate_daily(row))
            except (ValueError, KeyError, TypeError):
                issues.append('有一份每日推荐记录无效，已跳过。')
            continue
        try:
            job = validate_request(row)
            if job['missing']:
                jobs.append(job)
        except (ValueError, KeyError, TypeError):
            issues.append('有一份未完成或无效的导入请求，请在手机中重新保存。')
    return jobs + daily_jobs(daily), issues


def export_requests(jobs, root=CONTROL):
    root.mkdir(parents=True, exist_ok=True, mode=0o700)
    for job in jobs:
        label = hashlib.sha256(job['id'].encode()).hexdigest()[:16]
        destination = root / (label + '.csv')
        with destination.open('w', encoding='utf-8-sig', newline='') as file:
            os.chmod(destination, 0o600)
            writer = csv.writer(file)
            writer.writerow(['歌单', '平台', '歌曲', '歌手', '歌曲链接'])
            for track in job['missing']:
                # CSV opens safely in spreadsheets even when a provider supplies formula text.
                values = [job['name'], job['provider'], track['title'], ' / '.join(track['artists']), track['url']]
                writer.writerow(["'" + value if value.startswith(('=', '+', '-', '@', '\t', '\r')) else value for value in values])


def audio_info(path):
    result = subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration:format_tags=title,artist,album',
                             '-of', 'json', str(path)], capture_output=True, text=True, timeout=40)
    if result.returncode:
        raise ValueError('音频无法完整读取')
    data = json.loads(result.stdout).get('format', {})
    tags = {k.lower(): v for k, v in data.get('tags', {}).items()}
    return tags, float(data.get('duration', 0))


def matches(track, tags, duration):
    if not normalize(track['title']) or normalize(track['title']) != normalize(tags.get('title')):
        return False
    names = {normalize(a) for a in track['artists']}
    local = {normalize(tags.get('artist'))} | {normalize(a) for a in re.split(r'[/,;&、，]', tags.get('artist', ''))}
    if not names.intersection(local) or duration <= 0:
        return False
    return not track.get('duration') or abs(duration - track['duration']) <= 12


def audio_stream_hash(path):
    result = subprocess.run(['ffmpeg', '-nostdin', '-v', 'error', '-i', str(path), '-map', '0:a:0',
                             '-c:a', 'copy', '-f', 'hash', '-hash', 'sha256', '-'],
                            capture_output=True, timeout=180)
    if result.returncode:
        raise ValueError('无法校验音频压缩流')
    return result.stdout.strip()


def publish_plain(path, track, root, music, metadata=None):
    before = (path.stat().st_size, path.stat().st_mtime_ns)
    tags, duration = audio_info(path)
    if metadata:
        artists = metadata.get('artist', [])
        if isinstance(artists, str):
            artist = artists
        elif isinstance(artists, list) and all(isinstance(a, str) or (isinstance(a, list) and a and isinstance(a[0], str)) for a in artists):
            artist = ' / '.join(a[0] if isinstance(a, list) else a for a in artists)
        else:
            raise ValueError('NCM 歌手信息无效')
        tags = {'title': metadata.get('musicName', '') or '', 'artist': artist, 'album': metadata.get('album', '') or ''}
    if not matches(track, tags, duration):
        raise ValueError('歌曲名称、歌手、版本或时长不匹配；不会将试听片段作为完整歌曲入库')
    check_duration(duration, track.get('duration'))
    fmt = music.inspect_file(path).get('format')
    if fmt not in ('mp3', 'flac', 'ogg', 'm4a', 'wav') or not music.valid_audio(path, fmt):
        raise ValueError('音频完整性校验未通过')
    digest = music.fingerprint(path)
    destination = root / ('Tempus-' + digest[:24] + '.' + fmt)
    if destination.is_symlink():
        raise ValueError('目标音乐路径不能是符号链接')
    if destination.exists():
        existing_tags, existing_duration = audio_info(destination)
        if (not matches(track, existing_tags, existing_duration) or not music.valid_audio(destination, fmt)
                or audio_stream_hash(path) != audio_stream_hash(destination)):
            raise ValueError('同名入库成品已发生变化，请检查后重试；不会覆盖它')
        return destination
    if shutil.disk_usage(root).free < path.stat().st_size * 2 + 100 * 1024 * 1024:
        raise ValueError('音乐目录磁盘空间不足')
    with tempfile.TemporaryDirectory(prefix='.tempus-import-', dir=root) as work:
        staged = Path(work) / ('audio.' + fmt)
        if metadata:
            args = ['ffmpeg', '-nostdin', '-v', 'error', '-i', str(path), '-map', '0:a:0', '-c:a', 'copy']
            for key, value in tags.items():
                args += ['-metadata', f'{key}={value}']
            result = subprocess.run(args + [str(staged)], capture_output=True, timeout=180)
            if result.returncode:
                raise ValueError('无法补全 NCM 曲目信息')
        else:
            shutil.copyfile(path, staged)
        if before != (path.stat().st_size, path.stat().st_mtime_ns):
            raise ValueError('文件仍在下载，请下载完成后重试')
        if not music.valid_audio(staged, fmt):
            raise ValueError('发布前音频校验未通过')
        if metadata and audio_stream_hash(path) != audio_stream_hash(staged):
            raise ValueError('补标签改变了压缩音频流，已停止发布')
        if not metadata and music.fingerprint(staged) != digest:
            raise ValueError('音频复制校验失败')
        os.replace(staged, destination)
    return destination


class Companion:
    def __init__(self, unlock=UNLOCK, netease_dirs=None, host='mini_server', database='/home/daniel/services/qq-music/data/navidrome.db', hub_config=None, legacy_user=None):
        self.unlock = Path(unlock)
        self.netease_dirs = netease_dirs or NETEASE_DIRS
        self.host = host
        self.database = database
        default_config = CONTROL / 'hub.json'
        config = hub_config or (default_config if default_config.is_file() else None)
        self.hub = HubClient(config) if config else None
        self.legacy_user = legacy_user
        if self.hub and self.hub.host != host:
            raise ValueError('hub 下载任务与音频上传的 SSH 目标不一致')
        self.lock = threading.RLock()
        self.state = {'running': False, 'phase': 'idle', 'message': '先在手机保存导入歌单，再刷新电脑任务。', 'jobs': [], 'issues': [], 'processed': 0}

    def snapshot(self):
        with self.lock:
            return copy.deepcopy(self.state)

    def update(self, **values):
        with self.lock:
            self.state.update(values)

    def refresh(self):
        if not self.hub and not self.legacy_user:
            raise ValueError('请先配置账号专用的 hub.json；旧版 SSH 模式需要显式指定 --legacy-user，避免混读其他账号的任务')
        jobs, issues = self.hub.jobs(check_track) if self.hub else read_requests(self.host, self.database, self.legacy_user)
        export_requests(jobs)
        self.update(jobs=jobs, issues=issues, message=f'找到 {len(jobs)} 份待下载歌单。请在官方客户端下载后，点击校验并入库。')

    def start(self):
        with self.lock:
            if self.state['running']:
                raise ValueError('任务正在运行')
            self.state.update(running=True, phase='checking', message='正在检查下载文件…', processed=0)
        threading.Thread(target=self.run, daemon=True).start()

    def run(self):
        try:
            CONTROL.mkdir(parents=True, exist_ok=True, mode=0o700)
            with (CONTROL / 'worker.lock').open('a') as file_lock:
                fcntl.flock(file_lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                self.refresh()
                jobs = self.snapshot()['jobs']
                if not jobs:
                    self.update(phase='done', message='没有待处理请求。')
                    return
                if not (self.unlock / 'qq_decrypt.py').is_file() or not (self.unlock / 'sync_music.py').is_file():
                    raise ValueError('未找到现有音乐入库工具，请配置 unlock_music 路径')
                sys.path.insert(0, str(self.unlock))
                music = importlib.import_module('qq_decrypt')
                sync = importlib.import_module('sync_music')
                if sync.HOST != self.host:
                    raise ValueError('下载任务与音频上传的目标服务器不一致，请先统一配置')
                root = music.DEFAULT_ROOT
                root.mkdir(parents=True, exist_ok=True)
                wanted = {provider: {} for provider in ('QQ', 'NETEASE')}
                for job in jobs:
                    for track in job['missing']:
                        wanted[job['provider']][track['id']] = track
                issues, processed, ready = [], 0, {}
                qq_paths = music.collect_files(root, music.source_roots(root), root / 'decrypted', recursive=True)
                for path in qq_paths:
                    try:
                        info = music.inspect_file(path)
                        if info.get('kind') == 'musicex' and info.get('mid') in wanted['QQ']:
                            self.update(phase='processing', message='正在校验 QQ 下载：' + path.name)
                            track = wanted['QQ'][info['mid']]
                            proc = subprocess.run([sys.executable, str(Path(__file__).with_name('checked_worker.py')), 'qq',
                                                  '--unlock-dir', str(self.unlock), '--source', str(path), '--output', str(root / 'decrypted'),
                                                  '--duration', str(track.get('duration') or 0)],
                                                  stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True, timeout=1800)
                            if proc.returncode:
                                raise ValueError('QQ 下载处理未完成，请查看原音乐管理工具，确认登录和下载权限')
                            record = music.State(root / 'decrypted/.qq-decrypt-state.json').data['success'].get(str(path.resolve()))
                            if not record:
                                raise ValueError('未找到已校验的完整音频记录')
                            check_duration(record.get('duration_seconds', 0), track.get('duration'))
                            decoded = root / 'decrypted' / record['output']
                            if not decoded.resolve().is_relative_to((root / 'decrypted').resolve()) or music.fingerprint(decoded) != record['output_sha256']:
                                raise ValueError('QQ 成品记录与音频不一致')
                            ready[str(decoded)] = record['output_sha256']
                            processed += 1
                        elif info.get('kind') == 'plain' and wanted['QQ']:
                            tags, duration = audio_info(path)
                            matching = [track for track in wanted['QQ'].values() if matches(track, tags, duration)]
                            if matching:
                                check_duration(duration, matching[0].get('duration'))
                                if not music.valid_audio(path, info.get('format')):
                                    raise ValueError('QQ 音频完整性校验未通过')
                                if path.parent.resolve() != root.resolve():
                                    path = publish_plain(path, matching[0], root, music)
                                ready[str(path)] = music.fingerprint(path)
                                processed += 1
                    except (ValueError, OSError, subprocess.SubprocessError) as error:
                        issues.append(path.name + '：' + str(error)[:160])
                for folder in self.netease_dirs:
                    folder = Path(folder).expanduser()
                    if not folder.is_dir():
                        continue
                    for path in folder.rglob('*'):
                        if path.is_symlink() or not path.is_file() or not wanted['NETEASE']:
                            continue
                        try:
                            if path.suffix.lower() == '.ncm':
                                info = ncm.inspect(path)
                                track = wanted['NETEASE'].get(str(info.metadata.get('musicId', '')))
                                if not track:
                                    continue
                                self.update(phase='processing', message='正在校验网易云下载：' + track['title'])
                                with tempfile.TemporaryDirectory(prefix='.tempus-ncm-', dir=root) as temporary:
                                    fmt = info.metadata.get('format', 'flac')
                                    if fmt not in ('flac', 'mp3', 'ogg', 'm4a', 'wav'):
                                        raise ValueError('不支持此 NCM 音频格式')
                                    decoded = Path(temporary) / ('audio.' + fmt)
                                    ncm.decode(path, decoded, info)
                                    published = publish_plain(decoded, track, root, music, info.metadata)
                                    ready[str(published)] = music.fingerprint(published)
                                processed += 1
                            elif path.suffix.lower() in ('.flac', '.mp3', '.ogg', '.m4a', '.wav'):
                                tags, duration = audio_info(path)
                                candidates = [track for track in wanted['NETEASE'].values() if matches(track, tags, duration)]
                                if candidates:
                                    published = publish_plain(path, candidates[0], root, music)
                                    ready[str(published)] = music.fingerprint(published)
                                    processed += 1
                        except (ValueError, OSError, subprocess.SubprocessError) as error:
                            issues.append(path.name + '：' + str(error)[:160])
                self.update(phase='uploading', message='正在使用已有流程校验、上传并补全封面…', processed=processed, issues=issues[-50:])
                if ready:
                    wait = max(0, max(15 - (time.time() - Path(path).stat().st_mtime) for path in ready))
                    if wait:
                        self.update(phase='checking', message='等待文件稳定后上传…')
                        time.sleep(wait + 0.2)
                    with tempfile.TemporaryDirectory(prefix='checked-', dir=CONTROL) as temporary:
                        manifest = Path(temporary) / 'audio.json'
                        manifest.write_text(json.dumps([{'path': path, 'sha256': digest} for path, digest in ready.items()]))
                        os.chmod(manifest, 0o600)
                        proc = subprocess.run([sys.executable, str(Path(__file__).with_name('checked_worker.py')), 'upload',
                                              '--unlock-dir', str(self.unlock), '--manifest', str(manifest), '--expected-host', self.host],
                                              stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True, timeout=7200)
                        if proc.returncode:
                            raise ValueError('服务器上传未完成，请在原音乐管理工具查看同步结果')
                else:
                    self.update(phase='waiting', message='未找到与待补齐清单匹配的完整下载。请先在官方客户端下载音乐，再重试。', issues=issues[-50:])
                    return
                if self.hub:
                    self.hub.refresh()
                    message = '本机可用音频已上传。hub 将在服务器扫描后确认入库并回填歌单；未匹配或版本不明确的曲目继续保留。'
                else:
                    message = '本机可用音频已处理并上传。等待服务器扫描后，在手机点“手动更新／入库后重新匹配”；仍缺失的曲目会继续保留。'
                self.update(phase='waiting', message=message, processed=processed)
        except Exception as error:
            self.update(phase='error', message=str(error)[:250])
        finally:
            self.update(running=False)


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def allowed(self, write=False):
        hosts = {f'127.0.0.1:{self.server.server_port}', f'localhost:{self.server.server_port}'}
        if self.headers.get('Host') not in hosts:
            return False
        if write:
            return (self.headers.get('X-Tempus-Import') == '1'
                    and self.headers.get('Origin', f'http://127.0.0.1:{self.server.server_port}') in {'http://' + h for h in hosts})
        return True

    def send(self, code, body, content_type='application/json; charset=utf-8'):
        data = body.encode() if isinstance(body, str) else json.dumps(body, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header('Content-Type', content_type)
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if not self.allowed():
            return self.send(403, {'error': '仅允许本机访问'})
        if self.path == '/':
            return self.send(200, Path(__file__).with_name('dashboard.html').read_text(), 'text/html; charset=utf-8')
        if self.path == '/api/status':
            return self.send(200, self.server.companion.snapshot())
        self.send(404, {'error': '未找到入口'})

    def do_POST(self):
        if not self.allowed(write=True):
            return self.send(403, {'error': '请求来源无效'})
        if self.server.companion.snapshot()['running']:
            return self.send(409, {'error': '任务正在运行'})
        try:
            if self.path == '/api/refresh':
                self.server.companion.refresh()
            elif self.path == '/api/start':
                self.server.companion.start()
            else:
                return self.send(404, {'error': '未找到入口'})
            self.send(200, self.server.companion.snapshot())
        except Exception as error:
            self.send(400, {'error': str(error)[:250]})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, default=8769)
    parser.add_argument('--unlock-dir', type=Path, default=UNLOCK)
    parser.add_argument('--netease-dir', action='append', type=Path)
    parser.add_argument('--ssh-host', default='mini_server')
    parser.add_argument('--server-db', default='/home/daniel/services/qq-music/data/navidrome.db')
    parser.add_argument('--hub-config', type=Path, help='账号专用的 hub JSON 配置（权限 600），启用后不再通过 SSH 查询任务')
    parser.add_argument('--legacy-user', help='旧版 SSH 读取模式的 Navidrome 用户名，仅在没有 hub 配置时使用')
    parser.add_argument('--open', action='store_true', help='启动后在默认浏览器打开本机助手')
    parser.add_argument('--list', action='store_true', help='只读列出手机提交的下载任务')
    args = parser.parse_args()
    companion = Companion(args.unlock_dir, args.netease_dir, args.ssh_host, args.server_db, args.hub_config, args.legacy_user)
    if args.list:
        companion.refresh()
        state = companion.snapshot()
        print(json.dumps({'jobs': [{'name': j['name'], 'provider': j['provider'], 'missing': len(j['missing'])} for j in state['jobs']], 'issues': state['issues']}, ensure_ascii=False))
        return
    server = ThreadingHTTPServer(('127.0.0.1', args.port), Handler)
    server.companion = companion
    print(f'歌单导入助手：http://127.0.0.1:{args.port}/', flush=True)
    if args.open:
        import webbrowser
        webbrowser.open(f'http://127.0.0.1:{args.port}/')
    server.serve_forever()


if __name__ == '__main__':
    main()
