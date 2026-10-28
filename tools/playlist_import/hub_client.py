"""Account-scoped hub task source. Credentials stay in a private local config file."""
import base64
import json
from pathlib import Path
import stat
from urllib.parse import urlsplit
from urllib.request import Request, build_opener, HTTPRedirectHandler


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, *_args, **_kwargs):
        return None


class HubClient:
    def __init__(self, config):
        path = Path(config).expanduser()
        if stat.S_IMODE(path.stat().st_mode) & 0o077:
            raise ValueError('hub 配置包含访问凭证，请先将文件权限设置为 600')
        data = json.loads(path.read_text())
        self.url = data['url'].rstrip('/')
        parsed = urlsplit(self.url)
        if (parsed.username or parsed.password or parsed.query or parsed.fragment
                or not parsed.hostname or parsed.scheme not in ('http', 'https')
                or (parsed.scheme == 'http' and parsed.hostname not in ('localhost', '127.0.0.1', '::1'))):
            raise ValueError('hub 地址必须使用 HTTPS；本机 SSH 隧道可使用 HTTP')
        auth = {key: data[key] for key in ('u', 't', 's')}
        if not all(isinstance(v, str) and v for v in auth.values()):
            raise ValueError('hub 账号配置无效')
        self.auth = base64.b64encode(json.dumps(auth, ensure_ascii=False).encode()).decode()
        self.host = data.get('ssh_host', 'mini_server')
        self.opener = build_opener(NoRedirect())

    def request(self, path, body=None):
        data = None if body is None else json.dumps(body).encode()
        request = Request(self.url + '/hub/v1/' + path, data=data,
                          headers={'X-Tempus-Auth': self.auth, 'Content-Type': 'application/json'})
        try:
            with self.opener.open(request, timeout=40) as response:
                content = response.read(8 * 1024 * 1024 + 1)
                if len(content) > 8 * 1024 * 1024:
                    raise ValueError('hub 响应过大')
                return json.loads(content)
        except ValueError:
            raise
        except Exception:
            # urllib errors can contain URLs; don't expose credentials or arbitrary upstream text.
            raise RuntimeError('无法连接 hub，请检查地址、账号及服务器状态；不会改用其他账号读取任务') from None

    def jobs(self, validate_track):
        rows = self.request('wishes')['wishes']
        if not isinstance(rows, list):
            raise ValueError('hub 任务格式无效')
        tracks = {'QQ': {}, 'NETEASE': {}}
        issues = []
        for row in rows:
            if row.get('state') == 'ambiguous':
                issues.append('已入库歌曲存在多个版本，请在手机重新匹配并确认：' + str(row.get('title', ''))[:100])
                continue
            if row.get('state') != 'pending':
                continue
            provider = row.get('provider')
            if provider not in tracks:
                raise ValueError('hub 下载平台无效')
            track = {k: row[k] for k in ('id', 'title', 'artists', 'duration') if k in row}
            validate_track(provider, track)
            tracks[provider][track['id']] = track
        return [{'id': 'hub-' + p, 'name': '待下载音乐', 'provider': p, 'url': '',
                 'missing': list(items.values()), 'request_id': 'hub'}
                for p, items in tracks.items() if items], issues

    def refresh(self):
        return self.request('refresh', {})
