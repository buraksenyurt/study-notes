"""Bölüm dosyalarına üst/alt navigasyon ekler veya günceller.

Bölüm sırası ve başlıkları README.md'deki tablodan okunur. Script idempotent'tir;
yeni bölüm eklendiğinde README tablosunu güncelleyip tekrar çalıştırmak yeterlidir:

    python tools/update-nav.py
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
README = ROOT / "README.md"
ANCHOR = '<a id="top"></a>'
TOP_START, TOP_END = "<!-- nav:top:start -->", "<!-- nav:top:end -->"
BOT_START, BOT_END = "<!-- nav:bottom:start -->", "<!-- nav:bottom:end -->"
NL = "\r\n"


def read(p: Path) -> str:
    return p.read_text(encoding="utf-8").replace("\r\n", "\n")


def write(p: Path, text: str) -> None:
    p.write_bytes(text.replace("\n", NL).encode("utf-8"))


def chapters():
    rows = re.findall(r"^\|\s*\[(Bölüm\s+(\d+)[^\]]*)\]\((java-chapter-\d+\.md)\)", read(README), re.M)
    return [(title, num, path) for title, num, path in rows]


def ensure_anchor(text: str) -> str:
    if ANCHOR in text:
        return text
    return ANCHOR + "\n\n" + text.lstrip("\n")


def strip_block(text: str, start: str, end: str) -> str:
    return re.sub(rf"\n*{re.escape(start)}.*?{re.escape(end)}\n*", "\n\n", text, flags=re.S)


def top_nav(prev, nxt) -> str:
    parts = []
    if prev:
        parts.append(f"[← Bölüm {prev[1]}]({prev[2]}#top)")
    parts.append("[İçindekiler](README.md#top)")
    if nxt:
        parts.append(f"[Bölüm {nxt[1]} →]({nxt[2]}#top)")
    return f"{TOP_START}\n{' · '.join(parts)}\n{TOP_END}"


def bottom_nav(prev, nxt) -> str:
    p = f"[{prev[0]}]({prev[2]}#top)" if prev else ""
    n = f"[{nxt[0]}]({nxt[2]}#top)" if nxt else ""
    return (
        f"{BOT_START}\n---\n\n"
        "| ← Önceki Bölüm | İçindekiler | Sonraki Bölüm → |\n"
        "| :--- | :---: | ---: |\n"
        f"| {p} | [Ders Notları](README.md#top) | {n} |\n"
        f"{BOT_END}"
    )


def main():
    chs = chapters()
    write(README, ensure_anchor(read(README)))
    for i, ch in enumerate(chs):
        prev = chs[i - 1] if i > 0 else None
        nxt = chs[i + 1] if i + 1 < len(chs) else None
        path = ROOT / ch[2]
        text = read(path)
        text = strip_block(text, TOP_START, TOP_END)
        text = strip_block(text, BOT_START, BOT_END)
        # eski elle eklenmiş navigasyon satırları
        text = re.sub(r"^\[(Önceki|Sonraki) Bölüm\]\(.*\n", "", text, flags=re.M)
        text = ensure_anchor(text)
        # H1'in hemen altına üst navigasyon
        text = re.sub(r"^(# .*)\n+", lambda m: m.group(1) + "\n\n" + top_nav(prev, nxt) + "\n\n", text, count=1, flags=re.M)
        text = text.rstrip() + "\n\n" + bottom_nav(prev, nxt) + "\n"
        write(path, text)
        print(f"ok  {ch[2]}")


if __name__ == "__main__":
    main()
