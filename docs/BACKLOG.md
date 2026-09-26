# Backlog HyColony

Ce qui a été décidé ou signalé mais pas encore fait, du plus urgent au moins urgent. Une ligne qui commence par « Décision » attend une réponse de l'utilisateur.

## Relecture indépendante du 2026-09-26 (commits 6605ee1..6c6be03) : à corriger

Verdict : « à corriger ». Le découpage du cœur ne change pas le comportement (requêtes, constructeur, sauvegardes, permissions et fonctions Hytale vérifiés). Aucune correction n'est lancée sans l'accord de l'utilisateur.

**Important**
1. `core/.../kernel/nav/StuckHandler.java:67,85` : après une téléportation, `teleported=true` coupe le délai global pour toujours, et l'abandon n'arrive que s'il n'y a plus aucun progrès. Un PNJ posé à 2 blocs ou plus de sa cible (sous un toit) peut donc tourner en rond indéfiniment. Correctif : `teleportTick`, puis abandon au-delà du délai, avec un test `circlingAfterTeleportGivesUp`.
2. Il reste des commentaires séparateurs, interdits depuis aa1ee5a :
   - `request/RequestManager.java:56,75,216,247` ;
   - `request/Request.java:73` ;
   - `request/RequestSerializer.java:114,211` ;
   - `request/RequestStore.java:139` ;
   - `BuilderAITest.java:111,186`.

   Correctif : les supprimer et ajouter au build une vérification sur l'expression `//\s*-{3,}`.

**Mineur**
3. Trop de choses sont publiques :
   - `workorder/BuildCompletion.apply` : l'intégrer dans `WorkManager.finish(order, hut)` ou documenter pourquoi elle est publique ;
   - `WorkOrder.setStage` et `setProgressIndex` : les remplacer par un seul `progress(Stage, int)` ;
   - `WorkOrder.setPriority` : le repasser en package-private.
4. Dépendance circulaire : `workorder/WorkOrderAssignment.java:5` importe `builder.BuilderSettingsModule`, et `builder` dépend de `workorder`. Correctif : remonter ce module dans `construction`, ou exposer le mode via `Building`.
5. Tailles : le constructeur de `Colony.java` (lignes 55 à 117) fait environ 63 lignes, il faut en extraire `registerTicks()`. `BuilderStock` (312 lignes) gère à la fois les objets et les requêtes, il faut en extraire `BuilderRequests`.
6. `HytaleCitizenBodies.teleport` écrit un WARNING à chaque échec. Avec un indicateur `warned`, seul le premier doit être en WARNING, les suivants en FINE.
7. `WorkSpot.java:178` : l'emplacement de secours `free` peut tomber sur un bloc enterré ou au fond d'un lac. Correctif : exiger `!solid(top)` et considérer qu'on ne peut pas se tenir dans un fluide.
8. Tests manquants :
   - un constructeur qui attend ne doit pas prendre le stock de la hutte réservé à une autre requête (728d3f3) ;
   - l'égalité d'adéquation entre deux résolveurs (`RequestAssigner:74`) ;
   - `openTownHall` dans la boucle de refus de `ViewsTest` ;
   - une sauvegarde de requêtes sous forme de fixture.
9. Les écarts à MineColonies (StuckHandler, WorkSpot, walker) ne sont documentés que dans la Javadoc. Il faut les reporter dans la spec du sous-projet (CLAUDE.md § 6).
10. Historique git :
    - le commit 2a11ac9 cache l'orientation, l'emplacement de travail, l'anti-blocage et la téléportation sous un message « build: ». Il faut le mentionner dans la description de la PR ;
    - le commit d811903 ajoute `.mcp.json`, qui contient un chemin propre à la machine et un paquet `npx` sans version figée.
11. `ColonyWindows.show*` (`view/ColonyWindows.java:85-95`) sont publiques et ne vérifient pas les droits. Il faut au minimum documenter que l'appelant a vérifié ACCESS_HUTS.

**Exception PMD `CouplingBetweenObjects` sur `RequestManager` (26 pour une limite de 20)** : le relecteur recommande de la garder. C'est la seule porte d'entrée publique du système de requêtes. On pourra la réduire à 22 environ la prochaine fois qu'on touche ce fichier :
- déplacer `PUBLIC_STATES` dans `RequestTransitions` ;
- déplacer les boucles `cancelOrphans` et `cancelAllFrom` dans `RequestCanceller` ;
- retirer `Objects`.

## En cours ou prochain

- **Découpage du plugin** : lignes PMD restantes, `HytaleItemCatalog`, `BuildingPage`, `HytaleWorldBlocks`, etc.
- **Escaliers, toits, clôtures et murs connectés** : poser la variante exacte du prefab (voir `docs/research/connected-blocks.md`, option a).
- **Blocs impossibles à obtenir en survie dans les plans** : ramener le coût au drop du bloc, faire demander les graines pour les cultures, ajouter une table de remplacement par style, et un contrôle au démarrage et dans `selftest` (voir `docs/research/prefab-obtainability.md`).
  - Décision : Kweebec niveau 5 (séquoia géant) → garder ce prefab ou prendre une maison plus petite ?
  - Décision : Outlander niveau 1 → passer à `Tier0_006` ?
- **Les PNJ ignorent le feu** : ils marchent dedans et brûlent. MineColonies évite le feu et la lave dans le calcul de chemin et fait fuir le citoyen blessé. Chercher le réglage d'évitement des blocs dangereux de Hytale (rôle du PNJ, contrôleur de déplacement).
- **Fenêtres alignées sur le wiki MineColonies** (https://minecolonies.com/wiki/) : comparer chaque fenêtre à ses captures (disposition, onglets, boutons, ordre des infos), puis proposer une liste d'ajustements que l'utilisateur valide.

## Plus tard

- **Baguette de construction** (outil de Structurize) : aperçu fantôme du bâtiment avec `PersistentPrefabPreview`, qu'on déplace et fait pivoter avant de poser la hutte.
- **SP3a, colonie autonome** : entrepôt, livreurs, bûcheron, puis mineur ou carrière (voir les recherches `sp3a-*`).
  - Décision : mineur adapté (escalier en colimaçon, car les PNJ ne montent pas aux échelles) ou carrière d'abord ?
- **Modes de construction** (spirale, de l'extérieur vers l'intérieur…), débloqués par la recherche (université).
- **Apparences aléatoires des citoyens.**
- **Hôtel de ville disparu** sans être cassé par un joueur : aujourd'hui il ne peut plus être reposé, il faut passer par `/hycolony delete`.
- **Outlander niveau 5** : bâti à flanc de colline, son plancher peut flotter sur un terrain en pente.
