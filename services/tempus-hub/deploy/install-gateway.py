#!/usr/bin/env python3
"""Run on mini_server after hub tests pass. Back up config and restore it on a failed cutover."""
from pathlib import Path
import re
import shutil
import sqlite3
import subprocess
import time
from urllib.request import build_opener, ProxyHandler

root = Path.home() / 'services/tempus-hub'
local_http = build_opener(ProxyHandler({}))
source = Path(__file__).resolve().parent
config = Path.home() / 'services/qq-music/navidrome.toml'
backup = root / 'navidrome.before-gateway.toml'
if not backup.exists():
    shutil.copy2(config, backup)
    backup.chmod(0o600)
database_backup = root / 'backups/navidrome-before-hub.sqlite'
if not database_backup.exists():
    database_backup.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    with sqlite3.connect('file:' + str(config.parent/'data/navidrome.db') + '?mode=ro', uri=True) as src:
        with sqlite3.connect(database_backup) as dst: src.backup(dst)
    database_backup.chmod(0o600)
original = config.read_text()
updated = original
for key, value in [('Port', '4536'), ('Address', '"127.0.0.1"')]:
    pattern = r'(?m)^\s*' + key + r'\s*=.*$'
    if re.search(pattern, updated): updated = re.sub(pattern, key + ' = ' + value, updated)
    else: updated += '\n' + key + ' = ' + value + '\n'
gateway = (source / 'tempus-gateway.conf').read_text().replace('@ROOT@', str(root))
(root / 'gateway.conf').write_text(gateway)
(root / 'state/client-body').mkdir(parents=True, exist_ok=True)
(root / 'state/proxy').mkdir(parents=True, exist_ok=True)
# Test the config without trying to bind Navidrome's current live port.
subprocess.run(['/usr/sbin/nginx','-p',str(root)+'/', '-c', str(root/'gateway.conf'), '-t'], check=True)
unit = Path.home()/'.config/systemd/user/tempus-gateway.service'
shutil.copy2(source/'tempus-gateway.service', unit)
env = root / 'hub.env'
env.write_text('NAVIDROME_URL=http://127.0.0.1:4536\n')
env.chmod(0o600)
try:
    config.write_text(updated)
    subprocess.run(['systemctl','--user','daemon-reload'],check=True)
    subprocess.run(['systemctl','--user','restart','qq-music.service'],check=True)
    subprocess.run(['systemctl','--user','restart','tempus-hub.service'],check=True)
    subprocess.run(['systemctl','--user','enable','--now','tempus-gateway.service'],check=True)
    for _ in range(30):
        try:
            with local_http.open('http://127.0.0.1:4533/hub/v1/health',timeout=2) as r: assert r.status==200
            with local_http.open('http://127.0.0.1:4533/rest/ping.view?f=json&v=1.16.1&c=tempus-hub-probe',timeout=2) as r: assert r.status==200
            break
        except Exception: time.sleep(1)
    else: raise RuntimeError('gateway readiness check failed')
except Exception:
    subprocess.run(['systemctl','--user','disable','--now','tempus-gateway.service'])
    config.write_text(original)
    subprocess.run(['systemctl','--user','restart','qq-music.service'])
    env.write_text('NAVIDROME_URL=http://127.0.0.1:4533\n')
    subprocess.run(['systemctl','--user','restart','tempus-hub.service'])
    raise
print('Gateway verified: existing music port 4533 -> Navidrome 4536 and hub 4534')
