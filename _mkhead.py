# -*- coding: utf-8 -*-
"""用官方方案的 CSS + 本方案的封面目录，生成 parts/00-head.html。"""
import io
from pathlib import Path

SRC = Path(r"G:\minimaxworkspace\pico-proposal\parts\00-head.html")
DST = Path(r"G:\minimaxworkspace\pico-sonosphere\parts\00-head.html")
COVER = Path(r"G:\minimaxworkspace\pico-sonosphere\parts\_cover.html")

src = SRC.read_text(encoding="utf-8")
i = src.index("<body>")
head = src[: i + len("<body>")]

# 标题换成这一本
head = head.replace(
    "<title>PICO Space Program 2026 · 四方向参赛方案书</title>",
    "<title>PICO Space Program 2026 · 声之建筑 SONOSPHERE 落地方案</title>",
)

cover = COVER.read_text(encoding="utf-8")
out = head + "\n" + cover
DST.write_text(out, encoding="utf-8", newline="\n")
print("HEAD_OK bytes=", len(out), "cover_bytes=", len(cover))
