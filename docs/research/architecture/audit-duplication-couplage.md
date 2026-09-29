# Audit : duplication et couplage — HyColony (lecture seule)

Méthode : PMD CPD 7.28.0 (jars `pmd-core`/`pmd-java` du cache Gradle, pilotés par un petit programme
`CpdAnalysis`, aucun `pmd-cli` n'étant en cache), seuil 50 tokens, Java, sur `core/src/main` et
`plugin/src/main` séparément. Couplage : imports `dev.hycolony.core.*` sur 242 fichiers (~17 250 lignes).
Coût du job courrier : `git diff-tree --name-status` sur les 5 commits SP3a.

## 1. Duplication (PMD CPD, ≥ 50 tokens)

`core` : 14 blocs, `plugin` : 29 blocs. Rien d'énorme (max 88 tokens / 23 lignes) : pas de copier-coller
massif, un fond continu de petites redites.

| Tokens | Occ. | Fichiers |
|---|---|---|
| 88 | 2 | `HyColonyCommand.java:92-105` / `:132-145` |
| 76 | 2 | `ProtectionSystems.java:91-107` / `:133-149` |
| 71 (×2 blocs) | 2 | `CourierResolver.java` / `WarehouseStockResolver.java` |
| 70 | 2 | `HutBlockSystems.java:138-154` / `:189-205` |
| 69 | 2 | `HutActions.java:146-154` / `:169-177` |
| 63 | 2 | `PlayerResolver.java:110-132` / `RetryingResolver.java:134-156` |
| 54 | 3 | `HutBlockSystems.java` / `GogglesSystems.java` / `CitizenUseSystem.java` |

**Par nature :**

- **Boilerplate ECS Hytale** (~40 % des blocs plugin) : la signature `getQuery()` +
  `handle(int, ArchetypeChunk, Store, CommandBuffer, Event)` revient identique dans `HutBlockSystems`,
  `ProtectionSystems`, `GogglesSystems`, `CitizenUseSystem`. Imposé par l'API, non réductible sans interface à
  usage unique (interdite par CLAUDE.md § 2).
- **Vraie logique dupliquée, à extraire :**
  - `HutActions.hire/fire/setHiring` : le triplet « résoudre `ManagedHut`, résoudre `WorkerModule`, `null` ⇒
    `false` » répété 3 fois (l. 146-192) ; un `resolveWorker(player, hutPos)` suffirait.
  - `HyColonyCommand` : « `WorldRuntime` du monde, colonie à la position, vide ⇒ erreur » répété dans `Info`,
    `Rank` et 2 autres sous-commandes.
  - `PlayerResolver`/`RetryingResolver` et `BuildingResolver`/`WarehouseStockResolver` : résolution de requête
    recopiée par paire.
- **Duplication sémantique, hors de portée de CPD :**
  - Formule MC `getSuitabilityMetric` (`distance = sqrt(distSq(warehouse))`, puis
    `max(distance/10,1) + queue.size()`) recalculée séparément dans `CourierResolver` et
    `WarehouseStockResolver` — un `WarehouseSuitability` partagé l'unifierait.
  - Diffusion aux membres autorisés (`for member : permissions().members()... if hasPermission(...,
    RECEIVE_MESSAGES) notifier().send(...)`) répétée dans `BuildCompletion`, `WorkManager`,
    `WarehouseStorage` — candidat à `Colony.broadcast(Msg)`.
  - **XP des jobs, à l'inverse de l'hypothèse** : `BuilderContext.award` et `CourierContext.award` délèguent
    tous deux à `JobXp.award(...)` ; la formule MC n'est **pas** dupliquée — déjà bien extraite.
  - `BuilderAI.tick()`/`DeliverymanAI.tick()` : même garde de cadence, même `onException` (log +
    `machine.reset()` + délai) ; trop courts/dissemblables pour CPD (voir § 4).

## 2. Couplage entre familles fonctionnelles de `core`

Imports croisés agrégés (hors même famille) :

| De \ Vers | building | citizen | colony | construct. | job | kernel | logistics | request |
|---|---|---|---|---|---|---|---|---|
| **building** | – | 1 | 3 | 0 | 0 | 7 | 0 | 17 |
| **citizen** | 1 | – | 4 | 7 | 4 | 17 | 0 | 0 |
| **colony** | 34 | 29 | – | 33 | 16 | 75 | 6 | 46 |
| **construction** | 27 | 7 | 36 | – | 10 | 109 | 4 | 8 |
| **job** | 5 | 7 | 3 | 0 | – | 2 | 0 | 0 |
| **kernel** | 0 | 0 | 0 | 0 | 0 | – | 0 | 0 |
| **logistics** | 30 | 7 | 15 | 4 | 12 | 49 | – | 51 |
| **request** | 0 | 0 | 0 | 0 | 0 | 21 | 0 | – |

`kernel` a une ligne à zéro : la règle CLAUDE.md « `kernel` ne dépend d'aucun autre paquet » est vérifiée sur
les imports réels.

**Cycles entre familles (edges mutuels) :**

| Paire | Poids a→b / b→a |
|---|---|
| `colony` ↔ `construction` | 33 / 36 (seul cycle vraiment dense) |
| `building` ↔ `colony` | 3 / 34 |
| `citizen` ↔ `colony` | 4 / 29 |
| `colony` ↔ `logistics` | 6 / 15 |
| `colony` ↔ `job` | 16 / 3 |
| `citizen` ↔ `job` | 4 / 7 |

`colony ↔ construction` est le seul cycle bidirectionnel dense : `Colony` orchestre la construction, qui relit
`Colony`/`Building`/`WorkOrder` — attendu du rôle de façade de `Colony`, à surveiller s'il grossit.

**Hubs (imports entrants / sortants) :**

| Classe | Entrant | Classe | Sortant |
|---|---|---|---|
| `Building` | 60 | `LogisticsViews` | 20 |
| `Colony` | 57 | `RequestActions` | 18 |
| `CitizenData` | 29 | `BuilderAI` | 17 |
| `WorkerModule` | 18 | `HutActions` | 16 |
| `ColonyManager` | 15 | `ConstructionPorts`, `RequestViews` | 15 |
| `RequestManager` | 12 | `WandPlacement`, `CourierContext` | 15 |
| `UiPort` | 2 (relayé par `ColonyContext`) | | |

`ColonyManager` (216 l.), `RequestManager` (239), `Colony` (243), `Building` (223) restent loin des 400 lignes :
les hubs sont concentrés en fan-in, pas en taille de fichier — la délégation (§ 2) tient.

## 3. Coût d'ajout d'un métier (courrier, SP3a)

Sur les 5 commits (`f4f6ec6`, `de7c760`, `48089e1`, `fafcf98`, `deb7ac3`) : **97 fichiers uniques touchés**,
dont **53 nouveaux** (le métier : `logistics/courier/*`, tests, UI, assets JSON) et **44 fichiers
préexistants modifiés à la main** — ce second chiffre mesure le coût structurel.

**Registres partagés édités à la main :**

| Fichier | Rôle |
|---|---|
| `core/citizen/CitizenManager.java`, `CitizenAI.java` | brancher le job dans le cycle de vie / l'IA |
| `core/app/persistence/ColonySerializer.java` | (dé)sérialisation du job |
| `core/app/ColonyManager.java` | enregistrer `LogisticsActions` |
| `core/job/Job.java`, `job/WorkerModule.java` | hook générique |
| `core/request/Request.java` | nouveaux types de requêtes |
| `app/ui/{Building,Citizen,Requests}View.java` + `app/view/*Views.java` | agrégateurs de vues |
| `kernel/port/{WorldQuery,CitizenBodies}.java`, `kernel/nav/DetouringBodies.java` | ports étendus |
| `plugin/WorldRuntime.java` | câblage du monde |
| `plugin/IdMap.java`, `resources/hycolony/{id-map,styles}.json` | identifiants d'assets Hytale |
| `plugin/block/HutBlockSystems.java` | dispatch de pose de hutte |
| `plugin/command/HyColonyCommand.java` | commande de self-test |
| `plugin/ui/{BuildingPage,BuildingMainTab,RequestsPage,CitizenRequestsTab}.java` | dispatch pages/onglets |
| `Server/Languages/{en-US,fr-FR}/hycolony.lang` | chaque texte joueur, deux langues |

~20 points de couture obligatoires (hors création pure), stables : un prochain métier (garde…) touchera
vraisemblablement le même ensemble.

## 4. Héritage vs composition

- **1 seule classe abstraite** dans `core` : `Job`, étendue directement par `BuilderJob` et `DeliverymanJob`
  (profondeur 1).
- **34 interfaces**, très majoritairement des ports à une implémentation (11 dans `kernel/port`, plus
  `BlueprintSource`, `UiPort`) ou des contrats de valeur (`JobAI`, `Resolver`, `Requestable`).
- **Profondeur max : 2**, seulement dans le moteur d'état (`TickingTransition` → `AIEventTarget` →
  `AIOneTimeEventTarget`), jamais dans le code métier.

`BuilderAI` et `DeliverymanAI` n'ont **aucune classe mère commune** : chacun implémente seulement `JobAI` et
partage par **composition** : `TickRateStateMachine<S>` (moteur d'état générique, port MC
`BasicStateMachine`), `BodyWalker` (composé dans `CourierContext`, enveloppé dans `BuilderWalker` côté builder
pour le poste de travail), et `JobXp` pour l'XP (déjà partagé, § 1).

Reste dupliqué faute d'un troisième collaborateur : le garde de cadence dans `tick()`
(`if (++calls < MACHINE_RATE) return; calls = 0; machine.tick();`), la constante `MACHINE_RATE = 5` redéclarée
deux fois, et le patron `onException` (log + `reset()` + délai) — trop court et légèrement différent (le
builder ajoute `!ctx.canRun() ||`) pour CPD, mais le même choix fait deux fois ; un `TickGate` partagé
l'absorberait.
