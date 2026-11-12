# Copyright (c) 2026 GAndroidTools contributors
# SPDX-License-Identifier: GPL-3.0-or-later
import importlib.util
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('verify',Path(__file__).parents[1]/'verify_certificate.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)

class CertificateTests(unittest.TestCase):
    def test_expected_identity(self):module.verify('Signer #1 certificate SHA-256 digest: aabbcc','AA:BB:CC\n')
    def test_wrong_identity(self):
        with self.assertRaises(ValueError):module.verify('Signer #1 certificate SHA-256 digest: aabbcc','ddeeff')
    def test_multiple_signers_rejected(self):
        with self.assertRaises(ValueError):module.verify('Signer #1 certificate SHA-256 digest: aa\nSigner #2 certificate SHA-256 digest: aa','aa')
