"""Offline, reproducible filtering of the explicitly approved open reference snapshots.

No definitions are rewritten, ranked as exam senses, or promoted to editorial notes.
The two licensed tables remain separate collections with their own attribution.
"""
from collections import Counter
from pathlib import Path
import hashlib
import json
import re
import shutil
import sqlite3
import unicodedata

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / '.downloads/open-dictionary'
ASSETS = ROOT / 'app/src/main/assets'
REPORT = ROOT / 'docs/reference-data-audit.json'
MAX_CACHE = 300_000_000
CHINESE = re.compile(r'[\u3400-\u9fff]')
HEADWORD = re.compile(r"[a-zA-Z][a-zA-Z0-9 '\-./&]*\Z")
POSITIONS = {
    'noun': '名词', 'verb': '动词', 'adj': '形容词', 'adv': '副词', 'name': '专有名词',
    'pron': '代词', 'prep': '介词', 'conj': '连词', 'det': '限定词', 'article': '冠词',
    'num': '数词', 'intj': '感叹词', 'abbrev': '缩略语', 'contraction': '缩约形式',
    'phrase': '短语', 'prep_phrase': '介词短语', 'proverb': '谚语', 'particle': '小品词',
}
LABELS = {'obsolete': '废旧用法', 'archaic': '古语', 'dated': '过时用法', 'rare': '罕见',
          'slang': '俚语', 'informal': '非正式', 'formal': '正式', 'vulgar': '粗俗',
          'offensive': '冒犯用语', 'derogatory': '贬义', 'figuratively': '比喻',
          'countable': '可数', 'uncountable': '不可数', 'transitive': '及物',
          'intransitive': '不及物', 'in-plural': '复数用法', 'usually-in-plural': '通常用复数'}


def normalize(word):
    return unicodedata.normalize('NFKC', word.strip()).lower()


def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def main():
    manifest = json.loads((SOURCE / 'manifest.json').read_text(encoding='utf-8'))
    for name, info in manifest.items():
        path = SOURCE / name
        if path.stat().st_size != info['bytes'] or sha256(path) != info['sha256']:
            raise ValueError(f'Source integrity failure: {name}')
    output = ASSETS / 'references.db'
    temporary = ASSETS / 'references.building.db'
    if temporary.exists():
        temporary.unlink()
    db = sqlite3.connect(temporary)
    db.executescript('''
        PRAGMA journal_mode=OFF;
        PRAGMA user_version=1;
        CREATE TABLE definitions(word TEXT NOT NULL, headword TEXT NOT NULL, pos TEXT NOT NULL,
            gloss TEXT NOT NULL, labels TEXT NOT NULL, ordinal INTEGER NOT NULL,
            PRIMARY KEY(word,headword,pos,gloss,labels)) WITHOUT ROWID;
        CREATE TABLE netem_2024(word TEXT PRIMARY KEY, spellings TEXT NOT NULL) WITHOUT ROWID;
        CREATE TABLE metadata(key TEXT PRIMARY KEY, value TEXT NOT NULL) WITHOUT ROWID;
    ''')
    counts = Counter()
    ordinal = 0
    for filename in ['wiki-traditional.jsonl', 'wiki-simplified.jsonl']:
        with (SOURCE / filename).open(encoding='utf-8') as handle:
            for line in handle:
                entry = json.loads(line)
                counts['input_entries'] += 1
                pos, headword = entry.get('pos'), entry.get('word', '').strip()
                if entry.get('lang_code') != 'en' or pos not in POSITIONS:
                    counts['excluded_language_or_unknown_pos_entries'] += 1
                    continue
                if len(headword) > 100 or not HEADWORD.fullmatch(headword):
                    counts['excluded_non_searchable_headword_entries'] += 1
                    continue
                # Some extracted single-word pages lose the heading of a nested phrase.
                if pos in ('phrase', 'prep_phrase', 'proverb') and ' ' not in headword:
                    counts['excluded_ambiguous_phrase_entries'] += 1
                    continue
                for sense in entry.get('senses', []):
                    counts['considered_senses'] += 1
                    gloss = '；'.join(g.strip() for g in sense.get('glosses', []) if g.strip())
                    if not gloss or not CHINESE.search(gloss):
                        counts['excluded_empty_or_non_chinese_senses'] += 1
                        continue
                    if sense.get('form_of') or sense.get('alt_of'):
                        counts['excluded_form_or_variant_senses'] += 1
                        continue
                    if '\ufffd' in gloss or '{{' in gloss or '}}' in gloss:
                        counts['excluded_encoding_or_template_senses'] += 1
                        continue
                    labels = '、'.join(dict.fromkeys(
                        [LABELS.get(tag, tag) for tag in sense.get('tags', [])] + sense.get('raw_tags', [])))
                    ordinal += 1
                    db.execute('INSERT OR IGNORE INTO definitions VALUES (?,?,?,?,?,?)',
                               (normalize(headword), headword, POSITIONS[pos], gloss, labels, ordinal))
    rows = next(iter(json.loads((SOURCE / 'netem-2024.json').read_text(encoding='utf-8')).values()))
    for row in rows:
        # Deliberately exclude mixed-exam word counts and abbreviated glosses.
        db.execute('INSERT OR IGNORE INTO netem_2024 VALUES (?,?)',
                   (normalize(row['单词']), row['其他拼写'] or ''))
    db.commit()
    db.execute('ATTACH DATABASE ? AS base', (str(ASSETS / 'dictionary.db'),))
    scalar = lambda sql: db.execute(sql).fetchone()[0]
    counts.update({
        'accepted_senses': scalar('SELECT COUNT(*) FROM definitions'),
        'accepted_words': scalar('SELECT COUNT(DISTINCT word) FROM definitions'),
        'supplement_only_words': scalar('SELECT COUNT(DISTINCT word) FROM definitions WHERE word NOT IN (SELECT word FROM base.words)'),
        'netem_source_rows': len(rows),
        'netem_normalized_words': scalar('SELECT COUNT(*) FROM netem_2024'),
        'netem_covered_by_base': scalar('SELECT COUNT(*) FROM netem_2024 WHERE word IN (SELECT word FROM base.words)'),
        'netem_with_supplement': scalar('SELECT COUNT(*) FROM netem_2024 WHERE word IN (SELECT word FROM definitions)'),
        'base_words': scalar('SELECT COUNT(*) FROM base.words'),
    })
    counts['searchable_words'] = counts['base_words'] + counts['supplement_only_words']
    db.execute('DETACH DATABASE base')
    info = {
        'schema': 1,
        'sources': manifest,
        'wiktionary_attribution': 'Chinese Wiktionary contributors; extracted by Kaikki.org / Wiktextract',
        'wiktionary_license': 'CC BY-SA 4.0',
        'netem_attribution': 'exam-data/NETEMVocabulary contributors',
        'netem_license': 'CC BY-NC-SA 4.0',
        'transformations': 'Filtered, normalized search keys, deduplicated; original Chinese glosses retained; labels translated where mapped.',
        'counts': dict(counts),
        'netem_duplicate_keys': ['may', 'march'],
        'exam_target': '2027 English I',
        'target_syllabus_verified': False,
        'exam_sense_frequencies': False,
        'professional_lexical_review': False,
    }
    db.executemany('INSERT INTO metadata VALUES (?,?)', [(k, str(v)) for k, v in counts.items()])
    db.commit()
    assert scalar('PRAGMA integrity_check') == 'ok'
    db.close()
    # Includes the old generation during a rebuild, far below the approved cache ceiling.
    if temporary.stat().st_size + (output.stat().st_size if output.exists() else 0) > MAX_CACHE:
        raise RuntimeError('300 MB processing-cache ceiling exceeded')
    temporary.replace(output)
    info['database_bytes'] = output.stat().st_size
    info['database_sha256'] = sha256(output)
    encoded = json.dumps(info, ensure_ascii=False, indent=2) + '\n'
    (ASSETS / 'references-source.json').write_text(encoded, encoding='utf-8')
    REPORT.write_text(encoded, encoding='utf-8')
    for source, target in [('CC-BY-SA-4.0.txt', 'WIKTIONARY-LICENSE.txt'), ('NETEM-LICENSE.txt', 'NETEM-LICENSE.txt')]:
        shutil.copyfile(SOURCE / source, ASSETS / target)
    print(json.dumps({'counts': dict(counts), 'database_bytes': info['database_bytes']}, ensure_ascii=True))


if __name__ == '__main__':
    main()
