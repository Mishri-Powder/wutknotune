#!/usr/bin/env python3
"""Authorized, read-only private corpus check; detailed output stays in evidence/."""
import argparse
import collections
import hashlib
import json
from pathlib import Path
import subprocess
import time

INVENTORY = """
import os,json,sys
from pathlib import Path
paths=[]; errors=[]
for root in map(Path, sys.argv[1:]):
    if root.is_dir():
        for d,dirs,files in os.walk(root,onerror=lambda e: errors.append(str(e))):
            for f in files:
                p=Path(d)/f
                paths.append([str(root),str(p),p.suffix.lower()])
print(json.dumps({'files':paths,'errors':errors}))
"""
ORACLE = """
import json,sys
sys.path.insert(0,'/proc/self/cwd')
import mutagen
from mutagen.id3 import ID3
from mutagen.mp4 import MP4
out={}
for path in sys.argv[1:]:
    try:
        f=mutagen.File(path)
        tags=f.tags if f is not None else None
        fields={}
        if isinstance(tags,ID3):
            for key,frame in [('title','TIT2'),('artist','TPE1'),('album','TALB')]:
                fields[key]=[str(v) for item in tags.getall(frame) for v in item.text]
        elif isinstance(f,MP4):
            fields={key:list(tags.get(atom,[])) for key,atom in [('title','©nam'),('artist','©ART'),('album','©alb')]}
            for k, values in tags.items():
                field = k.rsplit(':',1)[-1].lower()
                if k.startswith('----:') and field in fields:
                    fields[field].extend(v.decode('utf-8') for v in values if v.dataformat == 1)
        elif tags is not None:
            fields={key:list(tags.get(key,[])) for key in ['title','artist','album']}
        out[path]={'fields':fields}
    except Exception as e:
        out[path]={'error':str(e)}
print(json.dumps(out))
"""


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('roots', nargs='*', default=[])
    parser.add_argument('--label', default='music')
    parser.add_argument('--per-format', type=int, default=150)
    parser.add_argument('--cli-dir', type=Path, default=Path('cli/build/install/cuegrain'))
    parser.add_argument('--inventory', type=Path)
    args = parser.parse_args()
    roots = args.roots or [str(p) for p in Path('/misc/ai').glob('Music*') if p.name == 'Music' or p.name.startswith('Music.')]
    root = Path('evidence')
    root.mkdir(exist_ok=True)
    inventory = json.loads(args.inventory.read_text()) if args.inventory else json.loads(subprocess.check_output(['sudo', '-n', '-u', 'yegor', '/usr/bin/python3', '-c', INVENTORY, *roots], text=True))
    assert not inventory['errors'], inventory['errors']
    groups = collections.defaultdict(list)
    non_utf8_names = []
    counts = collections.defaultdict(collections.Counter)
    for group, path, ext in inventory['files']:
        counts[group][ext] += 1
        if ext in {'.mp3', '.m4a', '.m4b', '.flac', '.ogg', '.opus', '.cue'}:
            if any(0xD800 <= ord(c) <= 0xDFFF for c in path):
                non_utf8_names.append(path)
                continue
            groups[group, ext].append(path)
    selected = []
    for (group, ext), paths in sorted(groups.items()):
        limit = len(paths) if ext in {'.cue', '.m4b'} else args.per_format
        selected.extend(sorted(paths, key=lambda p: hashlib.sha256(p.encode()).digest())[:limit])
    cli_dir = args.cli_dir.resolve()
    oracle_dir = next(Path('/home/riff-adb/venv/lib').glob('python*/site-packages'))
    reports, differences, oracle_errors = [], [], []
    started = time.monotonic()
    for offset in range(0, len(selected), 32):
        batch = selected[offset:offset+32]
        result = subprocess.run(['sudo', '-n', '-u', 'yegor', 'java', '-cp', '/proc/self/cwd/lib/*', 'link.roadio.cuegrain.cli.MainKt', *batch], cwd=cli_dir, capture_output=True, text=True, timeout=120)
        assert result.returncode in {0,1} and not result.stderr, (result.returncode, result.stderr)
        parsed = json.loads(result.stdout)['results']
        reports.extend(parsed)
        audio = [p for p in batch if not p.lower().endswith('.cue')]
        expected = json.loads(subprocess.check_output(['sudo','-n','-u','yegor','/usr/bin/python3','-c',ORACLE,*audio], cwd=oracle_dir, text=True, timeout=120))
        for report in parsed:
            oracle = expected.get(report['input'], {})
            if 'error' in oracle:
                oracle_errors.append({'input':report['input'], 'error':oracle['error']})
            for key, values in oracle.get('fields', {}).items():
                kinds = {'id3','mp4','vorbis'} if any(s['kind']=='id3' for s in report['sources']) else {'id3v1','mp4','vorbis'}
                actual = [v for s in report['sources'] if s['kind'] in kinds for v in s['metadata'].get(key,[])]
                if values and actual != values:
                    differences.append({'input':report['input'],'field':key,'expected':values,'actual':actual})
        print(json.dumps({'processed':len(reports),'total':len(selected)}), flush=True)
    summary = {
        'availableByRoot':counts, 'selected':len(selected), 'nonUtf8FilenamesExcluded':len(non_utf8_names),
        'selectedByExtension':dict(collections.Counter(Path(p).suffix.lower() for p in selected)),
        'status':dict(collections.Counter(r['status'] for r in reports)),
        'sourceKinds':dict(collections.Counter(s['kind'] for r in reports for s in r['sources'])),
        'gainKinds':dict(collections.Counter(g['kind'] for r in reports for s in r['sources'] for g in s['gains'])),
        'diagnostics':dict(collections.Counter(d['code'] for r in reports for d in r['diagnostics'])),
        'oracleDifferences':differences, 'oracleErrors':oracle_errors,
        'failures':[{'input':r['input'],'status':r['status'],'diagnostics':r['diagnostics']} for r in reports if r['status']!='ok'],
        'seconds':round(time.monotonic()-started,2),
    }
    (root/f'{args.label}-inventory-full.json').write_text(json.dumps(inventory,ensure_ascii=True,indent=2)+'\n')
    (root/f'{args.label}-results.json').write_text(json.dumps(reports,ensure_ascii=False,indent=2)+'\n')
    (root/f'{args.label}-summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({k:v for k,v in summary.items() if k not in {'failures','oracleDifferences','oracleErrors'}}, indent=2))
    print(json.dumps({'oracleDifferences':len(differences),'oracleErrors':len(oracle_errors)}))


if __name__ == '__main__':
    main()
