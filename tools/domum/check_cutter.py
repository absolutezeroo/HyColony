"""Checks of the architect's cutter the generator writes (run by check.py): its item, sounds, model and icon."""

import json
import math

from PIL import Image

import icon
import iconmap
from blocks import cutter_model
from check_pack import generate_into_temp
from models import bounds, walk

# The vanilla bench's stones: the lying slab and the pieces it carries, the slab under the saw and its cube.
STONES = {"10", "11", "12", "13", "14", "15", "16"}
# The most nodes a model may hold (the Hytale blockymodel codec of Blockbench's Hytale plugin, hytale_validate_model).
MAX_NODES = 255


def cutter_is_a_bench_without_container():
    """The architect's cutter: its own bench model, no container, a plain Use the plugin handles, crafted at the
    Workbench from DO's recipe (1 iron ingot, 3 stone slabs, 3 logs)."""
    ctx = generate_into_temp()
    item = _item(ctx)
    block = item["BlockType"]
    assert "BlockEntity" not in block
    assert block["Interactions"]["Use"] == {"Interactions": [{"Type": "Simple"}]}
    assert block["CustomModel"] == cutter_model.MODEL
    assert block["CustomModelTexture"] == [{"Texture": cutter_model.TEXTURE, "Weight": 1}]
    assert block["HitboxType"] == "Bench_Architect"
    inputs = {i.get("ItemId") or i["ResourceTypeId"]: i["Quantity"] for i in item["Recipe"]["Input"]}
    assert inputs == {"Ingredient_Bar_Iron": 1, "Rock_Stone_Half": 3, "Wood_Trunk": 3}
    assert item["Recipe"]["BenchRequirement"][0]["Id"] == "Workbench"


def cutter_sounds_are_the_builder_bench_ones():
    """The id-map fragment gives the cutter window the vanilla builder bench's open and close sounds."""
    ctx = generate_into_temp()
    fragment = json.loads((ctx.pack / "hydomum/id-map.json").read_text(encoding="utf-8"))
    assert fragment["sounds"] == {"cutter.open": "SFX_Workbench_Open", "cutter.close": "SFX_Workbench_Close"}
    assert fragment["ornamentTags"]


def cutter_model_carries_the_domum_miniatures_on_its_tabletop():
    """The bench loses its stones and gains one miniature per template, whose shapes all lie on its tabletop within
    its edges; every node keeps its own id."""
    ctx = generate_into_temp()
    model = _model(ctx)
    vanilla = ctx.assets.json("Common/" + ctx.assets.item("Bench_Builders")["BlockType"]["CustomModel"])
    ids = [node["id"] for node in walk(model["nodes"])]
    assert len(ids) == len(set(ids)) and len(ids) <= MAX_NODES
    assert {node["id"] for node in walk(vanilla["nodes"])} - set(ids) == STONES
    tabletop = next(node for node in walk(model["nodes"]) if node["name"] == "Tabletop")
    top = tabletop["shape"]["settings"]["size"]
    minis = [node for node in tabletop["children"] if node["name"].startswith("Mini_")]
    assert [m["name"] for m in minis] == ["Mini_" + i.removeprefix("HyDomum_") for i, *_ in cutter_model.MINIATURES]
    for mini, (_, base, _, _, _) in zip(minis, cutter_model.MINIATURES):
        at = mini["position"]
        assert (at["x"], at["y"], at["z"]) == base, mini["name"]
        low, high = bounds([mini])
        assert low[1] >= top["y"] / 2 - 1e-6, mini["name"]
        assert max(-low[0], high[0]) <= top["x"] / 2 and max(-low[2], high[2]) <= top["z"] / 2, mini["name"]


def cutter_miniatures_are_their_templates_scaled_with_their_texture():
    """Each miniature is its template's nodes scaled down, reading the template's texture copied whole into the band
    under the vanilla bench's texture, which stays as it was."""
    ctx = generate_into_temp()
    bench = ctx.assets.item("Bench_Builders")["BlockType"]
    base = ctx.assets.image("Common/" + bench["CustomModelTexture"][0]["Texture"])
    texture = Image.open(ctx.pack / "Common" / cutter_model.TEXTURE).convert("RGBA")
    assert texture.size == (base.width, 2 * base.height)
    assert texture.crop((0, 0, base.width, base.height)).tobytes() == base.tobytes()
    tabletop = next(node for node in walk(_model(ctx)["nodes"]) if node["name"] == "Tabletop")
    minis = [node for node in tabletop["children"] if node["name"].startswith("Mini_")]
    for mini, (ident, _, _, scale, path) in zip(minis, cutter_model.MINIATURES):
        sources, copies = list(walk(ctx.models[ident]["nodes"])), list(walk(mini["children"]))
        assert len(sources) == len(copies), mini["name"]
        shifts = set()
        for source, copied in zip(sources, copies):
            for key in "xyz":
                assert math.isclose(copied["position"][key], source["position"][key] * scale), mini["name"]
                for part in ("offset", "stretch"):
                    assert math.isclose(copied["shape"][part][key], source["shape"][part][key] * scale), mini["name"]
            for side, layout in source["shape"].get("textureLayout", {}).items():
                moved = copied["shape"]["textureLayout"][side]["offset"]
                shifts.add((moved["x"] - layout["offset"]["x"], moved["y"] - layout["offset"]["y"]))
        assert len(shifts) == 1, mini["name"]
        (x, y), = shifts
        own_path = path or ctx.items[ident]["BlockType"]["CustomModelTexture"][0]["Texture"]
        shipped = ctx.pack / "Common" / own_path
        own = Image.open(shipped).convert("RGBA") if shipped.exists() else ctx.assets.image("Common/" + own_path)
        assert y >= base.height, mini["name"]
        assert texture.crop((x, y, x + own.width, y + own.height)).tobytes() == own.tobytes(), mini["name"]


def cutter_icon_is_drawn_from_its_model():
    """The item's icon is the cutter's own, drawn from its written model and texture with the bench's icon camera."""
    ctx = generate_into_temp()
    item = _item(ctx)
    assert item["Icon"] == cutter_model.ICON
    texture = Image.open(ctx.pack / "Common" / cutter_model.TEXTURE).convert("RGBA")
    expected = icon.from_map(iconmap.render(_model(ctx), texture.size, item["IconProperties"]), texture)
    assert Image.open(ctx.pack / "Common" / cutter_model.ICON).convert("RGBA").tobytes() == expected.tobytes()


def _item(ctx):
    path = ctx.pack / "Server/Item/Items/HyDomum/HyDomum_ArchitectsCutter.json"
    return json.loads(path.read_text(encoding="utf-8"))


def _model(ctx):
    return json.loads((ctx.pack / "Common" / cutter_model.MODEL).read_text(encoding="utf-8"))


def run():
    """Runs this module's checks; an AssertionError names the failing case."""
    cutter_is_a_bench_without_container()
    cutter_sounds_are_the_builder_bench_ones()
    cutter_model_carries_the_domum_miniatures_on_its_tabletop()
    cutter_miniatures_are_their_templates_scaled_with_their_texture()
    cutter_icon_is_drawn_from_its_model()
