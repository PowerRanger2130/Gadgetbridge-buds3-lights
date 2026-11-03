#!/usr/bin/env python3
"""Verify signed APK identity/version/ABI compatibility without installing it."""
import argparse
import hashlib
import json
import re
import subprocess
import zipfile
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('previous', type=Path)
parser.add_argument('new', type=Path)
parser.add_argument('--build-tools', required=True, type=Path)
args = parser.parse_args()
EXPECTED_PACKAGE = 'nodomain.freeyourgadget.gadgetbridge.buds3lights'
EXPECTED_CERT = 'f144f5741ebaa4297cabe42523b19c184072e187a1195b28671e4cf92d2564e6'
BASELINE_SHA256 = '132bcbbf9f1ef444229f2006abfe38b28d4f7e9c160667ebe09033e4df4c4813'

def inspect(apk):
    certs = subprocess.run([str(args.build_tools / 'apksigner'), 'verify', '--print-certs', str(apk)],
                           check=True, capture_output=True, text=True).stdout
    digests = re.findall(r'certificate SHA-256 digest: ([0-9a-f]+)', certs)
    if digests != [EXPECTED_CERT]:
        raise SystemExit('Unexpected signing certificate; update would not preserve identity')
    aapt = args.build_tools / 'aapt'
    if not aapt.exists():
        aapt = args.build_tools / 'aapt2'
    badging = subprocess.run([str(aapt), 'dump', 'badging', str(apk)],
                             check=True, capture_output=True, text=True).stdout
    package = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if package is None or package[1] != EXPECTED_PACKAGE:
        raise SystemExit('Unexpected Android package ID')
    min_sdk = int(re.search(r"sdkVersion:'(\d+)'", badging)[1])
    with zipfile.ZipFile(apk) as archive:
        abis = sorted({p.split('/')[1] for p in archive.namelist() if p.startswith('lib/') and p.endswith('.so')})
    return dict(package=package[1], version_code=int(package[2]), version_name=package[3],
                signer_sha256=digests[0], sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),
                size=apk.stat().st_size, native_abis=abis, min_sdk=min_sdk)

previous, new = inspect(args.previous), inspect(args.new)
if previous['sha256'] != BASELINE_SHA256:
    raise SystemExit('Previous APK is not the exact delivered build 4 baseline')
if new['version_code'] <= previous['version_code']:
    raise SystemExit('New APK version code must increase')
if new['native_abis'] != ['arm64-v8a']:
    raise SystemExit('Preview must contain only ARM64 native libraries')
if new['min_sdk'] > previous['min_sdk']:
    raise SystemExit('Preview must not raise the minimum Android version')
print(json.dumps(dict(upgrade_identity_verified=True, uninstall_required=False,
                     previous=previous, new=new,
                     scope='Package, signing identity, version and ABI verified. No phone installation performed.'), indent=2))
