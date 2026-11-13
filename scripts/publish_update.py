#!/usr/bin/env python3
# Copyright (c) 2026 GAndroidTools contributors
# SPDX-License-Identifier: GPL-3.0-or-later
"""Write public metadata only for the APK already checked by apksigner and the certificate pin."""
import hashlib
import json
import os
from pathlib import Path
import re
import sys


def manifest(apk, code, version):
    if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+(?:-pre\.[0-9]+)?', version):
        raise ValueError('Invalid version name')
    if not 1 <= code <= 2_147_483_647:
        raise ValueError('Invalid version code')
    if apk.name != 'GAndroidTools-' + version + '.apk':
        raise ValueError('APK filename must match its version')
    size = apk.stat().st_size
    if not 0 < size <= 128 * 1024 * 1024:
        raise ValueError('APK size out of range')
    return {'versionCode': code, 'versionName': version,
            'url': 'https://z2bguoguo.github.io/GAndroidTools/downloads/' + apk.name,
            'size': size, 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest()}


if __name__ == '__main__':
    apk = Path(sys.argv[1])
    output = Path(sys.argv[2])
    output.write_text(json.dumps(manifest(apk, int(os.environ['BUILD_NUMBER']), os.environ['VERSION_NAME']), indent=2) + '\n')
