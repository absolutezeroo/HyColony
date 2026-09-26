# Backlog HyColony

Ce qui a été décidé ou signalé mais pas encore fait, du plus urgent au moins urgent. Une ligne qui commence par « Décision » attend une réponse de l'utilisateur.

## Garde-fous : limites connues (après 6767c89..b1362c9)

La garde arrête un agent qui dérive par erreur, pas un adversaire (CLAUDE.md § 10). Banc de test : `node .claude/hooks/test/run.js`. Restent hors de portée, à décider si besoin :

- les commandes cachées dans `$(...)` ou des accents graves hors guillemets, dans un script lancé (`node x.js`, `sh x.sh`), derrière `xargs` ou un alias git ;
- `git stash pop`, `git reset --hard`, `git apply` ou `git am` qui restaurent ou modifient un garde-fou ;
- les `build.gradle.kts` de `core/` et `plugin/`, et `settings.gradle.kts`, ne sont pas protégés (seuls les contrôles du fichier racine le sont) ;
- `"disableAllHooks": true` dans les réglages utilisateur (`~/.claude/settings.json`) coupe la garde ; un `"disableAllHooks": false` dans `.claude/settings.json` le contrerait ;
- en session déverrouillée (`HYCOLONY_GUARDRAILS_UNLOCKED=1`), la garde laisse passer quand elle plante, pour pouvoir la réparer.

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
