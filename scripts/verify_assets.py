#!/usr/bin/env python3
"""Fail the build if any bundled PNG is missing, uncredited or changed."""
import hashlib
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1] / 'app/src/main/assets'
document = json.loads((root / 'asset-provenance.json').read_text())
assert document['commit'] == '1a2f96ab86808b94a34337502cfae86d94eb43d1'
expected = set()
for asset in document['assets']:
    path = root / asset['file']
    assert path.is_file(), f'Missing {path}'
    assert hashlib.sha256(path.read_bytes()).hexdigest() == asset['sha256'], f'Changed art: {path}'
    assert asset['license'] in ('GNU GPL v2+', 'CC BY-SA 4.0'), f'Unknown art license: {path}'
    assert asset['author'] and asset['source'] and asset['modified'] is False
    expected.add(path.name)
assert expected == {p.name for p in (root / 'art').glob('*.png')}, 'Uncredited or missing art'
assert (root / 'LICENSE.txt').is_file() and (root / 'CC-BY-SA-4.0.txt').is_file()
print(f'Verified hashes, source and license for {len(expected)} original sprites/textures')
