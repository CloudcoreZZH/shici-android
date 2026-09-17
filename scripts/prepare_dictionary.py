"""Convert the authorized ECDICT download into an indexed, read-only Android asset."""
import csv
import hashlib
import json
from pathlib import Path
import sqlite3
import unicodedata
from bootstrap import download, request

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'

def main():
    metadata = json.loads(request('https://api.github.com/repos/skywind3000/ECDICT/contents/ecdict.csv').read())
    if metadata['size'] > 105_000_000:
        raise RuntimeError('Dictionary exceeds approved 100 MB estimate; ask user before downloading')
    source = download(metadata['download_url'], 'ecdict.csv', limit=105_000_000)
    raw = source.read_bytes()
    git_hash = hashlib.sha1(f'blob {len(raw)}\0'.encode() + raw).hexdigest()
    if git_hash != metadata['sha']:
        raise RuntimeError('ECDICT does not match GitHub source hash')
    ASSETS.mkdir(parents=True, exist_ok=True)
    output = ASSETS / 'dictionary.db'
    temporary = ASSETS / 'dictionary.building.db'
    if temporary.exists():
        temporary.unlink()
    db = sqlite3.connect(temporary)
    db.executescript('''
        PRAGMA journal_mode=OFF;
        CREATE TABLE words(word TEXT PRIMARY KEY, phonetic TEXT NOT NULL, translation TEXT NOT NULL,
            definition TEXT NOT NULL, tags TEXT NOT NULL, exchange TEXT NOT NULL) WITHOUT ROWID;
        CREATE TABLE exam_senses(word TEXT NOT NULL, sense_id TEXT NOT NULL, count INTEGER NOT NULL CHECK(count>=0),
            source TEXT NOT NULL, PRIMARY KEY(word,sense_id)) WITHOUT ROWID;
        CREATE TABLE metadata(key TEXT PRIMARY KEY, value TEXT NOT NULL);
        PRAGMA user_version=1;
    ''')
    with source.open(encoding='utf-8-sig', newline='') as handle:
        batch = []
        for row in csv.DictReader(handle):
            word = unicodedata.normalize('NFKC', row['word'].strip()).lower()
            if not word or not row['translation'].strip():
                continue
            batch.append((word, row['phonetic'], row['translation'].replace('\\n', '\n'),
                          row['definition'].replace('\\n', '\n'), row['tag'], row['exchange']))
            if len(batch) >= 5000:
                db.executemany('INSERT OR IGNORE INTO words VALUES (?,?,?,?,?,?)', batch)
                batch.clear()
        db.executemany('INSERT OR IGNORE INTO words VALUES (?,?,?,?,?,?)', batch)
    count = db.execute('SELECT COUNT(*) FROM words').fetchone()[0]
    info = {'source': 'ECDICT', 'url': 'https://github.com/skywind3000/ECDICT', 'git_blob_sha1': git_hash,
            'download_sha256': hashlib.sha256(raw).hexdigest(), 'entries': count, 'exam_sense_statistics': False}
    db.executemany('INSERT INTO metadata VALUES (?,?)', [(k, str(v)) for k, v in info.items()])
    db.commit()
    assert db.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
    for word in ['address', 'issue', 'approach', 'maintain', 'persist']:
        assert db.execute('SELECT translation FROM words WHERE word=?', (word,)).fetchone(), word
    db.close()
    temporary.replace(output)
    (ASSETS / 'dictionary-source.json').write_text(json.dumps(info, ensure_ascii=False, indent=2), encoding='utf-8')
    license_text = request('https://raw.githubusercontent.com/skywind3000/ECDICT/master/LICENSE').read()
    (ASSETS / 'ECDICT-LICENSE.txt').write_bytes(license_text)
    print(f'Dictionary ready: {count} entries; {output.stat().st_size} bytes', flush=True)

if __name__ == '__main__':
    main()
