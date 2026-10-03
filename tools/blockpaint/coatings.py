"""Coats laid over a substrate (spec 2026-10-03 blockpaint surfaces, coats), named *_coat: paint is a module and
misc.wax a brush. Each is a layers.Coat: its colour, how it lies (cover, clear, tint), its thickness (how deep a
scratch must go to take it off), its gloss and its film family (compat.py)."""

from layers import Coat


def primer_coat(rgb=(150, 74, 58)):
    """Primer: a thin, matte covering coat under paint, showing where the paint above it is scratched."""
    return Coat(rgb, "cover", thickness=0.5)


def paint_coat(rgb):
    """Paint: an opaque, matte coat."""
    return Coat(rgb, "cover", thickness=1.0)


def varnish_coat(rgb=(196, 150, 90), opacity=0.25):
    """Varnish: a clear, glossy coat, warming what is under it a little."""
    return Coat(rgb, "clear", thickness=0.4, opacity=opacity, gloss=0.6, family="varnish_film")


def stain_coat(rgb):
    """Wood stain: changes the wood's colour and keeps its grain; thin, so worn edges lose it first."""
    return Coat(rgb, "tint", thickness=0.2, family="wood")


def thin_paint_coat(rgb, opacity=0.7):
    """Thin paint: a wash of paint, the material faintly showing through; worn through quickly."""
    return Coat(rgb, "clear", thickness=0.4, opacity=opacity)


def lacquer_coat(rgb):
    """Lacquer: a smooth, coloured, glossy coat."""
    return Coat(rgb, "cover", thickness=0.8, gloss=0.5)


def enamel_coat(rgb):
    """Enamel: a hard, even, shining coat, the toughest to scratch off."""
    return Coat(rgb, "cover", thickness=1.4, gloss=0.8)


def glaze_coat(rgb=(170, 200, 210), opacity=0.35):
    """Glaze: a glassy coat over ceramic, tinting it and shining."""
    return Coat(rgb, "clear", thickness=0.6, opacity=opacity, gloss=0.9, family="varnish_film")


def plating_coat(rgb=(226, 184, 74)):
    """Plating: a thin skin of another metal (gold, silver, copper on steel), worn off its edges first."""
    return Coat(rgb, "cover", thickness=0.3, gloss=0.6, family="metal_film")


def gilding_coat(rgb=(232, 186, 78)):
    """Gilding: a very thin gold leaf that flakes off at the least wear."""
    return Coat(rgb, "cover", thickness=0.15, gloss=0.7, family="metal_film")


def silvering_coat(rgb=(206, 210, 216)):
    """Silvering: a thin silver skin, bright and shining."""
    return Coat(rgb, "cover", thickness=0.2, gloss=0.8, family="metal_film")


def tar_coat(rgb=(32, 28, 26)):
    """Tar or pitch: a thick, black, dull coat (hulls, roofs, ropes)."""
    return Coat(rgb, "cover", thickness=1.2, gloss=0.2)


def resin_coat(rgb=(196, 132, 52), opacity=0.4):
    """Resin: an amber, glossy layer through which the material shows, warmed."""
    return Coat(rgb, "clear", thickness=0.6, opacity=opacity, gloss=0.6, family="varnish_film")


def protective_coat(opacity=0.08):
    """A protective coat: clear, barely there, a faint sheen."""
    return Coat((230, 226, 214), "clear", thickness=0.3, opacity=opacity, gloss=0.3, family="varnish_film")


def oil_coat(rgb=(150, 96, 48), family="wood"):
    """An oil finish: deepens wood or leather (family), its grain kept, a soft sheen."""
    return Coat(rgb, "tint", thickness=0.2, gloss=0.25, family=family)


def wax_coat(opacity=0.12):
    """A wax coat: soft, faintly milky, satin."""
    return Coat((240, 228, 196), "clear", thickness=0.2, opacity=opacity, gloss=0.35, family="varnish_film")


def dye_coat(rgb, family="textile"):
    """A dye (fabric or leather: family): changes the colour and keeps the weave or the grain; it soaks in."""
    return Coat(rgb, "tint", thickness=0.1, family=family)


def blackening_coat(rgb=(52, 52, 56)):
    """Blackening: a black conversion skin on iron, the metal's grain kept."""
    return Coat(rgb, "tint", thickness=0.1, gloss=0.2, family="ferrous")


def bluing_coat(rgb=(58, 66, 92)):
    """Bluing: a blue-black conversion skin on steel."""
    return Coat(rgb, "tint", thickness=0.1, gloss=0.3, family="ferrous")


def browning_coat(rgb=(104, 76, 50)):
    """Browning: a brown conversion skin on iron (old gun barrels)."""
    return Coat(rgb, "tint", thickness=0.1, gloss=0.2, family="ferrous")


def heat_coat(rgb=(132, 108, 150)):
    """Heat treatment: the straw to purple-blue colour heat leaves on steel."""
    return Coat(rgb, "tint", thickness=0.1, family="ferrous")


COATS = {"primer": primer_coat, "paint": paint_coat, "thin_paint": thin_paint_coat, "lacquer": lacquer_coat,
         "enamel": enamel_coat, "varnish": varnish_coat, "stain": stain_coat, "glaze": glaze_coat,
         "plating": plating_coat, "gilding": gilding_coat, "silvering": silvering_coat, "tar": tar_coat,
         "resin": resin_coat, "protective": protective_coat, "oil": oil_coat, "wax": wax_coat, "dye": dye_coat,
         "blackening": blackening_coat, "bluing": bluing_coat, "browning": browning_coat, "heat": heat_coat}
