"""What the rod designs (rod_designs, rod_designs_rare) share: the usual reel axle, where most cranks' hinges turn about
x in the Reel animations (a design may set its reel elsewhere, its crank's hinge on that reel's axle), and the shapes
several designs build from."""

AXLE_Y = 12
AXLE_Z = -7


def ring_plate(name, x, size, mat, y=AXLE_Y, z=AXLE_Z):
    """A round reel flange of side size at x, as three crossed boxes (a stepped octagon), thin across x."""
    return [(f"{name}_A", (x, y, z), (1, size, size - 4), mat), (f"{name}_B", (x, y, z), (1, size - 4, size), mat),
            (f"{name}_C", (x, y, z), (1, size - 2, size - 2), mat)]


def flame(name, x, top, side, mat, tip):
    """A stepped flame fin on the +x (side 1) or -x (side -1) face of a section at x, rising to top: three boxes, wide
    at the root and narrowing outward, the last in the tip's brighter colour."""
    return [(f"{name}_A", (side * x, top - 3, 0), (1, 4, 3), mat),
            (f"{name}_B", (side * (x + 1), top - 1.5, 0), (1, 3, 2), mat),
            (f"{name}_C", (side * (x + 2), top, 0), (1, 2, 1), tip)]
