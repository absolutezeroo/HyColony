# API publique de HyColony : recherche (2026-09-30)

Décision de l'utilisateur (2026-09-30) : HyColony reçoit une API publique et versionnée (`dev.hycolony.api`, peut-être aussi `dev.hycolony.plugin.api`). L'outil de débogage (`debug-mod.md`) devient un 5e mod, « HyColony Debug », qui n'atteint HyColony **que** par cette API. Ce document fait l'inventaire complet avant la conception : MineColonies, les autres écosystèmes, la mécanique de Hytale, les outils de compatibilité, les choix de conception, les besoins du mod de débogage et l'impact sur les garde-fous.

Sources :
- MineColonies (MC) : `version/main`, clone partiel du commit `477ff1d` (2026-09-29), noté `MC:` et relatif à `src/main/java/com/minecolonies/`.
- Hytale : sources décompilées 0.7.0-pre.4, chemins relatifs à `build/vineflower/hytale-server/com/hypixel/hytale/`.
- HyColony : chemins relatifs à la racine du dépôt.

Marques : **vérifié** (lu dans les sources) ; **[in-game]** (le code le permet, résultat en jeu inconnu).

## 1. L'API de MineColonies

### 1.1 Découpage et publication

| Période | Forme | Preuve |
|---|---|---|
| 2019 → 1.19.4 | Source set `src/api` séparé, publié en jar à classifieur `api` | PR #3777 « Feature/api » (2019-08-18, https://github.com/ldtteam/minecolonies/pull/3777) ; `projectHasApi=true` dans le `gradle.properties` des branches `version/1.16.5` à `version/1.19.4` ; le script commun `OperaPublicaCreator/gradle/mod.gradle` crée alors le source set `api`, dont `main` dépend (l. 306-345), le jar `api` (l. 863-880) et sa publication (l. 1003-1010) |
| 1.20.1 et au-delà | **Plus de source set** : paquet `com.minecolonies.api` dans `main`, sans jar séparé | PR #9749 « move api module to api folder » (2024-01-28, https://github.com/ldtteam/minecolonies/pull/9749), sans justification ; `projectHasApi=false` sur `version/main` (1.20.1), `version/1.20.4` et `version/1.21` |

Conséquence mesurée : sur les 651 fichiers de `com.minecolonies.api`, **47 importent `com.minecolonies.core`** (`PathingStuckHandler`, `AbstractAdvancedPathNavigate`, `TileEntityColonyBuilding`, `GsonHelper`…). Sans frontière de compilation, l'API dépend de l'implémentation.

### 1.2 Point d'entrée et surface

- `IMinecoloniesAPI.getInstance()` renvoie un `MinecoloniesAPIProxy` statique, rempli par `setApiInstance` au démarrage (`MC:api/IMinecoloniesAPI.java`, `MC:api/MinecoloniesAPIProxy.java:40-52`). Implémentation : `MC:apiimp/CommonMinecoloniesAPIImpl`.
- Il expose : `getColonyManager`, `getCitizenDataManager`, `getBuildingDataManager`, `getJobDataManager`, `getMobAIRegistry`, `getPathNavigateRegistry`, `getConfig`, `getGlobalResearchTree`, `getFurnaceRecipes`, `getEventBus`, et **17 registres Forge** (bâtiments, extensions de bâtiment, métiers, réponses d'interaction, types de garde, recherche, événements de colonie, recettes, types d'artisanat, quêtes, bonheur, équipement…).
- Les objets sont **vivants et mutables** : `IColony` (environ 97 méthodes), `IBuilding` (≈ 75), `ICitizenData` (≈ 65), `IColonyManager` (≈ 51), `IJob` (≈ 44). Une absence rend `null` (`IColonyManager.getColonyByWorld`, l. 76). `ICitizenData.getEntity()` rend l'entité vivante (l. 222).
- Deux hiérarchies parallèles : `IColony` côté serveur, `IColonyView` côté client (`IColonyManager.getColonyView`, l. 195, 328).
- Accès par identifiant et par monde : `getColonyByDimension(int id, ResourceKey<Level>)`, `getColonyByPosFromWorld(Level, BlockPos)`, `getIColonyByOwner(Level, UUID)`.

### 1.3 Bus d'événements

Depuis la PR #10468 (2024-12-22, https://github.com/ldtteam/minecolonies/pull/10468), MC a **son propre bus**, « loader unaware ». Il remplace les événements Forge, supprimés sans période de dépréciation (`ColonyEvents`, `BuildingConstructionEvent`, `ColonyInformationChangedEvent`). Une première version (#10397) avait été annulée le même jour.

- `EventBus.subscribe(Class<T>, EventHandler<T>)` et `post(IModEvent)` ; `DefaultEventBus` distribue **sur la classe exacte**, attrape l'exception de chaque abonné, et n'offre **ni désabonnement, ni priorité, ni annulation** (`MC:api/eventbus/DefaultEventBus.java`).
- Chaque événement porte un `UUID` (`AbstractModEvent`) et des objets **vivants** (`AbstractCitizenModEvent` garde l'`ICitizenData`).
- Liste (`MC:api/eventbus/events/`) :
  - cycle : `ColonyManagerLoaded`, `ColonyManagerUnloaded`, `CustomRecipesReloaded` ;
  - colonie : `ColonyCreated`, `ColonyDeleted`, `ColonyNameChanged`, `ColonyFlagChanged`, `ColonyTeamColorChanged`, `ColonyPlayerRankChanged`, `ColonyViewUpdated` ;
  - bâtiments : `BuildingAdded`, `BuildingRemoved`, `BuildingConstruction` ;
  - citoyens : `CitizenAdded`, `CitizenRemoved`, `CitizenDied`, `CitizenBuried`, `CitizenJobChanged` ;
  - permissions : `PlayerEntering`, `PlayerLeaving`.

À comparer avec HyColony :
- `core/.../colony/ColonyEvents.java` : `ColonyCreated`, `ColonyDeleted`, `BuildingPlaced`, `BuildingRemoved`, `BuildingLevelChanged`, `WorkOrderCreated`, `DayStarted`, `NightFell`, plus `citizen/CitizenSpawned` ;
- même bus exact, un par monde, sans désabonnement (`kernel/event/EventBus.java:9-31`) ;
- un seul abonné aujourd'hui (`plugin/.../ui/citizen/CitizenInventoryWindows.java:112`).

### 1.4 Ce que les addons réels appellent

| Addon | Type | API utilisée | Internes touchés |
|---|---|---|---|
| colony4cc (https://github.com/uecasm/colony4cc, dernier commit 2022-04-04) | Lecture pour ComputerCraft | `IMinecoloniesAPI`, `IColony`, `IBuilding`, `IRequestManager`, `IWorkOrder`, recherche, permissions | `coremod.colony.Colony`, `AbstractBuildingStructureBuilder`, `BuildingBuilderResource` ; le paquet `coremod` a depuis été renommé `core`, ce qui casse ces imports |
| Advanced Peripherals (https://github.com/IntelligenceModding/AdvancedPeripherals, `dev/1.21.1`, 2026-09-28), `common/addons/minecolonies/MineColonies.java` | Lecture | `IColonyManager`, `IColony`, `ICitizenData`, `IVisitorData`, `IWorkOrder`, `IRequest`, recherche | `BuildingBuilder`, `BuildingBuilderResource`, `AbstractBuildingStructureBuilder` pour les ressources du bâtisseur (l. 349-400) ; `CitizenSkillHandler` (l. 167), parce que `ICitizenData.getCitizenSkillHandler().getSkills()` renvoie un type interne |
| colonist-thieves (https://github.com/Lovkar-Squid/colonist-thieves, 2026-09-27) | Contenu : un métier et sa hutte | 65 imports `api` (`BuildingEntry`, `ModuleProducer`…) | 22 imports `core` : `AbstractJob`, `AbstractBuilding`, `AbstractEntityAIBasic`, `CitizenAI`, `WorkerBuildingModule`, `SettingsModule`, `AbstractModuleWindow`… |

- Les addons de **style**, les plus nombreux, sont des **données** : un dossier `blueprints/<style>/pack.json` avec ses plans, sans Java (https://minecolonies.com/wiki/tutorials/schematics/style-packs/).
- MC ne soutient pas les addons qui touchent ses internes : « some addons modify Minecolonies in a reckless way, causing both stability and performance issues » (https://www.curseforge.com/minecraft/mc-mods/minecolonies).

### 1.5 Versions, cassures, regrets

- **Aucune politique de compatibilité écrite** : aucune note de version sur l'API, aucun outil de comparaison dans le build.
- Deux cassures majeures **au sein de la même version de Minecraft** (1.20.1) : la fin du jar `api` (#9749) et la fin des événements Forge (#10468).
- Dépréciations rares (11 `@Deprecated` dans `api/`) et sans date de retrait :
  - `IModuleContainer.getFirstModuleOccurance`, `getModuleMatching`, `getModulesByType` → `getModule(Class)` (l. 170-222) ;
  - `ModJobs.COOKASSISTANT_ID` (l. 64) ;
  - `ICraftingBuildingModule.getId` (l. 74).
- Ce que ces faits montrent : quand l'API ne couvre pas un besoin (ressources du bâtisseur, compétences, base d'un métier), les addons importent les internes, puis cassent au premier renommage.

### 1.6 Structurize, BlockUI, Domum Ornamentum

- **Structurize** (`version/main`) : source set `api` minuscule (`src/api/java/com/ldtteam/structurize/api/util`), `projectHasApi=true`.
- **BlockUI** (`version/main`) : pas d'API séparée (`projectHasApi=false`).
- **Domum Ornamentum** (`version/latest`) : source set `api`, entrée `IDomumOrnamentumApi.getInstance()`. Un `Holder` statique refuse une seconde initialisation : « Can not setup API twice! » (https://github.com/ldtteam/Domum-Ornamentum/blob/version/latest/src/api/java/com/ldtteam/domumornamentum/IDomumOrnamentumApi.java).

## 2. Ce que font les autres écosystèmes

| Écosystème | Découverte | Versions et dépréciation | Événements | Fils | Objets |
|---|---|---|---|---|---|
| **Fabric API** (https://github.com/FabricMC/fabric-api/blob/26.2/CONTRIBUTING.md) | Un module par fonction, version majeure dans le nom (`fabric-lifecycle-events-v1`), paquets `api`/`impl`/`mixin` ; `impl` est `@ApiStatus.Internal` d'office. Il existe même un `fabric-debug-api-v1` | « Modders should not need to update their source code when they update Fabric API ». Dépréciation sans `forRemoval`, modules retirés rangés dans `deprecated/`. Module expérimental : `@ApiStatus.Experimental` sur les classes, avertissement en Javadoc, drapeau dans `fabric.mod.json` | `Event<T>` avec un invocateur, des phases ordonnées (`Event.java:57-88`), une interface de rappel par événement. **Aucun désabonnement** (les mods ne se déchargent pas) | Événements serveur sur le fil principal | `@ApiStatus.NonExtendable` : ajouter une méthode à une interface qu'on n'implémente pas n'est pas une cassure |
| **NeoForge** (https://docs.neoforged.net/docs/concepts/events/) | Bus « game » et bus du mod ; registres différés ; capacités typées (`BlockCapability`, `EntityCapability`) enregistrées dans `RegisterCapabilitiesEvent`, avec cache et invalidation (https://neoforged.net/news/20.3capability-rework/) | Refonte des capacités en 20.3 pour « fixing all the issues that were found in the previous iteration » | Priorités de `HIGHEST` à `LOWEST`, annulation (`ICancellableEvent`) | Les événements du bus du mod tournent **en parallèle** ; `enqueueWork` repasse sur le fil principal | Vivants |
| **Bukkit / Paper** | `ServicesManager.register(Class, provider, plugin, ServicePriority)`, le fournisseur de plus haute priorité l'emporte (https://hub.spigotmc.org/javadocs/spigot/org/bukkit/plugin/ServicesManager.html) ; `paper-plugin.yml` : `load`, `required`, `join-classpath`, isolation des chargeurs de classes par défaut (https://docs.papermc.io/paper/dev/getting-started/paper-plugins/) | Plugins Paper marqués « experimental » | Événement personnel avec `HandlerList` statique, `callEvent()` rend `false` si l'événement est annulé (https://docs.papermc.io/paper/dev/custom-events/) | Un événement asynchrone déclenché depuis du code synchrone lève `IllegalStateException` (https://jd.papermc.io/paper/1.21.4/org/bukkit/event/Event.html) | Vivants |
| **Sponge** | Services, `@Listener` | - | Chaque événement porte sa **`Cause`** et un `EventContext` : qui a provoqué quoi (https://docs.spongepowered.org/stable/en/plugin/event/causes.html) | - | **Instantanés immuables** (`EntitySnapshot`, `BlockSnapshot`) à côté des objets vivants : « may be snapshotted of a World that is not currently loaded » (https://jd.spongepowered.org/spongeapi/9.1.0-SNAPSHOT/org/spongepowered/api/entity/EntitySnapshot.html) |
| **LuckPerms**, dispo sur Hytale (https://www.curseforge.com/hytale/mods/luckperms) | Artefact `net.luckperms:api` séparé ; `LuckPermsProvider.get()` lève `IllegalStateException` si l'API n'est pas chargée (https://github.com/LuckPerms/wiki/blob/master/pages/Developer-API.md, l. 127-132) | **Semver** de l'API : même majeure = pas de cassure (l. 7) | `subscribe(plugin, Class, handler)` rend un `EventSubscription` fermable, désabonné automatiquement quand le plugin se désactive (`api/.../event/EventBus.java`, l. 52-72) | Événements asynchrones ; méthodes lourdes en `CompletableFuture` (l. 141-231) | Collections rendues immuables (l. 148) |
| **JEI** (https://github.com/mezz/JustEnoughItems/wiki/Creating-Plugins-%5B1.13-and-Up%5D) | L'hôte **découvre** ses addons : annotation `@JeiPlugin`, ou point d'entrée `jei_mod_plugin` sous Fabric | Méthodes par défaut vides dans `IModPlugin` : l'interface grandit sans casser | - | - | - |
| **Hytale, communauté** | EcoAPI : holder statique `IEcoAPI.Service.getInstance()` / `setInstance` (https://www.curseforge.com/hytale/mods/ecoapi) ; HyVault, « Vault » pour Hytale, sans doc technique publique (https://www.curseforge.com/hytale/mods/hyvault) | EcoAPI en `1.0-SNAPSHOT`, sans politique | - | - | - |

À reprendre :
1. une **frontière de compilation** entre l'API et l'implémentation (Fabric, LuckPerms, MC ≤ 1.19) ;
2. le **semver de l'API** et une dépréciation longue (Fabric, LuckPerms) ;
3. un **statut expérimental** explicite pour ce qui peut bouger (Fabric) ;
4. un abonnement **fermable, lié au cycle de vie de l'addon** (LuckPerms) ;
5. des **instantanés immuables** plutôt que des objets vivants (Sponge, LuckPerms) ;
6. la **cause** d'un changement (Sponge) ;
7. une **règle de fil** vérifiée à l'exécution (Paper, et Hytale lui-même, § 3) ;
8. des interfaces de rappel à méthodes par défaut (JEI).

## 3. Hytale 0.7.0-pre.4 : un plugin qui en utilise un autre (vérifié)

| Sujet | Fait | Source |
|---|---|---|
| Manifeste | `Dependencies`, `OptionalDependencies` et `LoadBefore` (identifiant → plage semver), plus `SubPlugins` | `common/plugin/PluginManifest.java:74-99, 343` |
| Plages de versions | `^` suit npm (`^1.2.0` = `>=1.2.0 <2.0.0` ; `^0.2.0` = `<0.3.0`), plus `~`, `a - b` et `\|\|` | `common/semver/SemverRange.java:95-172` |
| Ordre | Arêtes vers les dépendances dures ; vers une dépendance optionnelle **seulement si elle est présente** ; `LoadBefore` inversé ; les plugins `Hytale` du classpath passent d'abord ; ordre alphabétique à égalité | `common/plugin/Mod.java:60-90, 133` |
| Setup et start | Le `setup` d'un plugin n'a lieu que si ses dépendances dures sont en `SETUP`, son `start` que si elles sont `ENABLED` ; sinon « lacking dependency », puis **DISABLED** | `server/core/plugin/PluginManager.java:1130-1206` |
| Dépendance absente, ou en mauvaise version | `MissingPluginDependencyException`, journalisée SEVERE, et le plugin est écarté. Mais un mod **qui a un pack** fait échouer l'ordre des packs, et **le serveur s'arrête** (`plugin-b-api.md` § 28.4). Le code est identique en U7 | `PluginManager.java:186-195, 442-515` ; `server/core/asset/AssetModule.java:498-521` |
| Plugin qui échoue | Une exception dans `setup` ou `start` le met en `FAILED`, et le serveur s'arrête (`MOD_ERROR`), sauf avec l'option `IGNORE_BROKEN_MODS` | `PluginBase.java:410-446` ; `PluginManager.java:282-309, 366-372` ; `server/core/Options.java:159` |
| Désactivé dans la config du serveur | Ignoré (« Disabled by server config ») ; ceux qui en dépendent échouent ensuite à la validation | `PluginManager.java:124-141` |
| Chargement des classes | Un `PluginClassLoader` (un `URLClassLoader` nommé `ThirdParty(...)`) cherche dans le serveur, puis son jar, puis le pont : `Dependencies`, puis `OptionalDependencies`, puis **tous les plugins chargés** | `PluginClassLoader.java:21, 54, 83-130` ; `PluginManager.java:1263-1300` ; une seule copie d'une classe d'API, vue en production (`plugin-b-api.md` § 28.2) |
| JPMS | Un `URLClassLoader` charge dans le module anonyme : un `module-info` n'aurait aucun effet à l'exécution | `PluginClassLoader.java:21`, https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ClassLoader.html#getUnnamedModule() |
| Trouver l'autre plugin | `PluginManager.get().getPlugin(PluginIdentifier)` rend `@Nullable PluginBase` ; il existe aussi `getPlugin(Class)` et `hasPlugin(id, plage)`. `PluginSetupEvent` est émis après le setup de chaque plugin, avec sa classe pour clé | `PluginManager.java:772-802, 1150-1155` |
| Services | **Aucun registre de services** dans le serveur. Seul `ServiceLoader` sert, pour les `ClassTransformer` des plugins précoces | `plugin/early/EarlyPluginLoader.java:40` |
| Déchargement, rechargement | `unload` décharge d'abord ceux qui en dépendent **en dur**, puis ferme le chargeur de classes. `reload` = `unload` puis `load` de ce **seul** plugin : ses dépendants ne reviennent pas. Un dépendant **optionnel** reste chargé, avec des références périmées | `PluginManager.java:864-924` |
| Nettoyage à l'arrêt d'un plugin | Tout ce qui passe par ses registres (événements, commandes, tâches, systèmes) est défait à son arrêt | `PluginBase.java:74-92, 469-481` ; `registry/Registry.java:49-75` |
| Lier un nettoyage à un autre plugin | `EventRegistry.register(EventRegistration)` ne touche pas au bus : il ajoute seulement la désinscription à la liste d'arrêt du plugin. HyColony peut donc y accrocher la fermeture d'un abonnement **[in-game]** | `event/EventRegistry.java:29-31` ; `event/EventRegistration.java:11` ; `registry/Registration.java` |
| Événements d'un plugin | Le vanilla définit ses propres événements `IEvent<String>` (clé = nom du monde). Il les émet par `getEventBus().dispatchFor(Classe, monde)`, seulement si `hasListener()` | `builtin/crafting/component/CraftingManager.java:208-212` ; `builtin/triggervolumes/event/TriggerVolumeEvent.java:12`, `.../system/TriggerVolumeTickingSystem.java:1337-1339` |
| Fils | Un monde = un fil (`World extends TickingThread implements Executor`). `world.execute` lève une exception quand le monde n'accepte plus de tâches. Le magasin d'entités lève `IllegalStateException("Assert not in thread!")` hors de son fil | `server/core/universe/world/World.java:119, 1120-1126` ; `component/Store.java:2310-2319` |
| Permissions | Chaque plugin a une permission de base `groupe.nom`, en minuscules | `PluginBase.java:122-128, 382` |

Conséquences pour HyColony Debug :
- **Dépendance dure** sur `HyColony:hycolony` :
  - ses classes voient l'API par le pont ;
  - il est déchargé avec HyColony ;
  - son pack (HUD, `.lang`, mode de jeu) arrête le serveur si HyColony manque, comme HyColony sans HyDomum (§ 28.4). C'est acceptable pour un outil de développement, à écrire dans la doc d'installation.
- Une dépendance **optionnelle** éviterait l'arrêt, mais le mod devrait alors ne toucher aucune classe `dev.hycolony.api` sans HyColony (`NoClassDefFoundError`) et survivre à un `reload` de HyColony.
- HyColony peut être **chargé mais inactif** : un id manque, et `WorldRuntimes.enableIfIdsValid` coupe tout (`plugin/.../WorldRuntimes.java:75-91`). Ses runtimes naissent au `StartWorldEvent`, pas au `setup` (`HyColonyPlugin.java:87-121`). L'API doit donc dire « disponible » **par monde**.

Essais en production à ajouter au § 28 (5 jars dans `mods/`) :
1. le chargeur de classes et l'`identityHashCode` d'un type d'API, vus de HyColony et du mod de débogage ;
2. le mod de débogage sans HyColony : arrêt attendu, avec le message du journal ;
3. `/plugin unload` du mod de débogage : plus aucun rappel, aucune exception ;
4. `/plugin reload` de HyColony : le mod de débogage est déchargé et ne revient pas ;
5. HyColony inactif (id manquant) : l'API répond « indisponible ».

## 4. Garder l'API stable dans le build

| Outil | Principe | Pour nous |
|---|---|---|
| `checkModApis` (existant) | Refuse tout import d'un autre mod hors de ses paquets `api` (`build-logic/.../hy.java-checks.gradle.kts:96-131`) | Passer `"dev.hycolony."` à `listOf("dev.hycolony.api.", "dev.hycolony.plugin.api.")`. **Piège** : un mod de débogage dans `dev.hycolony.debug` serait pris pour HyColony, et ses propres imports seraient refusés. Il lui faut un espace à part (`dev.hycolonydebug`), une entrée `modApis` et une entrée NullAway `AnnotatedPackages` (l. 35) |
| ArchUnit (déjà dans les cœurs) | Une règle « `dev.hycolony.api..` ne dépend que de `java..`, `org.jspecify..` et d'elle-même » | Empêche le travers de MC (§ 1.1) ; coût quasi nul |
| Projet Gradle `:api` séparé | Frontière de compilation (MC ≤ 1.19, LuckPerms, Fabric) | Seul moyen pour qu'un auteur **extérieur** compile contre l'API seule. Sinon il compile contre le jar du cœur et peut tout importer, comme colonist-thieves (§ 1.4) |
| Empreinte texte de l'API (façon BCV) | `apiDump` écrit la signature publique dans un fichier versionné ; `apiCheck` échoue sur toute différence. BCV (https://github.com/Kotlin/binary-compatibility-validator) est Kotlin, en maintenance, et remplacé par la validation d'ABI du plugin Kotlin | L'idée sans la dépendance : une tâche de `build-logic` qui liste les signatures publiques des classes compilées de `dev.hycolony.api`, par réflexion ou par `javap -public` de la chaîne d'outils. Tout changement se voit en revue |
| japicmp (https://github.com/melix/japicmp-gradle-plugin, 0.4.x) | Compare un jar de référence (`oldClasspath`) au nouveau ; `failOnModification`, `failOnSourceIncompatibility`, rapport semver, `annotationExcludes` | Utile dès qu'il y aura des versions publiées : la base est le jar de la dernière version |
| Revapi (https://github.com/palantir/gradle-revapi) | Compare au jar de la dernière étiquette git (`git describe`) | Même prérequis ; plus lourd |
| JPMS `module-info` | Contrôle des `exports` | Sans effet à l'exécution dans Hytale (§ 3) |
| Politique | `@since` sur chaque type ; dépréciation longue sans `forRemoval` (Fabric) ; un marqueur `@Experimental` exclu du contrôle (`nonPublicMarkers` de BCV, `annotationExcludes` de japicmp) | Notre propre annotation dans l'API : pas besoin de `org.jetbrains:annotations` |

**Versions** :
- les dépendances de Hytale portent sur la **version du mod** (§ 3) ;
- la version du mod suit donc le semver de l'API, avec une constante d'exécution `ApiVersion` pour les dépendances optionnelles ;
- tant que les cinq mods sortent ensemble avec des versions exactes (`==0.1.0`, split spec), la compatibilité n'engage personne d'autre. L'empreinte sert alors à **rendre visible** chaque changement, pas à l'interdire.

## 5. Choix de conception

Inventaire des questions qu'une API doit trancher :
1. découverte ;
2. disponibilité et cycle de vie ;
3. fils ;
4. lecture ;
5. identité ;
6. événements ;
7. actions et permissions ;
8. contenu ;
9. données d'addon ;
10. textes ;
11. pont Hytale ;
12. versions ;
13. documentation et exemples ;
14. isolation des erreurs ;
15. performance.

| Question | Options | Pour / contre | Proposé |
|---|---|---|---|
| Lieu | (a) paquet dans `:core`, comme HyDomum ; (b) projet `:api`, embarqué dans le jar de HyColony | (a) ne coûte rien, mais ne laisse qu'un contrôle d'imports, qu'un auteur extérieur n'a pas ; (b) coûte un `build.gradle.kts` et donne un jar publiable | (b) pour le Java pur ; `dev.hycolony.plugin.api` dans `:plugin` pour le pont Hytale, gardé minuscule |
| Entrée | Holder statique (MC, DO, EcoAPI, LuckPerms) ; `PluginManager.getPlugin(id)` | Le holder statique est simple, mais périmé après un `reload` ; la recherche par identifiant est toujours fraîche, mais demande un cast | Holder statique `HyColonyApi`, rempli au `setup` et vidé au `shutdown`, qui lève `IllegalStateException` quand il est vide (LuckPerms). La dépendance dure garantit l'ordre |
| Disponibilité | Globale ; par monde | Les runtimes sont par monde et HyColony peut être inactif | `world(World)` rend `Optional<ColonyWorld>` ; événements `ColonyWorldStarted` et `ColonyWorldStopped` (MC `ColonyManagerLoaded/Unloaded`) |
| Fils | Tout accepter et marshaller ; exiger le fil du monde | Tout l'état vit sur ce fil sans verrou (CLAUDE.md § 4) | Chaque appel vérifie le fil et lève `IllegalStateException` ailleurs (précédent : `Store.assertThread`). `world.execute` reste à l'addon. **Écart voulu** avec « un port ne lève jamais » : l'API n'est pas un port, et un appel hors du fil est une faute de programmation |
| Lecture | Objets vivants (MC) ; instantanés (Sponge, nos vues `app/view`) | Le vivant fige l'interne et laisse muter sans permission ; l'instantané coûte une allocation par lecture, ce qui suffit pour un HUD rafraîchi toutes les 10 ticks | Records immuables, listes copiées, pris sur le fil du monde |
| Identité | Objets ; identifiants | `BodyId` est un compteur de session (`plugin/.../adapter/HytaleCitizenBodies.java:76-85`) : jamais exposé | `ColonyRef(world, colonyId)`, `CitizenRef(world, colonyId, citizenId)`, position de hutte, id de requête en texte, ordre de travail |
| Événements | Bus du cœur plus `Subscription` ; événements Hytale natifs (`IEvent<String>`, § 3) | Le bus du cœur reste testable en Java pur, mais doit gagner un désabonnement ; les événements natifs offrent le nettoyage automatique, les priorités et `hasListener()`, au prix d'une classe Hytale par événement | Records purs dans `dev.hycolony.api.event`, publiés après le changement, sur le fil du monde, chaque abonné isolé par un `try`. `subscribe(owner, …)` lie la fermeture au plugin propriétaire (§ 3). Événements natifs plus tard, si des addons en ont besoin |
| Cause | Aucune (MC) ; `Cause` (Sponge) | Utile pour distinguer un joueur, une action d'addon, l'IA | Champ `Actor cause` dans les événements de changement |
| Actions | Méthodes qui lèvent ; résultat typé | Un addon doit savoir pourquoi c'est refusé | `ActionResult` scellé (`Done`, `Refused(ApiText)`, `NotFound`, `Unavailable`) ; `Actor` = joueur (permissions de colonie, `Permissions.hasPermission`, `colony/permission/Permissions.java:161`) ou système (plugin nommé) |
| Contenu | Données (styles, prefabs, fragments id-map et artisanat) ; code (types de bâtiment et de métier) | Les données sont peu risquées et couvrent l'addon le plus courant chez MC (§ 1.4). Le code exige d'exposer `JobAI`, `WorkerMachine`, les modules et les requêtes : c'est là que MC fuit | D'abord les données : fragments `Server/HyColony/*.json` lus dans **tous** les packs (`AssetModule.getAssetPacks()`, l. 258 ; `AssetPack.getRoot()`, `getManifest()`, `assetstore/AssetPack.java:49, 59`) **[in-game]**, sur le modèle de `SubPlugins`. `FeaturePack` (`core/FeaturePack.java`) est déjà le SPI interne ; l'ouvrir vient plus tard, en expérimental |
| Données d'addon | Aucune ; attachements par colonie ou citoyen (Fabric `data-attachment-api`) | La lecture tolérante garde déjà les clés inconnues (spec sous-plugins, point 1) | Plus tard |
| Textes | Chaînes ; clés | `Msg` est un type du cœur (`kernel/port/Msg.java`) ; les clés d'un autre mod s'affichent (`%hycolony.x`, § 28.3) | `ApiText(key, params)` |
| Pont Hytale | - | Le tag `CitizenTag(colonyId, citizenId)` est sur chaque corps (`plugin/.../npc/CitizenTag.java`, posé l. 116 de `HytaleCitizenBodies`) ; `WorldKey` = `World.getName()` | `citizenOf(Ref, accessor)`, `bodyOf(CitizenRef)`, `world(World)` |
| Documentation | Javadoc ; guide ; addon d'exemple | LuckPerms a un « api-cookbook » | Le mod de débogage **est** l'exemple. Langue du guide à trancher : `docs/` est en français (CLAUDE.md § 9.6), les auteurs d'addons sont internationaux |

## 6. Ce que le mod de débogage demande à l'API

Numéros de `debug-mod.md` § 8. **L** = lecture, **É** = événement, **A** = action, **H** = Hytale seul, dans le mod de débogage.

| # | Fonction | Demande à l'API | Internes touchés (cœur ou plugin) |
|---|---|---|---|
| 1 | Marche finie loin | É `WalkEnded(citizen, target, pos, navStatus, reason, distance)` | `BodyWalker.walkTo`, `arrive`, `unstick` (`kernel/nav/BodyWalker.java:65-171`) n'ont ni colonie ni citoyen : il faut un observateur injecté par `BuilderWalker`, `CraftingWorkContext` et `CourierContext`. Le mode « finit ailleurs » de `FakeBodies` reste dans les tests |
| 2 | Historique horodaté | L `history(citizen)` → `HistoryEntry(tick, kind, from, to, detail)` ; A `track(citizen, on)` (MC ne l'active que pour un joueur en mode débogage, `EntityCitizen.java:407-413`) | `TickRateStateMachine.history()` (20 entrées, sans horodatage, `kernel/ai/TickRateStateMachine.java:165`) ; `WorkerMachine` ; `CitizenAI` |
| 3 | Caméra qui suit | `bodyOf(citizen)` → `Ref` | H : mécanisme de `/spectate` |
| 4 | HUD « ce qu'il pense » | L `CitizenDebugSnapshot` : état, étape du métier, activité, cible, `NavStatus`, niveau d'anti-blocage, file du métier, loisir, 5 dernières transitions, alertes | `CitizenAI.state` ; `JobAI.stateName()` (une chaîne : **inutile de rendre publiques** les énumérations comme `CourierState`, qui est package-private, `logistics/courier/CourierState.java:6`) ; `JobAI.describe` ; champs privés `navTarget`, `settled`, `arrived` de `BodyWalker` et `level` de `StuckHandler` (à lire) ; file du livreur (`CourierTasks`, package-private) par une méthode par défaut sur `JobAI` ; `CitizenData.leisureTime()` |
| 5 | Dessins | L position du corps, cible, zone de la hutte | `CitizenBodies.position`, bornes du bâtiment |
| 6 | Invariants | L `check(colony)` → `Violation(code, subject, detail)` ; É `InvariantViolated` | Nouveau paquet `diagnostics` **dans le cœur** (testé, branché dans les simulations) : `RequestManager.get`, `resolverOf`, `assignedTo` (`request/RequestManager.java:191-216`), `CitizenManager.bodyOf`, `WorkOrder.claimedBy`, règles de `heal`. Seul le rendu part dans le mod de débogage |
| 7 | Pause, pas à pas | A `pause(world)`, `step(world, n)`, `resume(world)` (plugin) | `WorldRuntime.tickCore` et les accumulateurs de `ColonyTickSystem` (`plugin/.../ColonyTickSystem.java:27-43`). H : `Frozen` sur les corps, par `bodyOf` |
| 8 | Menu | L liste des colonies et des citoyens, avec leurs alertes | Vues existantes (`app/ui/CitizenView`), à convertir en instantanés d'API |
| 9 | « Envoyer ici » | A `walkTo(citizen, pos, range)` : une transition unique dans `CitizenAI`, portée 4, 3 min au plus, puis pause de la navigation 100 ticks (MC `CommandCitizenTriggerWalkTo`) | `CitizenAI`. H : filtre `PacketAdapters`, repères de carte |
| 10, 11 | Mode de jeu, première personne | - | H seulement |
| 12 | Actions | A `forceLeisure`, `overrule(request, items)`, `giveItem`, `teleport`, `respawnBody`, `resetRequests` | `CitizenData.leisureTime` ; `RequestManager.overrule` (l. 122-130) ; `CitizenInventoryActions` ; `CitizenBodies` ; `reset` n'existe pas : à porter de MC `CommandRSReset` |
| 13 | Graphe des requêtes | L `RequestSnapshot(id, state, requester, resolver, parent, children, courier)` | `RequestManager.all`, `resolverOf`, `assignedTo` |
| 14 à 20 | Relecture, carte de chaleur, repères, vue du dessus, couches, temps d'IA, rôle A* | L historique avec traces de marche ; É `StuckAction(citizen, level, action, pos)` ; L temps par tick | `StuckHandler` ; mesure autour de `tickCore` |

À retenir : la séparation déplace la **présentation** (caméra, HUD, dessins, menus, carte, mode de jeu) dans le 5e mod. Le **diagnostic** (historique, invariants, `WalkEnded`, pause) reste dans HyColony, où il est testé avec les `Fake*`.

## 7. Garde-fous et processus

Chaque ligne ci-dessous demande l'accord explicite de l'utilisateur et une session déverrouillée (`HYCOLONY_GUARDRAILS_UNLOCKED=1`, CLAUDE.md § 10).

| Fichier (garde-fou) | Changement |
|---|---|
| `CLAUDE.md` § 1 | « Quatre mods » devient cinq, avec le graphe (HyColony Debug → HyColony) et les paquets `api` de HyColony. Nouvelle règle : politique de l'API (semver, empreinte, `@Experimental`, fil du monde) |
| `CLAUDE.md` § 7 | `.lang` et id-map du nouveau mod |
| `AGENTS.md` | Section « Four mods » |
| `build-logic/.../hy.java-checks.gradle.kts` | `modApis` (HyColony et le nouveau mod), NullAway `AnnotatedPackages`, puis les tâches `apiDump` et `apiCheck` |
| `.claude/agents/hycolony-implementer.md`, `hycolony-reviewer.md` | Chemins des modules, commande de build, règles de l'API à relire |
| `.claude/skills/add-lang-key`, `hytale-api` | Table des `.lang`, liste des mods |
| `.claude/hooks/guard.js` | `config.json` et `.bak` du nouveau mod dans `PROTECTED`. Constat : ceux de `vanilla/plugin` n'y sont pas non plus aujourd'hui |

Hors garde-fous :
- `settings.gradle.kts` : `:api` et `:debug-plugin` ;
- leurs `build.gradle.kts` ;
- la spec et le plan ;
- `docs/TESTING.md` et `plugin-b-api.md` § 28 (essais en production).

## Proposition

### Portée de l'API v1 (pour le mod de débogage)

- **`:api`** (Java pur, `dev.hycolony.api`, jspecify seul, `@since 0.x`) :
  - `dev.hycolony.api` : `ColonyWorld` (lectures et actions d'un monde), `ColonyRef`, `CitizenRef`, `Pos`, `ApiText`, `Actor`, `ActionResult`, `Subscription`, `ApiVersion`, `@Experimental` ;
  - `dev.hycolony.api.read` : `ColonySummary`, `CitizenSnapshot`, `BuildingSnapshot`, `RequestSnapshot` ;
  - `dev.hycolony.api.event` :
    - `ColonyCreated`, `ColonyDeleted`, `BuildingPlaced`, `BuildingRemoved`, `BuildingLevelChanged`, `WorkOrderCreated`, `CitizenSpawned`, `DayStarted`, `NightFell` (l'existant) ;
    - `CitizenStateChanged`, `JobStateChanged`, `WalkEnded`, `StuckAction`, `RequestStateChanged` ;
  - `dev.hycolony.api.debug`, `@Experimental` : `CitizenDebugSnapshot`, `HistoryEntry`, `Violation`, `DebugActions` (`walkTo`, `track`, `forceLeisure`, `teleport`, `respawnBody`, `overrule`, `resetRequests`).
- **`dev.hycolony.plugin.api`** (`:plugin`) :
  - `HyColonyApi` : `world(World)`, `citizenOf(Ref, accessor)`, `bodyOf(CitizenRef)` ;
  - `subscribe(PluginBase owner, World, Class, Consumer)` ;
  - `ColonyClock` : `pause`, `step`, `resume` ;
  - `ColonyWorldStarted` et `ColonyWorldStopped`.
- **Le cœur implémente** l'API, avec l'historique, `diagnostics` et `walkTo`, tous testés TDD. Le bus du cœur gagne un désabonnement.

### Contrôle de compatibilité

1. **Dès la v1** : `checkModApis` à jour, une règle ArchUnit sur `:api`, et `apiDump`/`apiCheck` en texte dans `build-logic`, sans nouvelle dépendance. Le paquet `debug` en est exclu par `@Experimental`.
2. **Dès des addons extérieurs** : la version du mod suit le semver de l'API ; japicmp contre le jar de la dernière version publiée ; publication d'un jar `hycolony-api` avec sources et Javadoc.

### Feuille de route

Valeur : ★★★ indispensable ; ★★ forte ; ★ confort. Coût : faible (< 1 jour), moyen (quelques jours), élevé.

| Palier | Élément | Valeur | Coût | Faisabilité |
|---|---|---|---|---|
| v1 | `:api` + `HyColonyApi` + disponibilité par monde + règle de fil | ★★★ | faible | vérifié (§ 3) |
| v1 | Instantanés de lecture (colonies, citoyens, requêtes) | ★★★ | moyen | vérifié (cœur) |
| v1 | Événements existants + `Subscription` liée au propriétaire | ★★★ | faible | vérifié ; nettoyage **[in-game]** |
| v1 | `WalkEnded`, historique, `CitizenDebugSnapshot`, `diagnostics`, `walkTo`, pause et pas à pas | ★★★ (bugs A et B) | moyen | vérifié (cœur et plugin) |
| v1 | Garde-fous (§ 7), `apiDump`/`apiCheck`, essais en production 1 à 5 (§ 3) | ★★★ | moyen | à faire avec l'utilisateur |
| v2 | Données d'addon extérieures : styles, prefabs, id-map, artisanat lus dans tous les packs | ★★★ (l'addon le plus courant chez MC) | moyen | sources vérifiées, **[in-game]** |
| v2 | Parité d'événements avec MC : nom, rang, joueur qui entre ou sort, citoyen mort, métier changé | ★★ | faible | vérifié (MC) |
| v2 | Actions de joueur avec permissions (renommer, rangs, ordres de travail) | ★★ | moyen | vérifié (cœur) |
| v2 | Jar `hycolony-api` publié, semver, japicmp, guide pour les auteurs | ★★ | moyen | outils vérifiés |
| Plus tard | Contenu en code : `FeaturePack` public (types de bâtiment et de métier, base d'IA, modules, onglets de hutte), en expérimental | ★★ | élevé | fuite certaine (MC, § 1.4) |
| Plus tard | Résolveurs de requêtes, interactions, effets de recherche, attachements de données par colonie, événements annulables « pre », événements Hytale natifs | ★ | moyen à élevé | à étudier |

## Non vérifié

- **[in-game]** :
  - la fermeture d'un abonnement accrochée au registre d'événements de l'addon ;
  - la lecture de fragments `Server/HyColony/*.json` dans les packs d'autres mods, et leur ordre ;
  - les cinq essais en production du § 3.
- Le coût exact des instantanés pour un HUD à 10 ticks.
- Les addons MC lus ne sont qu'un échantillon (trois dépôts). La documentation technique de HyVault est introuvable.
- L'empreinte texte (`javap` ou réflexion) est une idée de tâche : elle n'a pas été écrite ni essayée.
