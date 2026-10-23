#!/usr/bin/env python3
"""Check maintained references against a current Wiki export, without reading history."""

import argparse
import html
import re
import subprocess
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[2]
EXCLUDED = ('LEGACY/', 'references/', 'compact_enc_det/')
RETIRED = re.compile(r'\b(?:MDAU|MCAT|noAccessTtlRestarts|processCapacityOnePipeline|AndroidIndexerPipeline)\b')
WIKI_FAMILIES = ('Riff', 'Cuetree', 'Cuegrain')
LINK = re.compile(r'\[[^\]\n]*\]\(([^)\n]+)\)')
URL = re.compile(r'https?://(?:wiki\.271\.pics|freki:8080)/[^\s<>)*]+')


def anchors(text):
    values = set(re.findall(r'(?:id|name)=["\']([^"\']+)', text))
    counts = {}
    for title in re.findall(r'^#{1,6}\s+(.+?)\s*#*$', text, re.M):
        title = html.unescape(re.sub(r'<[^>]*>|[`*_]', '', title)).lower()
        slug = re.sub(r'[^\w\-\s]', '', title).strip()
        slug = re.sub(r'\s+', '-', slug)
        count = counts.get(slug, 0)
        counts[slug] = count + 1
        values.add(slug if not count else f'{slug}-{count}')
    return values


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--wiki-tree', type=Path, required=True)
    args = parser.parse_args()
    wiki = args.wiki_tree.resolve()
    pages = {str(p.relative_to(wiki))[:-3]: p for p in wiki.rglob('*.md')}
    listed = subprocess.check_output(
        ['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], cwd=ROOT,
    ).decode().split('\0')
    for repository in (ROOT / 'standalone').glob('*'):
        if (repository / '.git').exists():
            names = subprocess.check_output(
                ['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], cwd=repository,
            ).decode().split('\0')
            listed.extend(str(repository.relative_to(ROOT) / name) for name in names if name)
    files = [ROOT / name for name in set(listed) if name and not name.startswith(EXCLUDED)
             and '/generated/' not in name and (ROOT / name).is_file()
             and Path(name).suffix in ('.md', '.kt', '.fbs')]
    errors = []
    for path in sorted(set(files + list(pages.values()))):
        text = path.read_text()
        in_wiki = path.is_relative_to(wiki)
        for number, line in enumerate(text.splitlines(), 1):
            if RETIRED.search(line):
                errors.append(f'{path}:{number}: retired contract name: {line.strip()}')
        urls = set(URL.findall(text))
        if path.suffix == '.md':
            urls.update(LINK.findall(text))
        for value in urls:
            value = value.strip('<>').split(' "', 1)[0]
            parsed = urlsplit(value)
            if parsed.scheme and parsed.hostname not in ('wiki.271.pics', 'freki'):
                continue
            route = unquote(parsed.path).removeprefix('/en/').lstrip('/')
            fragment = unquote(parsed.fragment)
            target = None
            # Relative paths in repository docs are local files, even when their first
            # directory shares a Wiki family name. Wiki routes are absolute or URL-based.
            is_wiki_route = (
                (bool(parsed.scheme) or value.startswith('/'))
                and route.split('/')[0] in WIKI_FAMILIES
            )
            if is_wiki_route:
                if route.endswith('/LEGACY') and path.name in ('Riff.md', 'AGENTS.md'):
                    continue  # The overview owns the single entry to the archive index.
                target = pages.get(route)
                if target is None:
                    errors.append(f'{path}: missing current Wiki page: {value}')
                    continue
            elif not parsed.scheme:
                if not parsed.path:
                    target = path
                elif in_wiki and parsed.path in ('/iconset_daynight.tar.xz', '/iconset_night.png', '/iconset_day.png'):
                    continue
                elif in_wiki:
                    errors.append(f'{path}: ambiguous relative Wiki link: {value}')
                    continue
                else:
                    target = path.parent / unquote(parsed.path)
                    if not target.exists():
                        errors.append(f'{path}: missing local target: {value}')
                        continue
            if target and fragment and target.suffix == '.md':
                if fragment not in anchors(target.read_text()):
                    errors.append(f'{path}: missing section: {value}')
    print(f'Checked {len(files)} maintained files and {len(pages)} current Wiki pages')
    if errors:
        print('\n'.join(errors))
        raise SystemExit(1)
    print('Current references: OK')


if __name__ == '__main__':
    main()
