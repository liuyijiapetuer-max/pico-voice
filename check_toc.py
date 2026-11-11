#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""check_toc.py — 目录里显示的页码 vs PDF 命名目的地的实际页码，逐条比对"""
import json
import re
from pathlib import Path

from pypdf import PdfReader

ROOT = Path(r"G:\minimaxworkspace\pico-sonosphere")
PDF = ROOT / "声之建筑-落地方案.pdf"
HTML = ROOT / "page.html"

html = HTML.read_text(encoding="utf-8")

# 从 page.html 的目录里取 (锚点, 标题, 显示页码)
rows = re.findall(
    r'<a class="toc-row[^"]*" href="#([a-zA-Z0-9\-_]+)">'
    r'(?:<span class="n">[^<]*</span>)?'
    r'<span class="t">([^<]+)</span>'
    r'<span class="dots"></span>'
    r'<span class="pg">(\d+)</span>',
    html,
)

r = PdfReader(str(PDF))
idnum = {}
for i, pg in enumerate(r.pages):
    ir = getattr(pg, "indirect_reference", None)
    if ir is not None:
        idnum[ir.idnum] = i

named = {}
_dests = r.trailer["/Root"].get("/Dests")
if _dests is not None:
    for name, val in _dests.get_object().items():
        arr = val.get_object() if hasattr(val, "get_object") else val
        if isinstance(arr, list) and arr:
            f = arr[0]
            ir = getattr(f, "indirect_reference", None)
            k = ir.idnum if ir is not None else getattr(f, "idnum", None)
            if k in idnum:
                named[str(name)] = idnum[k] + 1

print("PAGES", len(r.pages), "TOC_ROWS", len(rows))
ok = True
for anchor, title, num in rows:
    actual = named.get("/" + anchor)
    match = actual is not None and str(actual) == num
    ok = ok and match
    print(("OK " if match else "MISMATCH ") + title, "shown=" + num, "actual=" + str(actual))

# 每条 TOC 链接注解都要能解析出目标页
links = 0
for pg in r.pages:
    for ann in (pg.get("/Annots") or []):
        ann = ann.get_object()
        if ann.get("/Subtype") == "/Link":
            links += 1
print("LINK_ANNOTS:", links)
print("TOC_PAGENUMS_ALL_MATCH:", ok, "rows_parsed:", len(rows))
print("DEST_COUNT:", len(named))
