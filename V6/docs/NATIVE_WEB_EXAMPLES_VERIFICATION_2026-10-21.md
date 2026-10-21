# Native examples and link cleanup

The owner rejected the two homepage screenshot previews and requested HTML/CSS presentation integrated with the website, plus fewer repeated links.

- Replaced both screenshot figures with semantic HTML and theme-aware CSS. No image/image SVG elements remain in the product previews.
- Accounts example: bank ₹8,400 + cash ₹1,600 = ₹10,000. A sample ₹500 transfer produces ₹7,900 + ₹2,100 and keeps the total unchanged. Reset restores the original state.
- Split example: ₹1,200 paid by You, three equal ₹400 shares, ₹800 receivable. A sample ₹200 repayment from Aarav leaves ₹600 (Aarav ₹200, Meera ₹400). Reset restores ₹800.
- Examples are expressly labelled and operate only on page text. They do not read/write app data, backups, local storage or Google Sheets. Controls are progressively enabled by JS; static examples remain readable without JS.
- Guide section now contains exactly one link: its existing animated book. Removed the separate button and caption link. Book accessible name follows its visible cover text; keyboard activation retained.
- Removed repeated homepage feature links, the extra home header download CTA, redundant footer Guide link across pages, and the second current-PDF link in the guide hub. Guide authoring source updated for the PDF-link cleanup.

Verification: content/resources/version check passed; browser assertions confirmed transfer, repayment and both reset paths. Homepage checks at 1440×900 dark and 390×844 light show zero automated accessibility violations and no horizontal overflow. The initial book-label mismatch was corrected and both checks repeated successfully. Website motion engine is retained. No publication, app-data changes or cloud sharing changes.

The previously identified Android template sharing restriction is outside this local revision and remains unresolved. Existing guide PDF and image files were not altered. Browser proof is under ignored `tools/site-smoke/out/native-examples/`.
