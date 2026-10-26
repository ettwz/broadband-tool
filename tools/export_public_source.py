#!/usr/bin/env python3
"""Export the reviewed working tree without Git history, local files or caches."""
import argparse
import hashlib
import stat
from pathlib import Path
import zipfile
from public_source import issues, source_files

ROOT = Path(__file__).resolve().parents[1]


def export(root, destination):
    files = source_files(root)
    findings = issues(root, files)
    if findings:
        for name, line, reason in findings:
            print(f'{name}:{line}: {reason}')
        raise RuntimeError('Fix public-source findings before exporting')
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(destination.suffix + '.tmp')
    try:
        with zipfile.ZipFile(temporary, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for name in files:
                path = root / name
                info = zipfile.ZipInfo('broadband-tool/' + name, date_time=(1980, 1, 1, 0, 0, 0))
                info.create_system = 3
                mode = 0o755 if path.stat().st_mode & stat.S_IXUSR else 0o644
                info.external_attr = (stat.S_IFREG | mode) << 16
                info.compress_type = zipfile.ZIP_DEFLATED
                archive.writestr(info, path.read_bytes())
        temporary.replace(destination)
    finally:
        temporary.unlink(missing_ok=True)
    digest = hashlib.sha256(destination.read_bytes()).hexdigest()
    destination.with_suffix(destination.suffix + '.sha256').write_text(f'{digest}  {destination.name}\n')
    print(f'Public source archive created: {destination.name}; {len(files)} files; no Git history.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, default=ROOT / 'dist/broadband-tool-public-source.zip')
    args = parser.parse_args()
    export(ROOT, args.output.resolve())
