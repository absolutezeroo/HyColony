# Audit global HyColony : 02, axe D, état statique mutable et cycle de vie des plugins

```
ÉTAT : phase 2, axe D écrit. Code audité : commit 3e2e70ca. Sources : phase 0 (cmd 5, 6, 10), rapport « ECS »
(vérifications dans build/vineflower : PluginBase.cleanup, BuilderFactory, PluginManager.reload), relecteurs
plugin et mods frères. Chemins relatifs à la racine du dépôt.
```

**Note de l'axe : 4/5.** Aucun singleton, aucun `getInstance` ; l'état de jeu est entièrement par monde (`WorldRuntime`) ; les 2 champs statiques non finaux et les 3 conteneurs statiques sont de portée serveur voulue (effet de surbrillance, drapeaux « log une fois », marqueurs de carte) ; `PluginBase.cleanup` défait tout ce qui passe par les proxies. Deux enregistrements échappent aux proxies et cassent le rechargement.

## 1. Constats

### D-1 — MOYEN — Un rechargement de HyColony fait échouer `setup()` : le capteur NPC ne s'enregistre qu'une fois
`plugin/src/main/java/dev/hycolony/plugin/HyColonyPlugin.java:60`
```java
NPCPlugin.get().registerCoreComponentType("HyColonyTarget", BuilderSensorHyColonyTarget::new);
```
Mécanisme : `NPCPlugin.registerCoreComponentType` (`server/npc/NPCPlugin.java:1943-1947`) → `BuilderFactory.add`, qui lève `IllegalArgumentException("Builder with name %s already exists")` (`server/npc/asset/builder/BuilderFactory.java:41-44`) ; `BuilderFactory` n'a aucun retrait. `PluginManager.reload` existe (`PluginManager.java:864`, `shutdown0(false)` :891) et `PluginBase.cleanup` (:466-481) ne vide que les registres proxy du plugin (commandes, événements, entity store, codecs, assets), pas celui du NPCPlugin.
Impact : après `/plugin reload`, `setup()` lève → HyColony `FAILED`, colonies inertes jusqu'au redémarrage ; `shutdown()` (:143-145) et `SubPlugins.unregisterAssets` (:201-220), écrits « pour qu'un reload retrouve nos packs », n'ont alors aucun effet utile. Même sans l'exception, le capteur resterait celui de l'ancien classloader.
Règle : § 4 (aucun état bloqué), § 10 (garde-fou : le rechargement n'est pas dans `docs/TESTING.md`). Remède : attraper l'`IllegalArgumentException`, journaliser « reload not supported for the NPC sensor » et le dire dans la Javadoc de `shutdown()` ; ou documenter que le rechargement n'est pas supporté.
Sévérité MOYEN (rechargement seulement) · effort S · confiance HAUTE · DÉJÀ CONNU non.

### D-2 — MOYEN — Après un rechargement, HyDomum reste sans catalogues ni variantes jusqu'au redémarrage
`domum/plugin/src/main/java/dev/hydomum/plugin/HyDomumPlugin.java:87-94`
```java
getEventRegistry().register(LoadAssetEvent.PRIORITY_LOAD_LATE, LoadAssetEvent.class, e -> {
    try { ornaments.start(new OrnamentVariantRegistry.Catalogs(ShapeManifest.load(), MaterialCatalog.load(ids.ornamentTags()))); }
```
Mécanisme : `unload` → `AssetStore.removeAssetPack("HyColony:hydomum")` (`AssetModule.java:203-207`, `AssetStore.java:726-738`) retire les BlockType/Item chargés sous `packKey` et `CommonAssetModule.removeCommonAssets` (:104-129) les PNG ; `load` → `setup()` ré-enregistre un écouteur `LoadAssetEvent`, dispatché une seule fois au boot (`HytaleServer.java:350-353`) et refusé ensuite (`AssetModule.java:499-500`). `catalogs` reste vide, sans log.
Impact : l'établi et `/hydomum` répondent « Failed at step: load » ; `variants.json` n'est jamais rejoué → les blocs de variantes posés deviennent « Unknown » (`VariantStore.java:17-18` le décrit) jusqu'au redémarrage.
Règle : § 4 (échec silencieux, sans sortie). Remède : dans `registerVariants`, si les assets sont déjà chargés (`BlockType.getAssetMap().getAsset(CutterSystem.CUTTER) != null`), appeler `ornaments.start(...)` directement.
Sévérité MOYEN · effort S · confiance HAUTE (mécanisme), MOYENNE (blocs Unknown non observés) · DÉJÀ CONNU non.

### D-3 — BAS — Trois conteneurs qui ne se vident jamais
`plugin/src/main/java/dev/hycolony/plugin/ui/highlight/Highlights.java:27` ; `plugin/src/main/java/dev/hycolony/plugin/WorldRuntime.java:64` ; `plugin/src/main/java/dev/hycolony/plugin/ColonyTickSystem.java:20` ; `core/src/main/java/dev/hycolony/core/kernel/nav/DetouringBodies.java` (cartes `legs`, `replans`, `refusedFrom`)
```java
private static final Map<UUID, Active> ACTIVE = new ConcurrentHashMap<>();
```
Mécanisme : `Highlights.ACTIVE` n'est purgée qu'à l'expiration (60 s) lue par le thread de la carte, jamais à la déconnexion, au retrait d'un monde ni à l'arrêt ; `addMarkerProvider(HighlightMarkers.KEY, …)` n'est jamais retiré (remplacé à la recréation, `WorldMapManager.java:83,304`) ; `ColonyTickSystem.accumulators` est indexée par nom de monde et jamais nettoyée au retrait ; les cartes de détour de `DetouringBodies` ne sont vidées que par `forget` (dernière étape, BLOCKED/FAILED, `lookAt`, `teleport`, `despawn`) : un corps déchargé en plein détour laisse son `Plan` pour la vie du monde (les `BodyId` ne sont jamais réutilisés, donc pas de comportement faux).
Impact : croissance lente, bornée par le nombre de joueurs/mondes/corps ; aucune fuite entre rechargements (D-1 les empêche de toute façon).
Règle : § 4 (aucune accumulation par corps). Remède : `Highlights.forget(uuid)` sur `PlayerDisconnectEvent` ; `accumulators.remove` dans `WorldRuntimes.remove` (ou un champ `float` dans `WorldRuntime`, K-7) ; `DetouringBodies.forget(BodyId)` appelé au déchargement (`onBodyUnloaded`).
Sévérité BAS · effort S · confiance HAUTE · DÉJÀ CONNU non.

### D-4 — BAS — Drapeaux « log une fois » : trois conventions
`blockui/src/main/java/dev/hyblockui/api/UiSounds.java:18` (`static boolean warned`, non volatile) ; `PageEvents.java:14`, `InventoryMoves.java:22` (`AtomicBoolean`) ; `plugin/.../adapter/HytaleWorldEffects.java` (`warned` d'instance)
```java
private static boolean warned;
```
Mécanisme : course bénigne (quelques WARNING de plus) entre threads de monde ; incohérent avec les voisins.
Règle : § 4 (état partagé hors thread de monde). Remède : `AtomicBoolean`, ou un petit `WarnOnce` de HyBlockUI.
Sévérité BAS · effort S · confiance HAUTE · DÉJÀ CONNU non.

## 2. Non retenus

- `GlowingBlock.effect` (static volatile, écrit une fois dans `setup()`), `HyColonyComponents.citizenTag/moveTarget` (`ComponentType` statiques, assignés dans `setup()`), `BenchItems.byBench` (cache paresseux idempotent), `HytaleBlueprintSource.PREWARMED`, `TickingTransition.OFFSET_VARIANT` (documenté) : portée serveur voulue, tolérée par la consigne.
- `HyColonyPlugin.shutdown()` ne défait que les packs : `PluginBase.cleanup` (:466-481) vide commandes, événements, entity store, codecs (dont `registerCustomPageSupplier`, `OpenCustomUIInteraction.java:83-91`) et assets ; seul le capteur NPC échappe (D-1).
- HyDomum sans `shutdown()` : Hytale retire lui-même BlockType/Item et assets communs du pack au déchargement ; rien à nettoyer côté HyDomum (le trou est au rechargement, D-2).
- `HyDomumSystems`/`HyVanillaSystems` et `SystemDependency` sur un mod absent : `PluginManager.setup` (:1131-1132) n'appelle `setup0` qu'après `dependenciesMatchState` (:1190-1199) ; dépendance absente → HyColony DISABLED avec SEVERE, pas d'exception ; `registerCutter` et `registerSystem(FlowerPotSystem)` sont premiers et inconditionnels.
- Travaux asynchrones hors `TaskRegistry` (`CutterCraftQueue`, `CutterPreviewVariants`, `HytaleBlueprintSource.prewarm`, `OrnamentVariantRegistry`) : les files du cutter s'arrêtent à la fermeture de la fenêtre, le préchargement est borné ; à revoir seulement si le rechargement devient supporté.
- Cache d'assets de `Grant`/`Guard` (`immunityFire`, `physicalCauseIndex`) pour la vie du plugin : immuables.

## 3. Ce qui est bien fait

- `plugin/src/main/java/dev/hycolony/plugin/WorldRuntime.java:61-105` : tout l'état de jeu est construit par monde, sur le thread du monde, à `StartWorldEvent` ; `WorldRuntimes.remove` le sauvegarde et l'oublie à `RemoveWorldEvent` (`LAST`, non annulé).
- `plugin/src/main/java/dev/hycolony/plugin/subplugin/SubPlugins.java:201-220` : les packs d'assets optionnels sont désenregistrés dans `shutdown()` (sauf à l'arrêt du serveur, où c'est inutile).
- Aucun `getInstance`, aucun `INSTANCE`, aucune `Map<UUID, X>` statique dans les cœurs (phase 0, cmd 6).

## 4. Tableau

| Sév. | `chemin:ligne` | Titre |
|---|---|---|
| MOYEN | `plugin/.../HyColonyPlugin.java:60` | D-1 rechargement impossible (capteur NPC) |
| MOYEN | `domum/plugin/.../HyDomumPlugin.java:87-94` | D-2 rechargement laisse HyDomum sans catalogues |
| BAS | `plugin/.../ui/highlight/Highlights.java:27` ; `WorldRuntime.java:64` ; `ColonyTickSystem.java:20` ; `core/.../kernel/nav/DetouringBodies.java` | D-3 conteneurs jamais vidés |
| BAS | `blockui/.../api/UiSounds.java:18` | D-4 drapeau statique non atomique |
