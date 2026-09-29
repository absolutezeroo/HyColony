# HyColony : blocs de dev des plans MineColonies (substitutions Structurize)

Conception validée avec l'utilisateur le 2026-09-29. Recherche, **à lire avant tout** : `docs/research/structurize-placeholders.md` (noté « R § x »). Sources : Structurize `v1.20.1-1.0.818`, MineColonies `version/main` (`6b4e03e3`), Hytale 0.7.0-pre.4.

## Objectif

Les plans MineColonies convertis (`tools/blueprint`) se construisent comme dans MineColonies : l'air du plan est vidé, le terrain des substitutions est gardé, les substitutions pleines sont comblées avec le bloc de remplissage de la hutte du bâtisseur, les substitutions de fluide reçoivent de l'eau, et les fondations sous la hutte sont construites.

## Portée

- **Dedans :**
  - un mode « plan MineColonies » par niveau dans `styles.json`, sans effet sur les prefabs vanilla Hytale ;
  - deux blocs de dev HyColony, `HyColony_Placeholder_Solid` et `HyColony_Placeholder_Fluid` (pack du plugin, onglet créatif, visibles dans l'éditeur de prefabs) ;
  - leur lecture par `HytaleBlueprintSource`, l'air explicite (`Empty`) et les fondations ;
  - dans le cœur : CLEAR limitée aux cases du plan, remplissage, fluide, coût du remplissage ;
  - le réglage « bloc de remplissage » de la hutte du bâtisseur (terre par défaut), son onglet et son sélecteur ;
  - le convertisseur : nouveaux blocs, fluides dans `fluids`, ancre sur `primary_offset`.
- **Hors portée** (lots suivants) : étapes `CLEAR_WATER`, `CLEAR_NON_SOLIDS`, `WEAK_SOLID` ; points de passage (`blockwaypoint`, 24 cases) ; `decorationcontroller` gratuit (46 cases) ; tours de caserne gratuites.

## Règles de jeu (fidèles à MC, R § 1-2)

Le bâtisseur MC pose toujours en mode *fancy*. Chaque case du plan est de l'un de ces types :

| Case du plan | Correspond déjà si le monde a… | Sinon | Coût | Étape |
|---|---|---|---|---|
| **air explicite** | rien (air) | mine le bloc, retire le fluide | 0 | CLEAR |
| **absente** (substitution simple) | toujours | — | 0 | — |
| **remplissage** (substitution pleine) | un bon bloc de sol : cube plein non feuillage, ou terre labourée (`isGoodFloorBlock`) | CLEAR mine le bloc (pas un fluide), SOLID pose le bloc de remplissage | 1 bloc de remplissage par case qui ne correspond pas | CLEAR puis SOLID |
| **fluide** (substitution de fluide) | un fluide, ou un bloc plein | mine d'abord un bloc non plein (plante…), puis pose une source d'eau | 0 (eau) | DECORATE, sautée à CLEAR |
| **bloc** | l'état prévu | mine puis pose | son objet | CLEAR puis SOLID/DECORATE |

- **Bloc de remplissage** : réglage `fillblock` de la hutte du bâtisseur qui porte l'ordre (MC `BuildingModules.BUILDER_SETTINGS`, `BuildingMiner.FILL_BLOCK`, défaut `minecraft:dirt`). Défaut HyColony : la terre de Hytale, lue dans l'id-map (`blueprint.fillBlock`). Le joueur peut choisir n'importe quel bloc plein qui a un objet.
- **Fluide** : celui de la dimension dans MC (`getFluidForDimension`) : l'eau dans l'Overworld. Hytale n'a qu'un monde de surface : **l'eau**, `Water_Source`, lue dans l'id-map (`blueprint.placeholderFluid`).
- **Amélioration** : pour un plan MineColonies, `CLEAR_LEFTOVERS` fait ce que fait MC `CLEAR_NON_SOLIDS` : il ne visite que les cases d'air du nouveau plan (les niveaux MC partagent une emprise). Les cases de remplissage, de fluide et absentes gardent ce qu'elles ont. Les prefabs Hytale gardent l'écart documenté dans `Stage`.
- **Démolition** : REMOVE saute les cases de remplissage et de fluide (MC `AbstractEntityAIStructure.skipRemoval`).

## Écarts

1. **Le mode est par niveau** (`"minecolonies": true`) : les prefabs vanilla Hytale gardent leur sémantique (absent = terrain, couches sous le sol ignorées, CLEAR de toute la boîte). `Deviation from MC:` aucun, MC n'a qu'une sémantique ; c'est un ajout pour les prefabs Hytale.
2. **Pas de lave** : Hytale n'a pas de dimension où le fluide par défaut est la lave.
3. **Bloc « à fluide »** : MC garde un bloc `WATERLOGGED` et le met à `true`. Hytale stocke le fluide à part du bloc et le cœur ne sait pas quels blocs acceptent l'eau : un bloc non plein est miné avant la pose de l'eau (comme MC le fait pour une plante).
4. **N'importe quel fluide** correspond à une case de fluide : le cœur ne distingue pas une source d'un fluide qui coule (MC exige une source).
5. **Bon sol** : Structurize teste la forme de collision (`isGoodFullBlock`) ; Hytale n'en a pas côté serveur, donc `DrawType` `Cube` ou `CubeWithModel` (minerais), matériau `Solid`, hors groupe `Leaves`.
6. **Collage à la baguette** : il remplit avec le bloc de remplissage par défaut, où Structurize (`CreativeStructureHandler`) prend le bloc du générateur du monde.
7. **Bloc de remplissage figé** au chargement de l'ordre (plan et ressources) ; MC relit `getSolidSubstitution` à chaque pose. Un bloc sauvegardé qui n'est plus proposé revient au défaut.
8. **CLEAR retire aussi les fluides** sous l'air et sous les blocs prévus (écart antérieur : HyColony n'a pas d'étape `CLEAR_WATER`).

## Conception

### Prefab et `styles.json`

- Nouveau champ de niveau `"minecolonies": true` (défaut `false`), lu par `PrefabStyles.Level`.
- En mode MC, `HytaleBlueprintSource` :
  - garde **toutes** les couches (pas de `FLOOR_Y`), bornes = boîte entière du prefab ;
  - prend la hutte à l'**ancre du prefab** (`hutOffset` absent = ancre, et non plus le centre de la couche basse) : le convertisseur met la hutte à l'ancre ;
  - lit `Empty` explicite (bloc 0 sans fluide) comme **air explicite** ;
  - lit `HyColony_Placeholder_Solid` / `_Fluid` (ids dans l'id-map) comme cases de remplissage / fluide ;
  - ignore toujours `Editor_*`, qui garde son sens Hytale « case absente ».
- Hors mode MC, rien ne change (un bloc de dev y est ignoré).
- **À vérifier dans les sources** : que `PrefabBufferUtil`/`BsonPrefabBufferDeserializer` garde une entrée `Empty` explicite et que `forEach` la rend avec `blockId == 0`.

### Cœur

- `Blueprint` reçoit `Optional<BlueprintMarkers> markers` : absent = sémantique Hytale (inchangée), présent = sémantique MC. Un constructeur à 4 paramètres garde les appels existants.
- `BlueprintMarkers(List<BlockPos> air, List<BlockPos> fill, List<BlueprintEntry> fluid)`, offsets relatifs à la hutte ; `fluid` porte l'état fluide (`~fluid:Water_Source`).
- `StructurePlan.build(bp, hut, catalog, fillBlock)` :
  - **CLEAR** en mode MC : air explicite, blocs du plan et cases de remplissage, de haut en bas ; ni les cases absentes, ni les cases de fluide ;
  - les cases de remplissage deviennent des entrées SOLID du bloc de remplissage, marquées comme telles (`isFill(pos)`) ;
  - les cases de fluide rejoignent DECORATE.
- `StructureScan.needsWork` :
  - CLEAR d'une case de remplissage : le monde a un bloc minable qui n'est pas un bon sol ;
  - SOLID d'une case de remplissage : le monde n'a pas un bon sol ;
  - DECORATE d'une case de fluide : le monde n'a ni source de fluide ni bloc plein.
- Coût (`NeededResources`/`EntryCost`) : une case de remplissage compte le bloc de remplissage **seulement si elle ne correspond pas** au moment du calcul (MC `getResourceRequirements`) ; une case de fluide ne coûte rien.
- Port : `ItemCatalog.isGoodFloor(BlockKey)` (cube plein, matériau solide, hors feuillage ; terre labourée) ; `BlueprintSource.defaultFillBlock()`.
- `BuilderSettingsModule` : `fillBlock` persisté (clé `"fillBlock"`, lecture tolérante : absent ou inconnu = défaut), exposé dans la vue des réglages. Action `setFillBlock` qui vérifie la permission et refuse un bloc qui n'est pas un bon sol ou qui n'a pas d'objet.

### Plugin

- Assets : deux blocs cubiques translucides (texture propre, style Structurize : cadre et motif), dans l'onglet créatif HyColony, sans objet de recette.
- `HytaleItemCatalog.isGoodFloor` vérifié dans les sources (forme du bloc, matériau, groupe feuillage, terre labourée).
- Onglet Réglages de la hutte du bâtisseur : ligne « Bloc de remplissage » avec son icône et un bouton qui ouvre un sélecteur avec recherche (celui des graines du champ, généralisé), listant les blocs pleins qui ont un objet.

### Convertisseur (`tools/blueprint`)

| Source | Sortie |
|---|---|
| `minecraft:air`, `blocktagsubstitution` sans remplacement | `Empty` |
| `blocksubstitution` | rien |
| `blocksolidsubstitution` | `HyColony_Placeholder_Solid` |
| `blockfluidsubstitution` | `HyColony_Placeholder_Fluid` |
| `minecraft:water` / `lava` | tableau `fluids` (`Water_Source` / `Lava_Source`), pas de bloc |
| hutte à `primary_offset` | `Editor_Anchor` ; l'ancre est `primary_offset`, plus la première hutte trouvée |

## Tests

- Cœur (TDD) : CLEAR ne touche pas une case absente ; mine l'air explicite ; ne mine pas un bon sol sous une case de remplissage ; SOLID remplit une case d'air avec le bloc de remplissage choisi ; une case de remplissage déjà pleine ne coûte rien ; une case de fluide reçoit l'eau et ne coûte rien ; un plan sans marqueurs garde l'ancien CLEAR ; le réglage est persisté et relu (absent = défaut).
- Convertisseur : vérifications Python (`check_*.py`) pour les nouveaux blocs, les fluides et l'ancre des casernes.
- En jeu (`docs/TESTING.md`) : un plan converti avec fondations, substitutions et eau (pêcheur).
