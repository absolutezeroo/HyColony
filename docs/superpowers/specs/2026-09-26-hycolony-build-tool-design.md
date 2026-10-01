# HyColony : baguette de construction (Build Tool)

Conception validée avec l'utilisateur le 2026-09-26 : « on invente rien, on reprend le système de Structurize pour MineColonies ». Recherche : `docs/research/build-goggles-and-wand.md` (parties A.2, B.1, B.3, B.4).

## Objectif

Poser une hutte comme dans MineColonies : choisir un style, une hutte et un niveau, voir le plan en fantôme, le déplacer et le tourner, puis valider. La pose à la main du bloc de hutte reste possible, comme dans MineColonies.

## Portée de cette version

- **Dedans** : les huttes (catégorie « Hut Blocks » de Structurize).
- **Dehors**, noté au backlog :
  - décorations et ordres de décoration ;
  - miroir ;
  - aperçus partagés (`share_previews`) ;
  - huttes voisines affichées pendant le placement (`NearBuildPreview`) ;
  - collage créatif « Complete » (« Pretty » est porté, voir plus bas) ;
  - outils de scan et de formes.

## Règles de jeu (Structurize `ItemBuildTool`, `WindowExtendedBuildTool`, `AbstractBlueprintManipulationWindow` ; MineColonies `SurvivalHandler`)

- **Objet** : « Baguette de construction » (`HyColony_Build_Tool`, `sceptergold` de Structurize), pile de 1.
- **Recette** : à l'établi, la recette du wiki MineColonies transposée :

  | MineColonies | Hytale |
  |---|---|
  | 1 pierre (Cobblestone) | 1 `Rock_Stone_Cobble` |
  | 1 pierre noire (Blackstone) | 1 `Rock_Basalt_Cobble` |
  | 1 ardoise des profondeurs (Cobbled Deepslate) | 1 `Rock_Slate_Cobble` |
  | 6 bâtons (Sticks) | 6 `Ingredient_Stick` |

  La recette des lunettes prend ensuite la baguette comme ingrédient central, comme dans MineColonies (voir la spec des lunettes).
- **Ouvrir** :
  - un clic (principal ou secondaire) sur un bloc ouvre la fenêtre et place l'ancre sur la face visée du bloc (`useOn`) ;
  - un clic dans le vide rouvre la fenêtre en gardant l'ancre courante (`use`). Sans ancre, un message `hycolony.wand.missingPos` s'affiche et la fenêtre ne s'ouvre pas (`structurize.gui.missing.pos`). *Remplacé le 2026-10-01 (`2026-10-01-hycolony-build-tool-structurize-design.md` § 3.1) : comme Structurize, l'ancre n'est posée que s'il n'y en a pas, 10 blocs devant le joueur pour un clic dans le vide.*
- **Choisir** :
  - la fenêtre montre le style (le « pack »), puis la hutte, puis le niveau (1 à 5), comme l'arbre de Structurize ;
  - en survie, seules les huttes dont le joueur a le bloc dans son inventaire sont proposées (`BLOCK_BLUEPRINT_REQUIREMENT`) ; en créatif, toutes le sont ;
  - tant qu'aucune hutte n'est choisie, les boutons de manipulation sont cachés.
- **Manipuler** : boutons de Structurize :
  - avant, arrière, gauche, droite : d'un bloc, **relatifs à la direction du joueur** au moment du clic ;
  - haut, bas : d'un bloc sur Y ;
  - tourner à droite, tourner à gauche : un quart de tour ;
  - valider, annuler.
- **Aperçu** : le fantôme montre le **plan complet** du niveau choisi, tourné. Seul le joueur le voit (`share_previews=false` par défaut dans Structurize). Il est recréé à chaque changement.
- **Échap** ferme la fenêtre mais garde le fantôme. Un nouveau clic dans le vide rouvre la fenêtre pour ajuster. **Annuler** efface le fantôme et la sélection.
- **Valider** (port de `SurvivalHandler`), dans cet ordre, avec le premier refus en message :
  1. Dans une colonie, il faut la permission `MANAGE_HUTS` (`BP_NO_PERM`).
  2. Hôtel de ville hors de toute colonie : assez loin des autres colonies, sinon `hycolony.colony.tooClose` (`TOWNHALL_TOO_CLOSE`). Un hôtel de ville dans une colonie n'a pas cette contrainte.
  3. Toute autre hutte : elle doit être dans une colonie, et toute l'emprise du plan aussi, sinon `hycolony.wand.outsideColony` (`BP_OUTSIDE_COLONY`). Chaque cellule de 16×16 touchée par l'emprise est testée (écart, voir plus bas). Cette étape ne s'applique jamais à un hôtel de ville, même dans sa colonie.
  4. Les règles de `HutActions.checkHutRules` (`EventHandler.onBlockHutPlaced`) : un seul hôtel de ville par colonie ; pour fonder, la sauvegarde disponible, pas déjà propriétaire, la distance au spawn.
  5. Le bloc de hutte doit être dans l'inventaire, sauf en créatif. S'il manque, la validation est refusée.
  6. Le bloc déjà présent à l'ancre est cassé et ses drops tombent au sol à l'ancre (`destroyBlock(pos, true)`), puis le bloc de hutte est posé à l'ancre avec la rotation choisie, et un bloc est retiré de l'inventaire (sauf en créatif). La hutte est enregistrée par le même chemin que la pose à la main (`HutActions.place`), avec le style choisi et le **niveau 0**. Le niveau choisi ne sert qu'à l'aperçu : le wiki le dit, on améliore ensuite la hutte pour monter de niveau.
  7. **Aucun ordre de travail n'est créé** : le joueur lance la construction depuis la fenêtre de la hutte.
  8. Le fantôme et la sélection sont effacés.
- **Session** : la sélection d'un joueur est gardée en mémoire. Elle n'est pas sauvegardée, et elle est vidée à la déconnexion.

## Écarts à MineColonies (à reporter dans la spec SP1+2 § 11)

- **Pas de raccourcis clavier** (flèches, Maj+flèches, M, Entrée) : Hytale n'envoie pas les touches au serveur. Seuls les boutons de la fenêtre existent.
- **Pas de décalage de sol** par tags de plan : nos prefabs Hytale n'en ont pas, l'ancre est la face visée.
- **Recette** : les pierres de MineColonies sont remplacées par leurs équivalents Hytale, voir le tableau plus haut.
- **Emprise dans la colonie** : chaque cellule de 16×16 touchée par l'emprise est testée (`ClaimCell.allOwned`). MineColonies avance de 16 blocs à partir de `min+1` tant que `< max` : il saute les colonnes du bord et peut manquer la dernière cellule (bogue probable de MC). Une hutte qui dépasse de quelques blocs la frontière est refusée ici.
- **Bloc de hutte manquant** : un message `hycolony.wand.missingHut` s'affiche ; MineColonies ne joue qu'un son d'erreur (le cœur n'a pas de port de son).
- **Créatif** : « Valider » prend le chemin de survie, sans rien consommer. Le collage « Pretty » a sa section ; ses écarts sont listés dans la spec SP1+2 § 11.

## Collage créatif « Pretty » (ajouté le 2026-09-27)

Port de Structurize `BlueprintPlacementHandling` et de MineColonies `AbstractBlockHut.setup`/`canPaste` (recherche : `docs/research/build-goggles-and-wand.md`, C.1 à C.4).
- **Bouton** « Coller », visible seulement en créatif avec une hutte choisie. Le serveur revérifie le mode créatif au clic.
- **Vérifications** : `PLACE_HUTS` dans une colonie (`hycolony.permission.placeHuts`), puis les règles de `HutActions.checkHutRules`. Ni emprise, ni distance à l'hôtel de ville pour une autre hutte, ni bloc de hutte pris.
- **Hutte** : le bloc déjà là est cassé (drops au sol), le bloc de hutte est posé, la hutte est enregistrée avec son style, sa rotation et le **niveau choisi**, construite, avec son territoire, ses feux d'artifice et `BuildingLevelChanged` (`UpgradeCompletion.reach`, partagé avec la fin d'un ordre). Ni ordre, ni message, ni journal. La fenêtre et le fantôme restent, comme dans Structurize.
- **Blocs** : une file en mémoire (`PasteQueue`), dont seule la tête avance, d'au plus `Structurize.MaxOperationsPerTick` (1000) changements par tick. Elle casse d'abord ce que le plan laisse vide (sans drops), puis pose les solides, puis les décorations et fluides, de bas en haut. Un bloc déjà correct est laissé ; un bloc refusé (section non chargée) est sauté. Chaque bloc à conteneur posé devient un conteneur du bâtiment.
- **Hôtel de ville hors colonie** : les blocs sont collés et la fondation s'ouvre ; la colonie créée a son hôtel de ville au niveau collé, construit.

## Architecture

- **Cœur**, paquet `app/wand` (15 fichiers au plus). Seul `WandActions` est public.
  - `WandSession` (record) : style, type de hutte, niveau, ancre, rotation.
  - `WandSessions` : cycle de vie des sessions par joueur (créer, lire, effacer, vider à la déconnexion).
  - `WandActions` : une méthode par bouton. Chacune met la session à jour, rafraîchit l'aperçu et ré-affiche la vue ; elle renvoie `false` sur une entrée invalide.
  - `WandMoves` : déplacement relatif à la direction du joueur (4 directions cardinales tirées de son lacet) et rotation.
  - `WandPlacement` : le port de `SurvivalHandler`, qui fait les vérifications de validation et renvoie un refus (`Msg`) ou un succès.
  - `WandView` (record) : styles, huttes proposées, niveaux, sélection, manipulation visible ou non.
  - L'aperçu passe par `PreviewPort` (celui des lunettes), sous l'identifiant `wand`. Le plan vient de `StructurePlan` / `BlueprintSource`, déjà tourné.
  - L'inventaire du joueur passe par un port existant, ou par un ajout à un port existant : « a-t-il cet objet ? » et « retirer un objet ».
- **Plugin** :
  - l'objet `HyColony_Build_Tool`, avec `OpenCustomUI` sur `Primary` et `Secondary` (`registerCustomPageSupplier`, `context.getTargetBlock()` qui peut être nul) ;
  - `WandPage` et `WandPage.ui`, disposés comme `windowbuildtool.xml` et `layoutmanipulation.xml` de Structurize : un petit panneau sur le côté droit, **sans fond assombri** (`lightbox="false"`, racine `$C.@Container` comme `EntitySpawnPage.ui`, pas `$C.@PageOverlay`), les styles et le fil « style / hutte » en haut, les niveaux à gauche, les huttes au milieu, la croix 3×3 à droite (flèches vanilla `InputIconKey*_White`, « ±90° » pour tourner, « +/- » pour Y, case du miroir vide) avec l'indicateur de rotation, Annuler et Valider en bas. Pas de bouton Réglages : aucun réglage n'est porté ;
  - l'objet, la recette, `id-map.json`, et les traductions en-US et fr-FR.
- **Tests du cœur (TDD)** :
  - les déplacements selon chaque direction du joueur ;
  - la rotation dans les deux sens ;
  - l'ancre gardée par un clic dans le vide, et le refus sans ancre ;
  - la liste des huttes en survie (seulement celles dans l'inventaire) et en créatif (toutes) ;
  - chaque refus de validation, dans l'ordre ;
  - le bloc consommé en survie, mais pas en créatif ;
  - la hutte posée au niveau 0 avec style et rotation ;
  - aucun ordre de travail ;
  - la session vidée à la déconnexion et après validation ;
  - un aperçu affiché puis effacé par Annuler.

## À vérifier en jeu (`docs/TESTING.md`)

1. Fabriquer la baguette à l'établi.
2. Clic sur un bloc : la fenêtre s'ouvre. Choisir un style, une hutte et un niveau : le fantôme apparaît.
3. Les flèches, haut/bas et la rotation déplacent le fantôme sans clignotement gênant (point **[in-game]** : l'aperçu est recréé à chaque clic).
4. Échap garde le fantôme. Un clic dans le vide rouvre la fenêtre.
5. Valider : la hutte est posée, le bloc quitte l'inventaire en survie et aucun ordre n'est créé. Lancer « Construire » depuis la hutte bâtit le plan à l'endroit du fantôme.
6. Refus : hors de la colonie, sans le permis, sans le bloc de hutte.
7. Un second joueur ne voit pas le fantôme.
