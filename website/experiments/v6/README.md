# Pocket Ledger V6 — The shape of a day

The original V6 landing-page source, now promoted into `distribution/` for GitHub Pages. It preserves Pocket Ledger's brand colours and dark/light themes with an original visual story, continuous trace motion and a page-opening guide book.

Run from the repository root:

```sh
node website/experiments/v6/serve.cjs
```

Preview: http://127.0.0.1:4176/website/experiments/v6/

No build step or paid dependency. The abstract hero trace is original SVG/CSS. The sample visual uses five explicitly labelled illustrative transactions; its moments/category control and scroll position rearrange those same data marks. The optional-sync control explains Android's local-first behavior without initiating any network request. Motion uses CSS transforms and WAAPI, with reduced-motion support. All remote destinations were copied from the rendered live GitHub Pages site on 2026-10-13.

The Android download is now 6.0.8 at `downloads/Pocket-Ledger.apk`; it installs separately from V2. The current guide has a new filename, `Pocket-Ledger-Quick-Guide-2026-10.pdf`. The production page links to its `guides/` path, while this preview serves its adjacent PDF. Current Android Apps Script and Sheet links include Need / Want support. iPhone destinations remain separate and unchanged. GitHub Pages publishes `distribution/`, not this experiment folder.
