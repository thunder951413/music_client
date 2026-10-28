"""Isolated adapters around the existing tools, preserving their locks/cooldowns/verification."""
import argparse
import json
import math
from pathlib import Path
import sys


def check_duration(actual, expected):
    if not math.isfinite(actual) or actual <= 0 or not expected or expected <= 0:
        raise ValueError('缺少可靠的完整音频时长，保留待处理')
    if abs(actual - expected) > min(12, max(2, expected * 0.02)):
        raise ValueError('音频时长与歌单不符，不会将试听或不完整片段入库')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('mode', choices=('qq', 'upload'))
    parser.add_argument('--unlock-dir', required=True, type=Path)
    parser.add_argument('--source', type=Path)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--duration', type=float, default=0)
    parser.add_argument('--manifest', type=Path)
    parser.add_argument('--expected-host')
    args = parser.parse_args()
    sys.path.insert(0, str(args.unlock_dir.resolve()))
    import qq_decrypt as music
    if args.mode == 'qq':
        original_probe = music.probe
        def checked_probe(path):
            duration, metadata = original_probe(path)
            check_duration(duration, args.duration)
            return duration, metadata
        music.probe = checked_probe
        try:
            raise SystemExit(music.main([str(args.source), '-o', str(args.output)]))
        except music.StopBatch as error:
            print(str(error), file=sys.stderr)
            raise SystemExit(1)
    import sync_music as sync
    if args.expected_host and sync.HOST != args.expected_host:
        raise ValueError('任务读取与上传服务器不一致')
    allowed = {path.resolve() for path in (music.DEFAULT_ROOT, music.DEFAULT_ROOT / 'decrypted')}
    entries = json.loads(args.manifest.read_text())
    verified = []
    for entry in entries:
        source = Path(entry['path'])
        if (source.is_symlink() or not source.is_file() or source.suffix.lower() not in ('.flac', '.mp3', '.ogg', '.m4a')
                or not any(source.resolve().is_relative_to(root) for root in allowed)):
            raise ValueError('入库清单包含无效文件')
        if music.fingerprint(source) != entry['sha256']:
            raise ValueError('入库前文件发生变化，请重新校验')
        verified.append((source.resolve(), entry['sha256']))
    # Only files validated for these explicit import requests can be uploaded by this run.
    # The existing sync tool still performs staging, SHA-256 verification and deduplication.
    sync.candidates = lambda: verified
    sys.argv = [str(args.unlock_dir / 'sync_music.py'), '--metadata']
    sync.main()
    # A busy existing sync process or a skipped young file must not be reported as uploaded.
    saved = json.loads((sync.CONTROL / 'music-sync-state.json').read_text())
    inventory = sync.inventory()
    variants = sync.remote_variants()
    raw = {}
    for source, digest in verified:
        record = saved.get('files', {}).get(str(source), {})
        destination = record.get('destination')
        if record.get('sha256') != digest or destination not in inventory:
            raise ValueError('同步尚未写入全部校验文件，请等待现有同步任务结束后重试')
        variant = variants.get(destination, {})
        if variant.get('source_sha256') == digest and variant.get('size') == inventory[destination]:
            continue
        raw[destination] = digest
    if raw:
        checks = sync.remote_verify(raw)
        if not all(checks.get(name) is True for name in raw):
            raise ValueError('远端音频校验未通过，请重试上传')


if __name__ == '__main__':
    main()
