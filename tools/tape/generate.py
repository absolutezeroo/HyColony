"""Génère le bloc du ruban de chantier de HyColony (MC BlockConstructionTape, spec
docs/superpowers/specs/2026-10-02-hycolony-construction-tape-design.md) : les modèles de ses quatre formes, faits de
pièces (shapes.py), dépliés une zone par face, peints en couches et éclairés comme les huttes (tools/blockpaint :
catalog.model_texture), leurs textures et leurs hitbox, son gabarit de raccord, son icône et son objet, dans
les ressources du plugin de HyColony.

Lancé à la main (`python tools/blockpaint tape`, ou ce script), puis les sorties sont commitées : le build ne le lance
jamais. Python 3.10+ et Pillow.

    python tools/tape/generate.py
"""

import json
import sys
from pathlib import Path
from types import SimpleNamespace

from PIL import Image

TOOLS = Path(__file__).resolve().parents[1]
sys.path += [str(TOOLS / "blockpaint"), str(TOOLS / "domum")]
from blocks import common  # noqa: E402
from brushes import coloured, jitter, painted, stone, wood  # noqa: E402
from catalog import model_texture  # noqa: E402
from conditions import TEMPERATE_OUTDOOR, USED  # noqa: E402
from icons import ICON_SIZE, draw_model, frame  # noqa: E402
from models import bounds, unwrap  # noqa: E402
from pack import ROOT, rounded, save_png, write_json  # noqa: E402
from shapes import SHAPES, parts  # noqa: E402

OUT = ROOT / "plugin" / "src" / "main" / "resources"
IDENT = "HyColony_Construction_Tape"
FOLDER = "Blocks/HyColony/Construction_Tape/"
ICON = "Icons/Items/HyColony/Construction_Tape.png"
HITBOXES = "Server/Item/Block/Hitboxes/HyColony/"
TEMPLATE = "HyColony_TapeConnectedBlockTemplate"
TAG = "HyColonyTape"
DEFAULT = "Straight"
ROPE = (190, 154, 100)


def main():
    looks = {shape: _look(shape) for shape in SHAPES}
    block_type = {
        "Material": "Empty",  # MC noCollission : on le traverse, comme une plante
        "DrawType": "Model",
        "Opacity": "Transparent",
        "VariantRotation": "NESW",
        **looks[DEFAULT],
        "Gathering": {"Soft": {"DropList": "Empty"}},  # MC strength(0) et noLootTable
        "BlockParticleSetId": "Wood",
        "BlockSoundSetId": "Wood",  # MC SoundType.WOOD
        "PhysicalMaterialId": "Wood",
    }
    states = {shape: look for shape, look in looks.items() if shape != DEFAULT}
    block_type.update(common.connected(IDENT, TEMPLATE, DEFAULT, states))
    write_json(OUT / "Server/Item/CustomConnectedBlockTemplates" / (TEMPLATE + ".json"), template())
    write_json(OUT / "Server/Item/Items/HyColony" / (IDENT + ".json"), {
        "TranslationProperties": {"Name": "hycolony.item.tape.name", "Description": "hycolony.item.tape.description"},
        "Icon": ICON,
        "Categories": ["Blocks.Deco"],
        # MC's shaped recipe SWS / S S / S S : 6 bâtons et une laine (le tag minecraft:wool ; Hytale n'a pas de type
        # de ressource laine, d'où la laine blanche), à l'établi comme la baguette.
        "Recipe": {
            "Input": [{"ItemId": "Ingredient_Stick", "Quantity": 6},
                      {"ItemId": "Cloth_Block_Wool_White", "Quantity": 1}],
            "BenchRequirement": [{"Id": "Workbench", "Type": "Crafting", "Categories": ["Workbench_Crafting"]}],
            "KnowledgeRequired": False,
        },
        "PlayerAnimationsId": "Block",
        "BlockType": block_type,
        "ItemSoundSetId": "ISS_Blocks_Wood",
    })
    print(f"{IDENT}: {len(SHAPES)} formes")


def template():
    """Le gabarit de raccord : la forme d'après les rubans voisins (MC getConnections), tournée avec le bloc ; un
    seul voisin donne un droit qui le traverse, aucun garde la forme et la rotation posées. common.neighbour_template
    ne prend qu'un motif par forme : le droit en a deux ici."""
    face_tags = {side.capitalize(): [TAG] for side in common.SIDES}
    shapes = {}
    for shape, sides in SHAPES.items():
        sets = [sides, {"north"}] if shape == DEFAULT else [sides]
        shapes[shape] = {"FaceTags": face_tags, "PatternsToMatchAnyOf": [_pattern(s) for s in sets]}
    return {"ConnectsToOtherMaterials": True, "DefaultShape": DEFAULT, "Shapes": shapes}


def _pattern(sides):
    """Un motif exact : un ruban sur chacun des côtés sides, aucun sur les autres."""
    rules = [{"Position": dict(zip("XYZ", offset)), "IncludeOrExclude": "Include" if side in sides else "Exclude",
              "FaceTags": {face: [TAG]}} for side, (offset, face) in common.SIDES.items()]
    return {"Type": "Custom", "AllowedPatternTransformations": {"IsCardinallyRotatable": True},
            "RulesToMatch": rules}


def _look(shape):
    """Écrit le modèle, la texture et la hitbox de la forme (et l'icône, pour la forme par défaut) ; renvoie ses clés
    CustomModel, CustomModelTexture et HitboxType."""
    nodes = parts(shape)
    unwrap(nodes)
    model, texture = FOLDER + shape + ".blockymodel", FOLDER + shape + ".png"
    target = OUT / "Common" / model
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(rounded({"lod": "auto", "nodes": nodes}), separators=(",", ":")) + "\n",
                      encoding="utf-8", newline="\n")
    image = model_texture(LOOK, nodes, None)
    save_png(image, OUT / "Common" / texture)
    if shape == DEFAULT:
        icon = Image.new("RGBA", (ICON_SIZE, ICON_SIZE), (0, 0, 0, 0))
        draw_model(icon, nodes, image, *frame(nodes))
        save_png(icon, OUT / "Common" / ICON)
    hitbox = f"{IDENT}_{shape}"
    low, high = bounds(nodes)
    write_json(OUT / HITBOXES / (hitbox + ".json"), {"Boxes": [{
        "Min": {"X": _cell(low[0]), "Y": max(0.0, low[1] / 32), "Z": _cell(low[2])},
        "Max": {"X": _cell(high[0]), "Y": min(1.0, high[1] / 32), "Z": _cell(high[2])}}]})
    return {"CustomModel": model, "CustomModelTexture": [{"Texture": texture, "Weight": 1}], "HitboxType": hitbox}


def _cell(units):
    """A model x or z (units, -16..16 about the block's centre) as a hitbox coordinate, kept inside the block."""
    return round(min(1.0, max(0.0, (units + 16) / 32)), 4)


def _material(name, _side):
    """La motte de terre, le piquet de bois brut, sa pointe fraîchement taillée, la corde."""
    if name == "Mound":
        return "soil"
    if name == "Stake":
        return "stake"
    return "cut" if name.startswith("Stake_") else "rope"


def _rope():
    """Une corde de chanvre torsadée : des brins en diagonale le long de la corde, chacun clair sur son dos et sombre
    dans le creux entre deux brins."""
    def rule(x, y, w, h, side):
        strand = (x + y) % 3 if h >= w else (x - y) % 3
        return coloured(ROPE, (1.16, 0.98, 0.74)[strand] + 0.04 * jitter(x * 13 + y * 7, 46))
    return painted(rule)


TILES = {"soil": stone((92, 66, 46), chunk=(2, 2)), "stake": wood((112, 82, 54), plank=99),
         "cut": wood((198, 162, 112), plank=99), "rope": _rope()}
# Peint en couches (spec 2026-10-03 blockpaint surfaces, catalog.model_texture) : un balisage de chantier qui a servi,
# dehors, planté dans le sol.
LOOK = SimpleNamespace(GROUNDED=True, PICTURES=frozenset(), CONDITION=USED, ENVIRONMENT=TEMPERATE_OUTDOOR, SEED=11,
                       FAMILY={"rope": "textile"}, material=_material, tiles=lambda assets: TILES)


if __name__ == "__main__":
    main()
