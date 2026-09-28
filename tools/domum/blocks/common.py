"""What every Domum Ornamentum template shares: the generation context, the model file, the default-material
texture, the hitbox and the item/BlockType skeleton the mechanism modules fill in."""

import json
from dataclasses import dataclass, field
from pathlib import Path

import assemble
import convert
import faces
import names
import pairs
import tags
from flower_pots import rounded
from pack import write_json

MODELS = "Blocks/HyColony/DO/"
HITBOXES = "Server/Item/Block/Hitboxes/HyColony/DO/"
ITEMS = "Server/Item/Items/HyColony/DO/"
TEMPLATES = "Server/Item/CustomConnectedBlockTemplates/"
LANGUAGES = ("en-US", "fr-FR")
DEFAULT_ICON = {"Scale": 0.58823, "Rotation": [22.5, 45, 22.5], "Translation": [0, -13.5]}
# Horizontal neighbour -> (its offset, the face of it that touches us).
SIDES = {"north": ((0, 0, -1), "South"), "south": ((0, 0, 1), "North"),
         "east": ((1, 0, 0), "West"), "west": ((-1, 0, 0), "East")}


@dataclass
class Context:
    """One generation run: inputs (assets, DO cache root, built tags) and what it accumulates."""

    assets: object
    root: Path
    pack: Path
    resources: Path
    tags: dict
    items: dict = field(default_factory=dict)  # template id -> item JSON
    lang: dict = field(default_factory=lambda: {language: [] for language in LANGUAGES})
    shapes: list = field(default_factory=list)  # manifest entries, in generation order
    models: dict = field(default_factory=dict)  # template id -> .blockymodel content
    tab: dict = field(default_factory=dict)  # the creative tab JSON


def defaults(ctx, family):
    """DO's default material of each of the family's slots, as Hytale block ids."""
    return [tags.default_material(tag, component, ctx.tags) for tag, component in zip(family.slot_tags,
                                                                                         family.components)]


def layout_texture(ctx, family):
    """The texture the template's models read: its default material's own texture, or their pair texture."""
    materials = defaults(ctx, family)
    if len(materials) == 1:
        return tags.texture(ctx.assets, materials[0])
    return pairs.write(ctx.pack, ctx.assets, materials[0], materials[1])


def write_model(ctx, ident, blockymodel):
    """Writes the template's .blockymodel under the pack and returns its Common path."""
    path = MODELS + ident[len(names.PREFIX):] + ".blockymodel"
    target = ctx.pack / "Common" / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(rounded(blockymodel), separators=(",", ":")) + "\n", encoding="utf-8", newline="\n")
    ctx.models[ident] = blockymodel
    return path


def hitbox(ctx, ident, model):
    """The hitbox id of model's union box (Minecraft pixels, clamped to the block), written as its own asset; None
    for a full block, Hytale's default."""
    points = [p for e in model["elements"] for p in assemble.world_points(e)]
    low = [max(0.0, min(p[i] for p in points)) / 16 for i in range(3)]
    high = [min(16.0, max(p[i] for p in points)) / 16 for i in range(3)]
    if all(v <= 1e-3 for v in low) and all(v >= 1 - 1e-3 for v in high):
        return None
    box = {"Min": dict(zip("XYZ", (round(v, 4) for v in low))), "Max": dict(zip("XYZ", (round(v, 4) for v in high)))}
    write_json(ctx.pack / HITBOXES / (ident + ".json"), {"Boxes": [box]})
    return ident


def template(ctx, family, ident, parts, block_type):
    """Registers the template item ident: block_type completed with the default material's sounds, particles and
    gathering, the DO tab category, a name in both languages and a manifest entry."""
    materials = defaults(ctx, family)
    material = ctx.assets.item(materials[0])
    vanilla = material.get("BlockType", {})
    for key in ("Gathering", "BlockParticleSetId", "ParticleColor", "BlockSoundSetId", "PhysicalMaterialId"):
        if key in vanilla:
            block_type.setdefault(key, vanilla[key])
    key = names.lang_key(ident)
    item = {
        "TranslationProperties": {"Name": "hycolony." + key},
        "Icon": material["Icon"],  # replaced by the rendered icon (iconmap)
        "IconProperties": DEFAULT_ICON,
        "Categories": ["DomumOrnamentum." + family.name],
        "PlayerAnimationsId": "Block",
        "BlockType": block_type,
        "ItemSoundSetId": material.get("ItemSoundSetId", "ISS_Blocks_Wood"),
    }
    ctx.items[ident] = item
    write_json(ctx.pack / ITEMS / (ident + ".json"), item)
    for language, name in zip(LANGUAGES, names.names(family, parts)):
        ctx.lang[language].append(f"{key} = {name}")
    ctx.shapes.append({
        "id": ident[len(names.PREFIX):], "template": ident, "group": family.group,
        "slots": list(family.slot_tags), "optionalSecond": family.optional_second,
        "cutterQuantity": family.cutter_quantity,
    })
    return item


def model_block_type(ctx, family, model_path, hitbox_id, rotation):
    """The Model BlockType skeleton of a template: its model, the layout texture, rotation and hitbox; Solid for a
    full block (like vanilla structure blocks), Transparent for an open shape."""
    block_type = {
        "Material": "Solid",
        "DrawType": "Model",
        "Opacity": "Solid" if hitbox_id is None else "Transparent",
        "CustomModel": model_path,
        "CustomModelTexture": [{"Texture": layout_texture(ctx, family), "Weight": 1}],
        "VariantRotation": rotation,
    }
    if hitbox_id:
        block_type["HitboxType"] = hitbox_id
    return block_type


def convert_state(ctx, family, block, props):
    """One DO state, cleaned and converted to the family's material layout."""
    model = faces.clean(faces.cap_ends(assemble.state_model(ctx.root, family, block, props)))
    return model, convert.to_blockymodel(model, family)


def connected(ident, template_id, default, states):
    """The ConnectedBlockRuleSet and State of a CustomTemplate block: its default shape is the block itself, every
    other shape the state of the same name (states: shape -> state definition). The patterns name the template's
    own keys; a runtime variant rewrites them to its own."""
    patterns = {default: ident, **{shape: f"*{ident}_State_Definitions_{shape}" for shape in states}}
    return {
        "ConnectedBlockRuleSet": {"Type": "CustomTemplate", "TemplateShapeAssetId": template_id,
                                  "TemplateShapeBlockPatterns": patterns},
        "State": {"Definitions": states},
    }


def neighbour_template(tag, default, shapes):
    """A connected-block template choosing a shape from the horizontal neighbours carrying tag: shapes maps each
    shape to the sides ("north"...) that must be such neighbours, every other side must not; turned in the four
    directions with the block. default is the shape with no match. Every shape shows tag on its four sides."""
    face_tags = {side.capitalize(): [tag] for side in SIDES}
    result = {default: {"FaceTags": face_tags, "PatternsToMatchAnyOf": []}}
    for shape, present in shapes.items():
        rules = [{"Position": dict(zip("XYZ", offset)), "IncludeOrExclude": "Include" if side in present else "Exclude",
                  "FaceTags": {face: [tag]}} for side, (offset, face) in SIDES.items()]
        result[shape] = {"FaceTags": face_tags, "PatternsToMatchAnyOf": [{
            "Type": "Custom", "AllowedPatternTransformations": {"IsCardinallyRotatable": True},
            "RulesToMatch": rules}]}
    return {"ConnectsToOtherMaterials": True, "DefaultShape": default, "Shapes": result}


def look(ctx, family, name, block, props):
    """Writes the model of one DO state under name, and its hitbox unless it fills the block; returns the
    CustomModel and HitboxType keys of a BlockType or state drawing it. The HitboxType is always named ("Full",
    Hytale's default, for a full block): a state naming none inherits its block's."""
    model, blockymodel = convert_state(ctx, family, block, props)
    return {"CustomModel": write_model(ctx, name, blockymodel), "HitboxType": hitbox(ctx, name, model) or "Full"}
