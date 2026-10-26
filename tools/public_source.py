"""Collect and check the source-only publication surface without reading local secrets."""
from pathlib import Path
import hashlib
import ipaddress
import json
import os
import re
import subprocess

ROOT_FILES = {'.gitattributes', '.gitignore', 'README.md', 'LICENSE'}
ROOT_DIRS = {'apps', 'docs', 'native', 'tests', 'tools'}
LOCAL_PARTS = {'.git', '.cache', '.toolchains', '.idea', '.vscode', '.devcontainer',
               '.dart_tool', '.gradle', '.cxx', '.externalNativeBuild', '__pycache__',
               'node_modules', '.venv', 'venv', 'build', 'dist', 'coverage', 'Pods', '.symlinks'}
LOCAL_NAMES = {'local.properties', 'key.properties', 'Generated.xcconfig',
               'flutter_export_environment.sh', '.flutter-plugins', '.flutter-plugins-dependencies'}
BLOCKED_SUFFIXES = {'.key', '.pem', '.p12', '.pfx', '.jks', '.keystore', '.apk', '.aab',
                    '.so', '.pyc', '.log', '.pcap', '.pcapng', '.hprof'}
DOC_NETS = tuple(ipaddress.ip_network(net) for net in ('192.0.2.0/24', '198.51.100.0/24', '203.0.113.0/24'))
# Published fictional UI fixtures and protocol edge cases, never discovered hosts.
DEMO_IPS = {'0.1.2.3', '1.1.1.1', '10.0.2.2', '192.168.1.1', '192.168.1.23',
            '192.168.10.0', '192.168.20.0', '192.168.20.1', '192.168.20.10',
            '192.168.20.23', '192.168.20.36', '192.168.50.2'}
IPV4 = re.compile(r'(?<![\w.])(?:[0-9]{1,3}\.){3}[0-9]{1,3}(?![\w.])')
MAC = re.compile(r'(?i)(?<![0-9a-f:])(?:[0-9a-f]{2}:){5}[0-9a-f]{2}(?![0-9a-f:])')
LOCAL_PATH = re.compile(r'/(?:home|Users)/[^\s/"\']+|/mnt/[a-z]/(?:Users)/[^\s/"\']+|[A-Za-z]:[\\/]Users[\\/][^\s\\/"\']+')
URL_ACCOUNT = re.compile(r'(?i)(?:https?|ssh)://[^\s/]+@')
EMAIL = re.compile(r'[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}')
PRIVATE_MATERIAL = re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----')


def blocked(name):
    path = Path(name)
    lower = name.lower()
    return (any(part in LOCAL_PARTS or part.startswith('.env') for part in path.parts)
            or path.name in LOCAL_NAMES or path.name in {'id_rsa', 'id_ed25519'}
            or path.suffix.lower() in BLOCKED_SUFFIXES
            or any(word in lower for word in ('credential', 'secret', 'token')))


def source_files(root):
    root = Path(root)
    if (root / '.git').exists():
        names = set()
        for options in (['--cached'], ['--others', '--exclude-standard']):
            data = subprocess.check_output(['git', '-C', str(root), 'ls-files', '-z', *options,
                                            '--', *sorted(ROOT_FILES | ROOT_DIRS)])
            names.update(name for name in data.decode().split('\0') if name)
        # Deleted working-tree files are deliberately absent from the export.
        return sorted(name for name in names if blocked(name) or (root / name).is_symlink() or (root / name).is_file())
    names = {name for name in ROOT_FILES if (root / name).is_file()}
    for base in ROOT_DIRS:
        folder = root / base
        if folder.is_symlink():
            names.add(base)
            continue
        for current, directories, files in os.walk(folder, followlinks=False):
            prefix = Path(current).relative_to(root)
            for name in directories[:]:
                relative = str(prefix / name)
                if blocked(relative) or (root / relative).is_symlink():
                    directories.remove(name)
                    if (root / relative).is_symlink() or name not in LOCAL_PARTS:
                        names.add(relative)
            names.update(str(prefix / name) for name in files)
    return sorted(names)


def issues(root, names=None):
    root = Path(root)
    names = source_files(root) if names is None else names
    manifest_path = root / 'docs/public-assets.json'
    if manifest_path.is_symlink():
        return [('docs/public-assets.json', 0, 'symlink')]
    assets = json.loads(manifest_path.read_text()).get('assets', {}) if manifest_path.is_file() else {}
    findings = []
    for name in names:
        path = root / name
        if blocked(name):
            findings.append((name, 0, 'excluded-local-file'))
            continue  # Never open excluded files, even to scan them.
        if path.is_symlink():
            findings.append((name, 0, 'symlink'))
            continue
        vendor = name.startswith('native/third_party/') or name.startswith('apps/mobile/assets/licenses/')
        if path.suffix.lower() in {'.png', '.jpg', '.jpeg', '.webp', '.gif'} and not vendor:
            approval = assets.get(name)
            if not approval:
                findings.append((name, 0, 'unreviewed-image'))
            elif hashlib.sha256(path.read_bytes()).hexdigest() != approval['sha256']:
                findings.append((name, 0, 'changed-reviewed-image'))
            continue
        if vendor or name == 'apps/mobile/android/gradle/wrapper/gradle-wrapper.jar':
            continue
        try:
            text = path.read_text()
        except UnicodeError:
            findings.append((name, 0, 'unexpected-binary'))
            continue
        for number, line in enumerate(text.splitlines(), 1):
            for pattern, reason in ((LOCAL_PATH, 'local-user-path'), (URL_ACCOUNT, 'url-account'),
                                    (PRIVATE_MATERIAL, 'private-material')):
                if pattern.search(line):
                    findings.append((name, number, reason))
            for match in EMAIL.finditer(line):
                if not re.search(r'@[123]x\.(?:png|jpe?g|webp)$', match.group(), re.I):
                    findings.append((name, number, 'personal-email'))
            for match in IPV4.finditer(line):
                try:
                    address = ipaddress.ip_address(match.group())
                except ValueError:  # Deliberately malformed address-validation fixtures.
                    continue
                if (address.is_loopback or address.is_unspecified or address.is_multicast
                        or int(address) >= int(ipaddress.ip_address('240.0.0.0'))
                        or any(address in net for net in DOC_NETS) or str(address) in DEMO_IPS):
                    continue
                findings.append((name, number, 'non-example-ipv4'))
            for match in MAC.finditer(line):
                value = match.group().lower()
                if value not in {'00:00:00:00:00:00', '02:11:22:33:44:55'} and not re.fullmatch(r'02:00:00:00:00:[0-9a-f]{2}', value):
                    findings.append((name, number, 'non-example-mac'))
    for name in assets:
        if name not in names:
            findings.append((name, 0, 'missing-reviewed-image'))
    return findings
