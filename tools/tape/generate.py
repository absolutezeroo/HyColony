"""Génère le bloc du ruban de chantier de HyColony (MC BlockConstructionTape, spec
docs/superpowers/specs/2026-10-02-hycolony-construction-tape-design.md) : les modèles de ses quatre formes, leur
texture (planches de bois dur et laine blanche de Hytale, à la place des planches de chêne et de la laine blanche de
MC), leurs hitbox, son gabarit de raccord, son icône et son objet, dans les ressources du plugin de HyColony.

Lancé à la main, puis les sorties sont commitées : le build ne le lance jamais. Réutilise le convertisseur de
modèles Minecraft de HyDomum (tools/domum/convert.py). Python 3.10+ et Pillow.

    python tools/tape/generate.py [chemin/vers/Assets.zip]
"""

import json
import sys
from pathlib import Path
from types import SimpleNamespace

TOOLS = Path(__file__).resolve().parents[1]
sys.path += [str(TOOLS / "vanilla"), str(TOOLS / "domum")]
import convert  # noqa: E402
import icon  # noqa: E402
import iconmap  # noqa: E402
import pairs  # noqa: E402
import tags  # noqa: E402
from blocks import common  # noqa: E402
from flower_pots import rounded  # noqa: E402
from pack import ROOT, write_json  # noqa: E402
from shapes import SHAPES, TEXTURES, elements  # noqa: E402

OUT = ROOT / "plugin" / "src" / "main" / "resources"
IDENT = "HyColony_Construction_Tape"
FOLDER = "Blocks/HyColony/Construction_Tape/"
TEXTURE = FOLDER + "Texture.png"
ICON = "Icons/ItemsGenerated/" + IDENT + ".png"
HITBOXES = "Server/Item/Block/Hitboxes/HyColony/"
TEMPLATE = "HyColony_TapeConnectedBlockTemplate"
TAG = "HyColonyTape"
DEFAULT = "Straight"
# Les matériaux des deux tuiles de la texture, dans l'ordre des textures de MC (TEXTURES).
MATERIALS = ("Wood_Hardwood_Planks", "Cloth_Block_Wool_White")
FAMILY = SimpleNamespace(components=list(TEXTURES.values()))


def main():
    assets = tags.open_assets(sys.argv[1] if len(sys.argv) > 1 else None)
    texture = pairs.image(assets, *MATERIALS)
    _save(texture, TEXTURE)
    looks = {shape: _look(shape) for shape in SHAPES}
    block_type = {
        "Material": "Empty",  # MC noCollission : on le traverse, comme une plante
        "DrawType": "Model",
        "Opacity": "Transparent",
        "CustomModelTexture": [{"Texture": TEXTURE, "Weight": 1}],
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
    straight = convert.to_blockymodel(_model(DEFAULT), FAMILY)
    _save(icon.from_map(iconmap.render(straight, convert.layout_size(FAMILY), common.DEFAULT_ICON), texture), ICON)
    write_json(OUT / "Server/Item/Items/HyColony" / (IDENT + ".json"), {
        "TranslationProperties": {"Name": "hycolony.item.tape.name", "Description": "hycolony.item.tape.description"},
        "Icon": ICON,
        "IconProperties": common.DEFAULT_ICON,
        "Categories": ["Blocks.Deco"],
        "PlayerAnimationsId": "Block",
        "BlockType": block_type,
        "ItemSoundSetId": "ISS_Blocks_Wood",
    })
    print(f"{IDENT}: {len(SHAPES)} formes")


def template():
    """Le gabarit de raccord : la forme d'après les rubans voisins (MC getConnections), tournée avec le bloc ; un
    seul voisin donne un droit qui le traverse, aucun garde la forme et la rotation posées."""
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


def _model(shape):
    return {"textures": dict(TEXTURES), "elements": elements(shape)}


def _look(shape):
    """Écrit le modèle et la hitbox de la forme ; renvoie ses clés CustomModel et HitboxType."""
    model = _model(shape)
    path = FOLDER + shape + ".blockymodel"
    target = OUT / "Common" / path
    target.parent.mkdir(parents=True, exist_ok=True)
    blockymodel = rounded(convert.to_blockymodel(model, FAMILY))
    target.write_text(json.dumps(blockymodel, separators=(",", ":")) + "\n", encoding="utf-8", newline="\n")
    hitbox = f"{IDENT}_{shape}"
    low = [min(e["from"][i] for e in model["elements"]) / 16 for i in range(3)]
    high = [max(e["to"][i] for e in model["elements"]) / 16 for i in range(3)]
    write_json(OUT / HITBOXES / (hitbox + ".json"),
               {"Boxes": [{"Min": dict(zip("XYZ", low)), "Max": dict(zip("XYZ", high))}]})
    return {"CustomModel": path, "HitboxType": hitbox}


def _save(image, path):
    target = OUT / "Common" / path
    target.parent.mkdir(parents=True, exist_ok=True)
    image.save(target)


if __name__ == "__main__":
    main()
