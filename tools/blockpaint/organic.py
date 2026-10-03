"""Brushes of living matter (spec 2026-10-03 blockpaint, brushes): leather, skin, hair, fur, bone, horn, scales and
feathers. Like brushes.py, each paints a whole island, (w, h, side) -> image, as a painter would, broad and soft;
bake.py then lights it from the model. Along means along the island's long side (brushes.along: a hair falls along
it)."""

from brushes import along, coloured, edge, family, jitter, mix, painted, smooth, smooth2


@family("leather")
def leather(rgb):
    """Leather: an irregular grain of soft blotches with a few dark pits, broad gentle folds, edges darker and worn,
    a light scuff here and there."""
    def rule(x, y, w, h, side):
        k = 1 + 0.06 * smooth2(x / 1.6, y / 1.6, 31) + 0.07 * smooth2(x / 7, y / 5, 32)
        if jitter(x * 53 + y * 29, 33) > 0.93:
            k -= 0.12
        k -= 0.12 * max(0, 1 - edge(x, y, w, h) / 2)
        if smooth2(x / 3, y / 3, 34) > 0.72:
            k += 0.1
        return coloured(rgb, k)
    return painted(rule)


@family("leather")
def skin(rgb, flush=(206, 108, 96)):
    """Skin: barely varying, never noisy texel by texel: broad soft patches, a rosy flush in places, lighter towards
    the top of side faces and gently shaded below."""
    def rule(x, y, w, h, side):
        k = 1 + 0.025 * smooth2(x / 6, y / 6, 35)
        if side not in ("top", "bottom") and h > 1:
            k += 0.05 * (0.5 - y / (h - 1))
        colour = coloured(rgb, k)[:3]
        rosy = max(0.0, smooth2(x / 5, y / 4, 36))
        return (*mix(colour, flush, 0.12 * rosy), 255)
    return painted(rule)


@family("hair")
def hair(rgb, lock=2):
    """Hair: thin locks running along the island and waving a little, each of its own shade, a darker parting between
    them, darker at the roots (the start of the island), a soft sheen a third of the way along."""
    def rule(x, y, w, h, side):
        a, b, length = along(x, y, w, h)
        wave = b + round(1.4 * smooth(a / 6, 48))
        strand = wave // lock
        k = 1 + 0.12 * jitter(strand, 37) + 0.05 * smooth(a / 3 + strand * 2.3, 38)
        if wave % lock == lock - 1 and jitter(strand * 7 + a // 4, 49) > -0.3:
            k -= 0.1
        reach = a / max(length - 1, 1)
        k += 0.1 * max(0.0, 1 - abs(reach - 0.3) * 5) - 0.1 * max(0.0, 1 - reach * 6)
        return coloured(rgb, k)
    return painted(rule)


@family("hair")
def fur(rgb, tuft=4):
    """Fur: short strokes two texels wide and tuft long, each column of them starting at its own height and of its
    own shade, every stroke from a light tip down to a darker root, over a soft mottle."""
    def rule(x, y, w, h, side):
        column = x // 2
        shift = int((jitter(column, 52) + 1) * tuft)
        stroke = (y + shift) // tuft
        place = (y + shift) % tuft / max(tuft - 1, 1)
        k = 1 + 0.07 * jitter(column, 41) + 0.03 * jitter(column * 31 + stroke * 17, 40)
        k += 0.06 * smooth2(x / 5, y / 5, 39)
        return coloured(rgb, k + 0.08 * (0.5 - place))
    return painted(rule)


@family("bone")
def bone(rgb=(226, 214, 182), crack=(160, 142, 112)):
    """Bone or ivory: a yellowing ivory, smooth with a faint mottle and rare pores, darker towards both ends of the
    island, now and then a fine crack wandering along part of it."""
    def rule(x, y, w, h, side):
        a, b, length = along(x, y, w, h)
        ends = abs(2 * a / max(length - 1, 1) - 1)
        k = 1 + 0.025 * smooth2(x / 3, y / 3, 42) - 0.14 * ends ** 3
        if jitter(x * 61 + y * 37, 43) > 0.96:
            k -= 0.06
        colour = coloured(rgb, k)
        across = min(w, h)
        start = int((jitter(length * 3 + across, 50) + 1) / 2 * length / 2)
        # About one island in two has a crack, drawn by the island's own size.
        cracked = jitter(length * 5 + across * 11, 41) > 0
        if cracked and length >= 8 and start <= a < start + length // 2:
            path = across // 2 + round(1.5 * smooth(a / 3, 51))
            if b == path:
                return (*mix(colour[:3], crack, 0.45), 255)
        return colour
    return painted(rule)


@family("bone")
def horn(rgb, base=0.55):
    """Horn or claw: fine grooves running along it, each its own shade, darkening to base (a fraction of the light)
    at the island's end, its root: the bottom of an upright side face, the tip at the top."""
    def rule(x, y, w, h, side):
        a, b, length = along(x, y, w, h)
        k = 1 + 0.07 * jitter(b, 44) + 0.03 * smooth(a / 3 + b, 45)
        root = a / max(length - 1, 1)
        return coloured(rgb, k * (base + (1 - base) * (1 - root) ** 0.7))
    return painted(rule)


@family("leather")
def scales(rgb, size=6):
    """Scales (reptiles, dragons): rows of overlapping round scales, each row shifted half a scale and laid over the
    one below, so that each scale shows a rounded lower edge: lit in its upper middle, shaded along that edge."""
    rows = max(3, size * 2 // 3)

    def rule(x, y, w, h, side):
        row = y // rows
        cx = (x + (row % 2) * (size // 2)) % size - (size - 1) / 2
        cy = y % rows
        d = (cx / (size / 2)) ** 2 + (cy / rows) ** 2 * 1.6
        k = 1 + 0.05 * jitter((x + (row % 2) * (size // 2)) // size * 31 + row * 7, 46)
        k += 0.07 * max(0.0, 1 - d) - (0.12 if d > 1.0 else 0)
        return coloured(rgb, k)
    return painted(rule)


@family("hair")
def feathers(rgb, shaft=(236, 230, 216)):
    """Feathers: a pale shaft along the middle of the island, fine barbs slanting away from it towards the tip in
    gently alternating shades, the tip darker."""
    def rule(x, y, w, h, side):
        a, b, length = along(x, y, w, h)
        middle = (min(w, h) - 1) / 2
        k = 1 - 0.12 * (a / max(length - 1, 1)) ** 2 + 0.03 * jitter(a * 7 + b, 47)
        if abs(b - middle) < 0.6:
            return (*mix(coloured(rgb, k)[:3], shaft, 0.55), 255)
        barb = (a - int(abs(b - middle) * 1.5)) % 4
        return coloured(rgb, k + (0.07 if barb == 0 else -0.05 if barb == 2 else 0))
    return painted(rule)
