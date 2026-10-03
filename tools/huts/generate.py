"""HyColony's hand-built models (spec 2026-10-02 hut models: hut blocks, build goggles, build tool, clipboard), painted
with blockpaint (tools/blockpaint/catalog.py: each module below describes its model; the brushes of the set are in
materials.py).

Each model is built in Blockbench (docs/research/hytale-models.md; the clipboard, lumberjack, miner, quarries,
plantation and florist by one-off scripts) and saved to plugin/src/main/resources/Common/<MODEL>.blockymodel. Run
after changing a model or its materials, then commit the outputs: `python tools/blockpaint huts` (or this script).

    python tools/huts/generate.py [path/to/Assets.zip]
"""

import sys
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path.append(str(TOOLS / "blockpaint"))
import build_tool  # noqa: E402
import builder  # noqa: E402
import catalog  # noqa: E402
import clipboard  # noqa: E402
import cook  # noqa: E402
import courier  # noqa: E402
import farmer  # noqa: E402
import florist  # noqa: E402
import goggles  # noqa: E402
import lumberjack  # noqa: E402
import miner  # noqa: E402
import plantation  # noqa: E402
import quarry_large  # noqa: E402
import quarry_medium  # noqa: E402
import quarry_small  # noqa: E402
import residence  # noqa: E402
import town_hall  # noqa: E402
import warehouse  # noqa: E402
from pack import GRADLE_ASSETS, ROOT, Assets  # noqa: E402
from pack_rules import validate_pack  # noqa: E402

RESOURCES = ROOT / "plugin" / "src" / "main" / "resources"
MODELS = (builder, town_hall, residence, farmer, cook, courier, warehouse, lumberjack, miner, quarry_small,
          quarry_medium, quarry_large, plantation, florist, goggles, build_tool, clipboard)


def main():
    assets = Assets(Path(sys.argv[1]) if len(sys.argv) > 1 else GRADLE_ASSETS)
    catalog.paint(MODELS, RESOURCES / "Common", RESOURCES / "Common/Icons/Items/HyColony", assets)
    validate_pack(assets, RESOURCES)


if __name__ == "__main__":
    main()
