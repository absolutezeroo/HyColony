"""The colour of a lit texel (bake.lit): a painted colour scaled by the light baked for it, its hue nudged as a
painter would, kept off pure black and white."""

import colorsys

LOW, HIGH = 12, 244
# The hytale light (graded): hues turn towards violet in shadow and yellow in light, so many degrees per unit of shade
# or light (docs/research/blockpaint-surfaces.md § 2: Hytale's darker wood has a lower hue).
SHADE_HUE, SHADE_TURN = 275.0, 40.0
LIGHT_HUE, LIGHT_TURN = 50.0, 20.0
# Lit hues farther than this (degrees) from LIGHT_HUE keep their hue: a blue would pass through green on its way.
LIGHT_REACH = 110.0


def graded(pixel, value, mode="legacy"):
    """pixel lit by value, clamped off pure black and white. legacy (every model that declares nothing): shadows
    drift cool (blue) and lights warm (yellow). hytale (spec 2026-10-03 blockpaint surfaces, light): the hue turns
    towards SHADE_HUE in shadow and LIGHT_HUE in light, by the shorter way round, its saturation kept."""
    if mode == "hytale":
        return hue_graded(pixel, value)
    r, g, b, a = pixel
    shade = max(0.0, 1.0 - value)
    glow = max(0.0, value - 1.0)
    tint = (1 - 0.10 * shade + 0.06 * glow, 1 - 0.04 * shade + 0.03 * glow, 1 + 0.08 * shade - 0.05 * glow)
    return (*(min(HIGH, max(LOW, round(c * value * k))) for c, k in zip((r, g, b), tint)), a)


def hue_graded(pixel, value):
    """pixel scaled by value, its hue turned SHADE_TURN degrees per unit of shade towards SHADE_HUE, or LIGHT_TURN
    per unit of light towards LIGHT_HUE (never past it), its saturation kept."""
    r, g, b, a = pixel
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    shade, glow = max(0.0, 1.0 - value), max(0.0, value - 1.0)
    hue = h * 360
    if shade:
        hue = turned(hue, SHADE_HUE, shade * SHADE_TURN)
    else:
        hue = turned(hue, LIGHT_HUE, glow * LIGHT_TURN, LIGHT_REACH)
    rgb = colorsys.hsv_to_rgb(hue / 360, s, v)
    return (*(min(HIGH, max(LOW, round(c * 255 * value))) for c in rgb), a)


def turned(hue, target, degrees, reach=180.0):
    """hue (degrees) turned towards target by at most degrees, the shorter way round; a hue farther than reach from
    target keeps its hue."""
    gap = (target - hue + 180) % 360 - 180
    if abs(gap) > reach:
        return hue
    return (hue + max(-degrees, min(degrees, gap))) % 360
