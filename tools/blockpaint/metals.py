"""Brushes of worked metals (spec 2026-10-03 blockpaint, brushes): gold, bronze and copper, each with its own light
and shade rather than one tint of brushes.metal, painted metal and rusty metal. Each paints a whole island,
(w, h, side) -> image; bake.py then lights it."""

from PIL import Image, ImageFilter

from brushes import along, coloured, edge, family, jitter, metal, mix, painted, smooth, smooth2


def worked(light, body, deep, streak=0.06):
    """A precious or warm metal: brushed streaks along the island's long side, a bright band a third down side faces
    turning towards light, the lower part towards deep, a few bright flecks."""
    def rule(x, y, w, h, side):
        t = streak * jitter(along(x, y, w, h)[1], 74) + 0.03 * smooth(x / 3 + y, 75)
        if side not in ("top", "bottom") and h > 2:
            down = y / (h - 1)
            t += 0.5 * max(0.0, 1 - abs(down - 0.3) * 4) - 0.35 * max(0.0, down - 0.6)
        if jitter(x * 13 + y * 7, 76) > 0.94:
            t += 0.4
        colour = mix(body, light, t) if t >= 0 else mix(body, deep, -t)
        return (*colour, 255)
    return painted(rule)


@family("noble")
def gold():
    """Gold: a deep warm yellow, buttery highlights, amber shade."""
    return worked((255, 236, 150), (222, 176, 68), (150, 96, 30))


@family("cuprous")
def bronze():
    """Bronze: a dull brown gold, pale highlights, dark brown shade."""
    return worked((232, 194, 128), (176, 126, 66), (96, 62, 32))


@family("cuprous")
def copper():
    """Copper: a pinkish orange, salmon highlights, deep red-brown shade."""
    return worked((248, 172, 124), (196, 110, 72), (112, 52, 30))


@family("ferrous")
def painted_metal(paint, bare=(150, 154, 162)):
    """Painted metal: a flat coat of paint, faintly uneven, worn through to bare metal in small patches and along
    stretches of the island's rim."""
    under = metal(bare)

    def brush(w, h, side):
        image = under(w, h, side)
        pixels = image.load()
        for x in range(w):
            for y in range(h):
                worn = smooth2(x / 2, y / 2, 77) > 0.72
                rim = edge(x, y, w, h) == 0 and smooth2(x / 3 + y / 3, 0, 78) > 0.2
                if not (worn or rim):
                    pixels[x, y] = coloured(paint, 1 + 0.025 * smooth2(x / 5, y / 5, 79))
        return image
    return brush


@family("ferrous")
def rusty_metal(bare=(132, 134, 140), rust=(150, 74, 36)):
    """Rusty metal: brushed metal eaten by rust in clumps, orange to dark brown with pits, the rust running a little
    lower than where it starts."""
    under = metal(bare)

    def brush(w, h, side):
        metal_image = under(w, h, side)
        # Rust dulls the metal's bright streaks and flecks where it creeps.
        image = Image.blend(metal_image, metal_image.filter(ImageFilter.BoxBlur(1)), 0.5)
        pixels = image.load()
        for x in range(w):
            for y in range(h):
                runs = 0.25 * smooth2(x / 3, (y - 2) / 3, 80) if side not in ("top", "bottom") else 0
                cover = smooth2(x / 3, y / 3, 80) + runs
                if cover > 0.25:
                    tone = 0.9 + 0.15 * smooth2(x / 2.5, y / 2.5, 81)
                    if jitter(x * 37 + y * 53, 82) > 0.95:
                        tone -= 0.15
                    pixels[x, y] = coloured(rust, tone)
                elif cover > 0.05:
                    # The rust's border stains the metal by degrees rather than in one step.
                    pixels[x, y] = (*mix(pixels[x, y][:3], rust, 0.7 * (cover - 0.05) / 0.2), 255)
        return image
    return brush
