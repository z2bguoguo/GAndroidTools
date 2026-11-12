# Copyright (c) 2026 GAndroidTools contributors
# SPDX-License-Identifier: GPL-3.0-or-later
import base64
import hashlib
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('signing_alias', Path(__file__).parents[1] / 'prepare_signing_alias.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def entry(alias, certificate=b'pinned-leaf', kind='PrivateKeyEntry', chain=b''):
    text = 'Alias name: ' + alias + '\nEntry type: ' + kind + '\n'
    for data in [certificate] + ([chain] if chain else []):
        text += '-----BEGIN CERTIFICATE-----\n' + base64.b64encode(data).decode() + '\n-----END CERTIFICATE-----\n'
    return text


class SigningAliasTests(unittest.TestCase):
    expected = hashlib.sha256(b'pinned-leaf').hexdigest()

    def test_resolves_wrong_configured_alias_using_certificate(self):
        self.assertEqual('release', module.select_alias(entry('release'), self.expected, 'wrong'))

    def test_prefers_configured_alias_if_certificate_matches(self):
        self.assertEqual('second', module.select_alias(entry('first') + entry('second'), self.expected, 'second'))

    def test_rejects_unrelated_keystore(self):
        with self.assertRaises(ValueError):
            module.select_alias(entry('release', b'unrelated'), self.expected, 'release')

    def test_trusted_certificate_entry_is_not_a_private_key(self):
        with self.assertRaises(ValueError):
            module.select_alias(entry('release', kind='trustedCertEntry'), self.expected, 'release')

    def test_only_leaf_certificate_may_match(self):
        with self.assertRaises(ValueError):
            module.select_alias(entry('release', b'unrelated-leaf', chain=b'pinned-leaf'), self.expected, 'release')

    def test_rejects_ambiguous_aliases(self):
        with self.assertRaises(ValueError):
            module.select_alias(entry('first') + entry('second'), self.expected, 'wrong')

    def test_rejects_environment_injection(self):
        with self.assertRaises(ValueError):
            module.select_alias(entry('unsafe alias'), self.expected, 'wrong')
