# HyColony : établi de l'architecte (Domum Ornamentum DO-2a)

Sous-projet de Domum Ornamentum. DO-1 (spec `2026-09-28-hycolony-domum-ornamentum-do1-design.md`) livre les formes DO et crée leurs variantes de matériaux à l'exécution, mais on ne les obtient que par `/hyornament give`. DO-2a les rend **fabricables par le joueur**, comme l'Architect's Cutter de DO. La fabrication par les artisans de la colonie (DO-2b) viendra après.

Source : `docs/research/domum-ornamentum.md` § A.3 (cutter DO) et § B « 6. Architect's Cutter » ; DO au commit épinglé `82729d6c9dc0499b256b36b4506d0d9ef20e8aec` (`ldtteam/Domum-Ornamentum`).

## Décisions

- **Joueur d'abord.** Les artisans MC (scierie, tailleur de pierre, mécanicien : `AbstractDOCraftingBuildingModule`) sont hors de DO-2a.
- **Notre établi et notre fenêtre.** Les établis vanilla (`DiagramCrafting`, `StructuralCrafting`) demandent une recette par combinaison, qui nomme un objet existant ; les variantes DO-1 sont créées à la demande, donc aucune recette ne peut les nommer d'avance. L'établi DO-2a appelle le moteur de variantes de DO-1 au moment de fabriquer.
- **Exécution déléguée.** Le plan doit se suivre seul (une session cloud l'exécute) : faits vérifiés et code dans le plan, pas de renvoi à une conversation. La session cloud ne lance jamais le serveur Hytale ; elle s'arrête au build vert et aux relectures, l'utilisateur teste en jeu.

## En jeu

- **L'établi** : bloc et objet `HyColony_DO_ArchitectsCutter`, dans le pack DO (`plugin/src/subplugins/DomumOrnamentum`). Recette DO (`DO-gen: recipes/architectscutter.json` : 1 lingot de fer, 3 dalles de pierre, 3 bûches) transposée à l'établi de base Hytale (`Workbench`) : 1 lingot de fer, 3 dalles de pierre, 3 troncs (tout bois). Il se pose tourné vers le joueur (`NESW`), se casse et se ramasse comme un établi vanilla.
- **La fenêtre**, d'après `ArchitectsCutterScreen` de DO :
  - des **onglets de groupes**, dans l'ordre du cutter DO (`SortedBlocks` : `avanilla`, `btimberframe`, `cshingle`, `etrapdoor`, `ddoor`, `fpanel`, `hpaperwall`, `gpillar`, `kpost` ; les groupes sans forme en DO-1, `ilight` et `jbrick`, n'apparaissent pas) ;
  - la **grille des formes** du groupe choisi, chacune avec l'icône et le nom de son gabarit, dans l'ordre de l'index DO de chaque forme (`SortedBlocks`) ; les vitres encadrées, sans index DO, sont en fin, dans l'ordre du manifeste ;
  - **2 emplacements de matériaux** (1 pour une forme à un matériau), chacun avec son étiquette (« Cadre », « Centre », « Toit », « Support », « Matériau »…) ;
  - l'**aperçu** : l'icône de la variante et la quantité produite, ou la raison d'un refus.
- **Fabriquer** : le cœur vérifie les matériaux contre les tags DO (`VariantRequests`, DO-1), la variante est créée si elle n'existe pas (un scintillement au plus pour une nouvelle paire), puis **1** de chaque matériau requis est retiré (`ArchitectsCutterContainer`, `onTake`, `remove(1)`) et la quantité DO est donnée au joueur : colombages, bardeaux, demi-bardeau 4 ; murs de papier 6 ; porte et trappe ouvragées 2 ; panneaux 4 ; dalle 2 ; le reste 1 (`cutterQuantity` du manifeste, déjà généré). Comme `ArchitectsCutterRecipe.assemble`, la quantité donnée est `max(nombre de matériaux, cutterQuantity)`. Inventaire plein : le reste tombe aux pieds du joueur.
- **Mode créatif** : rien n'est retiré des emplacements, comme DO (`ArchitectsCutterContainer`, `onTake`, `!thePlayer.isCreative()`).
- **Dernier onglet** : l'établi rouvre sur le dernier onglet du joueur, sur sa première forme, comme DO (`ArchitectsCutterScreen.groupIndexCache`) ; oublié à la déconnexion (écart, plus bas).
- **Refus sans rien consommer** : un matériau hors tag (message avec l'emplacement et des matériaux acceptés, comme `/hyornament give`), un emplacement requis vide.
- **Emplacements du bloc** : les matériaux posés restent dans l'établi quand on ferme la fenêtre, gardés au rechargement du monde, et tombent au sol quand on casse l'établi, comme un établi vanilla. Deux joueurs sur le même établi partagent ses emplacements.

## Écarts avec DO

- **Un clic fabrique une fois**, comme DO. Pas de « tout fabriquer ».
- **Aperçu** : l'icône de la variante n'existe qu'une fois la variante créée ; avant, l'aperçu montre l'icône du gabarit, avec la quantité. `Deviation from MC: the cutter previews the template's icon until the variant exists`.
- **Second matériau facultatif** (porte ouvragée) : vide, il reprend le premier, comme DO-1 (`VariantRequests`). DO refuse un emplacement vide dans `matches` (A.3) : ici on accepte, comme le reste de DO-1.
- **Recette de l'établi** : ingrédients transposés (troncs pour bûches), à l'établi `Workbench`.
- **Groupe inconnu** : `SortedBlocks.sortGroups` de DO lève une exception sur un groupe sans index ; ici, un tel groupe va en fin, par id (`CutterOrder`). Aucun groupe du manifeste n'est dans ce cas.
- **Aperçu d'un refus** : un matériau hors tag entre dans l'emplacement et l'aperçu explique le refus ; dans DO, `mayPlace` (l.114-124) le refuse à l'entrée et la sortie reste vide. `Deviation from MC: out-of-tag materials enter the slot and the preview explains the refusal`.
- **Emplacements du bloc** : les matériaux restent dans l'établi, persistent et sont partagés entre joueurs ; l'établi DO n'a pas d'entité de bloc, ses emplacements sont propres à chaque joueur et rendus à la fermeture (`ArchitectsCutterContainer.removed` → `clearContainer`, l.362-366). `Deviation from MC: the cutter keeps its materials in the block`.
- **Dernier onglet** : oublié à la déconnexion du joueur ; DO le garde jusqu'à la fermeture du client, que le serveur ne voit pas (`CutterGroupMemory`).

## Architecture

### Cœur (`core/.../ornament/cutter`, Java pur, testé)

- **`CutterCatalog`** : groupes et formes dans l'ordre de `SortedBlocks` de DO (index par groupe et par forme, repris dans `CutterOrder`) ; construit depuis `ShapeCatalog` (DO-1). Un groupe sans forme est absent. Une forme sans index DO va en fin de son groupe, dans l'ordre du manifeste.
- **`CutterCraft`** : `check(OrnamentShape shape, List<SlotContent> slots, MaterialTags tags)` → `Ready(VariantKey key, int quantity, List<Integer> consumed)` ou `Refused(String reasonKey, int slot, Set<String> allowed)`. `SlotContent` = id d'objet (ou vide) et quantité. S'appuie sur `VariantRequests.check` ; un emplacement au-delà du nombre de matériaux de la forme est ignoré et n'est pas consommé.
- **`CutterView`** (record immuable, CLAUDE.md § 7) : onglets (clé de nom, sélectionné), formes du groupe (id, clé de nom, chemin d'icône du gabarit, sélectionnée), étiquettes des emplacements, aperçu (`Empty`, `Ready` avec quantité, `Refused` avec clé de raison et paramètres).
- **`CutterActions`** : `selectGroup`, `selectShape`, `view(slots)` ; changer de groupe choisit sa première forme. La fabrication elle-même est orchestrée par le plugin (elle attend le moteur de variantes).

### Plugin (`plugin/.../ornament/cutter`)

- **Bloc** : conteneur de 2 emplacements attaché au bloc, persistant et lâché à la casse ; mécanisme vanilla à vérifier dans les sources décompilées (Tâche 1) et à noter dans `docs/research/plugin-b-api.md`.
- **Ouverture** : l'interaction `Use` du bloc ouvre `CutterPage` et une `ContainerWindow` sur les emplacements du bloc (motif de la fenêtre d'inventaire du citoyen, `docs/research/citizen-inventory-window.md`).
- **`CutterPage`** (`.ui` copiant les motifs vanilla des onglets et grilles d'icônes) : affiche la `CutterView`, chaque bouton appelle une action du cœur puis ré-affiche la vue ; un changement des emplacements ré-affiche l'aperçu.
- **Fabriquer**, sans bloquer le thread du monde :
  1. sur le thread du monde, `CutterCraft.check` sur les emplacements actuels ;
  2. `OrnamentVariantRegistry.request(List.of(key))` (hors du thread du monde, DO-1) ;
  3. au retour, sur le thread du monde, `CutterCraft.check` à nouveau sur les emplacements actuels : il doit rendre la même clé ;
  4. retirer 1 de chaque emplacement de `consumed`, donner `quantity` objets de la variante.
  À tout échec (refus, changement entre 1 et 3, joueur parti, création en échec ou hors délai) : rien n'est consommé ni donné, message au joueur s'il est là.
- **Textes** : clés `ornament.cutter.*` en en-US et fr-FR (CLAUDE.md § 7).

## Robustesse

- Aucune perte ni duplication : la consommation et le don ont lieu ensemble, sur le thread du monde, après revalidation.
- Une création en cours n'est jamais attendue sur le thread du monde ; elle a un délai (DO-1 : 30 s).
- Pack DO désactivé : l'établi n'existe pas, rien n'est enregistré, rien ne plante.

## Contrôles et tests

- **Cœur (TDD)** : `CutterCatalog` (ordre des groupes, groupe vide absent, groupe inconnu à la fin), `CutterCraft` (quantités 4, 6, 2, 1 ; 1 retiré par emplacement requis ; matériau hors tag refusé avec son emplacement ; emplacement vide refusé ; second facultatif vide repris ; emplacement en trop ignoré), `CutterActions` (changement de groupe, aperçu).
- **Générateur** : `python tools/domum/check.py` couvre l'objet, le bloc, la recette et l'icône de l'établi.
- **En jeu** (`docs/TESTING.md`, nouvelle section) : fabriquer l'établi, le poser ; fabriquer un colombage, un bardeau, une porte ouvragée avec un seul matériau, une dalle (quantités) ; refus hors tag et emplacement vide ; changer un matériau pendant la création d'une nouvelle paire (refus, rien de perdu) ; deux joueurs ; casser l'établi plein ; inventaire plein ; redémarrage avec des matériaux dans l'établi ; pack désactivé.

## Hors DO-2a

- Fabrication par les artisans de la colonie, demande de blocs DO par le constructeur (DO-2b).
- « Tout fabriquer », liste de toutes les variantes connues.
- Tout-brique (DO-1b).
