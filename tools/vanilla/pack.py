"""Shared helpers of the HyVanilla generator: the vanilla assets zip, file writers and isometric icons."""

import io
import json
import math
import zipfile
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
PACK = ROOT / "vanilla" / "plugin" / "src" / "main" / "resources"
ICON_SIZE = 64


def _gradle_property(name):
    for line in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines():
        key, sep, value = line.partition("=")
        if sep and key.strip() == name:
            return value.strip()
    raise KeyError(name)


# The assets zip the Gradle plugin caches for the pinned Hytale version (downloadAssetsZip), so a version bump in
# gradle.properties needs no edit here.
GRADLE_ASSETS = (Path.home() / ".gradle" / "caches" / "hytale-assets"
                 / f"{_gradle_property('patchline')}-{_gradle_property('hytale_version')}-Assets.zip")


# Hytale's CommonAssetValidator roots (plugin-b-api § 23): a path outside them, or missing, stops the whole server.
ICON_ROOTS = ("Icons/ItemsGenerated/", "Icons/Items/")
MODEL_ROOTS = ("Blocks/", "Items/", "Resources/", "NPC/", "VFX/", "Consumable/")
TEXTURE_ROOTS = ("Blocks/", "BlockTextures/", "Items/", "NPC/", "Resources/", "VFX/")


class Assets:
    """Read-only view of the vanilla assets zip."""

    def __init__(self, path):
        self.zip = zipfile.ZipFile(path)
        self.names = set(self.zip.namelist())

    def json(self, name):
        return json.loads(self.zip.read(name).decode("utf-8"))

    def image(self, name):
        return Image.open(io.BytesIO(self.zip.read(name))).convert("RGBA")

    def has(self, name):
        return name in self.names

    def item(self, item_id):
        """The item JSON with its Parent chain merged (BlockType merged key by key, like the codec's inheritance)."""
        if not hasattr(self, "_items"):
            self._items = {n.rsplit("/", 1)[1][:-5]: n for n in self.names
                           if n.startswith("Server/Item/Items/") and n.endswith(".json")}
        path = self._items[item_id]
        data = self.json(path)
        parent = data.get("Parent")
        if not parent:
            return data
        merged = self.item(parent)
        for key, value in data.items():
            if key == "BlockType" and isinstance(value, dict):
                merged["BlockType"] = {**merged.get("BlockType", {}), **value}
            else:
                merged[key] = value
        return merged


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


def iso(x, y, z, scale, origin):
    """Screen position of a model point (units) in the icon's isometric view, seen from +x +z."""
    return (origin[0] + (x - z) * scale * 0.866, origin[1] + (x + z) * scale * 0.5 - y * scale)


def draw_face(icon, texture, corner, edge_u, edge_v, shade):
    """Draws texture onto the screen parallelogram corner + s*edge_u + t*edge_v (s, t in [0, 1])."""
    w, h = texture.size
    det = edge_u[0] * edge_v[1] - edge_u[1] * edge_v[0]
    if abs(det) < 1e-6:
        return
    # Inverse affine map screen -> texture pixel, as Image.transform expects.
    a, b = edge_v[1] / det * w, -edge_v[0] / det * w
    d, e = -edge_u[1] / det * h, edge_u[0] / det * h
    c = -(a * corner[0] + b * corner[1])
    f = -(d * corner[0] + e * corner[1])
    face = texture.transform(icon.size, Image.AFFINE, (a, b, c, d, e, f), resample=Image.NEAREST)
    if shade != 1.0:
        rgb = face.convert("RGB").point(lambda p: int(p * shade))
        face = Image.merge("RGBA", (*rgb.split(), face.getchannel("A")))
    mask = Image.new("L", icon.size, 0)
    points = [corner, (corner[0] + edge_u[0], corner[1] + edge_u[1]),
              (corner[0] + edge_u[0] + edge_v[0], corner[1] + edge_u[1] + edge_v[1]),
              (corner[0] + edge_v[0], corner[1] + edge_v[1])]
    ImageDraw.Draw(mask).polygon(points, fill=255)
    face.putalpha(ImageChops.multiply(face.getchannel("A"), mask))
    icon.alpha_composite(face)


def draw_box(icon, box, textures, scale, origin):
    """Draws an axis-aligned box ((x0, y0, z0), (x1, y1, z1)) with its +x, +z and top faces textured."""
    (x0, y0, z0), (x1, y1, z1) = box

    def p(x, y, z):
        return iso(x, y, z, scale, origin)

    def edge(a, b):
        return (b[0] - a[0], b[1] - a[1])

    front = p(x0, y1, z1)
    draw_face(icon, textures["front"], front, edge(front, p(x1, y1, z1)), edge(front, p(x0, y0, z1)), 0.8)
    right = p(x1, y1, z1)
    draw_face(icon, textures["right"], right, edge(right, p(x1, y1, z0)), edge(right, p(x1, y0, z1)), 0.65)
    top = p(x0, y1, z0)
    draw_face(icon, textures["top"], top, edge(top, p(x1, y1, z0)), edge(top, p(x0, y1, z1)), 1.0)


def draw_model(icon, nodes, texture, scale, origin):
    """Draws a model's unrotated boxes (their +z, +x and top faces, read from their texture islands) with a depth
    buffer, so boxes that pass through each other (a sheet fold over a blanket) hide each other correctly."""
    texels, out = texture.load(), icon.load()
    nearest = {}
    for n in nodes:
        shape, position = n["shape"], n["position"]
        if shape["type"] != "box" or n.get("children") or n["orientation"]["w"] != 1:
            raise SystemExit(f"{n['name']}: draw_model takes flat, unrotated boxes")
        size = shape["settings"]["size"]
        centre = [position[a] + shape["offset"][a] for a in "xyz"]
        low = tuple(centre[i] - size[a] / 2 for i, a in enumerate("xyz"))
        high = tuple(centre[i] + size[a] / 2 for i, a in enumerate("xyz"))
        for side, face in shape["textureLayout"].items():
            if side in ICON_SIDES:
                for point, texel in face_samples(side, low, high, face, scale):
                    sx, sy = iso(*point, scale, origin)
                    pixel, depth = (int(sx), int(sy)), sum(point)
                    colour = texels[texel]
                    if colour[3] and 0 <= pixel[0] < icon.width and 0 <= pixel[1] < icon.height \
                            and depth > nearest.get(pixel, float("-inf")):
                        nearest[pixel] = depth
                        out[pixel] = (*(int(c * ICON_SIDES[side]) for c in colour[:3]), 255)


# Faces an icon shows (seen from +x +y +z) -> their shade, as draw_box.
ICON_SIDES = {"front": 0.8, "right": 0.65, "top": 1.0}


def face_samples(side, low, high, face, scale):
    """(model point, texel) pairs covering a face, about two samples per icon pixel each way. The view looks along
    (1, 1, 1), so the nearest point has the largest x + y + z."""
    (x0, y0, z0), (x1, y1, z1) = low, high
    w, h = {"front": (x1 - x0, y1 - y0), "right": (z1 - z0, y1 - y0), "top": (x1 - x0, z1 - z0)}[side]
    columns, rows = max(1, math.ceil(w * scale * 2)), max(1, math.ceil(h * scale * 2))
    u0, v0 = face["offset"]["x"], face["offset"]["y"]
    for i in range(columns):
        for j in range(rows):
            s, t = (i + 0.5) / columns * w, (j + 0.5) / rows * h
            point = {"front": (x0 + s, y1 - t, z1), "right": (x1, y1 - t, z1 - s), "top": (x0 + s, y1, z0 + t)}[side]
            yield point, (int(u0 + s), int(v0 + t))


def save_png(image, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)


def validate_pack(assets, pack=PACK):
    """Fails loudly on what Hytale would refuse in the pack's items: a Common path outside its root, of the wrong type
    or missing (from the pack and the vanilla assets), an unknown item, hitbox, sound or particle set, material,
    animation set, or crafting bench and category."""
    errors = []
    items = {p.stem: p for p in (pack / "Server/Item/Items").rglob("*.json")}
    hitboxes = {p.stem for p in (pack / "Server/Item/Block/Hitboxes").rglob("*.json")}

    def vanilla(folder):
        return {n.rsplit("/", 1)[-1][:-5] for n in assets.names if n.startswith(folder) and n.endswith(".json")}

    known = {
        "ItemId": set(items) | vanilla("Server/Item/Items/"),
        # "Full" is built in, not an asset (BlockBoundingBoxes.DEFAULT, the unit box).
        "HitboxType": hitboxes | vanilla("Server/Item/Block/Hitboxes/") | {"Full"},
        "BlockSoundSetId": vanilla("Server/Item/Block/Sounds/"),
        "BlockParticleSetId": vanilla("Server/Item/Block/Particles/"),
        "PhysicalMaterialId": vanilla("Server/Item/Block/PhysicalMaterials/"),
        "ItemSoundSetId": vanilla("Server/Audio/ItemSounds/"),
        "PlayerAnimationsId": vanilla("Server/Item/Animations/"),
        "ResourceTypeId": vanilla("Server/Item/ResourceTypes/"),
    }
    benches = vanilla_benches(assets)

    def check_recipe(name, recipe):
        for bench in recipe.get("BenchRequirement", []):
            categories = benches.get((bench["Id"], bench["Type"]))
            if categories is None or not set(bench.get("Categories", [])) <= categories:
                errors.append(f"{name}: no vanilla bench {bench}")

    def common(path, roots, extension, where):
        if not path.startswith(roots) or not path.endswith(extension):
            errors.append(f"{where}: {path} must be a {extension} under {roots}")
        elif not (pack / "Common" / path).is_file() and not assets.has("Common/" + path):
            errors.append(f"{where}: {path} does not exist")

    for name, path in sorted(items.items()):
        data = json.loads(path.read_text(encoding="utf-8"))
        common(data["Icon"], ICON_ROOTS, ".png", name)
        for key, value in walk_json(data):
            if key in ("CustomModel", "Model"):
                common(value, MODEL_ROOTS, ".blockymodel", name)
            elif key == "Texture" or key in ("All", "Sides", "Top", "Bottom"):
                common(value, TEXTURE_ROOTS, ".png", name)
            elif key in known and value not in known[key]:
                errors.append(f"{name}: unknown {key} {value}")
        check_recipe(name, data.get("Recipe", {}))
    # Standalone recipes (Server/Item/Recipes, an item holds only one Recipe): known items, resources and benches.
    for path in sorted((pack / "Server/Item/Recipes").rglob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        for key, value in walk_json(data):
            if key in known and value not in known[key]:
                errors.append(f"{path.stem}: unknown {key} {value}")
        check_recipe(path.stem, data)
    if errors:
        raise SystemExit(f"Invalid {pack.name} pack:\n  " + "\n  ".join(errors))


def vanilla_benches(assets):
    """(bench id, type) -> its category ids, from the vanilla items' BlockType.Bench."""
    benches = {}
    for name in assets.names:
        if not (name.startswith("Server/Item/Items/") and name.endswith(".json")):
            continue
        try:
            block = assets.json(name).get("BlockType")
        except ValueError:
            continue
        bench = block.get("Bench") if isinstance(block, dict) else None
        if isinstance(bench, dict) and "Id" in bench:
            categories = {c["Id"] for c in bench.get("Categories", []) if isinstance(c, dict)}
            benches.setdefault((bench["Id"], bench.get("Type")), set()).update(categories)
    return benches


def walk_json(data):
    """Every (key, string value) pair, at any depth."""
    if isinstance(data, dict):
        for key, value in data.items():
            if isinstance(value, str):
                yield key, value
            else:
                yield from walk_json(value)
    elif isinstance(data, list):
        for value in data:
            yield from walk_json(value)
