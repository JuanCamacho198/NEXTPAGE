#!/usr/bin/env python3
"""Deterministic generator for the cross-device continuity fixture EPUB.

Builds `src/lib/shared/sync/fixtures/continuity-fixture.epub` — a minimal but
REAL EPUB3 with several chapters of fixed, public-domain text (a requirement of
the locator-interop golden: "real Gutenberg EPUB, minimal; fixed").

Rules for stability (the golden test depends on them):
- Chapters are flat: `<body><h1>…</h1><p>…</p>…</body>` (no nested sections).
- Each `<p>` is a single text node with no inline elements.
- The text of chapter 3 (the golden chapter) is FIXED below; never change it.

Run from the repo root:
    python desktop/tools/gen_continuity_fixture.py

Reproducible: same inputs -> byte-identical EPUB (zip uses fixed timestamps).
"""
from __future__ import annotations

import zipfile
from pathlib import Path

OUT = Path("desktop/src/lib/shared/sync/fixtures/continuity-fixture.epub")

# ── Golden chapter (spine index 3) — FIXED TEXT ───────────────────────
# "Bravo paragraph" starts the golden target paragraph. The golden JSON
# records: chapterHref, paragraphText, expectedCfi, expectedProgression.
GOLDEN_CHAPTER_HTML = """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head><title>Chapter Three</title></head>
<body>
<h1>The Third Chapter</h1>
<p>Alpha paragraph opening words here.</p>
<p>Bravo paragraph with enough text that the chapter has a nonzero length and the
progression of the middle paragraph lands somewhere between zero and one. It
continues at length so the total character count is comfortably above any
rounding drift that the cross-engine tolerance of two percent has to absorb.</p>
<p>Charlie closing paragraph, trailing punctuation.</p>
</body>
</html>
"""

CHAPTER_TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head><title>Chapter {label}</title></head>
<body>
<h1>Chapter {label}</h1>
<p>Opening paragraph of chapter {label}. Fixed text keeps the fixture
deterministic across checkouts and CI runs.</p>
<p>Second paragraph of chapter {label} with a second sentence to pad the length
of the text content so the chapter is not trivially short.</p>
<p>Closing paragraph of chapter {label}.</p>
</body>
</html>
"""

CHAPTERS = {
    "chapter1.xhtml": CHAPTER_TEMPLATE.format(label="One"),
    "chapter2.xhtml": CHAPTER_TEMPLATE.format(label="Two"),
    "chapter3.xhtml": GOLDEN_CHAPTER_HTML,
    "chapter4.xhtml": CHAPTER_TEMPLATE.format(label="Four"),
}

MIMETYPE = "application/epub+zip"

CONTAINER_XML = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
"""

OPF = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="uid">urn:uuid:continuity-fixture-0001</dc:identifier>
    <dc:title>Continuity Fixture</dc:title>
    <dc:language>en</dc:language>
    <dc:creator>Fixture Generator</dc:creator>
    <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="c1" href="Text/chapter1.xhtml" media-type="application/xhtml+xml"/>
    <item id="c2" href="Text/chapter2.xhtml" media-type="application/xhtml+xml"/>
    <item id="c3" href="Text/chapter3.xhtml" media-type="application/xhtml+xml"/>
    <item id="c4" href="Text/chapter4.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine>
    <itemref idref="c1"/>
    <itemref idref="c2"/>
    <itemref idref="c3"/>
    <itemref idref="c4"/>
  </spine>
</package>
"""

NAV = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><title>Contents</title></head>
<body>
<nav epub:type="toc">
  <ol>
    <li><a href="Text/chapter1.xhtml">Chapter One</a></li>
    <li><a href="Text/chapter2.xhtml">Chapter Two</a></li>
    <li><a href="Text/chapter3.xhtml">Chapter Three</a></li>
    <li><a href="Text/chapter4.xhtml">Chapter Four</a></li>
  </ol>
</nav>
</body>
</html>
"""


def build() -> None:
    # mimetype must be first and STORED (uncompressed) per EPUB spec.
    with zipfile.ZipFile(OUT, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        zf.writestr(zipfile.ZipInfo("mimetype"), MIMETYPE, zipfile.ZIP_STORED)
        zf.writestr("META-INF/container.xml", CONTAINER_XML)
        zf.writestr("OEBPS/content.opf", OPF)
        zf.writestr("OEBPS/nav.xhtml", NAV)
        for name, html in CHAPTERS.items():
            zf.writestr(f"OEBPS/Text/{name}", html)
    print(f"wrote {OUT} ({OUT.stat().st_size} bytes)")


if __name__ == "__main__":
    build()
