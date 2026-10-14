"""Converts Markdown files to PDF via HTML and headless Chrome.

Mermaid code blocks are rendered as diagrams. Rendering loads the Mermaid
library from a CDN, so documents with diagrams need network access.

A file under docs/<category>/ is written to docs/pdf/<category>/. Any other
file is written next to the Markdown file.

Usage: python -I scripts/md2pdf.py <markdown file>...
"""

import html
import pathlib
import re
import subprocess
import sys

import markdown

CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
MERMAID_JS = "https://cdn.jsdelivr.net/npm/mermaid@10.9.1/dist/mermaid.min.js"
MERMAID_BLOCK = re.compile(r"^```mermaid\n(.*?)^```", re.DOTALL | re.MULTILINE)

CSS = """
@page { size: A4; margin: 18mm 16mm; }
body {
  font-family: "Microsoft JhengHei", "Noto Sans TC", sans-serif;
  font-size: 10.5pt; line-height: 1.6; color: #1a1a1a;
}
h1 { font-size: 20pt; border-bottom: 2px solid #333; padding-bottom: 4px; }
h2 { font-size: 15pt; margin-top: 22px; border-bottom: 1px solid #bbb;
     padding-bottom: 2px; }
h3 { font-size: 12.5pt; margin-top: 16px; }
h2, h3 { break-after: avoid; }
table { border-collapse: collapse; width: 100%; margin: 8px 0;
        font-size: 9.5pt; }
th, td { border: 1px solid #999; padding: 4px 6px; vertical-align: top; }
th { background: #eee; white-space: nowrap; }
tr { break-inside: avoid; }
blockquote { margin: 0 0 12px; padding: 6px 12px; background: #f4f4f4;
             border-left: 4px solid #888; }
blockquote p { margin: 2px 0; }
code { font-family: Consolas, monospace; background: #f0f0f0;
       padding: 0 3px; }
pre.mermaid { text-align: center; break-inside: avoid; background: none; }
pre.mermaid svg { max-width: 100%; max-height: 225mm; height: auto; }
"""

MERMAID_SCRIPT = f"""
<script src="{MERMAID_JS}"></script>
<script>
  mermaid.initialize({{
    startOnLoad: false,
    theme: "neutral",
    fontFamily: "Microsoft JhengHei, sans-serif",
  }});
  mermaid.run();
</script>
"""


def _replace_mermaid(text: str) -> tuple[str, bool]:
  """Turns Mermaid code blocks into raw HTML blocks that Mermaid renders."""
  found = False

  def to_html(match: re.Match) -> str:
    nonlocal found
    found = True
    return f'\n<pre class="mermaid">\n{html.escape(match.group(1))}</pre>\n'

  return MERMAID_BLOCK.sub(to_html, text), found


def _pdf_path_for(md_path: pathlib.Path) -> pathlib.Path:
  """Places PDFs of docs/<category>/x.md in docs/pdf/<category>/x.pdf."""
  category_dir = md_path.parent
  docs_dir = category_dir.parent
  if docs_dir.name == "docs" and category_dir.name != "pdf":
    out_dir = docs_dir / "pdf" / category_dir.name
    out_dir.mkdir(parents=True, exist_ok=True)
    return out_dir / md_path.with_suffix(".pdf").name
  return md_path.with_suffix(".pdf")


def convert(md_path: pathlib.Path) -> pathlib.Path:
  text = md_path.read_text(encoding="utf-8")
  # Keep consecutive blockquote lines on separate rows.
  text = text.replace("\n> ", "  \n> ")
  text, has_mermaid = _replace_mermaid(text)
  body = markdown.markdown(text, extensions=["tables", "sane_lists"])
  scripts = MERMAID_SCRIPT if has_mermaid else ""
  page = (
      "<!doctype html><html lang='zh-Hant'><head><meta charset='utf-8'>"
      f"<title>{md_path.stem}</title><style>{CSS}</style></head>"
      f"<body>{body}{scripts}</body></html>"
  )
  html_path = md_path.with_suffix(".tmp.html")
  html_path.write_text(page, encoding="utf-8")
  pdf_path = _pdf_path_for(md_path)
  command = [
      CHROME,
      "--headless=new",
      "--disable-gpu",
      "--no-pdf-header-footer",
      f"--print-to-pdf={pdf_path}",
  ]
  if has_mermaid:
    # Gives the page time to download Mermaid and draw the diagrams.
    command.append("--virtual-time-budget=20000")
  command.append(html_path.as_uri())
  try:
    subprocess.run(command, check=True, capture_output=True, timeout=180)
  finally:
    html_path.unlink(missing_ok=True)
  return pdf_path


if __name__ == "__main__":
  for arg in sys.argv[1:]:
    print(convert(pathlib.Path(arg).resolve()))
