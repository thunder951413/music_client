"""Admin-operated smoke test of real Navidrome/hub reconciliation, without downloading audio.

Uses two uniquely matched recordings already in the library. Creates a private
temporary playlist, verifies ordered fill/duplicates, then removes only its own test artifacts.
The chart JSON is an export of chart_cache arrays; credentials come from a private Mac config.
"""
import argparse
import base64
import json
from pathlib import Path
import shlex
import subprocess
import sys
import time
from urllib.parse import urlencode
from urllib.request import Request
import uuid

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'tools/playlist_import'))
from hub_client import HubClient
from companion import matches


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--hub-config', type=Path, required=True)
    parser.add_argument('--charts', type=Path, help='Exported chart metadata for a real-source fixture')
    parser.add_argument('--library-fixture', action='store_true', help='Use explicitly synthetic provider IDs for two real library recordings; does not test external sourcing or downloads')
    parser.add_argument('--ssh-host', default='mini_server', help='hub administrator SSH host for scoped cleanup')
    args = parser.parse_args()
    client = HubClient(args.hub_config)
    config = json.loads(args.hub_config.read_text())

    def subsonic(method, **fields):
        data = dict(u=config['u'],t=config['t'],s=config['s'],v='1.16.1',c='tempus-hub-smoke',f='json',**fields)
        request = Request(client.url+'/rest/'+method+'.view',data=urlencode(data,doseq=True).encode())
        with client.opener.open(request,timeout=30) as response:
            root = json.load(response)['subsonic-response']
        if root['status'] != 'ok': raise RuntimeError('Navidrome rejected smoke request')
        return root

    if args.library_fixture:
        rows = subsonic('getRandomSongs',size=100).get('randomSongs',{}).get('song',[])
        candidates = [{'provider':'QQ','id':'TempusQa'+uuid.uuid4().hex,'title':s['title'],
                       'artists':[s['artist']],'duration':s.get('duration')}
                      for s in rows if s.get('title') and s.get('artist') and s.get('duration')]
        print('Controlled fixture: real library audio, synthetic provider IDs; no external source or download verification')
    elif args.charts:
        candidates = json.loads(args.charts.read_text())
    else:
        parser.error('choose --charts or --library-fixture')
    selected = []
    for track in candidates[:800]:
        songs = []
        for offset in range(0,10000,100):
            rows = subsonic('search3',query=track['title'],songCount=100,songOffset=offset,artistCount=0,albumCount=0).get('searchResult3',{}).get('song',[])
            songs.extend(rows)
            if len(rows)<100: break
        else: continue
        found = {s['id']:s for s in songs if matches(track,s,s.get('duration',0))}
        if len(found)==1:
            song = next(iter(found.values()))
            if all(song['id']!=old[1]['id'] for old in selected): selected.append((track,song))
        if len(selected)==2: break
    if len(selected)!=2: raise RuntimeError('Need two uniquely matched existing chart recordings; no playlist created')
    before = {w['wish_id'] for w in client.request('wishes')['wishes']}
    playlist_id = None
    clean_wishes = []
    try:
        (a,sa),(b,sb) = selected
        playlist = subsonic('createPlaylist',name='Tempus QA · '+uuid.uuid4().hex[:12],songId=[sa['id'],sa['id']])['playlist']
        playlist_id = playlist['id']
        subsonic('updatePlaylist',playlistId=playlist_id,public='false')
        items = [dict(a,song_id=sa['id']),dict(b,song_id=None),dict(b,song_id=None),dict(a,song_id=sa['id'])]
        client.request('imports',{'playlist_id':playlist_id,'request_id':uuid.uuid4().hex,'tracks':items})
        # Only this explicitly submitted track is eligible for cleanup; retain unrelated work.
        clean_wishes = [w['wish_id'] for w in client.request('wishes')['wishes']
                        if w['wish_id'] not in before and w['provider']==b['provider'] and w['id']==b['id']]
        client.refresh()
        desired = [sa['id'],sb['id'],sb['id'],sa['id']]
        for _ in range(90):
            current = subsonic('getPlaylist',id=playlist_id)['playlist']
            if [s['id'] for s in current.get('entry',[])] == desired:
                print('Real Navidrome ordered fill verified: four positions, two recordings, duplicate order preserved')
                break
            time.sleep(1)
        else: raise RuntimeError('Live fill did not finish; no audio download was attempted')
    finally:
        if playlist_id:
            subsonic('deletePlaylist',id=playlist_id)
            cleanup = '''import sys,json,sqlite3,base64
from pathlib import Path
p=json.load(sys.stdin)
c=sqlite3.connect(str(Path.home()/"services/tempus-hub/state/hub.sqlite"))
c.execute("DELETE FROM imports WHERE username=? AND playlist_id=?",(p['user'],p['playlist']))
for key in p['wishes']:
 key=base64.urlsafe_b64decode(key+'='*((-len(key))%4)).decode()
 c.execute("DELETE FROM wishes WHERE username=? AND key=?",(p['user'],key))
c.commit()
'''
            subprocess.run(['ssh',args.ssh_host,'python3 -c '+shlex.quote(cleanup)],
                           input=json.dumps({'user':config['u'],'playlist':playlist_id,'wishes':clean_wishes}),text=True,check=True)
            print('Temporary live-test playlist and its hub records removed')

if __name__ == '__main__': main()
