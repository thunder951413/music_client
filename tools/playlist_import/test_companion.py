import base64
import copy
import csv
import hashlib
from http.server import ThreadingHTTPServer
import json
from pathlib import Path
import platform
import shlex
import sqlite3
import struct
import subprocess
import sys
import shutil
import tempfile
import threading
import unittest
from unittest.mock import patch
from urllib.error import HTTPError
from urllib.request import Request, urlopen

import companion
import ncm


def request_row():
    return {'id': 'test-playlist', 'name': '=Playlist', 'comment': companion.PREFIX + json.dumps({
        'version': 1, 'provider': 'QQ', 'sourceId': '123', 'chart': False,
        'url': 'https://y.qq.com/n/ryqq_v2/playlist/123', 'requestId': 'test', 'complete': True,
        'total': 1, 'missing': [{'id': 'Ab123', 'title': '=Song', 'artists': ['Artist'], 'duration': 180, 'url': 'https://evil.example/'}]})}


class RequestTests(unittest.TestCase):
    def test_legacy_user_filter_is_scoped_and_sql_parameterized(self):
        original_run = subprocess.run
        with tempfile.TemporaryDirectory() as root:
            database = Path(root) / 'navidrome.db'
            row = request_row()
            with sqlite3.connect(database) as c:
                c.execute('CREATE TABLE user(id TEXT,user_name TEXT)')
                c.executemany('INSERT INTO user VALUES(?,?)',[('a',"alice'quoted"),('b','bob')])
                c.execute('CREATE TABLE playlist(id TEXT,name TEXT,comment TEXT,owner_id TEXT)')
                c.executemany('INSERT INTO playlist VALUES(?,?,?,?)',[
                    ('alice-list','Alice',row['comment'],'a'),('bob-list','Bob',row['comment'],'b')])
            def local_read(arguments, **kwargs):
                return original_run([sys.executable,'-c',shlex.split(arguments[-1])[2]],**kwargs)
            with patch('companion.subprocess.run',side_effect=local_read):
                jobs,issues=companion.read_requests('test',str(database),"alice'quoted")
            self.assertEqual([j['id'] for j in jobs],['alice-list'])
            self.assertFalse(issues)

    def test_ssh_query_reads_only_explicit_requests_and_never_modifies_the_database(self):
        original_run = subprocess.run
        with tempfile.TemporaryDirectory() as root:
            database = Path(root) / 'test navidrome.db'
            row = request_row()
            with sqlite3.connect(database) as connection:
                connection.execute('CREATE TABLE playlist(id TEXT,name TEXT,comment TEXT)')
                connection.executemany('INSERT INTO playlist VALUES(?,?,?)', [
                    (row['id'], row['name'], row['comment']), ('normal', 'Personal', 'Normal playlist')])
            digest = hashlib.sha256(database.read_bytes()).digest()
            def local_read(arguments, **kwargs):
                command = shlex.split(arguments[-1])
                self.assertEqual(command[:2], ['python3', '-c'])
                return original_run([sys.executable, '-c', command[2]], **kwargs)
            with patch('companion.subprocess.run', side_effect=local_read):
                jobs, issues = companion.read_requests('test-host', str(database))
            self.assertEqual([j['id'] for j in jobs], [row['id']])
            self.assertFalse(issues)
            self.assertEqual(hashlib.sha256(database.read_bytes()).digest(), digest)

    def test_daily_wishes_merge_per_platform_and_skip_bad_records(self):
        def daily(date, missing):
            return {'id': date, 'name': 'Daily', 'comment': companion.DAILY_PREFIX + json.dumps(
                {'version': 1, 'date': date, 'requestId': 'x', 'complete': True, 'missing': missing})}
        song = {'provider': 'QQ', 'id': 'Ab1', 'title': 'Song', 'artists': ['A'], 'duration': 200}
        rows = [daily('2026-10-27', [song, {'provider': 'NETEASE', 'id': '42', 'title': 'Other', 'artists': ['B']}]),
                daily('2026-10-28', [song]),
                daily('2026-10-26', [{'provider': 'QQ', 'id': '../x', 'title': 'Bad', 'artists': ['C']}])]
        payload = json.dumps(rows)
        with patch('companion.subprocess.run', return_value=subprocess.CompletedProcess([], 0, payload, '')):
            jobs, issues = companion.read_requests()
        self.assertEqual({j['provider']: [t['id'] for t in j['missing']] for j in jobs}, {'QQ': ['Ab1'], 'NETEASE': ['42']})
        self.assertEqual(jobs[0]['url'], '')
        self.assertEqual(jobs[1]['missing'][0]['url'], 'https://music.163.com/song?id=42')
        self.assertEqual(len(issues), 1)

    def test_untrusted_song_links_are_replaced_with_canonical_provider_links(self):
        job = companion.validate_request(request_row())
        self.assertEqual(job['missing'][0]['url'], 'https://y.qq.com/n/ryqq_v2/songDetail/Ab123')

    def test_incomplete_writes_foreign_links_and_bad_ids_cannot_be_processed(self):
        for change in ({'complete': False}, {'url': 'https://evil.example/'}, {'sourceId': '../../etc'}, {'version': 2}, {'total': 0}):
            row = request_row()
            binding = json.loads(row['comment'][len(companion.PREFIX):])
            binding.update(change)
            row['comment'] = companion.PREFIX + json.dumps(binding)
            with self.assertRaises(ValueError):
                companion.validate_request(row)

    def test_csv_exports_do_not_execute_provider_supplied_formulas(self):
        with tempfile.TemporaryDirectory() as root:
            companion.export_requests([companion.validate_request(request_row())], Path(root))
            file = next(Path(root).glob('*.csv'))
            with file.open(encoding='utf-8-sig') as stream:
                values = list(csv.reader(stream))
            self.assertEqual(values[1][0], "'=Playlist")
            self.assertEqual(values[1][2], "'=Song")
            self.assertEqual(file.stat().st_mode & 0o777, 0o600)

    def test_previews_wrong_artists_and_different_versions_are_not_imported(self):
        track = {'title': 'Song', 'artists': ['Artist'], 'duration': 240}
        self.assertTrue(companion.matches(track, {'title': 'Ｓｏｎｇ', 'artist': 'Artist'}, 240))
        self.assertFalse(companion.matches(track, {'title': 'Song', 'artist': 'Artist'}, 30))
        self.assertFalse(companion.matches(track, {'title': 'Song (Live)', 'artist': 'Artist'}, 240))
        self.assertFalse(companion.matches(track, {'title': 'Song', 'artist': 'Other'}, 240))


class CheckedWorkerTests(unittest.TestCase):
    def test_preview_duration_is_rejected_before_the_existing_tool_can_publish_it(self):
        from checked_worker import check_duration
        with self.assertRaises(ValueError):
            check_duration(30, 240)
        with self.assertRaises(ValueError):
            check_duration(30, 40)
        check_duration(239.5, 240)

    def test_only_manifest_files_are_uploaded_and_changed_files_abort_the_run(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            library = root / 'music'
            library.mkdir()
            wanted, unrelated = library / 'wanted.flac', library / 'unrelated.flac'
            wanted.write_bytes(b'test audio')
            unrelated.write_bytes(b'other audio')
            (root / 'qq_decrypt.py').write_text(f"""from pathlib import Path
import hashlib
DEFAULT_ROOT=Path({str(library)!r})
def fingerprint(path): return hashlib.sha256(path.read_bytes()).hexdigest()
""")
            output = root / 'uploaded.json'
            (root / 'sync_music.py').write_text(f"""from pathlib import Path
import json
from qq_decrypt import DEFAULT_ROOT
CONTROL=Path({str(root)!r})
def candidates(): return [(p,None) for p in DEFAULT_ROOT.iterdir()]
def inventory(): return {{p.name:p.stat().st_size for p in DEFAULT_ROOT.iterdir()}}
def remote_variants(): return {{}}
def remote_verify(expected): return {{name:True for name in expected}}
def main():
 rows=candidates()
 Path({str(output)!r}).write_text(json.dumps([str(p) for p,h in rows]))
 (CONTROL/'music-sync-state.json').write_text(json.dumps({{'files':{{str(p):{{'destination':p.name,'sha256':h}} for p,h in rows}}}}))
""")
            manifest = root / 'ready.json'
            manifest.write_text(json.dumps([{'path': str(wanted), 'sha256': hashlib.sha256(wanted.read_bytes()).hexdigest()}]))
            args = [sys.executable, str(Path(companion.__file__).with_name('checked_worker.py')), 'upload', '--unlock-dir', str(root), '--manifest', str(manifest)]
            result = subprocess.run(args, capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(json.loads(output.read_text()), [str(wanted.resolve())])
            wanted.write_bytes(b'changed since validation')
            result = subprocess.run(args, capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('文件发生变化', result.stderr)


@unittest.skipUnless(shutil.which('ffmpeg') and shutil.which('ffprobe'), 'FFmpeg tools are required for real audio validation')
class AudioValidationTests(unittest.TestCase):
    def test_ncm_metadata_is_added_without_reencoding_or_changing_the_source(self):
        class Music:
            @staticmethod
            def inspect_file(path): return {'format': 'flac'}
            @staticmethod
            def fingerprint(path): return hashlib.sha256(path.read_bytes()).hexdigest()
            @staticmethod
            def valid_audio(path, _):
                return subprocess.run(['ffmpeg', '-v', 'error', '-xerror', '-i', str(path), '-f', 'null', '-'], capture_output=True).returncode == 0
        def stream_hash(path):
            return subprocess.check_output(['ffmpeg', '-v', 'error', '-i', str(path), '-map', '0:a:0', '-c:a', 'copy', '-f', 'hash', '-hash', 'sha256', '-'])
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / 'download.flac'
            destination = root / 'library'
            destination.mkdir()
            subprocess.run(['ffmpeg', '-v', 'error', '-f', 'lavfi', '-i', 'sine=frequency=1000:duration=1', '-c:a', 'flac', str(source)], check=True)
            before = hashlib.sha256(source.read_bytes()).digest()
            track = {'title': 'Synthetic song', 'artists': ['Artist'], 'duration': 1}
            metadata = {'musicName': 'Synthetic song', 'artist': 'Artist', 'album': 'Test album'}
            published = companion.publish_plain(source, track, destination, Music, metadata)
            self.assertEqual(stream_hash(source), stream_hash(published))
            self.assertEqual(companion.audio_info(published)[0]['title'], track['title'])
            self.assertEqual(hashlib.sha256(source.read_bytes()).digest(), before)
            with self.assertRaises(ValueError):
                companion.publish_plain(source, dict(track, duration=180), destination, Music, metadata)


class LocalHttpTests(unittest.TestCase):
    def setUp(self):
        self.server = ThreadingHTTPServer(('127.0.0.1', 0), companion.Handler)
        self.server.companion = companion.Companion()
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.url = f'http://127.0.0.1:{self.server.server_port}'

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()

    def test_status_is_available_but_foreign_origin_writes_are_rejected(self):
        with urlopen(self.url + '/api/status') as response:
            self.assertEqual(json.load(response)['phase'], 'idle')
        request = Request(self.url + '/api/start', method='POST', headers={'Origin': 'https://evil.example', 'X-Tempus-Import': '1'})
        with self.assertRaises(HTTPError) as error:
            urlopen(request)
        self.assertEqual(error.exception.code, 403)
        self.assertFalse(self.server.companion.snapshot()['running'])

    def test_dns_rebinding_hosts_cannot_read_task_data(self):
        request = Request(self.url + '/api/status', headers={'Host': 'evil.example'})
        with self.assertRaises(HTTPError) as error:
            urlopen(request)
        self.assertEqual(error.exception.code, 403)


@unittest.skipUnless(platform.system() == 'Darwin', 'CommonCrypto is provided by macOS')
class NcmTests(unittest.TestCase):
    def test_cipher_roundtrip_across_stream_blocks_preserves_source(self):
        # Synthetic, non-copyrighted vector. Key generation is independent of the decoder's mask.
        key = b'test-only-netease-file-key'
        box = list(range(256))
        j = 0
        for i in range(256):
            j = (j + box[i] + key[i % len(key)]) % 256
            box[i], box[j] = box[j], box[i]
        plain = b'fLaC' + bytes(range(256)) * 513
        encrypted = bytearray()
        for position, value in enumerate(plain):
            index = (position + 1) % 256
            first = box[index]
            second = box[(index + first) % 256]
            encrypted.append(value ^ box[(first + second) % 256])
        key_block = bytes(b ^ 0x64 for b in ncm.aes(b'neteasecloudmusic' + key, ncm.CORE_KEY, decrypt=False))
        metadata = {'musicId': 123, 'musicName': 'Synthetic Song', 'artist': [['Artist', 1]], 'format': 'flac'}
        meta_block = b"163 key(Don't modify):" + base64.b64encode(ncm.aes(b'music:' + json.dumps(metadata).encode(), ncm.META_KEY, decrypt=False))
        meta_block = bytes(b ^ 0x63 for b in meta_block)
        content = b'CTENFDAM\0\0' + struct.pack('<I', len(key_block)) + key_block
        content += struct.pack('<I', len(meta_block)) + meta_block + b'\0' * 13 + encrypted
        with tempfile.TemporaryDirectory() as root:
            source, output = Path(root) / 'download.ncm', Path(root) / 'decoded.flac'
            source.write_bytes(content)
            before = hashlib.sha256(source.read_bytes()).digest()
            info = ncm.inspect(source)
            self.assertEqual(info.metadata['musicId'], 123)
            ncm.decode(source, output, info)
            self.assertEqual(output.read_bytes(), plain)
            self.assertEqual(hashlib.sha256(source.read_bytes()).digest(), before)

    def test_truncated_headers_and_invalid_padding_are_errors(self):
        with tempfile.TemporaryDirectory() as root:
            source = Path(root) / 'incomplete.ncm'
            source.write_bytes(b'CTENFDAM\0\0\xff\xff\xff\xff')
            with self.assertRaises(ValueError):
                ncm.inspect(source)
        with self.assertRaises(ValueError):
            ncm.aes(b'\x00' * 16, ncm.CORE_KEY)


if __name__ == '__main__':
    unittest.main()
