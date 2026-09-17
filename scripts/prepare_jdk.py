"""Fetch the explicitly approved complete JDK into this project only."""
import json
from pathlib import Path
from bootstrap import request, download, extract

ROOT = Path(__file__).resolve().parents[1]
target = ROOT / '.tooling/jdk-21'
if (target / 'bin/jlink.exe').exists():
    print('Complete project JDK already available.')
else:
    metadata = json.loads(request('https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse').read())[0]
    package = metadata['binary']['package']
    if package['size'] > 231_000_000:
        raise RuntimeError('JDK exceeds approved 220 MB range; obtain additional approval')
    archive = download(package['link'], package['name'], package['checksum'], limit=231_000_000)
    staging = ROOT / '.tooling/jdk-stage'
    extract(archive, staging)
    roots = [p for p in staging.iterdir() if p.is_dir() and (p / 'bin/jlink.exe').exists()]
    if len(roots) != 1:
        raise RuntimeError('Unexpected JDK archive layout')
    roots[0].rename(target)
    (ROOT / '.tooling/jdk-source.json').write_text(json.dumps(metadata, indent=2), encoding='utf-8')
    print('Complete JDK ready:', target, flush=True)
