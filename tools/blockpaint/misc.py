"""Brushes of the remaining materials (spec 2026-10-03 blockpaint, brushes): glass, rubber, wax, charred wood and
rope. Each paints a whole island, (w, h, side) -> image; bake.py then lights it."""

from brushes import along, coloured, family, jitter, mix, painted, smooth, smooth2, wood


@family("glass")
def glass(rgb, glint=(240, 250, 255)):
    """Glass: a very faint gradient across the face, lighter at the top left, a pale glint in that corner and a thin
    lighter frame along two edges, as light catches a pane."""
    def rule(x, y, w, h, side):
        u, v = x / max(w - 1, 1), y / max(h - 1, 1)
        k = 1.06 - 0.12 * (u + v) / 2
        colour = coloured(rgb, k)[:3]
        if (x, y) in ((1, 1), (2, 1), (1, 2)) and w >= 4 and h >= 4:
            colour = mix(colour, glint, 0.7 if (x, y) == (1, 1) else 0.4)
        elif x == 0 or y == 0:
            colour = mix(colour, glint, 0.2)
        return (*colour, 255)
    return painted(rule)


@family("rubber")
def rubber(rgb):
    """Rubber: dull and matte, a very soft mottle, a faint sheen worn into the middle of side faces."""
    def rule(x, y, w, h, side):
        k = 1 + 0.03 * smooth2(x / 4, y / 4, 83)
        if side not in ("top", "bottom") and h > 2:
            k += 0.04 * max(0.0, 1 - abs(y / (h - 1) - 0.45) * 4)
        return coloured(rgb, k)
    return painted(rule)


@family("wax")
def wax(rgb, glow=(255, 236, 196)):
    """Wax: soft and milky, lighter and warmer towards the middle of the face as light passes into it, a faint
    mottle, smoother than stone."""
    def rule(x, y, w, h, side):
        u, v = 2 * x / max(w - 1, 1) - 1, 2 * y / max(h - 1, 1) - 1
        inside = max(0.0, 1 - (u * u + v * v) / 2)
        colour = coloured(rgb, 1 + 0.02 * smooth2(x / 4, y / 4, 84))[:3]
        return (*mix(colour, glow, 0.22 * inside), 255)
    return painted(rule)


@family("wood")
def charred_wood(rgb, coal=(40, 32, 30)):
    """Charred wood: wood (brushes.wood) burnt black in patches that spread from the lower part of side faces, its
    char split by dark cracks into small blocks, 3 x 2 texels, each of its own shade and paler along its upper rim."""
    base = wood(rgb)

    def brush(w, h, side):
        image = base(w, h, side)
        pixels = image.load()
        for x in range(w):
            for y in range(h):
                lower = y / max(h - 1, 1) if side not in ("top", "bottom") else 0.5
                burn = smooth2(x / 4, y / 4, 85) + 0.8 * lower - 0.25
                if burn > 0.3:
                    # Blocks of 3 x 2 texels with a 1 texel crack after each, every other row shifted by 2.
                    row = y // 3
                    shifted = x + (row % 2) * 2
                    tone = 1 + 0.18 * jitter(shifted // 4 * 31 + row * 7, 86)
                    if shifted % 4 == 3 or y % 3 == 2:
                        tone = 0.55
                    elif y % 3 == 0 and jitter(x * 7 + y, 87) > -0.4:
                        tone += 0.2
                    pixels[x, y] = coloured(coal, tone)
                elif burn > 0.1:
                    pixels[x, y] = (*mix(pixels[x, y][:3], coal, 0.5), 255)
        return image
    return brush


@family("textile")
def rope(rgb):
    """Rope: twisted strands three texels wide crossing the island on a slant, each strand lighter on its crown and
    darker where it dips under the next, its tone drifting softly along the rope."""
    def rule(x, y, w, h, side):
        a, b, _ = along(x, y, w, h)
        twist = (a + b) // 3 % 4
        k = (1.06, 1.0, 0.9, 0.96)[twist] + 0.03 * smooth(a / 2 + b, 88)
        return coloured(rgb, k)
    return painted(rule)
