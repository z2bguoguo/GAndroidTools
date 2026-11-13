# Copyright (c) 2026 GAndroidTools contributors
# SPDX-License-Identifier: GPL-3.0-or-later
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('publish_update', Path(__file__).resolve().parents[1] / 'publish_update.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class UpdateManifestTest(unittest.TestCase):
    def test_metadata_matches_exact_published_bytes_and_version(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / 'GAndroidTools-0.2.0-pre.8.apk'
            apk.write_bytes(b'checked-release-bytes')
            value = module.manifest(apk, 8, '0.2.0-pre.8')
            self.assertEqual(value['size'], 21)
            self.assertEqual(value['versionCode'], 8)
            self.assertTrue(value['url'].endswith(apk.name))
            self.assertEqual(len(value['sha256']), 64)
            with self.assertRaises(ValueError):
                module.manifest(apk, 8, '0.2.0-pre.9')
            with self.assertRaises(ValueError):
                module.manifest(apk, 0, '0.2.0-pre.8')
