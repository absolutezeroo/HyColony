"""Generated pack checks of the Domum Ornamentum generator (run by check.py): the templates, the manifest and the
creative tab of a full generation into a temporary folder."""

import json
import tempfile
from pathlib import Path

import generate
import tags

_RUN = {}


def generate_into_temp():
    """One full generation into a temporary pack and resources folder, shared by every check of the run."""
    if "ctx" not in _RUN:
        tmp = Path(tempfile.mkdtemp(prefix="domum-check-"))
        (tmp / "pack").mkdir()
        _RUN["ctx"] = generate.run(tmp / "pack", tmp / "resources", tags.open_assets())
    return _RUN["ctx"]


def manifest_lists_every_template_with_its_slots():
    ctx = generate_into_temp()
    manifest = json.loads((ctx.resources / "hycolony/ornament/shapes.json").read_text(encoding="utf-8"))
    assert manifest["schemaVersion"] == 1 and manifest["shapes"]
    for shape in manifest["shapes"]:
        assert shape["template"] in ctx.items, shape
        assert shape["slots"] and all(tag in tags.TAG_GROUPS for tag in shape["slots"]), shape


def static_templates_live_in_the_do_tab():
    """Every template sits in the DO tab only; an open shape (a panel) has its own hitbox and is Transparent, a full
    block (a timber frame) keeps Hytale's full box and is Solid."""
    ctx = generate_into_temp()
    assert all(item["Categories"][0].startswith("DomumOrnamentum.") for item in ctx.items.values())
    panel = ctx.items["HyColony_DO_Panel_Full"]["BlockType"]
    assert panel["HitboxType"] == "HyColony_DO_Panel_Full" and panel["Opacity"] == "Transparent"
    frame = ctx.items["HyColony_DO_TimberFrame_Plain"]["BlockType"]
    assert "HitboxType" not in frame and frame["Opacity"] == "Solid", frame
    assert len([i for i in ctx.items if i.startswith("HyColony_DO_TimberFrame_")]) == 10


def two_material_templates_read_their_default_pair():
    ctx = generate_into_temp()
    texture = ctx.items["HyColony_DO_TimberFrame_Framed"]["BlockType"]["CustomModelTexture"][0]["Texture"]
    assert texture == "Blocks/HyColony/DO/Pairs/Wood_Hardwood_Planks__Soil_Clay_Smooth_White.png", texture
    assert (ctx.pack / "Common" / texture).exists()


def every_category_has_both_icons():
    ctx = generate_into_temp()
    assert ctx.tab["Children"]
    for child in ctx.tab["Children"]:
        for suffix in ("", "Active"):
            assert (ctx.pack / "Common" / child["Icon"].replace(".png", suffix + ".png")).exists(), child


def every_name_is_in_both_languages():
    ctx = generate_into_temp()
    keys = {language: {line.split(" = ")[0] for line in lines} for language, lines in ctx.lang.items()}
    assert keys["en-US"] == keys["fr-FR"]
    for item in ctx.items.values():
        assert item["TranslationProperties"]["Name"].removeprefix("hycolony.") in keys["en-US"], item


def shingle_rules_name_states_only():
    """Every slope is a vanilla-style roof whose rules name states only (a runtime copy resolves its own), with the
    four corner states and an upside-down placement."""
    ctx = generate_into_temp()
    for ident in ("HyColony_DO_Shingle", "HyColony_DO_Shingle_Flat", "HyColony_DO_Shingle_FlatLower",
                  "HyColony_DO_Shingle_Steep", "HyColony_DO_Shingle_SteepLower"):
        shingle = ctx.items[ident]["BlockType"]
        rules = shingle["ConnectedBlockRuleSet"]
        assert rules["Type"] == "Roof" and set(rules) == {"Type", "Regular", "MaterialName"}, ident
        assert all("State" in o and "Block" not in o for o in rules["Regular"].values()), ident
        assert set(shingle["State"]["Definitions"]) == {"Corner_Left", "Corner_Right",
                                                        "Inverted_Corner_Left", "Inverted_Corner_Right"}
        assert shingle["VariantRotation"] == "UpDownNESW"
    # DO forms corners between any two shingles (DOStairBlock.isStairs: any DOStairBlock, whatever its slope).
    material_names = {ctx.items[i]["BlockType"]["ConnectedBlockRuleSet"]["MaterialName"] for i in ctx.items
                      if i.startswith("HyColony_DO_Shingle") and "Slab" not in i}
    assert len(material_names) == 1, material_names


def shingle_slab_template_has_six_shapes():
    ctx = generate_into_temp()
    path = ctx.pack / "Server/Item/CustomConnectedBlockTemplates/HyColony_DO_ShingleSlabConnectedBlockTemplate.json"
    template = json.loads(path.read_text(encoding="utf-8"))
    assert set(template["Shapes"]) == {"Single", "One_Way", "Two_Way", "Curved", "Three_Way", "Four_Way"}
    slab = ctx.items["HyColony_DO_ShingleSlab"]["BlockType"]
    patterns = slab["ConnectedBlockRuleSet"]["TemplateShapeBlockPatterns"]
    assert set(patterns) == set(template["Shapes"]) and patterns["Single"] == "HyColony_DO_ShingleSlab"
    # Curved: south and east neighbours, nothing north or west (DO's facing=north curved slab).
    rules = template["Shapes"]["Curved"]["PatternsToMatchAnyOf"][0]["RulesToMatch"]
    included = {(r["Position"]["X"], r["Position"]["Z"]) for r in rules if r["IncludeOrExclude"] == "Include"}
    assert included == {(0, 1), (1, 0)}, included


def shingle_hitboxes_stay_in_their_block():
    """A DO slope fits one block (unlike Hytale's 2-block shallow and steep roofs): every shingle state collides
    inside its block, rising from low to high like the model."""
    ctx = generate_into_temp()
    hitboxes = ctx.pack / "Server/Item/Block/Hitboxes/HyColony/DO"
    for ident, item in ctx.items.items():
        if not ident.startswith("HyColony_DO_Shingle") or "Slab" in ident:
            continue
        block = item["BlockType"]
        for name in [block["HitboxType"]] + [s["HitboxType"] for s in block["State"]["Definitions"].values()]:
            boxes = json.loads((hitboxes / (name + ".json")).read_text(encoding="utf-8"))["Boxes"]
            for box in boxes:
                assert all(0 <= box["Min"][a] <= box["Max"][a] <= 1 for a in "XYZ"), (name, box)
            assert len({box["Max"]["Y"] for box in boxes}) > 1, name


def placement_follows_do():
    """DO turns a timber frame only when its pattern has a direction (TimberFrameBlock: FACING, 6 ways), and a panel
    lies on the floor, under the ceiling or against a wall (AbstractPanelBlockTrapdoor): Hytale's half-block
    rotation (DoublePipe) gives exactly these; a symmetric frame is never turned."""
    ctx = generate_into_temp()
    rotation = {i: item["BlockType"]["VariantRotation"] for i, item in ctx.items.items()}
    for ident in ("HyColony_DO_TimberFrame_SideFramed", "HyColony_DO_TimberFrame_UpGated",
                  "HyColony_DO_TimberFrame_DownGated", "HyColony_DO_TimberFrame_SideFramedHorizontal"):
        assert rotation[ident] == "DoublePipe", ident
    assert rotation["HyColony_DO_TimberFrame_Plain"] == rotation["HyColony_DO_TimberFrame_OneCrossedLr"] == "None"
    assert all(rotation[i] == "DoublePipe" for i in rotation if i.startswith("HyColony_DO_Panel_"))


def every_generated_model_reads_one_tile():
    """Every model written (all states included) reads inside its layout and never across two material tiles."""
    from models import face_rects

    ctx = generate_into_temp()
    for ident, item in ctx.items.items():
        texture = item["BlockType"]["CustomModelTexture"][0]["Texture"]
        width = 64 if "/Pairs/" in texture else 32
        for name, model in ctx.models.items():
            if name != ident and not name.startswith(ident + "_"):
                continue
            for node, u0, v0, u1, v1 in face_rects(model["nodes"]):
                assert -1e-6 <= u0 and u1 <= width + 1e-6 and -1e-6 <= v0 and v1 <= 32 + 1e-6, (name, node)
                assert (u0 + 1e-6) // 32 == (u1 - 1e-6) // 32, (name, node, u0, u1)


def door_node(model):
    """The node the vanilla door and trapdoor animations turn ("Door" in Door_Open_In and Trapdoor_Open)."""
    from models import walk

    return next(n for n in walk(model["nodes"]) if n["name"] == "Door")


def opened_bounds(ctx, ident, animation):
    """Bounds (units) of ident's model once its "Door" node takes the last orientation of the vanilla animation."""
    import copy

    from models import bounds

    model = copy.deepcopy(ctx.models[ident])
    keys = ctx.assets.json("Common/Blocks/Animations/" + animation)["nodeAnimations"]["Door"]["orientation"]
    door_node(model)["orientation"] = keys[-1]["delta"]
    return bounds(model["nodes"])


def door_copies_vanilla_mechanics():
    """A door is two blocks tall, centred in its block like the vanilla door, hinged on its -X edge under the node
    the vanilla animations turn (opened, it lies along the -X side like the vanilla Door_Open_In hitbox), with the
    vanilla door's states, hitboxes and double-door rule."""
    from models import bounds

    ctx = generate_into_temp()
    vanilla = ctx.assets.item("Furniture_Crude_Door")["BlockType"]
    door = ctx.items["HyColony_DO_Door_Full"]["BlockType"]
    assert door["HitboxType"] == vanilla["HitboxType"] == "Door" and door["IsDoor"] and door["Opacity"] == "Transparent"
    assert door["State"] == vanilla["State"] and door["Interactions"] == vanilla["Interactions"]
    assert door["ConnectedBlockRuleSet"]["TemplateShapeBlockPatterns"] == {"Default": "HyColony_DO_Door_Full"}
    low, high = bounds(ctx.models["HyColony_DO_Door_Full"]["nodes"])
    assert (low[0], low[1], high[0], high[1]) == (-16, 0, 16, 64) and abs(low[2] + high[2]) < 1e-6, (low, high)
    low, high = opened_bounds(ctx, "HyColony_DO_Door_Full", "Door/Door_Open_In.blockyanim")
    # Like the vanilla door (hinge x -16, z 0), the open leaf stands half its thickness outside the block.
    assert -20.5 < low[0] and high[0] < -11 and -0.5 < low[2] and 31 < high[2] < 33, (low, high)
    assert len([i for i in ctx.items if i.startswith("HyColony_DO_Door_")]) == 4
    assert len([i for i in ctx.items if i.startswith("HyColony_DO_FancyDoor_")]) == 2


def trapdoor_opens_like_vanilla():
    """A trapdoor closes on the floor of its block and opens up against its +Z side with the vanilla animation;
    turned upside down (UpDownNESW) it is the vanilla trapdoor, closed under the ceiling, opening down along -Z."""
    from models import bounds

    ctx = generate_into_temp()
    vanilla = ctx.assets.item("Furniture_Crude_Trapdoor")["BlockType"]
    trapdoor = ctx.items["HyColony_DO_Trapdoor_Full"]["BlockType"]
    assert set(trapdoor["State"]["Definitions"]) == set(vanilla["State"]["Definitions"])
    assert trapdoor["Interactions"] == vanilla["Interactions"] and trapdoor["VariantRotation"] == "UpDownNESW"
    assert trapdoor["Opacity"] == "Transparent" and trapdoor["HitboxType"] == "HyColony_DO_Trapdoor"
    low, high = bounds(ctx.models["HyColony_DO_Trapdoor_Full"]["nodes"])
    assert (low[1], high[1], low[2], high[2]) == (0, 6, -16, 16), (low, high)
    low, high = opened_bounds(ctx, "HyColony_DO_Trapdoor_Full", "Trapdoor/Trapdoor_Open.blockyanim")
    assert abs(low[1]) < 0.5 and abs(high[1] - 32) < 0.5 and abs(low[2] - 10) < 0.5 and abs(high[2] - 16) < 0.5, (
        low, high)
    assert len([i for i in ctx.items if i.startswith("HyColony_DO_Trapdoor_")]) == 15
    assert len([i for i in ctx.items if i.startswith("HyColony_DO_FancyTrapdoor_")]) == 2


def run():
    """Runs this module's checks; an AssertionError names the failing case."""
    manifest_lists_every_template_with_its_slots()
    static_templates_live_in_the_do_tab()
    two_material_templates_read_their_default_pair()
    every_category_has_both_icons()
    every_name_is_in_both_languages()
    shingle_rules_name_states_only()
    shingle_slab_template_has_six_shapes()
    every_generated_model_reads_one_tile()
    placement_follows_do()
    shingle_hitboxes_stay_in_their_block()
    door_copies_vanilla_mechanics()
    trapdoor_opens_like_vanilla()
