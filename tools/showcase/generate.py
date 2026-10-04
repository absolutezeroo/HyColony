"""Paints blockpaint's showcase (spec 2026-10-04 blockpaint apothecary): the apothecary's workbench, built by code
(apothecary_model.py) and painted by the catalog (apothecary.py) in its three states, into tools/showcase/out (out of
every pack: it validates the pipeline and shows it off, it is no block of the game).

    python tools/showcase/generate.py
"""

import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path += [str(HERE.parent / "blockpaint"), str(HERE.parent / "huts")]
import apothecary  # noqa: E402
import catalog  # noqa: E402
from apothecary_model import nodes  # noqa: E402
from pack import rounded  # noqa: E402

OUT = HERE / "out"


def main():
    """Writes the bench's model once per state, then lets the catalog lay it out, paint it and draw its icon."""
    built = nodes()
    names = [n["name"] for n in built]
    modules = [apothecary.module(state, names) for state in apothecary.STATES]
    for module in modules:
        path = OUT / (module.MODEL + ".blockymodel")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(rounded({"format": "prop", "lod": "auto", "nodes": built}), indent=2) + "\n",
                        encoding="utf-8", newline="\n")
    (OUT / "Icons").mkdir(parents=True, exist_ok=True)
    catalog.paint(modules, OUT, OUT / "Icons", None)


if __name__ == "__main__":
    main()
