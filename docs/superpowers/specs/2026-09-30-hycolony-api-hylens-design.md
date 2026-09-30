# API HyColony et HyLens : une API publique, et un cinquième mod de débogage qui ne passe que par elle

Décisions de l'utilisateur, le 2026-09-30 :
- une API publique et versionnée, et un 5ᵉ mod de débogage qui n'utilise qu'elle. En choisissant cette option, l'utilisateur a donné son accord pour modifier les garde-fous du § 7 : la description de l'option le disait. Il le reconfirmera en lançant la session déverrouillée de la tâche 7 ;
- l'API dans un projet `:api` séparé ;
- un appel hors du fil du monde lève une exception ;
- le mod s'appelle **HyLens** (`dev.hylens`) ;
- le guide des auteurs d'addons est écrit en anglais ;
- la V1 de HyLens reprend celle de la recherche, plus le menu, « envoyer ici » et les actions de débogage. La vue dans les yeux du citoyen reste en V2.

Recherches : `docs/research/debug-mod.md` (le débogage) et `docs/research/hycolony-api.md` (l'API). Elles nomment encore le mod « HyColony Debug » : c'est cette spec qui fait foi. Modèle d'un mod séparé : `2026-09-29-hyvanilla-design.md`.

## 1. Objectif

- **Ouvrir HyColony aux autres mods.** Ils doivent pouvoir lire, écouter des événements et agir, sans jamais voir l'implémentation. L'API de MineColonies dépend de son cœur : 47 de ses 651 fichiers importent l'implémentation. Ses addons importent aussi des classes internes et cassent à chaque version (`hycolony-api.md` § 1.1, § 1.4). Ici, c'est le compilateur qui l'interdit.
- **Déboguer en voyant.** L'utilisateur veut « le maximum de visuel » en temps réel. Il a demandé si c'était possible d'avoir un vrai menu, une caméra où l'on entre dans le PNJ pour voir à quoi il pense, et d'ouvrir la carte pour l'envoyer quelque part. C'est **HyLens**, premier client de l'API : s'il lui manque quelque chose, c'est l'API qui est incomplète.
- **Critères de réussite :**
  - HyLens ne compile qu'avec les paquets `api` de HyColony **[build : `checkModApis`]** ;
  - l'API ne voit que le JDK, jspecify et elle-même **[build : dépendances de `:api`, ArchUnit en liste blanche]** ;
  - tout changement de l'API stable se voit dans le diff **[build : `apiCheck`]** ;
  - avec HyLens V1, les deux bugs du livreur du 2026-09-29 se voient en jeu en moins d'une minute. Le toit : l'alerte « marche finie loin » et son dessin. Le livreur figé : l'étape du métier, la file et l'historique.

## 2. Dépendances

```
HyBlockUI ← HyDomum ← HyColony → HyVanilla
    ↑                    ↑
    └────── HyLens ──────┘
```

(`A ← B` : B dépend de A.)

- HyLens **dépend obligatoirement** de HyColony et de HyBlockUI (`Dependencies`, versions exactes). HyColony ne connaît pas HyLens.
- HyLens ne voit que `dev.hycolony.api`, `dev.hycolony.plugin.api` et `dev.hyblockui.api` **[build : `checkModApis`]**.
- Ses projets déclarent `group = "dev.hylens"` : `checkModApis` reconnaît un mod à son groupe Gradle. Sans cela, HyLens hériterait de `dev.hycolony` et pourrait importer tout HyColony.

## 3. Modules

| Module | Contenu |
|---|---|
| `api/` (`:api`, `dev.hycolony.api`) | L'API, en Java pur. `hy.java-core`, sans dépendance de projet. `:core` l'expose (`api(project(":api"))`). Elle est embarquée dans le jar de HyColony, et **dans aucun autre** : HyLens la voit en `compileOnly`. |
| `core/` | Implémente l'API dans `app/api`, et le diagnostic dans `app/diagnostics` (§ 5). La couche application voit toutes les fonctionnalités, et personne ne la voit (`FeatureDependenciesTest`, sans nouvelle arête). Les fonctionnalités lui parlent par `kernel` ou par le bus. |
| `plugin/` | `dev.hycolony.plugin.api` ne contient **que** le contrat : le holder, des interfaces et des records. L'implémentation (pont, horloge, abonnements) vit dans `dev.hycolony.plugin.bridge`, qui n'est pas importable. |
| `hylens/core` (`:hylens-core`, `dev.hylens.core`) | Java pur et testé. Construit ce qu'affichent le HUD, le menu et les dessins à partir des instantanés de l'API. `compileOnly(:api)`, `testImplementation(:api)`, avec son `ArchitectureTest` (aucun import `com.hypixel`). |
| `hylens/plugin` (`:hylens-plugin`, `dev.hylens.plugin`) | La caméra, le HUD, les dessins, les pages, la carte et les commandes `/hylens`. `compileOnly` de `:plugin`, `:api` et `:blockui`, et `bundled(:hylens-core)`. Son pack contient `hylens.lang` (en-US et fr-FR), ses `.ui` et `hylens/id-map.json` (le mode de jeu `Spectator`, les formes). |

## 4. L'API v1

### 4.1 Règles

| Sujet | Règle |
|---|---|
| Entrée | `HyColonyApi.get()`, un holder statique. Il est rempli au `setup` de HyColony, vidé au `shutdown`, et lève `IllegalStateException` quand il est vide. La dépendance dure de HyLens garantit l'ordre de chargement. |
| Disponibilité | Par monde : `world(World)` rend `Optional<ColonyWorld>`, vide si HyColony n'y tourne pas ou y est désactivé (`WorldRuntime.enabled`). `subscribeWorlds(owner, listener)` annonce `ColonyWorldStarted` et `ColonyWorldStopped` : c'est un abonnement global, puisqu'aucun `ColonyWorld` n'existe encore au démarrage d'un monde. |
| Fil | Chaque appel vérifie le fil du monde, sinon il lève `IllegalStateException`, comme `Store.assertThread`. **Écart voulu** avec CLAUDE.md § 4 (« un port ne lève jamais ») : l'API n'est pas un port, et un appel hors du fil est une faute de programmation. `world.execute` reste à la charge de l'addon. **Exceptions**, sûres entre fils : `HyColonyApi.get()`, `subscribeWorlds` (appelé au démarrage d'un addon, sur le fil principal) et `Subscription.close()`, que Hytale appelle depuis le fil de déchargement. |
| Lecture | Des instantanés : records immuables, listes copiées, pris sur le fil du monde. Une absence s'exprime par `Optional`. |
| Identité | `ColonyRef(String world, int colonyId)`, où `world` est le nom du monde. `CitizenRef(ColonyRef, int citizenId)`. `Pos(int x, int y, int z)` pour un bloc, `Vec(double x, double y, double z)` pour un corps. Un identifiant de requête en texte. Jamais `BodyId`, qui n'est qu'un compteur de session. |
| Événements | Des records publiés après le changement, sur le fil du monde. |
| Désabonnement | `Subscription.close()` est idempotente, sûre entre fils, et ne lève jamais : elle pose un drapeau, la distribution saute les abonnements fermés, et le retrait réel a lieu sur le fil du monde. `subscribe(owner, …)` ferme l'abonnement à l'arrêt du plugin propriétaire. |
| Distribution | Les abonnés sont gardés dans un tableau remplacé à chaque abonnement ou désabonnement (copie à l'écriture) : on peut s'abonner ou se désabonner dans un rappel, sans allocation à chaque distribution. Chaque abonné est isolé : une `RuntimeException` ou un `LinkageError` (addon déchargé) est journalisé une fois en WARNING puis en FINE, et les autres abonnés sont servis. |
| Coût | Aucun événement n'est construit sans abonné à son type (`EventBus.hasListeners`, comme `hasListener()` de Hytale). |
| Actions | Elles rendent un `ActionResult` scellé : `Done`, `Refused(ApiText)`, `NotFound`, `Unavailable`. `Actor` est scellé en trois cas, fixés dès la v1 : `Player(uuid)`, vérifié contre les permissions de la colonie ; `Plugin(name)`, dont le plugin appelant répond ; `Colony`, la colonie elle-même (son IA, un bâtiment périmé retiré). `Colony` n'est qu'une cause : une action demandée en son nom est refusée. |
| États tenus | Un addon qui met la colonie en pause ou suit un citoyen le fait au nom d'un plugin propriétaire. Quand ce plugin s'arrête, la colonie reprend et le suivi cesse. Cette levée est lancée depuis le fil de déchargement et ne lève jamais : pour la pause, elle pose un drapeau sûr entre fils, lu au tick suivant sur le fil du monde ; pour le suivi, elle ferme sa `Subscription`, avec les garanties de `close()`. La pause n'est jamais sauvegardée. |
| Textes | `ApiText(key, params)` : une clé de traduction et ses paramètres. Tout texte destiné à un joueur, y compris le détail d'une alerte ou d'un historique, est un `ApiText`. |
| Stabilité | L'API suit son propre semver (`ApiVersion`), avec `@since` sur chaque type. Toute rupture passe à la version majeure : retirer ou renommer, ajouter une composante à un record, ajouter un cas à un type scellé (cela casse les `switch` exhaustifs des addons). `@Experimental` (rétention `RUNTIME` ; sur un type, une méthode ou un paquet) peut changer d'une version mineure à l'autre, et l'empreinte l'ignore. |

### 4.2 Contenu

- **`dev.hycolony.api`** : `ColonyWorld`, `ColonyRef`, `CitizenRef`, `Pos`, `Vec`, `ApiText`, `Actor`, `ActionResult`, `Subscription`, `ApiVersion`, `@Experimental`. `ColonyWorld` rassemble les lectures, `subscribe(Class, Consumer)` et `debug()`, cette dernière marquée `@Experimental`.
- **`dev.hycolony.api.read`** :
  - `ColonySummary` : ref, nom, centre (celui de la fondation), propriétaire, nombre de citoyens ;
  - `CitizenSnapshot` : ref, nom, métier, maison, lieu de travail, position du corps ;
  - `BuildingSnapshot` : colonie, type, position, niveau, construit ou non, style ;
  - `RequestSnapshot`, `@Experimental` jusqu'à son premier client (le graphe des requêtes, V2) : id, colonie, état, genre, objet, nombre, hutte et citoyen qui demandent, résolveur, parent, enfants. Ce sont des données, pas un texte : le cœur ne connaît pas le nom des objets dans la langue du joueur (le plugin les tire de `Item.getTranslationMessage()`), et un addon peut filtrer. Le livreur qui porte une requête est lu dans la file du métier, dans le diagnostic.
- **`dev.hycolony.api.event`** (stable) : les événements existants, traduits en références : `ColonyCreated`, `ColonyDeleted`, `BuildingPlaced`, `BuildingRemoved`, `BuildingLevelChanged`, `WorkOrderCreated`, `CitizenSpawned`, `DayStarted`, `NightFell`. `ColonyCreated`, `ColonyDeleted`, `BuildingPlaced`, `BuildingRemoved`, `BuildingLevelChanged` et `WorkOrderCreated` portent `Actor cause` dès la v1, car l'ajouter plus tard serait une rupture. La cause est le joueur, ou `Actor.Colony` quand aucun joueur n'agit (un bâtiment périmé retiré par `HutActions.place`, un niveau gagné par le bâtisseur). Le cœur la transmet depuis `HutActions`, `ColonyFoundation`, `ColonyManager`, `WorkManager` et le retrait par la baguette. `CitizenSpawned`, `DayStarted` et `NightFell` n'ont pas de cause.
- **`dev.hycolony.api.debug`** (tout `@Experimental`, car il expose des états internes sous forme de texte) :
  - événements : `CitizenStateChanged`, `JobStateChanged`, `WalkEnded` (cible, position, statut de navigation, raison, distance), `StuckAction` (action, position), `RequestStateChanged` ;
  - `CitizenDebugSnapshot` : l'état de l'IA, l'étape du métier (`JobAI.stateName()`, une chaîne, pour ne pas exposer les énumérations package-private comme `CourierState`), l'activité, la cible de marche, la case d'arrêt, le statut de navigation, le niveau d'anti-blocage, la file du métier, le loisir, les dernières transitions et les alertes ;
  - `HistoryEntry(long tick, kind, from, to, ApiText detail)` et `Violation(code, ApiText detail, citizen?, pos?)` ;
  - `DebugAccess` :
    - lecture : `inspect`, `history`, `check(colony)`, et `track(ref)`, qui rend une `Subscription` : la fermer arrête le suivi ;
    - actions : `walkTo`, `forceLeisure`, `teleport`, `respawnBody`.
- **`dev.hycolony.plugin.api`** :
  - `HyColonyApi` : `world(World)`, `citizenOf(Ref, accessor)` (par le `CitizenTag` du corps), `bodyOf(CitizenRef)`, `subscribe(PluginBase owner, World, Class, Consumer)`, `track(PluginBase owner, CitizenRef)` (le suivi lié au propriétaire), `subscribeWorlds(owner, listener)`, `clock(World)` ;
  - `ColonyClock` : `pause(owner)`, `step(n)`, `resume`, `paused`. `step` fait tourner des ticks du cœur au rythme du temps, `MAX_STEP = 10` en attente au plus, sur le modèle de `MAX_CATCH_UP`. Tous d'un coup, les corps ne bougeraient pas : ils ne sont arrêtés qu'une fois les pas épuisés ;
  - `ColonyWorldStarted` et `ColonyWorldStopped`.

### 4.3 Stabilité dans le build

- **`:api`** n'a aucune dépendance de projet. Sa règle ArchUnit est une liste blanche : ses classes ne dépendent que de `java..`, `org.jspecify..` et `dev.hycolony.api..`. `hy.java-core` lui donne Gson en `compileOnly`, et la règle l'interdit.
- **`checkModApis`** :
  - `"dev.hycolony." to listOf("dev.hycolony.api.", "dev.hycolony.plugin.api.")` ;
  - `"dev.hylens." to emptyList()` ;
  - un fichier dont le paquet ne commence pas par le groupe de son projet est refusé.
- **`apiDump` et `apiCheck`** (`build-logic`, sans nouvelle dépendance) écrivent et comparent deux empreintes, `api/api.txt` et `plugin/api.txt` : les signatures publiques hors `@Experimental`. `apiCheck` fait partie de `check`. Un changement d'API passe donc par un `apiDump` commité, visible dans le diff.
- **Plus tard**, quand des addons extérieurs existeront : un jar `hycolony-api` publié, japicmp contre la dernière version, et la version du mod qui suit le semver de l'API.

## 5. Ce que HyColony gagne (cœur, TDD)

- **Événements sans coût.** `EventBus` gagne `hasListeners(Class)` et un désabonnement (§ 4.1). Chaque publication teste d'abord qu'un abonné existe. `TickRateStateMachine.history()`, qui alloue une chaîne à chaque transition et n'est lu que par un test, est **remplacé** par l'historique ci-dessous, pas doublé.
- **Historique horodaté.** Un anneau de 20 entrées par citoyen **suivi** : transitions de l'IA et du métier, marches, en ticks. Un citoyen non suivi n'alloue rien.
- **Ni l'un ni l'autre ne passe par le bus.** Sinon le diagnostic serait toujours abonné, et chaque transition construirait ses événements. Les fonctionnalités les tiennent sur place : les transitions par un crochet de la machine d'états (`TickRateStateMachine` ou `WorkerMachine`), les marches par `WalkListener`. Le bus ne sert qu'aux addons.
- **Signes vitaux.** Un petit objet par citoyen, dans le paquet `citizen` (que `job`, `logistics`, `construction`, `crafting` et `farming` voient déjà), alloué une seule fois et mis à jour sur place. Il garde des primitives toujours tenues, pour les invariants :
  - tick de la dernière transition du métier ;
  - dernière fin de marche (distance, raison) ;
  - nombre d'exceptions d'IA ;
  - niveau d'anti-blocage.
- **`WalkEnded` et `StuckAction`.** Un observateur est injecté dans `BodyWalker` par les contextes qui connaissent le citoyen (`CourierContext`, `CraftingWorkContext`, `BuilderWalker`). `StuckHandler` expose son niveau. `FakeBodies` gagne un mode « la navigation finit ailleurs ».
- **Invariants** (`app/diagnostics`). La v1 reprend les numéros 1 à 4, 7, 9 et 10 de `debug-mod.md` § 5 :
  - marche finie à plus de 2 blocs de sa cible, ou un étage plus haut ou plus bas ;
  - étape du métier inchangée depuis `JOB_STEP_STALE_TICKS = 6000` (5 minutes), une constante propre à HyColony. Les attentes légitimes en sont exclues : l'IA du métier les déclare par `JobAI.waiting()`, faux par défaut. Un livreur sans tâche qui attend à l'entrepôt attend légitimement, comme chez MC ;
  - file du livreur incohérente. L'invariant la lit par un crochet par défaut sur `JobAI`, implémenté par `DeliverymanAI`, pour ne pas rendre publics `CourierState` et `CourierTasks` ;
  - requête sans résolveur ;
  - citoyen sans corps ;
  - exceptions d'IA à répétition ;
  - anti-blocage à la téléportation ou à l'abandon.

  Ils tournent aussi dans les simulations de test.
- **`walkTo`** (MC `CommandCitizenTriggerWalkTo`) :
  - `kernel/ai` porte `shouldRemove`, pour qu'une transition unique dure jusqu'à l'arrivée ;
  - le citoyen marche jusqu'à 4 blocs de la position (`BlockApproach.walkToSafePos`), pendant 3 minutes au plus. Son IA attend pendant ce temps, puis encore 100 ticks, et reprend avec une IA de métier neuve ;
  - une nouvelle demande remplace la marche en cours, avec un marcheur neuf.

  **Deviation from MC :**
  - MC met sa navigation en pause 100 ticks, et son IA continue de tourner. Ici, c'est l'IA qui attend 100 ticks. Dans les deux cas, un chemin déjà en cours peut se terminer.
  - L'IA du métier repart à neuf, car nos marcheurs gardent un état (cible, case d'arrêt) que le `walkToPos` de MC n'a pas. La téléportation de débogage fait de même.
  - Un citoyen sans métier marche de la même façon, surveillé et borné. MC lui donne un simple `moveTo`, que surveille l'anti-blocage de sa navigation ; le nôtre n'en a pas. Une navigation laissée en cours après un abandon ou la limite relève de la limite de temps de la flânerie (tâche 6).
  - L'IA du métier est oubliée à la commande, mais sa vitesse et l'objet tenu restent : chez MC, la vitesse d'Agilité du livreur est un modificateur gardé avec le métier.
  - L'anti-blocage ne téléporte que vers une case vérifiée à côté de la cible, et abandonne sinon, ce qui finit la marche plus tôt. MC cherche une case sûre à 10 blocs et n'abandonne jamais.
  - Les décisions de l'IA attendent aussi : chez MC, `decideAiTask` est un événement qui passe d'abord. La limite de temps est testée avant la marche. Le pas tourne à chaque tick, alors que l'IA d'un citoyen MC tourne tous les 5 ticks.
- **Actions** `forceLeisure`, `teleport` et `respawnBody`, testées.
- **Horloge de colonie** : l'état vit dans le cœur (`app/api`), testé : propriétaire, drapeau sûr entre fils, pas restants bornés par `MAX_STEP`. La levée à l'arrêt du propriétaire ne fait que poser le drapeau, qui est lu au tick suivant sur le fil du monde. Le plugin se contente de lire `paused` et de consommer les pas dans `ColonyTickSystem`, et le pont lui transmet l'arrêt du propriétaire. La pause arrête l'IA, pas l'autosauvegarde. Les corps s'arrêtent : leur cible de marche est désactivée, un mécanisme à vérifier dans les sources Hytale. Le composant `Frozen` n'est pas employé, car il est sauvegardé avec le PNJ. À la reprise, `BodyWalker` relance la marche, puisque le statut n'est plus « en cours ». Une flânerie de `CitizenAI` finit au plus tard à `WANDER_TIMEOUT_TICKS`.

## 6. HyLens V1

Tout est réservé aux opérateurs. Les lots sont dans l'ordre du plan.

1. **Suivre un citoyen** : `/hylens watch [citoyen]` (sans argument, le citoyen visé), puis `/hylens unwatch`. La caméra suit à la 3ᵉ personne, par le mécanisme de `/spectate`. Le suivi démarre l'historique. Il cesse quand l'opérateur se déconnecte, et la colonie reprend si c'est lui qui l'avait mise en pause.
2. **HUD « ce qu'il pense »** pendant le suivi, rafraîchi toutes les 10 ticks du cœur (0,5 s) à partir de `CitizenDebugSnapshot` : état, étape, activité, cible, statut de navigation, anti-blocage, file, loisir, 5 dernières transitions, alertes. Coût à mesurer **[in-game]**.
3. **Dessins** pour l'opérateur seul :
   - ligne du corps à la cible, rouge si la marche a fini loin ;
   - sphère sur la cible ;
   - cube sur la case d'arrêt et sur la zone de travail ;
   - `/npc debug set VisPath` documenté pour le chemin A*. Attention : il retire la plaque de nom à chaque changement de drapeaux.

   Les dessins sont cachés par les blocs (vu en jeu).
4. **Menu** (page HyBlockUI) :
   - la liste des colonies et des citoyens, avec leur état et leurs alertes ;
   - Suivre ;
   - les couches à cocher : cible, case, zone, alertes ;
   - les actions : envoyer ici, forcer un loisir, téléporter, refaire le corps ;
   - l'horloge : pause, pas × N (N ≤ 10), reprise.
5. **Contrôle des incohérences** : `/hylens check`, un contrôle périodique en option, et les résultats dans le menu et le HUD.
6. **« Envoyer ici »** :
   - le bloc visé ;
   - des coordonnées dans le menu ;
   - **la carte** : en mode armé, l'entrée « Teleport » de la carte est interceptée. Elle ne donne que X et Z (`TeleportToWorldMapPosition`), alors HyLens cherche le sol lui-même. Le filtre de paquet est lu sur le fil réseau : l'état armé est sûr entre fils, et il se désarme à la déconnexion. Hors du mode armé, le paquet vanilla passe intact. `PacketAdapters.registerInbound` est statique : HyLens retire son filtre à son arrêt **[in-game]**.

**V2 et plus tard** (`debug-mod.md` § 8) :
- la vue dans les yeux du citoyen, en bascule : les réglages existent (`isFirstPerson`, `eyeOffset`), le rendu reste à vérifier **[in-game]** ;
- le mode de jeu HyLens, qui met les actions sur les clics ;
- le graphe des requêtes dans le monde ;
- les autres actions : remplir une requête, donner un objet, remettre à zéro les requêtes ;
- la relecture ;
- la carte de chaleur des blocages ;
- les repères de carte ;
- le temps d'IA par tick ;
- les événements de parité avec MC, dans des sous-paquets de `api.event`.

## 7. Garde-fous à modifier (session déverrouillée, tâche 7)

| Fichier | Changement |
|---|---|
| `CLAUDE.md` § 1 | Cinq mods et leur graphe. Les cœurs Java purs (`api/`, `hylens/core`). Les paquets `api` de HyColony. La politique de l'API (§ 4.1, § 4.3). |
| `CLAUDE.md` § 7, § 8, § 9.6 | `hylens.lang` et `hylens/id-map.json`. Les tests des nouveaux cœurs. L'exception du guide des addons en anglais. |
| `AGENTS.md` | La section des mods. |
| `build-logic/.../hy.java-checks.gradle.kts` | `modApis`, le refus d'un paquet hors du groupe de son projet, `NullAway:AnnotatedPackages` (`dev.hylens`), les tâches `apiDump` et `apiCheck`. |
| `.claude/agents/hycolony-implementer.md`, `hycolony-reviewer.md` | Les modules, la commande de build, les règles de l'API à relire. |
| `.claude/skills/add-lang-key`, `hytale-api` | La table des `.lang` et la liste des mods. |
| `.claude/hooks/guard.js` | Protéger `hylens/plugin/src/main/resources/config.json` et `vanilla/plugin/src/main/resources/config.json`, qui manque aujourd'hui. Tout `config.json.bak` est déjà protégé. |

Hors garde-fous : `settings.gradle.kts`, les `build.gradle.kts` des modules, `gradle.properties`, `docs/TESTING.md`, `plugin-b-api.md` § 28.

## 8. Tests

- `:api` : la règle ArchUnit en liste blanche, et `apiCheck`.
- Cœur, en TDD :
  - le bus : désabonnement depuis un autre fil et dans un rappel, abonnement dans un rappel, isolement d'un abonné qui lève, aucun événement construit sans abonné ;
  - diagnostic actif et aucun addon abonné : une transition ne construit aucun événement ;
  - l'arrêt du propriétaire, lancé depuis un autre fil, lève la pause et le suivi ;
  - l'historique, les signes vitaux, `WalkEnded`, `StuckAction` ;
  - chaque invariant, faux sur l'état fautif et vrai sur l'état sain ;
  - `walkTo`, avec `shouldRemove` ;
  - les actions ;
  - l'implémentation de l'API avec les `Fake*` : fil, instantanés, `Optional.empty()` pour un monde désactivé.
- `hylens/core` : ce qu'affichent le HUD, le menu et les dessins.
- Plugins : `/hylens selftest` et `docs/TESTING.md`, déroulés par l'utilisateur.
- **Avec les jars de production** (`plugin-b-api.md` § 28, `hycolony-api.md` § 3) :
  1. les cinq mods démarrent ensemble ;
  2. sans HyColony, **le serveur** s'arrête en nommant la dépendance manquante, parce que HyLens a un pack (§ 28.4). À écrire aussi dans la doc d'installation ;
  3. HyColony sans HyLens marche ;
  4. un type de l'API a le même `identityHashCode` vu depuis HyColony et depuis HyLens : c'est la seule preuve que `:api` n'est pas embarqué deux fois ;
  5. à l'arrêt de HyLens, son abonnement se ferme, la colonie reprend si elle était en pause, et son filtre de paquet est retiré ;
  6. `/plugin reload` de HyColony décharge d'abord HyLens, qui en dépend en dur, et HyLens ne revient pas (`PluginManager`, `hycolony-api.md` § 3). Résultat attendu : pas d'exception, et la colonie n'est plus en pause ;
  7. dans un monde où HyColony est désactivé, HyLens répond « indisponible ».

## 9. Écarts à MineColonies

- Le débogage est un mod séparé, alors que MC le garde dans le mod (`core/debug`). C'est le choix de l'utilisateur, et c'est la preuve que l'API suffit.
- L'API rend des instantanés immuables et des `Optional`. MC rend des objets vivants (environ 97 méthodes sur `IColony`) et `null`.
- Le bus d'événements permet de se désabonner, ce que celui de MC ne permet pas. Les deux isolent chaque abonné (MC `DefaultEventBus`).
- Un appel à l'API hors du fil du monde lève une exception (§ 4.1).
- Les événements portent leur cause (idée de Sponge). Ceux de MC non.
- L'historique est horodaté en ticks, note aussi les marches et les actions de l'anti-blocage, et n'est actif que pour un citoyen suivi. Un seul anneau de 20 entrées tient l'IA, le métier, les marches et l'anti-blocage. MC tient un anneau de 20 par machine d'états (celle du citoyen, celle du métier), horodaté à l'heure réelle, et l'active d'office hors production (`BasicStateMachine`, `historyEnabled`), pour un joueur en mode débogage qui interagit avec le citoyen (`EntityCitizen`), et à la première exception de l'IA.
- **Ajouts sans équivalent dans MC** : le contrôle des invariants (MC n'a que `debuginventories`), la pause et le pas à pas de la colonie, `forceLeisure` et `respawnBody`, `WalkEnded` et `StuckAction`.
- `walkTo` (§ 5) :
  - l'attente de 100 ticks de l'IA remplace la pause de navigation de MC ;
  - une marche par citoyen : MC range la marche par joueur, et une seconde commande finit les deux ;
  - l'IA du métier repart à neuf ;
  - le citoyen sans métier marche comme un ouvrier ;
  - l'anti-blocage abandonne au lieu de téléporter vers une case non vérifiée ;
  - la sous-commande `walk stop` de MC n'est pas portée : une nouvelle demande remplace la marche, et HyLens n'en a pas l'usage en V1.
- La flânerie n'attend une marche en cours que `WANDER_TIMEOUT_TICKS` (120 * 20, le `MIN_TP_DELAY` de MC), comptés depuis la première décision qui la voit. MC attend que la navigation ait fini, ce que son anti-blocage garantit sur tout chemin ; le nôtre ne surveille que les marches des marcheurs.

## 10. À vérifier en jeu

- Le coût du HUD rafraîchi toutes les 10 ticks.
- L'interception de « Teleport » sur la carte, et le vanilla intact hors du mode armé.
- La fermeture d'un abonnement et le retrait du filtre à l'arrêt de HyLens.
- Les corps arrêtés pendant la pause de la colonie, et le mécanisme employé.
- Les sept essais en production (§ 8).
