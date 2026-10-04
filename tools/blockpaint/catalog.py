"""Paints a catalog of models, each described by a module, and draws their icons. A block model (under Blocks/) is
first trimmed (trim.py: bottoms closed, hidden faces dropped, faces laid out) and rewritten in pack.write_json's
layout; an item model keeps its layout. The texture goes next to the model (<MODEL>.png: paint.texture, the module's
brushes and the light baked from the model), the icon to the catalog's icon folder.

A model's module declares:
- MODEL: its path under Common, without extension; a block's (under Blocks/) stands on a floor that shades its foot,
  unless GROUNDED, optional, says otherwise (a module painting models of its own: armor, tape, vanilla);
- ICON: its icon's name;
- PICTURES: the materials whose tile carries a drawing laid out for its island, never turned nor shifted; layered
  (effects.material), a drawing (brushes.drawing: never calmed nor worn, paint.tiled lays an image tile as one);
- FAMILY, optional with CONDITION: {material: family (compat.FAMILIES)} for the tiles whose brush carries none (an
  image tile, a brush of the module's own) or not the material's (cloth painting a reed's head), over the brush's; a
  layered material without a family is refused;
- material(name, side): the material of a node's face, from the node's name (without Blockbench's '--C<n>');
- tiles(assets): material -> a 32 px tile or a brush (brushes.py);
- ICON_VIEW, optional: the icon's view (icons.turned) instead of the isometric one of blocks;
- animation(nodes), optional: the model's looping blockyanim, written next to it (<MODEL>.blockyanim);
- SEE_THROUGH, optional: the nodes lit but casting no baked shadow (shown only part of the time by the animation);
- GLINT, optional: the name prefix of the crystal nodes that breathe and glint (glint.py), instead of animation;
- BREATHE, optional: False for crystals that only glint;
- SEED, optional: a number that gives each face its own pattern (paint.face_seed); without it, faces of one size and
  material look alike, as they always did;
- AXES, optional: {node name: box axis "x", "y" or "z"} along which a part's grain runs (paint.grain_of); without it,
  along each island's long side;
- CONDITION, optional: the model's condition (conditions.py): its tiles may then be layered materials
  (effects.material: coats and effects), and the model is lit in the hytale light; without it or with
  conditions.DEFAULT, the render of before;
- AGE, ENVIRONMENT, optional with CONDITION: the model's age (else the condition's) and environment (conditions.py);
- ROLES, USAGE, FOCUS, optional with CONDITION: {node name: role} (roles.py), {node name: roles.contact(…) and kin},
  and the nodes the eye goes to;
- ART, optional with CONDITION: the model's art (art.Art: detail budget, rest zones), else art.Art();
- HISTORY, optional with CONDITION: what happened to it, a few events (history.py: impact, fire, water…);
- DECALS, optional on a block model: {name: decals.Decal} small drawings on part of a face or past its border, laid
  as quads in front of it before the model is laid out, painted as drawings;
- IMPORTANCE, optional: {part: 0 calm, 1 normal, 2 important, 3 major accent}, the parts' visual importance the
  illustration pass and the composer read (illustration.importance_of);
- ILLUSTRATION, COMPOSER, optional with CONDITION: its illustration.Illustration and composer.Composer, the passes run
  between the layers and the light (spec 2026-10-04 blockpaint illustration, composer), their reports printed."""

import json
import math

import glint
from art import Art
from bake import light_map, survey
from brushes import drawing
from compat import FAMILIES
from composer_values import GROUP_CENTILES
from conditions import DEFAULT
from decals import node_name, place
from effects import Material, material
from history import resolved
from icons import ICON_SIZE, draw_model, frame
from models import walk
from pack import save_png, write_json
from paint import Look, Painting, islands, texture, tiled
from PIL import Image
from surface import Surface
from trim import faces, trim

# The material name of a decal: DECAL + its name in DECALS.
DECAL = "decal:"


def paint(modules, common, icons, assets):
    """Paints and draws the icon of every model of modules, trimming the blocks (trim.trim) first: their files under
    common (a pack's Common folder), their icons in icons."""
    for module in modules:
        if hasattr(module, "DECALS") and not module.MODEL.startswith("Blocks/"):
            raise SystemExit(f"{module.MODEL}: decals go on block models, which catalog lays out")
        model = common / (module.MODEL + ".blockymodel")
        data = json.loads(model.read_text(encoding="utf-8"))
        nodes = data["nodes"]
        if module.MODEL.startswith("Blocks/"):
            place(nodes, getattr(module, "DECALS", {}))
            read = faces(nodes)
            size = trim(nodes, module)
            write_json(model, data)
            print(f"{model.stem}: {read} faces read, {faces(nodes)} kept, texture {size[0]} x {size[1]}")
        image = model_texture(module, nodes, assets)
        if hasattr(module, "GLINT"):
            # The glint frames go below the texture; the icon reads only the islands above them.
            image, step = glint.frames(image, nodes, module.GLINT)
            write_json(model.with_suffix(".blockyanim"),
                       glint.animation(nodes, module.GLINT, step, getattr(module, "BREATHE", True)))
        elif hasattr(module, "animation"):
            write_json(model.with_suffix(".blockyanim"), module.animation(nodes))
        save_png(image, model.with_suffix(".png"))
        icon = Image.new("RGBA", (ICON_SIZE, ICON_SIZE), (0, 0, 0, 0))
        view = getattr(module, "ICON_VIEW", None)
        draw_model(icon, nodes, image, *frame(nodes, view), view)
        save_png(icon, icons / (module.ICON + ".png"))


def model_texture(module, nodes, assets):
    """The model's finished texture (paint.texture) from its module's materials and tiles; layered (surface.py) and
    in the hytale light when the module declares a CONDITION, else exactly as before."""
    condition = getattr(module, "CONDITION", None)
    # DEFAULT is the render of a model that declares nothing: the same bytes.
    if condition is None or condition is DEFAULT:
        see_through = getattr(module, "SEE_THROUGH", frozenset())
        return module_texture(module, nodes, assets, light_map(nodes, grounded(module), see_through), None)
    values, contexts = surveyed(module, nodes)
    return module_texture(module, nodes, assets, values, module_surface(module, contexts))


def module_texture(module, nodes, assets, values, surface):
    """The model's texture from its module's tiles, materials, seed and axes, lit by values: layered with surface in
    the hytale light, or as before without one; illustrated and composed when the module declares an ILLUSTRATION or a
    COMPOSER, their reports printed. Fails on either without a surface."""
    # A copy: the module's own tiles (a module-wide dict) stay as they are.
    tiles = dict(module.tiles(assets))
    if surface is not None:
        tiles = layered_tiles(module, tiles)
    tiles.update(decal_tiles(module, tiles, surface is not None))
    look = Look(tiles, material_of(module), module.PICTURES)
    light = "legacy" if surface is None else "hytale"
    illustration, composer, surface = passes(module, surface)
    importance = getattr(module, "IMPORTANCE", {})
    checked(importance, composer.groups if composer is not None else {}, nodes)
    painting = Painting(getattr(module, "SEED", None), getattr(module, "AXES", None), surface, light, illustration,
                        composer, importance)
    image = texture(nodes, texture_size(nodes), look, values, painting)
    for done in (illustration, composer):
        for line in done.report if done is not None else ():
            print(f"  {line}")
    return image


def checked(importance, groups, nodes):
    """Fails on an importance or a value group (composer.Composer.groups) naming a part the model has not, on an
    importance out of 0..3 and on an unknown value group."""
    parts = {n["name"].split("--")[0] for n in walk(nodes)}
    unknown = sorted((set(importance) | set(groups)) - parts)
    if unknown:
        raise SystemExit(f"IMPORTANCE or COMPOSER groups name parts the model has not: {', '.join(unknown)}")
    wrong = sorted(f"{part} {level}" for part, level in importance.items() if level not in (0, 1, 2, 3))
    wrong += sorted(f"{part} {group}" for part, group in groups.items() if group not in GROUP_CENTILES)
    if wrong:
        raise SystemExit(f"importances are 0 to 3, groups {', '.join(GROUP_CENTILES)}: {', '.join(wrong)}")


def passes(module, surface):
    """(illustration, composer, surface) a module declares (ILLUSTRATION, COMPOSER; None when it does not), each with a
    report of this paint's own (a module-wide list would gather every paint's in one process), surface recording its
    islands when either is declared. Fails on either without a surface: both read the layered islands."""
    declared = [getattr(module, name, None) for name in ("ILLUSTRATION", "COMPOSER")]
    if all(p is None for p in declared):
        return None, None, surface
    if surface is None:
        raise SystemExit("an ILLUSTRATION or a COMPOSER needs a CONDITION other than DEFAULT: it reads the layers")
    illustration, composer = (p._replace(report=[]) if p is not None else None for p in declared)
    return illustration, composer, surface if surface.record is not None else surface._replace(record=[])


def material_of(module):
    """material_of(node name, side) of the module's model as catalog paints it: a decal's node its DECAL material, any
    other node the module's material of its name before Blockbench's '--C<n>' (a group's later cubes)."""
    decal_of = {node_name(d.part, name): DECAL + name for name, d in getattr(module, "DECALS", {}).items()}

    def of(name, side):
        return decal_of.get(name) or module.material(name.split("--")[0], side)
    return of


def decal_tiles(module, tiles, layered):
    """{DECAL + name: material} of the module's DECALS: each its drawing, layered when the model is, of the family of
    its part's material on its side (what settles on the part settles on it). Fails, when layered, on a decal whose
    face's material is not in tiles."""
    found = {}
    for name, decal in getattr(module, "DECALS", {}).items():
        under = module.material(decal.part, decal.side)
        if layered and under not in tiles:
            raise SystemExit(f"decal {name}: {decal.part} {decal.side} is of {under}, a material the module has not")
        found[DECAL + name] = material(drawing(decal.brush), tiles[under].family) if layered else drawing(decal.brush)
    return found


def layered_tiles(module, tiles):
    """tiles as layered materials, the model layered whole: a brush with its family, an image tile the substrate of
    its own (paint.tiled), a picture a drawing (never calmed nor worn: surface.layered), of the family the module's
    FAMILY names over its brush's or its material's. Fails on a material left without a family (every effect would
    reach it) or of an unknown one."""
    named = getattr(module, "FAMILY", {})
    layered = {}
    for name, tile in tiles.items():
        if not isinstance(tile, Material):
            tile = material(tile if callable(tile) else tiled(tile, name in module.PICTURES))
        if name in named:
            # Rebuilt, not replaced: material refuses an effect of the tile's own impossible on the new family.
            tile = material(tile.substrate, named[name], tile.coats, tile.effects, tile.weights)
        if name in module.PICTURES and not getattr(tile.substrate, "drawn", False):
            tile = tile._replace(substrate=drawing(tile.substrate))
        if tile.family not in FAMILIES:
            raise SystemExit(f"layered material {name}: no known family ({tile.family}); give its brush one or name it"
                             " in FAMILY")
        layered[name] = tile
    return layered


def grounded(module):
    """Whether the module's model stands on a floor that shades its foot: its GROUNDED, else whether it is a block
    (its MODEL under Blocks/)."""
    return getattr(module, "GROUNDED", getattr(module, "MODEL", "").startswith("Blocks/"))


def surveyed(module, nodes):
    """(values, contexts) of bake.survey for the model of a module declaring a CONDITION: grounded (grounded()), its
    SEE_THROUGH nodes cast nothing, the bake's own chips and grime are its condition's."""
    see_through = getattr(module, "SEE_THROUGH", frozenset())
    return survey(nodes, grounded(module), see_through, module.CONDITION.wear, module.CONDITION.grime)


def module_surface(module, contexts, record=None):
    """The Surface a module declaring a CONDITION paints its layered materials with, its HISTORY resolved on contexts;
    record, a list, receives each layered island (semantics.painted)."""
    return Surface(contexts, module.CONDITION, getattr(module, "AGE", None), getattr(module, "ENVIRONMENT", None),
                   getattr(module, "ROLES", {}), getattr(module, "USAGE", {}), getattr(module, "FOCUS", ()),
                   getattr(module, "SEED", None), getattr(module, "ART", Art()),
                   resolved(getattr(module, "HISTORY", ()), contexts), record=record)


def texture_size(nodes):
    """The smallest texture holding every island, each side a multiple of 32 (Hytale's rule)."""
    rects = list(islands(nodes))
    return tuple(32 * math.ceil(max(r[i] + r[i + 2] for r in rects) / 32) for i in (2, 3))
