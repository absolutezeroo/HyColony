"""Maps derived from what the bake knows of a texel (bake.Texel), each from 0 to 1, for the masks of the surface
effects (spec 2026-10-03 blockpaint surfaces, maps): where a texel faces, how much sky it sees, how exposed it is, how
near the ground and where water would stay."""

# Texels from an open border within which a surface counts as exposed (fully on the border, not at all at RIM_REACH).
RIM_REACH = 3


def up(t):
    """How much the texel faces up: 1 on a top face, 0 on sides and below."""
    return max(0.0, t.normal[1])


def sky(t):
    """How much sky the texel sees: facing up and open above it."""
    return up(t) * (1.0 - t.occlusion)


def exposure(t):
    """How exposed the texel is to the world: under the sky, or on an open border of its face (a seam is no border)."""
    return max(sky(t), max(0.0, 1.0 - t.rim / RIM_REACH))


def ground(t):
    """How near the floor the texel lies (bake.Texel.ground): 1 at the foot of a grounded model, 0 from
    bake.GRIME_HEIGHT up and anywhere on a model that stands on no floor."""
    return t.ground


def water(t):
    """Where water would stay: on surfaces facing up, the more so the more enclosed."""
    return up(t) * t.occlusion
