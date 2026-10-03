"""Bakes light into a painted model texture, texel by texel, from the model's own geometry
(docs/research/hytale-models.md § 4): Hytale paints its shadows, occlusion and highlights into textures; this does it
from the boxes themselves.

For every texel of every face island it finds the point and the normal on the model (models.placed: Hytale's child
placement), then darkens it by ambient occlusion (rays into the hemisphere, against every other box and, for a block,
a floor: rays.py), by the shadow cast from a light above the front left, lights the bevel of the island's border rings
where it turns towards that light (with wear: lighter chips) and shades it elsewhere, grimes a block's foot, adds a
soft world-space colour variation like brush strokes, and tints shadows cool and lights warm. Faces in "flat" shading
also get the light's direction baked in (the engine does not light them). Every face needs its own island: one island
can only hold one face's light.

survey does the same pass and also tells what it knows of each texel (Texel: edge, rim, occlusion, height, light,
face, island axes) for the layers painted before the light (spec 2026-10-03 blockpaint surfaces); lit grades a
texel's colour by its light (shading.graded), in the hytale mode for those layers."""

import math
from collections import namedtuple

from models import FACE_NORMALS, add, bounds, dot, face_point, face_span, length, placed, rotate, scale, sub, unit
from rays import FLOOR, RAY_START, covered, hit, occlusion, world_boxes
from shading import graded

# What the bake knows of one texel, for the layers painted before the light (spec 2026-10-03 blockpaint surfaces,
# maps): world point and normal, edge (bevel ring direction and weight, None inside or on a seam), rim (texels to
# the face's nearest open border, at most RIM_CAP; a seam is no border), occlusion (0 open to 1 enclosed), height (0
# at the model's foot to 1 at its top), ground (1 on the floor of a grounded model to 0 from GRIME_HEIGHT up; 0 on a
# model held or hung, which stands on no floor), light (dot of the normal and LIGHT), face (node name, side), and the
# world directions of the island's u and v axes.
Texel = namedtuple("Texel", "point normal edge rim occlusion height ground light face u_dir v_dir")
RIM_CAP = 8
# How a model is baked: standing on a floor (grounded), the nodes that cast nothing (see_through), the scale of the
# light chips on its edges (wear) and of its grimed foot (grime).
Bake = namedtuple("Bake", "grounded see_through wear grime", defaults=(False, frozenset(), 1.0, 1.0))
# Where a texel lies for its light: world point and normal, edge (texels' edge), its occlusion (None: computed).
Spot = namedtuple("Spot", "point normal edge occluded", defaults=(None,))

# Towards the light: above, in front (+z) and to the viewer's left (-x).
LIGHT = tuple(c / math.sqrt(0.45 ** 2 + 1 + 0.55 ** 2) for c in (-0.45, 1.0, 0.55))
AO_STRENGTH = 0.85
# A glowing (fullbright) face keeps only this much of the occlusion and no cast shadow.
GLOW_AO = 0.25
SHADOW, SHADOW_REACH = 0.68, 40.0
# Bevel: strength, clamp, and the weight of the texel ring 0 and 1 from a face border.
BEVEL, BEVEL_CLAMP, BEVEL_RINGS = 0.9, 0.35, (1.0, 0.45)
NOISE_CELL, NOISE = 3.5, 0.10
# Grime: how much the lowest GRIME_HEIGHT units above the ground darken.
GRIME, GRIME_HEIGHT = 0.18, 6.0
# Wear: worn, lighter texels on the outer ring of edges facing the light.
WEAR = 0.12


def light_map(nodes, grounded=False, see_through=frozenset()):
    """{texel: brightness} of every island texel of the model, to light any texture painted on it (lit). A grounded
    model (a block) stands on a floor that occludes and grimes its foot. The nodes named in see_through are lit but
    occlude nothing (a part shown only part of the time, such as an animated drop). Fails when two faces share a
    texel: each face must have its own island, lit for it."""
    return baked(nodes, Bake(grounded, see_through), False)[0]


def survey(nodes, grounded=False, see_through=frozenset(), wear=1.0, grime=1.0):
    """(values, contexts) in one pass: values is light_map's {texel: brightness}, its light chips on edges scaled by
    wear and its grimed foot by grime (1, 1: light_map's own); contexts is {texel: Texel} for the layers painted before
    the light. Fails like light_map when two faces share a texel."""
    return baked(nodes, Bake(grounded, see_through, wear, grime), True)


def baked(nodes, how, with_contexts):
    """(values, contexts) of the model baked as how says (Bake): light_map's values, and survey's contexts when
    with_contexts (else empty: a model that declares nothing needs no rims). Fails when two faces share a texel."""
    boxes = list(world_boxes(nodes, how.see_through)) + ([FLOOR] if how.grounded else [])
    (_, low, _), (_, high, _) = bounds(nodes)
    values, contexts, owners = {}, {}, {}
    for n, position, rotation in placed(nodes):
        shape = n["shape"]
        if shape["type"] != "box":
            continue
        mode = shape.get("shadingMode", "standard")
        for side, face in shape.get("textureLayout", {}).items():
            u0, v0 = int(face["offset"]["x"]), int(face["offset"]["y"])
            _, u_step, v_step = face_frame(shape, side, position, rotation)
            rim = rims(shape, side, position, rotation, boxes) if with_contexts else None
            for (i, j), point, normal, edge in texels(shape, side, position, rotation, boxes):
                texel = (u0 + i, v0 + j)
                if texel in owners:
                    raise SystemExit(f"{n['name']} {side} shares texel {texel} with {owners[texel]}: give each face "
                                     "its own UV island")
                owners[texel] = f"{n['name']} {side}"
                spot = Spot(point, normal, edge, occlusion(point, normal, boxes))
                values[texel] = brightness(spot, boxes, mode, how.wear)
                ground = min(1.0, max(0.0, 1.0 - point[1] / GRIME_HEIGHT)) if how.grounded else 0.0
                if how.grounded:
                    values[texel] *= 1.0 - GRIME * how.grime * ground
                if with_contexts:
                    height = min(1.0, max(0.0, (point[1] - low) / (high - low))) if high - low > 1e-9 else 0.0
                    contexts[texel] = Texel(point, normal, edge, rim[i, j], spot.occluded, height, ground,
                                            dot(normal, LIGHT), (n["name"], side), unit(u_step), unit(v_step))
    return values, contexts


def lit(image, values, mode="legacy"):
    """image with each painted (not fully transparent) texel of values ({texel: brightness}, light_map) graded by its
    brightness in mode (graded); texels outside image are skipped. Returns image, changed in place."""
    pixels = image.load()
    for (x, y), value in values.items():
        if 0 <= x < image.width and 0 <= y < image.height and pixels[x, y][3]:
            pixels[x, y] = graded(pixels[x, y], value, mode)
    return image


def texels(shape, side, position, rotation, boxes=()):
    """((i, j), world point, world normal, edge) for each texel of a face; edge: (outward direction, ring weight)
    for the two texel rings along the face border, None inside. A border where the surface goes on into one of boxes
    (two boxes side by side, a wall split in several) is a seam, not a corner: it gets no edge."""
    w, h = face_span(side, tuple(shape["settings"]["size"][a] for a in "xyz"))
    normal = rotate(rotation, FACE_NORMALS[side])
    world, u_step, v_step = face_frame(shape, side, position, rotation)
    u_dir, v_dir = unit(u_step), unit(v_step)

    def beyond(d, ring):
        # Half a texel past the border, whichever ring the texel is on (its centre is ring + 0.5 texels from it).
        texel = length(u_step) if abs(dot(d, u_dir)) > 0.5 else length(v_step)
        return scale(d, (ring + 1) * texel)

    for i in range(int(w)):
        for j in range(int(h)):
            point = world(i + 0.5, j + 0.5)
            yield (i, j), point, normal, edge((i, j), (int(w), int(h)), (u_dir, v_dir),
                                              lambda d, ring, p=point: not covered(p, normal, beyond(d, ring), boxes))


def face_frame(shape, side, position, rotation):
    """(world, u_step, v_step) of a face: world(s, t) is the world point at texel coordinates (s, t) of its island,
    u_step and v_step the world offsets of one texel along the island's u and v."""
    size = tuple(shape["settings"]["size"][a] for a in "xyz")
    offset, stretch = (tuple(shape[k][a] for a in "xyz") for k in ("offset", "stretch"))

    def world(s, t):
        local = face_point(side, size, s, t)
        return add(position, rotate(rotation, tuple(o + c * k for o, c, k in zip(offset, local, stretch))))

    return world, sub(world(1, 0), world(0, 0)), sub(world(0, 1), world(0, 0))


def rims(shape, side, position, rotation, boxes):
    """{(i, j): texels from each texel of a face to its nearest open border, at most RIM_CAP}. Each border texel tests
    once whether the surface goes on past it into one of boxes (covered: a seam), so a seam is no border."""
    w, h = (int(c) for c in face_span(side, tuple(shape["settings"]["size"][a] for a in "xyz")))
    normal = rotate(rotation, FACE_NORMALS[side])
    world, u_step, v_step = face_frame(shape, side, position, rotation)

    def open_past(s, t, step):
        return not covered(world(s, t), normal, step, boxes)

    left = [open_past(0.5, j + 0.5, scale(u_step, -1)) for j in range(h)]
    right = [open_past(w - 0.5, j + 0.5, u_step) for j in range(h)]
    top = [open_past(i + 0.5, 0.5, scale(v_step, -1)) for i in range(w)]
    bottom = [open_past(i + 0.5, h - 0.5, v_step) for i in range(w)]
    return {(i, j): min([RIM_CAP] + [d for d, opened in ((i, left[j]), (w - 1 - i, right[j]), (j, top[i]),
                                                          (h - 1 - j, bottom[i])) if opened])
            for i in range(w) for j in range(h)}


def edge(cell, size, axes, open_side=lambda d, ring: True):
    """(in-plane direction towards the nearest open face borders, weight of the texel's ring) of the texel cell (i, j)
    of a face size (w, h) texels wide whose island axes run along axes (u_dir, v_dir), for the two rings along the
    border; None for inner texels (and for faces too thin to have an inside). A border direction d counts only when
    open_side(d, ring)."""
    (i, j), (w, h), (u_dir, v_dir) = cell, size, axes
    for ring, weight in enumerate(BEVEL_RINGS):
        if min(w, h) <= 2 * ring + 1:
            break
        sides = ((scale(u_dir, -1), i == ring), (u_dir, i == w - 1 - ring), (scale(v_dir, -1), j == ring),
                 (v_dir, j == h - 1 - ring))
        d = (0.0, 0.0, 0.0)
        for direction, on_border in sides:
            if on_border and open_side(direction, ring):
                d = add(d, direction)
        if any(d):
            return unit(d), weight
    return None


def brightness(spot, boxes, mode, wear=1.0):
    """The light of one texel (spot: Spot) for a face in shading mode: occlusion (the spot's, computed when None),
    cast shadow, bevel and wear (scaled by wear) on its border rings, soft variation; Lambert too on flat faces; a
    glowing (fullbright) face keeps only GLOW_AO of the occlusion and no cast shadow (bevel, wear and variation still
    apply)."""
    point, normal, border, occluded = spot
    glowing = mode == "fullbright"
    if occluded is None:
        occluded = occlusion(point, normal, boxes)
    value = 1.0 - (GLOW_AO if glowing else AO_STRENGTH) * occluded
    facing = dot(normal, LIGHT)
    lit_start = add(point, scale(normal, RAY_START))
    if not glowing and facing > 0 and hit(lit_start, LIGHT, boxes, SHADOW_REACH) is not None:
        value *= SHADOW
    if mode == "flat":
        value *= 0.8 + 0.3 * max(0.0, facing)
    if border is not None:
        outward, weight = border
        lit = dot(unit(add(normal, scale(outward, 0.9))), LIGHT) - facing
        # A darkened side or top edge reads as a groove next to its lit neighbour face: as Hytale paints them, edges
        # only catch light, but the ones turned down.
        if outward[1] > -0.5:
            lit = max(0.0, lit)
        value *= 1.0 + weight * max(-BEVEL_CLAMP, min(BEVEL_CLAMP, BEVEL * lit))
        if weight == BEVEL_RINGS[0] and lit > 0 and value_noise(scale(point, 3.0)) > 0.35:
            value *= 1.0 + WEAR * wear
    return value * (1.0 + NOISE * value_noise(point))


def value_noise(point):
    """Smooth noise in [-1, 1] over world space, cells NOISE_CELL units wide: brush-like colour variation."""
    cell = [c / NOISE_CELL for c in point]
    base = [math.floor(c) for c in cell]
    frac = [c - b for c, b in zip(cell, base)]
    smooth = [f * f * (3 - 2 * f) for f in frac]
    value = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = ((smooth[0] if dx else 1 - smooth[0]) * (smooth[1] if dy else 1 - smooth[1])
                     * (smooth[2] if dz else 1 - smooth[2]))
                value += w * lattice(base[0] + dx, base[1] + dy, base[2] + dz)
    return value


def lattice(x, y, z):
    h = (x * 374761393 + y * 668265263 + z * 2147483647) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return (h & 0xFFFF) / 32767.5 - 1.0
