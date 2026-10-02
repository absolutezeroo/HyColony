# Bordure de la colonie avec la baguette

Date : 2026-10-02. Demandé par l'utilisateur : « Pas moyen d'ajouter un truc sur la map pour savoir notre territoire », puis « on fait comme MC donc […] la carte pour le moment on s'en fout ».

Port de MineColonies : `ColonyBorderRenderer` (appelé par `WorldEventContext.renderWorldLastEvent`), réglage client `colonyteamborders`.

## 1. Ce que fait MineColonies

- Tant que le joueur tient la baguette de construction en main principale et qu'une colonie est « la plus proche » (`getClosestColonyView` : celle qui possède le chunk du joueur, sinon celle dont le centre est le plus proche en 2D), MC dessine en lignes les bords des chunks revendiqués autour de lui.
- Fenêtre : un chunk n'est dessiné que s'il est strictement à moins de `max(distance de rendu − 3, 2)` chunks du chunk du joueur, sur chaque axe (`RENDER_DIST_THRESHOLD` 3). Chaque colonie présente y est dessinée, pas seulement la plus proche.
- Pour un chunk revendiqué, un côté est un bord quand le voisin de ce côté n'appartient pas à la même colonie :
  - un poteau vertical (du bas jusqu'à `CHUNK_HEIGHT` 256) à chaque coin touché par un bord ;
  - le long d'un bord, une ligne horizontale tous les 16 blocs (`CHUNK_SIZE`) ;
  - dans la colonne (nord et sud) et la rangée (ouest et est) de chunks du joueur, pour la colonie la plus proche : des poteaux tous les 4 blocs (`PLAYER_CHUNK_STEP`) et une horizontale tous les 4 blocs.
- Couleur : avec `colonyteamborders` (vrai par défaut), la couleur d'équipe de la colonie, blanche par défaut ; sinon blanc pour la colonie la plus proche et rouge (255, 70, 70) pour les autres.
- Le dessin est refait quand la colonie la plus proche ou le chunk du joueur change.
- En tenant Ctrl, MC montre à la place les chunks gardés chargés par la colonie (tickets).

## 2. Cœur

- `app/wand/ColonyBorder` :
  - `nearest(manager, pos)` : la colonie de MC `getClosestColonyView` ;
  - `lines(manager, colonyId, cellule du joueur, distance de vue en cellules)` : les segments de MC, avec leur couleur (`WHITE`, `RED`). Un segment identique produit par deux cellules voisines n'est gardé qu'une fois : même dessin, deux fois moins de paquets.
- `TerritoryIndex.colonyAt(ClaimCell)`.
- `ColonyConfig.Client.colonyTeamBorders` (clé `Client.ColonyTeamBorders`, vrai par défaut).

## 3. Plugin

- `ui/wand/ColonyBorderSystem` (un `TickingSystem` par monde, tous les quarts de seconde) : pour chaque joueur qui tient la baguette (`InventoryComponent.getItemInHand`), la colonie la plus proche et la cellule du joueur ; à un changement, il efface les formes du joueur (`ClearDebugShapes`) et envoie les segments ; sinon il les renouvelle avant qu'elles expirent. Quand le joueur range la baguette, il les efface.
- Un segment est un cylindre fin `DisplayDebug` envoyé au seul joueur, comme HyLens (`ShapePackets`).
- Distance de vue : `Player.getViewRadius()` en tronçons Hytale de 32 blocs, soit deux cellules de 16 chacun.

## 4. Écarts

- Hauteur des poteaux : 320 (`ChunkUtil.HEIGHT`, le monde de Hytale) au lieu de 256 (celui de Minecraft).
- Couleur d'équipe : HyColony n'a pas encore de couleur de colonie, donc avec `colonyteamborders` toutes les bordures sont blanches, la couleur par défaut de MC.
- Pas de vue des tickets avec Ctrl : HyColony ne garde aucun chunk chargé, et le serveur ne voit pas la touche Ctrl.
- Les formes de Hytale sont cachées par les blocs (`plugin-b-api.md` § 29), comme les lignes de MC.
- `ClearDebugShapes` efface aussi les formes de HyLens du joueur ; HyLens les redessine à son rafraîchissement suivant (une demi-seconde).
- Le réglage `colonyteamborders` est un réglage serveur (un plugin Hytale n'a pas de configuration côté client), comme `buildgogglerange`.

## 5. Tests

- Cœur : une cellule seule (quatre poteaux, une horizontale tous les 16 blocs), pas de ligne entre deux cellules de la même colonie, le motif serré dans la colonne et la rangée du joueur, la fenêtre de dessin, les couleurs selon le réglage, un segment partagé gardé une fois, la colonie la plus proche.
- En jeu : nouveaux points de `docs/TESTING.md`.
