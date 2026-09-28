# HyColony — Sous-projet 0 : Fondations & colonie

- **Date** : 2026-09-25
- **Statut** : en revue
- **Cible** : serveur Hytale **0.6.8** (patchline `release`), Java 25
- **Licence** : GPL-3.0. On porte fidèlement les mécaniques et algorithmes de MineColonies (GPL-3.0). Aucun de ses assets n'est repris : textures, schematics et listes de noms seront recréés.
- **Références** : [analyse MineColonies](../../research/minecolonies-analysis.md) · [spike API Hytale](../../research/hytale-api-spike.md)

---

## 1. Objectif et contexte

HyColony recrée **à l'identique les systèmes de MineColonies** dans Hytale, pour un serveur perso ou communautaire, développé par une seule personne.

Deux contraintes structurent le projet :
1. **L'API Hytale casse à chaque mise à jour majeure.** Les Updates 5, 6 et 7 ont toutes cassé des plugins, au rythme d'environ une tous les trois mois.
2. **Le périmètre de MineColonies est énorme** : 54 bâtiments, environ 51 métiers, requêtes, recherche, raids.

D'où le découpage en sous-projets, chacun avec son propre cycle spec → plan → implémentation :

| # | Sous-projet | Dépend de |
|---|---|---|
| **0** | **Fondations et colonie** (ce document) | — |
| 1 | Système de requêtes | 0 |
| 2 | Construction (constructeur, blueprints, ordres de travail) | 0, 1 |
| 3 | Métiers et production (entrepôt, coursiers, bûcheron, fermier, mineur…) | 0, 1, 2 |
| 4 | Vie des citoyens (nourriture, logement, bonheur, maladies, naissances) | 0, 3 |
| 5 | Défense (gardes, casernes, raids) | 0, 3, 4 |

La recherche, les visiteurs et la taverne, les tombes et le deuil seront rattachés aux sous-projets 3 à 5 lors de leur spec.

### Critère de réussite du sous-projet 0 (en jeu)

1. Un joueur pose un **bloc hôtel de ville**. Une fenêtre « Fonder une colonie » s'ouvre. Il saisit un nom et confirme : la colonie est créée.
2. **Quatre citoyens nommés** apparaissent près de l'hôtel de ville, l'un après l'autre, et **errent** autour. Un point d'errance n'est jamais dans une colonne qui contient un bloc dangereux sur 3 blocs au-dessus et au-dessous (feu, lave, feu de camp éteint, brasero : `DangerousCells`, comme `PathfindingUtils.isDangerous` de MineColonies), et de préférence à plus d'1 bloc de tout bloc dangereux (écart, voir sp12 § 11) ; après 10 tirages dangereux, le citoyen reste au repos. Écart à MineColonies : la navigation de Hytale n'évite que les blocs à `DamageToEntities`, pas le feu (interaction de collision). Toute marche d'un citoyen passe donc par `DetouringBodies` : si la ligne droite vers la cible passe à moins d'1 bloc d'une colonne dangereuse (2 blocs au-dessus et au-dessous de la hauteur interpolée), `SafeRoute` cherche un détour en largeur sur la grille des colonnes qui garde le centre du corps à 1 bloc du danger, car le guidage de Hytale dévie un peu d'un segment droit ; sans détour aussi large, il recommence avec la seule demi-largeur du corps (0,35 bloc), pour passer un trou d'un bloc entre deux feux. Chaque recherche va jusqu'à 6 blocs autour du trajet et 4096 colonnes au plus, puis réduit le chemin à quelques segments droits sûrs ; l'étape suivante n'est prise que si la ligne vers elle garde la marge avec laquelle le détour a été planifié. Chaque étape compte comme atteinte à 1 bloc à l'horizontale. Sans détour possible, la marche va tout droit et la navigation de Hytale décide. Le relief n'est pas lu : la hauteur est interpolée entre le départ et la cible.
3. Après un **redémarrage du serveur**, la colonie, ses citoyens et leurs compétences sont intacts. Il n'y a ni doublon ni citoyen perdu.
4. Un joueur **sans permission** ne peut ni poser ni casser de blocs dans la colonie, et reçoit un message.
5. Un clic sur l'hôtel de ville ouvre sa fenêtre : nom (modifiable), propriétaire, jour, liste des citoyens.
6. `/hycolony selftest` passe au vert sur le serveur de dev.

---

## 2. Architecture

### 2.1 Modules Gradle

```
HyColony/
├── settings.gradle.kts          include(":core", ":plugin")
├── core/                        Java pur, zéro import com.hypixel
│   └── build.gradle.kts         java-library ; compileOnly(gson) ; test : JUnit 5, ArchUnit, gson
└── plugin/                      l'adaptateur Hytale
    ├── build.gradle.kts         plugin com.azuredoom.hytale-tools ; implementation(project(":core"))
    └── src/main/resources/      manifest.json + asset pack (Server/…, Common/…)
```

- **Gson** est fourni à l'exécution par le serveur Hytale. Le core le déclare en `compileOnly` : aucune dépendance ajoutée au jar.
- Les classes du core sont **incluses dans le jar du plugin**, qui reste un seul jar à déployer.
- La frontière est **imposée par Gradle** : `core` n'a pas Hytale dans son classpath, donc un import `com.hypixel` ne compile pas.

### 2.2 Découpage fonctionnel du core

```
dev.hycolony.core
├── kernel/        noyau partagé : ids, positions, horloge, bus d'événements, registres,
│                  moteur d'IA (state machine), config, persistance, ports
├── colony/        Colony, ColonyManager, territoire, permissions, journal
├── building/      types de bâtiments, Building, système de modules
└── citizen/       CitizenData, compétences, CitizenManager, IA citoyen de base
```

Règles vérifiées par un test **ArchUnit** :
- aucune classe du core n'importe `com.hypixel..` ;
- `kernel` ne dépend d'aucun autre package du core ;
- `colony`, `building` et `citizen` peuvent dépendre de `kernel` et entre eux, mais les **futurs** packages de fonctionnalités (`request`, `construction`, `job`, `life`, `defense`) ne se connaîtront que via `kernel` (événements et registres), conformément au graphe du tableau § 1.

Les fonctionnalités communiquent par **événements** (`BuildingAdded`, `CitizenSpawned`…) et par **registres** (types de bâtiments et de modules). Ce sont les points d'extension des sous-projets suivants.

### 2.3 Ports : ce que le core attend du monde

Ce sont des interfaces définies dans `kernel.port`. Le plugin les implémente, et les tests utilisent des fakes en mémoire.

| Port | Responsabilité | Implémentation Hytale (0.6.8) |
|---|---|---|
| `GameClock` | tick courant du core, jour/nuit, numéro du jour | compteur piloté par le plugin ; heure du monde Hytale |
| `CitizenBodies` | faire apparaître un corps, `isAlive`, `position`, `moveTo(pos)`, `navStatus`, `setDisplayName`, `playAnimation`, `despawn` | `NPCPlugin.spawnEntity`, capteur custom `HyColonyTarget` + `Seek`, `NavState`, `DisplayNameSupport` |
| `WorldQuery` | le chunk contenant une position est-il chargé ; hauteur du sol (pour choisir un point d'errance ou d'apparition) | `ChunkStore.getChunkReference`, `BlockSection` |
| `ColonyStorage` | lire, écrire et lister les documents JSON des colonies | `FileColonyStorage` (dans le core, java.nio) ; le plugin ne fournit que le dossier |
| `Notifier` | envoyer un message traduisible à un joueur | `Message.translation(...)`, `playerRef.sendMessage` |
| `UiPort` | afficher un modèle de vue à un joueur ; redessiner en place la fenêtre encore ouverte (`refresh*`) ; fermer | `InteractiveCustomUIPage` + fichiers `.ui` |
| `PlayerDirectory` | un joueur est-il en ligne ; son nom ; sa position | `Universe.get().getPlayer(uuid)` |

Fenêtres vivantes : comme `ColonyPackageManager.updateSubscribers` de MineColonies (toutes les `UPDATE_SUBSCRIBERS_INTERVAL` = 20 ticks), `ColonyWindows` redessine la fenêtre de hutte, d'hôtel de ville ou de citoyen que chaque joueur a ouverte (`OpenWindows`). Écart : pas de drapeaux « sale » par vue ; la vue recalculée est comparée à la dernière affichée, ce qui envoie les mêmes mises à jour. Le plugin ne redessine que si cette fenêtre est encore la page ouverte, par une mise à jour non initiale (`rebuild`), jamais par une réouverture. Chaque cycle vérifie d'abord que la fenêtre est encore ouverte (`UiPort.isShowing`) : une fenêtre fermée, d'un joueur parti ou dans un autre monde, est oubliée sans recalculer sa vue. Le redessin garde l'onglet et le sous-panneau « Options de construction » ; l'onglet Actions de l'hôtel de ville (champ de renommage) n'est pas redessiné tant qu'il est affiché.

Le core ne contient **aucune notion Hytale** : pas de `Ref`, `Store` ni `BlockType`. Les positions sont un `record BlockPos(int x, int y, int z)` et un `record Vec3(double x, double y, double z)` propres au core. Les mondes sont identifiés par un `WorldKey(String name)`.

### 2.4 Threads

- Hytale fait tourner chaque monde sur son propre thread.
- **Le core n'est pas thread-safe et n'a pas besoin de l'être** : il y a un `ColonyManager` par monde, et le plugin ne l'appelle que depuis le thread de ce monde (systèmes ECS, handlers d'événements, `world.execute`).
- Les écritures de fichiers sont les seules opérations hors du thread du monde. Le core sérialise en JSON sur le thread du monde (un instantané immuable), et l'écriture disque passe ensuite par un `Executor` fourni par le plugin.
- **Amendement (SP1+2)** : l'écriture disque est restée **synchrone** sur le thread du monde (`FileColonyStorage`, écriture atomique avec `.bak`). Une colonie pèse quelques dizaines de Ko et n'est écrite qu'à l'autosave ou à l'arrêt : l'`Executor` n'a pas été nécessaire. À revoir si une mesure montre un à-coup.

### 2.5 Cadence de tick

- Le plugin enregistre, par monde, un `DelayedSystem` à **0,05 s**, soit 20 ticks/s, la cadence de Minecraft. À chaque déclenchement, il appelle `ColonyManager.tick()`.
- Toutes les constantes de MineColonies, exprimées en ticks, sont donc **reprises telles quelles** : citoyens toutes les 60 ticks, tick lent toutes les 500, état de colonie toutes les 100, etc.
- Si le serveur est en retard, les ticks sont perdus, pas rattrapés. MineColonies se comporte de la même façon.

---

## 3. Fonctionnalités

### 3.1 Moteur d'IA : `kernel.ai`

C'est un portage fidèle de `BasicStateMachine` et `TickRateStateMachine` de MineColonies. Tous les métiers futurs reposeront dessus.

- `IState` : interface marqueur des états. `AIBlockingEventType` : `AI_BLOCKING`, `EVENT`, `STATE_BLOCKING`.
- `AITarget<S>(state, condition, action → nextState, tickRate)` et `AIEventTarget<S>(eventType, condition, action, tickRate)`, plus `AIOneTimeEventTarget`.
- Ordre d'évaluation à chaque tick : `AI_BLOCKING` → `EVENT` → `STATE_BLOCKING` → transitions de l'état courant. **La première transition qui déclenche termine le tick.** Une action qui renvoie `null` ne provoque pas de transition.
- Cadence par transition, bornée à [1, 12 000]. Chaque transition a un compte à rebours décrémenté du `tickRate` de la machine. Décalage initial réparti par un compteur global modulo 50. `setCurrentDelay(n)` permet à une action d'imposer son délai.
- Un état sans transitions lève une erreur puis `reset()` vers l'état initial. Les 20 dernières transitions sont gardées pour le débogage.
- Une exception dans une action est attrapée : la machine journalise, fait `reset()` et continue (§ 5).

### 3.2 Colonie : `colony`

**Modèle `Colony`** :
- `id` (int, unique par monde, attribué séquentiellement), `world`, `name`, `center` (position de l'hôtel de ville)
- `permissions`, `buildings` (BuildingManager), `citizens` (CitizenManager), `eventLog`
- `day` (compteur de jours), `lastOnlineEpochMs`
- `state` : `ACTIVE` / `UNLOADED` / `INACTIVE`

**Machine d'état de la colonie** (réévaluée toutes les 100 ticks, reprise de MineColonies) :
- `ACTIVE` si un joueur « abonné » est dans le territoire, ou si le chunk de l'hôtel de ville est chargé et qu'un membre de rang ≥ Friend est en ligne. MineColonies utilise ici « plus de 40 chunks chargés » ; comme les chunks Hytale font 32 blocs, on retient le chunk de l'hôtel de ville, qui donne un comportement équivalent.
- `UNLOADED` si un membre est en ligne ;
- `INACTIVE` sinon.

**Cadences en `ACTIVE`** :

| Intervalle (ticks) | Traitement |
|---|---|
| 60 | tick des données citoyens |
| 20 | détection jour/nuit |
| 500 | tick lent : bâtiments et modules tickants, managers (apparition et respawn des citoyens) |

- En `UNLOADED` ou `INACTIVE`, seule la réévaluation d'état tourne. Il n'y a pas de simulation hors chargement, comme dans MineColonies.
- **Jour/nuit** : à l'aube, `day++` et l'événement `DayStarted` ; à la tombée de la nuit, l'événement `NightFell`. Les sous-projets 4 et 5 s'y abonneront.

**Territoire** (conversion fidèle à MineColonies) :
- Le core découpe le monde en **cellules de claim de 16×16 blocs**, l'équivalent d'un chunk Minecraft, indépendamment des chunks Hytale de 32 blocs. Les distances en jeu sont donc identiques à MineColonies.
- À la création, la colonie revendique toutes les cellules dans un rayon de `initialColonySize` = **4 cellules** autour de l'hôtel de ville (carré de 9×9 cellules).
- **Création refusée** si une cellule dans un rayon de `initialColonySize + minColonyDistance` = **4 + 8 cellules** appartient déjà à une autre colonie.
- `maxColonySize` = **20 cellules** : rayon maximal atteignable par les claims des bâtiments, qui arrivent au sous-projet 2.
- Index `cellule → colonyId` par monde, pour savoir en O(1) à quelle colonie appartient une position.

**Règles de l'hôtel de ville** (fidèles à `AbstractBlockHut.canPaste`) :
- **Hors de toute colonie** : seul le bloc hôtel de ville peut être posé. Tout autre bloc de cabane est refusé, avec le message « pas d'hôtel de ville » ou « trop loin de votre hôtel de ville ».
- **Un joueur ne possède qu'une colonie par monde.** Poser un hôtel de ville hors colonie alors qu'on en possède déjà une est refusé.
- **Dans une colonie**, il faut la permission `PLACE_HUTS`, et au plus un hôtel de ville par colonie.
- **Casser l'hôtel de ville** exige `BREAK_HUTS`. Le bâtiment est retiré mais **la colonie persiste**, comme dans MineColonies. Le propriétaire peut reposer un hôtel de ville dans son territoire.
- **Supprimer une colonie** : `/hycolony delete <id>`, réservé aux opérateurs. Les citoyens sont retirés, les claims libérés et le fichier archivé (pas effacé).

**Permissions** (portage de `Permissions`) :
- 5 rangs par défaut : `OWNER=0`, `OFFICER=1`, `FRIEND=2`, `NEUTRAL=3`, `HOSTILE=4`. Des rangs personnalisés sont possibles (id ≥ 5).
- Un `Rank` porte : id, nom, masque `long` d'actions, `isInitial`, `isColonyManager`, `isHostile`.
- L'enum `Action` reprend les **27 actions de MineColonies dans le même ordre**, car le masque en dépend.
- Permissions par défaut, cumulatives vers le haut comme dans MineColonies :
  - **Neutral** : ACCESS_TOGGLEABLES, MAP_BORDER.
  - **Friend** : les actions de Neutral + ACCESS_HUTS, USE_SCAN_TOOL, TOSS_ITEM, PICKUP_ITEM, RIGHTCLICK_BLOCK, RIGHTCLICK_ENTITY, THROW_POTION, SHOOT_ARROW, ATTACK_CITIZEN, ATTACK_ENTITY, TELEPORT_TO_COLONY.
  - **Officer** : les actions de Friend + PLACE_HUTS, BREAK_HUTS, MANAGE_HUTS, RECEIVE_MESSAGES, PLACE_BLOCKS, BREAK_BLOCKS, FILL_BUCKET, OPEN_CONTAINER, RALLY_GUARDS, MAP_DEATHS ; `isColonyManager`.
  - **Owner** : les actions d'Officer + EDIT_PERMISSIONS.
  - **Hostile** : HURT_CITIZEN, HURT_VISITOR, MAP_BORDER ; `isHostile`.
- Un joueur inconnu de la colonie a le rang **Neutral**.
- **Appliquées dans le sous-projet 0** : PLACE_BLOCKS, BREAK_BLOCKS, PLACE_HUTS, BREAK_HUTS, ACCESS_HUTS, OPEN_CONTAINER. Les autres actions sont modélisées et sauvegardées, et seront appliquées par les sous-projets qui les concernent. Depuis le 2026-09-28, l'utilisation de tout bloc suit MC `ColonyPermissionEventHandler.on(PlayerInteractEvent)` (cœur `BlockUse`, plugin `BlockUseProtectionSystem`) : ACCESS_TOGGLEABLES pour les portes et barrières, puis RIGHTCLICK_BLOCK, OPEN_CONTAINER (conteneurs), RIGHTCLICK_ENTITY (blocs à entité), THROW_POTION (potion en main, liste `potions` de l'id-map). Les trappes ne sont pas des « toggleables » (MC ne compte que portes et barrières). Le message de refus revient au plus toutes les 10 secondes par colonie et par joueur, sur l'horloge du monde de la colonie (MC `cancelEvent`, une instance par colonie ; cœur `DenialNotices`, tenu par les `Permissions` de la colonie, et `ColonyRefusal`) : pose, casse et usage de blocs, casse d'une hutte (BREAK_HUTS), ouverture d'une hutte (ACCESS_HUTS) et de son stockage. *Écarts* : pas de blocs ni de positions libres (MC `freeBlocks`, `freePositions`, tag `colonyProtectionException`), faute de moyen de les déclarer ; pas d'outil de scan ; pas d'exception PvP ; pas de lévitation après plus de 10 refus en 10 secondes (aucun effet de lévitation dans Hytale 0.6.8) ; pas de journal des refus dans l'hôtel de ville (HyColony n'en a pas encore).
- La protection est désactivable par la config `Permissions.EnableColonyProtection` (vrai par défaut). Un joueur en créatif qui contourne les permissions (`Permissions.PermissionEventBypassMinPermLevel`, voir § 3.5) a les droits du rang opérateur de MineColonies.
- Le propriétaire ou un rang `EDIT_PERMISSIONS` gère les membres via `/hycolony rank <joueur> <rang>`. La fenêtre de permissions viendra plus tard, car elle est lourde en UI. *Écart temporaire assumé par rapport à MineColonies.*

**Journal de colonie** : liste bornée à **100 entrées** (type, jour, paramètres). Types du sous-projet 0 : `ColonyCreated`, `CitizenSpawned`, `BuildingPlaced`, `BuildingRemoved`.

### 3.3 Bâtiments : `building`

C'est le squelette qui accueillera les 54 bâtiments. Il est fidèle au modèle `BuildingEntry` et aux modules de MineColonies.

- **`BuildingType`** (entrée de registre) : `id` (`hycolony:townhall`), `hutBlockKey` (clé logique, cf. § 4.3), `maxLevel` (5), liste ordonnée de `ModuleProducer`.
- **`ModuleProducer`** : `key` (string stable, utilisée pour la sauvegarde) et `Supplier<BuildingModule>`.
- **`BuildingRegistry`** : enregistrement au démarrage. La recherche par id sert au chargement des sauvegardes.
- **`Building`** : `type`, `position`, `rotation` (0 à 3), `level` (0 à 5), `isBuilt`, `customName`, `style` (chaîne réservée au sous-projet 2), modules indexés par clé.
- **Interfaces de capacités des modules** : `BuildingModule` (base), `PersistentModule` (écrire et lire son `JsonObject`), `TickingModule` (`onColonyTick`), `BuildingEventsModule` (`onRemoved`, `onUpgradeComplete` ; `onPlaced` et `onWakeUp` ont été retirés : MC n'a pas d'événement de pose, et `onWakeUp` reviendra avec le sous-projet 4). Les capacités d'affectation de citoyens et de création de résolveurs arriveront avec les sous-projets 1 et 3.
- **Contenu du sous-projet 0** : un seul type, l'**hôtel de ville**, posé au **niveau 0**. Il deviendra niveau 1 quand le constructeur l'aura bâti, au sous-projet 2. Il n'a pas encore de module métier.

### 3.4 Citoyens : `citizen`

**`CitizenData`** (persistant ; l'entité Hytale est jetable) :
- `id` (int par colonie), `name` (prénom et nom), `gender` (MALE ou FEMALE), `isChild` (toujours faux pour l'instant)
- `skills` : `EnumMap<Skill, SkillData(level, xp)>`
- `lastPosition`, `respawnPosition`
- réservés, déjà sérialisés mais non utilisés dans le sous-projet 0 : `homeBuildingPos`, `workBuildingPos`, `saturation` (initialisée à la valeur de départ de MineColonies)

**`Skill`** : les 11 compétences, avec leurs relations complémentaire et adverse (tableau dans l'analyse, § 4). Les formules de gain d'XP et de niveau sont portées dès maintenant (fonctions pures et testées), même si seuls les métiers (sous-projet 3) donneront de l'XP.

**Génération d'un citoyen initial** : nom tiré de nos listes (§ 4.3), genre qui alterne pour équilibrer la colonie, niveaux de compétence initiaux calculés **selon la même règle que MineColonies**. Le plan d'implémentation lira `CitizenData` et `CitizenSkillHandler` pour recopier la distribution exacte.

**`CitizenManager`** (tick lent, toutes les 500 ticks) :
- **Apparition initiale** : tant que la colonie a moins de `initialCitizenAmount` citoyens (4 par défaut) et qu'un hôtel de ville existe, **un** citoyen apparaît par tick lent, à côté de l'hôtel de ville. Le premier citoyen déclenchera au sous-projet 4 la quête de bienvenue ; pour l'instant, il produit juste une entrée de journal.
- **Respawn** : toutes les **5 minutes** (6 000 ticks), pour chaque citoyen dont le corps est absent ou mort, et seulement si le chunk de destination est chargé, le citoyen réapparaît à `respawnPosition`, sinon `lastPosition`, sinon près de l'hôtel de ville.
- **Plafond** : `maxCitizenPerColony` (250). La capacité réelle en logements arrive au sous-projet 4.

**Liaison entre données et corps** (`CitizenBodies`) :
- Chaque corps Hytale porte un **composant persistant `HyColonyCitizen {colonyId, citizenId}`**, enregistré avec un codec : il survit à la sauvegarde des chunks.
- Quand une entité avec ce composant est chargée, le plugin prévient le core (`onBodyLoaded`). Si les données existent et n'ont pas encore de corps, le core les **relie**. Si le citoyen a déjà un corps vivant, ou n'existe plus dans les données, le corps chargé est **supprimé**. Ce cas couvre les doublons après un crash, et MineColonies fait de même.
- Toutes les 60 ticks, `lastPosition` est mise à jour depuis le corps.

**IA citoyen du sous-projet 0** : une `TickRateStateMachine` à deux états.
- `IDLE` : choisit toutes les 10 à 20 secondes un point au sol à ≤ 10 blocs de l'hôtel de ville, puis `moveTo`.
- `WANDERING` : attend `ARRIVED`, `BLOCKED` ou `FAILED` (délai max 30 s), puis revient en `IDLE`.

C'est la version réduite du `CitizenAI` de MineColonies (IDLE, WORK, SLEEP, EAT…). Les autres états viendront avec les sous-projets 3 et 4.

### 3.5 Configuration

La config du plugin est `mods/<group>_HyColony/config.json` (`withConfig`, codec Hytale). Elle reprend les sections de MineColonies ; le plugin la traduit en un `record ColonyConfig` du core (un record par section), qui ramène chaque valeur dans ses bornes.

| Section | Clé | Défaut (MineColonies) | Bornes |
|---|---|---|---|
| `Gameplay` | `InitialCitizenAmount` | 4 | 1–10 |
| `Gameplay` | `MaxCitizenPerColony` | 250 | 25–500 |
| `Claims` | `MaxColonySize` | 20 (cellules) | 1–250 |
| `Claims` | `MinColonyDistance` | 8 (cellules) | 1–200 |
| `Claims` | `InitialColonySize` | 4 (cellules) | 1–15 |
| `Claims` | `MaxDistanceFromWorldSpawn` | 30000 (blocs) | 1000–2147483647 |
| `Claims` | `MinDistanceFromWorldSpawn` | 0 (blocs) | 0–1000 |
| `Permissions` | `EnableColonyProtection` | true | |
| `Permissions` | `TurnOffExplosionsInColonies` | `DAMAGE_ENTITIES` | `DAMAGE_NOTHING`, `DAMAGE_PLAYERS`, `DAMAGE_ENTITIES`, `DAMAGE_EVERYTHING` |
| `Permissions` | `PermissionEventBypassMinPermLevel` | 2 | 0–4 |
| `Commands` | `CanPlayerUseShowColonyInfoCommand` | true | |
| `Commands` | `CanPlayerUseAddOfficerCommand` | true | |
| `Commands` | `CanPlayerUseDeleteColonyCommand` | false | |
| `Client` | `BuildGoggleRange` | 50 (blocs) | 1–250 |
| `HyColony` | `AutosaveIntervalMinutes` | 5 (propre à HyColony) | 1–60 |
| `HyColony` | `BuilderInfiniteResources` | false (constante `BUILDER_INF_RESOURECES` chez MC) | |
| `HyColony` | `CreativeOperatorFreeBuilds` | true (propre à HyColony) | |

Migration : les neuf clés plates du premier format restent lues (leur valeur passe dans la section) mais ne sont jamais réécrites ; le `save()` du démarrage réécrit donc un ancien fichier en sections, avec ses valeurs.

Écarts avec MineColonies :
- `BuildGoggleRange` est une option cliente chez MC, un réglage serveur ici (pas de config cliente pour un plugin Hytale).
- `PermissionEventBypassMinPermLevel` : Hytale n'a pas de niveaux d'opérateur. 0 laisse passer tout joueur en créatif, 1 à 4 un opérateur (groupe de `/op`) en créatif.
- `TurnOffExplosionsInColonies` ne protège que les blocs : Hytale ne permet pas de distinguer les dégâts d'explosion aux entités des dégâts de projectile.
- Commandes refusées par la config : message « pas la permission » de Hytale au lieu de « commande désactivée dans la config ».
- `MinDistanceFromWorldSpawn` / `MaxDistanceFromWorldSpawn` mesurent la distance au point d'apparition du fondateur (Hytale peut en donner un par joueur).
- `MaxCitizenPerColony` n'est lue par rien : comme chez MC, elle ne plafonne que l'immigration et les naissances, pas encore portées.

---

## 4. Intégration Hytale : `plugin`

### 4.1 Cycle de vie

- **Constructeur** : `withConfig("config", HyColonyConfig.CODEC)`.
- **`setup()`** :
  - enregistrer le composant `HyColonyCitizen` (avec codec) ;
  - enregistrer le capteur PNJ `HyColonyTarget` via `NPCPlugin.get().registerCoreComponentType` ;
  - enregistrer les systèmes ECS : tick 20 Hz par monde, `PlaceBlockEvent`, `BreakBlockEvent`, `UseBlockEvent.Pre`, chargement d'entité citoyen ;
  - enregistrer les commandes et les listeners (`StartWorldEvent`, `PlayerReadyEvent`, `ShutdownEvent`).
- **`LoadedAssetsEvent`** : validation de la table d'identifiants (§ 4.3).
- **Démarrage d'un monde** : créer son `ColonyManager` et charger les colonies de `<sauvegarde du monde>/hycolony/`.
- **`shutdown()`** : sauvegarde synchrone de toutes les colonies modifiées.

### 4.2 Adaptateurs

- **`HytaleCitizenBodies`** :
  - apparition : `spawnEntity` du rôle `HyColony_Citizen`, en attachant `HyColonyCitizen` dans `preAddToWorld` ;
  - déplacement : `moveTo` écrit la cible dans le composant `HyColonyMoveTarget` (non persistant), lu par le capteur `HyColonyTarget` ;
  - suivi : `navStatus` traduit `NavState` (`AT_GOAL` → ARRIVED, `BLOCKED` → BLOCKED, `ABORTED` → FAILED, sinon MOVING ou IDLE) ;
  - nom affiché : `DisplayNameSupport.setDisplayName`.
- **Rôle PNJ** `Server/NPC/Roles/HyColony_Citizen.json` : apparence humanoïde, invulnérable pour l'instant (les dégâts arrivent avec les sous-projets 4 et 5), barre de vie masquée si la clé est disponible en 0.6.8. Instruction racine : `Sensor HyColonyTarget` + `BodyMotion Seek` ; sinon rester immobile.
- **`HytaleWorldQuery`** : uniquement via l'API par sections (`ChunkStore`, `BlockSection`), jamais les méthodes `@Deprecated`.
- **Protection** : systèmes ECS sur `PlaceBlockEvent`, `BreakBlockEvent` et `UseBlockEvent.Pre`. Ils interrogent `colony.permissions` et **annulent** l'événement si l'action n'est pas permise, avec un message.
- **Hôtel de ville** :
  - `PlaceBlockEvent` avec l'objet hôtel de ville : le core valide la pose. Hors colonie, la pose est acceptée et la fenêtre « Fonder une colonie » s'ouvre.
  - Si le joueur annule ou ferme la fenêtre, le bloc est retiré et l'objet rendu.
  - `UseBlockEvent.Pre` sur le bloc ouvre la fenêtre de l'hôtel de ville (`ACCESS_HUTS` requis).

### 4.3 Asset pack et table d'identifiants

L'asset pack est dans `plugin/src/main/resources/` (`IncludesAssetPack: true`) :
- `Server/Item/Items/HyColony/…` : objet et bloc **hôtel de ville**, avec un modèle provisoire.
- `Server/NPC/Roles/HyColony_Citizen.json`.
- `Server/Languages/en-US/hycolony.lang` et `fr-FR/hycolony.lang`.
- `Common/UI/Custom/Pages/HyColony/*.ui` : fenêtres « Fonder une colonie » et « Hôtel de ville ». Ce chemin a été déduit du code et sera vérifié au premier lancement.
- Données du core (`citizen-names/*.json`) : listes de noms **créées pour HyColony**, dans l'ambiance Hytale.

**Table d'identifiants** (`hycolony/id-map.json`) : `clé logique → id d'asset Hytale`, par exemple `"hut.townhall": "HyColony_TownHall"`, `"npc.citizen": "HyColony_Citizen"`.
- Au `LoadedAssetsEvent`, chaque id est vérifié (`BlockType.getAssetMap().getIndex`, `Item.getAssetMap().getAsset`, `NPCPlugin.hasRoleName`).
- Un id manquant produit un **rapport d'erreur** listant toutes les références cassées. Si un id vital manque (hôtel de ville, rôle citoyen), HyColony **se désactive proprement** : les colonies ne tickent plus, rien n'est écrit, et les sauvegardes restent intactes.

### 4.4 UI : modèles de vue

- Le core produit des **records de vue** immuables et consomme des **actions** :
  - `FoundColonyView(suggestedName)` → actions `Confirm(name)` et `Cancel` ;
  - `TownHallView(colonyName, ownerName, day, citizens: List<CitizenRow(name, gender, status)>, canRename)` → action `Rename(newName)`.
- Le plugin rend ces vues avec `InteractiveCustomUIPage` et des fichiers `.ui`, et retraduit les événements du client en actions du core.
- Quand l'API Noesis arrivera côté serveur, seul le rendu changera.

### 4.5 Commandes

`/hycolony` (`AbstractCommandCollection`) :
- `info` : colonie à la position du joueur (nom, propriétaire, état, jour, citoyens).
- `rank <joueur> <owner|officer|friend|neutral|hostile>` : exige EDIT_PERMISSIONS.
- `delete <id>` : opérateurs.
- `selftest` : opérateurs.
  - Fait apparaître un corps de test, lui ordonne d'avancer de 3 blocs et attend `ARRIVED` (15 s max), puis le supprime.
  - Écrit puis relit une colonie factice dans un dossier temporaire.
  - Relance la validation des identifiants.
  - Affiche OK ou KO par étape.

---

## 5. Persistance

- **Un fichier par colonie** : `<sauvegarde du monde>/hycolony/colony-<id>.json`, plus `index.json` (prochain id et liste des colonies).
- **Format JSON détenu par le core** (Gson) :
  - `schemaVersion` (1 pour le sous-projet 0) ;
  - la colonie (`permissions`, `buildings[]` avec `type` et `modules{clé: …}`, `citizens[]`, `eventLog[]`) ;
  - les claims ne sont pas sauvegardés, ils sont recalculés au chargement à partir des colonies.
- **Écriture atomique** : écrire `colony-<id>.json.tmp`, renommer l'actuel en `.bak`, puis déplacer `.tmp` à sa place de façon atomique.
- **Chargement** : lire le fichier, ou le `.bak` s'il est illisible ; s'il est illisible aussi, déplacer les deux dans `hycolony/corrupt/` et le journaliser, sans bloquer le serveur.
- **Migrations** : une chaîne `Migration(from, to, JsonObject → JsonObject)`.
  - Avant toute migration, une copie `colony-<id>.v<ancienne version>.json` est conservée.
  - Une version plus récente que celle du code refuse de charger la colonie et le journalise. Le fichier n'est jamais écrasé.
- **Types inconnus** : un bâtiment ou module dont le type n'est plus enregistré est **conservé tel quel** (JSON brut) et réécrit sans modification. On ne perd jamais de données à cause d'un changement de code.
- **Déclencheurs de sauvegarde** : toutes les `autosaveIntervalMinutes` pour les colonies modifiées ; à l'arrêt d'un monde ou du serveur ; après la création ou la suppression d'une colonie.

---

## 6. Gestion d'erreurs

| Situation | Comportement |
|---|---|
| Exception pendant le tick d'une colonie | Journaliser avec la trace, puis **suspendre les ticks de cette colonie pendant 5 minutes** (comme MineColonies). Les autres colonies continuent. |
| Exception dans une action d'IA de citoyen | Journaliser, `reset()` de la machine vers son état initial. |
| Échec d'écriture disque | Journaliser, laisser la colonie marquée comme modifiée, réessayer à la sauvegarde suivante. Le `.bak` n'est jamais supprimé. |
| Fichier corrompu | Se rabattre sur le `.bak`, sinon mettre en quarantaine dans `corrupt/` (§ 5). |
| Id d'asset manquant | Rapport d'erreur, désactivation propre si l'id est vital (§ 4.3). |
| Le corps d'un citoyen disparaît (déchargement, bug) | Rien d'immédiat : le respawn toutes les 5 minutes s'en charge. |
| `moveTo` en échec (`FAILED`/`BLOCKED`) | L'IA retourne en `IDLE` et choisit un autre point. |
| Événement d'un monde sans `ColonyManager` | Ignorer, avec un log de debug. |

---

## 7. Tests

**Core (JUnit 5, sans serveur)**, avec des fakes pour tous les ports :
- **State machine** : ordre des types d'événements, cadences, `setCurrentDelay`, action qui renvoie `null`, état sans transition, exception dans une action. Les cas sont tirés du comportement de MineColonies.
- **Territoire** : claims initiaux, refus de création trop près d'une autre colonie, appartenance d'une position.
- **Permissions** : permissions par défaut de chaque rang, rang Neutral par défaut, cumul, masque stable (ordre de l'enum).
- **Création de colonie** : règles de l'hôtel de ville (hors colonie, une colonie par joueur, un hôtel de ville par colonie, casse sans suppression).
- **CitizenManager** : apparition initiale à une par tick lent jusqu'à 4, alternance des genres, respawn après 6 000 ticks uniquement si le chunk est chargé, liaison et suppression des doublons.
- **Compétences** : formules d'XP et de niveau (valeurs connues de MineColonies).
- **Persistance** : aller-retour complet, écriture atomique et reprise sur `.bak`, quarantaine, conservation des types inconnus, **chaîne de migrations avec fichiers de test** (on garde un exemple de fichier par version de schéma).
- **Architecture (ArchUnit)** : règles du § 2.2.

**Plugin** : pas de tests unitaires, la couche est volontairement mince. Validation par :
- `/hycolony selftest` ;
- la checklist manuelle de `docs/TESTING.md`, qui reprend les 6 critères du § 1.

**CI** : le workflow GitHub existant (`./gradlew build`) exécute les tests du core à chaque push.

---

## 8. Mises à jour de Hytale

- **Version épinglée** dans `gradle.properties` : `hytale_version = 0.6.8`, `manifestServerVersion = >=0.6.8 <0.7.0`.
- **`docs/UPGRADING.md`** : checklist de montée de version.
  1. Lire les notes de patch (section plugins/API).
  2. Changer la version épinglée.
  3. Compiler et corriger `plugin/` (le core ne doit pas bouger).
  4. `./gradlew test`.
  5. Lancer le serveur de dev.
  6. `/hycolony selftest`.
  7. Lire le rapport de validation des identifiants.
  8. Charger une sauvegarde de la version précédente.
  9. Suivre la checklist `docs/TESTING.md`.
- **Update 7** : le plugin utilise déjà l'API par sections. Seul `BlockOperations.setBlock`, annoncé instable, n'est pas utilisé dans le sous-projet 0.

---

## 9. Remise en état du template

- Supprimer les classes d'exemple (`ExamplePlugin`, `ExampleCommand`, `ExampleConfig`, `ExampleEvent`). Leurs déclarations de package ne correspondent pas à leurs dossiers, donc le projet ne compile pas aujourd'hui.
- `settings.gradle.kts` : `rootProject.name = "HyColony"`, `include(":core", ":plugin")`.
- `gradle.properties` :
  - `main_class = dev.hycolony.plugin.HyColonyPlugin`
  - `mod_id = hycolony`
  - `mod_license = GPL-3.0`
  - `mod_description`, `mod_author` à compléter
  - supprimer la ligne `hytaleHomeOverride`, qui pointe vers un chemin factice
  - versions épinglées (§ 8)
  - `manifest_dependencies` : ajouter la dépendance au plugin PNJ de Hytale (nom exact à relever dans le manifeste du serveur)
- Ajouter `LICENSE` (GPL-3.0) et un `README.md` propre au projet.

---

## 10. Hors périmètre du sous-projet 0

Construction et niveaux de bâtiments, style packs, système de requêtes, métiers, inventaire des citoyens, nourriture, logement, bonheur, maladies, naissances, recherche, gardes, raids, visiteurs, fenêtre de permissions, fenêtre de citoyen, interactions (« chat » des citoyens), carte ou visualisation des frontières.

## 11. Points à vérifier en jeu pendant l'implémentation

1. Chemin exact des fichiers `.ui` (`Common/UI/Custom/…`).
2. Les PNJ créés par plugin ne sont-ils jamais despawnés automatiquement ?
3. Le `Seek` piloté par notre capteur fonctionne-t-il tel que prévu (arrivée, blocage) ?
4. Nom exact de la dépendance au plugin PNJ dans le manifeste.
5. `HiddenUIComponents` (masquer la barre de vie) existe-t-il en 0.6.8 ?

Si l'un de ces points échoue, le plan d'implémentation prévoit la solution de repli correspondante, **sans changer les ports du core**.
