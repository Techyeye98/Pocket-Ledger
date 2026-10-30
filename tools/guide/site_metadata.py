"""Shared page metadata for the static site and handbook rebuilds."""
import html
import json
import re
from current_guide_content import VERSION

BASE = 'https://techyeye98.github.io/Pocket-Ledger/'
PAGES = {
    'index.html': ('Pocket Ledger — Offline Expense Tracker for Android', 'Track accounts, income, expenses and money owed offline on Android. Split bills separately, connect Google Sheets, or use the simpler iPhone Shortcut.'),
    'features.html': ('Accounts, Expenses & Bill Splitting — Pocket Ledger', 'Explore offline Android accounts, income, transfers, refunds, shared expenses and bill splitting, with optional Google Sheets sync.'),
    'download.html': ('Download Pocket Ledger for Android & iPhone Shortcut', 'Get the Pocket Ledger Android preview or separate iPhone expense Shortcut. Find Android requirements, matching Google Sheets templates and setup help.'),
    'your-data.html': ('Offline Storage, Sync & Backups — Pocket Ledger', 'Understand Pocket Ledger offline storage, optional Google Sheets sync, financial JSON backups and the split-bill data that stays on your Android phone.'),
    'guide.html': ('Pocket Ledger Guide — Accounts, Expenses, Splits & Sync', 'Learn Pocket Ledger step by step: set up accounts, record expenses, track repayments, split bills, connect Google Sheets and back up your ledger.'),
    'guide-accounts.html': ('Set Up Bank, Cash & Card Accounts — Pocket Ledger', 'Set up bank, cash, wallet and card accounts in Pocket Ledger. Choose starting balances and dates, then link payment names to the right Android account.'),
    'guide-entries.html': ('Record Expenses, Income & Transfers — Pocket Ledger', 'Record expenses and income in Pocket Ledger, move money between accounts and link purchase refunds without counting transfers as new spending.'),
    'guide-insights.html': ('Review Spending & Find Transactions — Pocket Ledger', 'Read category charts and Need / Want tags in Pocket Ledger. Filter Activity, find original entries and use Android quick entry for everyday spending.'),
    'guide-people.html': ('Shared Expenses, Loans & Repayments — Pocket Ledger', 'Understand Lena / Dena in Pocket Ledger: separate your share of a purchase, track lending and borrowing, and link partial repayments to open items.'),
    'guide-split-bills.html': ('Split Bill Items with Friends — Pocket Ledger', 'Split a bill item by item in Pocket Ledger. Choose the group, enter full line totals and assign portions to the friends who shared each item.'),
    'guide-split-payments.html': ('Split Bill Tax, Payments & Settlements — Pocket Ledger', 'Handle bill tax and discounts in Pocket Ledger, record multiple payers and track partial reimbursements. See worked examples before settling a bill.'),
    'guide-receipts.html': ('Scan & Review Receipt Items — Pocket Ledger', 'Import a receipt image or scanned PDF into the Android bill splitter. Review detected items, correct errors and confirm tax before adding the bill.'),
    'guide-sheets.html': ('Connect Google Sheets to Android — Pocket Ledger', 'Connect Pocket Ledger for Android to your own Google Sheet. Copy the template, deploy the matching Apps Script and understand sync and restore limits.'),
    'guide-backups.html': ('Back Up Your Ledger & Change Phones — Pocket Ledger', 'Export and restore a financial JSON backup in Pocket Ledger. Check what moves to a new Android phone and which split-bill data must stay on the old one.'),
}


def metadata_head(head, filename, label):
    """Keep tags, page identity and breadcrumbs consistent when regenerating HTML."""
    title, description = PAGES[filename]
    url = BASE + ('' if filename == 'index.html' else filename)
    esc = lambda value: html.escape(value, quote=True)
    head = re.sub(r'<title>.*?</title>', lambda _: '<title>' + esc(title) + '</title>', head)
    for attr, key, value in [('name', 'description', description), ('property', 'og:title', title), ('property', 'og:description', description), ('property', 'og:url', url)]:
        head = re.sub(r'(<meta ' + attr + '="' + key + r'" content=")[^"]*', lambda match: match[1] + esc(value), head)
    head = re.sub(r'(<link rel="canonical" href=")[^"]*', lambda match: match[1] + url, head)
    graph = [
        {'@type': 'WebSite', '@id': BASE + '#website', 'name': 'Pocket Ledger', 'url': BASE},
        {'@type': 'SoftwareApplication', '@id': BASE + '#android-app', 'name': 'Pocket Ledger', 'applicationCategory': 'FinanceApplication', 'operatingSystem': 'Android 8.0+', 'softwareVersion': VERSION, 'description': 'An offline Android personal ledger with accounts, income, expenses, money owed, separate bill splitting and optional Google Sheets sync.', 'url': BASE, 'downloadUrl': BASE + 'downloads/Pocket-Ledger.apk', 'sameAs': ['https://github.com/Techyeye98/Pocket-Ledger']},
        {'@type': 'CollectionPage' if filename == 'guide.html' else 'WebPage', '@id': url + '#webpage', 'url': url, 'name': title, 'description': description, 'isPartOf': {'@id': BASE + '#website'}, 'about': {'@id': BASE + '#android-app'}},
    ]
    if filename != 'index.html':
        crumbs = [('Home', BASE)]
        if filename.startswith('guide-'):
            crumbs.append(('Guide', BASE + 'guide.html'))
        crumbs.append((label, url))
        graph.append({'@type': 'BreadcrumbList', '@id': url + '#breadcrumbs', 'itemListElement': [{'@type': 'ListItem', 'position': i, 'name': name, 'item': item} for i, (name, item) in enumerate(crumbs, 1)]})
        graph[2]['breadcrumb'] = {'@id': url + '#breadcrumbs'}
    schema = json.dumps({'@context': 'https://schema.org', '@graph': graph}, ensure_ascii=False).replace('<', '\u003c')
    head = re.sub(r'<script type="application/ld\+json">.*?</script>', lambda _: '<script type="application/ld+json">' + schema + '</script>', head, flags=re.S)
    return head
