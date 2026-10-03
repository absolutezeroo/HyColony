"""Casts rays against a model's boxes, for bake.py: how far a ray goes before a box stops it, how much of the
hemisphere above a point the boxes hide (ambient occlusion), and whether a surface goes on past a face border."""

import math

from models import placed
from vectors import add, cross, rotate, scale, sub, unit

AO_DISTANCE = 10.0
# Rays start this far off the face, so that they never hit their own box.
RAY_START = 0.05
# A floor under grounded models (blocks): a wide, 100 units thick slab below y = 0 that occludes like any box.
FLOOR = ((0.0, -50.0, 0.0), (0.0, 0.0, 0.0, 1.0), (1000.0, 50.0, 1000.0), 1e9)
# Hemisphere directions around a normal: (elevation, azimuth) in degrees, plus the normal itself.
RAYS = [(90, 0)] + [(e, a) for e in (25, 55) for a in range(0, 360, 60)]
# The quaternion of an unturned box.
UNTURNED = (0.0, 0.0, 0.0, 1.0)


def covered(point, normal, reach, boxes):
    """Whether the surface at point goes on past its face border: just under the surface, at point + reach (half a
    texel past the border), lies inside a box. A box beside it (a seam), against it (a concave corner) or the floor
    under a block's side all cover it."""
    probe = sub(add(point, reach), scale(normal, RAY_START))
    return any(inside(probe, box) for box in boxes)


def inside(point, box):
    centre, inverse, half, _ = box
    local = rotate(inverse, sub(point, centre))
    return all(abs(local[k]) <= half[k] for k in range(3))


def occlusion(point, normal, boxes):
    """0 (open) to 1 (enclosed): how much of the hemisphere above the point other boxes hide, nearer counting more."""
    nx, ny, nz = normal
    tx, ty, tz = unit(cross(normal, (0.0, 1.0, 0.0) if abs(ny) < 0.9 else (1.0, 0.0, 0.0)))
    bx, by, bz = cross(normal, (tx, ty, tz))
    start = add(point, scale(normal, RAY_START))
    total = 0.0
    for weight, along_t, along_b in RAY_TERMS:
        direction = (nx * weight + (tx * along_t + bx * along_b), ny * weight + (ty * along_t + by * along_b),
                     nz * weight + (tz * along_t + bz * along_b))
        t = hit(start, direction, boxes, AO_DISTANCE)
        total += weight * (0.0 if t is None else 1.0 - t / AO_DISTANCE)
    return total / RAY_WEIGHT


def ray_terms():
    """Per ray of RAYS, computed once: (sin elevation, its weight and its share along the normal; its shares along the
    tangent and the bitangent); and the sum of the weights."""
    terms = []
    for elevation, azimuth in RAYS:
        e, a = math.radians(elevation), math.radians(azimuth)
        terms.append((math.sin(e), math.cos(e) * math.cos(a), math.cos(e) * math.sin(a)))
    weight = 0.0
    for term in terms:
        weight += term[0]
    return terms, weight


RAY_TERMS, RAY_WEIGHT = ray_terms()


def world_boxes(nodes, skipped=frozenset()):
    """(centre, inverse rotation, half extents, bounding radius) of every box, in world space, but those of the nodes
    named in skipped."""
    for n, position, rotation in placed(nodes):
        shape = n["shape"]
        if shape["type"] != "box" or n["name"] in skipped:
            continue
        offset, stretch = (tuple(shape[k][a] for a in "xyz") for k in ("offset", "stretch"))
        half = tuple(abs(shape["settings"]["size"][a] * k) / 2 for a, k in zip("xyz", stretch))
        centre = add(position, rotate(rotation, offset))
        inverse = (-rotation[0], -rotation[1], -rotation[2], rotation[3])
        yield centre, inverse, half, math.sqrt(sum(c * c for c in half))


def hit(origin, direction, boxes, reach):
    """Distance along the ray to the nearest box within reach, None if none (slab test in each box's frame)."""
    nearest = None
    ox, oy, oz = origin
    dx, dy, dz = direction
    for centre, inverse, half, radius in boxes:
        # The bounding sphere first: most boxes are behind, past reach or beside the ray. sum, as dot computes it.
        cx, cy, cz = centre
        tx, ty, tz = cx - ox, cy - oy, cz - oz
        along = sum((tx * dx, ty * dy, tz * dz))
        if along < -radius or along > reach + radius:
            continue
        if sum((tx * tx, ty * ty, tz * tz)) - along * along > radius * radius:
            continue
        o = (ox - cx, oy - cy, oz - cz)
        d = direction
        # An unturned box (most of them, and the floor) needs no turn: the identity gives the same values.
        if inverse != UNTURNED:
            o, d = rotate(inverse, o), rotate(inverse, d)
        t0, t1 = 0.0, reach
        for k in range(3):
            if abs(d[k]) < 1e-9:
                if abs(o[k]) > half[k]:
                    break
                continue
            a, b = (-half[k] - o[k]) / d[k], (half[k] - o[k]) / d[k]
            t0, t1 = max(t0, min(a, b)), min(t1, max(a, b))
            if t0 > t1:
                break
        else:
            if t0 > 1e-4 and (nearest is None or t0 < nearest):
                nearest = t0
    return nearest
