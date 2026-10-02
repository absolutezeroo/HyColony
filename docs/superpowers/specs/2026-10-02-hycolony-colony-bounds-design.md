# Citoyens dans la colonie, monstres dehors

Date : 2026-10-02. Demandé par l'utilisateur : « il faudrait éviter que les citoyens sortent de la bordure et que les monstres spawn, car sur Hytale il n'y a pas de système de luminosité ». Choix de l'utilisateur : « MC + borner la flânerie » et « tout le territoire » pour les monstres.

Recherche : `docs/research/colony-bounds-and-mob-spawns.md` (MC ne garde pas ses citoyens dans le territoire et ne refuse l'apparition des monstres que dans les bâtiments ; Hytale n'a pas de condition de lumière sur les monstres de surface).

## 1. Ce que fait MineColonies

- `EntityAICitizenWander.decide` (toutes les 100 ticks, au repos) : 5 fois sur 100 (`LEISURE_CHANCE`), un site de loisir (`getRandomLeisureSite`), sinon la maison du citoyen, sinon le centre de la colonie, puis une pause de l'IA de 60 s (`setCurrentDelay(60 * 20)`) et l'état `GO_TO_LEISURE_SITE`. Sinon, une marche vers un point aléatoire à 10 blocs (`walkToRandomPos`).
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

- Monstres : retrait, à son ajout au monde, d'un PNJ hostile né d'une apparition naturelle dans une cellule revendiquée. Le test « hostile » et le crochet d'ajout sont à vérifier (`docs/research/colony-bounds-and-mob-spawns.md`, suite).

## 4. Écarts

- Loisir : sans site mystique, bibliothèque, université, taverne ni décorations de loisir, le site est l'hôtel de ville (niveau 3, ou sous la pluie), la maison ou le centre. Pas de places balisées ni de lecture : les plans de HyColony n'ont pas de balises (`sit`…). Pas de préférence pour l'intérieur sous la pluie.
- Le loisir est un sous-état du repos (l'état affiché reste « repos ») ; MC a trois états à lui.
- Aller au site s'arrête après 120 s (`WANDER_TIMEOUT_TICKS`) : MC compte sur son anti-blocage qui téléporte.
- Une marche dans les coins du bâtiment vise un point au hasard dans la boîte, sans recherche de chemin.
- Bornage de la flânerie et retour vers la maison : ajouts demandés, MC laisse dériver ses citoyens.
- Pas d'apparition naturelle de monstre hostile dans tout le territoire : MC ne refuse que dans les bâtiments (Hytale n'a pas l'équivalent de la lumière). Un monstre qui entre en marchant reste possible.

## 5. Tests

- Cœur : la chance de loisir et le choix du site (hôtel de ville de niveau 3, pluie, maison, centre) ; la pause de 60 s ; l'arrivée au site puis la flânerie dans les coins ; la sortie une fois sur 300 ; le site qui n'est pas un bâtiment ; l'abandon après 120 s ; la cible hors territoire écartée ; le retour d'un citoyen hors territoire ; le refus d'apparition dans une cellule revendiquée et pas ailleurs.
- En jeu : nouveaux points de `docs/TESTING.md`.
