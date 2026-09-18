"""Fetch the explicitly approved free reference datasets, bounded to 85 MB in this project."""
from pathlib import Path
import argparse
import hashlib
import json
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / '.downloads' / 'open-dictionary'
MAX_BYTES = 85_000_000
COMMIT = '70dc6b68c855f21e666a7a291ff8ead5ca1f7b44'
FILES = {
    'wiki-traditional.jsonl': 'https://kaikki.org/zhwiktionary/%E8%8B%B1%E8%AA%9E/kaikki.org-dictionary-%E8%8B%B1%E8%AA%9E.jsonl',
    'wiki-simplified.jsonl': 'https://kaikki.org/zhwiktionary/%E8%8B%B1%E8%AF%AD/kaikki.org-dictionary-%E8%8B%B1%E8%AF%AD.jsonl',
    'netem-2024.json': f'https://raw.githubusercontent.com/exam-data/NETEMVocabulary/{COMMIT}/netem_full_list.json',
    'NETEM-LICENSE.txt': f'https://raw.githubusercontent.com/exam-data/NETEMVocabulary/{COMMIT}/LICENSE',
    'NETEM-README.md': f'https://raw.githubusercontent.com/exam-data/NETEMVocabulary/{COMMIT}/README.md',
    'kaikki-source.html': 'https://kaikki.org/zhwiktionary/',
    'CC-BY-SA-4.0.txt': 'https://creativecommons.org/licenses/by-sa/4.0/legalcode.txt',
}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--download-approved', action='store_true', required=True)
    parser.parse_args()
    DEST.mkdir(parents=True, exist_ok=True)
    manifest = {}
    for name, url in FILES.items():
        path = DEST / name
        if not path.exists():
            partial = path.with_suffix(path.suffix + '.part')
            used = sum(p.stat().st_size for p in DEST.iterdir() if p.is_file())
            offset = partial.stat().st_size if partial.exists() else 0
            headers = {'User-Agent': 'Shici-Android/0.3.0 approved-offline-data'}
            if offset:
                headers['Range'] = f'bytes={offset}-'
            request = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(request, timeout=90) as response:
                if offset and response.status != 206:
                    raise RuntimeError('Server cannot resume safely; keep partial data and review download budget.')
                expected = int(response.headers.get('Content-Length', '0'))
                if used + expected > MAX_BYTES:
                    raise RuntimeError('85 MB approved download ceiling exceeded; additional approval required.')
                with partial.open('ab' if offset else 'wb') as output:
                    while True:
                        block = response.read(min(256 * 1024, MAX_BYTES - used + 1))
                        if not block:
                            break
                        used += len(block)
                        if used > MAX_BYTES:
                            raise RuntimeError('85 MB approved download ceiling exceeded.')
                        output.write(block)
            partial.replace(path)
        digest = hashlib.sha256()
        with path.open('rb') as source:
            for block in iter(lambda: source.read(1024 * 1024), b''):
                digest.update(block)
        manifest[name] = {'url': url, 'bytes': path.stat().st_size, 'sha256': digest.hexdigest()}
        print(json.dumps({'file': name, 'bytes': path.stat().st_size}), flush=True)
    (DEST / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'total_bytes': sum(p['bytes'] for p in manifest.values()), 'download_ceiling': MAX_BYTES}), flush=True)

if __name__ == '__main__':
    main()
