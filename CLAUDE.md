# HyColony : règles du projet

Ces règles valent pour tout le monde : l'utilisateur, Claude et chaque agent. Un agent qui code ou relit ici les lit **en entier** avant de commencer. Une règle vérifiée par le build est marquée **[build]**.

HyColony porte MineColonies sur Hytale 0.6.8 (épinglé), **à l'identique** : mêmes systèmes, règles, constantes et formules. Les écarts sont rares, justifiés et documentés (voir § 6).

## 1. Modules et dépendances

- `core/` contient la logique du jeu en Java pur. **Aucun import `com.hypixel`** **[build : ArchitectureTest]**. Il ne dépend que du JDK et de Gson (compileOnly). Il est compilé en `--release 21`.
- `plugin/` contient les adaptateurs Hytale et le pack d'assets. Il ne contient **pas** de règles de jeu : une décision de jeu prise dans le plugin est un bug.
- Architecture ports & adaptateurs :
  - le cœur définit des ports (`kernel/port`, `construction/blueprint/BlueprintSource`, `colony/ui/UiPort`) ;
  - le plugin les implémente (préfixe `Hytale*`) ;
  - les tests les simulent (préfixe `Fake*`, dans `core/src/test/.../testing`).
- **Un paquet contient au plus 15 fichiers** **[build : `checkFileSizes`]**. Au-delà, on crée des sous-paquets par sous-domaine : par exemple `construction/blueprint`, `construction/workorder`, `construction/builder`, `construction/resources`, ou `colony/territory`, `colony/permission`, `colony/view`. Un sous-paquet regroupe ce qui change ensemble. Seul le point d'entrée du sous-domaine est `public`, le reste reste package-private autant que possible.
- Les paquets du cœur sont découpés **par fonctionnalité** (`colony`, `building`, `citizen`, `request`, `job`, `construction`…), pas par couche. `kernel` ne dépend d'aucun autre paquet **[build]**.
- Toute l'API Hytale utilisée doit être vérifiée dans les sources décompilées (`build/vineflower/hytale-server`), jamais supposée. Les découvertes vont dans `docs/research/plugin-b-api.md`.

## 2. Conception des classes : pas de classe fourre-tout

- **Une classe a une seule responsabilité**, que l'on peut décrire en une phrase sans « et ».
- **Taille** :
  - 300 lignes par fichier au maximum visé ;
  - **400 lignes au maximum absolu** **[build : `checkFileSizes`]** ;
  - 40 lignes par méthode au maximum ;
  - 5 paramètres par méthode au maximum (au-delà, un record).
- Quand une classe grossit, on en **extrait des collaborateurs** dans la même modification, sans ajouter une méthode de plus. Exemples :
  - `ColonyManager` délègue aux actions par domaine (huttes, ordres, requêtes, fenêtres) ;
  - `BuilderAI` délègue à `BuilderStock`, `BuilderWalker`, `BuildCompletion`.
- `*Manager` est réservé aux classes qui possèdent le cycle de vie d'une collection. Les autres classes portent un nom de rôle précis (`WorkOrderActions`, `CitizenViews`, `PathStuckHandler`…).
- Visibilité : le moins possible (package-private par défaut). Les champs sont `final` sauf raison.
- Les valeurs sont des `record`. Les absences s'expriment avec `Optional`, jamais un `null` renvoyé, sauf les « reste » des ports, documentés.
- Pas d'interface à une seule implémentation, sauf un port. Pas de fabrique ni de config pour une valeur qui ne change jamais.

## 3. Style

- Identifiants, Javadoc et commentaires de code en anglais. Documentation `docs/` et messages de commit : voir § 9.
- 4 espaces, 120 colonnes, UTF-8, fin de ligne LF **[.editorconfig]**. Pas d'import `*`.
- Le formatage est celui de palantir-java-format **[build : `spotlessCheck`]**. On lance `./gradlew spotlessApply` avant chaque commit.
- Imports (Google Java Style) : un bloc d'imports statiques, une ligne vide, puis un bloc d'imports normaux, chacun trié dans l'ordre ASCII **[build : `spotlessCheck`]**.
- Complexité, classes fourre-tout, code mort et code fragile sont vérifiés par PMD (`config/pmd/ruleset.xml`) **[build : `pmdMain`]**.
- Les commentaires expliquent **pourquoi**, jamais ce que le code dit déjà. Javadoc courte sur l'API publique du cœur.
- Constantes : `static final` en `UPPER_SNAKE`, avec leur unité dans le nom ou la Javadoc (`DELAY_TICKS`).

## 4. Robustesse

- **Un port ne lève jamais d'exception.** Chunk non chargé, joueur hors ligne ou entrée inconnue renvoient 0, vide, `false` ou le reste complet. Le premier échec est journalisé, les suivants en FINE.
- Les gestionnaires d'événements du plugin attrapent `RuntimeException`, annulent l'événement si c'est possible et journalisent en SEVERE.
- Tout tourne **sur le thread du monde**, sans synchronisation. Ce qui en sort (préchargement de prefabs…) est documenté et ne touche pas l'état du jeu.
- Aucun état de jeu ne doit pouvoir bloquer pour toujours. Chaque attente a une sortie : délai, anti-blocage, abandon.
- Pas d'allocation par tick dans les chemins chauds. Les parcours sont bornés (`SCAN_LIMIT`).

## 5. Persistance

- Format JSON versionné (`schemaVersion`). Toute évolution passe par `MigrationChain` et garde une fixture de l'ancienne version.
- La lecture est tolérante : une clé absente prend sa valeur par défaut, une valeur inconnue sa valeur de repli. On ne plante jamais sur une vieille sauvegarde.
- Une sauvegarde incohérente est **réparée au chargement** (`ColonySerializer.heal`), puis marquée à réécrire.

## 6. Fidélité à MineColonies

- Chaque système porté cite sa source MineColonies dans sa Javadoc (`MC EntityAIStructureBuilder.placeBlock`).
- Constantes et formules reprises telles quelles, en ticks (le cœur tourne à 20 ticks/s).
- Un écart (contrainte Hytale, bug de MC corrigé, ajout demandé) porte un commentaire `Deviation from MC: …` et figure dans la spec du sous-projet.
- Référence : `github.com/ldtteam/minecolonies`, branche `version/main`, et les analyses de `docs/research/`.

## 7. Textes et fenêtres

- Tout texte vu par un joueur passe par une clé de traduction présente dans **en-US et fr-FR** (`plugin/src/main/resources/Server/Languages/*/hycolony.lang`), avec des paramètres `{p0}`, `{p1}`…
- Une traduction imbriquée dans une autre (`param(key, Message)`) s'affiche sur `.TextSpans`, **jamais** sur `.Text`. Sur un bouton : une clé complète par variante.
- Les fenêtres affichent des **vues** du cœur (records immuables). Chaque bouton appelle une action du cœur, qui vérifie les permissions puis ré-affiche la vue. Les fichiers `.ui` copient les motifs vanilla (voir les `.ui` des assets).
- Les identifiants d'assets Hytale ne vivent que dans `hycolony/id-map.json`. Les plans de bâtiments sont dans `hycolony/styles.json`.

## 8. Tests

- **TDD** : le test qui échoue d'abord, puis le code. Tout changement de comportement du cœur a un test. Tout bug corrigé a le test qui le reproduit.
- Noms de tests : phrases en camelCase (`waitingBuilderTakesToolPlacedInHutAndResumes`).
- `./gradlew build` **vert avant chaque commit** : tests du cœur, compilation du plugin, `checkFileSizes`, `spotlessCheck` et PMD. Le build échoue sur une erreur de formatage, une violation PMD ou un fichier trop long.
- Les listes d'exceptions `gradle/file-size-allowlist.txt` et `config/pmd/known-violations.txt` ne peuvent que **rétrécir** : on retire une ligne quand le fichier est découpé ou nettoyé, on n'en ajoute jamais.
- Le plugin n'a pas de tests unitaires. Il est vérifié par `/hycolony selftest` et `docs/TESTING.md`, que l'utilisateur déroule en jeu.

## 9. Processus

1. **Nouveau système** : conception validée par l'utilisateur, spec (`docs/superpowers/specs/`), plan (`docs/superpowers/plans/`), puis implémentation.
2. **Petite modification** : courte conception validée dans la conversation, puis implémentation.
3. **Chaque modification, même petite, est relue par un agent indépendant** (correction, tests, taille et responsabilité des classes) avant d'annoncer qu'elle est prête. Les corrections de la relecture sont relues à leur tour.
4. On ne lance **jamais** le serveur Hytale. C'est l'utilisateur qui le relance et qui teste en jeu. On lui donne le feu vert seulement quand le build est vert et la relecture terminée.
5. Commits :
   - messages en anglais, au format `type(module): description` (`feat(core):`, `fix(plugin):`, `docs:`, `refactor(core):`, `test(core):`) ;
   - un commit par unité logique, qui compile seul ;
   - `git add <chemins>` explicites, jamais `-A`. `config.json` et `config.json.bak` (réglages locaux) ne sont jamais commités ;
   - les lignes de fin de commit sont celles demandées par la session en cours.
6. La documentation du projet (`docs/`) est en français. L'utilisateur est francophone : on lui répond en français.
