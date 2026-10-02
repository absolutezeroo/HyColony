"""Where HyVanilla's generators (generate.py, beds.py, flower_pots.py) write: the mod's resource pack."""

from pathlib import Path

PACK = Path(__file__).resolve().parents[2] / "vanilla" / "plugin" / "src" / "main" / "resources"
