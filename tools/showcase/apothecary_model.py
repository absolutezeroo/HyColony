"""The apothecary's workbench (spec 2026-10-04 blockpaint apothecary § 1): a massive herbalist's bench, two and a half
blocks wide and over two high, built from boxes in Hytale's units (32 per block), its foot's centre at the origin,
its front towards +z. Below, a case of nine labelled drawers and two iron-banded doors; the worktop laden with the
ledger, the mortar, the scale, a bowl of herbs, scrolls and a candle, two cloths hanging over its front; above, a hutch
of two shelves of bottles, jars, books and a copper still, potted plants on its crown, herbs hanging under it, a
satchel on its side; a stool before it. Every part is a static box of its own, no stretch; catalog trims the faces
nothing sees."""

from models import box_shape, node

ALL = ("front", "back", "left", "right", "top", "bottom")
# The case's front (z), the worktop's top (y), the shelves' tops (y) and their depth's middle (z).
CASE_FRONT, WORKTOP, SHELF_1, SHELF_2, SHELF_Z = 12, 33, 47, 59, -5
# The bottles and jars of each shelf: (name, width, height, x, material); a bottle (glass) takes a cork on top.
SHELF_1_ITEMS = (("Bottle_1", 2, 5, -33, "glass_green"), ("Bottle_2", 3, 6, -29, "glass_amber"),
                 ("Bottle_3", 2, 4, -25, "glass_red"), ("Bottle_4", 3, 5, -21, "glass_blue"),
                 ("Jar_1", 5, 6, -13, "jar"), ("Jar_2", 4, 5, -6, "jar"), ("Bottle_5", 2, 6, 1, "glass_purple"),
                 ("Bottle_6", 3, 4, 5, "glass_green"), ("Jar_3", 4, 5, 33, "jar"))
SHELF_2_ITEMS = (("Jar_4", 5, 6, -31, "jar"), ("Bottle_7", 2, 5, -25, "glass_amber"),
                 ("Bottle_8", 3, 5, -21, "glass_red"), ("Tin", 4, 3, -14, "brass"), ("Jar_5", 4, 5, 32, "jar"))


def parts():
    """{part: (size (x, y, z), centre (x, y, z))} of the whole bench."""
    built = {}
    for group in (case, drawers, doors, worktop, worktop_items, hutch, shelf_items, crown, hanging, stool):
        built.update(group())
    return built


def case():
    """The feet and the case."""
    feet = {f"Foot_{n}": ((4, 4, 4), (x, 2, z)) for n, (x, z) in enumerate(((-37, 9), (37, 9), (-37, -9), (37, -9)))}
    return feet | {"Case": ((80, 26, 24), (0, 17, 0))}


def drawers():
    """Nine small drawers on the case's left, three by three, each with a brass knob."""
    built = {}
    for row, y in enumerate((25.5, 17.5, 9.5)):
        for column, x in enumerate((-32, -21, -10)):
            built[f"Drawer_{row}{column}"] = ((10, 7, 1), (x, y, CASE_FRONT + 0.5))
            built[f"Knob_{row}{column}"] = ((2, 2, 1), (x, y - 1, CASE_FRONT + 1.5))
    return built


def doors():
    """Two doors on the case's right, each banded with iron at top and bottom and pulled by a brass handle."""
    built = {}
    for name, x, handle in (("A", 7, 15), ("B", 28, 20)):
        built[f"Door_{name}"] = ((20, 22, 1), (x, 17, CASE_FRONT + 0.5))
        built[f"Band_{name}_Top"] = ((18, 1, 1), (x, 25, CASE_FRONT + 1.5))
        built[f"Band_{name}_Bottom"] = ((18, 1, 1), (x, 9, CASE_FRONT + 1.5))
        built[f"Handle_{name}"] = ((2, 3, 1), (handle, 17, CASE_FRONT + 1.5))
    return built


def worktop():
    """The worktop, overhanging the case, and the two cloths hanging over its front."""
    return {"Worktop": ((84, 3, 28), (0, 31.5, 0)),
            "Cloth_1": ((10, 12, 1), (-21, 27, 14.5)), "Cloth_2": ((9, 10, 1), (28, 28, 14.5))}


def worktop_items():
    """The open ledger, the mortar and pestle, the scale, a bowl of herbs, two scrolls and a candle."""
    top = WORKTOP
    return {
        "Ledger": ((14, 1, 9), (-14, top + 0.5, 6)), "Ledger_Pages": ((13, 1, 8), (-14, top + 1.5, 6)),
        "Mortar": ((5, 4, 5), (2, top + 2, 5)), "Pestle": ((1, 5, 1), (3, top + 4, 5)),
        "Scale_Base": ((7, 1, 5), (14, top + 0.5, 4)), "Scale_Post": ((1, 9, 1), (14, top + 5.5, 4)),
        "Scale_Beam": ((12, 1, 1), (14, top + 10.5, 4)),
        "Chain_L": ((1, 3, 1), (9, top + 8.5, 4)), "Chain_R": ((1, 3, 1), (19, top + 8.5, 4)),
        "Pan_L": ((5, 1, 5), (9, top + 6.5, 4)), "Pan_R": ((5, 1, 5), (19, top + 6.5, 4)),
        "Bowl": ((6, 2, 6), (29, top + 1, 6)), "Bowl_Herbs": ((5, 1, 5), (29, top + 2.5, 6)),
        "Scroll_1": ((8, 2, 2), (-31, top + 1, 9)), "Scroll_2": ((7, 2, 2), (-30, top + 1, 6)),
        "Candle_Holder": ((3, 1, 3), (-35, top + 0.5, 2)), "Candle": ((2, 4, 2), (-35, top + 3, 2)),
        "Candle_Flame": ((1, 2, 1), (-35, top + 6, 2)), "Herb_Bundle": ((6, 2, 2), (22, top + 1, 11)),
    }


def hutch():
    """The hutch on the worktop's back: its back, two sides, two shelves and its crown."""
    return {"Hutch_Back": ((76, 36, 2), (0, 51, -11)),
            "Hutch_Side_L": ((3, 36, 12), (-38.5, 51, -6)), "Hutch_Side_R": ((3, 36, 12), (38.5, 51, -6)),
            "Shelf_1": ((74, 2, 10), (0, SHELF_1 - 1, SHELF_Z)), "Shelf_2": ((74, 2, 10), (0, SHELF_2 - 1, SHELF_Z)),
            "Crown": ((84, 3, 16), (0, 70.5, -6))}


def shelf_items():
    """The shelves' bottles (corked), jars and tin, two books lying on the first, two standing and the copper still on
    the second."""
    built = {}
    for top, items in ((SHELF_1, SHELF_1_ITEMS), (SHELF_2, SHELF_2_ITEMS)):
        for name, width, height, x, _ in items:
            built[name] = ((width, height, width), (x, top + height / 2, SHELF_Z))
            if name.startswith("Bottle"):
                cork = max(1, width - 1)
                built["Cork" + name[len("Bottle"):]] = ((cork, 1, cork), (x, top + height + 0.5, SHELF_Z))
    return built | {
        "Book_1": ((9, 2, 6), (17, SHELF_1 + 1, SHELF_Z)), "Book_2": ((8, 2, 6), (17, SHELF_1 + 3, SHELF_Z)),
        "Book_3": ((2, 8, 6), (22, SHELF_2 + 4, SHELF_Z)), "Book_4": ((2, 7, 6), (24.5, SHELF_2 + 3.5, SHELF_Z)),
        "Still_Body": ((7, 7, 7), (6, SHELF_2 + 3.5, SHELF_Z)), "Still_Lid": ((5, 1, 5), (6, SHELF_2 + 7.5, SHELF_Z)),
        "Still_Spout": ((6, 1, 1), (12.5, SHELF_2 + 5.5, SHELF_Z)),
    }


def crown():
    """Two potted plants on the crown."""
    built = {}
    for name, x in (("A", -28), ("B", 28)):
        built[f"Plant_Pot_{name}"] = ((6, 6, 6), (x, 75, -6))
        built[f"Plant_{name}"] = ((9, 4, 9), (x, 80, -6))
    return built


def hanging():
    """Three bundles of herbs hanging under the crown's front, and the satchel hung on the hutch's right side."""
    return {"Herbs_1": ((2, 9, 2), (-35, 63.5, 0)), "Herbs_2": ((2, 8, 2), (-31, 64, 0)),
            "Herbs_3": ((2, 9, 2), (-27, 63.5, 0)),
            "Hook": ((2, 2, 1), (40.5, 62, -5)), "Satchel_Strap": ((1, 12, 1), (40.5, 55, -5)),
            "Satchel": ((3, 10, 9), (41.5, 44, -5)), "Satchel_Flap": ((1, 5, 9), (43.5, 46.5, -5))}


def stool():
    """The stool before the bench's left: a seat on four legs."""
    legs = {f"Stool_Leg_{n}": ((2, 18, 2), (x, 9, z))
            for n, (x, z) in enumerate(((-33, 21), (-23, 21), (-33, 31), (-23, 31)))}
    return legs | {"Stool_Seat": ((12, 2, 12), (-28, 19, 26))}


def nodes():
    """The bench's nodes: one static box per part (parts), every face shown until catalog trims them, numbered."""
    built = []
    for number, (name, (size, centre)) in enumerate(parts().items(), 1):
        part = node(name, centre, box_shape(size, ALL))
        part["id"] = str(number)
        part["shape"]["settings"]["isStaticBox"] = True
        built.append(part)
    return built
