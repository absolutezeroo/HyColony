"""Looping blockyanim orientation tracks sampled from smooth motions: a node's turn as a function of time, keyed every
STEP ticks (1/60 s, the blockyanim time unit), so that a sway reads as continuous motion rather than a few poses.
Hytale has no physics for a model's nodes: hanging and swaying things are animated.

Keys are linear: smooth keys ease into each key and stop there (Hytale's own swings, as
Decorative_Sets/Human_Ruins/Banner_Swing, key only the ends of a swing), which a key every quarter second turned into a
stutter (seen by the user in Blockbench). Ten linear keys a second keep the motion going."""

import math

from models import multiply

STEP = 6


def about(axis, degrees):
    """The quaternion turning degrees about the unit axis."""
    half = math.radians(degrees) / 2
    return tuple(c * math.sin(half) for c in axis) + (math.cos(half),)


def wave(amplitude, cycles, duration, phase=0.0):
    """degrees(t): amplitude * sin, cycles whole periods over duration ticks (so that the loop closes), phase in
    periods."""
    return lambda t: amplitude * math.sin(2 * math.pi * (cycles * t / duration + phase))


def track(turn, duration):
    """A node's tracks: its orientation turn(t) (a quaternion) keyed every STEP ticks from 0 to duration."""
    keys = [{"time": t, "delta": dict(zip("xyzw", (round(c, 5) for c in turn(t)))), "interpolationType": "linear"}
            for t in range(0, duration + 1, STEP)]
    return {"position": [], "shapeStretch": [], "shapeVisible": [], "shapeUvOffset": [], "orientation": keys}


def leaning(about_x, about_y, about_z):
    """turn(t): about_y(t) degrees about y, then about_x(t) about x and about_z(t) about z (each a function of t)."""
    return lambda t: multiply(about((0, 1, 0), about_y(t)),
                              multiply(about((1, 0, 0), about_x(t)), about((0, 0, 1), about_z(t))))


def blockyanim(tracks, duration):
    """The looping blockyanim of the node tracks."""
    return {"formatVersion": 1, "duration": duration, "holdLastKeyframe": False, "nodeAnimations": tracks}
