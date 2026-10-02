"""Validates a generated pack's items against what Hytale accepts, before the server refuses them (a bad asset in an
immutable pack stops the whole server, plugin-b-api § 23)."""

import json

# Hytale's CommonAssetValidator roots (plugin-b-api § 23): a path outside them, or missing, stops the whole server.
ICON_ROOTS = ("Icons/ItemsGenerated/", "Icons/Items/")
MODEL_ROOTS = ("Blocks/", "Items/", "Resources/", "NPC/", "VFX/", "Consumable/")
TEXTURE_ROOTS = ("Blocks/", "BlockTextures/", "Items/", "NPC/", "Resources/", "VFX/")


def validate_pack(assets, pack):
    """Fails loudly on what Hytale would refuse in the pack's items: a Common path outside its root, of the wrong type
    or missing (from the pack and the vanilla assets; models, block animations, textures, icons), an unknown item,
    hitbox, sound or particle set, particle system or spawner, material, animation set, or crafting bench and
    category."""
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
        # A particle system's id is its file name (ModelParticle validates SystemId: an unknown one stops the server).
        "SystemId": {p.stem for p in (pack / "Server/Particles").rglob("*.particlesystem")}
        | {n.rsplit("/", 1)[-1][:-15] for n in assets.names if n.endswith(".particlesystem")},
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
            elif key == "CustomModelAnimation":
                # CommonAssetValidator.ANIMATION_ITEM_BLOCK: the model roots, a .blockyanim.
                common(value, MODEL_ROOTS, ".blockyanim", name)
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
    # The pack's particle systems: each spawner they use exists (ParticleSpawnerGroup validates SpawnerId).
    spawners = ({p.stem for p in (pack / "Server/Particles").rglob("*.particlespawner")}
                | {n.rsplit("/", 1)[-1][:-16] for n in assets.names if n.endswith(".particlespawner")})
    for path in sorted((pack / "Server/Particles").rglob("*.particlesystem")):
        system = json.loads(path.read_text(encoding="utf-8"))
        if not system.get("Spawners"):
            errors.append(f"{path.stem}: no Spawners (ParticleSystem requires a non-empty array)")
        for spawner in system.get("Spawners") or []:
            if spawner.get("SpawnerId") not in spawners:
                errors.append(f"{path.stem}: unknown SpawnerId {spawner.get('SpawnerId')}")
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
