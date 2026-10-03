"""What happened to a model (spec 2026-10-03 blockpaint surfaces § 3.6): a few readable events rather than a hundred
marks. Each has a source on the model, a direction (none: it radiates; gravity: it runs down), a radius and a falloff,
a severity (its degree) and an age (old: before the deposits; recent: after them), and the effects it lays. Everything
is measured in world space, so an event crosses from one face to the next, as a stain runs from a roof's edge down a
wall (γ-ton tracing, docs/research/blockpaint-surfaces.md § 5, simplified)."""

import math
from collections import namedtuple

import maps
import marks
import weathering
from models import FACE_AXES
from vectors import dot

# An event: its kind (for its name), source (point, edge, sky), direction (None, a vector, or GRAVITY), radius (world
# units), falloff (exponent of its fading; 0: whole up to the radius), severity (0 to 1: the degree its effects
# reach), age (0 recent to 1 old), the effects it lays ((effect, weight)) and, radiating, its shape (round, or square
# in the plane of each face: a patch).
Event = namedtuple("Event", "kind source direction radius falloff severity age effects shape", defaults=("round",))
# A source: the centre of a part's face (of the whole part without side, inside it for a box) shifted by offset
# (point), the middle of a face's border towards a world direction (edge), or every face under the sky (sky).
Source = namedtuple("Source", "kind part offset side towards", defaults=(None, (0.0, 0.0, 0.0), None, None))
GRAVITY = (0.0, -1.0, 0.0)
# Events this old or older happen before the deposits (their soot lies under the dust); younger ones after them.
PAST_AGE = 0.5
# A flow is this many world units wide at its source and narrows to a point at its radius.
FLOW_WIDTH = 3.0
# The blows of battle_damage lie within this many world units of the face's centre.
BLOW_SPREAD = 4.0
TOWARDS = {"up": (0, 1, 0), "down": (0, -1, 0), "left": (-1, 0, 0), "right": (1, 0, 0), "front": (0, 0, 1),
           "back": (0, 0, -1)}


def point(part, offset=(0.0, 0.0, 0.0), side="front"):
    """A source at the centre of part's face side, shifted by offset (world units); with side None, at the centre of
    all its texels (inside a box, where every face turns its back on it)."""
    return Source("point", part, tuple(offset), side)


def edge(part, side, towards):
    """A source at the middle of the border of part's face side that lies furthest towards (up, down, left, right,
    front, back)."""
    return Source("edge", part, side=side, towards=towards)


def sky():
    """A source everywhere under the sky (rain, sun)."""
    return Source("sky")


def locate(source, contexts):
    """The world point of source on the model whose texels are contexts (bake.survey); None for the sky. Fails on a
    part or face the model does not have."""
    if source.kind == "sky":
        return None
    texels = [t for t in contexts.values() if t.face[0].split("--")[0] == source.part
              and (source.side is None or t.face[1] == source.side)]
    if not texels:
        raise SystemExit(f"history: no {source.part} {source.side or ''} on the model")
    if source.kind == "edge":
        if source.towards not in TOWARDS:
            raise SystemExit(f"history: unknown direction {source.towards} (directions: {', '.join(TOWARDS)})")
        axis = TOWARDS[source.towards]
        far = max(dot(t.point, axis) for t in texels)
        texels = [t for t in texels if dot(t.point, axis) >= far - 0.5]
    centre = tuple(sum(t.point[k] for t in texels) / len(texels) for k in range(3))
    return tuple(c + o for c, o in zip(centre, source.offset))


def reach_of(event, t, origin):
    """How strongly event reaches texel t (0 to 1), its source at origin (locate): radiating, fading to its radius on
    the faces that do not turn their back on the source (square: in the source face's plane only); flowing, along
    its direction, narrowing, never upstream nor on a face turned the flow's way; the sky, maps.sky."""
    if event.source.kind == "sky":
        return maps.sky(t)
    v = tuple(p - o for p, o in zip(t.point, origin))
    if event.direction is None:
        distance = math.sqrt(dot(v, v))
        if event.shape == "square":
            # A patch lies in its face's plane: a texel off that plane (another face) never gets it.
            if abs(dot(v, t.normal)) > 0.5:
                return 0.0
            distance = max(abs(dot(v, t.u_dir)), abs(dot(v, t.v_dir)))
        # A face turned away from the source (its normal along v) is in its shadow, but at the source itself.
        if distance >= event.radius or distance > 1.0 and dot(t.normal, v) > 0:
            return 0.0
        return (1.0 - distance / event.radius) ** event.falloff
    # A flow runs along the faces it meets and leaves those turned its way: water drips off an underside, smoke
    # rises off a top and gathers under a lintel.
    if dot(t.normal, event.direction) > 0.5:
        return 0.0
    along = dot(v, event.direction)
    if along < -0.5 or along >= event.radius:
        return 0.0
    across = math.sqrt(max(0.0, dot(v, v) - along * along))
    width = FLOW_WIDTH * (1.0 - max(0.0, along) / event.radius) + 0.5
    return max(0.0, 1.0 - across / width) * (1.0 - max(0.0, along) / event.radius) ** event.falloff


def moment_of(age):
    """The moment an event of age happens in a face's run (effects.MOMENTS): past (before the deposits) or recent."""
    return "past" if age >= PAST_AGE else "recent"


def resolved(events, contexts):
    """[(event, its source's world point)] of events on the model whose texels are contexts (locate); an entry of
    events may be a tuple of events (fire, battle_damage). Fails on anything but events, such as a lone event not
    put in a tuple (HISTORY = impact(…) rather than (impact(…),))."""
    if isinstance(events, Event):
        raise SystemExit("history: HISTORY is a tuple of events: (impact(...),), not impact(...)")
    flat = [e for entry in events for e in (entry if isinstance(entry, tuple) and not isinstance(entry, Event)
                                            else (entry,))]
    wrong = [e for e in flat if not isinstance(e, Event)]
    if wrong:
        raise SystemExit(f"history: not an event: {wrong[0]!r}")
    return [(event, locate(event.source, contexts)) for event in flat]


def uses(events):
    """[(effect, degree)] of resolved events (resolved): each of an event's effects, its mask the event's reach times
    its weight times the effect's own mask (where the mark can be), at the event's moment, its degree the event's
    severity."""
    return [(base._replace(mask=reaching(base, weight, event, origin), moment=moment_of(event.age)), event.severity)
            for event, origin in events for base, weight in event.effects]


def reaching(base, weight, event, origin):
    """The mask of base laid by event, its source at origin: the event's reach times weight times base's own mask."""
    def mask(t, declared, island, i, j):
        return reach_of(event, t, origin) * weight * base.mask(t, declared, island, i, j)
    return mask


def impact(part, offset=(0.0, 0.0, 0.0), severity=0.8, age=0.2, side="front"):
    """A blow on part's face side: a dent, chips and scratches round where it struck (offset from the face's
    centre)."""
    return Event("impact", point(part, offset, side), None, 3.5, 0.6, severity, age,
                 ((mark("dent"), 1.0), (mark("scratch"), 0.9), (wear("chips"), 0.9)))


def battle_damage(count, part, severity=0.8, age=0.2, side="front"):
    """A few readable blows on part's face side (not a hundred marks), the biggest a little off its middle, the others
    further out, up to BLOW_SPREAD, each in the face's plane."""
    across, up = (tuple(1.0 if a == axis else 0.0 for a in "xyz") for axis in FACE_AXES[side])
    blows = []
    for k in range(count):
        angle = 2.4 * k + 0.7
        reach = BLOW_SPREAD * (0.35 + 0.65 * k / max(1, count - 1))
        offset = tuple(reach * math.cos(angle) * a + reach * math.sin(angle) * 0.6 * b for a, b in zip(across, up))
        blows.append(impact(part, offset, severity * (1.0 - 0.15 * k), age, side))
    return tuple(blows)


def dropped(part, side="front", severity=0.7, age=0.3):
    """A fall: chips and dents along the lowest edge of part's face."""
    return Event("dropped", edge(part, side, "down"), None, 5.0, 0.8, severity, age,
                 ((wear("chips"), 1.0), (mark("dent"), 0.6)))


def fire(part, offset=(0.0, 0.0, 0.0), severity=0.8, age=0.7, side="front"):
    """A fire before part's face side (its hearth offset from the face's centre): scorch round the hearth, soot rising
    above it."""
    hearth = point(part, offset, side)
    return (Event("fire", hearth, None, 5.0, 0.7, severity, age, ((mark("scorch"), 1.0),)),
            Event("smoke", hearth, (0.0, 1.0, 0.0), 14.0, 0.6, severity, age, ((mark("smoke_soot"), 1.0),)))


def water(part, side="front", severity=0.8, age=0.6):
    """Water run down from the top edge of part's face: a stain with its tide mark, down the faces below."""
    return Event("water", edge(part, side, "up"), GRAVITY, 16.0, 0.5, severity, age, ((mark("water_stain"), 1.0),))


def blood(part, offset=(0.0, 0.0, 0.0), severity=0.8, age=0.1, side="front"):
    """Blood spattered round a point of part's face side."""
    return Event("blood", point(part, offset, side), None, 7.0, 1.0, severity, age, ((mark("blood"), 1.0),))


def rust_from(part, offset=(0.0, 0.0, 0.0), severity=0.8, age=0.5, side="front"):
    """Rust run down from a rivet or a nail on part's face side, staining whatever is below."""
    return Event("rust_streak", point(part, offset, side), GRAVITY, 12.0, 0.8, severity, age,
                 ((mark("rust_streak"), 1.0),))


def chemical(part, offset=(0.0, 0.0, 0.0), severity=0.7, age=0.4, side="front"):
    """A chemical spill round a point of part's face side: the surface discoloured."""
    return Event("chemical", point(part, offset, side), None, 4.5, 0.6, severity, age, ((mark("discolour"), 1.0),))


def magic(part, offset=(0.0, 0.0, 0.0), severity=0.7, age=0.2, side="front"):
    """Magic corruption spreading from a point of part's face side: a glow tinting the surface."""
    return Event("magic", point(part, offset, side), None, 9.0, 1.0, severity, age, ((mark("glow"), 1.0),))


def repaired(part, offset=(0.0, 0.0, 0.0), radius=3.0, age=0.6, side="front"):
    """A repair on part's face side: a square patch of fresher material, its own shade, a seam round it."""
    return Event("repaired", point(part, offset, side), None, radius, 0.0, 1.0, age, ((mark("patch"), 1.0),),
                 "square")


def mark(name):
    return marks.MARKS[name]


def wear(name):
    return weathering.EFFECTS[name]
