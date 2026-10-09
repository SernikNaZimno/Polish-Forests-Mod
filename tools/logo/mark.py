"""Polish Forests mark: oak leaf whose venation is a spruce, standing on a pine-bark trunk.

Round 1 refinements over round 0 (same concept, tighter drawing):
  * lobes are true <ellipse> primitives (exact in SVG, no 72-gon approximations)
  * auricles tucked against the lowest lobe so they read as Q. robur "ears", not loose bubbles
  * spruce tiers keep a minimum stroke (blunt, round-capped tips) -> survive 64/128 px
  * leader (midrib) has round ends instead of a hairline spike / square foot
  * shorter, sturdier pine-bark trunk; tree scaled up and optically centred in the badge
(An alternative circle-arc leaf construction was tried and rejected: sketch_arcleaf.py.)

Round 2: auricles removed (at every size they read as loose bubbles / a frilly collar),
leaf base tapers straight into the trunk, leader shortened so its round foot keeps a clear
band of leaf below it. build(knockout=True) gives the badge-less mark with the spruce cut
out to transparency (for dark backgrounds and single-colour use).
"""
import sys, os, math
sys.path.insert(0, os.path.dirname(__file__))
from vec import Mark

DEEP = (21, 58, 38, 255)      # badge: deep forest green  #153A26
LEAF = (138, 190, 72, 255)    # young oak leaf green      #8ABE48
BARK = (214, 106, 46, 255)    # Scots pine bark           #D66A2E
CREAM = (246, 240, 222, 255)  #F6F0DE

# --- leaf geometry in a 0..100 design space (axis x = 50) ---------------------------------
# lobe pairs: (dx, y, a, b, tilt) ; tilt = degrees above horizontal for the right lobe
LOBES = [(10.0, 27.0, 8.6, 6.4, 38),
         (14.6, 40.0, 10.2, 7.0, 22),
         (14.4, 53.4, 9.6, 6.8, 9),
         (10.4, 64.4, 7.6, 5.7, -6)]      # round 2: auricles dropped (read as bubbles)
TERMINAL = (0.0, 17.5, 7.2, 8.6)
BODY = [(0, 14), (6.8, 21), (11.0, 33.5), (12.4, 47), (11.6, 60), (8.4, 68.0), (3.4, 72.4), (0, 72.6)]
# spruce: leader + four drooping tiers (y at midrib, half span, droop, w start, w tip)
LEADER = (67.0, 14.2, 4.6, 1.5)           # y bottom, y top, w bottom, w top
TIERS = [(24.2, 8.0, 5.8, 3.3, 1.9),
         (35.6, 13.4, 8.4, 3.7, 2.0),
         (49.0, 14.2, 8.4, 3.9, 2.1),
         (61.4, 10.4, 6.0, 3.9, 2.1)]
TRUNK = (70.0, 86.5, 5.8, 7.6)            # y top, y bottom, w top, w bottom
SCALE = 1.045                             # whole tree, about (50, PIVOT_Y)
PIVOT_Y = 50.0
DY = 2.6                                  # optical centring (heavy crown sits a bit low)
CX = 50.0


def taper(p0, p1, w0, w1, cap=True):
    """Quad from p0 (width w0) to p1 (width w1); cap=True adds a round end at p1."""
    (x0, y0), (x1, y1) = p0, p1
    dx, dy = x1 - x0, y1 - y0
    L = math.hypot(dx, dy); ux, uy = dx / L, dy / L; nx, ny = -uy, ux
    pts = [(x0 + nx * w0 / 2, y0 + ny * w0 / 2), (x1 + nx * w1 / 2, y1 + ny * w1 / 2)]
    if cap:
        r = w1 / 2
        for k in range(1, 12):
            a = math.pi * k / 12
            pts.append((x1 + (nx * math.cos(a) + ux * math.sin(a)) * r,
                        y1 + (ny * math.cos(a) + uy * math.sin(a)) * r))
    pts += [(x1 - nx * w1 / 2, y1 - ny * w1 / 2), (x0 - nx * w0 / 2, y0 - ny * w0 / 2)]
    return pts


def build(badge=True, detail="full", leaf=LEAF, deep=DEEP, bark=BARK, scale=SCALE, dy=DY, knockout=False):
    """detail: 'full' (spruce cut) or 'none' (silhouette only, used as base for hinted sizes)."""
    if knockout:
        badge, deep = False, (0, 0, 0, 0)
    m = Mark()
    if badge:
        m.rrect(4, 4, 96, 96, 22, deep)
    s = scale
    P = lambda x, y: (CX + (x - CX) * s, PIVOT_Y + (y - PIVOT_Y) * s + dy)
    def poly(pts, col):
        m.poly([P(x, y) for x, y in pts], col)
    def ell(x, y, a, b, ang, col):
        px, py = P(x, y)
        m.ellipse(px, py, a * s, b * s, ang, col)

    yt, yb, wt, wb = TRUNK
    poly([(CX - wt / 2, yt), (CX + wt / 2, yt), (CX + wb / 2, yb), (CX - wb / 2, yb)], bark)
    tx, ty, ta, tb = TERMINAL
    ell(CX + tx, ty, ta, tb, 0, leaf)
    for dx, y, a, b, tilt in LOBES:
        ell(CX + dx, y, a, b, -tilt, leaf)
        ell(CX - dx, y, a, b, 180 + tilt, leaf)
    poly([(CX + x, y) for x, y in BODY] + [(CX - x, y) for x, y in reversed(BODY[1:-1])], leaf)
    if detail == "full":
        y0, y1, w0, w1 = LEADER
        poly(taper((CX, y0), (CX, y1), w0, w1), deep)
        bx, by = P(CX, y0)
        m.circle(bx, by, w0 / 2 * s, deep)          # round foot of the leader
        for y, half, drop, w0, w1 in TIERS:
            for sg in (1, -1):
                poly(taper((CX, y), (CX + sg * half, y + drop), w0, w1), deep)
    return m


def to_px(x, y, size, box=(3, 3, 97, 97), scale=SCALE, dy=DY):
    """Design-space point -> pixel coordinate in an icon of `size` px (for hand hinting)."""
    X, Y = CX + (x - CX) * scale, PIVOT_Y + (y - PIVOT_Y) * scale + dy
    k = size / (box[2] - box[0])
    return (X - box[0]) * k, (Y - box[1]) * k
