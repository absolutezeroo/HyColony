# Ruban de chantier

Date : 2026-10-02. Demandé par l'utilisateur : « Il manque aussi les tape qui sont poser à la construction qui permet de délimiter les bâtiments et retirer à la fin d'une construction », puis « Les ruban de chantier fait » (conception validée dans la conversation).

Port de MineColonies : `ConstructionTapeHelper`, `BlockConstructionTape`, le réglage `BuildingTownHall.CONSTRUCTION_TAPE`.

## 1. But

Un chantier est entouré d'un ruban posé sur le sol, un bloc au-delà de l'emprise du plan, comme dans MineColonies. Le ruban apparaît quand le chantier commence et disparaît quand il finit.

Succès :
- un ordre de construction, d'amélioration, de réparation ou de retrait entoure le bâtiment de ruban ;
- le ruban suit le sol, se raccorde en coin aux angles, et ne remplace ni un bloc plein ni un objet posé ;
- il disparaît à la fin ou à l'annulation de l'ordre, à la fin d'une amélioration et quand le bâtiment est retiré ;
- l'hôtel de ville a le réglage « Ruban de chantier » (activé par défaut), qui empêche toute pose quand il est coupé.

## 2. Ce que fait MineColonies

**Pose** (`ConstructionTapeHelper.placeConstructionTape(corners, colony)`) :
- rien si le réglage de colonie `tape` est coupé ;
- les coins de l'emprise sont élargis d'un bloc en X et en Z ; la hauteur de départ est le Y le plus haut des deux coins ;
- un ruban par case des bords nord et sud (pour chaque X), et des bords ouest et est (pour chaque Z), plus le coin sud-est ;
- chaque ruban va sur `firstValidPosition` : en descendant depuis la hauteur de départ, au plus `hauteur de l'emprise + 5` cases, la première case dont le bloc est plein et dont la case du dessus n'est pas pleine et est remplaçable ou vide ; le ruban va sur cette case du dessus. Sans case valide, rien ;
- le ruban d'un coin a `CORNER` ; sa forme vient des rubans voisins (`getPlacementState`).

**Retrait** (`removeConstructionTape(corners, world)`) : sur chaque case du contour élargi, de `Y min − 5` à `Y max + 1`, le premier bloc de ruban trouvé est retiré.

**Quand** :
- pose à la création d'un ordre de bâtiment (`WorkOrderBuilding.onAdded`, pas au rechargement) et à la pose d'une hutte par la baguette en survie (`SurvivalHandler`) ;
- retrait au retrait de l'ordre, quelle qu'en soit la raison (`onRemoved`), à la fin d'une amélioration (`AbstractBuilding.onUpgradeComplete`, avant le nouveau calcul des coins), à la destruction du bâtiment (`onDestroyed`) et à la fin d'un collage créatif (`CreativeBuildingStructureHandler.onCompletion`).
- Les coins sont ceux du bâtiment (`getCorners`), donc du plan de son niveau actuel (niveau 1 pour une hutte non construite).

**Bloc** (`BlockConstructionTape`) : sans collision, cassé d'un coup, ne lâche rien, remplaçable. Raccords N/E/S/O avec les rubans voisins : seul, il est droit (ou en coin s'il a `CORNER`) ; un raccord devient droit ; trois raccords font un T, dont la tige saute si le voisin de la tige est lui aussi un T tourné vers lui. Modèle multipart : un nœud au centre toujours, un piquet au centre quand les deux raccords sont en coin, et pour chaque raccord un piquet au bord avec une corde qui pend jusqu'au centre. Textures : planches de chêne et laine blanche.

## 3. Cœur (`construction/tape/`, Java pur, TDD)

- `TapeLayout` : à partir de deux coins, la liste des rubans à poser, chacun avec sa colonne, sa forme (`STRAIGHT`, `CORNER`) et sa rotation, dans l'ordre de MC. La recherche du sol (`firstValidPosition`) lit le monde par `WorldBlocks` et `ItemCatalog` : plein = `SOLID` ou `UNBREAKABLE`, remplaçable = vide, ou un bloc que le catalogue dit remplaçable.
- `ConstructionTape` : `place(colony, building)` et `remove(colony, building)`, d'après `HutFootprint.of`. La pose respecte le réglage. Le retrait n'enlève que des blocs de ruban.
- Un port `TapeBlocks` (`kernel/port`) donne l'état de bloc d'un ruban pour une forme et une rotation, et dit si un bloc est un ruban. Le plugin l'implémente avec les ids de l'id-map.
- Les rubans d'un même chantier se raccordent par construction (droits le long des bords, coins aux angles). Les formes `T_JUNCTION` et `CROSS_JUNCTION` existent pour les rubans de deux chantiers voisins, que le cœur calcule à la pose d'après les rubans déjà là (MC `getConnections`, sans la règle de la tige).
- Branchements : `WorkManager.create` (pose), `WorkManager.cancel` et `complete` (retrait), `BuildCompletion` à la fin d'une amélioration (retrait autour des anciens coins), le retrait d'un bâtiment (`ColonyBuildingListener`), la pose d'une hutte par la baguette en survie (`WandPlacement`) et la fin d'un collage (`PasteQueue`, retrait).
- Réglage `ColonySettings.Toggle.CONSTRUCTION_TAPE`, vrai par défaut, montré par `TownHallView.Settings` après les trois réglages existants (MC le met en dernier ; HyColony n'a pas `entermessages`). Sauvegardé dans `settings.constructionTape` ; schéma 9 par `MigrationChain`, qui écrit `true` dans une sauvegarde de schéma 8.

## 4. Plugin

- Bloc `HyColony_Construction_Tape` : forme par défaut `Straight`, états `Corner`, `T_Junction`, `Cross_Junction`, rotation `NESW`. Sans collision, cassé d'un coup, sans butin. Un gabarit de raccord (`CustomTemplate`, comme les vitres de HyDomum) recalcule la forme quand un joueur casse ou pose un ruban voisin.
- Modèles générés par un script (`tools/tape/`) depuis la géométrie des trois modèles de MC (×2, en unités Hytale). La texture est un atlas fait des planches de bois dur et de la laine blanche de Hytale, comme les atlas de HyVanilla.
- `HytaleTapeBlocks` implémente le port ; les ids sont dans `hycolony/id-map.json`.
- Textes (en-US, fr-FR) : nom et description du bloc ; la ligne du réglage et son infobulle, d'après MC (« Construction tape: », « Controls construction tape being placed on building sites »).

## 5. Écarts

- Les formes sont calculées par le cœur à la pose, puis par le gabarit de raccord de Hytale quand un joueur change un voisin. La règle de MC qui coupe la tige d'un T face à un autre T n'est pas reprise.
- « Remplaçable » est ce que le catalogue de HyColony en sait (à vérifier dans les assets à l'implémentation), au lieu de `canBeReplaced` de Minecraft.
- Les textures sont celles de Hytale (bois dur, laine blanche), pas celles de Minecraft.

## 6. Tests

- Cœur : contour et formes d'une emprise (coins, côtés, ordre de MC) ; ruban posé sur le premier sol en descendant ; colonne sans sol valide sautée ; retrait qui n'enlève que du ruban, dans la plage de hauteurs de MC ; réglage coupé, rien de posé ; pose à la création d'un ordre, pas au rechargement ; retrait à la fin, à l'annulation, à la fin d'une amélioration, au retrait du bâtiment ; migration de schéma 8 en 9 avec sa fixture.
- En jeu : nouveaux points de `docs/TESTING.md`.
