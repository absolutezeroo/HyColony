#!/usr/bin/env python3
"""Génère une plateforme d'atelier pour poser tous ses blueprints en jeu.

    python plateforme.py dossier_des_blueprints/ -o plateforme/

Avec --avec-batiments, produit aussi plateforme_complete.prefab.json : la
plateforme avec tous les bâtiments déjà posés.

Produit plateforme.prefab.json, plan.md (où coller chaque prefab),
plan.svg (vue de dessus) et positions.json.
"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))  # tools/, pour le paquet blueprint

from blueprint import domum  # noqa: E402
from blueprint.converter import Converter, Options  # noqa: E402
from blueprint.platform import generate  # noqa: E402

if __name__ == "__main__":
    ap = argparse.ArgumentParser(description="Plateforme d'atelier pour blueprints MineColonies.")
    ap.add_argument("dossier", help="dossier contenant les .blueprint")
    ap.add_argument("-o", "--output", default="plateforme", help="dossier de sortie")
    ap.add_argument("--pack", action="store_true",
                    help="dossier prêt pour le jeu : un dossier par type de hutte avec ses prefabs, "
                         "plus plateforme, plateforme_complete et plan.md")
    ap.add_argument("--avec-batiments", action="store_true",
                    help="produit aussi plateforme_complete.prefab.json, avec tous les bâtiments posés")
    ap.add_argument("--variantes-hydomum", metavar="FICHIER",
                    help="blocs Domum avec leurs matériaux, et leurs variantes ajoutées au variants.json de HyDomum "
                         "(universe/hydomum/variants.json du serveur), qui les crée au démarrage ; implique "
                         "--avec-batiments. Serveur arrêté : il réécrit ce fichier depuis sa mémoire")
    a = ap.parse_args()
    files = sorted(p for p in Path(a.dossier).rglob("*") if p.suffix.lower() == ".blueprint")
    if not files:
        sys.exit(f"Aucun .blueprint dans {a.dossier}")
    converter = Converter(Options(domum_materials=bool(a.variantes_hydomum)))
    with_buildings = a.avec_batiments or bool(a.variantes_hydomum)
    info = generate(files, Path(a.output), with_buildings=with_buildings, converter=converter, pack=a.pack)
    print(f"{info['emplacements']} emplacements, plateforme {info['taille'][0]} x {info['taille'][1]}, "
          f"{info['blocs']} blocs, {info['trous']} case(s) laissée(s) ouverte(s) -> {a.output}/")
    if "complete" in info:
        c = info["complete"]
        print(f"plateforme complète : {c['batiments']} bâtiments, {c['blocs_total']} blocs")
    if a.variantes_hydomum:
        try:
            n = domum.register_from_prefabs(Path(a.output).rglob("*.prefab.json"), Path(a.variantes_hydomum))
        except ValueError as e:
            sys.exit(f"variantes HyDomum non enregistrées : {e}")
        print(f"variantes HyDomum ajoutées à {a.variantes_hydomum} : {n} (prises en compte au prochain démarrage)")
