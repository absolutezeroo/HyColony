"""The Minecraft block model templates Domum Ornamentum's vanilla-compatible blocks inherit (fence, fence gate,
wall, stairs, slab, cube): their cuboids, written from Minecraft's own templates, since DO ships only the child
models naming them as parent (e.g. fence/fence_post_spec -> block/fence_post). Faces carry no uv: the default uv of
their position applies; where Minecraft's own uv differs (the underside of a fence or wall arm), a uniform
material's pattern is only shifted. source.load merges them like DO-internal parents; a template missing here is
an error.
"""

ALL = ("down", "up", "north", "south", "west", "east")
OPEN_SOUTH = ("down", "up", "north", "west", "east")  # an arm whose south end meets the post


def _box(low, high, texture, directions=ALL):
    """One cuboid, every listed face reading texture (a "#name" reference, or a callable of the direction)."""
    faces = {d: {"texture": texture(d) if callable(texture) else texture} for d in directions}
    return {"from": list(low), "to": list(high), "faces": faces}


def _shaped(direction):
    """The texture of a stairs or slab face: its bottom, top or side texture."""
    return {"down": "#bottom", "up": "#top"}.get(direction, "#side")


def _gate(texture):
    """template_fence_gate, facing south: two posts, and a leaf of three bars on each side of the middle."""
    posts = [_box((0, 5, 7), (2, 16, 9), texture), _box((14, 5, 7), (16, 16, 9), texture)]
    leaves = [_box((6, 6, 7), (8, 15, 9), texture), _box((8, 6, 7), (10, 15, 9), texture),
              _box((2, 6, 7), (6, 9, 9), texture), _box((2, 12, 7), (6, 15, 9), texture),
              _box((10, 6, 7), (14, 9, 9), texture), _box((10, 12, 7), (14, 15, 9), texture)]
    return posts + leaves


TEMPLATES = {
    "block/fence_post": [_box((6, 0, 6), (10, 16, 10), "#texture")],
    "block/fence_side": [_box((7, 12, 0), (9, 15, 9), "#texture", OPEN_SOUTH),
                         _box((7, 6, 0), (9, 9, 9), "#texture", OPEN_SOUTH)],
    "block/template_fence_gate": _gate("#texture"),
    "block/template_wall_post": [_box((4, 0, 4), (12, 16, 12), "#wall")],
    "block/template_wall_side": [_box((5, 0, 0), (11, 14, 8), "#wall", OPEN_SOUTH)],
    "block/template_wall_side_tall": [_box((5, 0, 0), (11, 16, 8), "#wall", OPEN_SOUTH)],
    "block/stairs": [_box((0, 0, 0), (16, 8, 16), _shaped), _box((8, 8, 0), (16, 16, 16), _shaped)],
    "block/inner_stairs": [_box((0, 0, 0), (16, 8, 16), _shaped), _box((8, 8, 0), (16, 16, 16), _shaped),
                           _box((0, 8, 8), (8, 16, 16), _shaped)],
    "block/outer_stairs": [_box((0, 0, 0), (16, 8, 16), _shaped), _box((8, 8, 8), (16, 16, 16), _shaped)],
    "block/slab": [_box((0, 0, 0), (16, 8, 16), _shaped)],
    "block/slab_top": [_box((0, 8, 0), (16, 16, 16), _shaped)],
    "block/cube_all": [_box((0, 0, 0), (16, 16, 16), "#all")],
}


def template(parent):
    """The template named by a model's parent (with or without the minecraft: namespace), as a model with no
    textures of its own; None for a vanilla parent without geometry (block/block); an unknown block/template_*
    parent fails."""
    name = parent.removeprefix("minecraft:")
    elements = TEMPLATES.get(name)
    if elements is None:
        assert "template_" not in name, "Minecraft template missing from minecraft.TEMPLATES: " + parent
        return None
    return {"textures": {}, "elements": [{**e, "faces": {d: dict(f) for d, f in e["faces"].items()}} for e in elements]}
