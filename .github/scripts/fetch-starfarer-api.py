#!/usr/bin/env python3
"""Fetch a single member from a remote ZIP using HTTP range requests.

Starsector ships the Linux/macOS builds as plain ZIPs, and Backblaze B2 honours
the Range header, so the central directory tells us where a member lives and
only that slice of the archive has to be transferred. For the 0.98a-RC8 Linux
build this turns a 233 MiB download into about 7 MiB.

Falls back to downloading the whole archive when the server does not advertise
range support (or ignores it), so the script stays correct on other mirrors.
"""

from __future__ import annotations

import argparse
import os
import shutil
import struct
import sys
import tempfile
import urllib.error
import urllib.request
import zipfile
import zlib

EOCD_SIG = b"PK\x05\x06"
EOCD64_LOCATOR_SIG = b"PK\x06\x07"
EOCD64_SIG = b"PK\x06\x06"
CD_SIG = b"PK\x01\x02"
LOCAL_SIG = b"PK\x03\x04"

DEFAULT_URL = (
    "https://f005.backblazeb2.com/file/fractalsoftworks/release/"
    "starsector_linux-{version}.zip"
)
DEFAULT_MEMBER = "starfarer.api.jar"
USER_AGENT = "nanoforged-ci-fetch/1.0"
CHUNK = 1024 * 1024


class RangeUnsupported(RuntimeError):
    """The server answered with the whole archive instead of a partial body."""


class Fetcher:
    def __init__(self, url: str, timeout: float = 120.0) -> None:
        self.url = url
        self.timeout = timeout
        self.size = 0
        self.accept_ranges = ""
        self.requests = 0
        self.transferred = 0
        self._whole: bytes | None = None

    def _request(self, extra_headers: dict[str, str] | None = None):
        headers = {"User-Agent": USER_AGENT}
        if extra_headers:
            headers.update(extra_headers)
        return urllib.request.Request(self.url, headers=headers)

    def probe(self) -> None:
        request = urllib.request.Request(
            self.url, method="HEAD", headers={"User-Agent": USER_AGENT}
        )
        with urllib.request.urlopen(request, timeout=self.timeout) as response:
            self.size = int(response.headers["Content-Length"])
            self.accept_ranges = (response.headers.get("Accept-Ranges") or "").lower()
        self.requests += 1

    def read_range(self, start: int, end: int) -> bytes:
        if self._whole is not None:
            return self._whole[start : end + 1]

        with urllib.request.urlopen(
            self._request({"Range": f"bytes={start}-{end}"}), timeout=self.timeout
        ) as response:
            status = response.status
            body = response.read()
        self.requests += 1
        self.transferred += len(body)

        if status == 206:
            if len(body) != end - start + 1:
                raise RuntimeError(
                    f"short range body: asked {end - start + 1}, got {len(body)}"
                )
            return body
        if status == 200:
            # Server ignored the Range header and handed us the entire archive.
            self._whole = body
            if start == 0 and len(body) == end + 1:
                return body
            raise RangeUnsupported("server ignored the Range header")
        raise RuntimeError(f"HTTP {status} for bytes {start}-{end}")


def _read_eocd(fetcher: Fetcher) -> tuple[int, int]:
    """Return (central_directory_offset, central_directory_size)."""
    tail_len = min(fetcher.size, 128 * 1024)
    tail = fetcher.read_range(fetcher.size - tail_len, fetcher.size - 1)

    eocd = tail.rfind(EOCD_SIG)
    if eocd < 0:
        raise RuntimeError("end of central directory record not found")

    entries = struct.unpack_from("<H", tail, eocd + 10)[0]
    cd_size = struct.unpack_from("<I", tail, eocd + 12)[0]
    cd_offset = struct.unpack_from("<I", tail, eocd + 16)[0]

    if entries == 0xFFFF or cd_size == 0xFFFFFFFF or cd_offset == 0xFFFFFFFF:
        locator = tail.rfind(EOCD64_LOCATOR_SIG)
        if locator < 0:
            raise RuntimeError("ZIP64 locator not found")
        eocd64_offset = struct.unpack_from("<Q", tail, locator + 8)[0]
        record = fetcher.read_range(eocd64_offset, eocd64_offset + 55)
        if not record.startswith(EOCD64_SIG):
            raise RuntimeError("bad ZIP64 end of central directory record")
        cd_size = struct.unpack_from("<Q", record, 40)[0]
        cd_offset = struct.unpack_from("<Q", record, 48)[0]

    return cd_offset, cd_size


def find_member(fetcher: Fetcher, member_name: str) -> dict:
    cd_offset, cd_size = _read_eocd(fetcher)
    cd = fetcher.read_range(cd_offset, cd_offset + cd_size - 1)

    position = 0
    while position + 46 <= len(cd):
        if cd[position : position + 4] != CD_SIG:
            break
        method = struct.unpack_from("<H", cd, position + 10)[0]
        crc = struct.unpack_from("<I", cd, position + 16)[0]
        comp_size, uncomp_size = struct.unpack_from("<II", cd, position + 20)
        name_len, extra_len, comment_len = struct.unpack_from("<HHH", cd, position + 28)
        local_offset = struct.unpack_from("<I", cd, position + 42)[0]
        name = cd[position + 46 : position + 46 + name_len].decode("utf-8", "replace")

        if name.rsplit("/", 1)[-1] == member_name:
            return {
                "name": name,
                "method": method,
                "crc": crc,
                "comp_size": comp_size,
                "uncomp_size": uncomp_size,
                "local_offset": local_offset,
            }
        position += 46 + name_len + extra_len + comment_len

    raise LookupError(f"{member_name} not found in archive")


def extract_member(fetcher: Fetcher, entry: dict) -> bytes:
    header = fetcher.read_range(entry["local_offset"], entry["local_offset"] + 29)
    if not header.startswith(LOCAL_SIG):
        raise RuntimeError("bad local file header")

    name_len, extra_len = struct.unpack_from("<HH", header, 26)
    data_start = entry["local_offset"] + 30 + name_len + extra_len
    raw = fetcher.read_range(data_start, data_start + entry["comp_size"] - 1)

    if entry["method"] == 0:
        data = raw
    elif entry["method"] == 8:
        data = zlib.decompress(raw, -15)
    else:
        raise RuntimeError(f"unsupported compression method {entry['method']}")

    if len(data) != entry["uncomp_size"]:
        raise RuntimeError(
            f"size mismatch: header says {entry['uncomp_size']}, inflated {len(data)}"
        )
    if zlib.crc32(data) & 0xFFFFFFFF != entry["crc"]:
        raise RuntimeError("CRC mismatch")
    return data


def extract_from_whole_archive(url: str, member_name: str, timeout: float) -> bytes:
    print(f"falling back to a full download of {url}", file=sys.stderr)
    handle, path = tempfile.mkstemp(suffix=".zip")
    os.close(handle)
    try:
        request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(request, timeout=timeout) as response, open(
            path, "wb"
        ) as sink:
            shutil.copyfileobj(response, sink, CHUNK)
        with zipfile.ZipFile(path) as archive:
            for info in archive.infolist():
                if info.filename.rsplit("/", 1)[-1] == member_name:
                    return archive.read(info)
        raise LookupError(f"{member_name} not found in archive")
    finally:
        os.unlink(path)


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", help="Starsector version, e.g. 0.98a-RC8")
    parser.add_argument("--url", help=f"archive URL (default: {DEFAULT_URL})")
    parser.add_argument("--member", default=DEFAULT_MEMBER)
    parser.add_argument("--output", required=True)
    parser.add_argument("--timeout", type=float, default=120.0)
    args = parser.parse_args(argv)

    if not args.url and not args.version:
        parser.error("either --version or --url is required")
    url = args.url or DEFAULT_URL.format(version=args.version)

    output = os.path.abspath(args.output)
    os.makedirs(os.path.dirname(output) or ".", exist_ok=True)

    fetcher = Fetcher(url, timeout=args.timeout)
    fetcher.probe()
    print(f"archive : {url}")
    print(f"size    : {fetcher.size} bytes, accept-ranges={fetcher.accept_ranges or 'none'}")

    mode = "range"
    try:
        if "bytes" not in fetcher.accept_ranges:
            raise RangeUnsupported("server does not advertise range support")
        entry = find_member(fetcher, args.member)
        print(
            f"member  : {entry['name']} "
            f"({entry['uncomp_size']} bytes, method={entry['method']})"
        )
        data = extract_member(fetcher, entry)
    except (RangeUnsupported, RuntimeError, LookupError) as exc:
        print(f"range path failed: {exc}", file=sys.stderr)
        mode = "full"
        data = extract_from_whole_archive(url, args.member, args.timeout)

    with open(output, "wb") as sink:
        sink.write(data)

    print(f"wrote   : {output} ({len(data)} bytes)")
    if mode == "range":
        ratio = 100.0 * fetcher.transferred / fetcher.size if fetcher.size else 0.0
        print(
            f"transfer: {fetcher.transferred} bytes in {fetcher.requests} requests "
            f"({ratio:.2f}% of archive)"
        )
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1:]))
    except urllib.error.HTTPError as error:
        print(f"HTTP {error.code} for {error.url}", file=sys.stderr)
        sys.exit(1)
    except urllib.error.URLError as error:
        print(f"network error: {error.reason}", file=sys.stderr)
        sys.exit(1)
