# Pocket Ledger distribution package

This folder is the end-user release package and GitHub Pages-ready distribution portal.

## Contents

- `index.html` + `style.css` + `main.js` — the live static website served at the GitHub Pages root. Zero runtime dependencies (Google Fonts is the only external request).
- `website/` — preserved V1 website, still served at `/website/`; `website/code.html` redirects to the root.
- `downloads/Pocket-Ledger-Android-V2.apk` — current Android release, served directly from this folder (the `.apk` ignore rule carries an explicit exception for this path).
- `downloads/Pocket-Ledger-Android.apk` — the V1 Android debug APK copied from `NativeExpenseEntry/app/build/outputs/apk/debug/app-debug.apk`.
- `downloads/Pocket-Ledger-iPhone.shortcut` — byte-for-byte copy of `Reference/iPhone/Original_iPhone.shortcut`.
- `guides/Google-Sheets/Pocket-Ledger-Setup-Guide.pdf` — supplied setup guide.
- `apps-script/Pocket-Ledger-Apps-Script.gs.txt` — supplied Apps Script source for the user's own copied Sheet.

## User setup

1. Choose Android or iPhone from the website.
2. Use **Make your copy** to add the template to the user's Google Drive.
3. Follow the supplied guide, add/deploy the supplied Apps Script in that personal Sheet, and get the Web App URL.
4. Configure that personal URL in Pocket Ledger or the iPhone Shortcut.
5. Test the connection before logging expenses.

The site uses this exact public make-a-copy URL:

`https://docs.google.com/spreadsheets/d/16j8RzujL-Z_zTnZ8ST50UopyROpsU-yM21yhWZf2IxY/copy`

## GitHub Pages

The included GitHub Actions workflow publishes `distribution/` to GitHub Pages after a push to `main`. The root `index.html` is the live homepage, so every link in it is relative to this folder (`downloads/…`, `guides/…`, `apps-script/…`). In GitHub repository settings, set Pages source to **GitHub Actions**.

The previous (V2) homepage is preserved verbatim at `archive/v2-site/index.html` and in git history (commit `d337f26`).

## Verifying a change

```bash
node tools/site-smoke/smoke.js
```

This serves `distribution/` the way Pages does and drives it in headless Edge: donut (3 segments / 3 legend rows / exactly 100%), period tabs, quick log, tag filter, theme toggle, mobile menu, pipeline scrub, APK reachability, every local link, and horizontal overflow at 1440×900 and 390×844.
