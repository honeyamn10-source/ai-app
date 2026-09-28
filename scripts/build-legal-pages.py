#!/usr/bin/env python3
"""Render docs/legal/*.md into standalone, dependency-free HTML pages.

The output has no external CSS, fonts or scripts, so the same file can be
dropped into GitHub Pages, an InfinityFree htdocs folder, or any static host
and will render identically with no build step.

The build fails if a [[PLACEHOLDER]] is still present, because a policy with no
operator name or address is rejected by Google Play.

Usage:
    python3 scripts/build-legal-pages.py
    python3 scripts/build-legal-pages.py --out-dir /tmp/out
"""

from __future__ import annotations

import argparse
import html
import re
import sys
from datetime import date
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DOCS = REPO / "docs" / "legal"
DEFAULT_OUT = REPO / "site" / "legal"

PAGES = [
    ("PRIVACY_POLICY.md", "privacy.html", "Privacy Policy"),
    ("TERMS.md", "terms.html", "Terms of Service"),
]

PLACEHOLDER = re.compile(r"\[\[[^\]]+\]\]")
EMAIL = "byakai@yahoo.com"

CSS = """
:root{--bg:#05060a;--panel:#0b0d14;--ink:#e9ecf5;--muted:#9aa3bd;--line:#1c2130;
--a1:#c266ff;--a2:#7057ff;--a3:#58f6ff;--warn:#ffcf66}
*{box-sizing:border-box}
html{-webkit-text-size-adjust:100%}
body{margin:0;background:var(--bg);color:var(--ink);
font:16px/1.65 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif}
.wrap{max-width:760px;margin:0 auto;padding:48px 20px 96px}
a{color:var(--a3)}
header{border-bottom:1px solid var(--line);padding-bottom:24px;margin-bottom:8px}
.brand{display:inline-flex;align-items:center;gap:10px;font-weight:800;letter-spacing:.02em;
text-decoration:none;color:var(--ink);font-size:18px}
.brand small{display:block;font-size:10px;letter-spacing:.18em;color:var(--muted);font-weight:600}
h1{font-size:34px;line-height:1.2;margin:36px 0 8px;letter-spacing:-.02em}
h2{font-size:21px;margin:38px 0 10px;padding-top:22px;border-top:1px solid var(--line);letter-spacing:-.01em}
h3{font-size:17px;margin:26px 0 8px;color:#dfe4f2}
p,li{color:#d3d9e8}
ul,ol{padding-left:22px}
li{margin:6px 0}
code{background:#11141d;border:1px solid var(--line);border-radius:5px;padding:1px 5px;
font:13px/1.5 ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;color:#c9d4f0}
hr{border:0;border-top:1px solid var(--line);margin:34px 0}
blockquote{margin:20px 0;padding:14px 18px;background:var(--panel);
border:1px solid #2a2f42;border-left:3px solid var(--warn);border-radius:10px;color:#e6e9f5}
blockquote p{margin:6px 0}
mark.todo{background:rgba(255,207,102,.18);color:var(--warn);border-radius:4px;padding:0 4px}
table{width:100%;border-collapse:collapse;margin:18px 0;font-size:14.5px;display:block;overflow-x:auto}
th,td{border:1px solid var(--line);padding:9px 11px;text-align:left;vertical-align:top}
th{background:var(--panel);color:var(--ink);font-weight:600}
td{color:#c6cddf}
footer{margin-top:56px;padding-top:20px;border-top:1px solid var(--line);
color:var(--muted);font-size:13.5px}
.meta{color:var(--muted);font-size:14px;margin:0 0 8px}
.danger{border-left-color:#ff6b6b}
@media (prefers-color-scheme: light){
body{background:#fbfbfe;color:#161a24}
p,li{color:#2b3245}h1,h2{color:#0d1018}th{background:#f1f3fa;color:#0d1018}
td{color:#2b3245}code{background:#eef1f8;border-color:#dde2ee;color:#1c2740}
blockquote{background:#fffbeb;border-color:#f0e2b8}
footer{color:#5b6478}.brand{color:#0d1018}
}
@media print{body{background:#fff;color:#000}.wrap{max-width:none}}
"""

PAGE = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>{title} &middot; BYAK AI</title>
<meta name="description" content="{description}">
<meta name="robots" content="index,follow">
<meta name="theme-color" content="#05060a">
<link rel="icon" href="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 32 32'%3E%3Ctext y='26' font-size='26'%3E%E2%97%89%3C/text%3E%3C/svg%3E">
<style>{css}</style>
</head>
<body>
<div class="wrap">
<header><span class="brand">BYAK AI<small>ANDROID INTELLIGENCE</small></span></header>
{body}
<footer>
<p>BYAK AI &mdash; package <code>ai.byak.app</code> on Google Play.</p>
<p>Privacy questions: <a href="mailto:{email}">{email}</a></p>
<p>Generated {generated} from <code>docs/legal/</code> in the app repository, so this page and the
app always describe the same behaviour.</p>
</footer>
</div>
</body>
</html>
"""

DESCRIPTIONS = {
    "privacy.html": "How BYAK AI handles data: on-device storage, provider requests, "
                    "Google Play billing, retention, deletion and security.",
    "terms.html": "The terms that apply to the BYAK AI Android app and website, including "
                  "BYAK Pro subscriptions, acceptable use and liability.",
}


def inline(text: str) -> str:
    """Markdown inline -> HTML. Placeholders are escaped, not stripped."""
    out = html.escape(text, quote=False)
    out = re.sub(r"\[([^\]]+)\]\((mailto:[^)]+|https?://[^)]+)\)", r'<a href="\2">\1</a>', out)
    out = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", r'<a href="\2">\1</a>', out)
    out = re.sub(r"`([^`]+)`", r"<code>\1</code>", out)
    out = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", out)
    out = re.sub(r"(?<![\w*])\*([^*\n]+)\*(?![\w*])", r"<em>\1</em>", out)
    out = PLACEHOLDER.sub(lambda m: f'<mark class="todo">{m.group(0)}</mark>', out)
    return out


# A line that ends a list item / paragraph rather than continuing it.
BREAK = re.compile(r"^(#{1,6}\s|[-*]\s|\d+[.)]\s|\||>|(-{3,}|\*{3,})$)")


def render_table(rows: list[list[str]]) -> str:
    if not rows:
        return ""
    head, *body = rows
    out = ["<table><thead><tr>"]
    out += [f"<th>{inline(c)}</th>" for c in head]
    out.append("</tr></thead><tbody>")
    for row in body:
        out.append("<tr>" + "".join(f"<td>{inline(c)}</td>" for c in row) + "</tr>")
    out.append("</tbody></table>")
    return "".join(out)


def split_row(line: str) -> list[str]:
    return [c.strip() for c in line.strip().strip("|").split("|")]


def markdown_to_html(md: str) -> str:
    lines = md.split("\n")
    out: list[str] = []
    i = 0
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()

        if not stripped:
            i += 1
            continue

        if stripped.startswith("|") and i + 1 < len(lines) and re.match(
            r"^\|[\s:|-]+\|$", lines[i + 1].strip()
        ):
            rows = [split_row(stripped)]
            i += 2  # skip the |---|---| separator
            while i < len(lines) and lines[i].strip().startswith("|"):
                rows.append(split_row(lines[i].strip()))
                i += 1
            out.append(render_table(rows))
            continue

        m = re.match(r"^(#{1,6})\s+(.*)$", stripped)
        if m:
            level = len(m.group(1))
            out.append(f"<h{level}>{inline(m.group(2))}</h{level}>")
            i += 1
            continue

        if re.match(r"^(-{3,}|\*{3,})$", stripped):
            out.append("<hr>")
            i += 1
            continue

        if stripped.startswith(">"):
            buf = []
            while i < len(lines) and lines[i].strip().startswith(">"):
                buf.append(re.sub(r"^>\s?", "", lines[i].strip()))
                i += 1
            out.append(f"<blockquote>{markdown_to_html(chr(10).join(buf))}</blockquote>")
            continue

        if re.match(r"^[-*]\s+", stripped):
            items = []
            while i < len(lines) and re.match(r"^[-*]\s+", lines[i].strip()):
                # Gather lazy continuation lines so inline markup spanning a
                # line break is still rendered as one run of text.
                item = re.sub(r"^[-*]\s+", "", lines[i].strip())
                i += 1
                while i < len(lines) and lines[i].strip() and not BREAK.match(lines[i].strip()):
                    item += " " + lines[i].strip()
                    i += 1
                items.append(item)
            out.append("<ul>" + "".join(f"<li>{inline(it)}</li>" for it in items) + "</ul>")
            continue

        if re.match(r"^\d+[.)]\s+", stripped):
            items = []
            while i < len(lines) and re.match(r"^\d+[.)]\s+", lines[i].strip()):
                item = re.sub(r"^\d+[.)]\s+", "", lines[i].strip())
                i += 1
                while i < len(lines) and lines[i].strip() and not BREAK.match(lines[i].strip()):
                    item += " " + lines[i].strip()
                    i += 1
                items.append(item)
            out.append("<ol>" + "".join(f"<li>{inline(it)}</li>" for it in items) + "</ol>")
            continue

        buf = []
        while i < len(lines) and lines[i].strip() and not BREAK.match(lines[i].strip()):
            buf.append(lines[i].strip())
            i += 1
        if buf:
            out.append(f"<p>{inline(' '.join(buf))}</p>")
        else:
            i += 1

    return "\n".join(out)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out-dir", default=str(DEFAULT_OUT))
    ap.add_argument(
        "--allow-placeholders",
        action="store_true",
        help="render even if [[...]] operator details are still missing",
    )
    args = ap.parse_args()

    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    problems: list[str] = []
    written: list[Path] = []

    for src_name, out_name, title in PAGES:
        src = DOCS / src_name
        if not src.exists():
            problems.append(f"missing source: {src}")
            continue
        md = src.read_text(encoding="utf-8")

        # Drop the maintainer "Before publishing" note from the published page.
        md = re.sub(r"(?ms)^> \*\*Before publishing:\*\*.*?(?=\n---)", "", md)

        found = sorted(set(PLACEHOLDER.findall(md)))
        if found:
            problems.append(
                f"{src_name}: {len(found)} unresolved placeholder(s): {', '.join(found)}"
            )

        body = markdown_to_html(md)
        page = PAGE.format(
            title=html.escape(title),
            description=html.escape(DESCRIPTIONS[out_name], quote=True),
            css=CSS,
            body=body,
            email=EMAIL,
            generated=date.today().isoformat(),
        )
        dest = out_dir / out_name
        dest.write_text(page, encoding="utf-8")
        written.append(dest)

    for p in written:
        print(f"wrote {p.relative_to(REPO) if p.is_relative_to(REPO) else p} ({p.stat().st_size} bytes)")

    if problems:
        print("\n".join(f"ERROR: {p}" for p in problems), file=sys.stderr)
        if not args.allow_placeholders:
            print(
                "\nGoogle Play rejects a policy without an operator name and address.\n"
                "Fill in the [[...]] markers in docs/legal/*.md, or pass --allow-placeholders\n"
                "to render a draft for review.",
                file=sys.stderr,
            )
            return 1

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
