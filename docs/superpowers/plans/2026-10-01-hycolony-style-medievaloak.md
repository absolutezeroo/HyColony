# Style Medieval Oak : plan

> Exécution en ligne. Une relecture par lot, puis une relecture finale.

**Spec :** `docs/superpowers/specs/2026-10-01-hycolony-style-medievaloak-design.md`

## Contraintes

- CLAUDE.md entier. Le code se modifie par Edit et Write. Les fichiers générés (prefabs, PNG) sont produits par les outils du dépôt (`tools/blueprint`) ou un script de PNG.
- `./gradlew build` vert avant chaque commit ; `git add` explicites.

## Points à surveiller en relecture

- Un `domum-variants.json` mal formé ne doit ni empêcher le démarrage ni faire échouer les autres variantes.
- HyDomum absent ou désactivé : HyColony démarre quand même ; les blocs Domum des plans restent alors inconnus.
- Le métier à tisser ne doit pas être enregistré comme établi de la hutte du cuisinier.
- Une colonie qui ne connaît aucun style démarre quand même (Medieval Oak désactivé dans `config.json`).

## Lot 1 : convertisseur

Les règles et l'adaptateur du § 2, chacun avec sa vérification ; `python -m blueprint` sur les 35 plans donne 0 bloc non mappé.

## Lot 2 : variantes livrées

- `BootVariants` dans HyDomum ; `OrnamentVariantRegistry.start` crée aussi les variantes demandées.
- Le fragment `domum-variants.json` est lu par HyColony et passé à HyDomum.

## Lot 3 : le style et le retrait

- `Styles_MedievalOak` : prefabs, `styles.json`, `packs.json`, `domum-variants.json`, icône, clés de langue.
- Retrait de Kweebec et d'Outlander, et de leurs prefabs du fermier.
- `docs/TESTING.md` : les points des sous-plugins et un point par hutte de Medieval Oak.
