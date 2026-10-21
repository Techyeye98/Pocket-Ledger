"""Check every local page/resource/fragment and public outbound link without publishing."""
from concurrent.futures import ThreadPoolExecutor
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urlsplit, unquote
from urllib.request import Request, urlopen
import json

ROOT = Path(__file__).resolve().parents[2]
SITE = ROOT / 'distribution'
OUT = ROOT / 'tools/site-smoke/out/home-refinement'

class Page(HTMLParser):
    def __init__(self, path):
        super().__init__()
        self.ids, self.links = set(), []
        self.feed(path.read_text(encoding='utf-8'))
    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if a.get('id'): self.ids.add(a['id'])
        if tag in ('a','link','script','img','image'):
            url = a.get('href') or a.get('src')
            if url: self.links.append((tag,url,a.get('rel','')))

def main():
    pages = {p.name:Page(p) for p in SITE.glob('*.html')}
    external, failures, local = set(), [], 0
    for name, page in pages.items():
        for tag, url, rel in page.links:
            u = urlsplit(url)
            if u.scheme in ('http','https'):
                # Canonical metadata describes the future published path; it is not a visitor link.
                if tag == 'a': external.add(url)
                continue
            if u.scheme: continue
            target = unquote(u.path).removeprefix('/Pocket-Ledger/') or name
            path = (SITE / target).resolve()
            if not path.is_relative_to(SITE.resolve()) or not path.exists():
                failures.append(f'{name}: missing {url}')
            elif u.fragment and (target not in pages or unquote(u.fragment) not in pages[target].ids):
                failures.append(f'{name}: missing fragment {url}')
            local += 1

    def check(url):
        try:
            req = Request(url,headers={'User-Agent':'Mozilla/5.0 PocketLedgerLocalLinkAudit'})
            with urlopen(req, timeout=30) as response:
                body = response.read(150000).decode('utf-8',errors='replace')
                result = {'url':url,'status':response.status,'final':response.url}
                if '/spreadsheets/' in url:
                    import re
                    title = re.search(r'<title>(.*?)</title>',body,re.S)
                    result['title'] = title.group(1) if title else None
                    result['access'] = 'sign-in required' if 'accounts.google.com' in response.url else 'reachable'
                return result
        except Exception as error:
            return {'url':url,'error':str(error)}

    with ThreadPoolExecutor(max_workers=4) as pool:
        outbound = list(pool.map(check,sorted(external)))
    report = {'pages':len(pages),'localReferences':local,'localFailures':failures,'outbound':outbound}
    OUT.mkdir(parents=True,exist_ok=True)
    (OUT/'links.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps(report,indent=2))
    assert not failures, failures
    assert not any('error' in r for r in outbound), 'Outbound checks need review'

if __name__ == '__main__': main()
