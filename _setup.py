# -*- coding: utf-8 -*-
"""建立 声之建筑 PDF 工程目录：复制 vendor 与工具脚本，并把路径常量改写为新根目录。"""
import os
import shutil
from pathlib import Path

SRC = Path(r"G:\minimaxworkspace\pico-proposal")
DST = Path(r"G:\minimaxworkspace\pico-sonosphere")

OLD_ROOT = r"G:\minimaxworkspace\pico-proposal"
NEW_ROOT = r"G:\minimaxworkspace\pico-sonosphere"
OLD_PDF = "PICO-Space-Program-2026-四方向方案书.pdf"
NEW_PDF = "声之建筑-落地方案.pdf"

(DST / "parts").mkdir(parents=True, exist_ok=True)
(DST / "vendor").mkdir(parents=True, exist_ok=True)

for v in ("chart.umd.js", "pdf.min.mjs", "pdf.worker.min.mjs"):
    shutil.copyfile(SRC / "vendor" / v, DST / "vendor" / v)

for f in ("verify_pdf.py", "fillcheck.py", "check_toc.py", "resolve_toc.py", "pdfshot.cjs"):
    t = (SRC / f).read_text(encoding="utf-8")
    t = t.replace(OLD_ROOT, NEW_ROOT).replace(OLD_PDF, NEW_PDF)
    (DST / f).write_text(t, encoding="utf-8", newline="\n")

shutil.copyfile(SRC / "pdfview.html", DST / "pdfview.html")

for p in sorted(DST.rglob("*")):
    if p.is_file():
        print("  ", p.relative_to(DST), p.stat().st_size)
print("SETUP_OK")
