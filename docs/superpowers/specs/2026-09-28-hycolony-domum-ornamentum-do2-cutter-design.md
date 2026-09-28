# HyColony : établi de l'architecte (Domum Ornamentum DO-2a)

Sous-projet de Domum Ornamentum. DO-1 (spec `2026-09-28-hycolony-domum-ornamentum-do1-design.md`) livre les formes DO et crée leurs variantes de matériaux à l'exécution, mais on ne les obtient que par `/hyornament give`. DO-2a les rend **fabricables par le joueur**, comme l'Architect's Cutter de DO. La fabrication par les artisans de la colonie (DO-2b) viendra après.

Source : `docs/research/domum-ornamentum.md` § A.3 (cutter DO) et § B « 6. Architect's Cutter » ; DO au commit épinglé `82729d6c9dc0499b256b36b4506d0d9ef20e8aec` (`ldtteam/Domum-Ornamentum`).

## Décisions

- **Joueur d'abord.** Les artisans MC (scierie, tailleur de pierre, mécanicien : `AbstractDOCraftingBuildingModule`) sont hors de DO-2a.
- **Notre établi et notre fenêtre.** Les établis vanilla (`DiagramCrafting`, `StructuralCrafting`) demandent une recette par combinaison, qui nomme un objet existant ; les variantes DO-1 sont créées à la demande, donc aucune recette ne peut les nommer d'avance. L'établi DO-2a appelle le moteur de variantes de DO-1 au moment de fabriquer.
- **Exécution déléguée.** Le plan doit se suivre seul (une session cloud l'exécute) : faits vérifiés et code dans le plan, pas de renvoi à une conversation. La session cloud ne lance jamais le serveur Hytale ; elle s'arrête au build vert et aux relectures, l'utilisateur teste en jeu.

## En jeu

- **L'établi** : bloc et objet `HyColony_DO_ArchitectsCutter`, dans le pack DO (`plugin/src/subplugins/DomumOrnamentum`). Recette DO (`DO-gen: recipes/architectscutter.json` : 1 lingot de fer, 3 dalles de pierre, 3 bûches) transposée à l'établi de base Hytale (`Workbench`) : 1 lingot de fer, 3 dalles de pierre, 3 troncs (tout bois). Il se pose tourné vers le joueur (`NESW`), se casse et se ramasse comme un établi vanilla.
- **La fenêtre**, d'après `ArchitectsCutterScreen` de DO, mise en page comme les établis vanilla (révision après le premier essai en jeu : le client n'affiche pas une fenêtre de conteneur à côté d'une page personnalisée) :
  - des **onglets de groupes** à icônes (celle de la première forme du groupe, nom au survol), dans l'ordre du cutter DO (`SortedBlocks` : `avanilla`, `btimberframe`, `cshingle`, `etrapdoor`, `ddoor`, `fpanel`, `hpaperwall`, `gpillar`, `kpost` ; les groupes sans forme en DO-1, `ilight` et `jbrick`, n'apparaissent pas) ;
  - la **grille des formes** du groupe choisi, chacune avec l'icône et le nom de son gabarit, dans l'ordre de l'index DO de chaque forme (`SortedBlocks`) ; les vitres encadrées, sans index DO, sont en fin, dans l'ordre du manifeste ;
  - **2 emplacements de matériaux** (1 pour une forme à un matériau), chacun avec son étiquette (« Cadre », « Centre », « Toit », « Support », « Matériau »…), le matériau choisi et « possédé / requis » ;
  - l'**aperçu** : l'icône de la variante et la quantité produite, ou la raison d'un refus ; boutons Fabriquer, ×10 et Tout ;
  - **« Vos matériaux »** : les objets de l'inventaire du joueur que l'emplacement sélectionné accepte, avec leur quantité. Un clic en met un dans l'emplacement sélectionné, puis sélectionne l'emplacement vide suivant.
- **Fabriquer** : le cœur vérifie les matériaux contre les tags DO (`VariantRequests`, DO-1), la variante est créée si elle n'existe pas (un scintillement au plus pour une nouvelle paire), puis **1** de chaque matériau requis est retiré de l'inventaire du joueur par fabrication (`ArchitectsCutterContainer`, `onTake`, `remove(1)`) et la quantité DO est donnée au joueur : colombages, bardeaux, demi-bardeau 4 ; murs de papier 6 ; porte et trappe ouvragées 2 ; panneaux 4 ; dalle 2 ; le reste 1 (`cutterQuantity` du manifeste, déjà généré). Comme `ArchitectsCutterRecipe.assemble`, la quantité donnée est `max(nombre de matériaux, cutterQuantity)`. Inventaire plein : le reste tombe aux pieds du joueur.
- **Mode créatif** : rien n'est retiré de l'inventaire, comme DO (`ArchitectsCutterContainer`, `onTake`, `!thePlayer.isCreative()`).
- **Dernier onglet** : l'établi rouvre sur le dernier onglet du joueur, sur sa première forme, comme DO (`ArchitectsCutterScreen.groupIndexCache`) ; oublié à la déconnexion (écart, plus bas).
- **Refus sans rien consommer** : un matériau hors tag (message avec l'emplacement et des matériaux acceptés, comme `/hyornament give`), un emplacement requis vide.
- **Pas de conteneur** : l'établi ne garde rien ; les matériaux restent dans l'inventaire du joueur jusqu'à la fabrication. Deux joueurs sur le même établi travaillent chacun avec leur inventaire.

## Écarts avec DO

- **×10 et Tout** : DO fabrique un par un ; ici, comme les établis vanilla, ×10 fabrique 10 fois et Tout autant que les matériaux le permettent (64 au plus en créatif, `CutterCraft.MAX_BATCH`). `Deviation from MC: the cutter window also offers x10 and All`.
- **Aperçu** : l'icône de la variante n'existe qu'une fois la variante créée ; avant, l'aperçu montre l'icône du gabarit, avec la quantité. `Deviation from MC: the cutter previews the template's icon until the variant exists`.
- **Second matériau facultatif** (porte ouvragée) : vide, il reprend le premier, comme DO-1 (`VariantRequests`). DO refuse un emplacement vide dans `matches` (A.3) : ici on accepte, comme le reste de DO-1.
- **Recette de l'établi** : ingrédients transposés (troncs pour bûches), à l'établi `Workbench`.
- **Groupe inconnu** : `SortedBlocks.sortGroups` de DO lève une exception sur un groupe sans index ; ici, un tel groupe va en fin, par id (`CutterOrder`). Aucun groupe du manifeste n'est dans ce cas.
- **Aperçu d'un refus** : un matériau choisi reste choisi quand on change de forme ; s'il n'est pas dans le tag de la nouvelle, l'aperçu explique le refus. Dans DO, `mayPlace` (l.114-124) refuse le bloc à l'entrée et la sortie reste vide.
- **Matériaux pris dans l'inventaire** : DO fait poser les blocs dans 2 emplacements propres au joueur, rendus à la fermeture (`ArchitectsCutterContainer.removed` → `clearContainer`, l.362-366) ; ici on les choisit dans « Vos matériaux » et la fabrication les retire de l'inventaire, comme les établis vanilla. Dans les deux cas, l'établi ne garde rien. `Deviation from MC: materials picked from inventory`.
- **Dernier onglet** : oublié à la déconnexion du joueur ; DO le garde jusqu'à la fermeture du client, que le serveur ne voit pas (`CutterGroupMemory`).

## Architecture

### Cœur (`core/.../ornament/cutter`, Java pur, testé)

- **`CutterCatalog`** : groupes et formes dans l'ordre de `SortedBlocks` de DO (index par groupe et par forme, repris dans `CutterOrder`) ; construit depuis `ShapeCatalog` (DO-1). Un groupe sans forme est absent. Une forme sans index DO va en fin de son groupe, dans l'ordre du manifeste.
- **`CutterCraft`** : `check(OrnamentShape shape, List<SlotContent> slots, MaterialTags tags)` → `Ready(VariantKey key, int quantity, List<Integer> consumed)` ou `Refused(String reasonKey, int slot, Set<String> allowed)`. `SlotContent` = id d'objet (ou vide) et quantité. S'appuie sur `VariantRequests.check` ; un emplacement au-delà du nombre de matériaux de la forme est ignoré et n'est pas consommé.
- **`CutterView`** (record immuable, CLAUDE.md § 7) : onglets (`Tab(group, nameKey, iconKey, selected)`), formes du groupe (`ShapeButton(shapeId, templateKey, selected)` : la page montre l'icône et le nom de l'objet gabarit), emplacements (`Slot(labelKey, itemId, have, need, selected)`), matériaux du joueur acceptés par l'emplacement sélectionné (`Material(itemId, count)`), aperçu (`Empty`, `Ready(itemId, templateKey, quantity, maxCrafts)`, `Refused` avec clé de raison et paramètres).
- **`CutterActions`** : `selectGroup`, `selectShape`, `selectSlot`, `choose(itemId)`, `group()` (l'onglet ouvert), `slots(inventory)`, `view(inventory)` (inventaire = id → quantité) ; changer de groupe choisit sa première forme ; les matériaux choisis restent. `CutterCraft.maxCrafts(ready, inventory)` : le nombre de fabrications possibles (le matériau le plus rare sur ce qu'une fabrication en prend). Le plugin garde le dernier onglet de chaque joueur (`CutterGroupMemory`, en mémoire, oublié à la déconnexion). `CutterCraft.check(..., boolean creative)` ne retire rien pour un joueur en créatif. La fabrication elle-même est orchestrée par le plugin (elle attend le moteur de variantes).

### Plugin (`plugin/.../ornament/cutter`)

- **Bloc** : sans conteneur ; son `Use` est un no-op et `CutterSystem` (`UseBlockEvent.Pre`, après la protection de colonie) ouvre la fenêtre.
- **Ouverture** : `CutterOpener` ouvre `CutterPage` seule (`openCustomPage`).
- **`CutterPage`** : lit l'inventaire du joueur (`CutterInventory.counts`) à chaque affichage et affiche la `CutterView` (`CutterDrawing`, `.ui` copiant les tuiles d'onglets vanilla) ; chaque bouton appelle une action du cœur puis ré-affiche la vue. L'inventaire n'est pas suivi : la vue se met à jour à la prochaine action (la fabrication revérifie tout).
- **Fabriquer**, sans bloquer le thread du monde :
  1. sur le thread du monde, `CutterCraft.check` sur les matériaux choisis et l'inventaire actuel ;
  2. `OrnamentVariantRegistry.request(List.of(key))` (hors du thread du monde, DO-1) ;
  3. au retour, sur le thread du monde, `CutterCraft.check` à nouveau sur l'inventaire actuel : il doit rendre la même clé ; le nombre de fabrications est plafonné par `maxCrafts` ;
  4. retirer de l'inventaire, tout ou rien, 1 de chaque matériau de `consumed` par fabrication (`CutterInventory.take`), donner `quantity` × fabrications objets de la variante.
  À tout échec (refus, changement entre 1 et 3, joueur parti, création en échec ou hors délai) : rien n'est consommé ni donné, message au joueur s'il est là.
- **Textes** : clés `ornament.cutter.*` en en-US et fr-FR (CLAUDE.md § 7).

## Robustesse

- Aucune perte ni duplication : la consommation et le don ont lieu ensemble, sur le thread du monde, après revalidation.
- Une création en cours n'est jamais attendue sur le thread du monde ; elle a un délai (DO-1 : 30 s).
- Pack DO désactivé : l'établi n'existe pas, rien n'est enregistré, rien ne plante.

## Contrôles et tests

- **Cœur (TDD)** : `CutterCatalog` (ordre des groupes, groupe vide absent, groupe inconnu à la fin), `CutterCraft` (quantités 4, 6, 2, 1 ; 1 retiré par emplacement requis ; matériau hors tag refusé avec son emplacement ; emplacement vide refusé ; second facultatif vide repris ; emplacement en trop ignoré), `CutterActions` (changement de groupe, choix et sélection des emplacements, matériaux listés, aperçu, `maxCrafts`).
- **Générateur** : `python tools/domum/check.py` couvre l'objet, le bloc, la recette et l'icône de l'établi.
- **En jeu** (`docs/TESTING.md`, nouvelle section) : fabriquer l'établi, le poser ; fabriquer un colombage, un bardeau, une porte ouvragée avec un seul matériau, une dalle (quantités) ; ×10 et Tout ; refus hors tag et emplacement vide ; jeter un matériau pendant la création d'une nouvelle paire (refus, rien de perdu) ; deux joueurs ; inventaire plein ; pack désactivé.

## Hors DO-2a

- Fabrication par les artisans de la colonie, demande de blocs DO par le constructeur (DO-2b).
- Liste de toutes les variantes connues.
- Tout-brique (DO-1b).
