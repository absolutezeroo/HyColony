#!/usr/bin/env python3
"""Point d'entrée : python convert.py <fichier.blueprint | dossier> [options]

Sous Windows, tu peux aussi glisser un fichier ou un dossier sur ce script.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))  # tools/, pour le paquet blueprint

from blueprint.cli import main  # noqa: E402

if __name__ == "__main__":
    sys.exit(main())
