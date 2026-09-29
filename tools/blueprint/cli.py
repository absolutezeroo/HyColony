"""Interface en ligne de commande.

    python convert.py maison.blueprint
    python convert.py maison.blueprint sortie.prefab.json rapport.json   (ancien format)
    python convert.py dossier_du_style/ -o prefabs/
"""
from __future__ import annotations

import argparse
import json
import sys
from collections import Counter
from pathlib import Path

from . import __version__
from .converter import Converter, Options, Result, load_mapping_csv
from .validation import load_known_ids, unknown_targets

SUFFIX_PREFAB = ".prefab.json"
SUFFIX_REPORT = ".report.json"
SUFFIX_TRACE = ".trace.json"


def build_report(res: Result, known: set[str], known_src: str) -> dict:
    bp = res.blueprint
    prefab_blocks = [c for c in res.cells if c.mapping.target]
    targets = Counter(c.mapping.target for c in prefab_blocks)
    fluids = Counter(c.mapping.fluid for c in res.cells if c.mapping.fluid)
    notes = Counter(n for c in res.cells for n in c.mapping.notes if c.mapping.rule != "placeholder")
    upstream_used = Counter(f"{c.source.get('Name')} -> {c.mapping.target}"
                            for c in res.cells if c.mapping.rule == "upstream" and c.mapping.target)
    upstream_deferred = Counter(c.source.get("Name") for c in res.cells if c.mapping.rule == "upstream_deferred")
    unknown = unknown_targets(targets, known)
    warnings = list(bp.warnings)
    if unknown:
        warnings.append(f"{len(unknown)} ID(s) Hytale inconnu(s) de la {known_src} : voir 'ids_inconnus'.")
    return {
        "convertisseur": f"blueprint2hytale {__version__}",
        "source": bp.name,
        "dimensions": dict(zip("xyz", bp.size)),
        "ancre": {**dict(zip("xyz", bp.anchor)), "methode": bp.anchor_method},
        "blocs_emis": len(prefab_blocks),
        "non_mappes": dict(res.unmapped().most_common()),
        "ids_inconnus": unknown,
        "avertissements": warnings,
        "assemblages": {**res.detection.stats, **res.detection.details},
        "secours_hytaleshub": {
            "utilises": dict(upstream_used.most_common()),
            "refuses_car_directionnels": dict(upstream_deferred.most_common()),
        },
        "cibles": dict(targets.most_common()),
        "fluides": dict(fluids.most_common()),
        "notes": dict(notes.most_common()),
    }


def write_json(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")


def strip_suffix(name: str) -> str:
    return name[: -len(".blueprint")] if name.lower().endswith(".blueprint") else Path(name).stem


def convert_one(conv: Converter, src: Path, prefab_path: Path, report_path: Path,
                trace: bool, known: set[str], known_src: str) -> dict:
    res = conv.convert_file(src)
    write_json(prefab_path, res.prefab())
    report = build_report(res, known, known_src)
    write_json(report_path, report)
    if trace:  # une case par ligne : facile à parcourir et à « grep »
        lines = ",\n".join(json.dumps(t, ensure_ascii=False) for t in res.trace())
        trace_path = report_path.with_name(report_path.name.replace(SUFFIX_REPORT, "") + SUFFIX_TRACE)
        trace_path.write_text("[\n" + lines + "\n]\n", encoding="utf-8")
    return report


def print_line(label: str, report: dict) -> None:
    flags = []
    if report["non_mappes"]:
        flags.append(f"{sum(report['non_mappes'].values())} non mappé(s)")
    if report["ids_inconnus"]:
        flags.append(f"{len(report['ids_inconnus'])} ID inconnu(s)")
    if any(w.startswith("Aucune ancre") for w in report["avertissements"]):
        flags.append("ancre introuvable")
    status = "OK " if not flags else "!! "
    print(f"  {status}{label}  ({report['blocs_emis']} blocs){'  -> ' + ', '.join(flags) if flags else ''}")


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(
        prog="convert.py",
        description="Convertit des blueprints MineColonies/Structurize en prefabs Hytale.")
    ap.add_argument("entree", nargs="?", help="fichier .blueprint ou dossier (parcouru récursivement)")
    ap.add_argument("sortie", nargs="?", help="(fichier seul) chemin du prefab à écrire")
    ap.add_argument("rapport", nargs="?", help="(fichier seul) chemin du rapport à écrire")
    ap.add_argument("-o", "--output", help="dossier de sortie")
    ap.add_argument("--overrides", help="CSV minecraft_block,hytale_block prioritaire (format HytalesHub)")
    ap.add_argument("--ids", help="liste d'IDs Hytale valides, un par ligne (remplace la liste fournie)")
    ap.add_argument("--no-upstream", action="store_true", help="désactive la table de secours HytalesHub")
    ap.add_argument("--sans-blocs-editeur", action="store_true",
                    help="omet air et substitutions au lieu de les traduire en Empty / "
                         "HyColony_Placeholder_Solid|Fluid / Editor_Anchor")
    ap.add_argument("--domum-materiaux", action="store_true",
                    help="blocs Domum avec leurs matériaux (<gabarit>__<m1>__<m2>) ; demande que HyDomum sache créer "
                         "ces matériaux au chargement d'un prefab (DO-3). Par défaut : le gabarit HyDomum")
    ap.add_argument("--trace", action="store_true", help="écrit aussi, pour chaque case, la règle appliquée")
    ap.add_argument("--strict", action="store_true", help="code de sortie 1 s'il reste des non mappés ou des IDs inconnus")
    ap.add_argument("--version", action="version", version=f"blueprint2hytale {__version__}")
    args = ap.parse_args(argv)

    if not args.entree:
        if sys.stdin.isatty():
            args.entree = input("Glisse ici un fichier .blueprint ou un dossier, puis Entrée : ").strip().strip('"')
        if not args.entree:
            ap.print_help()
            return 2

    src = Path(args.entree)
    if not src.exists():
        print(f"Introuvable : {src}", file=sys.stderr)
        return 2

    options = Options(use_upstream_csv=not args.no_upstream, editor_blocks=not args.sans_blocs_editeur,
                      overrides=load_mapping_csv(args.overrides) if args.overrides else {},
                      domum_materials=args.domum_materiaux)
    conv = Converter(options)
    known, known_src = load_known_ids(args.ids)
    problems = 0

    if src.is_file():
        stem = strip_suffix(src.name)
        out_dir = Path(args.output) if args.output else src.parent
        prefab_path = Path(args.sortie) if args.sortie else out_dir / (stem + SUFFIX_PREFAB)
        report_path = Path(args.rapport) if args.rapport else prefab_path.with_name(
            prefab_path.name.replace(SUFFIX_PREFAB, "") + SUFFIX_REPORT)
        report = convert_one(conv, src, prefab_path, report_path, args.trace, known, known_src)
        print_line(src.name, report)
        for w in report["avertissements"]:
            print(f"     ! {w}")
        for name, n in report["non_mappes"].items():
            print(f"     ? non mappé : {name} x{n}")
        for t, n in report["ids_inconnus"].items():
            print(f"     ? ID inconnu : {t} x{n}")
        print(f"  -> {prefab_path}")
        problems = bool(report["non_mappes"] or report["ids_inconnus"])
        return 1 if args.strict and problems else 0

    # ---- Mode dossier ----
    files = sorted(p for p in src.rglob("*") if p.is_file() and p.suffix.lower() == ".blueprint")
    if not files:
        print(f"Aucun .blueprint dans {src}")
        return 2
    out_dir = Path(args.output) if args.output else src.parent / (src.name + "_hytale")
    print(f"{len(files)} blueprint(s) -> {out_dir}")

    all_unmapped: Counter = Counter()
    unmapped_where: dict[str, list[str]] = {}
    all_unknown: Counter = Counter()
    summary = []
    for f in files:
        rel = f.relative_to(src)
        base = out_dir / rel.parent / strip_suffix(f.name)
        try:
            report = convert_one(conv, f, base.with_name(base.name + SUFFIX_PREFAB),
                                 base.with_name(base.name + SUFFIX_REPORT), args.trace, known, known_src)
        except Exception as exc:  # un fichier cassé ne bloque pas le lot
            print(f"  XX {rel}  -> ERREUR : {exc}")
            summary.append({"fichier": str(rel), "erreur": str(exc)})
            problems += 1
            continue
        print_line(str(rel), report)
        for name, n in report["non_mappes"].items():
            all_unmapped[name] += n
            unmapped_where.setdefault(name, []).append(str(rel))
        all_unknown.update(report["ids_inconnus"])
        summary.append({"fichier": str(rel), "blocs": report["blocs_emis"],
                        "non_mappes": sum(report["non_mappes"].values()),
                        "ids_inconnus": len(report["ids_inconnus"]),
                        "ancre": report["ancre"]["methode"]})

    write_json(out_dir / "resume.json", {
        "convertisseur": f"blueprint2hytale {__version__}",
        "fichiers": summary,
        # La liste de travail pour porter un nouveau style : quoi mapper en premier.
        "a_mapper_par_frequence": [
            {"bloc": n, "cases": c, "fichiers": len(unmapped_where[n]), "exemples": unmapped_where[n][:3]}
            for n, c in all_unmapped.most_common()],
        "ids_inconnus": dict(all_unknown.most_common()),
    })
    if all_unmapped:
        print("\nBlocs à mapper en priorité :")
        for name, n in all_unmapped.most_common(15):
            print(f"  {n:>6}  {name}  ({len(unmapped_where[name])} fichier(s))")
    if all_unknown:
        print("\nIDs Hytale inconnus :")
        for t, n in all_unknown.most_common(15):
            print(f"  {n:>6}  {t}")
    print(f"\nRésumé : {out_dir / 'resume.json'}")
    problems += bool(all_unmapped or all_unknown)
    return 1 if (args.strict and problems) else 0
