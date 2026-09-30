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

## Après HyLens : ce que HyLens a montré dans HyColony (2026-09-30)

À traiter à la fin du plan, à la demande de l'utilisateur :

- [ ] **Livreur face à une caisse.** Sans tâche, le livreur vise une case à côté du bloc de l'entrepôt posée sur une caisse : `BlockApproach.standable` accepte un meuble (`UNBREAKABLE`) comme sol, et la navigation de Hytale n'y monte pas. À 3 blocs, l'anti-blocage abandonne (« assez près ») : l'alerte 10 se déclenche à chaque retour. Correction proposée : n'accepter comme sol qu'un bloc plein (`SOLID`), en TDD, avec une caisse à côté d'une hutte.
- [ ] **Pics de TPS pendant la récolte du fermier.** `/world perf` : 30 TPS en moyenne, mais un écart de ±13 à ±27 sur 1 et 5 min, qui suit les récoltes et les tâches du fermier. Mesurer ce que fait `FieldPass` à chaque tick de récolte avant de corriger.
- [ ] **Entrepôt « plein » alors qu'il a des coffres (vu en jeu, 2026-09-30).** L'entrepôt ne range que dans sa hutte et dans ses contenants enregistrés (`Building.containers`). Un coffre n'est enregistré que si le constructeur ou le collage créatif le pose depuis le plan (`BuilderBlockWork.place`, `PasteQueue.register`) ; `StructureScan.needsWork` saute une case déjà conforme, donc un coffre déjà en place n'est jamais enregistré. Vérifié (`docs/research/mc-warehouse-containers.md`) : MC enregistre aussi le contenant et le banc d'une case déjà conforme au plan, à chaque étape de pose (Structurize `AbstractBlueprintIterator.iterateWithCondition` l. 109-111 appelle `triggerSuccess` sans poser), et le collage créatif fait de même. Correction : enregistrer ce que le plan met sur une case trouvée déjà satisfaite, dans `StructureScan` et `PasteQueue`, aux étapes de pose seulement, en TDD.
- [ ] **`ViolationWatch` confirme trop tôt une seconde incohérence pareille** (vu en relecture de la tâche 13) : sa clé (code, citoyen, clé du message) ne distingue pas deux requêtes sans résolveur d'un même citoyen, ni surtout toutes les requêtes sans citoyen d'une colonie, qui partagent la même clé (le cas courant : bâtisseur, fermier, livraisons, requêtes enfants) ; la seconde hérite du `firstSeen` de la première et se confirme sans attendre 5 s. Distinguer par occurrence, ou ajouter l'objet concerné (le jeton de la requête) à `Violation`. Quand `Violation` portera l'identité de la requête, `NewAlerts` de HyLens, qui copie cette clé, devra la prendre aussi.
- [x] **Citoyen tombé dans un trou de 2 blocs** (vu en jeu, 2026-09-30) : la marche monte maintenant 2 blocs, comme un joueur de Hytale (commit a056398b, `docs/TESTING.md` point 223, rangé avec HyLens qui l'a montré).
- [ ] **Amender la spec § 8, essai 6** (accord de l'utilisateur requis) : `/plugin reload` de HyColony échoue tant que D-1 n'est pas corrigé (piège 1.9) ; l'essai 240 attend cet échec.
- [ ] **Garde-fou, session déverrouillée** : ajouter HyLens (`hylens/plugin/src/main/resources/Server/Languages`, `hylens`) aux listes DIR et NAME de `.claude/skills/add-lang-key/SKILL.md`.
- [ ] **À vérifier en jeu** : où vise `/hylens send` pendant un suivi, la caméra de spectateur étant loin du corps du joueur (`docs/TESTING.md` point 232).
- Le fermier qui travaille une case à 4 blocs est fidèle à MC (`walkToSafePos`, `FieldPass.CELL_RANGE`) : rien à faire.

## Écarts constatés à l'implémentation

- **Tâche 1 :** `ColonyWorld` n'est pas créé vide. Une interface vide serait du code mort, et elle entrerait dans l'empreinte de l'API sans rien porter. La tâche 2 le crée avec ses lectures.
- **Tâche 1 :** un paramètre d'`ApiText` est une `String` ou un `ApiText`, rien d'autre. Un nombre est formaté par l'appelant, comme dans le `Msg` du cœur. Changer ce domaine, dans un sens ou dans l'autre, est une rupture : le restreindre casse l'addon qui construit un `ApiText`, l'élargir casse celui qui l'affiche.
- **Tâche 2 :** une requête est décrite par des données (genre, objet, nombre, demandeur), pas par un `ApiText` : le cœur ne connaît pas le nom des objets dans la langue du joueur. `RequestSnapshot` et `ColonyWorld.requests` sont `@Experimental` jusqu'au graphe des requêtes (V2), puisqu'il leur manque encore le type et les niveaux d'un outil. `ColonySummary` gagne le propriétaire, et `BuildingSnapshot` sa colonie.
- **Tâche 3 :** les 9 événements stables sont rares (une pose de hutte, un jour qui commence) : leurs éditeurs les publient sans tester `hasListeners`. Ce test vaut pour les événements fréquents de la tâche 4, un par transition ou par marche.
- **Tâche 3 :** `ColonyManager.deleteColony` prend un `UUID` et non un `Optional<UUID>` : une colonie n'est supprimée que par un joueur, et le paramètre optionnel faisait dépasser à la classe le seuil de couplage de PMD.
- **Tâche 3 :** `HutActions.place` et `onRemoved` prennent un `UUID` : ils ne sont appelés que pour un joueur. Seul le retrait d'un bâtiment périmé a pour cause la colonie. Un gain de niveau porte aussi son joueur : une copie créative ou une fondation copiée à un niveau est son fait, pas celui du bâtisseur.
- **Tâche 3 :** le test « une action demandée au nom de `Actor.Colony` est refusée » attend la tâche 5, la première à ajouter des actions.
- **Tâche 3 :** un abonnement fermé oublie tout de suite son écouteur, donc rien d'un addon déchargé n'est retenu. Le bus retire l'abonnement lui-même au prochain `subscribe`, `post` ou `hasListeners` de ce type.
- **Tâche 4 :** les signes vitaux vivent dans un sous-paquet `citizen/vitals` : `CitizenVitals`, `EndedWalk`, les événements de débogage du cœur, l'écouteur de marche et `AiWatch`. Le plan prévoyait un seul fichier dans `citizen`, qui en compte déjà 11.
- **Tâche 4 :** le « niveau d'anti-blocage » est remplacé par la dernière action de l'anti-blocage et son tick. Une action postérieure au début de la marche en cours suffit pour l'invariant 10.
- **Tâche 4 :** les transitions sont relevées en comparant, après chaque tick, l'état de l'IA et le nom d'étape du métier (une constante d'énumération, donc aucune allocation), au lieu d'un crochet dans la machine d'états. Les métiers restent intacts.
- **Tâche 4 :** une fin de marche porte le statut de la navigation (arrivée, bloquée) et une raison de plus, `TELEPORTED`.
- **Tâche 4 :** l'historique (`CitizenHistory`, dans `citizen/vitals`) note aussi les actions de l'anti-blocage. Son détail est un `Msg` du cœur, que `app/api` traduira en `ApiText`, avec des clés `debug.history.*` dans `hycolony.lang`. Plusieurs suivis peuvent tenir le même citoyen : l'historique dure tant que l'un d'eux reste ouvert, puis il est libéré. Les flèches des textes sont `->`, car `→` n'est dans aucune police de l'interface Hytale.
- **Tâche 4 :** le test « diagnostic actif et aucun addon abonné : une transition ne construit aucun événement » n'est pas écrit. `EventBus` est `final` et ne compte pas ses publications, et l'historique ne voit pas le bus : un tel test ne pourrait pas échouer. La garantie tient à la garde `hasListeners` devant chaque publication, et à un historique qui ne passe jamais par le bus.
- **Tâche 4 :** `JobAI` gagne un troisième crochet, `servesQueueHead()`. L'invariant 3 doit savoir quelles étapes travaillent sur la tête de file sans rendre `CourierState` public. `waiting()` est aussi implémenté par `BuilderAI` et `FarmerAI` (`NEEDS_ITEM`) : sinon, tout bâtisseur qui attend ses matériaux plus de 5 minutes serait signalé.
- **Tâche 4 :** l'invariant 1 ne vise que les marches finies par la navigation. Une marche simple (`NAV_ENDED`) est signalée à plus de `ARRIVAL_RANGE` de sa cible ou à un autre étage. Une marche vers un bloc finit en `IN_REACH`, une nouvelle raison de fin : sa navigation s'arrête par construction dans la portée de ce bloc (MC `walkCloseToXNearY`), donc seul l'étage compte, et c'est le cas du toit. Une fin `CLOSE` ou `TELEPORTED` est dans la portée de l'appelant, et un abandon relève de l'invariant 10.
- **Tâche 4 :** l'invariant 7 devient « la vérification de réapparition a trouvé un emplacement chargé pour un citoyen sans corps, mais aucun corps n'y est apparu ». `FailedRespawns` le note dans `CitizenManager`. Un corps déchargé avec son chunk n'est pas signalé, car MC l'attend aussi. « Corps sans citoyen » est retiré : `onBodyLoaded` fait déjà disparaître un tel corps.
- **Tâche 4 :** l'invariant 4 ne garde que « requête assignée ou en cours sans résolveur » (spec § 5). « Absente de toute file de livreur », que cite `debug-mod.md`, attend le graphe des requêtes. L'invariant 9 compte `REPEATED_FAILURES = 2` exceptions depuis le chargement du citoyen.
- **Tâche 4 :** une `Violation` du cœur porte un `Msg` (clés `debug.violation.*`), traduit en `ApiText` à la tâche 4e. `DebugText` écrit les positions et les distances de la même façon pour l'historique et les invariants. `Invariants.check` est un instantané, et certaines ruptures durent un instant par conception : un livreur ne voit sa tâche annulée qu'à son étape suivante, jusqu'à 25 ticks plus tard. `ViolationWatch` ne garde donc que celles vues à chaque contrôle pendant `CONFIRM_TICKS = 100`. Les simulations de construction et de livraison contrôlent à chaque tick, et échouent en `@AfterEach` sur une violation qui dure. Le test de performance vérifie qu'un bâtisseur qui a posé 900 blocs n'est pas figé à la fin. Une trace (invariants 1, 7, 9 et 10 : une fin de marche, un échec) est confirmée tout de suite, car la marche suivante peut l'effacer. Le code 7 s'appelle `RESPAWN_FAILED`.
- **Tâche 4 :** l'étape figée (invariant 2) compte aussi le dernier progrès du métier : le compteur d'actions de MC (`Job.actionsDone`, un bloc posé, une case bêchée, une fabrication). Sans lui, un bâtisseur reste en `BUILDING_STEP` pendant tout un seau et serait signalé. Les signes vitaux de marche passent dans `WalkVitals`, pour laisser `CitizenVitals` sous le seuil de méthodes de PMD. Le bâtisseur attend aussi légitimement une case de son plan dans un chunk déchargé.
- **Tâche 4 :** `CitizenManager` expose `ai(id)` plutôt que `jobAi(id)` : `JobAI` ferait dépasser le seuil de couplage de PMD. Limite connue : un livreur qui ne prend jamais les tâches de l'entrepôt attend « légitimement » (`waiting()`), ce qui masque l'étape figée.
- **Tâche 4, pour les tâches 4e et 5 :** `check` ne tourne jamais depuis un rappel du système de requêtes. La pause de colonie arrêtera l'IA : si l'horloge du cœur continue, l'étape figée apparaîtra après 5 minutes de pause. L'invariant 9 compte depuis le chargement du citoyen, il ne retombe donc jamais : HyLens doit le montrer comme tel. `ViolationWatch` suppose des contrôles toutes les quelques ticks : un contrôle isolé (`/hylens check`) ne confirme que les traces. La tâche 4e fixe la cadence, et l'allocation d'une `HashMap` par contrôle.
- **Tâche 4e :** `CitizenDebugSnapshot` ne porte pas les alertes du citoyen. Elles se lisent dans `check(colony)`, filtré par citoyen, car leur confirmation demande des appels réguliers. Le « niveau d'anti-blocage » y est la dernière action et son tick. « La case d'arrêt » et « le statut de navigation » sont dans `lastWalkEnd`, qui reprend le record `WalkEnded` de l'événement. S'y ajoutent le tick de lecture et le début de l'état de l'IA et de l'étape, pour que le HUD affiche des durées.
- **Tâche 4e :** `DebugAccess.track` rend un `Optional<Subscription>`, vide pour un citoyen inconnu (§ 4.1 : une absence est un `Optional`). `check` garde une `ViolationWatch` par colonie : il confirme d'un appel à l'autre. Sa `HashMap` n'est allouée qu'à la demande, à chaque appel de `check`, et son coût se mesure avec le HUD (§ 10). La veille d'une colonie supprimée reste en mémoire, sans fausse confirmation possible, car un id n'est jamais réutilisé dans une session.
- **Tâche 4e :** chaque transition d'une requête passe par `RequestStore.changeState`, qui prévient un écouteur (`RequestManager.setStateListener`), et seulement quand l'état change (une annulation pose CANCELLED deux fois). L'état lu dans une sauvegarde ne publie rien : `RequestSerializer` le pose directement, sans test qui le fige. Une requête réassignée au chargement publie ses vraies transitions. `RequestStatePoster`, dans `colony`, publie `RequestStateChanged` sur le bus, seulement quand quelqu'un écoute : placé dans `Colony`, il lui faisait dépasser les seuils de couplage et de méthodes de PMD. `api.debug` est `@Experimental` au niveau du paquet et de chaque type. Les genres de l'historique et les codes des violations y sont les noms des énumérations du cœur.
- **Tâche 5 :** `TickingTransition.isOneTime()` devient `shouldRemove()`, le nom de MC (`IStateMachineOneTimeEvent`). Une transition unique peut le redéfinir pour durer jusqu'à ce qu'elle ait fini, comme `CommandCitizenTriggerWalkTo`.
- **Tâche 5 :** `walkTo` est porté dans `citizen/CommandedWalk`, branché par `CitizenAI.walkTo`. Les écarts sont détaillés dans la spec (§ 5 et § 9) et dans la Javadoc de `CommandedWalk` : une marche par citoyen, un marcheur neuf à chaque demande, l'IA du métier relancée à neuf après la marche et après `teleport`, le citoyen sans métier qui marche aussi, la téléportation seulement vers une case vérifiée (sinon abandon). La marche passe par `CitizenWalkReports` : elle apparaît dans les signes vitaux et l'historique. Relecture : l'IA du métier gardait ses marcheurs et se croyait arrivée après la marche ; renvoyer la même cible réutilisait la marche abandonnée.
- **Tâche 5 :** les actions de `DebugAccess` sont refusées à `Actor.Colony` (clé `debug.refused.colony`) et à un joueur ni op ni gérant de la colonie (MC `IMCColonyOfficerCommand`, désormais `ColonyAccess.isOfficer`, partagé avec la suppression d'une colonie ; clé `permission.denied`, déjà existante). Les droits sont vérifiés avant de chercher le citoyen, comme MC. `walkTo` et `teleport` demandent un corps chargé et vivant. `forceLeisure` pose `LEISURE_TICKS`. `respawnBody` fait apparaître le nouveau corps avant de retirer l'ancien, et garde l'ancien s'il échoue. MC n'a d'équivalent ni pour `forceLeisure` ni pour `respawnBody` (spec § 9). Hors du périmètre : `StuckHandler` n'inflige pas les 20 % de dégâts de MC en cas de blocage complet, un écart plus ancien à noter dans sa Javadoc.
- **Tâche 6 (cœur) :** `ColonyClockState` (`app/api`) tient l'horloge d'un monde. `pause(owner)` est refusé tant qu'un autre propriétaire tient la pause, et pour toujours à un propriétaire levé : sa levée peut arriver avant sa pause, qui ne serait alors jamais levée. Le pont donne donc une clé propre à chaque instance de plugin. `step` est refusé quand la colonie tourne, et plafonné à `MAX_STEP` pas en attente. `resume` jette les pas non joués. `release(owner)` vient de n'importe quel fil, dans un ensemble concurrent. La levée du suivi à l'arrêt du propriétaire est déjà testée à la tâche 4 (`trackingClosedFromAnotherThreadStopsTheHistory`). `CitizenAI` gagne `WANDER_TIMEOUT_TICKS = 120 * 20`, le `MIN_TP_DELAY` de MC : la flânerie n'attend plus une marche en cours au-delà, comptés depuis la première décision qui la voit (écart, spec § 9). L'horloge du cœur ne compte que ses propres ticks, donc la pause ne fait pas apparaître l'étape figée. En revanche, l'autosauvegarde, rythmée par cette horloge, demande un compteur à part côté plugin.
- **Tâche 6 (plugin) :** le contrat (`plugin/api`) contient `HyColonyApi`, son `HyColonyApiHolder`, `ColonyClock` et `ColonyWorldEvent`, scellé en `ColonyWorldStarted` et `ColonyWorldStopped`. Le pont (`plugin/bridge`) contient `ApiBridge`, `BridgeClock`, `OwnerBinding` (marqueur `IBaseEvent<Void>` jamais publié), `ApiThreads` et `WorldAnnouncer`. `subscribe(owner, world, …)` rend un `Optional<Subscription>`, vide là où HyColony ne tourne pas (§ 4.1 : une absence est un `Optional`). `ColonyWorldStopped` est annoncé sur le fil qui retire le monde, qui peut ne plus être le sien. `WorldRuntime.runCore(due)` joue les ticks permis par `ColonyClockState`, arrête les corps en pause (`HytaleCitizenBodies.haltAll`, sans `Frozen`) et sauvegarde sur un compteur à part. Pour rester sous le seuil de couplage de PMD, `WorldPorts` sort de `WorldRuntime` (les adaptateurs de construction), et le pont garde lui-même le `CoreColonyWorld` de chaque monde. `/hycolony selftest` gagne l'étape `ApiSelfTest` : api installée, appel refusé hors du fil, horloge, et aller-retour `bodyOf` puis `citizenOf`. Relecture : les pas tournaient tous dans un tick serveur, puis l'arrêt des corps annulait leur marche, et l'anti-blocage les aurait téléportés ; ils tournent maintenant au rythme du temps (`ColonyClockState.allow` rend min(pas, dus)), et les corps ne sont arrêtés qu'une fois les pas épuisés. `ColonyWorldStarted` n'est annoncé que pour un monde activé, et `ColonyWorldStopped` que pour un monde annoncé démarré. `ColonyTickSystem` attrape les exceptions de `runCore`. Fermer un abonnement retire son lien des tâches d'arrêt du propriétaire. `WorldAnnouncer` garde un drapeau fermé par abonnement, et n'avertit qu'une fois. La course entre une inscription et l'arrêt du propriétaire est documentée dans `OwnerBinding`.
- **Tâche 7 :** les empreintes sont lues par réflexion dans le démon Gradle (`build-logic/.../ApiSignatures.kt`, sans dépendance). Elles comptent aussi les annotations de type (le `@Nullable` de jspecify) et les membres hérités d'une classe mère non publique. Elles ne voient pas la valeur des constantes, les valeurs par défaut des annotations ni `@Deprecated`. Comme le format dépend du JDK, `apiDump` et `apiCheck` refusent un démon dont le JDK n'est pas `java_version`. Le contrôle des projets embarqués est une tâche à part, `checkBundled`, dont dépend le `jar`. Les commits des fichiers `.claude/` suivent la convention du dépôt, `chore(guardrails)`.
- **Tâche 8 :** `hylens/id-map.json` attend la tâche 9, la première à nommer un asset (le mode de jeu `Spectator`). Le contrôle `CheckPackAssets` du pack attend le premier fichier `Common/` ou `.ui` (tâche 10 ou 12) : le pack ne tient aujourd'hui que deux `.lang`. `gradle.properties` ne change pas, car l'identité de HyLens est dans son `build.gradle.kts`, comme pour HyVanilla. Les versions exactes du manifeste viennent de `project.version`. Le cœur gagne `ApiCompatibility` : HyLens refuse une API d'une autre majeure **ou d'une autre mineure**, car il se sert des parties `@Experimental`, qui peuvent changer d'une mineure à l'autre. Le manifeste épingle la version du mod, qu'un build de dev ne change pas ; ce contrôle lit la version de l'API dans le jar de HyColony. `/hylens selftest` montre les lignes « api version » et « api » (et « api class » depuis la tâche 15). Depuis la passe finale, `HyLensPlugin.setup` applique ce refus : une autre version de l'API laisse HyLens éteint, avec un SEVERE qui dit les deux versions.
- **Tâche 9 :** le suivi se fait avec le mode de jeu `Spectator` de Hytale, nommé dans `hylens/id-map.json`. Notre propre mode (clics pour changer de citoyen, ouvrir le menu) est en V2. Le nom est l'argument « reste de la ligne » de `/hylens watch <nom>`, et la forme sans argument, le citoyen visé, une variante d'usage de zéro mot : Hytale choisit une variante à son nombre exact de mots, et un nom de citoyen en a plusieurs (§ 36 de `plugin-b-api.md`). Un argument optionnel de Hytale s'écrirait `--citizen=…`. Un corps mourant est refusé, comme par `/spectate`. Le nom est le nom complet, sans tenir compte de la casse, sinon une partie unique du nom, parmi les citoyens des colonies du monde (`CitizenPicker`). Un corps refait ou déchargé passe la caméra en vue libre (Hytale) : le suivi et l'historique continuent, et `/hylens watch` remet la caméra sur le nouveau corps. HyLens ne s'y réaccroche pas seul. La reprise de la colonie à la déconnexion de l'opérateur qui l'a mise en pause arrive avec l'horloge (tâche 12) : avant, HyLens ne met rien en pause. `/hylens unwatch` sort du mode spectateur même sans suivi, car ce mode est sauvegardé avec le joueur et survit à un redémarrage.
- **Tâche 10 :** le HUD est un panneau de lignes : le cœur de HyLens les construit en `ApiText` (`WatchHudView`, au plus 20 : 10 d'état, les 5 derniers changements, les 3 premières alertes, et leurs titres ; le panneau fait exactement 20 lignes de 20 px), et le plugin les traduit en messages sur `.TextSpans`. Ajouts à la spec § 6.2, demandés en jeu : le métier du citoyen, et le bloc à sa cible de marche et celui du dessous, lus dans le monde par HyLens (`TargetCells`) ; c'est ce qui a montré le livreur qui vise une case au-dessus d'une caisse. La file nomme ses 3 premières requêtes, chacune par ses 8 premiers caractères comme les alertes de HyColony, puis « , ... ». Le panneau n'est pas ouvert par `/hylens watch` : `WatchHudSystem` (renommé `WatchRefreshSystem` à la tâche 11) le montre, le rafraîchit et le retire toutes les 0,5 s selon `Watches`, donc un arrêt du suivi l'enlève sans autre branchement ; à l'arrêt de HyLens, `shutdown` le retire de chaque joueur, sinon il resterait figé. Les alertes sont celles de `check(colony)` pour ce citoyen, confirmées d'un rafraîchissement à l'autre. Les secondes sont celles de l'horloge du cœur, tronquées. `checkPackAssets` est branché mais ne lit que les objets (`Server/Item/Items`) : HyLens n'en a pas, et ses `.ui` ne sont validés que par l'éditeur de l'utilisateur. Coût mesuré en jeu (2026-09-30) : panneau affiché, `/world perf` donne 30,0 TPS, écart ±0 sur 10 s et sur 1 min ; les pics vus ensuite suivent les récoltes du fermier, pas le HUD. La cadence de 0,5 s est gardée. Le fond est porté par chaque ligne, pour suivre leur nombre, et le panneau fait 640 de large, car la ligne de la dernière marche était coupée à 480.
- **Tâche 11 :** le cœur de HyLens décide des formes (`WatchShapes`) : une ligne du corps à la cible et une sphère sur la cible, rouges quand HyColony confirme pour ce citoyen l'alerte « anti-blocage » (invariant 10, marche en cours ou dernière), ou « marche finie loin » (invariant 1) pour une marche vers cette même cible : l'alerte 1 parle de la dernière marche finie, pas de celle qui commence (règles de HyColony plutôt qu'une règle propre à HyLens) ; un cube sur la case où s'est arrêtée la dernière marche, et un sur la hutte de travail (la « zone de travail » de la spec). Les dessins sont envoyés au seul opérateur, avec la même lecture que le HUD : le système de rafraîchissement devient `WatchRefreshSystem`, dans le paquet `watch` (ex-`hud`), et la lecture passe dans `Watched`. Chaque forme dure 0,65 s pour un rafraîchissement de 0,5 s. Les couches à cocher arrivent avec le menu (tâche 12) : d'ici là, tout est dessiné.
- **Tâche 12, en deux commits :** 12a, la page ; 12b, l'horloge (pause, pas × N, reprise, et reprise à la déconnexion de l'opérateur qui a mis en pause). « Envoyer ici » reste à la tâche 14 (bloc visé, coordonnées, carte). 12a : `/hylens menu` ouvre une page (`MenuPage`, dessinée par `MenuRender`) : les colonies du monde, les citoyens de la colonie choisie (métier, état d'IA, étape, nombre d'alertes confirmées), et pour le citoyen choisi « Suivre » (la page se ferme une fois le suivi commencé), « Forcer un loisir », « Téléporter vers moi » (sur la case de l'opérateur) et « Refaire le corps ». Les actions sont demandées au nom de l'opérateur (`Actor.Player`) : HyColony laisse agir un opérateur ou un gestionnaire de la colonie, et refuse quiconque aurait reçu /hylens sans l'être. Chaque bouton porte l'id de sa colonie ou de son citoyen, pas sa place : une liste changée entre l'affichage et le clic ne fait pas choisir la ligne voisine. Les alertes du menu viennent de `check(colony)` : une trace se voit tout de suite, un état après 5 s d'observation (un suivi, ou deux clics espacés), comme le veut `ViolationWatch`. Les couches (cible, case d'arrêt, lieu de travail, rouge si échec) filtrent les dessins de `WatchShapes`. Les choix de chaque opérateur (`Menus`, `MenuState`) vivent en mémoire tant que HyLens tourne, et sont oubliés à sa déconnexion. La vue (`MenuViews`) est construite dans le cœur, testée avec un faux `ColonyWorld`.
- **Tâche 12b :** l'horloge est une rangée du menu : l'état (« en marche » ou « en pause »), « Pause », « - » et « + » pour le pas (1 à 10 ticks, gardé dans `MenuState`), « Avancer » et « Reprendre » (`MenuClock`). HyColony tient la pause pour le plugin HyLens (`ColonyClock.pause(owner)`) ; `Pauses` retient, monde par monde, l'opérateur qui l'a demandée, et sa déconnexion fait reprendre les mondes qu'il a mis en pause (sauf si un autre opérateur a repris la pause depuis). À l'arrêt de HyLens, HyColony lève la pause de son propriétaire. « Reprendre » reprend quel que soit le demandeur, comme `ColonyClock.resume`.
- **Tâche 13 :** `/hylens check` et « Contrôler maintenant » disent dans le chat les incohérences confirmées de chaque colonie du monde (`CheckReport`) ; un appel isolé ne confirme que les traces, comme le veut `ViolationWatch`. Le contrôle automatique (`/hylens autocheck` ou le bouton du menu, gardé dans `MenuState`) tourne toutes les 2 s (40 ticks, sous les 100 de la confirmation) pour les opérateurs qui l'ont demandé (`AutoCheckSystem`) : chaque colonie est contrôlée une fois par tour, et chaque opérateur n'apprend que les incohérences nouvelles (`NewAlerts` : par code, citoyen et clé du message comme `ViolationWatch`, plus la position, pas par les valeurs du texte, qui changent ; deux incohérences pareilles sont comptées, la seconde est nouvelle). Activer ou couper le contrôle (`AutoCheck.toggle`) oublie ce qu'il a dit. Ces appels réguliers sont aussi ce qui confirme les états pour le menu et le HUD. L'accès à HyColony passe par `HyColonyAccess`, qui rend vide une fois HyColony arrêté ; les états partagés de HyLens sont regroupés dans `LensParts`.
- **Tâche 14 :** « Envoyer ici » demande `walkTo` au nom de l'opérateur (`SendHere`), pour le citoyen suivi, sinon celui choisi dans le menu (`SendTarget.who`). Trois façons : `/hylens send` vers le bloc visé à 64 blocs au plus (`TargetUtil.getTargetBlock`), la case tapée dans le menu (pré-remplie avec les pieds de l'opérateur à l'ouverture, puis gardée : chaque clic porte les champs, `MenuSend`), et la carte (`/hylens send --map` ou « Par la carte », `MapSend`). `walkTo` prend la case où le corps se tient, pas le sol : le bloc visé et le sol de la carte donnent `standingOn` (le bloc + 1). La carte : le filtre de `PacketAdapters.registerInbound(PlayerPacketFilter)` lit sur le fil réseau ; il ne consomme `TeleportToWorldMapPosition` que si l'opérateur est armé (`ArmedMaps.use`, qui désarme : une seule utilisation), puis passe au fil du monde comme `IWorldPacketHandler`. Le sol est trouvé comme le fait `GamePacketHandler.handleTeleportToWorldMapPosition` : `getChunkReferenceAsync(index, 32)`, puis `HeightmapColumn.getHeight` (le plus haut bloc opaque ; `Integer.MIN_VALUE` : pas de sol). Désarmé à la déconnexion ; `deregisterInbound` au `shutdown` (il lève si le filtre n'est pas inscrit, d'où la garde). `MenuChecks` et le contrôle « aucun citoyen choisi » de `MenuActions` sortent de `MenuPage`, qui dépassait le couplage de PMD.
- **Tâche 15 :** le guide des auteurs d'addons est `api/README.md` (en anglais). L'installation des cinq mods est dans le `README.md` racine (section « Install ») et en tête de `docs/TESTING.md`. Les sept essais de production sont les points 235 à 241 de `docs/TESTING.md`, et leur protocole est au § 28.6 de `plugin-b-api.md`, **à dérouler par l'utilisateur**. L'essai 4 (une seule copie de l'API) est automatisé : `/hylens selftest` charge `ApiVersion` par le chargeur de HyColony et la compare à la sienne (`api class`). Reste à l'utilisateur, en session déverrouillée : ajouter HyLens (`hylens/plugin/src/main/resources/Server/Languages`, `hylens`) aux listes DIR et NAME de `.claude/skills/add-lang-key/SKILL.md`, un fichier garde-fou. **Écart avec la spec § 8, essai 6** : `/plugin reload` de HyColony ne peut pas finir « sans exception », parce que son second `setup()` échoue (piège 1.9, audit D-1, non corrigé) ; l'essai 240 attend donc cet échec, et la spec reste à amender avec l'accord de l'utilisateur. L'arrêt de HyLens fait maintenant sortir ses spectateurs du mode spectateur (`OperatorExit.leaveSpectators`) : sans lui, `/hylens unwatch` disparaissait avec HyLens.
- **Passe finale (2026-10-01)** : quatre relectures de tout HyLens (robustesse du plugin, cœur et tests, textes et fenêtres, conformité). Corrigé :
  - les incohérences sans citoyen (une requête sans résolveur, le cas courant) comptent dans l'alerte de la colonie, au menu (`MenuView.ColonyRow.alerts`) ; le HUD, qui montre un citoyen, garde les siennes seulement ;
  - `HyLensPlugin.setup` refuse une API d'une autre version (voir tâche 8) ; les deux systèmes et les clics du menu attrapent aussi `LinkageError` (`MenuEvents`, créé avec chaque page pour que sa classe soit chargée avant l'arrêt), qui arrêterait le fil du monde ; un `setup` refusé ne laisse rien à arrêter (`started`) ;
  - l'arrêt de HyLens ferme les menus ouverts (`MenuCommand.closeOpen`), comme le HUD et le mode spectateur ; la déconnexion et l'arrêt passent par `OperatorExit`, qui refait les oublis et la reprise de la pause sur le fil du monde du joueur, après un clic ou un tour déjà en file ;
  - un suivi dont HyColony ne connaît plus le citoyen, sa colonie supprimée, s'arrête et le dit (`WatchLoss`, `LostWatch`) ; un citoyen mort, lui, reste connu de HyColony (son corps revient) : le suivi continue, caméra en vue libre ; un suivi d'un autre monde ne passe plus avant le citoyen choisi pour « Envoyer ici » (`SendTarget.who`) ;
  - le loisir n'est jamais montré négatif ; une cible hors de la hauteur du monde se lit comme du vide ; `watch.noTarget` reçoit la portée en paramètre ;
  - tests : l'historique de moins de 5 entrées, un oubli qui ne touche que son opérateur (`NewAlerts`, `Menus`, `ArmedMaps`), la violation d'un citoyen à une position ; `Pauses.by`, inutilisé, est retiré.

  Laissé, mineur : de petites décisions sont encore dans le plugin (`MenuClicks`, le filtre des alertes de `Watched`, les textes de `MenuClock.run`) ; `CitizenPicker` ne sait pas départager deux citoyens de même nom dans deux colonies ; la caméra n'est pas raccrochée seule au corps refait (`/hylens watch` le fait) ; le paquet `command` est à sa limite de 15 fichiers : un sous-paquet `menu` au prochain ajout.
