# Citoyens dans la colonie, monstres dehors

Date : 2026-10-02. Demandé par l'utilisateur : « il faudrait éviter que les citoyens sortent de la bordure et que les monstres spawn, car sur Hytale il n'y a pas de système de luminosité ». Choix de l'utilisateur : « MC + borner la flânerie » et « tout le territoire » pour les monstres.

Recherche : `docs/research/colony-bounds-and-mob-spawns.md` (MC ne garde pas ses citoyens dans le territoire et ne refuse l'apparition des monstres que dans les bâtiments ; Hytale n'a pas de condition de lumière sur les monstres de surface).

## 1. Ce que fait MineColonies

- `EntityAICitizenWander.decide` (toutes les 100 ticks, au repos) : 5 fois sur 100 (`LEISURE_CHANCE`), un site de loisir (`getRandomLeisureSite`), sinon la maison du citoyen, sinon le centre de la colonie, et l'état `GO_TO_LEISURE_SITE` ; `setCurrentDelay(60 * 20)` retarde la seule décision de flânerie, dont le compte à rebours ne court qu'au repos : la prochaine décision vient donc 60 s de repos après la fin du loisir. Sinon, une marche vers un point aléatoire à 10 blocs (`walkToRandomPos`). Un enfant ou un garde ne flâne pas (`canUse`).
- `walkToPos` : avant toute marche, le citoyen est arrivé à 1,5 bloc ; ensuite, une fois sa marche finie, à 3 blocs, sinon il repart ; une marche vers un autre but est remplacée.
- `walkToRandomPosWithin(citizen, 10, …, coins)` : un point de la boîte, à plus de 10 blocs, sur un sol praticable, hors danger ; rien pendant une marche aléatoire en cours, et un tirage sur deux constate seulement la fin de la précédente (le résultat de chemin est gardé 20 s).
- `getRandomLeisureSite` : une fois sur 4, l'hôtel de ville s'il est au niveau 3 ; puis site mystique, bibliothèque, université, taverne ; sous la pluie, l'hôtel de ville ; sinon un site de loisir enregistré (décorations) ; sinon rien.
- `GO_TO_LEISURE_SITE` (toutes les 20 ticks) : marche vers le site jusqu'à 3 blocs, puis `WANDER_AT_LEISURE_SITE`.
- `WANDER_AT_LEISURE_SITE` (toutes les 20 ticks) : une chance sur 300 de repartir au repos ; si le site n'est pas un bâtiment, retour au repos ; sinon une fois sur 10 une marche vers un point au hasard dans les coins du bâtiment (pause de 30 ticks), ou les places balisées (`sit`, `sit_in`, `stand_in`…) où s'asseoir ou se tenir 30 s, ou la lecture (`READ_A_BOOK`) dans une bibliothèque.
- Aucun citoyen n'est borné au territoire ; le travail peut en sortir (bûcheron, 50 blocs).
- Monstres : `EventHandler` refuse (`MobSpawnEvent.PositionCheck`) l'apparition d'un `Enemy` dans la boîte d'un bâtiment de niveau 1 ou plus ; ailleurs, la lumière de Minecraft et les gardes.

## 2. Cœur

- `citizen/wander/` (nouveau paquet ; `citizen/` est plein) : `CitizenWander` y déménage.
  - `LeisureSites` : le port de `getRandomLeisureSite` réduit à ce que HyColony a (l'hôtel de ville de niveau 3, une fois sur 4 ; l'hôtel de ville sous la pluie au centre), puis maison, puis centre.
  - `CitizenWander` garde la décision (100 ticks) et ajoute le loisir (20 ticks) en sous-état du repos : aller au site, flâner dans ses coins, repartir une fois sur 300.
- Bornage (écart demandé) : une cible de flânerie hors du territoire de la colonie est écartée ; un citoyen au repos hors du territoire marche vers sa maison, sinon le centre.
- Monstres (écart demandé) : `ColonyProtection.allowsHostileSpawn(pos)` (ou équivalent) : faux dans toute cellule revendiquée.

## 3. Plugin

- Monstres (`npc/spawn/`, recherche `docs/research/colony-bounds-and-mob-spawns.md` § 6) :
  - hostile = dans le groupe de PNJ `HyColony_Hostile` (asset `Server/NPC/Groups/HyColony/`, id dans la section `npcGroups` de l'id-map, validée au démarrage) : le groupe vanilla `Aggressive`, `Outlander`, `Scarak` et quelques rôles nommés qu'ils oublient, sans le cheval squelette ;
  - trois origines à l'apparition : une apparition du monde (`RefSystem` à l'ajout, `spawnConfiguration` déjà posé), une balise et un marqueur (`RefChangeSystem` sur `SpawnBeaconReference` et `SpawnMarkerReference`, posés juste après l'ajout) ; un PNJ né naturellement et ré-ajouté (`AddReason.LOAD` : rechargement de son chunk, marqueur qui restaure ses PNJ, changement de monde… ; ajout du 2026-10-02 : configuration d'apparition, ou marque de balise ou de marqueur déjà présente) est vérifié aussi, car il est né avant la revendication de sa cellule ou y est entré en marchant ;
  - le PNJ disparaît au tick suivant, comme une disparition vanilla (`setDespawning`, `NPCPreTickSystem`) : retiré pendant son ajout, son générateur journaliserait une erreur et un marqueur pourrait être supprimé.
- Failles (ajout du 2026-10-02, recherche § 7) : le territoire n'est pas de la nature sauvage. `ColonyWildernessTracker` remplace le tracker de Hytale (`WildernessTracker`) : un chunk de 32 qui touche une cellule revendiquée n'est pas sauvage (`ColonyProtection.isWilderness`), et sa `generation` suit `TerritoryIndex.revision()`. `WildernessTrackerSystem` le remet en place quand Hytale recrée le tracker.

## 4. Écarts

- Loisir : sans site mystique, bibliothèque, université, taverne ni décorations de loisir, le site est l'hôtel de ville (niveau 3, ou sous la pluie), la maison ou le centre. Pas de places balisées ni de lecture : les plans de HyColony n'ont pas de balises (`sit`…). Pas de préférence pour l'intérieur sous la pluie.
- Le loisir est un sous-état du repos (l'état affiché reste « repos ») ; MC a trois états à lui.
- Aller au site s'arrête après 120 s (`WANDER_TIMEOUT_TICKS`) : MC compte sur son anti-blocage qui téléporte.
- Une marche dans les coins du bâtiment vise, sans recherche de chemin, un point au hasard dans la boîte à la hauteur du bloc de hutte, à plus de 10 blocs et hors danger ; elle part dès que la précédente est finie (MC saute un tirage tant qu'il garde son dernier résultat de chemin). Pas de préférence liée à la pluie (`preferInside`, qui dans le code de MC écarte d'ailleurs les cases couvertes).
- La pause de 60 s est comptée par la flânerie elle-même (12 décisions de 100 ticks de repos hors loisir), le loisir n'ayant pas d'état à lui.
- Bornage de la flânerie et retour vers la maison : ajouts demandés, MC laisse dériver ses citoyens. Une maison inaccessible est retentée à chaque décision.
- Pas d'apparition naturelle de monstre hostile dans tout le territoire : MC ne refuse que dans les bâtiments (Hytale n'a pas l'équivalent de la lumière). Un monstre qui entre en marchant reste jusqu'au prochain rechargement de son chunk, où il disparaît (MC ne retire jamais un monstre déjà là). Les marqueurs d'apparition (camps du monde) sont visés aussi, alors que MC laisse passer ses spawners (`MobSpawnType.SPAWNER`). La copie d'un monstre né du monde (`/entity clone`) garde sa configuration d'apparition et disparaît aussi. Le monstre peut se voir un tick.
- Pas de faille (événement du monde d'Update 7) dans un chunk de 32 qui touche le territoire : MC n'a pas de failles. Pour Hytale, seul un lit rend une zone « habitée ». Une faille tirée juste au-delà de la bordure peut déborder de quelques blocs (rayon de recherche de 16).

## 5. Tests

- Cœur : la chance de loisir et le choix du site (hôtel de ville de niveau 3, pluie, maison, centre) ; la pause de 60 s ; l'arrivée au site puis la flânerie dans les coins ; la sortie une fois sur 300 ; le site qui n'est pas un bâtiment ; l'abandon après 120 s ; la cible hors territoire écartée ; le retour d'un citoyen hors territoire ; le refus d'apparition dans une cellule revendiquée et pas ailleurs.
- En jeu : nouveaux points de `docs/TESTING.md`.
