"""Build the illustrated web handbook and linked PDF from one verified content source.

Screenshots remain original bytes. Framing, focus windows, highlights and numbered
callouts are native HTML/SVG/PDF elements, so app text and amounts stay exact.
Only explicitly reviewed demo-only screenshots are copied to the public package.
"""
from pathlib import Path
import base64
import hashlib
import html
import json
import re
import shutil
import sys

from PIL import Image
from reportlab.pdfgen import canvas
from reportlab.lib.colors import HexColor
from reportlab.lib.styles import ParagraphStyle
from reportlab.platypus import Paragraph
from reportlab.lib.utils import ImageReader
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from pypdf import PdfReader

from current_guide_content import LESSONS, CHAPTERS, FAQS, VERSION, PDF_NAME, ANDROID_SHEET, IOS_SHEET

ROOT = Path(__file__).resolve().parents[2]
SITE = ROOT / 'distribution'
RAW = ROOT / 'V6/guide-captures/2026-10-20/raw'
ASSETS = SITE / 'guides/current/screens'
TMP = ROOT / 'tmp/pdfs/current-guide'
OUT = ROOT / 'output/pdf' / PDF_NAME
for path in [ASSETS, TMP, OUT.parent]:
    path.mkdir(parents=True, exist_ok=True)

# Private Home totals, personal Accounts list, Sync/device name and launcher are
# intentionally excluded. Every listed asset contains only fixture data or generic UI.
APPROVED = {x['image'] for x in LESSONS if x['image']}
assert not APPROVED & {'01-home-overview-dark', '07-accounts-list', '38-sync-settings-private-reference', '42-widget-add-preview', '48-restored-original-home'}
asset_manifest = []
for name in sorted(APPROVED):
    source = RAW / f'{name}.png'
    with Image.open(source) as im:
        assert im.size == (1220, 2712)
        im.verify()
    target = ASSETS / source.name
    shutil.copyfile(source, target)
    digest = hashlib.sha256(source.read_bytes()).hexdigest()
    assert hashlib.sha256(target.read_bytes()).hexdigest() == digest
    asset_manifest.append({'file': source.name, 'sha256': digest, 'pixelEdits': False,
                           'presentation': 'native frame, focus window, highlights and numbered callouts'})
(SITE / 'guides/current/asset-manifest.json').write_text(json.dumps(asset_manifest, indent=2), encoding='utf-8')

def esc(value):
    return html.escape(str(value), quote=True)

shell = (SITE / 'guide.html').read_text(encoding='utf-8')
head = re.search(r'<head>(.*?)</head>', shell, re.S).group(1)
header = re.search(r'<header.*?</header>', shell, re.S).group()
footer = re.search(r'<footer.*?</footer>', shell, re.S).group()
# Rebuilding is idempotent; remove only our own added stylesheet first.
head = head.replace('<link rel="stylesheet" href="guide.css">', '')

def page(filename, title, description, body):
    h = re.sub(r'<title>.*?</title>', f'<title>Pocket Ledger | {esc(title)}</title>', head)
    h = re.sub(r'(<meta name="description" content=")[^"]*', lambda m: m[1]+esc(description), h)
    url = 'https://Techyeye98.github.io/Pocket-Ledger/' + filename
    h = re.sub(r'(<link rel="canonical" href=")[^"]*', lambda m: m[1]+url, h)
    for prop, value in [('og:title', 'Pocket Ledger | '+title), ('og:description', description), ('og:url', url)]:
        h = re.sub(r'(<meta property="'+prop+r'" content=")[^"]*', lambda m: m[1]+esc(value), h)
    h += '<link rel="stylesheet" href="guide.css">'
    he = header if filename == 'guide.html' else header.replace(' aria-current="page"', '')
    doc = '<!doctype html>\n<html lang="en" data-theme="dark"><head>'+h+'</head><body><a class="skip-link" href="#main">Skip to content</a>'+he+'<main id="main" tabindex="-1" class="handbook">'+body+'</main>'+footer+'</body></html>\n'
    (SITE / filename).write_text(doc, encoding='utf-8')

FOCUS = {
 'accounts':['Account name','Account type','Starting balance','Create account'],
 'expense':['Amount','Need / Want'], 'money-moves':['Transfer'],
 'insights':['Need / Want summary'], 'shared-money':['Receive / pay totals','Create a money-owed entry'],
 'repayments':['Open items'], 'split-items':['Name and full line total','People and portions'],
 'split-tax':['Tax rate','Whole final bill'], 'split-pay':['Individual shares'],
 'split-settle':['Updated outstanding shares'], 'receipts':['Unticked false item','Confirmed tax','Whole final amount'],
 'backups':['Export and import'],
}

def figure(lesson):
    if not lesson['image']:
        return '''<figure class="manual-diagram"><div><span>1</span><strong>Your Android app</strong><p>Primary financial ledger</p></div><b aria-hidden="true">↕</b><div><span>2</span><strong>Your private Web app URL</strong><p>The connection you deploy</p></div><b aria-hidden="true">↕</b><div><span>3</span><strong>Your own Google Sheet</strong><p>Transactions + Dashboard + hidden Sync Support</p></div><figcaption>Optional sync. Split bills stay on the phone.</figcaption></figure>'''
    x, y, w, h = lesson['area']
    image = 'guides/current/screens/'+lesson['image']+'.png'
    marks = []
    for i, (mx, my, mw, mh) in enumerate(lesson.get('marks', []), 1):
        # Focus rectangles are presentation annotations, never replacement app UI.
        mx, my = mx-x, my-y
        marks.append(f'<rect x="{mx}" y="{my}" width="{mw}" height="{mh}" rx="12"/><circle cx="-8" cy="{my+30}" r="36"/><text x="-8" y="{my+45}">{chr(64+i)}</text>')
    legend=' · '.join(chr(65+i)+': '+label for i,label in enumerate(FOCUS.get(lesson['key'],[])))
    return f'''<figure class="manual-figure"><div class="manual-canvas" style="aspect-ratio:{w}/{h}"><div class="manual-screen"><img src="{image}" width="1220" height="2712" loading="lazy" alt="{esc(lesson['caption'])}" style="width:{1220/w*100:.5f}%;left:{-x/w*100:.5f}%;top:{-y/h*100:.5f}%"></div><svg viewBox="0 0 {w} {h}" aria-hidden="true">{''.join(marks)}</svg></div><figcaption><span class="manual-proof">Real app capture · example data</span>{esc(lesson['caption'])}<span class="manual-focus-key">{esc(legend)}</span><a href="{image}" target="_blank" rel="noopener">Enlarge original</a></figcaption></figure>'''

def lesson_html(l):
    steps = ''.join(f'<li><h3>{esc(title)}</h3><p>{esc(body)}</p></li>' for title, body in l['steps'])
    return f'''<section class="manual-lesson" id="{l['key']}"><div class="manual-instructions"><p class="manual-path">{esc(l['path'])}</p><h2>{esc(l['title'])}</h2><p class="manual-lead">{esc(l['intro'])}</p><ol class="manual-steps">{steps}</ol><aside class="manual-example"><h3>{esc(l['example'][0])}</h3><p>{esc(l['example'][1])}</p></aside><p class="manual-remember">{esc(l['remember'])}</p></div>{figure(l)}</section>'''

def chapter_link(key):
    return 'guide-'+key+'.html'

index_links = ''.join(f'<a href="{chapter_link(key)}"><span>{esc(title)}</span><small>{esc(desc)}</small></a>' for key,title,desc in CHAPTERS)
hub = f'''<section class="manual-title" id="top"><a class="text-link" href="index.html">← Back to Pocket Ledger</a><p class="manual-edition">Android {VERSION} · Illustrated handbook</p><h1>Your money,<br>explained.</h1><p>Choose what you want to do. Each chapter takes one part of the app, one step at a time.</p><a class="action action-primary" href="guides/{PDF_NAME}">Download the illustrated PDF <span aria-hidden="true">↓</span></a><small class="manual-edition">14 linked pages · Real screenshots with demo data</small></section>
<section class="manual-first" id="install"><div><h2>New here? Start with three small steps.</h2><p>You can begin offline, without a Google Sheet or sign-in.</p><p>Not installed yet? <a href="download.html">Get the Android preview</a> (Android 8.0+, debug-signed). For an older app, <a href="guide-backups.html">export JSON before migrating</a> and keep the old app until you verify the import.</p></div><ol><li><a href="guide-accounts.html">Add the accounts you already use.</a></li><li><a href="guide-entries.html">Record your first expense.</a></li><li><a href="guide-backups.html">Keep a financial JSON backup.</a></li></ol></section>
<section class="manual-topics" aria-labelledby="topics-title"><h2 id="topics-title">What would you like to understand?</h2><div class="manual-topic-grid">{index_links}</div></section>
<section class="manual-shortcuts"><div id="everyday"><h2>Find a specific answer.</h2><p><a href="guide-entries.html#money-moves">Transfer money or record a refund</a><br><a href="guide-people.html#repayments">Receive a partial loan repayment</a><br><a href="guide-split-payments.html#split-tax">Enter the whole bill’s final amount</a><br><a href="guide-backups.html#backups">Move to a new phone safely</a></p></div><div id="sheets"><h2>Connecting a Sheet?</h2><p>Use the Android template with the Android script. Keep the iOS Shortcut workflow separate.</p><a class="text-link" href="guide-sheets.html">Open the Sheets chapter ↗</a></div></section>
<section class="manual-resources" id="resources"><h2>Downloads &amp; earlier editions</h2><div class="resource-links"><a href="apps-script/Pocket-Ledger-Apps-Script.gs.txt">Android script <span aria-hidden="true">↓</span></a><a href="apps-script/Pocket-Ledger-iOS.gs.txt">iOS script <span aria-hidden="true">↓</span></a><a href="guides/iPhone/iPhone-Shortcut-Setup-Guide.pdf">iOS setup PDF <span aria-hidden="true">↗</span></a><a href="guides/Pocket-Ledger-Quick-Guide-2026-10.pdf">Open earlier Android PDF (expenses &amp; widgets) <span aria-hidden="true">↗</span></a></div><p>The screenshots use clearly marked examples. Your own accounts and balances will differ. The iOS Shortcut does not include Android’s accounts or standalone bill splitter.</p></section>'''
first_panel=re.search(r'<section class="manual-first".*?</section>',hub,re.S).group()
hub=hub.replace(first_panel,'').replace('<section class="manual-shortcuts">',first_panel+'<section class="manual-shortcuts">')
page('guide.html', 'Your money, explained.', 'A beginner-friendly Pocket Ledger handbook: accounts, entries, Lena / Dena, split bills, receipts, Sheets and backups.', hub)

for n, (key, title, description) in enumerate(CHAPTERS):
    side = '<nav class="manual-index" aria-label="Guide chapters"><a href="guide.html">All guide topics</a>'+''.join(f'<a href="{chapter_link(k)}"'+(' aria-current="page"' if k==key else '')+f'>{esc(t)}</a>' for k,t,d in CHAPTERS)+'</nav>'
    lessons = ''.join(lesson_html(l) for l in LESSONS if l['chapter']==key)
    faqs = ''.join('<details><summary>'+esc(q)+'</summary><p>'+esc(a)+'</p></details>' for q,a in FAQS[key])
    extra = ''
    if key == 'sheets':
        extra = f'''<div class="manual-tools"><a href="{ANDROID_SHEET}" target="_blank" rel="noopener noreferrer">Copy Android Sheet ↗</a><a href="apps-script/Pocket-Ledger-Apps-Script.gs.txt">Open Android script ↗</a><a href="{IOS_SHEET}" target="_blank" rel="noopener noreferrer">Separate iOS Sheet ↗</a><a href="https://developers.google.com/apps-script/guides/web" target="_blank" rel="noopener noreferrer">Google’s Web app deployment reference ↗</a></div>'''
    back = '<a href="guide.html">← All topics</a>' if n==0 else f'<a href="{chapter_link(CHAPTERS[n-1][0])}">← {esc(CHAPTERS[n-1][1])}</a>'
    forward = '<a href="guide.html">Back to all topics →</a>' if n==len(CHAPTERS)-1 else f'<a href="{chapter_link(CHAPTERS[n+1][0])}">{esc(CHAPTERS[n+1][1])} →</a>'
    in_page=[l for l in LESSONS if l['chapter']==key]
    jumps='<nav class="manual-onpage" aria-label="On this page">'+''.join(f'<a href="#{l["key"]}">{esc(l["title"])}</a>' for l in in_page)+'</nav>' if len(in_page)>1 else ''
    body = f'''<section class="manual-chapter-title" id="top"><a class="text-link" href="guide.html">← All guide topics</a><p class="manual-edition">Android handbook · {VERSION}</p><h1>{esc(title)}.</h1><p>{esc(description)}</p>{jumps}</section><div class="manual-layout">{side}<div class="manual-body">{lessons}{extra}<section class="manual-faq"><h2>Questions you might have.</h2>{faqs}</section><nav class="manual-pagination" aria-label="Previous and next guide chapter">{back}{forward}</nav></div></div>'''
    page(chapter_link(key), title, description, body)

# PDF typography: instantiate the same bundled variable fonts as the website.
sys.path.insert(0, str(ROOT / 'tools/site-smoke/out/font-runtime'))
from fontTools.ttLib import TTFont as FontToolsFont
from fontTools.varLib.instancer import instantiateVariableFont
for source, name, weight in [('dm-sans-latin.woff2','GuideBody',400),('dm-sans-latin.woff2','GuideBold',700),('archivo-latin.woff2','GuideDisplay',800)]:
    font = FontToolsFont(SITE / 'fonts' / source)
    axes = {a.axisTag: (weight if a.axisTag=='wght' else a.defaultValue) for a in font['fvar'].axes}
    font = instantiateVariableFont(font, axes, inplace=True)
    font.flavor = None
    target = TMP / (name+'.ttf')
    font.save(target)
    pdfmetrics.registerFont(TTFont(name,str(target)))
pdfmetrics.registerFontFamily('GuideBody',normal='GuideBody',bold='GuideBold',italic='GuideBody',boldItalic='GuideBold')

W,H = 595.276,841.89
BG,INK,GREEN,MUTED,LINE = '#f2f8f4','#10251b','#087345','#466156','#c9ddd2'
c = canvas.Canvas(str(OUT), pagesize=(W,H), pageCompression=1)
c.setTitle('Pocket Ledger - Your money, explained')
c.setAuthor('Pocket Ledger')
c.setSubject('Illustrated Android handbook: accounts, financial entries, Lena / Dena, split bills, OCR, Sheets and backups')
layout = []

def rect(x,y,w,h,color,radius=0):
    c.setFillColor(HexColor(color))
    if radius:c.roundRect(x,H-y-h,w,h,radius,fill=1,stroke=0)
    else:c.rect(x,H-y-h,w,h,fill=1,stroke=0)

def para(text,x,y,w,size=10.5,color=INK,bold=False,leading=None,footer=False):
    style=ParagraphStyle('guide',fontName='GuideBold' if bold else 'GuideBody',fontSize=size,leading=leading or size*1.42,textColor=HexColor(color))
    p=Paragraph(text,style);_,height=p.wrap(w,H)
    if y+height>H-(12 if footer else 38):raise ValueError(f'PDF overflow on page {c.getPageNumber()}: {text[:65]}')
    p.drawOn(c,x,H-y-height)
    layout.append({'page':c.getPageNumber(),'top':y,'bottom':y+height,'text':text})
    return y+height

def display(text,x,y,w,size=29):
    p=Paragraph(esc(text),ParagraphStyle('display',fontName='GuideDisplay',fontSize=size,leading=size*1.04,textColor=HexColor(INK)))
    _,height=p.wrap(w,H);p.drawOn(c,x,H-y-height)
    layout.append({'page':c.getPageNumber(),'top':y,'bottom':y+height,'text':text})
    return y+height

def base(page_label,number):
    rect(0,0,W,H,BG)
    para('Pocket Ledger',42,28,260,10,bold=True)
    para('Android '+VERSION,385,29,168,8.2,color=MUTED)
    c.setStrokeColor(HexColor(LINE));c.line(42,H-53,W-42,H-53)
    para(page_label,42,H-29,400,8,color=MUTED,footer=True)
    para(str(number).zfill(2)+' / 14',503,H-29,52,8,color=MUTED,footer=True)
    if number>1:
        c.linkRect('Back to contents','contents',(42,12,300,33),relative=0,thickness=0)

def shot(l,x,y,w,max_h=535):
    sx,sy,sw,sh=l['area']; h=w*sh/sw
    if h>max_h:w*=max_h/h;h=max_h
    rect(x-9,y-9,w+18,h+18,'#dcece2',16)
    c.saveState();clip=c.beginPath();clip.roundRect(x,H-y-h,w,h,9);c.clipPath(clip,stroke=0,fill=0)
    scale=w/sw
    c.drawImage(ImageReader(str(ASSETS/(l['image']+'.png'))),x-sx*scale,H-y+(sy-2712)*scale,width=1220*scale,height=2712*scale,mask='auto')
    c.restoreState()
    for i,(mx,my,mw,mh) in enumerate(l.get('marks',[]),1):
        px=x+(mx-sx)*scale;py=y+(my-sy)*scale;pw=mw*scale;ph=mh*scale
        if py<y or py+ph>y+h:continue
        c.setStrokeColor(HexColor('#54e09e'));c.setLineWidth(1.6);c.roundRect(px,H-py-ph,pw,ph,4,fill=0,stroke=1)
        c.setFillColor(HexColor(GREEN));c.circle(x-10,H-py-9,9,fill=1,stroke=0)
        c.setFillColor(HexColor('#ffffff'));c.setFont('GuideBold',9);c.drawCentredString(x-10,H-py-12,chr(64+i))
    para('Real app capture · example data',x,y+h+18,w,7.8,color=GREEN,bold=True)
    bottom=para(esc(l['caption']),x,y+h+32,w,8.4,color=MUTED)
    legend=' · '.join(chr(65+i)+': '+label for i,label in enumerate(FOCUS.get(l['key'],[])))
    return para(esc(legend),x,bottom+8,w,7.8,color=GREEN)

base('Illustrated handbook',1);c.bookmarkPage('contents');c.addOutlineEntry('Start here / contents','contents',0)
display('Your money,\nexplained.'.replace('\n',' '),42,88,480,46)
para('A practical guide to Pocket Ledger.',44,199,470,16,color=MUTED)
para('Choose a task below. Every topic has short steps and real app screenshots. Example amounts are for learning; your own ledger will differ.',44,238,480,12)
para('Install the Android preview from the website. Moving from an older app? Export JSON first and keep the old app until you verify the import (page 14).',44,287,485,9,color=MUTED)
c.linkURL('https://Techyeye98.github.io/Pocket-Ledger/download.html',(44,H-319,530,H-285),relative=0,thickness=0)
rect(42,322,511,100,'#dcece2',12)
para('Your first three steps',58,339,460,13,bold=True)
for sx,text in [(58,'1. Add your accounts.'),(222,'2. Record an expense.'),(386,'3. Keep a JSON backup.')]:para(text,sx,371,150,10.3)
chapter_pages={key:next(i+2 for i,l in enumerate(LESSONS) if l['chapter']==key) for key,_,_ in CHAPTERS}
yy=451
for key,title,desc in CHAPTERS:
    para(esc(title),44,yy,438,11,bold=True);para(str(chapter_pages[key]).zfill(2),510,yy,40,11,color=GREEN,bold=True)
    c.linkRect(title,key,(42,H-yy-22,553,H-yy+2),relative=0,thickness=0)
    c.setStrokeColor(HexColor(LINE));c.line(44,H-yy-24,551,H-yy-24);yy+=32
para('Android 8.0+ · Current preview · Offline first · Optional Google Sheets',44,768,500,8.4,color=MUTED)
c.showPage()

seen_chapters=set()
for number,l in enumerate(LESSONS,2):
    base('Guide topics ←   /   '+dict((k,t) for k,t,d in CHAPTERS)[l['chapter']],number)
    c.bookmarkPage(l['key'])
    if l['chapter'] not in seen_chapters:
        c.bookmarkPage(l['chapter'])
        c.addOutlineEntry(dict((k,t) for k,t,d in CHAPTERS)[l['chapter']],l['chapter'],0)
        seen_chapters.add(l['chapter'])
    top=display(l['title'],42,82,510,29)
    top=para(esc(l['intro']),42,top+15,505,12,color=MUTED)
    start=max(205,top+30)
    if l['image']:
        left=239;right_x=320;right_w=225
        para(esc(l['path']),42,start,left,9.3,color=GREEN,bold=True)
        yy=start+44
        for i,(title,body) in enumerate(l['steps'],1):
            rect(42,yy+1,20,20,GREEN,10);para(str(i),48,yy+3,13,9.5,color='#ffffff',bold=True)
            yy=para(esc(title),72,yy,left-30,11,bold=True)+6
            yy=para(esc(body),72,yy,left-30,10.3,color=MUTED)+19
        yy+=1
        yy=para(esc(l['example'][0]),42,yy,left,11,color=GREEN,bold=True)+7
        yy=para(esc(l['example'][1]),42,yy,left,10.3)+19
        para(esc(l['remember']),42,yy,left,9.2,color=MUTED)
        shot(l,right_x,start+3,right_w,min(530,H-start-105))
    else:
        # A setup map is clearer than a screenshot containing someone's private URL.
        para(esc(l['path']),42,start,500,10,color=GREEN,bold=True)
        yy=start+42
        for i,(title,body) in enumerate(l['steps'],1):
            rect(42,yy+1,22,22,GREEN,11);para(str(i),49,yy+4,14,10,color='#ffffff',bold=True)
            end=para(esc(title),78,yy,470,12,bold=True)+6
            yy=para(esc(body),78,end,470,10.8,color=MUTED)+22
        yy=para(esc(l['example'][0]),42,yy+4,500,12,color=GREEN,bold=True)+8
        yy=para(esc(l['example'][1]),42,yy,500,10.7)+18
        yy=para(esc(l['remember']),42,yy,500,10.1,color=MUTED)+19
        for title,url in [('Copy Android Sheet',ANDROID_SHEET),('Android script','https://Techyeye98.github.io/Pocket-Ledger/apps-script/Pocket-Ledger-Apps-Script.gs.txt'),('Google deployment reference','https://developers.google.com/apps-script/guides/web')]:
            para(esc(title),42,yy,500,9.3,color=GREEN,bold=True)
            c.linkURL(url,(42,H-yy-15,553,H-yy+2),relative=0,thickness=0);yy+=23
    c.showPage()
c.save()
reader=PdfReader(str(OUT));assert len(reader.pages)==14
assert all(p.extract_text() and '\ufffd' not in p.extract_text() for p in reader.pages)
(TMP/'layout.json').write_text(json.dumps(layout,indent=2),encoding='utf-8')
shutil.copyfile(OUT,SITE/'guides'/PDF_NAME)
print(f'Built 10 web pages, 14-page PDF, {len(APPROVED)} original screenshot assets. PDF: {OUT.stat().st_size:,} bytes.')
