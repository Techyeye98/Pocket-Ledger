"""Check metadata, structured-data relationships and handbook rebuild parity."""
from pathlib import Path
import html
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/guide'))
from site_metadata import BASE, PAGES, metadata_head

titles = set()
for filename in PAGES:
    doc = (ROOT / 'distribution' / filename).read_text(encoding='utf-8')
    head = re.search(r'<head>(.*?)</head>', doc, re.S)[1]
    title = html.unescape(re.search(r'<title>(.*?)</title>', head)[1])
    assert title not in titles, filename + ': duplicate title'
    titles.add(title)
    url = BASE + ('' if filename == 'index.html' else filename)
    assert f'rel="canonical" href="{url}"' in head, filename
    blocks = re.findall(r'<script type="application/ld\+json">(.*?)</script>', head, re.S)
    assert len(blocks) == 1, filename
    graph = json.loads(blocks[0])['@graph']
    nodes = {node['@id']: node for node in graph}
    assert len(nodes) == len(graph), filename + ': duplicate entity ID'
    webpage = nodes[url + '#webpage']
    assert webpage['about']['@id'] in nodes and webpage['isPartOf']['@id'] in nodes
    app = nodes[BASE + '#android-app']
    assert app['operatingSystem'] == 'Android 8.0+'
    assert 'aggregateRating' not in app and 'offers' not in app
    if filename != 'index.html':
        crumbs = nodes[webpage['breadcrumb']['@id']]['itemListElement']
        assert crumbs[-1]['item'] == url
        assert len(crumbs) == (3 if filename.startswith('guide-') else 2)
    if filename.startswith('guide'):
        assert metadata_head(head, filename, graph[-1]['itemListElement'][-1]['name']) == head, filename + ': rebuild would change metadata'
    levels = [int(level) for level in re.findall(r'<h([1-6])\b', doc)]
    assert levels.count(1) == 1
    assert all(right <= left + 1 for left, right in zip(levels, levels[1:])), filename + ': skipped heading level'

assert (ROOT / 'distribution/pocket-ledger-logo.webp').stat().st_size < 20000
assert (ROOT / 'distribution/pocket-ledger-logo.png').stat().st_size < 50000
print('PASS: 14 unique metadata sets, canonical/entity/breadcrumb relationships, truthful app schema, guide rebuild parity, heading order and optimized logos.')
