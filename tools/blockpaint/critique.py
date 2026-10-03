"""Critique of a painted model, island by island (spec 2026-10-03 blockpaint surfaces, validation): measures each face
island of at least MIN_SIDE texels and compares it with Hytale's furniture, measured island by island
(docs/research/blockpaint-surfaces.md § 2): too flat, too noisy, uniform noise, micro dominant, shadows that do not
turn. python tools/blockpaint/critique.py <model.blockymodel> (its texture beside it, same name, .png)."""

import colorsys
import json
import sys
from collections import namedtuple
from pathlib import Path

from PIL import Image

from models import face_span, walk

MIN_SIDE = 6
# Hytale's furniture, 1741 islands: per kind, the 10th percentile of the mean step (below: too flat), its 90th
# percentile and that of the share of jumps over JUMP (above: too noisy).
Budget = namedtuple("Budget", "flat step jumps")
BUDGETS = {"warm": Budget(2.9, 15.3, 0.42), "grey": Budget(2.9, 21.9, 0.56), "colour": Budget(5.0, 20.3, 0.53)}
JUMP = 12
# Micro against meso and macro: Hytale's 90th percentile is about 1 (its median 0.5).
MICRO_SHARE = 1.03
# Uniform noise: jumps in more than UNIFORM of the island's CELL x CELL cells, at the scale of the texel (micro share
# over MICRO_UNIFORM): about one Hytale island in ten.
UNIFORM, CELL, MICRO_UNIFORM = 0.9, 3, 0.7
# A measure of one island: its kind (warm: wood and leather; grey: metal and stone; colour: cloth and paint), mean
# step to the next texel, share of jumps, micro / (meso + macro), share of cells holding a jump, and whether its
# darkest fifth has a lower hue than its lightest (shadows turning towards red and violet; None: not warm).
Measure = namedtuple("Measure", "kind step jumps micro_share spread darker_turns")


def lightness(p):
    return 0.3 * p[0] + 0.59 * p[1] + 0.11 * p[2]


def kind_of(colours):
    """warm, grey or colour, from the island's mean colour."""
    hue, sat, _ = colorsys.rgb_to_hsv(*(sum(c[k] for c in colours) / len(colours) / 255 for k in range(3)))
    return "grey" if sat < 0.18 else "warm" if hue * 360 <= 50 or hue * 360 >= 345 else "colour"


def box_mean(grid, x, y, r):
    """The mean of grid ([column][row]) over the box of radius r round (x, y), cut at the grid's sides."""
    w, h = len(grid), len(grid[0])
    values = [grid[i][j] for i in range(max(0, x - r), min(w, x + r + 1))
              for j in range(max(0, y - r), min(h, y + r + 1))]
    return sum(values) / len(values)


def scales(grid, w, h):
    """micro / (meso + macro) of a lightness grid: texel against its 3 x 3 mean, 3 x 3 against 7 x 7, 7 x 7 against
    the island's mean."""
    whole = sum(map(sum, grid)) / (w * h)
    m1 = [[box_mean(grid, x, y, 1) for y in range(h)] for x in range(w)]
    m3 = [[box_mean(grid, x, y, 3) for y in range(h)] for x in range(w)]
    cells = [(x, y) for x in range(w) for y in range(h)]
    micro = sum(abs(grid[x][y] - m1[x][y]) for x, y in cells)
    larger = sum(abs(m1[x][y] - m3[x][y]) + abs(m3[x][y] - whole) for x, y in cells)
    return micro / max(larger, 1e-6)


def measure(island):
    """The Measure of an opaque island image, None when any texel is transparent or when it has no two texels side
    by side."""
    w, h = island.size
    px = island.load()
    colours = [px[x, y] for x in range(w) for y in range(h)]
    if any(c[3] < 200 for c in colours) or w * h < 2:
        return None
    steps, jumped = [], set()
    for x in range(w):
        for y in range(h):
            for dx, dy in ((1, 0), (0, 1)):
                if x + dx < w and y + dy < h:
                    step = sum(abs(px[x, y][k] - px[x + dx, y + dy][k]) for k in range(3)) / 3
                    steps.append(step)
                    if step > JUMP:
                        jumped.add((x // CELL, y // CELL))
    cells = ((w + CELL - 1) // CELL) * ((h + CELL - 1) // CELL)
    kind = kind_of(colours)
    return Measure(kind, sum(steps) / len(steps), sum(s > JUMP for s in steps) / len(steps),
                   scales([[lightness(px[x, y]) for y in range(h)] for x in range(w)], w, h), len(jumped) / cells,
                   darker_turns(colours) if kind == "warm" else None)


def darker_turns(colours):
    """Whether the darkest fifth of colours turns from the lightest fifth towards red and violet: a hue lower by less
    than a quarter turn (a blue-grey shadow on brown wood is a quarter turn and more away)."""
    ordered = sorted(colours, key=lightness)
    fifth = max(1, len(ordered) // 5)

    def hue(group):
        return 360 * colorsys.rgb_to_hsv(*(sum(c[k] for c in group) / len(group) / 255 for k in range(3)))[0]

    gap = (hue(ordered[:fifth]) - hue(ordered[-fifth:]) + 180) % 360 - 180
    return -90 < gap < 0


def verdicts(m):
    """What is wrong with an island's measure, against Hytale's furniture: [] when nothing."""
    budget, found = BUDGETS[m.kind], []
    if m.step < budget.flat:
        found.append("trop plat")
    if m.step > budget.step or m.jumps > budget.jumps:
        found.append("trop bruité")
    if m.spread > UNIFORM and m.micro_share > MICRO_UNIFORM:
        found.append("bruit uniforme")
    if m.micro_share > MICRO_SHARE:
        found.append("micro dominant")
    if m.darker_turns is False:
        found.append("ombres sans virage")
    return found


def islands_of(nodes):
    """(node name, side, u, v, w, h) of each face island of at least MIN_SIDE texels, not turned nor mirrored."""
    for n in walk(nodes):
        shape = n["shape"]
        if shape["type"] != "box":
            continue
        size = tuple(shape["settings"]["size"][a] for a in "xyz")
        for side, face in shape.get("textureLayout", {}).items():
            if face.get("angle", 0) or any(face.get("mirror", {}).values()):
                continue
            w, h = (int(round(c)) for c in face_span(side, size))
            if min(w, h) >= MIN_SIDE:
                yield n["name"], side, int(face["offset"]["x"]), int(face["offset"]["y"]), w, h


def critique(model):
    """[(node name, side, Measure, verdicts)] of a .blockymodel and its texture beside it."""
    nodes = json.loads(Path(model).read_text(encoding="utf-8"))["nodes"]
    image = Image.open(Path(model).with_suffix(".png")).convert("RGBA")
    report = []
    for name, side, u, v, w, h in islands_of(nodes):
        m = measure(image.crop((u, v, u + w, v + h)))
        if m:
            report.append((name, side, m, verdicts(m)))
    return report


def main(args):
    for model in args:
        report = critique(model)
        flagged = [row for row in report if row[3]]
        print(f"{model}: {len(report)} islands, {len(flagged)} flagged")
        for name, side, m, found in flagged:
            print(f"  {name} {side} ({m.kind}, step {m.step:.1f}, jumps {m.jumps:.0%}, micro {m.micro_share:.2f}): "
                  f"{', '.join(found)}")


if __name__ == "__main__":
    main(sys.argv[1:])
