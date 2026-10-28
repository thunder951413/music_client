"""Real HTTP/SQLite regression against a local fake Navidrome, without music downloads.

Run cargo build, then python3 -m unittest discover -s tests -v from services/tempus-hub.
"""
import base64
import json
import os
from pathlib import Path
import socket
import sqlite3
import subprocess
import tempfile
import threading
import time
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.error import HTTPError
from urllib.parse import parse_qs, urlsplit
from urllib.request import Request, build_opener, ProxyHandler


class Navidrome(BaseHTTPRequestHandler):
    playlists = {}
    songs = []
    def log_message(self, *_): pass
    def do_POST(self):
        p = parse_qs(self.rfile.read(int(self.headers['Content-Length'])).decode(), keep_blank_values=True)
        user = p.get('u', [''])[0]
        root = {'status': 'ok'}
        def one(k): return p[k][0]
        method = self.path.split('/')[-1].split('.')[0]
        if user not in ('alice', 'bob') or one('t') != 'a' * 32:
            root = {'status': 'failed', 'error': {'code': 40}}
        elif method == 'getRandomSongs': root['randomSongs'] = {'song': self.songs}
        elif method == 'getSong': root['song'] = next((s for s in self.songs if s['id'] == one('id')), {})
        elif method == 'getSimilarSongs2': root['similarSongs2'] = {'song': self.songs}
        elif method == 'search3': root['searchResult3'] = {'song': [s for s in self.songs if s['title'] == one('query')]}
        elif method == 'getPlaylists': root['playlists'] = {'playlist': [p for (u, _), p in self.playlists.items() if u == user]}
        elif method == 'getPlaylist': root['playlist'] = self.playlists.get((user, one('id')), {})
        elif method == 'createPlaylist':
            identifier = p.get('playlistId', [f'p{len(self.playlists)}'])[0]
            row = self.playlists.setdefault((user, identifier), {'id': identifier, 'owner': user, 'name': p.get('name', [''])[0], 'comment': '', 'entry': []})
            row['entry'] = [next((s for s in self.songs if s['id'] == id), {'id': id}) for id in p.get('songId', [])]
            root['playlist'] = row
        elif method == 'updatePlaylist':
            row = self.playlists[(user, one('playlistId'))]
            for key in ('name', 'comment', 'public'):
                if key in p: row[key] = one(key)
        elif method == 'deletePlaylist': self.playlists.pop((user, one('id')), None)
        self.send_response(200); self.send_header('Content-Type', 'application/json'); self.end_headers()
        self.wfile.write(json.dumps({'subsonic-response': root}).encode())


class ApiTests(unittest.TestCase):
    def setUp(self):
        Navidrome.playlists = {}
        Navidrome.songs = [{'id': str(i), 'title': f'Song {i}', 'artist': f'Artist {i}', 'duration': 200} for i in range(50)]
        self.fake = ThreadingHTTPServer(('127.0.0.1', 0), Navidrome)
        threading.Thread(target=self.fake.serve_forever, daemon=True).start()
        self.tmp = tempfile.TemporaryDirectory()
        with socket.socket() as s: s.bind(('127.0.0.1', 0)); self.port = s.getsockname()[1]
        self.opener = build_opener(ProxyHandler({}))
        env = dict(os.environ, NO_PROXY='127.0.0.1,localhost', no_proxy='127.0.0.1,localhost', HUB_STATE_DIR=self.tmp.name, NAVIDROME_URL=f'http://127.0.0.1:{self.fake.server_port}', HUB_LISTEN=f'127.0.0.1:{self.port}', HUB_CHARTS_ENABLED='false')
        binary = os.environ.get('HUB_TEST_BINARY', str(Path(__file__).resolve().parents[1] / 'target/debug/tempus-hub'))
        self.process = subprocess.Popen([binary], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        for _ in range(200):
            try: self.call('health', user=None); break
            except OSError: time.sleep(.05)
        else: self.fail('hub did not start')

    def tearDown(self):
        self.process.terminate(); self.process.wait(timeout=5)
        self.fake.shutdown(); self.fake.server_close(); self.tmp.cleanup()

    def call(self, path, body=None, user='alice'):
        headers = {'Content-Type': 'application/json'}
        if user:
            headers['X-Tempus-Auth'] = base64.b64encode(json.dumps({'u': user, 't': 'a'*32, 's': 'salt'}).encode()).decode()
        data = None if body is None else json.dumps(body).encode()
        with self.opener.open(Request(f'http://127.0.0.1:{self.port}/hub/v1/{path}', data=data, headers=headers), timeout=5) as r: return json.load(r)

    def register(self, user='alice', device='phone'):
        self.call('devices', {'id': device, 'name': device}, user)

    def until(self, predicate):
        for _ in range(200):
            value = predicate()
            if value: return value
            time.sleep(.05)
        self.fail('background work did not finish')

    def test_accounts_devices_retries_and_background_credentials(self):
        self.register(); self.register(device='tablet'); self.register('bob')
        event = {'event_id':'event1','song_id':'3','at':int(time.time()*1000),'completed':True,'position_ms':200000,'duration_ms':200000}
        for _ in range(2): self.call('plays', {'device':'phone','events':[event]})
        event2 = dict(event, event_id='event2')
        self.call('plays', {'device':'phone','events':[event2]})
        for name in ('phone', 'tablet'):
            self.call(f'daily?device={name}')
        self.until(lambda: self.call('daily?device=phone').get('playlist_id'))
        self.until(lambda: self.call('daily?device=tablet').get('playlist_id'))
        with sqlite3.connect(Path(self.tmp.name)/'hub.sqlite') as db:
            self.assertEqual(db.execute("SELECT COUNT(*) FROM plays WHERE username='alice' AND device='phone'").fetchone()[0], 2)
            self.assertEqual(db.execute("SELECT COUNT(*) FROM plays WHERE username='bob'").fetchone()[0], 0)
        self.assertNotEqual(self.call('daily?device=phone')['playlist_id'], self.call('daily?device=tablet')['playlist_id'])
        for path in ('wishes', 'daily?device=phone'):
            with self.assertRaises(HTTPError) as e: self.call(path, user=None)
            self.assertEqual(e.exception.code, 401)
        with self.assertRaises(HTTPError) as e: self.call('daily?device=tablet', user='bob')
        self.assertEqual(e.exception.code, 404)
        with self.assertRaises(HTTPError): self.call('plays', {'device':'phone','events':[dict(event, song_id='changed')]})

    def track(self, id, song_id=None):
        return {'provider':'QQ','id':f'Mid{id}','title':f'Song {id}','artists':[f'Artist {id}'],'duration':200,'song_id':song_id}

    def test_import_reconciliation_preserves_order_duplicates_and_account_isolation(self):
        self.register(); self.register('bob')
        Navidrome.playlists[('alice','import')] = {'id':'import','owner':'alice','name':'Import','entry':[Navidrome.songs[0]],'comment':''}
        body = {'playlist_id':'import','request_id':'request1','tracks':[self.track(0,'0'),self.track(1),self.track(1),self.track(2)]}
        self.call('imports', body)
        self.assertEqual(len(self.call('wishes')['wishes']), 2)
        self.assertEqual(self.call('wishes',user='bob')['wishes'], [])
        wid = self.call('wishes')['wishes'][0]['wish_id']
        with self.assertRaises(HTTPError) as e: self.call('wishes/'+wid, {'state':'ignored'},user='bob')
        self.assertEqual(e.exception.code,404)
        self.call('refresh', {})
        self.until(lambda: [s['id'] for s in Navidrome.playlists[('alice','import')]['entry']] == ['0','1','1','2'])
        self.assertTrue(all(w['state']=='completed' for w in self.call('wishes')['wishes']))
        # Retrying the original request must not overwrite the successful fill.
        self.assertTrue(self.call('imports',body)['saved'])

    def test_manual_playlist_edit_is_preserved_and_ambiguous_song_is_not_chosen(self):
        self.register()
        Navidrome.playlists[('alice','import')] = {'id':'import','owner':'alice','name':'Import','entry':[],'comment':''}
        self.call('imports',{'playlist_id':'import','request_id':'r','tracks':[self.track(1)]})
        Navidrome.playlists[('alice','import')]['entry'] = [Navidrome.songs[4]]
        Navidrome.songs.append(dict(Navidrome.songs[1], id='another-version'))
        self.call('refresh',{})
        self.until(lambda: self.call('wishes')['wishes'][0]['state']=='ambiguous')
        self.assertEqual([s['id'] for s in Navidrome.playlists[('alice','import')]['entry']],['4'])

    def test_deleted_import_does_not_block_other_imports(self):
        self.register()
        for id in ('deleted','remaining'):
            Navidrome.playlists[('alice',id)] = {'id':id,'owner':'alice','name':id,'entry':[],'comment':''}
            self.call('imports',{'playlist_id':id,'request_id':id,'tracks':[self.track(1)]})
        del Navidrome.playlists[('alice','deleted')]
        self.call('refresh',{})
        self.until(lambda: [s['id'] for s in Navidrome.playlists[('alice','remaining')]['entry']] == ['1'])
        with sqlite3.connect(Path(self.tmp.name)/'hub.sqlite') as db:
            self.assertEqual(db.execute("SELECT COUNT(*) FROM imports WHERE playlist_id='deleted'").fetchone()[0],0)

if __name__ == '__main__': unittest.main()
