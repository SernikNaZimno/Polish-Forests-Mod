"""Review sheets of tools/fauna (previews from code, without the game), written to docs/wycinek/fauna/:

    roe_deer_sides.png       the 4 texture variants from the front, left, back, right and 3/4 (rows: doe and buck,
                             summer and winter), with a 1 m pole and a 1-block grid
    roe_deer_animations.png  each animation at 6 moments of its cycle (summer doe; buck for alert), from the left side
    roe_deer_textures.png    the 4 textures (8x) and the spawn egg
    axis_gauge.png           the calibration model at rest and in its animation pose
    compare_<variant>.png    (with --game <dir>) the game screenshots of the fauna game test next to the preview from
                             the same camera, and the silhouette difference

Usage: python tools/fauna/sheet.py [--out <dir>] [--game <screenshot dir>]
"""
import argparse
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import animations  # noqa: E402
import generate  # noqa: E402
import render  # noqa: E402

OUT = os.path.join(generate.ROOT, 'docs', 'wycinek', 'fauna')
SKY = (186, 206, 232, 255)
GRASS = (110, 150, 80)
INK = (30, 30, 30)


def ground(img, cam, size=3):
    """1-block grid on the ground under the animal and a red 1 m pole beside it."""
    d = ImageDraw.Draw(img)
    for k in range(-size, size + 1):
        for a, b in (((k, 0, -size), (k, 0, size)), ((-size, 0, k), (size, 0, k))):
            pts = [cam.project(np.array(a) + (np.array(b) - np.array(a)) * s / 24.0) for s in range(25)]
            for pa, pb in zip(pts, pts[1:]):
                if pa[2] > 0.1 and pb[2] > 0.1:
                    d.line([tuple(pa[:2]), tuple(pb[:2])], fill=(120, 140, 110), width=1)
    pa, pb = cam.project((1.2, 0, 0.0)), cam.project((1.2, 1.0, 0.0))
    d.line([tuple(pa[:2]), tuple(pb[:2])], fill=(200, 40, 40), width=3)


def view(model, tex, cam, pose=None, yaw=0.0, hidden=(), grid=True):
    img = Image.new('RGBA', (cam.width, cam.height), SKY)
    if grid:
        ground(img, cam)
    rgba, mask = render.render(model, tex, cam, pose, (0, 0, 0), yaw, hidden=hidden)
    base = np.array(img)
    base[mask] = rgba[mask]
    return Image.fromarray(base)


def orbit_camera(yaw_view, pitch=12.0, dist=3.2, w=320, h=240, target=(0, 0.62, 0), fov=40):
    """A camera looking at the target from the horizontal direction yaw_view (Minecraft yaw of the camera)."""
    y, p = math.radians(yaw_view), math.radians(pitch)
    f = np.array([-math.sin(y) * math.cos(p), -math.sin(p), math.cos(y) * math.cos(p)])
    eye = np.array(target) - dist * f
    return render.Camera(eye, yaw_view, pitch, fov, w, h)


def label(img, text):
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 8 + 6 * len(text), 14], fill=(255, 255, 255, 200))
    d.text((4, 2), text, fill=INK)
    return img


def grid_sheet(cells, cols, title):
    w, h = cells[0].size
    rows = (len(cells) + cols - 1) // cols
    sheet = Image.new('RGBA', (cols * w, rows * h + 22), (40, 42, 46, 255))
    ImageDraw.Draw(sheet).text((6, 5), title, fill=(235, 235, 235))
    for k, c in enumerate(cells):
        sheet.paste(c, ((k % cols) * w, 22 + (k // cols) * h))
    return sheet


def sides(models, out):
    deer, texs = models['deer'], models['deer_textures']
    cells = []
    for sex in generate.SEXES:
        for season in generate.SEASONS:
            hidden = generate.ANTLERS if sex == 'doe' else ()
            for name, cam_yaw in (('front', 180), ('left', 90), ('back', 0), ('right', -90), ('3/4', 140)):
                cam = orbit_camera(cam_yaw)
                cells.append(label(view(deer, texs[(sex, season)], cam, hidden=hidden), f'{sex} {season} {name}'))
    grid_sheet(cells, 5, 'roe deer, preview from code (tools/fauna); grid 1 block, red pole 1 m').save(out)


def animation_sheet(models, out):
    deer, texs, anims = models['deer'], models['deer_textures'], models['deer_anims']['animations']
    cells = []
    for name, anim in anims.items():
        sex = 'buck' if name == 'misc.alert' else 'doe'
        hidden = generate.ANTLERS if sex == 'doe' else ()
        length = anim['animation_length']
        for k in range(6):
            t = length * k / 6 if anim.get('loop') is True else length * k / 5
            pose = animations.pose_at(anim, t)
            cam = orbit_camera(110, pitch=8, w=260, h=200, dist=3.0)
            cells.append(label(view(deer, texs[(sex, 'summer')], cam, pose, hidden=hidden), f'{name} t={t:.2f}s'))
    grid_sheet(cells, 6, 'roe deer animations, preview from code (frames of each cycle)').save(out)


def texture_sheet(models, files, out):
    imgs = [(f'{s}_{season}', models['deer_textures'][(s, season)]) for s in generate.SEXES for season in generate.SEASONS]
    egg = files[f'{generate.ASSETS}/textures/item/roe_deer_spawn_egg.png'][1]
    k = 8
    w = 64 * k
    sheet = Image.new('RGBA', (2 * w + 30, 2 * (64 * k + 20) + 16 * k + 40), (40, 42, 46, 255))
    d = ImageDraw.Draw(sheet)
    for n, (name, img) in enumerate(imgs):
        x, y = (n % 2) * (w + 30), (n // 2) * (64 * k + 20)
        bg = Image.new('RGBA', (w, 64 * k), (70, 72, 78, 255))
        bg.alpha_composite(Image.fromarray(img).resize((w, 64 * k), Image.NEAREST))
        sheet.paste(bg, (x, y + 16))
        d.text((x + 4, y + 2), name, fill=(235, 235, 235))
    y = 2 * (64 * k + 20)
    d.text((4, y + 2), 'roe_deer_spawn_egg (8x)', fill=(235, 235, 235))
    sheet.alpha_composite(Image.fromarray(egg).resize((16 * k, 16 * k), Image.NEAREST), (4, y + 18))
    sheet.save(out)


def gauge_sheet(models, files, out):
    gauge = models['gauge']
    tex = files[f'{generate.PROBE_ASSETS}/textures/entity/axis_gauge.png'][1]
    import species
    cells = []
    for title, pose in (('rest', None), ('calib.pose', species.AXIS_GAUGE_ANIM)):
        for cam_yaw in (150, 210):
            cam = orbit_camera(cam_yaw, pitch=20, dist=3.0, target=(0, 0.6, 0))
            cells.append(label(view(gauge, tex, cam, pose), f'axis gauge {title}, camera yaw {cam_yaw}'))
    grid_sheet(cells, 4, 'axis gauge (game test only): red pitch +30 X, green yaw +30 Y, blue roll +30 Z, yellow chain, '
                         'magenta animated; white = left (+X), dark = front (-Z)').save(out)


def compare(models, game_dir, out_dir):
    """Game screenshots of the 4-side views (fauna_roe_deer_<sex>_<season>_<view>.png) next to the preview."""
    deer, texs = models['deer'], models['deer_textures']
    cam = generate.view_camera()
    for sex in generate.SEXES:
        for season in generate.SEASONS:
            hidden = generate.ANTLERS if sex == 'doe' else ()
            cells = []
            for name, yaw in generate.VIEWS:
                shot = os.path.join(game_dir, f'fauna_roe_deer_{sex}_{season}_{name}.png')
                if not os.path.exists(shot):
                    continue
                game = Image.open(shot).convert('RGBA')
                pv, mask = render.render(deer, texs[(sex, season)], cam, None, (0, 0, 0), yaw, hidden=hidden)
                base = np.array(game).copy()
                g = np.array(game)[..., :3].astype(int)
                gmask = ~((g[..., 1] > g[..., 0] * 1.15) & (g[..., 1] > g[..., 2] * 1.15))
                prev = base.copy()
                prev[mask] = pv[mask]
                diff = np.zeros_like(base)
                diff[..., 3] = 255
                diff[gmask & mask] = (200, 200, 200, 255)
                diff[gmask & ~mask] = (230, 60, 60, 255)
                diff[~gmask & mask] = (60, 110, 230, 255)
                crop = _crop(gmask | mask)
                for img, t in ((game, 'game'), (Image.fromarray(prev), 'preview'), (Image.fromarray(diff), 'difference')):
                    cells.append(label(img.crop(crop).resize((300, 240), Image.NEAREST), f'{name}: {t}'))
            if cells:
                grid_sheet(cells, 3, f'roe deer {sex} {season}: game (fauna game test) / preview from code / silhouettes '
                                     f'(grey both, red only game, blue only preview)').save(
                    os.path.join(out_dir, f'compare_{sex}_{season}.png'))


def _crop(mask):
    ys, xs = np.nonzero(mask)
    cx, cy = (xs.min() + xs.max()) // 2, (ys.min() + ys.max()) // 2
    w = max(xs.max() - xs.min(), int((ys.max() - ys.min()) * 1.25)) + 40
    h = int(w * 0.8)
    return (int(cx - w // 2), int(cy - h // 2), int(cx + w // 2), int(cy + h // 2))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--out', default=OUT)
    ap.add_argument('--game', help='directory with the fauna game test screenshots')
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    files, models = generate.build_all()
    sides(models, os.path.join(a.out, 'roe_deer_sides.png'))
    animation_sheet(models, os.path.join(a.out, 'roe_deer_animations.png'))
    texture_sheet(models, files, os.path.join(a.out, 'roe_deer_textures.png'))
    gauge_sheet(models, files, os.path.join(a.out, 'axis_gauge.png'))
    if a.game:
        compare(models, a.game, a.out)
    print('fauna sheets written to', a.out)


if __name__ == '__main__':
    main()
