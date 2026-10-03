"""The large quarry's hut block (spec 2026-10-02 hut models, § quarries, plantation and florist; decoration until the
quarrier's job is ported), built by a one-off script, two blocks tall: sheerlegs of three poles sunk in footing
stones and lashed at the top, a pulley, a rope down to a hook and a cut block hanging in a sling that wraps it, a
winch with its crank between the front legs, cut blocks below. The hanging load swings gently (animation)."""

from brushes import metal, stone, wood
from materials import CUT_STONE, ROCK, rope
from motion import blockyanim, leaning, track, wave

MODEL = "Blocks/HyColony/Huts/Quarry_Large"
ICON = "Hut_Quarry_Large"
PICTURES = frozenset()
# The hanging load, as a heavy block on a rope moves: it turns slowly on the rope, TWIST_DEGREES each way over the
# loop of LOOP_TICKS (1/60 s, the blockyanim time unit), and barely swings, a pendulum of about a block of rope
# (period 2 s, SWING_CYCLES in the loop), SWING_DEGREES to and fro and a little less side to side. At every key the
# hook, sling and load stay at least 1.03 units from every fixed box, the winch's coil the closest (spec).
LOOP_TICKS, TWIST_DEGREES = 480, 6
SWING_CYCLES, SWING_DEGREES = 4, 0.8
# Node name stem (before the first '_') -> material.
STEMS = {"Leg": "pole", "Lash": "rope", "Rope": "rope", "Haul": "rope", "Sling": "rope", "Drum": "drum",
         "Pulley": "iron", "Hook": "iron", "Axle": "iron", "Crank": "iron", "Hung": "cut", "Ground": "cut",
         "Foot": "rock", "Chip": "rock"}


def material(name, side):
    """The drum's coil of rope, then by the name's stem."""
    return "rope" if name == "Drum_Coil" else STEMS[name.split("_")[0]]


def tiles(assets):
    """Material -> its 32 px tile or brush."""
    return {
        "pole": wood((122, 86, 54), plank=99), "rope": rope(), "iron": metal((96, 98, 106)),
        "drum": wood((140, 100, 62), plank=99), "cut": stone(CUT_STONE, chunk=(8, 6)), "rock": stone(ROCK),
    }


def animation(nodes):
    """The looping blockyanim of the rope node (pivoting under the pulley, carrying hook, sling and load): a slow
    twist about the rope and a slight pendulum, a quarter period apart to and fro and side to side."""
    turn = leaning(wave(SWING_DEGREES, SWING_CYCLES, LOOP_TICKS), wave(TWIST_DEGREES, 1, LOOP_TICKS),
                   wave(SWING_DEGREES * 0.6, SWING_CYCLES, LOOP_TICKS, 0.25))
    return blockyanim({"Rope": track(turn, LOOP_TICKS)}, LOOP_TICKS)
