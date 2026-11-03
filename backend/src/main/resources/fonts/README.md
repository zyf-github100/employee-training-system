# Certificate font

`CertificateSans-Regular.ttf` is the bundled Chinese font used by the PDF service. It is a modified static instance of Noto Sans SC (weight 400), renamed **Zhixing Certificate Sans** under the SIL Open Font License 1.1. The original copyright metadata remains embedded. The license is included in `OFL.txt`; distribute it with the font and application.

- Upstream font: https://raw.githubusercontent.com/notofonts/noto-cjk/main/Sans/Variable/TTF/Subset/NotoSansSC-VF.ttf
- Upstream license: https://github.com/notofonts/noto-cjk/blob/main/Sans/LICENSE
- Prepared 2026-11-02 with Python and fontTools 4.68.0.
- SHA-256: `066df3dc64bbf1fff66474e06195dfe03de890f29564cd9128b517d4df907249`.

To reproduce, download the upstream font to `backend/.local/NotoSansSC-VF.ttf`, install `fonttools==4.68.0` into `backend/.local/pdf-font-tools`, then run `python backend/scripts/prepare_certificate_font.py` from the repository root. The binary is checked in, so runtime/build machines need no font download, Python or system font installation. PDFBox embeds only the glyphs used in each PDF. Unsupported glyphs produce a clear error rather than replacing a recipient's name.
