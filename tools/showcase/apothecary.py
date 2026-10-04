"""The apothecary's workbench as blockpaint paints it (spec 2026-10-04 blockpaint apothecary), the showcase of the
whole pipeline: its materials and coats, roles and usage, history, decals and visual importance, in three states
(STATES: new, used, neglected). Its model is apothecary_model.py's."""

from types import SimpleNamespace

import history
from brushes import clay, crystal, paper, terracotta, wood
from coatings import blackening_coat, glaze_coat, paint_coat, primer_coat, stain_coat, varnish_coat
from composer import Composer
from conditions import ANCIENT, HUMID_INTERIOR, NEGLECTED, NEW, OLD, PRISTINE, WORN
from decals import Decal
from effects import material as layered
from illustration import Illustration
from materials import leaf
from metals import copper
from metalwork import metal
from misc import glass, wax
from organic import leather
from PIL import Image, ImageDraw
from textiles import linen
from woods import timber

from apothecary_model import SHELF_1_ITEMS, SHELF_2_ITEMS

PICTURES = frozenset({"pages"})
SEED = 11
# The ledger's open pages, laid out for their 13 x 8 island, written in lines.
INK = (70, 52, 40, 255)
# Node name prefix -> material, the first that matches (the bottles' and jars' own come from the model's tables).
PREFIXES = (("Foot", "walnut"), ("Case", "walnut"), ("Drawer", "walnut"), ("Knob", "brass"), ("Door", "walnut"),
            ("Band", "iron"), ("Handle", "brass"), ("Worktop", "oak"), ("Cloth_1", "linen"), ("Cloth_2", "cloth_red"),
            ("Ledger_Pages", "paper"), ("Ledger", "leather_red"), ("Mortar", "ceramic"), ("Pestle", "brass"),
            ("Scale", "iron"), ("Chain", "iron"), ("Pan", "brass"), ("Bowl_Herbs", "herbs"), ("Bowl", "ceramic"),
            ("Scroll", "paper"), ("Candle_Holder", "brass"), ("Candle_Flame", "flame"), ("Candle", "wax"),
            ("Herb", "herbs"), ("Hutch_Back", "painted"), ("Hutch", "walnut"), ("Shelf", "walnut"),
            ("Crown", "walnut"), ("Cork", "cork"), ("Book_1", "leather_green"), ("Book_2", "leather_red"),
            ("Book_3", "leather_brown"), ("Book_4", "leather_green"), ("Still", "copper"), ("Plant_Pot", "terracotta"),
            ("Plant", "herbs"), ("Hook", "iron"), ("Satchel", "leather_brown"), ("Stool", "walnut"))
ITEMS = {name: kind for name, _, _, _, kind in SHELF_1_ITEMS + SHELF_2_ITEMS}
GLASS = {"glass_green": (96, 200, 120), "glass_amber": (214, 144, 52), "glass_red": (196, 44, 56),
         "glass_blue": (70, 120, 210), "glass_purple": (150, 80, 190)}
# Roles (roles.py) by name prefix.
ROLE_PREFIXES = (("Foot", "leg"), ("Stool_Leg", "leg"), ("Case", "frame"), ("Crown", "frame"), ("Drawer", "drawer"),
                 ("Knob", "handle"), ("Handle", "handle"), ("Door", "door"), ("Band", "hinge"), ("Hook", "hinge"),
                 ("Worktop", "worktop"), ("Stool_Seat", "worktop"), ("Shelf", "shelf"), ("Hutch", "wall"),
                 ("Ledger", "ledger"), ("Bottle", "bottle"), ("Jar", "pot"), ("Mortar", "pot"), ("Bowl", "pot"),
                 ("Plant_Pot", "pot"), ("Pestle", "handle"), ("Pan", "rim"), ("Cloth", "cloth_panel"),
                 ("Satchel_Strap", "strap"))


def material(name, side):
    """The material of a node's face: the ledger's written pages on top, the shelves' bottles and jars as the model
    lists them, then by name prefix."""
    if name == "Ledger_Pages" and side == "top":
        return "pages"
    if name in ITEMS:
        return ITEMS[name]
    return next(m for prefix, m in PREFIXES if name.startswith(prefix))


def tiles(_assets):
    """Material -> its layered material (effects.material): substrates, their coats."""
    return {
        "walnut": layered(timber("walnut", "planed"), coats=(stain_coat((96, 58, 38)),)),
        "oak": layered(timber("oak", "sanded"), coats=(stain_coat((150, 102, 60)), varnish_coat())),
        "painted": layered(timber("pine", "rough_sawn"), coats=(primer_coat(), paint_coat((112, 142, 112)))),
        "cork": layered(wood((176, 136, 92), plank=99)),
        "iron": layered(metal("iron", "forged"), coats=(blackening_coat(),)),
        "brass": layered(metal("brass", "polished")), "copper": layered(copper()),
        **{kind: layered(glass(rgb)) for kind, rgb in GLASS.items()},
        "jar": layered(clay((222, 214, 196)), coats=(glaze_coat((206, 214, 210)),)),
        "ceramic": layered(clay((214, 206, 190)), coats=(glaze_coat(),)),
        "terracotta": layered(terracotta((178, 98, 62))),
        "paper": layered(paper((232, 222, 196))), "pages": layered(pages(), family="paper"),
        "leather_red": layered(leather((118, 40, 34))), "leather_green": layered(leather((52, 84, 58))),
        "leather_brown": layered(leather((104, 64, 38))),
        "linen": layered(linen()), "cloth_red": layered(linen((156, 52, 46))),
        "wax": layered(wax((232, 214, 170))),
        "flame": layered(crystal((255, 242, 176), (255, 184, 72), (232, 104, 34))),
        "herbs": layered(leaf((96, 140, 64))),
    }


def pages():
    """The ledger's two open pages: cream paper, a fold down the middle, lines of brown ink."""
    def brush(w, h, side):
        image = Image.new("RGBA", (w, h), (234, 224, 198, 255))
        draw = ImageDraw.Draw(image)
        draw.line([(w // 2, 0), (w // 2, h - 1)], fill=(180, 160, 124, 255))
        for y in range(1, h - 1, 2):
            draw.line([(1, y), (w // 2 - 2, y)], fill=INK)
            draw.line([(w // 2 + 2, y), (w - 2, y)], fill=INK)
        return image
    return brush


def label(w, h, side):
    """A small paper label: cream, a darker rim, one line of ink."""
    image = Image.new("RGBA", (w, h), (226, 214, 184, 255))
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 0, w - 1, h - 1], outline=(176, 156, 118, 255))
    if w > 2 and h > 1:
        draw.line([(1, h // 2), (w - 2, h // 2)], fill=INK)
    return image


def decals():
    """A label on each drawer above its knob, and on each jar's front."""
    found = {f"label_{row}{column}": Decal(f"Drawer_{row}{column}", "front", (3, 1, 4, 2), label)
             for row in range(3) for column in range(3)}
    for name, width, height, _, kind in SHELF_1_ITEMS + SHELF_2_ITEMS:
        if kind == "jar":
            found["label_" + name] = Decal(name, "front", (0, height // 3, width, 2), label)
    return found


def roles(names):
    """{part: role} of the parts names, by ROLE_PREFIXES."""
    return {name: next((r for prefix, r in ROLE_PREFIXES if name.startswith(prefix)), None) for name in names}


IMPORTANCE = {"Scale_Base": 3, "Scale_Post": 3, "Scale_Beam": 3, "Chain_L": 3, "Chain_R": 3, "Pan_L": 3,
              "Pan_R": 3, "Bottle_1": 3, "Ledger": 2, "Ledger_Pages": 2, "Bottle_3": 2, "Still_Body": 2,
              "Hutch_Back": 0}
# The middle drawer is opened a hundred times a day, the bottom right one hardly ever.
USAGE = {"Drawer_11": {"contact": 1.0}, "Knob_11": {"contact": 1.0}, "Drawer_22": {"contact": 0.1},
         "Knob_22": {"contact": 0.1}}
# What happened to it, once used: the green bottle leaked on its shelf, a corner of the worktop was mended, a band of
# the left door bled rust; neglected, rain from the crown ran down the hutch's back too.
USED_HISTORY = (history.chemical("Shelf_1", (-30, 0, 2), side="top"),
                history.repaired("Worktop", (34, 0, 8), side="top"),
                history.rust_from("Band_A_Top", side="front"))
NEGLECTED_HISTORY = USED_HISTORY + (history.water("Hutch_Back", severity=0.7),)
# The three states (spec § 5): name -> (condition, age, environment, history). Used is worn: a hut's USED is meant
# to stay discreet, and next to the new bench it hardly showed.
STATES = {"New": (PRISTINE, NEW, None, ()), "Used": (WORN, OLD, HUMID_INTERIOR, USED_HISTORY),
          "Neglected": (NEGLECTED, ANCIENT, HUMID_INTERIOR, NEGLECTED_HISTORY)}


def module(state, names):
    """The catalog module of the bench in state (STATES), its model Blocks/Showcase/Apothecary_<state>."""
    condition, age, environment, events = STATES[state]
    return SimpleNamespace(
        MODEL=f"Blocks/Showcase/Apothecary_{state}", ICON=f"Apothecary_{state}", PICTURES=PICTURES, SEED=SEED,
        material=material, tiles=tiles, DECALS=decals(), CONDITION=condition, AGE=age, ENVIRONMENT=environment,
        HISTORY=events, ROLES={k: v for k, v in roles(names).items() if v}, USAGE=USAGE, IMPORTANCE=IMPORTANCE,
        ILLUSTRATION=Illustration(), COMPOSER=Composer())
