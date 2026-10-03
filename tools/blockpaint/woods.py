"""Wood species and how the wood was worked (spec 2026-10-03 blockpaint surfaces, substrate and finish): oak, pine,
walnut and mahogany, their colours after Hytale's own (docs/research/blockpaint-surfaces.md § 7: hardwood, lightwood,
darkwood and redwood planks, lightened for props, which bake their own light), each with its figure (oak's rays,
pine's knots, walnut's waves, mahogany's ribbons), planed, sanded, rough sawn, carved or split. Like brushes.wood, the
grain runs along the island's long side or the part's declared axis."""

from collections import namedtuple

from brushes import along_u, coloured, family, jitter, painted, smooth, smooth2

# A species: its colour, how strongly its grain shows, and its figure.
Species = namedtuple("Species", "rgb grain figure")
SPECIES = {
    "oak": Species((150, 92, 54), 0.10, "rays"),
    "pine": Species((198, 172, 138), 0.07, "knots"),
    "walnut": Species((116, 68, 46), 0.09, "waves"),
    "mahogany": Species((158, 82, 48), 0.06, "ribbons"),
}
# How each finish changes the grain (a factor) and whether its boards show seams.
Finish = namedtuple("Finish", "grain seams")
FINISHES = {"planed": Finish(1.0, True), "sanded": Finish(0.55, True), "rough_sawn": Finish(1.3, True),
            "carved": Finish(0.8, False), "split": Finish(1.4, False)}


@family("wood")
def timber(species, finish="planed", plank=8):
    """A brush of species (SPECIES) worked to finish (FINISHES), boards plank texels wide."""
    kind, work = SPECIES[species], FINISHES[finish]

    def rule(x, y, w, h, side):
        a, b, width = (x, y, h) if along_u(w, h) else (y, x, w)
        board = b // plank
        # Each board its own shade, drifting along it (meso), over which the fibres (micro) stay finer.
        k = 1 + 0.08 * jitter(board, 5) + 0.07 * smooth(a / 7 + board * 3.1, board)
        k += kind.grain * work.grain * grain(a, b, board, kind.figure)
        k += figure(a, b, kind.figure) + worked(a, b, finish)
        if work.seams and width > plank and b % plank == plank - 1:
            k -= 0.2
        return coloured(kind.rgb, k)
    return painted(rule)


def grain(a, b, board, figure):
    """The grain at (a along, b across): fibres along the wood, wavy for walnut, in light and dark ribbons for
    mahogany; from -1 to 1."""
    if figure == "waves":
        b = b + round(1.5 * smooth(a / 6, 61))
    fibre = smooth(a / 9 + b * 0.8 + board, 62)
    if figure == "ribbons":
        return 0.5 * fibre + (0.5 if (b // 3) % 2 else -0.5)
    return fibre


def figure(a, b, kind):
    """A species' figure: oak's short light rays, pine's dark knots."""
    if kind == "rays" and jitter((a // 3) * 31 + b * 7, 63) > 0.92:
        return 0.08
    if kind == "knots":
        centre = (a // 12) * 12 + 6 + round(3 * jitter(a // 12, 64))
        across = 3 + round(2 * jitter(a // 12, 65))
        distance = ((a - centre) / 2.0) ** 2 + (b - across) ** 2
        if jitter(a // 12, 66) > 0.2 and distance < 2.5:
            return -0.22 if distance < 1.0 else -0.1
    return 0.0


def worked(a, b, finish):
    """The marks of how the wood was worked: saw lines across a rough sawn board, chisel facets on carved wood,
    raised ridges along split wood."""
    if finish == "rough_sawn" and (a + b // 3) % 5 == 0:
        return -0.05
    if finish == "carved":
        return 0.07 if smooth2(a / 2.5, b / 2.5, 67) > 0 else -0.04
    if finish == "split" and b % 5 in (1, 2):
        return 0.06
    return 0.0
