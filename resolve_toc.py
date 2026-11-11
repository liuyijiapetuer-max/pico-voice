#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""resolve_toc.py — 从 Chromium 生成的命名目的地解析章节页码，回填 page.html，并注入 PDF 书签"""
import json
import re
import shutil
import sys
from pathlib import Path

from pypdf import PdfReader, PdfWriter

ROOT = Path(r"G:\minimaxworkspace\pico-sonosphere")
PDF = ROOT / "声之建筑-落地方案.pdf"
HTML = ROOT / "page.html"

MODE = sys.argv[1] if len(sys.argv) > 1 else "resolve"

OUTLINE = [
    ("01 结论与三处关键修正", "sec-1"),
    ("02 竞品对位：他们赢在第一眼", "sec-2"),
    ("03 作品机制与「显影」开场", "sec-3"),
    ("04 空间音频引擎（官方 API 核对）", "sec-4"),
    ("05 场景 · 交互 · 多窗口", "sec-5"),
    ("06 音频内核与素材", "sec-6"),
    ("07 硬指标 · 排期 · 风险 · 评分", "sec-7"),
    ("08 执行落地与决策", "sec-8"),
    ("附录 A 官方 API 与 Stage 配置速查", "sec-appx"),
]
OUTLINE_SUB = [
    ("1.1 为什么现在做这个方向", "sec-1-1"),
    ("3.2 建筑感的三层", "sec-3-1"),
    ("4.1 API 清单与参数", "sec-4-1"),
    ("8.2 第一周 Day 1–5", "sec-8-1"),
    ("8.4 与方向 D 的关系", "sec-8-2"),
]

# ── 页对象 idnum -> 页索引 ───────────────────────────────────────────
reader = PdfReader(str(PDF))
idnum_to_page = {}
for idx, pg in enumerate(reader.pages):
    ir = getattr(pg, "indirect_reference", None)
    if ir is not None:
        idnum_to_page[ir.idnum] = idx

# ── 命名目的地 -> 目标页 ─────────────────────────────────────────────
dests = reader.trailer["/Root"].get("/Dests")
dest_page = {}
if dests is not None:
    d = dests.get_object()
    for name, val in d.items():
        arr = val.get_object() if hasattr(val, "get_object") else val
        if isinstance(arr, list) and arr:
            first = arr[0]
            ir = getattr(first, "indirect_reference", None)
            if ir is not None and ir.idnum in idnum_to_page:
                dest_page[str(name)] = idnum_to_page[ir.idnum] + 1
            elif hasattr(first, "idnum") and first.idnum in idnum_to_page:
                dest_page[str(name)] = idnum_to_page[first.idnum] + 1

html = HTML.read_text(encoding="utf-8")
ids = re.findall(r"\{\{P:([a-zA-Z0-9\-_]+)\}\}", html)
mapping = {}
for i in ids:
    key = "/" + i
    if key in dest_page:
        mapping[i] = dest_page[key]

print("IDS:", len(ids), "RESOLVED:", len(mapping))
missing = [i for i in ids if i not in mapping]
if missing:
    print("MISSING:", missing)

if MODE == "resolve":
    for i, pg in mapping.items():
        html = html.replace("{{P:%s}}" % i, str(pg))
    left = len(re.findall(r"\{\{P:", html))
    print("PLACEHOLDERS_LEFT:", left)
    HTML.write_text(html, encoding="utf-8")
    (ROOT / "toc_map.json").write_text(json.dumps(mapping, ensure_ascii=False, indent=2), encoding="utf-8")
    print("WROTE:", HTML)
else:
    mp = ROOT / "toc_map.json"
    if mp.exists():
        mapping = json.loads(mp.read_text(encoding="utf-8"))
    print("TOC_MAP:", json.dumps(mapping, ensure_ascii=False))
    out = ROOT / "声之建筑-落地方案.pdf"
    tmp = ROOT / "_outlined.pdf"
    writer = PdfWriter(clone_from=str(out))
    parent = writer.add_outline_item("目录", 2)
    for title, anchor in OUTLINE:
        p = mapping.get(anchor)
        if p:
            writer.add_outline_item(title, p - 1, parent=parent)
    for title, anchor in OUTLINE_SUB:
        p = mapping.get(anchor)
        if p:
            writer.add_outline_item(title, p - 1)
    with open(tmp, "wb") as fh:
        writer.write(fh)
    shutil.move(str(tmp), str(out))
    print("OUTLINE_ADDED items=%d -> %s" % (len(OUTLINE) + len(OUTLINE_SUB) + 1, out))
