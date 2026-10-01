# Backlog HyColony

Ce qui a été décidé ou signalé mais pas encore fait, du plus urgent au moins urgent. Une ligne qui commence par « Décision » attend une réponse de l'utilisateur.

## Garde-fous : limites connues (après 6767c89..b1362c9)

La garde arrête un agent qui dérive par erreur, pas un adversaire (CLAUDE.md § 10). Banc de test : `node .claude/hooks/test/run.js`. Restent hors de portée, à décider si besoin :

- les commandes cachées dans `$(...)` ou des accents graves hors guillemets, dans un script lancé (`node x.js`, `sh x.sh`), derrière `xargs` ou un alias git ;
- `git stash pop`, `git reset --hard`, `git apply` ou `git am` qui restaurent ou modifient un garde-fou ;
- les `build.gradle.kts` de `core/` et `plugin/`, et `settings.gradle.kts`, ne sont pas protégés (seuls les contrôles du fichier racine le sont) ;
- `"disableAllHooks": true` dans les réglages utilisateur (`~/.claude/settings.json`) coupe la garde ; un `"disableAllHooks": false` dans `.claude/settings.json` le contrerait ;
- en session déverrouillée (`HYCOLONY_GUARDRAILS_UNLOCKED=1`), la garde laisse passer quand elle plante, pour pouvoir la réparer.

## Citoyens et feu : reste à faire (2026-09-26)

- Le détour (`SafeRoute`) ne lit pas le relief : la hauteur est interpolée entre départ et cible. Un feu sur une colline ou dans un creux de plus de 2 blocs peut être manqué, et entre deux étapes la navigation de Hytale peut encore dévier sur un feu pour contourner un obstacle. À reprendre si on le voit en jeu (lecture de la hauteur du sol par colonne).
- Le détour ne tient pas compte des murs : `SafeRoute` ne lit que les colonnes dangereuses, et peut faire passer un segment à travers un bloc plein. La navigation de Hytale contourne alors le mur à sa façon, parfois sur le feu (relecture du 2026-09-26).
- `isHarmful` compte comme dangereux tout bloc qui a une interaction de collision (`BlockType.isTrigger`), y compris des blocs sans dégâts (pièges, algue ralentissante, fleur d'eau). Les citoyens les évitent aussi (relecture du 2026-09-26).
- `DetouringBodiesTest` : aucun test ne montre que l'étape refusée est revérifiée quand le corps change de bloc (relecture de b7666c4).
- Pas de réaction à une brûlure : MineColonies n'en a pas non plus (il compte sur son pathfinding) ; le rôle `HyColony_Citizen` est `Invulnerable`. À ajouter seulement si le point précédent ne suffit pas.

## Déplacement du constructeur : écarts à MineColonies non traités (contrôle de fidélité du 2026-09-26)

Sujet volontairement mis en pause par l'utilisateur, faute de problème constaté en jeu. À reprendre seulement si un problème apparaît :

- La spec § 11 « Portée » dit que le constructeur ne travaille jamais un bloc à plus de 5 blocs. C'est faux : après un deuxième choix d'emplacement, il travaille d'où il est (`BuilderWalker.java:68`). MC ne le fait qu'une fois, et seulement à moins de 100 blocs du chantier. **Corriger au moins la spec.**
- Emplacement préféré : extérieur, puis côtés, puis intérieur. MC préfère la case la plus proche du bloc et du centre du chantier (`PathJobMoveCloseToXNearY`).
- La distance verticale n'est pas bornée (jusqu'à 16 blocs). MC exige une distance de Manhattan 3D ≤ 4.
- Seules 12 colonnes sont testées (4 directions × 2 à 4 blocs). MC teste toute case à distance de Manhattan de 1 à 4, diagonales comprises.
- Pas de remise à zéro de l'emplacement quand le constructeur est sur la colonne du bloc. Pas de nouveau choix d'emplacement quand il est bloqué.
- Toute fin de navigation compte comme une arrivée (rayon de 2). MC relance la marche si le constructeur est à plus de 4 blocs.
- La téléportation arrive environ 8 fois plus tôt que dans MC, qui passe d'abord par sauts de nœud, recalcul et recul. Près de la cible, MC remet à zéro le délai global et attend.
- La cadence réelle de MC est de 12 ticks, pas 10. La source MC citée dans `StuckHandler` est inexacte (c'est `AbstractEntityCitizen:352`).
- Si le même emplacement passe de « vérifié » à « non vérifié », le trajet n'est pas relancé : téléportation possible dans ce cas très improbable (`BuilderWalker.navTarget`).

## Lunettes de constructeur : points mis de côté (relecture du 2026-09-26)

- Si le chunk de l'entité d'aperçu se décharge, l'aperçu disparaît et n'est recréé qu'au prochain changement de blocs.
- Aucun test ne couvre le cas « colonie la plus proche » (`GogglesView.nearest`), quand le joueur est hors de toute colonie.

## Garde-fous : relecture du 2026-09-26 (3e passe)

**Mineur**
- Faux positifs : un code en ligne (`python -c`, `node -e`) qui ne fait que **lire** un garde-fou est refusé.
- `%USERPROFILE%` dans `.claude/skills/hytale-api/SKILL.md:11`.
- Limites à ajouter à la liste ci-dessus : chemins en variable (`$CLAUDE_PROJECT_DIR`, `~`), `find -delete`, `git clean -fdX`, changement de commit courant (`checkout`/`switch`/`merge`/`rebase`/`pull`), `Push-Location`, `git -C core add .`, code placé avant `// CLAUDE.md §` dans `build.gradle.kts`.
- Le confinement du chercheur à `docs/research/` est surestimé dans CLAUDE.md.
- `pre-push` construit HEAD et non la référence poussée. `pre-commit` ignore les listes non indexées.
- Message trompeur pour `git checkout -- .`. `guard.js` fait 526 lignes : à découper.
- Contournements trouvés par la relecture de 807cd8a. Ce sont des formes qu'un agent n'écrit pas par erreur, donc elles sont parquées :
  - `env -S '…'` et `--split-string` ;
  - `sh -o errexit gradlew runServer` ;
  - un `cd ~/.claude` ou `cd` sans argument avant d'écrire `settings.json` ;
  - `$USERPROFILE`, `${USERPROFILE}` et `${env:USERPROFILE}` dans le chemin des réglages utilisateur ;
  - du code en ligne qui écrit `~/.claude/settings.json`.

## Relecture indépendante du 2026-09-26 (commits 6605ee1..6c6be03) : reste

Les autres points de la relecture sont corrigés. Il reste :

- **Historique git** (point 10 de la relecture), impossible à corriger sans réécrire l'historique :
  - le commit 2a11ac9 cache l'orientation, l'emplacement de travail, l'anti-blocage et la téléportation sous un message « build: ». Il faut le mentionner dans la description de la PR ;
  - le commit d811903 ajoute `.mcp.json`, qui contient un chemin propre à la machine et un paquet `npx` sans version figée.
- **Exception PMD `CouplingBetweenObjects` sur `RequestManager`** : 25 pour une limite de 20 après les coupes (`PUBLIC_STATES` dans `RequestTransitions`, annulations en masse dans `RequestCanceller`, `Objects` retiré). L'exception reste : c'est la seule porte d'entrée publique du système de requêtes.

## Audit du code du 2026-09-29 : reste à faire

Les corrections de l'audit sont commitées : bugs, socle des métiers (`job/work`), couche `app`, protection de colonie, règles des onglets, lectures tolérantes, garde des ports du plugin. `FeatureDependenciesTest` fige les dépendances entre paquets de premier niveau : la matrice ne peut que rétrécir. Le cycle restant passe par `Colony`, que toutes les fonctionnalités tiennent, comme `IColony` dans MC. Restent :

- **Garde-fous, avec l'accord de l'utilisateur (session déverrouillée)** :
  - lancer les contrôles Python des outils dans le hook `pre-push` : aucun ne tourne aujourd'hui (`tools/domum/check.py` a besoin des assets et du réseau, il ne peut pas tourner sur la CI) ;
  - longueur des lignes : environ 55 lignes dépassent 120 colonnes (Javadoc, commentaires, chaînes de test). palantir ne les recoupe pas et aucune règle ne le vérifie.
- **Constructeur, outil manquant** : `BuilderBlockWork.fetchTool` ne suit pas l'ordre de `ToolRequests.missing` (la hutte d'abord, vidage quand l'inventaire est plein). À comparer à MC (`checkForToolOrWeapon`, `holdEfficientTool`) avant de les unifier.
- **Plugin** :
  - découper les classes fourre-tout tolérées par PMD : `HytaleCitizenBodies` (références, navigation, gestes), `HytaleItemCatalog` (faits des blocs, faits des outils), `HyColonyCommand` (une classe par sous-commande), `HytaleUiPort` ;
  - le retrait du bloc d'une fondation d'hôtel de ville abandonnée est décidé dans `HytaleUiPort` (quand `pendingPositionOf` devient vide) : à remonter dans le cœur par un port des blocs de huttes ;
  - `RequestsPage` et `CitizenRequestsTab` choisissent la fenêtre à ré-afficher après « Fournir » ;
  - identifiants d'assets hors des id-maps (`Immunity_Fire`, `Physical`, les types de récolte, `Soil_Dirt_Tilled`, `Tool_Fertilizer`, `Block_Spawner_Block`) et descriptions des commandes en anglais brut.
- **HyDomum** : les règles du cutter (plafond de fabrications, prise dans les emplacements, file de fabrication) sont dans `domum/plugin`, sans test ; les remonter dans `domum/core`. Reporté tant qu'une autre session travaille sur HyDomum.
- **Outils Python** : une seule recherche d'`Assets.zip` (trois copies, dont une sur `release/latest` au lieu de la version épinglée), un paquet `tools/common` au lieu des `sys.path.append`, les racines du validateur d'assets en un seul endroit (Python et Kotlin divergent déjà).

## En cours ou prochain

- **Documenter tout le code** : ajouter une Javadoc courte à chaque classe et à chaque méthode non triviale du cœur et du plugin (règle de CLAUDE.md § 3), puis la faire respecter par le build avec la règle PMD `CommentRequired` (classes, méthodes publiques et protégées). À lancer après les corrections de la relecture, pour éviter les conflits.
- **Découpage du plugin** : lignes PMD restantes, `HytaleItemCatalog`, `BuildingPage`, `HytaleWorldBlocks`, etc.
- **Blocs impossibles à obtenir en survie dans les plans** : ramener le coût au drop du bloc, faire demander les graines pour les cultures, ajouter une table de remplacement par style, et un contrôle au démarrage et dans `selftest` (voir `docs/research/prefab-obtainability.md`).
- **Fenêtres alignées sur le wiki MineColonies** (https://minecolonies.com/wiki/) : comparer chaque fenêtre à ses captures (disposition, onglets, boutons, ordre des infos), puis proposer une liste d'ajustements que l'utilisateur valide.

## Plus tard

- **Touches clavier pour la baguette** (pas urgent, demandé par l'utilisateur) : Structurize déplace l'aperçu aux flèches. Deux pistes à tester en jeu : la liaison `KeyDown` d'une page (`CustomUIEventBindingType.KeyDown`, jamais utilisée en vanilla, contenu inconnu) et les touches d'interaction de l'objet (`Ability1-3`, `Use`, `Pick`) baguette en main, fenêtre fermée.
- **Inventaire du citoyen, complément de pile** : dans Minecraft, un clic simple qui complète une pile passe par `Slot.set` et résout une requête ; seul Maj+clic ne le fait pas. Hytale ne distingue pas les deux : aujourd'hui aucun complément ne résout (à vérifier dans les sources vanilla de Minecraft avant de trancher).
- **Inventaire du citoyen et mort** : quand un citoyen pourra mourir ou être retiré seul, fermer ses fenêtres d'inventaire ouvertes (aujourd'hui seulement sur `ColonyDeleted`).

- **Baguette de construction, hors de la première version** (spec `2026-09-26-hycolony-build-tool-design.md`) :
  - décorations et ordres de décoration ;
  - miroir ;
  - aperçus partagés (`share_previews` de Structurize) ;
  - huttes voisines affichées pendant le placement (`NearBuildPreview`) ;
  - collage créatif « Complete » (inutile sans blocs substituts ; « Pretty » est fait), outils de scan et de formes ;
  - ancre sur la face visée plutôt qu'au-dessus du bloc, si Hytale peut la donner à une interaction serveur ;
  - retirer les lunettes efface aussi le fantôme de la baguette (`BuildGoggles.unequip` appelle `hideAll`) : il revient au prochain clic.
- **SP3a, colonie autonome** : entrepôt et livreurs faits ; restent bûcheron, puis mineur ou carrière (voir les recherches `sp3a-*`).
  - Décision : mineur adapté (escalier en colimaçon, car les PNJ ne montent pas aux échelles) ou carrière d'abord ?
- **Entrepôt, hors de la première version** (spec `2026-09-27-hycolony-sp3a-warehouse-courier-design.md`) :
  - stock minimum (`MinimumStockModule`), avec les `StackList` (`leftOver`) et la règle « pas pour le stock minimum d'un autre entrepôt » ;
  - bouton « trier » de l'entrepôt au niveau 3 (`SortBuildingMessage`) ;
  - améliorations de stockage au niveau 5 (`UpgradeWarehouseMessage`, `MAX_STORAGE_UPGRADE = 3`) et le message « payez un bloc d'émeraude » ;
  - onglet Livreurs : rattacher ou détacher un livreur à la main, mode d'embauche (MC `SpecialAssignmentModuleWindow`) ;
  - onglet Stock : bouton de tri et recherche (MC `WindowHutAllInventory`) ;
  - annonce au joueur d'une livraison ou d'un ramassage qu'il détient faute de livreur (aujourd'hui seulement visibles dans le presse-papiers).
  - `keepFood` quand le système de faim existera : chaque hutte garde `niveau × 2` aliments, inventaire compris (MC `AbstractBuilding.keepFood`, `HutKeep`).
  - `CitizenAI` en IDLE ne revérifie `shouldWork` que toutes les 20 ticks (MC : 10 ticks dans tous les états, `decideAiTask`).
- **Entrepôt et livreurs : points mineurs reportés** (relecture finale de SP3a, 2026-09-27) :
  - `CourierTaskPicker` appelle `RequesterLocation.of` pour chaque entrée de la file (file × bâtiments, sans `SCAN_LIMIT`) ;
  - la tâche en cours (`ongoing`) d'un livreur n'est pas vidée quand il devient inactif (comme MC), sans que ce soit documenté ;
  - Javadoc à compléter : `tokens()` de la file, bouton Stock (`BuildingViews.stock`) ;
  - tests : le message « entrepôt plein » n'est suivi que sur 2000 ticks ; les simulations d'échec ne vérifient la conservation des objets que pour `PLANKS` ;
  - plugin : les gestionnaires « fournir » revérifient `fulfillable` (fait le 2026-10-01, `RequestTreeEvents`, `RequestDetailPage`) ;
  - nom d'objet : repli sur le `Message` brut quand la traduction manque (`itemName`) ;
  - constante de cadre dupliquée dans les fenêtres de logistique du plugin.
- **Domum Ornamentum** : repris le 2026-09-28 par un générateur au build, en trois sous-projets. DO-1 (les blocs, spec `docs/superpowers/specs/2026-09-28-hycolony-domum-ornamentum-do1-design.md`) est en cours ; restent :
  - DO-2, le cutter : établi de l'architecte, recettes, liste des matériaux par emplacement ;
  - DO-3, le lien avec MineColonies : blocs DO dans les plans, requêtes du constructeur, artisans ;
  - lumières encadrées : Hytale n'a pas de bloc lumineux plein comme la glowstone pour le centre ;
  - briques DO et blocs « extra » : textures DO 16 px à redessiner en 32 px ;
  - tonneaux et tapis flottants DO.
- **Modes de construction** (spirale, de l'extérieur vers l'intérieur…), débloqués par la recherche (université).
- **Fermier, suites** (SP3b-2) :
  - les citoyens ne mangent ni les graines des champs ni le blé : à revoir avec la nourriture (SP4) ;
  - la lanterne de l'épouvantail : le bloc Champ n'a pas de moitié haute ni de lumière.
- **Apparences aléatoires des citoyens.**
- **Hôtel de ville disparu** sans être cassé par un joueur : aujourd'hui il ne peut plus être reposé, il faut passer par `/hycolony delete`.
- **Nourriture et salle à manger (SP4b), reste** :
  - les interactions de MC (plaintes du serveur : pas de combustible choisi, pas de four, menu vide ; citoyen affamé sans salle) attendent un système d'interactions ;
  - des plans propres à la salle à manger (aujourd'hui ceux de la résidence), avec feux de camp et sièges ;
  - le module de statistiques de MC (repas servis) ;
  - la mort des citoyens (rôle `Invulnerable`) pour voir soins et blessures en jeu.
