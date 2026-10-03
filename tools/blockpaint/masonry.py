"""Brushes of built and loose mineral matter (spec 2026-10-03 blockpaint, brushes): brick, plaster, concrete, sand,
mud and mossy stone. Like brushes.py, each paints a whole island, (w, h, side) -> image; bake.py then lights it."""

from PIL import Image, ImageFilter

from brushes import coloured, family, jitter, mix, painted, smooth2, stone

# How far mossy stone softens the chips of brushes.stone under its moss.
SOFTEN = 0.6


@family("ceramic")
def brick(rgb, mortar=(186, 172, 152), size=(8, 4)):
    """Brick: courses of bricks size texels each (mortar included), every other course shifted half a brick, each
    brick of its own shade with a soft mottle and rare pits, a lighter mortar line between them, a little sunken."""
    bw, bh = size

    def rule(x, y, w, h, side):
        course = y // bh
        shifted = x + (course % 2) * (bw // 2)
        if y % bh == bh - 1 or shifted % bw == bw - 1:
            # Sunken and in shade: the joint reads without a hard light line between small bricks.
            return coloured(mortar, 0.84 + 0.03 * smooth2(x / 3, y / 3, 53))
        # Each brick its own shade (meso), broad over the mortar's single texel lines (micro): Macro > Meso > Micro.
        k = 1 + 0.12 * jitter(shifted // bw * 31 + course * 7, 54) + 0.06 * smooth2(x / 6, y / 6, 55)
        if jitter(x * 61 + y * 43, 56) > 0.96:
            k -= 0.1
        return coloured(rgb, k)
    return painted(rule)


@family("stone")
def plaster(rgb, under=(150, 120, 96)):
    """Plaster: flat and pale, gently uneven over broad patches, here and there a small chipped patch showing the
    darker wall under it, with a light broken rim."""
    def rule(x, y, w, h, side):
        k = 1 + 0.03 * smooth2(x / 6, y / 6, 57) + 0.012 * jitter(x * 23 + y * 7, 58)
        chip = smooth2(x / 2.5, y / 2.5, 59)
        if chip > 0.7:
            return coloured(under, 1 + 0.05 * jitter(x + y * 5, 60))
        if chip > 0.6:
            k += 0.06
        return coloured(rgb, k)
    return painted(rule)


@family("stone")
def concrete(rgb):
    """Concrete: broad diffuse shifts of tone over the whole face and scattered small pores, a few a pair of texels
    wide."""
    def rule(x, y, w, h, side):
        k = 1 + 0.05 * smooth2(x / 9, y / 9, 61) + 0.025 * smooth2(x / 3, y / 3, 62)
        pore = jitter(x * 41 + y * 29, 63)
        if pore > 0.94 or (pore > 0.9 and jitter((x - 1) * 41 + y * 29, 63) > 0.94):
            k -= 0.13
        return coloured(rgb, k)
    return painted(rule)


@family("stone")
def sand(rgb):
    """Sand: soft drifts of tone in little clumps, a few scattered light and dark grains."""
    def rule(x, y, w, h, side):
        k = 1 + 0.05 * smooth2(x / 2.5, y / 2.5, 64)
        grain = jitter(x * 67 + y * 31, 65)
        k += 0.07 if grain > 0.9 else -0.07 if grain < -0.9 else 0
        return coloured(rgb, k)
    return painted(rule)


@family("stone")
def mud(rgb, wet=0.72):
    """Mud: wetter, darker puddles (wet times as light) with ragged organic edges in drier, lighter mud, a few
    lighter crumbs."""
    def rule(x, y, w, h, side):
        puddle = smooth2(x / 4, y / 4, 66) + 0.35 * smooth2(x / 1.3, y / 1.3, 67)
        k = 1 + 0.04 * smooth2(x / 2, y / 2, 68)
        if puddle > 0.15:
            k *= wet + 0.06 * jitter(x * 11 + y * 7, 69)
        elif jitter(x * 53 + y * 19, 70) > 0.9:
            k += 0.1
        return coloured(rgb, k)
    return painted(rule)


@family("stone")
def mossy_stone(rgb, moss=(88, 126, 58)):
    """Mossy stone: stone (brushes.stone, its chips softened) with moss in clumps, more of it on top faces and low on
    side faces, its edges broken into tufts."""
    base = stone(rgb)

    def brush(w, h, side):
        stone_image = base(w, h, side)
        image = Image.blend(stone_image, stone_image.filter(ImageFilter.BoxBlur(1)), SOFTEN)
        pixels = image.load()
        for x in range(w):
            for y in range(h):
                lower = y / max(h - 1, 1) if side not in ("top", "bottom") else 0.6 if side == "top" else 0
                tufts = 0.25 * smooth2(x / 1.4, y / 1.4, 72)
                cover = smooth2(x / 3.5, y / 3.5, 71) + 0.6 * lower - 0.35 + tufts
                if cover > 0.25:
                    tone = 1 + 0.08 * smooth2(x / 2.5, y / 2.5, 73)
                    pixels[x, y] = (*mix(coloured(moss, tone)[:3], pixels[x, y][:3], 0.15 if cover < 0.4 else 0), 255)
        return image
    return brush
