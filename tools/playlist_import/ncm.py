"""Read locally downloaded NCM files; source files are never modified.

Format reference: Unlock Music src/decrypt/ncm.ts (MIT); see LICENSE.unlock-music.
AES uses macOS CommonCrypto, so the companion needs no extra Python crypto package.
"""
import base64
import ctypes
import json
import struct
from dataclasses import dataclass, field

CORE_KEY = bytes.fromhex('687a4852416d736f356b496e62617857')
META_KEY = bytes.fromhex('2331346c6a6b5f215c5d2630553c2728')


def aes(data, key, decrypt=True):
    if len(key) != 16 or (decrypt and (not data or len(data) % 16)):
        raise ValueError('NCM AES 数据无效')
    if not decrypt:
        padding = 16 - len(data) % 16
        data += bytes([padding]) * padding
    lib = ctypes.CDLL('/usr/lib/system/libcommonCrypto.dylib')
    lib.CCCrypt.argtypes = [ctypes.c_uint32, ctypes.c_uint32, ctypes.c_uint32,
                           ctypes.c_void_p, ctypes.c_size_t, ctypes.c_void_p,
                           ctypes.c_void_p, ctypes.c_size_t, ctypes.c_void_p,
                           ctypes.c_size_t, ctypes.POINTER(ctypes.c_size_t)]
    lib.CCCrypt.restype = ctypes.c_int
    output = ctypes.create_string_buffer(len(data) + 16)
    moved = ctypes.c_size_t()
    code = lib.CCCrypt(1 if decrypt else 0, 0, 2, key, len(key), None,
                       data, len(data), output, len(output), ctypes.byref(moved))
    if code:
        raise ValueError('NCM 密钥或数据校验失败')
    result = output.raw[:moved.value]
    if decrypt:
        padding = result[-1] if result else 0
        if not 1 <= padding <= 16 or result[-padding:] != bytes([padding]) * padding:
            raise ValueError('NCM AES 填充校验失败')
        result = result[:-padding]
    return result


def read_exact(file, size):
    data = file.read(size)
    if len(data) != size:
        raise ValueError('NCM 文件尚未下载完成或已损坏')
    return data


def read_block(file, maximum):
    size = struct.unpack('<I', read_exact(file, 4))[0]
    if size > maximum:
        raise ValueError('NCM 数据长度超出限制')
    return read_exact(file, size)


@dataclass
class Info:
    metadata: dict
    offset: int
    keybox: bytes = field(repr=False)
    stamp: tuple = field(repr=False)


def inspect(path):
    stamp = (path.stat().st_size, path.stat().st_mtime_ns)
    with path.open('rb') as file:
        if read_exact(file, 8) != b'CTENFDAM':
            raise ValueError('不是完整的 NCM 文件')
        read_exact(file, 2)
        key = aes(bytes(x ^ 0x64 for x in read_block(file, 4096)), CORE_KEY)
        if not key.startswith(b'neteasecloudmusic') or len(key) <= 17:
            raise ValueError('NCM 文件密钥格式不正确')
        key = key[17:]
        box = list(range(256))
        j = 0
        for i in range(256):
            j = (box[i] + j + key[i % len(key)]) & 255
            box[i], box[j] = box[j], box[i]
        mask = bytes(box[(box[(i + 1) & 255] + box[((i + 1) + box[(i + 1) & 255]) & 255]) & 255] for i in range(256))
        raw = bytes(x ^ 0x63 for x in read_block(file, 1024 * 1024))
        metadata = {}
        if raw:
            if not raw.startswith(b"163 key(Don't modify):"):
                raise ValueError('NCM 元信息格式不正确')
            plain = aes(base64.b64decode(raw[22:], validate=True), META_KEY)
            kind, separator, value = plain.partition(b':')
            if not separator or kind not in (b'music', b'dj'):
                raise ValueError('NCM 元信息格式不正确')
            metadata = json.loads(value)
            if kind == b'dj':
                metadata = metadata['mainMusic']
            if not isinstance(metadata, dict):
                raise ValueError('NCM 元信息无效')
        # CRC32 (4), unused byte (1), allocated image length (4), image length (4).
        read_exact(file, 5)
        image_space, image_length = struct.unpack('<II', read_exact(file, 8))
        if image_length > image_space or image_space > 20 * 1024 * 1024:
            raise ValueError('NCM 封面数据无效')
        offset = file.tell() + image_space
        if offset >= path.stat().st_size:
            raise ValueError('NCM 音频尚未下载完成')
        if stamp != (path.stat().st_size, path.stat().st_mtime_ns):
            raise ValueError('源文件仍在下载，请完成后再导入')
        return Info(metadata, offset, mask, stamp)


def decode(path, output, info=None):
    info = info or inspect(path)
    before = (path.stat().st_size, path.stat().st_mtime_ns)
    if before != info.stamp:
        raise ValueError('源文件发生变化，请重新读取完整下载文件')
    with path.open('rb') as source, output.open('xb') as target:
        source.seek(info.offset)
        position = 0
        while True:
            data = source.read(64 * 1024)
            if not data:
                break
            target.write(bytes(value ^ info.keybox[(position + i) & 255] for i, value in enumerate(data)))
            position += len(data)
    if before != (path.stat().st_size, path.stat().st_mtime_ns):
        output.unlink(missing_ok=True)
        raise ValueError('源文件仍在下载，请完成后再导入')
    return info.metadata
