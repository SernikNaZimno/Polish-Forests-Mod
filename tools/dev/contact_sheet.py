#!/usr/bin/env python3
"""Contact sheet of PNG screenshots: one compact image to review many frames at once (docs/BRIEF.md, T3).

Usage:
  python tools/dev/contact_sheet.py <dir|file|glob> [...] --out sheet.png [options]

Inputs:
  Any mix of directories (all *.png inside, not recursive), files and glob patterns (quote them in the shell,
  e.g. 'docs/wycinek/baza/*_eye.png'). Files are deduplicated and sorted by name.

Options:
  --out PATH        output PNG (required)
  --cols N          thumbnails per row (default 3)
  --thumb W         thumbnail width in px (default 480); reduced automatically so the sheet stays <= --max-width
  --max-width W     maximum sheet width in px (default 1600)
  --crop x0,y0,x1,y1
                    crop this region (in source pixels) from every input and enlarge it to the thumbnail width
                    with nearest-neighbor scaling; meant for zooming into one file (or the same region of a few)
  --title TEXT      title line at the top of the sheet

Every thumbnail has its file name as a caption. Requires Python 3.10+ and Pillow.

Examples:
  python tools/dev/contact_sheet.py docs/wycinek/baza --out build/sheet.png --title "M2 baseline"
  python tools/dev/contact_sheet.py 'docs/wycinek/baza/*_eye.png' --cols 4 --out build/eye.png
  python tools/dev/contact_sheet.py docs/wycinek/baza/x.png --crop 500,200,780,400 --out build/zoom.png
"""

from __future__ import annotations

import argparse
import glob
import os
import sys

from PIL import Image, ImageDraw, ImageFont

BACKGROUND = (32, 32, 32)
CAPTION_COLOR = (230, 230, 230)
TITLE_COLOR = (255, 255, 255)
GAP = 8


def collect(inputs: list[str]) -> list[str]:
    files: set[str] = set()
    for item in inputs:
        if os.path.isdir(item):
            files.update(glob.glob(os.path.join(item, "*.png")))
        elif os.path.isfile(item):
            files.add(item)
        else:
            matches = glob.glob(item)
            if not matches:
                print(f"warning: no files match {item!r}", file=sys.stderr)
            files.update(m for m in matches if m.lower().endswith(".png") and os.path.isfile(m))
    return sorted(files, key=lambda f: (os.path.basename(f).lower(), f))


def parse_crop(text: str | None) -> tuple[int, int, int, int] | None:
    if text is None:
        return None
    try:
        x0, y0, x1, y1 = (int(v) for v in text.split(","))
    except ValueError:
        raise SystemExit(f"--crop needs x0,y0,x1,y1 as integers, got {text!r}")
    if x1 <= x0 or y1 <= y0:
        raise SystemExit(f"--crop region is empty: {text!r}")
    return x0, y0, x1, y1


def load_font(size: int) -> ImageFont.ImageFont:
    for name in ("DejaVuSans.ttf", "arial.ttf", "segoeui.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def fit_text(draw: ImageDraw.ImageDraw, text: str, font: ImageFont.ImageFont, width: int) -> str:
    if draw.textlength(text, font=font) <= width:
        return text
    # Cut the middle: the start (scale, mode) and the end (camera, variant) of a name say the most.
    head, tail = text[: len(text) // 2], text[len(text) // 2:]
    while len(head) + len(tail) > 2 and draw.textlength(head + "…" + tail, font=font) > width:
        if len(head) > len(tail):
            head = head[:-1]
        else:
            tail = tail[1:]
    return head + "…" + tail


def thumbnail(path: str, width: int, crop: tuple[int, int, int, int] | None) -> Image.Image:
    with Image.open(path) as source:
        image = source.convert("RGB")
    if crop is not None:
        x0, y0, x1, y1 = crop
        if x1 > image.width or y1 > image.height:
            raise SystemExit(f"--crop {crop} is outside {path} ({image.width}x{image.height})")
        image = image.crop(crop)
        resample = Image.Resampling.NEAREST
    else:
        resample = Image.Resampling.LANCZOS
    height = max(1, round(image.height * width / image.width))
    return image.resize((width, height), resample)


def main() -> int:
    parser = argparse.ArgumentParser(description="Contact sheet of PNG screenshots with file-name captions.")
    parser.add_argument("inputs", nargs="+", help="directories, files or glob patterns")
    parser.add_argument("--out", required=True)
    parser.add_argument("--cols", type=int, default=3)
    parser.add_argument("--thumb", type=int, default=480)
    parser.add_argument("--max-width", type=int, default=1600)
    parser.add_argument("--crop")
    parser.add_argument("--title")
    args = parser.parse_args()

    files = collect(args.inputs)
    if not files:
        print("error: no PNG files found", file=sys.stderr)
        return 1
    crop = parse_crop(args.crop)
    cols = max(1, min(args.cols, len(files)))
    thumb_width = min(args.thumb, (args.max_width - GAP * (cols + 1)) // cols)
    if thumb_width < 16:
        raise SystemExit("thumbnails would be narrower than 16 px; use fewer --cols")

    font = load_font(14)
    title_font = load_font(20)
    measure = ImageDraw.Draw(Image.new("RGB", (1, 1)))
    caption_height = measure.textbbox((0, 0), "Ag", font=font)[3] + 6
    title_height = measure.textbbox((0, 0), "Ag", font=title_font)[3] + GAP * 2 if args.title else 0

    thumbs = [(os.path.basename(f), thumbnail(f, thumb_width, crop)) for f in files]
    rows = [thumbs[i:i + cols] for i in range(0, len(thumbs), cols)]
    row_heights = [max(t.height for _, t in row) + caption_height for row in rows]

    sheet_width = GAP + cols * (thumb_width + GAP)
    sheet_height = title_height + GAP + sum(h + GAP for h in row_heights)
    sheet = Image.new("RGB", (sheet_width, sheet_height), BACKGROUND)
    draw = ImageDraw.Draw(sheet)
    if args.title:
        draw.text((GAP, GAP), fit_text(draw, args.title, title_font, sheet_width - 2 * GAP),
                  fill=TITLE_COLOR, font=title_font)

    y = title_height + GAP
    for row, row_height in zip(rows, row_heights):
        for column, (name, image) in enumerate(row):
            x = GAP + column * (thumb_width + GAP)
            sheet.paste(image, (x, y))
            label = name if crop is None else f"{name} [{args.crop}]"
            draw.text((x, y + image.height + 2), fit_text(draw, label, font, thumb_width),
                      fill=CAPTION_COLOR, font=font)
        y += row_height + GAP

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    sheet.save(args.out, optimize=True)
    print(f"{args.out}: {len(files)} images, {sheet_width}x{sheet_height} px")
    return 0


if __name__ == "__main__":
    sys.exit(main())
