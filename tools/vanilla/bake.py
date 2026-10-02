"""Bakes light into a painted model texture, texel by texel, from the model's own geometry
(docs/research/hytale-models.md § 4): Hytale paints its shadows, occlusion and highlights into textures; this does it
from the boxes themselves.

For every texel of every face island it finds the point and the normal on the model (models.placed: Hytale's child
placement), then darkens it by ambient occlusion (rays into the hemisphere, against every other box and, for a block,
a floor), by the shadow cast from a light above the front left, lights the bevel of the island's border rings where
it turns towards that light (with wear: lighter chips) and shades it elsewhere, grimes a block's foot, adds a soft
world-space colour variation like brush strokes, and tints shadows cool and lights warm. Faces in "flat" shading also
get the light's direction baked in (the engine does not light them). Every face needs its own island: one island
can only hold one face's light."""

import math

from models import add, placed, rotate
from pack import FACE_NORMALS, face_point, face_span

# Towards the light: above, in front (+z) and to the viewer's left (-x).
LIGHT = tuple(c / math.sqrt(0.45 ** 2 + 1 + 0.55 ** 2) for c in (-0.45, 1.0, 0.55))
AO_DISTANCE, AO_STRENGTH = 10.0, 0.85
# A glowing (fullbright) face keeps only this much of the occlusion and no cast shadow.
GLOW_AO = 0.25
SHADOW, SHADOW_REACH = 0.68, 40.0
# Rays start this far off the face, so that they never hit their own box.
RAY_START = 0.05
# Bevel: strength, clamp, and the weight of the texel ring 0 and 1 from a face border.
BEVEL, BEVEL_CLAMP, BEVEL_RINGS = 0.9, 0.35, (1.0, 0.45)
NOISE_CELL, NOISE = 3.5, 0.10
# Grime: how much the lowest GRIME_HEIGHT units above the ground darken.
GRIME, GRIME_HEIGHT = 0.18, 6.0
# Wear: worn, lighter texels on the outer ring of edges facing the light.
WEAR = 0.12
LOW, HIGH = 12, 244
# A floor under grounded models (blocks): a wide, 100 units thick slab below y = 0 that occludes like any box.
FLOOR = ((0.0, -50.0, 0.0), (0.0, 0.0, 0.0, 1.0), (1000.0, 50.0, 1000.0), 1e9)
# Hemisphere directions around a normal: (elevation, azimuth) in degrees, plus the normal itself.
RAYS = [(90, 0)] + [(e, a) for e in (25, 55) for a in range(0, 360, 60)]


def light(image, nodes, grounded=False):
    """image with the model's light (light_map) baked into every island texel. Returns image, changed in place."""
    return lit(image, light_map(nodes, grounded))


def light_map(nodes, grounded=False):
    """{texel: brightness} of every island texel of the model, to light any texture painted on it (lit). A grounded
    model (a block) stands on a floor that occludes and grimes its foot. Fails when two faces share a texel: each face
    must have its own island, lit for it."""
    boxes = list(world_boxes(nodes)) + ([FLOOR] if grounded else [])
    values, owners = {}, {}
    for n, position, rotation in placed(nodes):
        shape = n["shape"]
        if shape["type"] != "box":
            continue
        mode = shape.get("shadingMode", "standard")
        for side, face in shape.get("textureLayout", {}).items():
            u0, v0 = int(face["offset"]["x"]), int(face["offset"]["y"])
            for (i, j), point, normal, edge in texels(shape, side, position, rotation, boxes):
                texel = (u0 + i, v0 + j)
                if texel in owners:
                    raise SystemExit(f"{n['name']} {side} shares texel {texel} with {owners[texel]}: give each face "
                                     "its own UV island")
                owners[texel] = f"{n['name']} {side}"
                value = brightness(point, normal, edge, boxes, mode)
                if grounded:
                    value *= 1.0 - GRIME * min(1.0, max(0.0, 1.0 - point[1] / GRIME_HEIGHT))
                values[texel] = value
    return values


def lit(image, values):
    """image with each painted (not fully transparent) texel of values ({texel: brightness}, light_map) graded by its
    brightness; texels outside image are skipped. Returns image, changed in place."""
    pixels = image.load()
    for (x, y), value in values.items():
        if 0 <= x < image.width and 0 <= y < image.height and pixels[x, y][3]:
            pixels[x, y] = graded(pixels[x, y], value)
    return image


def texels(shape, side, position, rotation, boxes=()):
    """((i, j), world point, world normal, edge) for each texel of a face; edge: (outward direction, ring weight)
    for the two texel rings along the face border, None inside. A border where the surface goes on into one of boxes
    (two boxes side by side, a wall split in several) is a seam, not a corner: it gets no edge."""
    size = tuple(shape["settings"]["size"][a] for a in "xyz")
    offset, stretch = (tuple(shape[k][a] for a in "xyz") for k in ("offset", "stretch"))
    w, h = face_span(side, size)
    normal = rotate(rotation, FACE_NORMALS[side])

    def world(s, t):
        local = face_point(side, size, s, t)
        return add(position, rotate(rotation, tuple(o + c * k for o, c, k in zip(offset, local, stretch))))

    u_step, v_step = sub(world(1, 0), world(0, 0)), sub(world(0, 1), world(0, 0))
    u_dir, v_dir = unit(u_step), unit(v_step)

    def beyond(d, ring):
        # Half a texel past the border, whichever ring the texel is on (its centre is ring + 0.5 texels from it).
        texel = length(u_step) if abs(dot(d, u_dir)) > 0.5 else length(v_step)
        return scale(d, (ring + 1) * texel)

    for i in range(int(w)):
        for j in range(int(h)):
            point = world(i + 0.5, j + 0.5)
            yield (i, j), point, normal, edge(i, j, int(w), int(h), u_dir, v_dir,
                                              lambda d, ring, p=point: not covered(p, normal, beyond(d, ring), boxes))


def edge(i, j, w, h, u_dir, v_dir, open_side=lambda d, ring: True):
    """(in-plane direction towards the nearest open face borders, weight of the texel's ring) for the two rings along
    the border, None for inner texels (and for faces too thin to have an inside). A border direction d counts only
    when open_side(d, ring)."""
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


def brightness(point, normal, border, boxes, mode):
    """The light of one texel for a face in shading mode: occlusion, cast shadow, bevel and wear on its border
    rings, soft variation; Lambert too on flat faces; a glowing (fullbright) face keeps only GLOW_AO of the
    occlusion and no cast shadow (bevel, wear and variation still apply)."""
    glowing = mode == "fullbright"
    value = 1.0 - (GLOW_AO if glowing else AO_STRENGTH) * occlusion(point, normal, boxes)
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
            value *= 1.0 + WEAR
    return value * (1.0 + NOISE * value_noise(point))


def occlusion(point, normal, boxes):
    """0 (open) to 1 (enclosed): how much of the hemisphere above the point other boxes hide, nearer counting more."""
    tangent = unit(cross(normal, (0.0, 1.0, 0.0) if abs(normal[1]) < 0.9 else (1.0, 0.0, 0.0)))
    bitangent = cross(normal, tangent)
    start = add(point, scale(normal, RAY_START))
    total = weight_sum = 0.0
    for elevation, azimuth in RAYS:
        e, a = math.radians(elevation), math.radians(azimuth)
        direction = add(scale(normal, math.sin(e)),
                        add(scale(tangent, math.cos(e) * math.cos(a)), scale(bitangent, math.cos(e) * math.sin(a))))
        weight = math.sin(e)
        t = hit(start, direction, boxes, AO_DISTANCE)
        total += weight * (0.0 if t is None else 1.0 - t / AO_DISTANCE)
        weight_sum += weight
    return total / weight_sum


def world_boxes(nodes):
    """(centre, inverse rotation, half extents, bounding radius) of every box, in world space."""
    for n, position, rotation in placed(nodes):
        shape = n["shape"]
        if shape["type"] != "box":
            continue
        offset, stretch = (tuple(shape[k][a] for a in "xyz") for k in ("offset", "stretch"))
        half = tuple(abs(shape["settings"]["size"][a] * k) / 2 for a, k in zip("xyz", stretch))
        centre = add(position, rotate(rotation, offset))
        inverse = (-rotation[0], -rotation[1], -rotation[2], rotation[3])
        yield centre, inverse, half, math.sqrt(sum(c * c for c in half))


def hit(origin, direction, boxes, reach):
    """Distance along the ray to the nearest box within reach, None if none (slab test in each box's frame)."""
    nearest = None
    for centre, inverse, half, radius in boxes:
        to_centre = sub(centre, origin)
        along = dot(to_centre, direction)
        if along < -radius or along > reach + radius or dot(to_centre, to_centre) - along * along > radius * radius:
            continue
        o = rotate(inverse, sub(origin, centre))
        d = rotate(inverse, direction)
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


def graded(pixel, value):
    """pixel lit by value, shadows drifting cool (blue) and lights warm (yellow), clamped off pure black and white."""
    r, g, b, a = pixel
    shade = max(0.0, 1.0 - value)
    glow = max(0.0, value - 1.0)
    tint = (1 - 0.10 * shade + 0.06 * glow, 1 - 0.04 * shade + 0.03 * glow, 1 + 0.08 * shade - 0.05 * glow)
    return (*(min(HIGH, max(LOW, round(c * value * k))) for c, k in zip((r, g, b), tint)), a)


def sub(a, b):
    return tuple(x - y for x, y in zip(a, b))


def scale(a, k):
    return tuple(x * k for x in a)


def dot(a, b):
    return sum(x * y for x, y in zip(a, b))


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def length(a):
    return math.sqrt(dot(a, a))


def unit(a):
    size = length(a)
    return scale(a, 1 / size) if size > 1e-9 else a
