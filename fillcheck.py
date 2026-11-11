from PIL import Image
from pathlib import Path
import json
d = Path(r"G:\minimaxworkspace\pico-sonosphere\pdfpages")
pdf_mtime = (Path(r"G:\minimaxworkspace\pico-sonosphere\声之建筑-落地方案.pdf")).stat().st_mtime
rows = []
for f in sorted(d.glob("p*.png")):
    if f.stat().st_mtime < pdf_mtime - 2:
        continue
    im = Image.open(f).convert("L")
    w, h = im.size
    px = im.load()
    # 页脚带在底部 ~7%（渲染缩放 1.6 -> 842.88*1.6=1349px）
    foot = int(h * 0.93)
    bottom = 0
    for y in range(foot - 1, 0, -1):
        row = [px[x, y] for x in range(0, w, 3)]
        if min(row) < 245:
            bottom = y
            break
    content_h = foot
    blank_pct = round((content_h - bottom) / content_h * 100, 1)
    rows.append({"p": f.name, "h": h, "content_bottom": bottom, "blank_pct": blank_pct})
bad = [r for r in rows if r["blank_pct"] > 33]
print("PAGES:", len(rows))
print("MAX_BLANK:", max(r["blank_pct"] for r in rows))
print("OVER_33:", json.dumps(bad, ensure_ascii=False))
print("TOP5:", json.dumps(sorted(rows, key=lambda r: -r["blank_pct"])[:5], ensure_ascii=False))
