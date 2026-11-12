#!/usr/bin/env python3
# Copyright (c) 2026 GAndroidTools contributors
# SPDX-License-Identifier: GPL-3.0-or-later
"""Offline repository invariants; content secret scanning runs separately in CI."""
from pathlib import Path
import re
import sys

ROOT=Path(__file__).resolve().parent.parent
errors=[]
required=['AGENTS.md','README.md','README.en.md','LICENSE','SECURITY.md','CONTRIBUTING.md','THIRD_PARTY_NOTICES.md','gradlew','gradlew.bat','gradle/wrapper/gradle-wrapper.jar','package-lock.json','docs/.vitepress/config.mjs','.github/workflows/quality.yml']
for name in required:
    if not(ROOT/name).is_file():errors.append('Missing '+name)
for p in ROOT.rglob('*'):
    if not p.is_file() or any(x in p.parts for x in ['.git','.gradle','build','node_modules','dist','cache']):continue
    if p.suffix in ['.jks','.keystore','.p12'] or p.name=='credentials.json':errors.append('Signing material: '+str(p.relative_to(ROOT)))
for name,other in [('README.md','README.en.md'),('README.en.md','README.md')]:
    text=(ROOT/name).read_text()
    if other not in text:errors.append('Missing README language switch: '+name)
for p in ROOT.glob('docs/guide/*.md'):
    if not(ROOT/'docs/en/guide'/p.name).exists():errors.append('Missing English doc '+p.name)
manifest=(ROOT/'app/src/main/AndroidManifest.xml').read_text()
if 'android:allowBackup="false"' not in manifest:errors.append('Sensitive application data backup must remain disabled')
if 'gandroidtools' not in (ROOT/'app/build.gradle.kts').read_text():errors.append('Application ID missing')
if errors:
    print('\n'.join(errors));sys.exit(1)
print('Repository checks passed.')
