#!/usr/bin/env python3
# Copyright (c) 2026 GAndroidTools contributors
# SPDX-License-Identifier: GPL-3.0-or-later
import re
import sys
from pathlib import Path

def verify(report,expected):
    matches=re.findall(r'Signer #\d+ certificate SHA-256 digest:\s*([a-fA-F0-9:]+)',report)
    normalize=lambda s:s.strip().replace(':','').lower()
    if len(matches)!=1 or normalize(matches[0])!=normalize(expected):
        raise ValueError('APK signing certificate does not match the persistent release identity')

if __name__=='__main__':
    verify(Path(sys.argv[1]).read_text(),Path(sys.argv[2]).read_text())
    print('Persistent release certificate verified.')
