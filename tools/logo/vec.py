"""Tiny vector helper: shapes in a 0..100 unit space, rendered to PIL (supersampled) and SVG."""
import math
from PIL import Image, ImageDraw

def rrect(x0, y0, x1, y1, r, n=24):
    pts = []
    for cx, cy, a0 in ((x1 - r, y0 + r, -90), (x1 - r, y1 - r, 0), (x0 + r, y1 - r, 90), (x0 + r, y0 + r, 180)):
        for i in range(n + 1):
            a = math.radians(a0 + 90 * i / n)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts

def circle_pts(cx, cy, r, n=96):
    return [(cx + r * math.cos(2 * math.pi * i / n), cy + r * math.sin(2 * math.pi * i / n)) for i in range(n)]

def ellipse_pts(cx, cy, a, b, ang, n=180):
    t = math.radians(ang); ca, sa = math.cos(t), math.sin(t)
    return [(cx + a * math.cos(u) * ca - b * math.sin(u) * sa, cy + a * math.cos(u) * sa + b * math.sin(u) * ca)
            for u in (2 * math.pi * i / n for i in range(n))]

class Mark:
    def __init__(self):
        self.items = []  # (kind, data, color)
    def poly(self, pts, color):
        self.items.append(("poly", [tuple(p) for p in pts], color)); return self
    def circle(self, cx, cy, r, color):
        self.items.append(("circle", (cx, cy, r), color)); return self
    def rrect(self, x0, y0, x1, y1, r, color):
        self.items.append(("rrect", (x0, y0, x1, y1, r), color)); return self
    def path(self, pts, d, color):
        """Arbitrary outline: sampled polygon pts (for raster) + exact SVG path data d."""
        self.items.append(("path", (pts, d), color)); return self
    def ellipse(self, cx, cy, a, b, ang, color):
        """Ellipse with semi-axes a (along angle ang, degrees, y-down) and b."""
        self.items.append(("ellipse", (cx, cy, a, b, ang), color)); return self


    def render(self, size, ss=8, bg=(0, 0, 0, 0), box=(0, 0, 100, 100)):
        S = size * ss
        bx0, by0, bx1, by1 = box
        k = S / (bx1 - bx0)
        img = Image.new("RGBA", (S, S), bg)
        d = ImageDraw.Draw(img)
        T = lambda p: ((p[0] - bx0) * k, (p[1] - by0) * k)
        for kind, data, col in self.items:
            if kind == "poly":
                d.polygon([T(p) for p in data], fill=col)
            elif kind == "circle":
                cx, cy, r = data
                x, y = T((cx, cy))
                d.ellipse([x - r * k, y - r * k, x + r * k, y + r * k], fill=col)
            elif kind == "rrect":
                x0, y0, x1, y1, r = data
                d.polygon([T(p) for p in rrect(x0, y0, x1, y1, r)], fill=col)
            elif kind == "ellipse":
                d.polygon([T(p) for p in ellipse_pts(*data)], fill=col)
            elif kind == "path":
                d.polygon([T(p) for p in data[0]], fill=col)
        return img.resize((size, size), Image.LANCZOS) if ss > 1 else img

    def _svg_el(self, kind, data, col, fill):
        f = lambda v: f"{v:.3f}".rstrip("0").rstrip(".")
        op = '' if len(col) < 4 or col[3] in (0, 255) else f' fill-opacity="{col[3]/255:.3f}"'
        if kind == "poly":
            d = "M" + " L".join(f"{f(x)} {f(y)}" for x, y in data) + " Z"
            return f'<path d="{d}" fill="{fill}"{op}/>'
        if kind == "circle":
            cx, cy, r = data
            return f'<circle cx="{f(cx)}" cy="{f(cy)}" r="{f(r)}" fill="{fill}"{op}/>'
        if kind == "rrect":
            x0, y0, x1, y1, r = data
            return f'<rect x="{f(x0)}" y="{f(y0)}" width="{f(x1-x0)}" height="{f(y1-y0)}" rx="{f(r)}" fill="{fill}"{op}/>'
        if kind == "path":
            return f'<path d="{data[1]}" fill="{fill}"{op}/>'
        cx, cy, a, b, ang = data
        rot = f' transform="rotate({f(ang)} {f(cx)} {f(cy)})"' if abs(ang) > 1e-6 else ''
        return f'<ellipse cx="{f(cx)}" cy="{f(cy)}" rx="{f(a)}" ry="{f(b)}"{rot} fill="{fill}"{op}/>'

    def svg(self, box=(0, 0, 100, 100), px=512):
        """Items with alpha 0 are cut-outs: they go into a <mask> applied to everything else."""
        hexc = lambda c: "#%02x%02x%02x" % c[:3]
        bx0, by0, bx1, by1 = box
        g = lambda v: f"{v:g}"
        out = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{g(bx0)} {g(by0)} {g(bx1-bx0)} {g(by1-by0)}" width="{px}" height="{px}">',
               '  <title>Polish Forests</title>']
        cuts = [it for it in self.items if len(it[2]) == 4 and it[2][3] == 0]
        solid = [it for it in self.items if it not in cuts]
        ind = '  '
        if cuts:
            out.append('  <mask id="cut" maskUnits="userSpaceOnUse">')
            out.append(f'    <rect x="{g(bx0)}" y="{g(by0)}" width="{g(bx1-bx0)}" height="{g(by1-by0)}" fill="#fff"/>')
            out += ['    ' + self._svg_el(k, d, c, "#000") for k, d, c in cuts]
            out.append('  </mask>')
            out.append('  <g mask="url(#cut)">'); ind = '    '
        out += [ind + self._svg_el(k, d, c, hexc(c)) for k, d, c in solid]
        if cuts:
            out.append('  </g>')
        out.append("</svg>")
        return "\n".join(out)
