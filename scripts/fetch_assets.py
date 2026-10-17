#!/usr/bin/env python3
"""Fetch pinned Wesnoth art and retain its per-file upstream attribution."""
import csv
import hashlib
import io
import json
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parents[1]
COMMIT = '1a2f96ab86808b94a34337502cfae86d94eb43d1'  # Wesnoth 1.18.6
BASE = f'https://raw.githubusercontent.com/wesnoth/wesnoth/{COMMIT}/'
FILES = {
    'elf_fighter': 'units/elves-wood/fighter/fighter.png',
    'elf_archer': 'units/elves-wood/archer.png',
    'elf_shaman': 'units/elves-wood/shaman.png',
    'elf_captain': 'units/elves-wood/captain.png',
    'elf_hero': 'units/elves-wood/hero.png',
    'elf_ranger': 'units/elves-wood/ranger.png',
    'elf_druid': 'units/elves-wood/druid.png',
    'orc_grunt': 'units/orcs/grunt.png',
    'orc_archer': 'units/orcs/archer.png',
    'orc_warrior': 'units/orcs/warrior.png',
    'orc_crossbow': 'units/orcs/xbowman.png',
    'grass': 'terrain/grass/green.png',
    'grass2': 'terrain/grass/green2.png',
    'grass3': 'terrain/grass/green3.png',
    'forest': 'terrain/forest/deciduous-summer-small.png',
    'pine': 'terrain/forest/pine-small.png',
    'hills': 'terrain/hills/regular.png',
    'village': 'terrain/village/human-tile.png',
    'village_elf': 'terrain/village/elven-tile.png',
    'castle': 'terrain/castle/castle-tile.png',
    'keep': 'terrain/castle/keep-tile.png',
    'water': 'terrain/water/coast-tile.png',
}
for n in range(1, 5):
    FILES[f'elf_fighter_attack{n}'] = f'units/elves-wood/fighter/fighter-melee-{n}.png'
    FILES[f'elf_archer_attack{n}'] = f'units/elves-wood/archer-bow-attack{n}.png'
for n in range(1, 11):
    FILES[f'elf_fighter_run{n}'] = f'units/elves-wood/fighter/fighter-se-run{n}.png'
for n in range(1, 3):
    FILES[f'orc_grunt_attack{n}'] = f'units/orcs/grunt-attack-{n}.png'
    FILES[f'orc_warrior_attack{n}'] = f'units/orcs/warrior-attack-{n}.png'


def main():
    copyright_text = urlopen(BASE + 'copyrights.csv', timeout=45).read().decode()
    rows = {r['File']: r for r in csv.DictReader(io.StringIO(copyright_text))}
    destination = ROOT / 'app/src/main/assets/art'
    destination.mkdir(parents=True, exist_ok=True)

    def fetch(item):
        name, relative = item
        upstream = 'data/core/images/' + relative
        attribution = rows.get(upstream)
        if attribution is None:
            raise ValueError(f'Missing upstream attribution for {upstream}')
        local = destination / (name + '.png')
        content = local.read_bytes() if local.exists() else urlopen(BASE + upstream, timeout=45).read()
        if not content.startswith(b'\x89PNG\r\n\x1a\n'):
            raise ValueError(f'Not a PNG: {upstream}')
        if attribution.get('MD5') and hashlib.md5(content).hexdigest() != attribution['MD5']:
            raise ValueError(f'Upstream copyright checksum differs: {upstream}')
        (destination / (name + '.png')).write_bytes(content)
        return dict(file=f'art/{name}.png', upstream_path=upstream,
                    source=f'https://github.com/wesnoth/wesnoth/blob/{COMMIT}/{upstream}',
                    license=attribution['License'],
                    author=attribution['Author - Real Name(other name);Real Name(other name);etc'],
                    date=attribution['Date'], notes=attribution['Notes'],
                    sha256=hashlib.sha256(content).hexdigest(), modified=False)

    with ThreadPoolExecutor(max_workers=6) as pool:
        manifest = list(pool.map(fetch, FILES.items()))
    document = dict(project='The Battle for Wesnoth', version='1.18.6', commit=COMMIT,
                    copyright_source=BASE + 'copyrights.csv', assets=manifest)
    (destination.parent / 'asset-provenance.json').write_text(json.dumps(document, indent=2) + '\n')
    print(f'Fetched and verified {len(manifest)} original art files')


if __name__ == '__main__':
    main()
