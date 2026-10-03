"""Generates HyColony's plate armor (MC ItemPlateArmor, spec
docs/superpowers/specs/2026-10-02-hycolony-plate-armor-design.md) and the knight's sword and shield: the models of its
four pieces (pieces.py) and of the weapons (arms.py), unwrapped one island per face, painted and lit (tools/blockpaint:
catalog.model_texture, in layers; materials: finish.py), their icons and their items, into HyColony's plugin resources.

Run by hand (`python tools/blockpaint armor`, or this script), then its outputs are committed: the build never runs
it. Needs Python 3.10+ and Pillow.

    python tools/armor/generate.py [path/to/Assets.zip]
"""

import json
import sys
from pathlib import Path

from PIL import Image

TOOLS = Path(__file__).resolve().parents[1]
sys.path += [str(TOOLS / "blockpaint"), str(TOOLS / "huts")]
from catalog import model_texture  # noqa: E402
import finish  # noqa: E402
from icons import ICON_SIZE, draw_model, frame, turned  # noqa: E402
from models import unwrap, walk  # noqa: E402
from pack import GRADLE_ASSETS, ROOT, Assets, rounded, save_png, write_json  # noqa: E402
from pack_rules import validate_pack  # noqa: E402
from arms import ARMS  # noqa: E402
from pieces import PIECES  # noqa: E402

RESOURCES = ROOT / "plugin" / "src" / "main" / "resources"
FOLDER = "Items/HyColony/Plate_Armor/"
# Piece -> (physical and projectile resistance, health, cosmetics hidden): Hytale's Cobalt tier (spec § 2), the
# cosmetics of its iron armour.
STATS = {
    "Head": (0.064, 12, ["EarAccessory", "Ear", "Haircut", "HeadAccessory"]),
    "Chest": (0.1152, 22, ["Overtop", "Cape"]),
    "Hands": (0.0512, 10, []),
    "Legs": (0.0896, 17, ["Pants", "Shoes"]),
}
# MC's plate armour durability (multiplier 37) is about 2.5 times its iron's (15, 37 / 15 = 2.47): Hytale's iron
# has 100.
DURABILITY = 250
# The icons face the piece's front, turned a little to its left and seen slightly from above, as Hytale's armour
# icons (IconProperties Rotation 22.5, 45, 22.5 shows a front three-quarter).
ICON_VIEW = turned(-30, 12, 0)
# The sword laid diagonally, point top right, as Hytale's sword icons; the shield seen from its face (+x).
WEAPON_VIEWS = {"Sword": turned(90, 0, -45), "Shield": turned(-75, 10, 0)}
ITEMS = RESOURCES / "Server/Item/Items/HyColony"


def main():
    assets = Assets(Path(sys.argv[1]) if len(sys.argv) > 1 else GRADLE_ASSETS)
    for piece, build in PIECES.items():
        draw(piece, build(), assets, ICON_VIEW)
        write_json(ITEMS / f"HyColony_Plate_Armor_{piece}.json", item(piece))
    for weapon, build in ARMS.items():
        draw(weapon, build(), assets, WEAPON_VIEWS[weapon])
        write_json(ITEMS / f"HyColony_Knight_{weapon}.json", weapon_item(weapon, assets))
    validate_pack(assets, RESOURCES)


def draw(name, nodes, assets, view):
    """Writes the model name (unwrapped, numbered), its painted and lit texture and its icon seen from view."""
    size = unwrap(nodes)
    for number, n in enumerate(walk(nodes), 1):
        n["id"] = str(number)
    image = model_texture(finish, nodes, assets)
    model = RESOURCES / "Common" / (FOLDER + name + ".blockymodel")
    model.parent.mkdir(parents=True, exist_ok=True)
    model.write_text(json.dumps(rounded({"format": "character", "lod": "auto", "nodes": nodes}), indent=2) + "\n",
                     encoding="utf-8", newline="\n")
    save_png(image, model.with_suffix(".png"))
    icon = Image.new("RGBA", (ICON_SIZE, ICON_SIZE), (0, 0, 0, 0))
    draw_model(icon, nodes, image, *frame(nodes, view), view)
    save_png(icon, RESOURCES / "Common" / icon_path(name))
    print(name, size)


def icon_path(name):
    return f"Icons/Items/HyColony/Plate_Armor_{name}.png"


def weapon_item(weapon, assets):
    """The knight's weapon as Hytale's cobalt one (its template parent, attacks and guard, and the sword's durability;
    spec § 5) under our name, model and icon, without its recipe (creative only)."""
    data = dict(assets.json(f"Server/Item/Items/Weapon/{weapon}/Weapon_{weapon}_Cobalt.json"))
    data.pop("Recipe", None)
    key = "hycolony.item.knight_" + weapon.lower()
    data.update({"TranslationProperties": {"Name": key + ".name", "Description": key + ".description"},
                 "Model": FOLDER + weapon + ".blockymodel", "Texture": FOLDER + weapon + ".png",
                 "Icon": icon_path(weapon)})
    return data


def item(piece):
    """The piece's item, as Hytale's armours: its slot, resistances, health and hidden cosmetics, no recipe (creative
    only until the blacksmith and research are ported, spec § 2)."""
    resistance, health, hidden = STATS[piece]
    armor = {"ArmorSlot": piece, "BaseDamageResistance": 0}
    if hidden:
        armor["CosmeticsToHide"] = hidden
    armor["DamageResistance"] = {kind: [{"Amount": resistance, "CalculationType": "Percent"}]
                                 for kind in ("Physical", "Projectile")}
    armor["StatModifiers"] = {"Health": [{"Amount": health, "CalculationType": "Additive"}]}
    key = "hycolony.item.plate_armor_" + piece.lower()
    return {
        "TranslationProperties": {"Name": key + ".name", "Description": key + ".description"},
        "Quality": "Rare",
        "ItemLevel": 35,
        "PlayerAnimationsId": "Block",
        "ItemSoundSetId": "ISS_Armor_Heavy",
        "Categories": ["Items.Armors"],
        "Icon": icon_path(piece),
        "Model": FOLDER + piece + ".blockymodel",
        "Texture": FOLDER + piece + ".png",
        "Interactions": {"Primary": {"Interactions": [{"Type": "EquipItem"}]},
                         "Secondary": {"Interactions": [{"Type": "EquipItem"}]}},
        "Armor": armor,
        "MaxDurability": DURABILITY,
        "DurabilityLossOnHit": 0.5,
        "Tags": {"Type": ["Armor"]},
    }


if __name__ == "__main__":
    main()
