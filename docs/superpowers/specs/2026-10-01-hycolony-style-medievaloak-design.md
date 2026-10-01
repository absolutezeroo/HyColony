# Style Medieval Oak, à la place des styles vanilla

Date : 2026-10-01. Demandé par l'utilisateur : « finir medieval-oak entièrement et l'ajouter », puis « supprimer ceux vanilla non adaptés ». Choix validés dans la conversation :
- on convertit et livre seulement les plans des huttes dont le métier existe ;
- les blocs manquants reçoivent les équivalents proposés (tableau du § 2) ;
- aucune sauvegarde n'utilise plus Kweebec ni Outlander, rien n'est donc à migrer.

## 1. But

HyColony livre un seul style, **Medieval Oak**, converti depuis le pack de MineColonies (`sources/minecolonies/src/main/resources/blueprints/minecolonies/medievaloak/`) par `tools/blueprint`, avec :
- tous ses blocs traduits (aucun « non mappé ») ;
- les vrais matériaux de ses blocs Domum Ornamentum.

Les styles Kweebec et Outlander, faits de prefabs vanilla de Hytale non adaptés aux huttes de MC, disparaissent.

Succès :
- `python -m blueprint` convertit les 35 plans livrés sans bloc non mappé ni id inconnu ;
- en jeu, la baguette ne propose que Medieval Oak, avec son icône, son nom et sa description ;
- chaque niveau de chaque hutte se construit, et les blocs Domum ont leurs matériaux.

## 2. Convertisseur (`tools/blueprint`)

Les 35 plans livrés sont les niveaux 1 à 5 de 7 huttes :
- `fundamentals/builder`, `cook`, `residence`, `townhall` ;
- `craftsmanship/storage/warehouse`, `deliveryman` ;
- `agriculture/horticulture/farmer`.

Ils ont 7 blocs sans règle :

| Bloc MC | Règle |
|---|---|
| `domum_ornamentum:blockpaperwall` (et `blocktiledpaperwall`) | Adaptateur de vitre : la forme HyDomum `PaperWall` (poteau, bout, droit, angle, T, croisement) d'après les connexions `north/east/south/west`, comme les clôtures (`domum._connected`) |
| `minecraft:nether_brick_slab` | Règle des dalles, sur `Rock_Runic_Dark_Brick_Half` (comme les escaliers de briques du Nether, déjà en `Rock_Runic_Dark_Brick_Stairs`) |
| `minecraft:wall_torch` sur un escalier | Le dos plein d'un escalier compte comme support d'une torche murale |
| `minecraft:rose_bush` | `Plant_Flower_Tall_Red` en bas, rien en haut |
| `minecraft:loom` | La caisse 1 × 1 des autres établis de MC (`Furniture_Village_Crate`) : `Bench_Loom` est un établi de fabrication de deux cases, qui deviendrait l'établi de la hutte et déborderait sur sa voisine |
| `minecolonies:blockstash` | Petit coffre, comme `minecraft:chest` (la Réserve n'est pas un bâtiment de HyColony) |
| `minecraft:end_rod` | `Furniture_Crude_Candle` |

Chaque règle a sa vérification dans `tools/blueprint/check_*.py`. Les 120 autres blocs du pack attendent le portage de leurs huttes.

## 3. Variantes Domum livrées (HyDomum)

Les 35 plans utilisent 42 variantes HyDomum (`forme|matériau1|matériau2`). Aujourd'hui, une variante n'existe qu'après sa création par le découpeur ou par `universe/hydomum/variants.json`. Une variante absente se charge en bloc « Unknown ».

- **HyDomum** (`dev.hydomum.plugin.api`) gagne `BootVariants.require(Collection<String> ids)`.
  - Un autre mod l'appelle pendant son `setup` ; HyDomum crée ces variantes au démarrage, avec ses variantes sauvegardées (`OrnamentVariantRegistry.start`).
  - Un id mal formé, ou une forme ou un matériau inconnu, est journalisé et ignoré. HyDomum ne connaît pas HyColony.
- **HyColony** lit le fragment `hycolony/domum-variants.json` (`{"variants": [...]}`) de chaque sous-plugin actif et le passe à `BootVariants.require` à son `setup`.
- **Convertisseur** : l'option `--variantes-hydomum` écrit déjà ces ids. Le fragment du sous-plugin est produit par le même chemin.

## 4. Sous-plugin `Styles_MedievalOak`

- `subplugin.json` (activé par défaut).
- `Server/Prefabs/MedievalOak/<dossier>/<plan>.prefab.json` : les 35 plans.
- `hycolony/styles.json` : `medievaloak` → hutte → niveau → `{"prefab": "...", "minecolonies": true}`.
- `hycolony/packs.json` :
  - `name` et `desc` en clés de traduction ; leurs textes viennent du `pack.json` de MC (« Medieval Oak », sa description) ;
  - `authors` du `pack.json` ;
  - `owner` « minecolonies » ;
  - `icon` « pack_medievaloak ».
- `hycolony/domum-variants.json` : les 42 variantes.
- `Common/UI/Custom/Pages/HyColony/Structurize/pack_medievaloak@2x.png` : `medievaloak.png` (400 × 400) ramenée à 320 × 320.

## 5. Retrait des styles vanilla

On supprime :
- `plugin/src/subplugins/Styles_Kweebec/` et `Styles_Outlander/` ;
- `plugin/src/main/resources/Server/Prefabs/HyColony/Farmer/` (leurs plans du fermier) ;
- les clés `pack.kweebec.*` et `pack.outlander.*`.

`docs/TESTING.md` est mis à jour : les points des sous-plugins et des styles Hytale passent à Medieval Oak. Les docs de recherche gardent leur historique.

## 6. Écarts

- Seules les huttes dont le métier existe sont converties et livrées.
- Les blocs du § 2 sans équivalent exact (dalle de briques du Nether, rosier, métier à tisser, réserve, tige de l'End) prennent l'équivalent le plus proche de Hytale.
