"""Software renderer of tools/fauna: the textured model from code, without the game.

The camera is Minecraft's first-person camera (eye position, yaw and pitch in Minecraft degrees, vertical field of view,
image size), so a preview from the same camera as a game test screenshot can be laid over it: silhouette() gives the
mask the fauna game test compares with its screenshot. Faces are drawn with a depth buffer and nearest texel lookup;
the light approximates Minecraft's entity lighting (two fixed directional lights and an ambient term).
"""
import math

import numpy as np

from model import bone_matrices, face_corners, to_world, NORMALS

# Minecraft's entity light directions (Lighting: (0.2, 1, -0.7) and (-0.2, 1, 0.7), normalized), ambient 0.4, diffuse 0.6.
_L0 = np.array([0.2, 1.0, -0.7]) / np.linalg.norm([0.2, 1.0, -0.7])
_L1 = np.array([-0.2, 1.0, 0.7]) / np.linalg.norm([-0.2, 1.0, 0.7])


class Camera:
    def __init__(self, eye, yaw, pitch, fov, width, height):
        self.eye = np.array(eye, float)
        self.width, self.height = width, height
        y, p = math.radians(yaw), math.radians(pitch)
        self.f = np.array([-math.sin(y) * math.cos(p), -math.sin(p), math.cos(y) * math.cos(p)])
        r = np.cross(self.f, [0.0, 1.0, 0.0])
        self.r = r / np.linalg.norm(r)
        self.u = np.cross(self.r, self.f)
        self.t = math.tan(math.radians(fov) / 2.0)
        self.aspect = width / height

    def project(self, p):
        d = np.asarray(p, float) - self.eye
        z = d @ self.f
        x = (d @ self.r) / (z * self.t * self.aspect)
        y = (d @ self.u) / (z * self.t)
        return np.array([(x + 1) * 0.5 * self.width, (1 - y) * 0.5 * self.height, z])


def world_quads(model, texture, pose, entity_pos, yaw, hidden=()):
    """(world corners 4x3, texel uv 4x2, world normal, texture) for every face of every visible bone."""
    ms = bone_matrices(model, pose)
    out = []
    for b in model.bones:
        if b.name in hidden:
            continue
        m = ms[b.name]
        for c in b.cubes:
            for face, corners in face_corners(c).items():
                if c.size[0] == 0 and face in ('left', 'right') or c.size[1] == 0 and face in ('up', 'down') \
                        or c.size[2] == 0 and face in ('front', 'back'):
                    continue
                pts = [to_world((m @ np.array([*p, 1.0]))[:3], entity_pos, yaw) for p, _ in corners]
                uvs = [t for _, t in corners]
                n_model = (m[:3, :3] @ np.array(NORMALS[face], float))
                n_world = to_world(n_model, (0, 0, 0), yaw, lift=0.0) * 16.0
                out.append((np.array(pts), np.array(uvs, float), n_world / np.linalg.norm(n_world)))
    return out


def render(model, texture, camera, pose=None, entity_pos=(0, 0, 0), yaw=0.0, background=(0, 0, 0, 0), hidden=(), lit=True):
    """RGBA image (H x W x 4) of the model; also returns the coverage mask."""
    H, W = camera.height, camera.width
    img = np.zeros((H, W, 4), np.uint8)
    img[:] = background
    depth = np.full((H, W), np.inf)
    th, tw = texture.shape[:2]
    for pts, uvs, n in world_quads(model, texture, pose, entity_pos, yaw, hidden):
        scr = np.array([camera.project(p) for p in pts])
        if (scr[:, 2] <= 0.05).any():
            continue
        shade = 1.0
        if lit:
            shade = min(1.0, 0.4 + 0.6 * (max(0.0, n @ _L0) + max(0.0, n @ _L1)))
        for tri in ((0, 1, 2), (0, 2, 3)):
            _raster(img, depth, scr[list(tri)], uvs[list(tri)], texture, tw, th, shade)
    mask = np.isfinite(depth)
    return img, mask


def _raster(img, depth, s, uv, tex, tw, th, shade):
    H, W = depth.shape
    x0, x1 = int(max(0, math.floor(s[:, 0].min()))), int(min(W - 1, math.ceil(s[:, 0].max())))
    y0, y1 = int(max(0, math.floor(s[:, 1].min()))), int(min(H - 1, math.ceil(s[:, 1].max())))
    if x0 > x1 or y0 > y1:
        return
    (ax, ay), (bx, by), (cx, cy) = s[0, :2], s[1, :2], s[2, :2]
    area = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay)
    if abs(area) < 1e-12:
        return
    ys, xs = np.mgrid[y0:y1 + 1, x0:x1 + 1]
    px, py = xs + 0.5, ys + 0.5
    w0 = ((bx - px) * (cy - py) - (cx - px) * (by - py)) / area
    w1 = ((cx - px) * (ay - py) - (ax - px) * (cy - py)) / area
    w2 = 1.0 - w0 - w1
    inside = (w0 >= -1e-9) & (w1 >= -1e-9) & (w2 >= -1e-9)
    if not inside.any():
        return
    # perspective-correct interpolation
    iz = w0 / s[0, 2] + w1 / s[1, 2] + w2 / s[2, 2]
    z = 1.0 / iz
    u = (w0 * uv[0, 0] / s[0, 2] + w1 * uv[1, 0] / s[1, 2] + w2 * uv[2, 0] / s[2, 2]) * z
    v = (w0 * uv[0, 1] / s[0, 2] + w1 * uv[1, 1] / s[1, 2] + w2 * uv[2, 1] / s[2, 2]) * z
    region = depth[y0:y1 + 1, x0:x1 + 1]
    closer = inside & (z < region)
    if not closer.any():
        return
    ui = np.clip(np.floor(u).astype(int), 0, tw - 1)
    vi = np.clip(np.floor(v).astype(int), 0, th - 1)
    texel = tex[vi, ui]
    opaque = closer & (texel[..., 3] > 0)
    region[opaque] = z[opaque]
    out = img[y0:y1 + 1, x0:x1 + 1]
    col = texel[..., :3].astype(float) * shade
    out[opaque, :3] = np.clip(col[opaque], 0, 255).astype(np.uint8)
    out[opaque, 3] = 255


def silhouette(model, camera, pose=None, entity_pos=(0, 0, 0), yaw=0.0, hidden=()):
    """Coverage mask (H x W bool) of the model seen from the camera."""
    tex = np.full((max(1, model.texture_size[1]), max(1, model.texture_size[0]), 4), 255, np.uint8)
    _, mask = render(model, tex, camera, pose, entity_pos, yaw, hidden=hidden, lit=False)
    return mask
