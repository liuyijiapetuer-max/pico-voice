#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""verify_pdf.py — A4 方案书 PDF 三项验证 + TOC 链接解析 + 每页留白估算"""
import json
import re
import sys
from pathlib import Path

from pypdf import PdfReader

ROOT = Path(r"G:\minimaxworkspace\pico-sonosphere")
PDF = ROOT / "声之建筑-落地方案.pdf"
HTML = ROOT / "page.html"

A4_W, A4_H = 595.276, 841.89
TOL = 1.0
MARGIN_TOP = 14 * 72 / 25.4      # 14mm -> pt
MARGIN_BOT = 14 * 72 / 25.4
MARGIN_SIDE = 12 * 72 / 25.4

report = {"pdf": str(PDF)}
reader = PdfReader(str(PDF))
pages = reader.pages
report["pages"] = len(pages)

# ── 1. 页面尺寸 ────────────────────────────────────────────────────────
bad_size = []
for i, pg in enumerate(pages, 1):
    w = float(pg.mediabox.width)
    h = float(pg.mediabox.height)
    if abs(w - A4_W) > TOL or abs(h - A4_H) > TOL:
        bad_size.append({"page": i, "w": round(w, 2), "h": round(h, 2)})
report["page_size_bad"] = bad_size
report["page_size_ok"] = not bad_size
report["page_size_sample"] = {
    "w": round(float(pages[0].mediabox.width), 3),
    "h": round(float(pages[0].mediabox.height), 3),
}

# ── 2. 文字可提取（不是栅格化）─────────────────────────────────────────
texts = [(pg.extract_text() or "") for pg in pages]
report["extractable_chars_total"] = sum(len(t) for t in texts)
report["empty_pages"] = [i + 1 for i, t in enumerate(texts) if len(t.strip()) < 20]
report["first_12_lines"] = "\n".join(texts[1].splitlines()[:12]) if len(texts) > 1 else ""

# ── 3. 每页底部留白估算（文本最低点 vs 内容区下沿）────────────────────
def content_bottom(page):
    ys = []

    def visitor(text, cm, tm, font_dict, font_size):
        if text and text.strip():
            try:
                ys.append(tm[5])
            except Exception:
                pass

    try:
        page.extract_text(visitor_text=visitor)
    except Exception:
        return None
    return max(ys) if ys else None


gaps = []
for i, pg in enumerate(pages, 1):
    cb = content_bottom(pg)
    if cb is None:
        gaps.append({"page": i, "bottom_pt": None, "blank_pct": None})
        continue
    # 内容区下沿 = 页高 - 下边距；页脚在其下，故以 (页高 - 下边距 - 6pt) 为基准
    limit = A4_H - MARGIN_BOT - 6
    blank = max(0.0, limit - cb)
    pct = round(blank / (A4_H - MARGIN_TOP - MARGIN_BOT) * 100, 1)
    gaps.append({"page": i, "bottom_pt": round(cb, 1), "blank_pct": pct})
report["page_fill"] = gaps
report["pages_over_33pct_blank"] = [g["page"] for g in gaps if g["blank_pct"] is not None and g["blank_pct"] > 33]

# ── 4. TOC 链接注解 + 目标页解析 ─────────────────────────────────────
# Chromium 对内部锚点生成的是「命名目的地」：/Dest 是一个名字串，
# 真正的目标在 root /Dests 字典里。必须先建名字 -> 页 的映射。
idnum_to_page = {}
for idx, pg in enumerate(pages):
    ir = getattr(pg, "indirect_reference", None)
    if ir is not None:
        idnum_to_page[ir.idnum] = idx

named = {}
_dests = reader.trailer["/Root"].get("/Dests")
if _dests is not None:
    for name, val in _dests.get_object().items():
        arr = val.get_object() if hasattr(val, "get_object") else val
        if isinstance(arr, list) and arr:
            first = arr[0]
            ir = getattr(first, "indirect_reference", None)
            nid = getattr(first, "idnum", None)
            key = ir.idnum if ir is not None else nid
            if key is not None and key in idnum_to_page:
                named[str(name)] = idnum_to_page[key] + 1

toc_links = []
for i, pg in enumerate(pages, 1):
    for ann in (pg.get("/Annots") or []):
        ann = ann.get_object()
        if ann.get("/Subtype") != "/Link":
            continue
        dest = ann.get("/Dest")
        if dest is None:
            act = ann.get("/A")
            if act is not None:
                act = act.get_object()
                dest = act.get("/D")
        if dest is None:
            continue
        if isinstance(dest, (str, bytes)):
            tgt = named.get(str(dest))
        else:
            arr = dest.get_object() if hasattr(dest, "get_object") else dest
            tgt = None
            if isinstance(arr, list) and arr:
                first = arr[0]
                ir = getattr(first, "indirect_reference", None)
                nid = getattr(first, "idnum", None)
                key = ir.idnum if ir is not None else nid
                if key is not None and key in idnum_to_page:
                    tgt = idnum_to_page[key] + 1
        if tgt:
            toc_links.append({"page": i, "target_page": tgt})
report["internal_links_total"] = len(toc_links)
report["internal_links_sample"] = toc_links[:20]
report["named_dests_total"] = len(named)
report["outline_present"] = bool(reader.outline)
try:
    _o = reader.outline
    report["outline_top_count"] = len(_o)
except Exception:
    report["outline_top_count"] = 0

# 解析 TOC 锚点 -> 页码（从 HTML 的 {{P:id}} 反查）
html = HTML.read_text(encoding="utf-8")
ids = re.findall(r"\{\{P:([a-zA-Z0-9\-_]+)\}\}", html)
report["toc_ids_in_html"] = ids

# pypdf 的 /Annots -> destination page，逐个解析并按出现顺序配对
resolved = []
for i, pg in enumerate(pages, 1):
    annots = pg.get("/Annots") or []
    for ann in annots:
        ann = ann.get_object()
        if ann.get("/Subtype") != "/Link":
            continue
        d = ann.get("/Dest")
        if d is None:
            act = ann.get("/A")
            if act is not None:
                act = act.get_object()
                d = act.get("/D")
        if d is None:
            continue
        d = d.get_object() if hasattr(d, "get_object") else d
        try:
            resolved.append(reader.get_destination_page_number(d) + 1)
        except Exception:
            continue

report["resolved_link_count"] = len(resolved)
if len(resolved) >= len(ids):
    mapping = dict(zip(ids, resolved[: len(ids)]))
else:
    mapping = {}
report["toc_page_map"] = mapping

out = ROOT / "verify_report.json"
out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

print("PAGES:", report["pages"])
print("PAGE_SIZE_OK:", report["page_size_ok"], report["page_size_sample"])
print("EXTRACT_CHARS:", report["extractable_chars_total"])
print("EMPTY_PAGES:", report["empty_pages"])
print("INTERNAL_LINKS:", report["internal_links_total"], "RESOLVED:", report["resolved_link_count"])
print("OUTLINE:", report["outline_present"])
print("OVER_33_BLANK:", report["pages_over_33pct_blank"])
print("TOC_PAGE_MAP:", json.dumps(mapping, ensure_ascii=False))
print("REPORT:", out)
