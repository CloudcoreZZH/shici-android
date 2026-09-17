"""Authorized, workspace-local Android toolchain. Run with conda NLP Python."""
from pathlib import Path
import concurrent.futures
import hashlib
import json
import os
import subprocess
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DOWNLOADS = ROOT / '.downloads'
TOOLS = ROOT / '.tooling'
SDK = TOOLS / 'android-sdk'
JAVA = (TOOLS / 'jdk-21') if (TOOLS / 'jdk-21/bin/java.exe').exists() else Path(r'D:\PyCharm 2025.2.1.1\jbr')
REPOSITORY = 'https://dl.google.com/android/repository/'

def request(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'Shici-build/0.1'}), timeout=90)

def download(url, name, digest=None, algorithm='sha256', limit=300_000_000):
    target = DOWNLOADS / name
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists() and digest and hashlib.new(algorithm, target.read_bytes()).hexdigest() == digest:
        print('Verified cached', name, flush=True)
        return target
    temporary = target.with_suffix(target.suffix + '.part')
    print('Downloading', name, flush=True)
    hasher = hashlib.new(algorithm)
    count = 0
    with request(url) as response, temporary.open('wb') as output:
        while chunk := response.read(1024 * 1024):
            count += len(chunk)
            if count > limit:
                raise RuntimeError(f'{name} exceeds approved individual size limit')
            hasher.update(chunk)
            output.write(chunk)
    if digest and hasher.hexdigest() != digest:
        raise RuntimeError(f'Checksum mismatch: {name}')
    temporary.replace(target)
    print('Saved', name, count, 'bytes', flush=True)
    return target

def extract(archive, destination):
    destination.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive) as package:
        root = destination.resolve()
        for member in package.infolist():
            resolved = (destination / member.filename).resolve()
            if not resolved.is_relative_to(root):
                raise RuntimeError('Archive path escapes destination')
        package.extractall(destination)

def main():
    if not (JAVA / 'bin/java.exe').is_file():
        raise RuntimeError('Expected existing JBR 21 was not found')
    for folder in (TOOLS, SDK, ROOT / '.cache/gradle', ROOT / '.cache/android', ROOT / '.cache/tmp'):
        folder.mkdir(parents=True, exist_ok=True)
    xml = request(REPOSITORY + 'repository2-3.xml').read()
    packages = ET.fromstring(xml)
    candidates = []
    for package in packages.findall('remotePackage'):
        if not package.attrib['path'].startswith('cmdline-tools;'):
            continue
        if package.find('channelRef').attrib['ref'] != 'channel-0':
            continue
        for archive in package.findall('archives/archive'):
            if archive.findtext('host-os') == 'windows':
                candidates.append((int(package.findtext('revision/major')), archive.find('complete')))
    _, info = max(candidates, key=lambda p: p[0])
    commandline_name = info.findtext('url')
    checksum = info.find('checksum')
    gradle_version = '8.13'
    gradle_url = f'https://downloads.gradle.org/distributions/gradle-{gradle_version}-bin.zip'
    gradle_hash = request(gradle_url + '.sha256').read().decode().strip()
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        a = pool.submit(download, REPOSITORY + commandline_name, commandline_name, checksum.text, checksum.attrib.get('type', 'sha1'))
        b = pool.submit(download, gradle_url, f'gradle-{gradle_version}-bin.zip', gradle_hash)
        commandline_zip, gradle_zip = a.result(), b.result()
    commandline_home = SDK / 'cmdline-tools/latest'
    if not (commandline_home / 'bin/sdkmanager.bat').exists():
        staging = TOOLS / 'sdk-stage'
        extract(commandline_zip, staging)
        commandline_home.parent.mkdir(parents=True, exist_ok=True)
        (staging / 'cmdline-tools').rename(commandline_home)
    if not (TOOLS / f'gradle-{gradle_version}/bin/gradle.bat').exists():
        extract(gradle_zip, TOOLS)
    env = os.environ.copy()
    env.update(JAVA_HOME=str(JAVA), ANDROID_HOME=str(SDK), ANDROID_SDK_ROOT=str(SDK),
               ANDROID_USER_HOME=str(ROOT / '.cache/android'), GRADLE_USER_HOME=str(ROOT / '.cache/gradle'),
               TEMP=str(ROOT / '.cache/tmp'), TMP=str(ROOT / '.cache/tmp'))
    # Install official archives directly. The new CLI wrapper does not preserve
    # semicolon package names on this Windows setup; no additional CLI is needed.
    for package_name in ['platforms;android-36', 'build-tools;35.0.0', 'platform-tools']:
        destination = SDK.joinpath(*package_name.split(';'))
        if (destination / 'source.properties').exists():
            continue
        package = next(p for p in packages.findall('remotePackage') if p.attrib['path'] == package_name)
        archive = next(a for a in package.findall('archives/archive') if a.findtext('host-os') in (None, 'windows'))
        complete = archive.find('complete')
        archive_name = complete.findtext('url')
        check = complete.find('checksum')
        archive_file = download(REPOSITORY + archive_name, archive_name, check.text, check.attrib.get('type', 'sha1'))
        stage = TOOLS / ('stage-' + package_name.replace(';', '-'))
        extract(archive_file, stage)
        roots = [p for p in stage.iterdir() if p.is_dir()]
        if len(roots) != 1:
            raise RuntimeError(f'Unexpected SDK archive layout: {archive_name}')
        destination.parent.mkdir(parents=True, exist_ok=True)
        roots[0].rename(destination)
        print('Installed', package_name, flush=True)
    licenses = SDK / 'licenses'
    licenses.mkdir(exist_ok=True)
    for license_node in packages.findall('license'):
        digest = hashlib.sha1(license_node.text.encode('utf-8')).hexdigest()
        (licenses / license_node.attrib['id']).write_text(digest + '\n', encoding='utf-8')
    (ROOT / 'local.properties').write_text('sdk.dir=.tooling/android-sdk\n', encoding='utf-8')
    (TOOLS / 'toolchain.json').write_text(json.dumps({'java': str(JAVA), 'sdk': str(SDK), 'gradle': gradle_version, 'gradle_sha256': gradle_hash, 'commandline': commandline_name}, indent=2), encoding='utf-8')
    print('Toolchain ready.', flush=True)

if __name__ == '__main__':
    main()
