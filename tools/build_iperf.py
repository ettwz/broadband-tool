#!/usr/bin/env python3
"""Build the vendored iperf CLI for Linux tests and Android APK packaging."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'native/third_party/iperf-3.22'


def build(abi):
    work = ROOT / '.cache/iperf-build' / abi
    work.mkdir(parents=True, exist_ok=True)
    env = os.environ.copy()
    options = ['--disable-shared', '--enable-static', '--without-openssl',
               '--disable-profiling', '--disable-dependency-tracking']
    if abi != 'host':
        sdk = Path(env.get('ANDROID_HOME', ROOT / '.toolchains/android'))
        ndk = sdk / 'ndk/28.2.13676358/toolchains/llvm/prebuilt/linux-x86_64'
        target = {'arm64-v8a': 'aarch64-linux-android', 'x86_64': 'x86_64-linux-android'}[abi]
        env.update(CC=str(ndk / f'bin/{target}28-clang'),
                   AR=str(ndk / 'bin/llvm-ar'), RANLIB=str(ndk / 'bin/llvm-ranlib'),
                   STRIP=str(ndk / 'bin/llvm-strip'),
                   CFLAGS='-O2 -fPIE', LDFLAGS='-pie -Wl,-z,max-page-size=16384',
                   LIBS='-landroid')
        options.append(f'--host={target}')
    with (work / 'configure.log').open('w') as log:
        result = subprocess.run([str(SOURCE / 'configure'), *options], cwd=work,
                                env=env, stdout=log, stderr=subprocess.STDOUT)
    if result.returncode:
        print((work / 'configure.log').read_text()[-3500:])
        raise SystemExit(result.returncode)
    with (work / 'make.log').open('w') as log:
        result = subprocess.run(['make', '-j4'], cwd=work, env=env,
                                stdout=log, stderr=subprocess.STDOUT)
    if result.returncode:
        print((work / 'make.log').read_text()[-4500:])
        raise SystemExit(result.returncode)
    binary = work / 'src/iperf3'
    if abi != 'host':
        destination = ROOT / f'apps/mobile/android/app/src/main/jniLibs/{abi}/libiperf_exec.so'
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(binary, destination)
        subprocess.run([env['STRIP'], str(destination)], check=True)
        print(f'{abi}: {destination}', flush=True)
    else:
        print(f'host: {binary}', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--abi', choices=['host', 'arm64-v8a', 'x86_64', 'android'], default='host')
    args = parser.parse_args()
    for architecture in (['arm64-v8a', 'x86_64'] if args.abi == 'android' else [args.abi]):
        build(architecture)
