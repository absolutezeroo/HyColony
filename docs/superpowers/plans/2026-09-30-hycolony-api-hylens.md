# API HyColony et HyLens (cinquième mod de débogage)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal :** une API publique et versionnée de HyColony (`:api`, `dev.hycolony.plugin.api`), et HyLens, le mod de débogage visuel qui ne passe que par elle.

**Spec :** `docs/superpowers/specs/2026-09-30-hycolony-api-hylens-design.md`. Recherches : `docs/research/hycolony-api.md`, `docs/research/debug-mod.md`. Modèle d'un nouveau mod : `2026-09-29-hyvanilla-split.md`.

## Contraintes globales

- **Tests et relectures.** TDD pour `core/`, `:api` et `hylens/core`. `./gradlew build` vert avant chaque commit. Relecture `hycolony-reviewer` pour chaque tâche, et ses corrections relues à leur tour. `mc-fidelity-checker` pour les tâches qui portent MC (4 et 5).
- **Paquets pleins (15 fichiers, `checkFileSizes`).** Aucun nouveau fichier dans `kernel/port`, `request` ni `plugin/.../adapter` :
  - le contrôle du fil est un `BooleanSupplier`, et le plugin passe `world::isInThread` ;
  - un record du cœur lié aux requêtes va dans `request/model`.

  Il faut vérifier le compte des fichiers avant d'en ajouter un.
- **Garde-fous.** Les tâches 1 à 6 n'en touchent aucun. La tâche 7 demande une **session déverrouillée** (`HYCOLONY_GUARDRAILS_UNLOCKED=1`) : l'utilisateur la lance lui-même, ce qui reconfirme son accord (spec, en tête). Les tâches 8 à 15 viennent après la 7, car HyLens a besoin de `checkModApis`.
- **Changements d'API.** L'API ne change qu'avec un `apiDump` commité, dès que la tâche 7 l'a rendu disponible.
- **Règles de jeu.** Aucune ne change, sauf `walkTo`, portée de MC.
- **Hors de ce plan.** Du code non commité d'une autre session peut traîner dans l'arbre (par exemple `WorkerMachine.java`). On ne l'indexe jamais, et on vérifie `git status` avant chaque commit.

## Tâche 1 : le projet `:api`

**Files :** `settings.gradle.kts`, `api/build.gradle.kts` (nouveau), `api/src/main/java/dev/hycolony/api/*`, `api/src/test/java/dev/hycolony/api/ApiArchitectureTest.java`, `core/build.gradle.kts`, `plugin/build.gradle.kts`

- [ ] `include(":api")`. `api/build.gradle.kts` : `hy.java-core`, `group = "dev.hycolony"`, aucune dépendance de projet.
- [ ] Types de base (spec § 4.2), avec une Javadoc anglaise et `@since 1.0` :
  - `ColonyRef`, `CitizenRef`, `Pos`, `Vec`, `ApiText` ;
  - `Actor` (scellé, avec ses trois cas dès maintenant : `Player`, `Plugin`, `Colony`) et `ActionResult` (scellé) ;
  - `Subscription`, `ApiVersion` ;
  - `@Experimental` (rétention `RUNTIME` ; sur un type, une méthode ou un paquet) ;
  - `ColonyWorld` : reporté à la tâche 2 (voir « Écarts constatés »).
- [ ] `ApiArchitectureTest`, une liste blanche : les classes de `dev.hycolony.api..` ne dépendent que de `java..`, `org.jspecify..` et `dev.hycolony.api..`.
- [ ] `core` : `api(project(":api"))`. `plugin` : `:api` embarqué dans le jar de HyColony, comme `:core`.
- [ ] Commit `feat(api): the api project, its references and results`.

## Tâche 2 : les lectures

**Files :** `api/.../read/*`, `core/.../app/api/*` (nouveau), tests `core/src/test/.../app/api/*`

- [ ] Tests d'abord :
  - les instantanés d'une colonie de test (colonies, citoyens, bâtiments, requêtes) ;
  - une liste copiée ne bouge plus quand le cœur change ;
  - un id inconnu rend un `Optional` vide ;
  - un appel hors du fil lève `IllegalStateException` (contrôle par `BooleanSupplier`).
- [ ] `ColonySummary`, `CitizenSnapshot`, `BuildingSnapshot` et `RequestSnapshot` (des données, `@Experimental`, spec § 4.2).
- [ ] `ColonyWorld` et son implémentation `app/api/CoreColonyWorld`.
- [ ] Commit `feat(core): the api's snapshots of colonies, citizens, buildings and requests`.

## Tâche 3 : le bus et les événements stables

**Files :** `core/.../kernel/event/EventBus.java`, `api/.../event/*`, `core/.../app/api/ApiEvents.java`, `core/.../colony/ColonyEvents.java`, les éditeurs de ces événements (`HutActions`, `ColonyFoundation`, `ColonyManager`, `WorkManager`…), `core/.../kernel/ai/TickRateStateMachine.java`

- [ ] Tests d'abord pour `EventBus` :
  - `subscribe` rend un désabonnement idempotent, sûr entre fils, qui ne lève jamais : il pose un drapeau, et le retrait a lieu sur le fil du monde ;
  - désabonnement dans un rappel ; abonnement dans un rappel (copie à l'écriture : aucune allocation par distribution) ;
  - un abonné qui lève une `RuntimeException` ou un `LinkageError` n'arrête pas les autres ; l'erreur est journalisée une fois en WARNING puis en FINE ;
  - `hasListeners(Class)` ;
  - sans abonné, aucun événement n'est construit.
- [ ] La cause (`Actor cause`, spec § 4.2) est transmise depuis ses éditeurs, y compris le retrait par la baguette et `HutActions.place`. Tests :
  - `ColonyCreated`, `BuildingPlaced` et `WorkOrderCreated` portent le joueur ;
  - un bâtiment périmé retiré porte `Actor.Colony` ;
  - une action demandée au nom de `Actor.Colony` est refusée.
- [ ] Les 9 événements existants traduits dans `dev.hycolony.api.event`. `ColonyWorld.subscribe(Class, Consumer)` rend une `Subscription`.
- [ ] Retirer `TickRateStateMachine.history()` et son test, que la tâche 4 remplace.
- [ ] Commit `feat(core): the api's stable events, with safe unsubscription and no cost without listeners`.

## Tâche 4 : le diagnostic

**Files :**
- navigation : `core/.../kernel/nav/{BodyWalker,StuckHandler,WalkListener}.java` ;
- machines d'états : un crochet de transition dans `core/.../kernel/ai/TickRateStateMachine.java` ou `core/.../job/work/WorkerMachine.java` ;
- IA et métiers : `core/.../job/JobAI.java`, `core/.../logistics/courier/DeliverymanAI.java`, `core/.../citizen/{CitizenAI,CitizenManager}.java` ;
- signes vitaux : un fichier dans `core/.../citizen` ;
- diagnostic : `core/.../app/diagnostics/*` (nouveau) ;
- tests : `core/src/test/.../testing/FakeBodies.java` ;
- contextes : `CourierContext`, `CraftingWorkContext`, `BuilderWalker` ;
- API : `api/.../debug/*`.

- [ ] `FakeBodies` : un mode « la navigation finit ailleurs », qui arrête le corps à une position donnée.
- [ ] Tests d'abord :
  - `WalkEnded` porte sa raison (proche, fin de navigation, abandon, téléportation) et la distance ;
  - `StuckAction` part à chaque relance, téléportation et abandon ;
  - `StuckHandler` expose son niveau.
- [ ] `WalkListener` (`kernel/nav`), injecté par les contextes qui connaissent le citoyen.
- [ ] Signes vitaux par citoyen, dans `citizen`, alloués une seule fois et mis à jour sur place par les fonctionnalités elles-mêmes, jamais par le bus :
  - tick de la dernière transition du métier ;
  - dernière fin de marche ;
  - nombre d'exceptions d'IA ;
  - niveau d'anti-blocage.
- [ ] Historique :
  - un anneau de 20 entrées `HistoryEntry` par citoyen suivi, dont le détail est un `ApiText` ;
  - il note les transitions de l'IA et du métier, et les marches ;
  - `track(ref)` le démarre et rend une `Subscription` ; la fermer l'arrête ;
  - un citoyen non suivi n'alloue rien (test) ;
  - diagnostic actif et aucun addon abonné : une transition ne construit aucun événement (test).
- [ ] Crochets sur `JobAI` :
  - `waiting()`, faux par défaut ;
  - la file du métier, une liste vide par défaut ;
  - les deux implémentés par `DeliverymanAI`, sans rendre publics `CourierState` ni `CourierTasks`.
- [ ] Invariants de la v1 (spec § 5), avec `JOB_STEP_STALE_TICKS = 6000`. Chaque code a un test qui échoue sur l'état fautif et passe sur l'état sain. `check(colony)` rend les `Violation`. Le contrôle tourne aussi dans les simulations de construction et de livraison.
- [ ] Les événements `@Experimental` de `api.debug` : `CitizenStateChanged`, `JobStateChanged`, `WalkEnded`, `StuckAction` et `RequestStateChanged`, ce dernier avec son record du cœur dans `request/model`.
- [ ] `CitizenDebugSnapshot`, et les méthodes `inspect` et `history` de `DebugAccess`.
- [ ] Commit `feat(core): citizen history, vital signs, walk-end and stuck events, invariant checks`.

## Tâche 5 : `walkTo` et les actions de débogage

**Files :** `core/.../kernel/ai/*` (`shouldRemove`), `core/.../citizen/CitizenAI.java` et un collaborateur de marche commandée (si la taille l'exige), `core/.../app/api/CoreDebugAccess.java`, tests

- [ ] Tests d'abord pour `shouldRemove` (MC `TickingOneTimeEvent`) : une transition unique reste active tant qu'elle n'a pas dit « terminé ».
- [ ] Tests d'abord pour `walkTo` (MC `CommandCitizenTriggerWalkTo`) :
  - un citoyen avec un métier marche jusqu'à 4 blocs, pendant 3 minutes au plus, avec un `BodyWalker` ;
  - il reste ensuite sur place 100 ticks (`Deviation from MC`), puis reprend son IA ;
  - un citoyen sans métier reçoit un simple `moveTo` ;
  - une nouvelle demande remplace la marche en cours.
- [ ] Tests d'abord pour `forceLeisure`, `teleport` et `respawnBody` : `NotFound` pour un citoyen inconnu, `Unavailable` sans corps.
- [ ] `mc-fidelity-checker` sur `walkTo`.
- [ ] Commit `feat(core): the api's debug actions (walk to, leisure, teleport, respawn)`.

## Tâche 6 : le côté Hytale de l'API

**Files :** `core/.../app/api/*` et ses tests (l'état de l'horloge, commit `feat(core)` à part), `plugin/src/main/java/dev/hycolony/plugin/api/*` (le contrat), `plugin/src/main/java/dev/hycolony/plugin/bridge/*` (l'implémentation), `plugin/.../HyColonyPlugin.java`, `plugin/.../ColonyTickSystem.java`, `plugin/.../WorldRuntime(s).java`, `docs/research/plugin-b-api.md`

- [ ] `HyColonyApi` (le contrat) : `get()`, qui lève `IllegalStateException` quand il est vide, ainsi que `world(World)`, `citizenOf(Ref, accessor)`, `bodyOf(CitizenRef)`, `subscribe(owner, …)`, `track(owner, ref)`, `subscribeWorlds(owner, …)` et `clock(World)`. `world` rend `Optional.empty()` quand le runtime est désactivé. `get()` et `subscribeWorlds` sont sûrs entre fils.
- [ ] `bridge` :
  - le holder est rempli au `setup` et vidé au `shutdown` ;
  - le contrôle du fil vaut `world::isInThread` ;
  - la fermeture des abonnements est accrochée au registre d'événements du propriétaire (`EventRegistry.register(EventRegistration)`, qui demande une classe marqueur `IBaseEvent`). Vérifier dans les sources avant d'écrire.
- [ ] `ColonyClock` :
  - l'état de l'horloge vit dans le cœur (`app/api`), en TDD : propriétaire, drapeau sûr entre fils, pas restants bornés par `MAX_STEP`. La pause n'est jamais sauvegardée. À l'arrêt du propriétaire, la levée depuis le fil de déchargement ne fait que poser le drapeau, lu au tick suivant sur le fil du monde. Un test du cœur, avec un contrôle de fil simulé, vérifie que l'arrêt depuis un autre fil lève la pause et le suivi. Aucun test unitaire dans le plugin (CLAUDE.md § 8) ;
  - `ColonyTickSystem` lit seulement `paused` et consomme les pas ; le pont lui transmet l'arrêt du propriétaire ;
  - `step(n)` borné à `MAX_STEP = 10` ;
  - la pause arrête l'IA mais pas l'autosauvegarde ;
  - elle désactive la cible de marche des corps, sans `Frozen`. Le mécanisme est à vérifier dans les sources Hytale avant d'écrire.
- [ ] `plugin-b-api.md` : noter ce qui a été vérifié.
- [ ] Commit `feat(plugin): the Hytale side of the api (entry point, bodies, clock)`.

## Tâche 7 : garde-fous (session déverrouillée)

**Files :**
- `build-logic/src/main/kotlin/hy.java-checks.gradle.kts`, et la tâche `apiDump`/`apiCheck` dans `build-logic` ;
- `api/api.txt`, `plugin/api.txt` ;
- `CLAUDE.md`, `AGENTS.md` ;
- `.claude/agents/hycolony-{implementer,reviewer}.md`, `.claude/skills/{add-lang-key,hytale-api}` ;
- `.claude/hooks/guard.js` et son banc de test.

- [ ] `modApis` :
  - `"dev.hycolony." to listOf("dev.hycolony.api.", "dev.hycolony.plugin.api.")` et `"dev.hylens." to emptyList()` ;
  - refuser un fichier dont le paquet ne commence pas par le groupe de son projet.

  `NullAway:AnnotatedPackages` : `dev.hylens`.
- [ ] `hy.hytale-mod` : le `jar` échoue si un projet résolu par `bundled` n'a pas le groupe du mod. `bundled` est transitif, et rien d'autre n'empêche HyLens d'embarquer une seconde copie de `:api`.
- [ ] `apiDump` et `apiCheck` : les empreintes des signatures publiques de `:api` et de `dev.hycolony.plugin.api`, sans les éléments `@Experimental`. `apiCheck` entre dans `check`. On génère et on commite les premières empreintes.
- [ ] `CLAUDE.md` : § 1 (cinq mods, les cœurs purs, la politique de l'API), § 7, § 8, § 9.6. Puis `AGENTS.md`, les agents et les skills (spec § 7).
- [ ] `guard.js` : protéger le `config.json` de `hylens/plugin` et celui de `vanilla/plugin`. `node .claude/hooks/test/run.js` doit être vert.
- [ ] Commits séparés : `build: …`, `docs: …`, `chore(claude): …`.

## Tâche 8 : squelette de HyLens

**Files :** `settings.gradle.kts`, `hylens/core/build.gradle.kts`, `hylens/plugin/build.gradle.kts`, `hylens/plugin/src/main/java/dev/hylens/plugin/HyLensPlugin.java`, `hylens/plugin/src/main/resources/` (manifeste, `Server/Languages/*/hylens.lang`, `hylens/id-map.json`), `hylens/core/src/test/.../ArchitectureTest.java`, `.gitignore`, `gradle.properties`

- [ ] `:hylens-core` :
  - `hy.java-core`, `group = "dev.hylens"` ;
  - `compileOnly(:api)`, `testImplementation(:api)` ;
  - `ArchitectureTest` : aucun import `com.hypixel`.
- [ ] `:hylens-plugin` :
  - `hy.hytale-mod`, `group = "dev.hylens"`, `modId = "hylens"`, jar `HyLens` ;
  - `compileOnly` de `:plugin`, `:api` et `:blockui`, et `bundled(:hylens-core)` ;
  - `Dependencies` : HyColony et HyBlockUI, en versions exactes ;
  - les projets pointent vers `hylens/core` et `hylens/plugin` (gradle/gradle#847).
- [ ] Les commandes `/hylens` sont réservées aux opérateurs. `/hylens selftest` vérifie que l'API répond dans le monde du joueur.
- [ ] Vérifier que le workspace de dev charge les cinq mods.
- [ ] Commit `feat(hylens): the fifth mod's skeleton, reaching HyColony through its api`.

## Tâches 9 à 14 : HyLens V1 (spec § 6)

Chaque tâche suit le même déroulé :
- la logique d'affichage dans `hylens/core`, en TDD ;
- l'adaptateur dans `hylens/plugin` ;
- les textes en en-US et fr-FR (skill `add-lang-key`) ;
- une ligne dans `docs/TESTING.md` ;
- une relecture, puis un commit.

- [ ] **9. Suivre** :
  - `/hylens watch [citoyen]` et `/hylens unwatch`, la caméra en 3ᵉ personne ;
  - `track` démarre à l'entrée et s'arrête à la sortie, et aussi à la déconnexion de l'opérateur ;
  - à cette déconnexion, la colonie reprend si c'est lui qui l'avait mise en pause.
- [ ] **10. HUD « ce qu'il pense »** : `CustomUIHud`, rafraîchi toutes les 10 ticks du cœur (0,5 s). On mesure son coût **[in-game]** avant de fixer la fréquence.
- [ ] **11. Dessins pour l'opérateur seul**, sur le modèle de `WildernessDebugShapeSystem` :
  - ligne du corps à la cible, rouge après un `WalkEnded` loin ;
  - sphère sur la cible ;
  - cube sur la case d'arrêt et sur la zone de travail.

  `docs/TESTING.md` : `/npc debug set VisPath` retire la plaque de nom à chaque changement de drapeaux.
- [ ] **12. Menu (page HyBlockUI)** :
  - colonies et citoyens, avec leurs alertes ;
  - Suivre ;
  - les couches ;
  - les actions ;
  - l'horloge, avec N ≤ 10 pour le pas à pas.
- [ ] **13. Incohérences** : `/hylens check` et le contrôle périodique en option. Les résultats vont dans le menu et le HUD.
- [ ] **14. Envoyer ici** :
  - le bloc visé et les coordonnées du menu ;
  - la carte : on intercepte `TeleportToWorldMapPosition` en mode armé seulement (`PacketAdapters.registerInbound`, puis `world.execute`), et on cherche le sol ;
  - l'état armé est sûr entre fils, et se désarme à la déconnexion ;
  - `deregisterInbound` au `shutdown` de HyLens **[in-game]**.

## Tâche 15 : docs et essais en production

- [ ] Le guide des auteurs d'addons, **en anglais**, à côté de l'API (`api/README.md`). Il couvre l'entrée, le fil, les instantanés, les événements, le désabonnement, les actions, `@Experimental` et la politique de version, avec HyLens comme exemple.
- [ ] `plugin-b-api.md` § 28 et `docs/TESTING.md` : les sept essais avec les jars de production (spec § 8), dont le même `identityHashCode` d'un type de l'API vu des deux mods.
- [ ] La doc d'installation : sans HyColony, le serveur s'arrête (spec § 8).
- [ ] Vérification finale : `./gradlew build` vert, `apiCheck` vert, et les cinq mods démarrent ensemble.

## Écarts constatés à l'implémentation

- **Tâche 1 :** `ColonyWorld` n'est pas créé vide. Une interface vide serait du code mort, et elle entrerait dans l'empreinte de l'API sans rien porter. La tâche 2 le crée avec ses lectures.
- **Tâche 1 :** un paramètre d'`ApiText` est une `String` ou un `ApiText`, rien d'autre. Un nombre est formaté par l'appelant, comme dans le `Msg` du cœur. Changer ce domaine, dans un sens ou dans l'autre, est une rupture : le restreindre casse l'addon qui construit un `ApiText`, l'élargir casse celui qui l'affiche.
- **Tâche 2 :** une requête est décrite par des données (genre, objet, nombre, demandeur), pas par un `ApiText` : le cœur ne connaît pas le nom des objets dans la langue du joueur. `RequestSnapshot` et `ColonyWorld.requests` sont `@Experimental` jusqu'au graphe des requêtes (V2), puisqu'il leur manque encore le type et les niveaux d'un outil. `ColonySummary` gagne le propriétaire, et `BuildingSnapshot` sa colonie.
