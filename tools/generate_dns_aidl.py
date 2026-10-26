#!/usr/bin/env python3
"""Generate a versioned DNS resolver client from frozen AOSP AIDL schemas.

Only Java package names change; @Descriptor preserves each original Binder
descriptor. The compiler derives transaction IDs from the complete official
interface. No hand-written transaction numbers or incomplete interface padding.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'native/third_party/android-dnsresolver'
PACKAGE = 'dev.broadband.toolbox.dnsapi'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    manifest = json.loads((SOURCE / 'manifest.json').read_text())
    actual = {str(p.relative_to(SOURCE)) for sub in ['v10', 'event-v1']
              for p in (SOURCE / sub).rglob('*') if p.is_file()}
    if actual != set(manifest['files']):
        raise SystemExit('Unexpected or missing files in the frozen AOSP schemas')
    for name, expected in manifest['files'].items():
        if hashlib.sha256((SOURCE / name).read_bytes()).hexdigest() != expected:
            raise SystemExit(f'AOSP schema checksum mismatch: {name}')
    stage = ROOT / '.cache/dns-aidl'
    if stage.exists():
        shutil.rmtree(stage)
    inputs = stage / 'aidl'
    output = stage / 'java'
    output.mkdir(parents=True)
    work = []
    for directory, version in [('event-v1', 1), ('v10', 10)]:
        api_hash = (SOURCE / directory / '.hash').read_text().strip()
        for source in sorted((SOURCE / directory).rglob('*.aidl')):
            text = source.read_text()
            package = re.search(r'^package ([\w.]+);', text, re.M).group(1)
            contract = re.search(r'^(?:oneway )?interface (\w+)', text, re.M)
            text = text.replace('android.net', PACKAGE)
            if contract:
                declaration = contract.group(0)
                text = text.replace(declaration, f'@Descriptor(value="{package}.{contract.group(1)}")\n{declaration}', 1)
            relative = Path(*package.replace('android.net', PACKAGE).split('.')) / source.name
            target = inputs / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(text)
            work.append((target, version, api_hash))
    sdk = Path(os.environ.get('ANDROID_HOME', ROOT / '.toolchains/android'))
    compiler = sdk / 'build-tools/36.0.0/aidl'
    for source, version, api_hash in work:
        subprocess.run([str(compiler), '--lang=java', '--structured', '--min_sdk_version=28',
                        f'--version={version}', f'--hash={api_hash}',
                        '-I', str(inputs.relative_to(ROOT)), '-o', str(output.relative_to(ROOT)),
                        str(source.relative_to(ROOT))], cwd=ROOT, check=True)
    destination = ROOT / 'apps/mobile/android/app/src/main/java'
    expected_files = {p.relative_to(output) for p in output.rglob('*.java')}
    for relative in sorted(expected_files):
        # AIDL writes argv[0] into a comment; omit the machine-specific SDK path.
        generated = (output / relative).read_text().replace(
            f' * Using: {compiler}', ' * Using: build-tools/36.0.0/aidl', 1).encode()
        target = destination / relative
        if args.check:
            if not target.is_file() or target.read_bytes() != generated:
                raise SystemExit(f'Generated DNS client needs refresh: {relative}')
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(generated)
    existing = {p.relative_to(destination) for p in (destination / Path(*PACKAGE.split('.'))).rglob('*.java')}
    if existing != expected_files:
        raise SystemExit('Unexpected files in the generated DNS client directory')
    print(f'AOSP DNS resolver Java client {"verified" if args.check else "generated"}: {len(expected_files)} files')


if __name__ == '__main__':
    main()
