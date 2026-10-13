#!/usr/bin/env python3
"""words_source.tsv + CMU Pronouncing Dictionary  ->  app/src/main/assets/words.json

IPA, stress, syllables, homophones and common sound-confusions are all derived
from CMUdict (Carnegie Mellon University, American English, free for any use).
Turkish meanings and example sentences come from words_source.tsv.

usage: build_words.py OUT_JSON REPORT_TXT [--cmu-file PATH]
"""
import collections
import json
import os
import re
import sys
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, 'words_source.tsv')

CMU_URLS = [
    'https://raw.githubusercontent.com/cmusphinx/cmudict/master/cmudict.dict',
    'https://github.com/cmusphinx/cmudict/raw/master/cmudict.dict',
]


# ------------------------------------------------------------------ CMUdict
def parse_cmu_text(text):
    d = collections.OrderedDict()
    for line in text.splitlines():
        line = line.split('#')[0].strip()
        if not line or line.startswith(';;;'):
            continue
        m = re.match(r'^(\S+?)(?:\((\d+)\))?\s+(.+)$', line)
        if not m:
            continue
        w = m.group(1).lower()
        d.setdefault(w, []).append(m.group(3).upper().split())
    return d


def load_cmu(path=None):
    if path:
        print('CMUdict from file:', path)
        return parse_cmu_text(open(path, encoding='latin-1').read())
    try:
        import cmudict  # pip install cmudict
        d = cmudict.dict()
        print('CMUdict from the pip package (%d words)' % len(d))
        return {w: [[p.upper() for p in pr] for pr in prons] for w, prons in d.items()}
    except Exception as e:
        print('pip package not available (%s), downloading...' % e)
    last = None
    for url in CMU_URLS:
        try:
            with urllib.request.urlopen(url, timeout=60) as r:
                text = r.read().decode('latin-1')
            d = parse_cmu_text(text)
            if len(d) > 50000:
                print('CMUdict downloaded from %s (%d words)' % (url, len(d)))
                return d
        except Exception as e:
            last = e
    sys.exit('ERROR: CMUdict could not be loaded (%s). Nothing was generated.' % last)


# ------------------------------------------------------------- ARPAbet -> IPA
CONS = {'B': 'b', 'CH': 'tʃ', 'D': 'd', 'DH': 'ð', 'F': 'f', 'G': 'g', 'HH': 'h', 'JH': 'dʒ', 'K': 'k',
        'L': 'l', 'M': 'm', 'N': 'n', 'NG': 'ŋ', 'P': 'p', 'R': 'r', 'S': 's', 'SH': 'ʃ', 'T': 't',
        'TH': 'θ', 'V': 'v', 'W': 'w', 'Y': 'j', 'Z': 'z', 'ZH': 'ʒ'}
VOWELS = {'AA', 'AE', 'AH', 'AO', 'AW', 'AY', 'EH', 'ER', 'EY', 'IH', 'IY', 'OW', 'OY', 'UH', 'UW'}

ONSET2 = {('P', 'L'), ('B', 'L'), ('K', 'L'), ('G', 'L'), ('F', 'L'), ('S', 'L'), ('P', 'R'), ('B', 'R'),
          ('T', 'R'), ('D', 'R'), ('K', 'R'), ('G', 'R'), ('F', 'R'), ('TH', 'R'), ('SH', 'R'), ('T', 'W'),
          ('D', 'W'), ('K', 'W'), ('G', 'W'), ('S', 'W'), ('TH', 'W'), ('S', 'P'), ('S', 'T'), ('S', 'K'),
          ('S', 'M'), ('S', 'N'), ('P', 'Y'), ('B', 'Y'), ('T', 'Y'), ('D', 'Y'), ('K', 'Y'), ('G', 'Y'),
          ('M', 'Y'), ('N', 'Y'), ('F', 'Y'), ('V', 'Y'), ('HH', 'Y'), ('L', 'Y'), ('S', 'Y'), ('Z', 'Y')}
ONSET3 = {('S', 'P', 'L'), ('S', 'P', 'R'), ('S', 'T', 'R'), ('S', 'K', 'R'), ('S', 'K', 'W'),
          ('S', 'P', 'Y'), ('S', 'T', 'Y'), ('S', 'K', 'Y')}


def legal_onset(seq):
    if len(seq) == 1:
        return seq[0] != 'NG'
    if len(seq) == 2:
        return tuple(seq) in ONSET2
    if len(seq) == 3:
        return tuple(seq) in ONSET3
    return False


def parse_phones(ph):
    out = []
    for p in ph:
        m = re.match(r'^([A-Z]+)(\d)?$', p)
        if not m:
            return None
        out.append((m.group(1), int(m.group(2)) if m.group(2) is not None else None))
    return out


def vowel_ipa(base, stress):
    return {'AA': 'ɑː', 'AE': 'æ', 'AH': 'ə' if stress == 0 else 'ʌ', 'AO': 'ɔː', 'AW': 'aʊ', 'AY': 'aɪ',
            'EH': 'e', 'ER': 'ər' if stress == 0 else 'ɜːr', 'EY': 'eɪ', 'IH': 'ɪ',
            'IY': 'i' if stress == 0 else 'iː', 'OW': 'oʊ', 'OY': 'ɔɪ', 'UH': 'ʊ',
            'UW': 'u' if stress == 0 else 'uː'}[base]


def to_syllables(ph):
    """-> list of (ipa_text, stress) syllables, or None if the entry is not usable."""
    ps = parse_phones(ph)
    if not ps:
        return None
    nuclei = [i for i, (b, s) in enumerate(ps) if b in VOWELS]
    if not nuclei:
        return None
    # decide where each consonant cluster is split
    bounds = [0]  # start index of each syllable
    for a, b in zip(nuclei, nuclei[1:]):
        cluster = [ps[k][0] for k in range(a + 1, b)]
        cut = len(cluster)  # onset = cluster[cut:]
        for n in (3, 2, 1):
            if len(cluster) >= n and legal_onset(cluster[len(cluster) - n:]):
                cut = len(cluster) - n
                break
        bounds.append(a + 1 + cut)
    bounds.append(len(ps))
    syls = []
    for i in range(len(nuclei)):
        seg = ps[bounds[i]:bounds[i + 1]]
        text = ''
        stress = 0
        for b, s in seg:
            if b in VOWELS:
                text += vowel_ipa(b, s)
                stress = s or 0
            else:
                text += CONS[b]
        syls.append((text, stress))
    return syls


def ipa_of(ph, marks=True):
    syls = to_syllables(ph)
    if syls is None:
        return None
    parts = []
    for text, st in syls:
        mark = ''
        if marks and len(syls) > 1:
            mark = 'ˈ' if st == 1 else ('ˌ' if st == 2 else '')
        parts.append(mark + text)
    return '.'.join(parts)


def flat(ipa):
    return re.sub(r'[.ˈˌ/]', '', ipa)


def edit_distance(a, b):
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


# ----------------------------------------------------- Turkish approximation
TOK = ['tʃ', 'dʒ', 'eɪ', 'aɪ', 'aʊ', 'oʊ', 'ɔɪ', 'ɜːr', 'ɑːr', 'ɔːr', 'ɪr', 'ʊr', 'er', 'ər', 'iː', 'uː',
       'ɑː', 'ɔː', 'ɜː', 'ɪ', 'e', 'æ', 'ʊ', 'ʌ', 'ə', 'i', 'u', 'ŋ', 'θ', 'ð', 'ʃ', 'ʒ']
TRMAP = {'tʃ': 'ç', 'dʒ': 'c', 'eɪ': 'ey', 'aɪ': 'ay', 'aʊ': 'av', 'oʊ': 'o', 'ɔɪ': 'oy', 'ɜːr': 'ır',
         'ɑːr': 'ar', 'ɔːr': 'or', 'ɪr': 'ir', 'ʊr': 'ur', 'er': 'er', 'ər': 'ır', 'iː': 'i', 'uː': 'u',
         'ɑː': 'a', 'ɔː': 'o', 'ɜː': 'ı', 'ɪ': 'i', 'e': 'e', 'æ': 'e', 'ʊ': 'u', 'ʌ': 'a', 'ə': 'ı',
         'i': 'i', 'u': 'u', 'ŋ': 'ng', 'θ': 'th', 'ð': 'dh', 'ʃ': 'ş', 'ʒ': 'j', 'w': 'v', 'j': 'y'}


def syl_tr(s):
    out, i = '', 0
    while i < len(s):
        for t in TOK:
            if s.startswith(t, i):
                out += TRMAP[t]
                i += len(t)
                break
        else:
            out += TRMAP.get(s[i], s[i])
            i += 1
    return out


def tr_of(ipa):
    syls = ipa.split('.')
    parts = []
    for s in syls:
        stressed = s.startswith('ˈ')
        t = syl_tr(s.replace('ˈ', '').replace('ˌ', ''))
        parts.append(t.upper() if (stressed and len(syls) > 1) else t)
    return '-'.join(parts)


def focus_of(ph):
    ps = parse_phones(ph)
    bases = [b for b, s in ps]
    n = sum(1 for b in bases if b in VOWELS)
    if 'TH' in bases or 'DH' in bases:
        return 'th'
    if 'AE' in bases:
        return 'ae'
    if 'V' in bases:
        return 'v'
    if 'W' in bases:
        return 'w'
    if n >= 3 and any(b == 'AH' and s == 0 for b, s in ps):
        return 'schwa'
    if 'IH' in bases or 'IY' in bases:
        return 'ih'
    if 'UH' in bases or 'UW' in bases:
        return 'uu'
    if any(b == 'AH' and s for b, s in ps):
        return 'uh'
    if 'EH' in bases:
        return 'eh'
    if 'R' in bases:
        return 'r'
    if 'L' in bases:
        return 'l'
    return 'none'


# ------------------------------------------------- confusions (Turkish speakers)
CONF = {'TH': ['T', 'S', 'F'], 'DH': ['D', 'Z', 'V'], 'V': ['W', 'B', 'F'], 'W': ['V'], 'R': ['L'],
        'L': ['R'], 'AE': ['EH', 'AH'], 'EH': ['AE'], 'AH': ['AE'], 'IH': ['IY'], 'IY': ['IH'],
        'UH': ['UW'], 'UW': ['UH'], 'P': ['B'], 'B': ['P'], 'K': ['G'], 'G': ['K'], 'CH': ['SH'],
        'SH': ['CH', 'S'], 'S': ['Z'], 'Z': ['S'], 'F': ['V']}
LABEL = {'TH': 'TH (θ)', 'DH': 'TH (ð)', 'V': 'V', 'W': 'W', 'R': 'R', 'L': 'L', 'AE': 'Æ', 'EH': 'E',
         'AH': '/ʌ/', 'IH': 'kısa /ɪ/', 'IY': 'uzun /iː/', 'UH': 'kısa /ʊ/', 'UW': 'uzun /uː/', 'P': 'P',
         'B': 'B', 'K': 'K', 'G': 'G', 'CH': 'CH', 'SH': 'SH', 'S': 'S', 'Z': 'Z', 'F': 'F', 'T': 'T', 'D': 'D'}
MAX_ERRORS = 10


def main():
    args = [a for a in sys.argv[1:]]
    cmu_file = None
    if '--cmu-file' in args:
        i = args.index('--cmu-file')
        cmu_file = args[i + 1]
        del args[i:i + 2]
    min_words = 900
    if '--min' in args:
        i = args.index('--min')
        min_words = int(args[i + 1])
        del args[i:i + 2]
    out_json = args[0]
    report = args[1] if len(args) > 1 else 'words_report.txt'

    cmu = load_cmu(cmu_file)

    rows = []
    for line in open(SRC, encoding='utf-8'):
        if not line.strip() or line.startswith('#'):
            continue
        c = line.rstrip('\n').split('\t')
        c += [''] * (7 - len(c))
        rows.append(dict(word=c[0].strip().lower(), level=c[1], mean=c[2], ex=c[3],
                         hand=c[4], tr=c[5], syl=c[6]))
    in_list = {r['word'] for r in rows}

    # indexes over the whole dictionary
    by_flat = collections.defaultdict(set)   # exact IPA (no marks) -> words
    by_base = collections.defaultdict(set)   # phoneme bases only -> words
    for w, prons in cmu.items():
        if not re.fullmatch(r'[a-z]+', w):
            continue
        for ph in prons:
            ps = parse_phones(ph)
            ip = ipa_of(ph, marks=False)
            if ps is None or ip is None:
                continue
            by_flat[flat(ip)].add(w)
            by_base[tuple(b for b, s in ps)].add(w)

    result, lines, missing, flagged = [], [], [], []
    for r in rows:
        w = r['word']
        prons = cmu.get(w)
        if not prons:
            missing.append(w)
            continue
        cands = []
        for ph in prons:
            ip = ipa_of(ph)
            if ip:
                cands.append((ph, ip))
        if not cands:
            missing.append(w)
            continue
        hand = flat(r['hand'])
        best = min(cands, key=lambda x: edit_distance(flat(x[1]), hand))  # first wins ties
        ph, ip = best
        dist = edit_distance(flat(ip), hand) if hand else 0
        if dist > 2:
            flagged.append('%s: dictionary /%s/ vs hand-typed /%s/ (distance %d)' % (w, ip, r['hand'], dist))

        key = flat(ip)
        same = sorted(x for x in by_flat.get(key, ()) if x != w and x in in_list)[:6]

        errors = {}
        ps = parse_phones(ph)
        bases = tuple(b for b, s in ps)
        found = []
        for i, (b, s) in enumerate(ps):
            if b not in CONF:
                continue
            if b in VOWELS and not s:   # only stressed vowels
                continue
            for sub in CONF[b]:
                cand = bases[:i] + (sub,) + bases[i + 1:]
                for hw in by_base.get(cand, ()):
                    if hw == w or hw in same or not re.fullmatch(r'[a-z]+', hw):
                        continue
                    found.append((hw, b, sub))
        found.sort(key=lambda t: (t[0] not in in_list, len(t[0]), t[0]))
        for hw, b, sub in found:
            if hw in errors:
                continue
            errors[hw] = '%s sesini %s gibi söyledin (%s / %s).' % (LABEL[b], LABEL[sub], w, hw)
            if len(errors) >= MAX_ERRORS:
                break

        result.append({
            'word': w, 'ipa': '/%s/' % ip, 'syl': r['syl'], 'tr': r['tr'] or tr_of(ip),
            'mean': r['mean'], 'ex': r['ex'], 'level': r['level'], 'focus': focus_of(ph),
            'same': same, 'errors': errors})
        lines.append('%s\t/%s/\t(hand: /%s/)' % (w, ip, r['hand']))

    os.makedirs(os.path.dirname(os.path.abspath(out_json)), exist_ok=True)
    with open(out_json, 'w', encoding='utf-8') as f:
        json.dump(result, f, ensure_ascii=False)

    levels = collections.Counter(x['level'] for x in result)
    with open(report, 'w', encoding='utf-8') as f:
        f.write('Words generated: %d %s\n' % (len(result), dict(levels)))
        f.write('Not found in CMUdict (left out): %d\n' % len(missing))
        for w in missing:
            f.write('  %s\n' % w)
        f.write('\nLarge differences between dictionary IPA and hand-typed IPA: %d\n' % len(flagged))
        f.write('(the dictionary version is the one used)\n')
        for x in flagged:
            f.write('  %s\n' % x)
        f.write('\nAll words:\n')
        for x in lines:
            f.write(x + '\n')
    print('words.json: %d words %s' % (len(result), dict(levels)))
    print('left out (not in CMUdict): %d' % len(missing), missing[:20])
    print('large IPA differences: %d' % len(flagged))
    if len(result) < min_words:
        sys.exit('ERROR: only %d words generated, something is wrong.' % len(result))


if __name__ == '__main__':
    main()
