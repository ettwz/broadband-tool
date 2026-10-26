#!/usr/bin/env python3
"""Check publishable files; print locations and categories, never matched values."""
import argparse
from pathlib import Path
from public_source import issues, source_files

parser = argparse.ArgumentParser()
parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
args = parser.parse_args()
files = source_files(args.root)
findings = issues(args.root, files)
for name, line, reason in findings:
    print(f'{name}:{line}: {reason}')
if findings:
    raise SystemExit(f'Public source check failed: {len(findings)} findings')
print(f'Public source check passed: {len(files)} files; reviewed images unchanged.')
