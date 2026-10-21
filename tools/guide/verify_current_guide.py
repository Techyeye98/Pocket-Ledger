"""Verify the built handbook, link destinations, screenshot provenance and privacy scope."""
from pathlib import Path
import hashlib
import json
from PIL import Image
from pypdf import PdfReader
from current_guide_content import PDF_NAME, VERSION, CHAPTERS, LESSONS

root=Path(__file__).resolve().parents[2]
site=root/'distribution'
pdf=root/'output/pdf'/PDF_NAME
reader=PdfReader(str(pdf))
assert len(reader.pages)==14
text='\n'.join(p.extract_text() for p in reader.pages)
for value in [VERSION,'Lena / Dena','portions','restaurant','repayment','setupCleanTransactions','JSON','receipt']:
    assert value.lower() in text.lower(),value
for private in ['14919.61','20776.61','5857.00','Fampay','ICICI','Namya','AKfycb']:
    assert private not in text,private
assert '\ufffd' not in text
assert len(reader.outline)==10
for dest in reader.outline:
    assert 0<=reader.get_destination_page_number(dest)<14
links=[a.get_object() for p in reader.pages for a in p.get('/Annots',[])]
assert sum(a.get('/Subtype')=='/Link' for a in links)>=25
assert pdf.read_bytes()==(site/'guides'/PDF_NAME).read_bytes()
manifest=json.loads((site/'guides/current/asset-manifest.json').read_text())
expected={l['image']+'.png' for l in LESSONS if l['image']}
assert {m['file'] for m in manifest}==expected
assert {p.name for p in (site/'guides/current/screens').glob('*.png')}==expected
for m in manifest:
    image=site/'guides/current/screens'/m['file']
    original=root/'V6/guide-captures/2026-10-20/raw'/m['file']
    assert hashlib.sha256(image.read_bytes()).hexdigest()==m['sha256']
    assert image.read_bytes()==original.read_bytes()
    with Image.open(image) as im:assert im.size==(1220,2712)
for key,title,description in CHAPTERS:
    assert (site/('guide-'+key+'.html')).exists()
layout=json.loads((root/'tmp/pdfs/current-guide/layout.json').read_text())
assert all(row['bottom']<841.89-12 for row in layout)
print('PASS: 14 pages; 10 valid bookmarks; working link annotations; complete topics; no private text; 12 exact original screenshot assets; staged PDF parity.')
