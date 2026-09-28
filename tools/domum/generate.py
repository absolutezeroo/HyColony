"""Generates the Domum Ornamentum templates (DO-1): every DO shape converted to a model reading its material layout,
one template block per shape with its states, rotation, hitbox and connections, the shape manifest and the creative
tab; the materials themselves are chosen at runtime (docs/superpowers/specs/2026-09-28-hycolony-domum-ornamentum-
do1-design.md).

Run by hand, then commit the outputs (in the HyDomum mod's resources, domum/plugin/src/main/resources): the
build never runs it. Downloads DO's pinned commit once into build/domum-cache/. Needs Python 3.10+ and Pillow.

    python tools/domum/generate.py [path/to/Assets.zip]
"""

import shutil
import sys
from pathlib import Path

sys.path.append(str(Path(__file__).resolve().parents[1] / "vanilla"))
import manifest  # noqa: E402
import source  # noqa: E402
import tabs  # noqa: E402
import tags  # noqa: E402
import validate  # noqa: E402
from blocks import common, compat, cutter, door, pane, pillar, roof, static  # noqa: E402
from families import FAMILIES  # noqa: E402
from pack import ROOT, validate_pack, write_json  # noqa: E402

PACK = ROOT / "domum" / "plugin" / "src" / "main" / "resources"
RESOURCES = PACK
# Mechanism -> the module writing its templates.
GENERATORS = {"static": static.generate, "roof": roof.shingles, "shingle_slab": roof.shingle_slab,
              "door": door.doors, "trapdoor": door.trapdoors, "pillar": pillar.generate, "pane": pane.generate,
              "vanilla": compat.generate}


# What the generator owns inside the mod's resources; the rest (the cutter's .ui, hydomum.lang, manifest.json) is
# written by hand and must survive a regeneration.
GENERATED = ("Common/Blocks/HyDomum", "Common/Icons/ItemsGenerated/HyDomum", "Server/Item/Block/Hitboxes/HyDomum",
             "Server/Item/CustomConnectedBlockTemplates", "Server/Item/Items/HyDomum", "hydomum")
GENERATED_FILES = ("Common/Icons/ItemCategories/HyDomum*.png", "Server/Item/Category/CreativeLibrary/HyDomum.json",
                   "Server/Languages/*/hydomum_blocks.lang")


def clear(pack):
    """Removes what a previous run generated in pack, and nothing else."""
    for generated in GENERATED:
        shutil.rmtree(pack / generated, ignore_errors=True)
    for pattern in GENERATED_FILES:
        for path in pack.glob(pattern):
            path.unlink()


def run(pack, resources, assets):
    """Regenerates everything into pack and resources; returns the Context. Fails on an invalid pack."""
    clear(pack)
    shutil.rmtree(resources / "hydomum", ignore_errors=True)
    ctx = common.Context(assets, source.fetch(), pack, resources, tags.build(assets))
    for family in FAMILIES:
        generator = GENERATORS.get(family.mechanism)
        # All-brick blocks (a material cube under a fixed brick overlay, forge:composite) are out of DO-1: the
        # runtime would have to compose the overlay (spec, "Hors DO-1").
        if generator and not family.name.startswith("AllBrick"):
            generator(ctx, family)
    cutter.generate(ctx)
    manifest.write(ctx)
    ctx.tab = tabs.generate(ctx)
    write_json(pack / "hydomum" / "id-map.json", {"sounds": ctx.sounds, "ornamentTags": ctx.tags})
    for language, lines in ctx.lang.items():
        path = pack / "Server" / "Languages" / language / "hydomum_blocks.lang"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("\n".join(sorted(lines)) + "\n", encoding="utf-8", newline="\n")
    validate_pack(assets, pack)
    validate.pack(ctx)
    return ctx


def main():
    ctx = run(PACK, RESOURCES, tags.open_assets(sys.argv[1] if len(sys.argv) > 1 else None))
    print(f"{len(ctx.items)} templates, {len(ctx.shapes)} shapes")


if __name__ == "__main__":
    main()
