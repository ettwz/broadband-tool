#!/usr/bin/env python3
"""Install pinned Linux build toolchains into this checkout (no system writes)."""
from concurrent.futures import ThreadPoolExecutor
import hashlib
import fcntl
import json
from pathlib import Path
import platform
import subprocess
import time
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / '.toolchains'
DOWNLOADS = BASE / 'downloads'


def install(item):
    name, spec = item
    destination = BASE / {'android_commandline': 'android/cmdline-tools/latest',
                          'flutter': 'flutter', 'jdk': 'jdk'}[name]
    marker = destination / '.broadband-installed.json'
    if marker.exists() and json.loads(marker.read_text()) == spec:
        print(f'{name}: already installed', flush=True)
        return
    if destination.exists():
        raise RuntimeError(f'{destination} exists without a matching installation marker')
    algorithm = 'sha1' if 'sha1' in spec else 'sha256'
    expected = spec.get(algorithm)
    if expected is None:
        with urllib.request.urlopen(spec['checksum_url'], timeout=30) as response:
            expected = response.read(4096).decode().split()[0]
    if len(expected) != {'sha1': 40, 'sha256': 64}[algorithm]:
        raise RuntimeError(f'{name}: invalid publisher checksum')
    archive = DOWNLOADS / spec['url'].rsplit('/', 1)[-1]
    if not archive.exists():
        print(f'{name}: downloading pinned archive', flush=True)
        for attempt in range(6):
            download = subprocess.run([
                'curl', '--fail', '--location', '--silent', '--show-error',
                '--connect-timeout', '20', '--speed-time', '60', '--speed-limit',
                '1024', '--continue-at', '-', '--output',
                str(archive) + '.part', spec['url']])
            if download.returncode == 0:
                break
            if attempt == 5:
                raise RuntimeError(f'{name}: archive download failed after resume attempts')
            print(f'{name}: resuming interrupted download ({attempt + 1}/5)', flush=True)
            time.sleep(2)
        Path(str(archive) + '.part').rename(archive)
    digest = hashlib.new(algorithm)
    with archive.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    if digest.hexdigest().lower() != expected.lower():
        raise RuntimeError(f'{name}: checksum mismatch; archive not extracted')
    print(f'{name}: checksum verified; extracting', flush=True)
    staging = BASE / f'.extract-{name}'
    if staging.exists():
        raise RuntimeError(f'{staging} exists; inspect the interrupted installation')
    staging.mkdir()
    if archive.name.endswith('.zip'):
        with zipfile.ZipFile(archive) as package:
            package.extractall(staging)
        for executable in (staging / 'cmdline-tools/bin').iterdir():
            executable.chmod(executable.stat().st_mode | 0o111)
    else:
        subprocess.run(['tar', '--no-same-owner', '-xf', str(archive), '-C', str(staging)],
                       check=True)
    children = list(staging.iterdir())
    if len(children) != 1 or not children[0].is_dir():
        raise RuntimeError(f'{name}: unexpected archive layout')
    destination.parent.mkdir(parents=True, exist_ok=True)
    children[0].rename(destination)
    staging.rmdir()
    marker.write_text(json.dumps(spec, indent=2) + '\n')
    print(f'{name}: ready at {destination}', flush=True)


if __name__ == '__main__':
    if platform.system() != 'Linux' or platform.machine() != 'x86_64':
        raise SystemExit('Use this bootstrap inside x86_64 WSL/Linux.')
    DOWNLOADS.mkdir(parents=True, exist_ok=True)
    install_lock = (BASE / 'install.lock').open('a')
    try:
        fcntl.flock(install_lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        raise SystemExit('Another bootstrap is already running; wait for that process.')
    manifest = json.loads((ROOT / 'tools/toolchains.lock.json').read_text())
    with ThreadPoolExecutor(max_workers=3) as pool:
        list(pool.map(install, manifest.items()))
    print('Run: source tools/wsl-env.sh', flush=True)
