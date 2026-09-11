# Pocket Ledger distribution package

This folder is the end-user release package and GitHub Pages-ready distribution portal.

## Contents

- `website/` — static distribution website. Publish this directory with GitHub Pages.
- `downloads/Pocket-Ledger-Android.apk` — the verified Android debug APK copied from `NativeExpenseEntry/app/build/outputs/apk/debug/app-debug.apk`.
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

The included GitHub Actions workflow publishes `distribution/` to GitHub Pages after a push to `main`. Its root `index.html` opens `website/`; all packaged links are relative (`../downloads`, `../guides`, `../apps-script`) and remain valid. In GitHub repository settings, set Pages source to **GitHub Actions** before the first deployment.

No deployment has been performed by this package.
