"""blockpaint: paints every set of hand-built Blockbench models of the mods, or the sets named.

    python tools/blockpaint [huts] [armor] [tape] [vanilla] [--assets path/to/Assets.zip]

Each set is a folder of tools/ whose generate.py describes its models and calls the engine (catalog.paint for the
models a module describes, paint/bake/icons directly for the others, built by code or in Blockbench): huts
(HyColony's huts and held items), armor (the plate armor, the knight's sword and shield), tape (the construction
tape), vanilla (HyVanilla's beds and flower pots). HyDomum's converter (tools/domum) is a pipeline of its own and
stays apart."""

import runpy
import sys
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
SETS = ("huts", "armor", "tape", "vanilla")


def main(args):
    """Runs the generate.py of each set named in args (every set when none), passing --assets on."""
    assets = []
    if "--assets" in args:
        at = args.index("--assets")
        if at + 1 == len(args):
            raise SystemExit("blockpaint: --assets needs a path")
        assets, args = args[at + 1:at + 2], args[:at] + args[at + 2:]
    unknown = [name for name in args if name not in SETS]
    if unknown:
        raise SystemExit(f"blockpaint: unknown set {', '.join(unknown)} (sets: {', '.join(SETS)})")
    for name in args or SETS:
        print(f"== {name}")
        script = TOOLS / name / "generate.py"
        sys.argv = [str(script), *assets]
        # Each set runs as when alone: its folder first on the path, and none of the modules or path entries of the
        # sets before it (two sets may each have a module of the same name).
        modules, path = set(sys.modules), list(sys.path)
        sys.path.insert(0, str(script.parent))
        try:
            runpy.run_path(str(script), run_name="__main__")
        finally:
            for module in set(sys.modules) - modules:
                del sys.modules[module]
            sys.path[:] = path


if __name__ == "__main__":
    main(sys.argv[1:])
