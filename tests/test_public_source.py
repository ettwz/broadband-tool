import hashlib
import json
from pathlib import Path
import stat
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from public_source import issues
from export_public_source import export


class PublicSourceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='public-source-test-')
        self.root = Path(self.temp.name)
        (self.root / 'docs').mkdir()
        (self.root / 'README.md').write_text('Documentation sample: 192.0.2.10; loopback: 127.0.0.1\n')

    def tearDown(self):
        self.temp.cleanup()

    def test_examples_pass_and_actual_environment_shapes_are_reported_without_values(self):
        self.assertEqual(issues(self.root), [])
        address = '.'.join(['172', '31', '240', '10'])
        user_path = str(Path('/') / 'home' / 'example-user' / 'work')
        (self.root / 'docs/note.md').write_text(address + '\n' + user_path + '\n')
        found = issues(self.root)
        self.assertEqual({item[2] for item in found}, {'non-example-ipv4', 'local-user-path'})
        self.assertNotIn(address, str(found))
        self.assertNotIn(user_path, str(found))

    def test_excluded_files_are_not_opened(self):
        folder = self.root / 'apps'
        folder.mkdir()
        excluded = folder / '.env'
        excluded.write_text('TEST_FIXTURE_ONLY')
        original = Path.read_text

        def guarded(path, *args, **kwargs):
            if path == excluded:
                raise AssertionError('Excluded file must not be read')
            return original(path, *args, **kwargs)

        with patch.object(Path, 'read_text', guarded):
            self.assertIn(('apps/.env', 0, 'excluded-local-file'), issues(self.root))

    def test_image_approval_is_invalidated_when_image_changes(self):
        image = self.root / 'docs/example.png'
        image.write_bytes(b'PNG fixture')
        self.assertIn(('docs/example.png', 0, 'unreviewed-image'), issues(self.root))
        manifest = self.root / 'docs/public-assets.json'
        manifest.write_text(json.dumps({'assets': {'docs/example.png': {'sha256': hashlib.sha256(image.read_bytes()).hexdigest()}}}))
        self.assertEqual(issues(self.root), [])
        image.write_bytes(b'changed fixture')
        self.assertIn(('docs/example.png', 0, 'changed-reviewed-image'), issues(self.root))

    def test_symlinks_are_rejected_without_reading_targets(self):
        (self.root / 'docs/link.md').symlink_to(self.root / 'absent-file')
        self.assertIn(('docs/link.md', 0, 'symlink'), issues(self.root))
        (self.root / 'docs/public-assets.json').symlink_to(self.root / 'absent-manifest')
        self.assertEqual(issues(self.root), [('docs/public-assets.json', 0, 'symlink')])

    def test_export_uses_working_tree_preserves_executable_and_omits_git_history(self):
        subprocess.run(['git', 'init', '--quiet', str(self.root)], check=True)
        (self.root / '.git/local-history-note').write_text('not for export')
        (self.root / 'LICENSE').write_text('Test license\n')
        (self.root / '.cache').mkdir()
        (self.root / '.cache/private-evidence.txt').write_text('not for export')
        (self.root / 'tools').mkdir()
        script = self.root / 'tools/run.sh'
        script.write_text('#!/bin/sh\nexit 0\n')
        script.chmod(0o755)
        first, second = self.root / 'one.zip', self.root / 'two.zip'
        export(self.root, first)
        export(self.root, second)
        self.assertEqual(first.read_bytes(), second.read_bytes())
        with zipfile.ZipFile(first) as package:
            self.assertEqual(set(package.namelist()), {'broadband-tool/README.md', 'broadband-tool/LICENSE', 'broadband-tool/tools/run.sh'})
            mode = package.getinfo('broadband-tool/tools/run.sh').external_attr >> 16
            self.assertTrue(mode & stat.S_IXUSR)
            self.assertFalse(any('.git/' in name or '.cache/' in name for name in package.namelist()))


if __name__ == '__main__':
    unittest.main()
