import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from PIL import Image, ImageDraw, ImageFont
import numpy as np
import mark as mk
from mark import build, DEEP, LEAF, BARK, CREAM
from vec import Mark

HERE = os.path.dirname(os.path.abspath(__file__))
FINAL = os.path.join(HERE, "final")
os.makedirs(FINAL, exist_ok=True)
FONT = "C:/Windows/Fonts/bahnschrift.ttf"

LIGHT_BG = (247, 245, 238, 255)
DARK_BG = (32, 33, 36, 255)
INK_LIGHT = DEEP                    # wordmark on light
TAG_LIGHT = (78, 124, 46, 255)      # tagline on light
INK_DARK = CREAM                    # wordmark on dark
TAG_DARK = LEAF                     # tagline on dark

ICON_BOX = (3, 3, 97, 97)           # crop so the badge nearly fills the icon

# ---------------------------------------------------------------- icons
def icon(size):
    if size == 32:
        return icon32()
    return build(detail="full").render(size, ss=8, box=ICON_BOX)


# Round 2: both small sizes are hand-drawn pixel art laid over an anti-aliased badge.
# L leaf, l half-tone leaf edge, D spruce cut / badge, B pine bark, '.' = keep badge pixel.
# 32 px: only the right half (columns 16..25) is drawn; it is mirrored to x' = 31 - x so the
# 2 px leader sits exactly on columns 15-16. Geometry follows to_px() of the vector mark:
# tiers start at rows 7/11/16/20 and are drawn as overlapping 2-3 px runs (so each reads as one
# solid stroke, not a row of dashes); lobe notches at rows 11, 16 and 20.
HALF32 = {
    2: "Ll", 3: "LLl", 4: "DLL", 5: "DLLL", 6: "DLLLLl",
    7: "DDDLLLL", 8: "DLDDLLL", 9: "DLLLLLL", 10: "DLLLLl",            # lobe 1, tier 1
    11: "DDDLLL", 12: "DLDDDLLl", 13: "DLLLDDLLL", 14: "DLLLLLLLL", 15: "DLLLLLLl",  # lobe 2, tier 2
    16: "DDDLLLl", 17: "DLDDDLLLL", 18: "DLLLDDLLL", 19: "DLLLLLLl",    # lobe 3, tier 3
    20: "DDDLLL", 21: "DLDDDLl", 22: "DLLLLLl", 23: "LLLLl",            # lobe 4, tier 4
    24: "BLl", 25: "B", 26: "B", 27: "B", 28: "B", 29: "B",
}


def mix(a, b, t):
    return tuple(int(round(a[i] * (1 - t) + b[i] * t)) for i in range(3)) + (255,)


CMAP = {"L": LEAF, "l": mix(DEEP, LEAF, 0.5), "D": DEEP, "B": BARK}


def _badge(size):
    return Mark().rrect(4, 4, 96, 96, 22, DEEP).render(size, ss=8, box=ICON_BOX)


def icon32():
    img = _badge(32)
    px = img.load()
    for y, row in HALF32.items():
        for i, c in enumerate(row):
            if c in CMAP:
                px[16 + i, y] = CMAP[c]
                px[15 - i, y] = CMAP[c]
    return img


# 16 px: leader on columns 7-8, two drooping tiers drawn as clean 1 px diagonals.
PIX16 = [
    "................",
    ".......ll.......",
    "......lLLl......",
    ".....lLddLl.....",
    "...llLLDDLLll...",
    "..lLLLDDDDLLLl..",
    "..LLLDLDDLDLLL..",
    "..lLDLLDDLLDLl..",
    "...lLLLDDLLLl...",
    "..lLLLDDDDLLLl..",
    "..LLLDLDDLDLLL..",
    "..lLDLLDDLLDLl..",
    "....lLLLLLLl....",
    ".......BB.......",
    ".......BB.......",
    "................",
]


def icon16():
    base = _badge(16)
    px = base.load()
    cmap = dict(CMAP, l=mix(DEEP, LEAF, 0.55), d=mix(DEEP, LEAF, 0.28))   # d: tapered leader tip
    for y, row in enumerate(PIX16):
        for x, c in enumerate(row):
            if c in cmap:
                px[x, y] = cmap[c]
    return base

# ---------------------------------------------------------------- wordmark
KERN = {  # manual pair kerning in em (Bahnschrift Bold)
    ("F", "o"): -0.045, ("P", "o"): -0.010, ("o", "r"): -0.005, ("r", "e"): -0.020,
    ("e", "s"): -0.005, ("s", "t"): -0.010, ("t", "s"): -0.005, (" ", "F"): -0.010,
    ("l", "i"): 0.005, ("i", "s"): 0.0,
}

def font(size, var):
    f = ImageFont.truetype(FONT, size)
    f.set_variation_by_name(var)
    return f

def text_img(text, size, color, var="Bold", tracking=0.0, kern=None, space=1.0, ss=4, colors=None):
    """Glyph-by-glyph layout with manual kerning; returns tight RGBA image."""
    kern = kern or {}
    f = font(size * ss, var)
    em = size * ss
    xs, x = [], 0.0
    for i, ch in enumerate(text):
        if i:
            x += kern.get((text[i - 1], ch), 0.0) * em + tracking * em
        xs.append(x)
        w = f.getlength(ch)
        if ch == " ":
            w *= space
        x += w
    asc, desc = f.getmetrics()
    W, H = int(x + em), int(asc + desc + em * 0.6)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for ch, cx in zip(text, xs):
        d.text((cx + em * 0.1, em * 0.4), ch, font=f, fill=(colors or {}).get(ch, color))
    img = img.crop(img.getbbox())
    return img.resize((max(1, img.width // ss), max(1, img.height // ss)), Image.LANCZOS)

WORD = "Polish Forests"
TAG = "LASY • RZEKI • GÓRY"

TAG_WORDS = ["LASY", "RZEKI", "GÓRY"]
TAG_TRACK = 0.30


TAG_GAP = 1.70          # word -> word distance (em), the bark square sits in the middle


def _tag_layout(size, color, track, gap_px):
    words = [text_img(w, size, color, "SemiBold", tracking=track) for w in TAG_WORDS]
    return words, sum(w.width for w in words) + gap_px * (len(words) - 1)


def tag_img(size, color, width=None, mode="natural"):
    """Tagline. mode='natural': fixed tracking TAG_TRACK and TAG_GAP gaps (width ignored);
    mode='track': fixed TAG_GAP gaps, tracking solved so the line is exactly `width` px.
    Returns (image, accent_px): accent_px = how far the Ó accent rises above cap height."""
    gap = TAG_GAP * size
    track = TAG_TRACK
    words, nat = _tag_layout(size, color, track, gap)
    if mode == "track" and width:
        lo, hi = 0.0, 1.2
        for _ in range(22):
            track = (lo + hi) / 2
            words, nat = _tag_layout(size, color, track, gap)
            lo, hi = (track, hi) if nat < width else (lo, track)
        gap = (width - sum(w.width for w in words)) / (len(words) - 1)
    cap_h = text_img("LASY", size, color, "SemiBold").height
    H = max(w.height for w in words)
    W = int(round(sum(w.width for w in words) + gap * (len(words) - 1)))
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    x = 0.0
    dot = max(2, round(size * 0.20))                     # square separator in pine-bark orange
    for i, w in enumerate(words):
        img.alpha_composite(w, (int(round(x)), H - w.height))
        x += w.width
        if i < len(words) - 1:
            cx, cy = x + gap / 2, H - cap_h / 2
            ImageDraw.Draw(img).rectangle([cx - dot / 2, cy - dot / 2, cx + dot / 2 - 1, cy + dot / 2 - 1], fill=BARK)
            x += gap
    return img, H - cap_h


TAG_SCALE, TAG_MODE = 0.36, "natural"


def wordmark(size, ink, tagc, tag_scale=None, mode=None):
    w = text_img(WORD, size, ink, "Bold", tracking=-0.005, kern=KERN, space=0.92)
    t, acc = tag_img(int(size * (tag_scale or TAG_SCALE)), tagc, w.width, mode or TAG_MODE)
    return w, t, acc


def _mark(ms, knock):
    return build(knockout=knock).render(ms, ss=4, box=ICON_BOX)


def logo_horizontal(dark=False, knock=None):
    """Badge mark on light; on dark the badge (#153A26 on #202124) all but vanishes, so the
    dark lockup uses the badge-less knockout mark (spruce cut to transparency)."""
    knock = dark if knock is None else knock
    W, H = 1600, 600
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ink, tagc = (INK_DARK, TAG_DARK) if dark else (INK_LIGHT, TAG_LIGHT)
    if knock:
        mimg = _mark(470, True)
        mimg = mimg.crop(mimg.getbbox())                 # tree silhouette only
        gap_mt = 64
    else:
        mimg = _mark(420, False)
        gap_mt = 70                                      # mark -> text
    mw, mh = mimg.size
    size = 220                                           # size fitted on the badge lockup, so
    while True:                                          # light and dark use the same type size
        w, t, acc = wordmark(size, ink, tagc)
        if 420 + 70 + w.width <= W - 2 * 80:
            break
        size -= 2
    total = mw + gap_mt + w.width
    mx = (W - total) // 2                                # centre the whole lockup
    my = (H - mh) // 2
    img.alpha_composite(mimg, (mx, my))
    tx = mx + mw + gap_mt
    g = int(size * 0.24)                                 # wordmark baseline -> tagline cap top
    block = w.height + g + (t.height - acc)
    y0 = (H - block) // 2
    img.alpha_composite(w, (tx, y0))
    img.alpha_composite(t, (tx, y0 + w.height + g - acc))
    return img


def logo_vertical(dark=False, knock=None):
    knock = dark if knock is None else knock
    W = H = 1024
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ms = 480
    ink, tagc = (INK_DARK, TAG_DARK) if dark else (INK_LIGHT, TAG_LIGHT)
    size = 220
    while True:
        w, t, acc = wordmark(size, ink, tagc)
        if w.width <= 840:
            break
        size -= 2
    g1, g2 = 64, int(size * 0.24)
    mimg = _mark(ms, knock)
    if knock:
        mimg = mimg.crop(mimg.getbbox())
        g1 = 56
    block = mimg.height + g1 + w.height + g2 + (t.height - acc)
    y = (H - block) // 2
    img.alpha_composite(mimg, ((W - mimg.width) // 2, y)); y += mimg.height + g1
    img.alpha_composite(w, ((W - w.width) // 2, y)); y += w.height + g2
    img.alpha_composite(t, ((W - t.width) // 2, y - acc))
    return img

# ---------------------------------------------------------------- preview
def preview(icons, lh, ld, lv, lvd):
    W = 1800
    sheet = Image.new("RGBA", (W, 2000), LIGHT_BG)
    d = ImageDraw.Draw(sheet)
    lab = font(22, "SemiBold")
    # icon strips: light and dark
    for row, bg in enumerate((LIGHT_BG, DARK_BG)):
        y0 = 30 + row * 600
        d.rectangle([0, y0 - 30, W, y0 + 570], fill=bg)
        x = 40
        for s in (512, 128, 64, 32, 16):
            sheet.alpha_composite(icons[s], (x, y0 + 10))
            d.text((x, y0 + 530 if s == 512 else y0 + s + 20), f"{s}", font=lab, fill=(120, 120, 120))
            x += s + 50
        # nearest-neighbour zooms of the hand-hinted sizes, under the strip
        zx = 602
        for s_, k in ((32, 8), (16, 16)):
            z = icons[s_].resize((s_ * k,) * 2, Image.NEAREST)
            sheet.alpha_composite(z, (zx, y0 + 200))
            d.text((zx, y0 + 470), f"{s_}px  ({k}x zoom)", font=lab, fill=(120, 120, 120))
            zx += z.width + 50
        # 2x of 32 (hi-dpi look)
        sheet.alpha_composite(icons[32].resize((64, 64), Image.NEAREST), (zx, y0 + 200))
        d.text((zx, y0 + 270), "32px @2x", font=lab, fill=(120, 120, 120))
    y = 1200
    half = lh.resize((800, 300), Image.LANCZOS)
    halfd = ld.resize((800, 300), Image.LANCZOS)
    d.rectangle([0, y, 900, y + 400], fill=LIGHT_BG)
    d.rectangle([900, y, W, y + 400], fill=DARK_BG)
    sheet.alpha_composite(half, (50, y + 50))
    sheet.alpha_composite(halfd, (950, y + 50))
    y += 400
    d.rectangle([0, y, W, y + 400], fill=(235, 230, 214, 255))
    v = lv.resize((380, 380), Image.LANCZOS)
    sheet.alpha_composite(v, (30, y + 10))
    d.rectangle([440, y, 860, y + 400], fill=DARK_BG)
    sheet.alpha_composite(lvd.resize((380, 380), Image.LANCZOS), (460, y + 10))
    # mod-menu style mock: icon 32 next to name on dark list
    d.rectangle([900, y + 40, 1770, y + 360], fill=(24, 24, 24, 255))
    f1, f2 = font(28, "SemiBold"), font(20, "Regular")
    for i, (name, ic) in enumerate((("Polish Forests", icons[32]), ("Polish Forests", icons[64]))):
        yy = y + 70 + i * 140
        sheet.alpha_composite(ic, (930, yy))
        d.text((930 + ic.width + 20, yy), name, font=f1, fill=(255, 255, 255))
        d.text((930 + ic.width + 20, yy + 36), "Realistic Polish forests & landscapes", font=f2, fill=(170, 170, 170))
    return sheet

def main():
    sizes = {512: icon(512), 128: icon(128), 64: icon(64), 32: icon(32), 16: icon16()}
    sizes[512].save(os.path.join(FINAL, "icon.png"))
    for s in (128, 64, 32, 16):
        sizes[s].save(os.path.join(FINAL, f"icon_{s}.png"))
    with open(os.path.join(FINAL, "mark.svg"), "w", encoding="utf-8") as fh:
        fh.write(build().svg(box=ICON_BOX))
    with open(os.path.join(FINAL, "mark_knockout.svg"), "w", encoding="utf-8") as fh:
        fh.write(build(knockout=True).svg(box=ICON_BOX))
    build(knockout=True).render(512, ss=8, box=ICON_BOX).save(os.path.join(FINAL, "mark_knockout.png"))
    lh, ld, lv = logo_horizontal(False), logo_horizontal(True), logo_vertical(False)
    lh.save(os.path.join(FINAL, "logo_horizontal.png"))
    ld.save(os.path.join(FINAL, "logo_horizontal_dark.png"))
    lv.save(os.path.join(FINAL, "logo_vertical.png"))
    lvd = logo_vertical(True)
    lvd.save(os.path.join(FINAL, "logo_vertical_dark.png"))
    preview(sizes, lh, ld, lv, lvd).save(os.path.join(FINAL, "preview.png"))
    print("done")

if __name__ == "__main__":
    main()
