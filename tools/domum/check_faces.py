"""Face cleaning checks of the Domum Ornamentum generator (run by check.py)."""

import assemble
import faces
import source
from families import FAMILIES


def coplanar_overlap_is_removed():
    a = {"from": [0, 0, 0], "to": [16, 16, 1], "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#a"}}}
    b = {"from": [4, 4, 0], "to": [12, 12, 1], "faces": {"north": {"uv": [0, 0, 8, 8], "texture": "#b"}}}
    cleaned = faces.clean({"textures": {}, "elements": [a, b]})
    assert faces.overlapping_pairs(cleaned) == []
    # The small face is a detail on the big one (DO's timber beams): it stays in front, the backing moves back.
    assert "north" in cleaned["elements"][1]["faces"]
    assert cleaned["elements"][0]["from"][2] == faces.MIN_GAP_PX


def near_coplanar_backing_moves_behind_its_detail():
    beam = {"from": [0, 0, 0.01], "to": [16, 2, 1], "faces": {"north": {"texture": "#frame"}}}
    centre = {"from": [1, 0, 0.02], "to": [15, 16, 15], "faces": {"north": {"texture": "#centre"}}}
    cleaned = faces.clean({"textures": {}, "elements": [beam, centre]})
    assert faces.overlapping_pairs(cleaned) == []
    assert abs(cleaned["elements"][1]["from"][2] - (0.01 + faces.MIN_GAP_PX)) < 1e-9
    assert cleaned["elements"][0]["from"][2] == 0.01


def fully_hidden_face_is_dropped():
    front = {"from": [0, 0, 0], "to": [16, 16, 1], "faces": {"north": {"texture": "#a"}}}
    back = {"from": [4, 4, 0.02], "to": [12, 12, 1], "faces": {"north": {"texture": "#b"}}}
    cleaned = faces.clean({"textures": {}, "elements": [front, back]})
    assert "north" not in cleaned["elements"][1]["faces"]


def pillar_column_is_capped():
    """Every pillar column gets its ends closed; blockpillar's eight blades (tilted about y) share one lid per end,
    covering their real footprint, never eight coplanar caps."""
    root = source.fetch()
    pillar = next(f for f in FAMILIES if f.name == "Pillar")
    for block in pillar.blocks:
        raw = assemble.state_model(root, pillar, block, {"column": "pillar_column"})
        model = faces.cap_ends(raw)
        assert faces.overlapping_pairs(model) == [], (block, faces.overlapping_pairs(model))
        for direction, y in (("up", 16), ("down", 0)):
            caps = [e for e in model["elements"] if direction in e["faces"] and e[("to" if y else "from")][1] == y]
            assert caps, (block, direction)
        if block == "blockpillar":
            points = [p for e in raw["elements"] for p in assemble.world_points(e)]
            lid = next(e for e in model["elements"] if list(e["faces"]) == ["up"])
            assert abs(lid["from"][0] - min(p[0] for p in points)) < 1e-3, lid
            assert abs(lid["to"][2] - max(p[2] for p in points)) < 1e-3, lid


def clean_resolves_every_do_default_state():
    """faces.clean leaves no z-fighting pair in any DO-1 block's default state (compat families, whose geometry
    comes from Minecraft's vanilla templates, are Task 10's)."""
    root = source.fetch()
    for family in FAMILIES:
        if family.mechanism == "vanilla":
            continue
        for block in family.blocks:
            model = faces.clean(faces.cap_ends(assemble.state_model(root, family, block, {})))
            assert faces.overlapping_pairs(model) == [], block


def run():
    """Runs this module's checks; an AssertionError names the failing case."""
    coplanar_overlap_is_removed()
    near_coplanar_backing_moves_behind_its_detail()
    fully_hidden_face_is_dropped()
    pillar_column_is_capped()
    clean_resolves_every_do_default_state()
