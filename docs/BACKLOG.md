# Backlog HyColony

Ce qui a été décidé ou signalé mais pas encore fait, du plus urgent au moins urgent. Une ligne qui commence par « Décision » attend une réponse de l'utilisateur.

## Relectures du 2026-09-26, 2e passe : à corriger (en attente de l'accord de l'utilisateur)

### Correctifs du tour 2 (9e77965..d4641b8)

**Bloquant**
1. `WorkSpot.java:92` : la règle « on peut se tenir dans 1 bloc de fluide » accepte aussi **1 bloc de lave**, parce que `HytaleItemCatalog` classe tous les `~fluid:*` en FLUID. Correctif : ajouter au port une information indiquant si l'on peut se tenir dans ce fluide (sûr ou dangereux), remplie à partir de l'asset `Fluid`. Ajouter le test `neverStandsInAnkleDeepLava`.

**Mineur**

2. `WorkSpot.ground()` : la branche qui monte refuse toujours l'eau, tandis que celle qui descend l'accepte. Les aligner ou documenter l'écart.
3. Un cycle reste via `building` : `shared.ClaimRadius` → `building.BuildingTypes` → `hut.ConstructionBuildingTypes` → `shared`. ArchUnit ne le détecte pas. Soit on l'accepte explicitement, soit on passe l'id de l'hôtel de ville en paramètre à `ClaimRadius`.
4. `RequestManager.reassignLoaded` (ligne 224) ne cite pas sa méthode source dans MineColonies.
5. Les séparateurs écrits en commentaire bloc sur plusieurs lignes ne sont pas détectés.

### Garde-fous (d80fbc1, 487c6ad, 0c5ba06, b9c7eda)

Pour les corriger, l'utilisateur doit relancer Claude Code avec `HYCOLONY_GUARDRAILS_UNLOCKED=1`.

**Bloquant**
1. On peut contourner l'interdiction de sauter les hooks (`guard.js:18`) de plusieurs façons :
   - l'option entre guillemets ;
   - un préfixe abrégé ;
   - une option globale placée avant la sous-commande ;
   - un `;` ou un `|` dans le message ;
   - un message en heredoc.

   Correctif : découper la commande en mots en retirant les guillemets.
2. On peut désactiver les hooks via `core.hooksPath` : valeur vide, `unset`, suppression de la section `core`, `config --edit`, ou écriture directe dans `.git/config`.
3. `.claude/settings.local.json` n'est pas protégé. `{"disableAllHooks": true}` y coupe toute la garde, et le fichier n'est pas non plus dans le `.gitignore` du dépôt.
4. On peut contourner les listes « shrink-only » en remplaçant une ligne par une autre, avec `replace_all`, ou avec un chemin écrit dans une autre casse. Correctif : comparer les entrées, pas le nombre de lignes.
5. Bash et PowerShell peuvent écrire sans contrôle dans les listes, les fichiers MCP, la config locale et `settings.local.json`.
6. La protection des fichiers garde-fous a plusieurs trous :
   - chemins avec `\` ;
   - alias PowerShell ;
   - écriture via `node -e`, `python -c` ou `perl -pi` ;
   - restauration par `git checkout` ou `restore` ;
   - `cd` suivi d'un chemin relatif ;
   - chemin écrit dans une autre casse.
7. La liste des garde-fous varie d'un document à l'autre. `AGENTS.md`, `CLAUDE.md`, `.claude/agents` et `.claude/skills` ne sont pas protégés. `build.gradle.kts` (seuils, regex) et `config/pmd/ruleset.xml` se modifient librement.
8. Les hooks git vérifient les fichiers sur disque, pas ce qui est indexé. Avec le hook Stop (`spotlessApply`), du code mal formaté peut donc passer. Correctif :
   - `pre-commit` refuse si un fichier indexé a aussi des modifications non indexées ;
   - `pre-push` refuse si l'arbre de travail n'est pas propre.

**Mineur**

9. Faux positifs :
   - un message de commit qui contient `-a` ou `-n` ;
   - une commande dont le texte cite un fichier ou une option interdite, par exemple l'écriture de ce backlog le 2026-09-26 ;
   - une lecture des garde-fous accompagnée d'une redirection, d'une flèche, de `rm` ou de `cp`.
10. Des formes d'indexation trop larges passent encore : options entre guillemets ou combinées, `--update`, `:/`, `*`, la commande `stage`, une option globale. L'ajout du fichier MCP et de `settings.local.json` passe aussi.
11. On peut encore lancer le serveur avec les abréviations Gradle, des guillemets insérés, `java -cp HytaleServer.jar` ou `Start-Process`.
12. Autres trous :
    - `push` : le forçage entre guillemets ou avec une option globale passe ; `--mirror`, `--delete` et `origin :main` ne sont pas couverts ;
    - `commit-msg` : `Merge`, `Revert`, `fixup!` et `squash!` passent sans que le format soit vérifié ;
    - `pre-commit` : `gradle.properties` n'est pas couvert.
13. `guard.js` laisse passer quand il plante (JSON illisible), et `MultiEdit` n'est pas couvert.
14. Documentation :
    - AGENTS.md:18 : « -n on push » est faux ;
    - CLAUDE.md § 8 ne cite que 2 des 3 listes ;
    - CLAUDE.md:98 dit « impossibles » ; il faudrait « difficiles à commettre par inadvertance ».
15. Agents :
    - `hycolony-implementer` renvoie au skill `port-mc`, qui n'est pas invocable par le modèle : il faut lui faire lire le fichier SKILL.md ;
    - `hycolony-researcher` n'est limité à `docs/research/` que par une consigne : il faut un hook dans son frontmatter ;
    - remplacer `%USERPROFILE%` par `$USERPROFILE`.

Le banc de test de la garde (`t.js`, `cases.txt`) est dans le scratchpad de la session du 2026-09-26.

## Relecture indépendante du 2026-09-26 (commits 6605ee1..6c6be03) : reste

Les autres points de la relecture sont corrigés. Il reste :

- **Historique git** (point 10 de la relecture), impossible à corriger sans réécrire l'historique :
  - le commit 2a11ac9 cache l'orientation, l'emplacement de travail, l'anti-blocage et la téléportation sous un message « build: ». Il faut le mentionner dans la description de la PR ;
  - le commit d811903 ajoute `.mcp.json`, qui contient un chemin propre à la machine et un paquet `npx` sans version figée.
- **Exception PMD `CouplingBetweenObjects` sur `RequestManager`** : 25 pour une limite de 20 après les coupes (`PUBLIC_STATES` dans `RequestTransitions`, annulations en masse dans `RequestCanceller`, `Objects` retiré). L'exception reste : c'est la seule porte d'entrée publique du système de requêtes.

## En cours ou prochain

- **Documenter tout le code** : ajouter une Javadoc courte à chaque classe et à chaque méthode non triviale du cœur et du plugin (règle de CLAUDE.md § 3), puis la faire respecter par le build avec la règle PMD `CommentRequired` (classes, méthodes publiques et protégées). À lancer après les corrections de la relecture, pour éviter les conflits.
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
