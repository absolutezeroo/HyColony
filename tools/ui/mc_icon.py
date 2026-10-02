"""Draws GUI icons in MineColonies' style, for the controls MC has no texture for (MC's own textures are copied, never
redrawn). Built on pixelstudio.py of the pixel-art-studio skill (github.com/Gamezxz/pixel-art-studio, MIT, commit
f8c246635c4621a6c2b427833149afdc3dc3c719: the outputs are byte-identical with that commit only; it also uses the
private Sprite._img()), installed at the user level: ~/.claude/skills/pixel-art-studio.

MC's style, measured on its GUI textures with that skill's study.py: single-hue ramps (no hue shift), relief from
shapes shifted toward the top-left light, soft shading melted inside the silhouette, a rim coloured by the angle of
the edge to the light, symbols pressed in (flat floor, wall shadow on the light side).
"""

import sys
from pathlib import Path

SKILL = Path.home() / ".claude" / "skills" / "pixel-art-studio" / "scripts"
if not (SKILL / "pixelstudio.py").exists():
    sys.exit("pixel-art-studio missing: git clone https://github.com/Gamezxz/pixel-art-studio, git checkout "
             "f8c246635c4621a6c2b427833149afdc3dc3c719, and copy SKILL.md, scripts/, references/ and examples/ into "
             + str(SKILL.parent))
sys.path.insert(0, str(SKILL))
from PIL import Image, ImageChops, ImageFilter  # noqa: E402
from pixelstudio import Sprite  # noqa: E402

# MC's wax, darkest -> lightest (red_wax_home.png): shadow, recess wall, inner rim, recess floor, face, light.
D0, SH, D2, FLOOR = "#3e0404", "#450505", "#6f0b0b", "#7f0f0f"
B0, B1, B2 = "#8d0f0f", "#940f0f", "#9b0f0f"
L1 = "#bb1c1c"
WAX = [D0, SH, D2, FLOOR, B0, B1, B2, L1]

# Red of MC's seal rim by how much the edge faces the top-left light (1 toward it, -1 away), read on red_wax_blank:
# 201-206 top-left, 187 top, 167 left, 125 top-right, 99-135 bottom-left, 74 bottom, 62 right and bottom-right. The
# table makes it symmetric about the top-left diagonal.
RIM_RED = ((-1.0, 62), (-0.7, 70), (0.0, 128), (0.7, 167), (1.0, 205))
# Red of the wax lips round a pressed symbol, read on red_wax_citizens and red_wax_settings: the far lip 167-198,
# the near lip 103-117.
FAR_LIP_RED, NEAR_LIP_RED = 180, 114
SEAL_SIZE = 17
# MC's GUI textures are drawn x4 nearest-neighbour into the @2x files the windows load (CLAUDE.md § 7).
EXPORT_SCALE = 4


def soft(s, radius):
    """Gaussian blur of the sprite's layer inside its silhouette only (normalised by the blurred alpha, nothing bleeds
    out), its outermost ring kept crisp: the in-between tones of MC's painted textures (67 colours on a seal)."""
    im = s._img()
    alpha = im.getchannel("A")
    premul = Image.merge("RGB", [ImageChops.multiply(c, alpha) for c in im.split()[:3]])
    blur, weight = premul.filter(ImageFilter.GaussianBlur(radius)), alpha.filter(ImageFilter.GaussianBlur(radius))
    out = im.copy()
    for y in range(s.h):
        for x in range(s.w):
            if s.get(x, y) and not edge(s, x, y, 1):
                w = max(weight.getpixel((x, y)), 1) / 255
                out.putpixel((x, y), tuple(min(255, round(c / w)) for c in blur.getpixel((x, y))) + (255,))
    im.paste(out)


def edge(s, x, y, ring):
    """True when an empty pixel lies ring pixels away from (x, y) in one of the four directions."""
    return any(not s.get(x + ring * dx, y + ring * dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))


def wax_red(r):
    """MC's wax colour for a red value: green and blue grow from 6 % of the red in the shadows to 15 % in the light."""
    k = 0.06 + 0.09 * max(0.0, min(1.0, (r - 62) / 143))
    return (round(r), round(r * k), round(r * k), 255)


def rim(s, cx, cy):
    """Colours each edge pixel by the angle of its outward normal to the light, a continuous gradient round the seal;
    the ring inside it becomes MC's shallow groove (under the face on the lit side, halfway on the shadow side)."""
    im = s._img()
    for y in range(s.h):
        for x in range(s.w):
            ring = 1 if s.get(x, y) and edge(s, x, y, 1) else 2 if s.get(x, y) and edge(s, x, y, 2) else 0
            if not ring:
                continue
            nx, ny = x - cx, y - cy
            facing = -(nx + ny) / max(1e-6, (nx * nx + ny * ny) ** 0.5) / 2**0.5
            red = next(r0 + (r1 - r0) * (facing - f0) / (f1 - f0)
                       for (f0, r0), (f1, r1) in zip(RIM_RED, RIM_RED[1:]) if facing <= f1)
            if ring == 2:
                face = im.getpixel((x, y))[0]
                red = min(face - 5, face * 0.6 + red * 0.4)
            im.putpixel((x, y), wax_red(red))


def blank_seal(size=SEAL_SIZE):
    """A blank seal drawn like MC's red_wax_blank (17 x 17) or colonist_wax_*_smaller (30 x 30): shifted discs for the
    relief, soft shading, the rim by the light's angle."""
    s = Sprite(size, size, palette=WAX)
    c = size // 2
    s.circle(c, c, c, L1, fill=True)
    s.circle(c + 1, c + 1, c, D0, fill=True, only="opaque")
    s.circle(c, c, c - 1, D2, fill=True, only="opaque")
    s.circle(c - 1, c - 1, c - 1, B0, fill=True, only=D2)
    s.circle(c - 1, c - 1, round(c * 0.65), B1, fill=True, only=B0)
    s.circle(c - 1, c, round(c * 0.4), B2, fill=True, only=B1)
    soft(s, 0.9 * size / SEAL_SIZE)
    rim(s, c, c)
    return s


def region(size, draw):
    """The pixels a shape covers: draw(sprite) paints it, in any colour, on a scratch size x size sprite."""
    t = Sprite(size, size)
    draw(t)
    return {(x, y) for x in range(size) for y in range(size) if t.get(x, y)}


def press(s, pixels):
    """Presses pixels into the wax as red_wax_citizens and red_wax_settings do: flat floor, wall shadow on the
    recess's top/left inner edges, and two lips in the wax around it: the far lip (wax right of or under the hole)
    catches the light, the near lip (wax left of or above it) slopes down, darker. Only pixels at least 3 px inside
    the edge are pressed: MC keeps its rim and groove whole round a symbol."""
    pixels = {(x, y) for x, y in pixels if s.get(x, y) and not edge(s, x, y, 1) and not edge(s, x, y, 2)}
    for x, y in pixels:
        in_shadow = any(p not in pixels for p in ((x - 1, y), (x, y - 1), (x - 1, y - 1)))
        s.px(x, y, SH if in_shadow else FLOOR)
    face = {(x, y) for y in range(s.h) for x in range(s.w) if s.get(x, y) and not edge(s, x, y, 1)} - pixels
    im = s._img()
    for x, y in face:
        if (x - 1, y) in pixels or (x, y - 1) in pixels:
            im.putpixel((x, y), wax_red(FAR_LIP_RED))
        elif (x + 1, y) in pixels or (x, y + 1) in pixels:
            im.putpixel((x, y), wax_red(NEAR_LIP_RED))


def seal(draw, size=SEAL_SIZE):
    """A blank seal of size x size with the shape draw(sprite) paints pressed into it."""
    s = blank_seal(size)
    press(s, region(size, draw))
    return s


def export(s, path):
    """Writes the sprite x4 nearest-neighbour, as the @2x file a window loads."""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    s.composite().resize((s.w * EXPORT_SCALE, s.h * EXPORT_SCALE), Image.NEAREST).save(path)
