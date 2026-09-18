"""Verify the release binary, permissions, signatures, tests and 16 KB native alignment."""
from pathlib import Path
import hashlib
import json
import re
import shutil
import struct
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
APK = Path('app/build/outputs/apk/release/app-release.apk')
SDK_TOOLS = Path('.tooling/android-sdk/build-tools/35.0.0')

def run(arguments):
    result = subprocess.run([str(x) for x in arguments], cwd=ROOT, capture_output=True, text=True,
                            encoding='utf-8', errors='replace', check=True)
    return result.stdout

def main():
    badging = run([SDK_TOOLS / 'aapt.exe', 'dump', 'badging', APK])
    permissions = run([SDK_TOOLS / 'aapt.exe', 'dump', 'permissions', APK])
    assert "targetSdkVersion:'36'" in badging
    assert 'application-debuggable' not in badging
    assert "package: name='io.github.shici.app'" in badging
    for line in permissions.splitlines():
        if 'uses-permission:' in line:
            assert 'io.github.shici.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION' in line, line
    signed = run([Path('.tooling/jdk-21/bin/java.exe'), '-jar', SDK_TOOLS / 'lib/apksigner.jar',
                  'verify', '--verbose', '--print-certs', APK])
    alignment = run([SDK_TOOLS / 'zipalign.exe', '-c', '-P', '16', '-v', '4', APK])
    libraries = {}
    with zipfile.ZipFile(ROOT / APK) as package:
        assert 'assets/dictionary.db' in package.namelist()
        for name in package.namelist():
            if name.startswith(('lib/arm64-v8a/', 'lib/x86_64/')) and name.endswith('.so'):
                binary = package.read(name)
                assert binary[:4] == b'\x7fELF' and binary[4] == 2 and binary[5] == 1
                offset = struct.unpack_from('<Q', binary, 32)[0]
                stride, count = struct.unpack_from('<HH', binary, 54)
                alignments = [struct.unpack_from('<Q', binary, offset + stride * i + 48)[0]
                              for i in range(count) if struct.unpack_from('<I', binary, offset + stride * i)[0] == 1]
                assert all(value >= 16384 for value in alignments), (name, alignments)
                libraries[name] = alignments
    tests = []
    for module, task in [('core', 'test'), ('app', 'testDebugUnitTest')]:
        files = list((ROOT / module / 'build/test-results' / task).glob('TEST-*.xml'))
        assert files, f'Missing test results: {module}'
        for file in files:
            suite = ET.parse(file).getroot()
            assert int(suite.attrib['failures']) == 0 and int(suite.attrib['errors']) == 0, suite.attrib
            tests.append({'suite': suite.attrib['name'], 'tests': int(suite.attrib['tests']), 'failures': 0})
    lint = ROOT / 'app/build/reports/lint-results-release.xml'
    assert lint.exists(), 'Release lint report missing'
    issues = ET.parse(lint).getroot().findall('issue')
    assert not [issue for issue in issues if issue.attrib['severity'] in ('Error', 'Fatal')]
    destination = ROOT / 'dist'
    destination.mkdir(exist_ok=True)
    version = re.search(r"versionName='([^']+)'", badging).group(1)
    delivered = destination / f'拾词-{version}.apk'
    shutil.copy2(ROOT / APK, delivered)
    report = {'apk': delivered.name, 'bytes': delivered.stat().st_size,
              'sha256': hashlib.sha256(delivered.read_bytes()).hexdigest(),
              'target_sdk': 36, 'debuggable': False, 'tests': tests,
              'lint_errors': 0, 'lint_warnings': [{'id': issue.attrib['id'], 'message': issue.attrib['message']} for issue in issues],
              'permissions': permissions.strip(), 'native_16kb_alignment': libraries,
              'signature_verification': signed.strip(), 'zip_alignment': 'passed',
              'device_validation': 'not yet run on the user phone'}
    (destination / '验证报告.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    (destination / 'SHA256.txt').write_text(report['sha256'] + '  ' + delivered.name + '\n', encoding='utf-8')
    print(json.dumps({k: report[k] for k in ['apk', 'bytes', 'sha256', 'target_sdk', 'lint_errors']}, ensure_ascii=False), flush=True)

if __name__ == '__main__':
    main()
