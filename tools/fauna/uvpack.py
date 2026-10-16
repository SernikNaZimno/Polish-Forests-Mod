"""Box UV packing of tools/fauna: every cube gets its own rectangle of the texture, with no texel shared by two faces.

A shelf packer: cubes sorted by the height of their box UV rectangle (then width), placed left to right in rows; a row
is as high as its highest rectangle. The result is checked by occupancy(): every texel is used by at most one face, and
every face lies inside the texture.
"""
import numpy as np

from model import face_rects


def pack(model, width=None):
    """Sets cube.uv for every cube of the model; returns the number of texture rows used."""
    width = width or model.texture_size[0]
    cubes = model.cubes()
    order = sorted(range(len(cubes)), key=lambda i: (-cubes[i].box_uv_size()[1], -cubes[i].box_uv_size()[0], i))
    x = y = shelf = 0
    for i in order:
        c = cubes[i]
        w, h = c.box_uv_size()
        if w > width:
            raise ValueError(f'cube {c.part} needs {w} texels, the texture is {width} wide')
        if x + w > width:
            x, y, shelf = 0, y + shelf, 0
        c.uv = (x, y)
        x += w
        shelf = max(shelf, h)
    return y + shelf


def occupancy(model):
    """Texel use count (H x W ints) and a list of problems (faces outside the texture, texels used twice)."""
    tw, th = model.texture_size
    occ = np.zeros((th, tw), int)
    problems = []
    for b in model.bones:
        for c in b.cubes:
            if c.uv is None:
                problems.append(f'{b.name}/{c.part}: no UV')
                continue
            for face, (u, v, w, h) in face_rects(c).items():
                if u < 0 or v < 0 or u + w > tw or v + h > th:
                    problems.append(f'{b.name}/{c.part} {face}: UV ({u}, {v}, {w} x {h}) outside the {tw} x {th} texture')
                    continue
                occ[v:v + h, u:u + w] += 1
    twice = int((occ > 1).sum())
    if twice:
        problems.append(f'{twice} texels are used by more than one face')
    return occ, problems
