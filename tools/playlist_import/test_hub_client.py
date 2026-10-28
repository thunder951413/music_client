import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from hub_client import HubClient
from companion import check_track, Companion


class HubClientTests(unittest.TestCase):
    def config(self, root, url='https://music.example'):
        path = Path(root) / 'hub.json'
        path.write_text(json.dumps({'url':url,'u':'alice','t':'a'*32,'s':'salt'}))
        path.chmod(0o600)
        return path

    def test_credentials_require_private_file_and_https(self):
        with tempfile.TemporaryDirectory() as root:
            path = self.config(root)
            HubClient(path)
            path.chmod(0o644)
            with self.assertRaises(ValueError): HubClient(path)
            with self.assertRaises(ValueError): HubClient(self.config(root,'http://remote.example'))
            with self.assertRaises(ValueError): HubClient(self.config(root,'https://user:pass@remote.example'))
            HubClient(self.config(root,'http://127.0.0.1:4534'))

    def test_only_pending_tasks_are_downloaded_and_links_are_canonical(self):
        with tempfile.TemporaryDirectory() as root:
            client = HubClient(self.config(root))
            song = {'provider':'QQ','id':'Ab1','title':'Song','artists':['A'],'duration':200,'state':'pending','url':'https://evil.example'}
            rows = [song, dict(song,id='Ab2',state='completed'),dict(song,id='Ab3',state='ignored'),dict(song,id='Ab4',state='ambiguous')]
            with patch.object(client,'request',return_value={'wishes':rows}): jobs,issues = client.jobs(check_track)
            self.assertEqual([s['id'] for s in jobs[0]['missing']], ['Ab1'])
            self.assertEqual(jobs[0]['missing'][0]['url'],'https://y.qq.com/n/ryqq_v2/songDetail/Ab1')
            self.assertEqual(len(issues),1)

    def test_configured_hub_failure_never_falls_back_to_ssh(self):
        with tempfile.TemporaryDirectory() as root:
            helper = Companion(hub_config=self.config(root))
            with patch.object(helper.hub,'jobs',side_effect=RuntimeError('offline')), patch('companion.read_requests') as legacy:
                with self.assertRaises(RuntimeError): helper.refresh()
                legacy.assert_not_called()

if __name__ == '__main__': unittest.main()
