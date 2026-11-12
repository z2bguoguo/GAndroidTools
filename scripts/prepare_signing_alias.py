#!/usr/bin/env python3
# Copyright (c) 2026 GAndroidTools contributors
# SPDX-License-Identifier: GPL-3.0-or-later
"""Resolve the release private-key alias by the pinned certificate, without printing secrets."""
import base64
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys


def select_alias(report, expected, configured):
    expected = expected.strip().replace(':', '').lower()
    if not re.fullmatch(r'[a-f0-9]{64}', expected):
        raise ValueError('Invalid pinned signing certificate fingerprint')
    candidates = []
    for block in re.split(r'(?m)^Alias name: ', report)[1:]:
        alias = block.splitlines()[0].strip()
        if not re.search(r'(?m)^Entry type: PrivateKeyEntry\s*$', block):
            continue
        leaf = re.search(r'-----BEGIN CERTIFICATE-----\s*([A-Za-z0-9+/=\s]+?)\s*-----END CERTIFICATE-----', block)
        if leaf is None:
            continue
        digest = hashlib.sha256(base64.b64decode(re.sub(r'\s+', '', leaf.group(1)), validate=True)).hexdigest()
        if digest == expected:
            candidates.append(alias)
    if configured in candidates:
        selected = configured
    elif len(candidates) == 1:
        selected = candidates[0]
    else:
        raise ValueError('Keystore must contain one unambiguous private key matching signing-certificate.sha256')
    if not re.fullmatch(r'[A-Za-z0-9._-]{1,128}', selected):
        raise ValueError('Release key alias must contain only letters, digits, dots, underscores or hyphens')
    return selected


def main():
    store = os.environ.get('SIGNING_STORE_FILE', '')
    if not store or not os.environ.get('SIGNING_STORE_PASSWORD'):
        raise ValueError('Missing persistent keystore or store password')
    java_home = os.environ.get('JAVA_HOME')
    keytool = str(Path(java_home) / 'bin' / 'keytool') if java_home else 'keytool'
    result = subprocess.run([
        keytool, '-J-Duser.language=en', '-J-Duser.country=US', '-list', '-rfc',
        '-keystore', store, '-storepass:env', 'SIGNING_STORE_PASSWORD'
    ], capture_output=True, text=True, timeout=30)
    if result.returncode:
        raise ValueError('Cannot read signing keystore; check ANDROID_KEYSTORE_BASE64 and ANDROID_STORE_PASSWORD')
    configured = os.environ.get('SIGNING_KEY_ALIAS', '')
    alias = select_alias(result.stdout, Path(sys.argv[1]).read_text(), configured)
    if alias != configured:
        print('::warning::ANDROID_KEY_ALIAS does not identify the pinned release key; using the certificate-verified alias for this build.')
    print('::add-mask::' + alias)
    with open(os.environ['GITHUB_ENV'], 'a', encoding='utf-8') as output:
        output.write('SIGNING_KEY_ALIAS=' + alias + '\n')
    print('Persistent release private-key alias verified against the pinned certificate.')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, subprocess.SubprocessError) as error:
        print('::error::' + str(error), file=sys.stderr)
        sys.exit(1)
