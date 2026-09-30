# Audit global HyColony : 02, axe H, données et code, textes, configuration

```
ÉTAT : phase 2, axe H écrit. Code audité : commit 3e2e70ca. Sources : phase 0 (cmd 17, 19, 20), rapport « UI »
(textes), rapport « modèle de données » (config), relecteurs plugin et mods frères, MC ServerConfiguration
(version/main) relue. Chemins relatifs à la racine du dépôt.
```

**Note de l'axe : 4/5.** Contenu en données là où MC le met (ids d'assets dans les id-maps, plans dans les packs de styles, règles d'artisanat en JSON, config par sections MC avec défauts et bornes vérifiés identiques à `ServerConfiguration`), 5 paires de `.lang` à parité exacte, aucune traduction imbriquée sur `.Text`, aucun nom JSON renommé par Update 7, aucun asset qui écrase un asset de base (packs préfixés `HyColony_`/`HyDomum_`/`HyVanilla_`). Restent une clé de config sans effet, un identifiant brut dans un message, et des replis anglais.

## 1. Constats

### H-1 — MOYEN — `Gameplay.maxCitizenPerColony` est exposé, borné, documenté… et lu par personne
`core/src/main/java/dev/hycolony/core/kernel/config/ColonyConfig.java:19-20, 24-27` ; `plugin/src/main/resources/config.json:4`
```java
 * @param maxCitizenPerColony read by nothing yet: MC only caps immigration and births (housing), and forces the
 *     initial spawn
```
Mécanisme : la clé `MaxCitizenPerColony: 250` est écrite dans le `config.json` de chaque serveur, clampée [25..500], et aucun code ne la lit (grep) ; MC l'applique dans `CitizenManager.getMaxCitizens` (min du logement et de la config). La Javadoc le sait.
Impact : un opérateur qui la baisse ne voit rien changer ; le jour où le logement arrive (SP4), l'oubli passera inaperçu.
Règle : § 3 (un réglage MC exposé passe par `config.json` **et** agit). Remède : soit l'appliquer dès maintenant à `CitizenManager.onColonyTick` (aucun spawn au-delà), soit ne pas l'exposer avant SP4.
Sévérité MOYEN · effort S · confiance HAUTE · DÉJÀ CONNU partiel (la Javadoc l'annonce).

### H-2 — MOYEN — Le message « ordre créé » montre l'identifiant brut du type de bâtiment
`core/src/main/java/dev/hycolony/core/construction/workorder/WorkManager.java:92-98` ; `core/src/main/java/dev/hycolony/core/building/Building.java:186` ; `core/.../construction/workorder/BuildCompletion.java:54-56`
```java
Msg created = Msg.of("hycolony.workorder.created", b.displayName(), colony.name(), ...
```
Mécanisme : `Building.displayName()` renvoie `type.id()` (`hycolony:builder`) ; `BuildCompletion` traduit le même paramètre par `%hycolony.ui.building.type.…` (paramètre imbriqué), `WorkManager` non : le joueur lit « Construire : hycolony:builder… » dans le chat à chaque ordre créé (l'audit n'a pas rejoué le message en jeu ; `docs/TESTING.md` point 15 ne vérifie pas ce chat).
Règle : § 7 (tout texte vu par un joueur passe par une clé). Remède : le même paramètre `%hycolony.ui.building.type.<id>` que `BuildCompletion`, et un test sur `FakeNotifier`.
Sévérité MOYEN · effort S · confiance MOYENNE (à confirmer en jeu) · DÉJÀ CONNU non.

### H-3 — BAS — Textes anglais ou bruts qui atteignent le joueur
`core/src/main/java/dev/hycolony/core/app/ColonyFoundation.java:58` ; `domum/plugin/.../cutter/CutterOpener.java:30` (+ `CutterCrafting.java:98,111`, `OrnamentCommand.java:53,112,120`, `VariantGift.java:46`) ; `plugin/.../ui/ColonyPage.java:141-145, 159-162` ; `plugin/.../command/HyColonyCommand.java:54, 82, 120, 163, 196, 213, 216, 258, 281`
```java
manager.windows().ui().showFoundColony(player, new FoundColonyView(playerName + "'s Colony"));
```
```java
playerRef.sendMessage(Texts.translated("hydomum.ornament.failed", List.of("load")));
```
Mécanisme : nom de colonie par défaut en anglais (le champ est éditable, mais un joueur francophone voit « Jean's Colony ») ; `hydomum.ornament.failed` reçoit `load`/`create`/`give` bruts (« Échec à l'étape : create ») ; `ColonyPage.buildingName/itemName` rendent `Message.raw(id)` sur `.Text` pour un type hors `hycolony:` ou un objet absent des assets, ce que le projet sait déconnecter le client (`WandPage.java:123`) : inatteignable aujourd'hui, atteint le jour où un pack apporte un type (`SubPlugins.registerFeatures`) ; descriptions et étapes du selftest en anglais (opérateurs, DÉJÀ CONNU).
Règle : § 7. Remède : clé `hycolony.colony.defaultName` avec `{p0}` ; clés `ornament.failed.load/create/give` ; clé fournie par le pack ou `String` brute sur `.Text`.
Sévérité BAS · effort S · confiance HAUTE (nom, cutter), MOYENNE (`Message.raw`, non rejoué) · DÉJÀ CONNU partiel.

### H-4 — BAS — Identifiants d'assets hors des id-maps non listés au backlog
`plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleCitizenBodies.java:265-270` (`"Block","Build"`, `"Pickaxe","Mine"`, `"Hoe","Till"`, `"Item","Interact"`) ; `ui/highlight/HighlightMarkers.java:29` (`"Coordinate.png"`) ; `adapter/HytaleItemCatalog.java:130` (`"Leaves"`), `:294-298` (`"Pickaxe"/"Hatchet"/"Shovel"`) ; `farming/HytaleFarming.java:111` (`endsWith("_Eternal")`)
```java
case BUILD -> AnimationUtils.playAnimation(ref, AnimationSlot.Action, "Block", "Build", store());
```
Mécanisme : un renommage d'animation ou d'icône vanilla casse silencieusement un geste ou le marqueur ; la convention `_Eternal` porte une règle de récolte (« ce qui repousse ») dans le plugin.
Règle : § 7 (« les identifiants d'assets ne vivent que dans l'id-map »). Remède : entrées `animations.*`, `map.marker`, `farming.eternalSuffix` dans `id-map.json`, validées par `IdMap.validate` et le selftest.
Sévérité BAS · effort S · confiance HAUTE · DÉJÀ CONNU partiel (la famille est au backlog : `Immunity_Fire`, `Physical`, types de récolte, `Soil_Dirt_Tilled`, `Tool_Fertilizer`, `Block_Spawner_Block`).

### H-5 — BAS — `TurnOffExplosionsInColonies` promet trois modes dont deux sont identiques ; les événements d'entité de MC ne sont ni portés ni déclarés en écart
`core/src/main/java/dev/hycolony/core/kernel/config/Explosions.java:5-9` ; `core/src/main/java/dev/hycolony/core/app/ColonyProtection.java:14-18, 78-81`
```java
 * What players may do inside the colonies (MC ColonyPermissionEventHandler over Permissions.hasPermission): the rank
 * check with the creative operator bypass, EnableColonyProtection and TurnOffExplosionsInColonies.
```
Mécanisme : `DAMAGE_PLAYERS`/`DAMAGE_ENTITIES` sont parsés mais sans effet (seul `DAMAGE_EVERYTHING` change quelque chose, :79 ; le plugin documente l'écart entités, pas le cœur) ; `TOSS_ITEM`, `PICKUP_ITEM`, `FILL_BUCKET`, `SHOOT_ARROW`, `ATTACK_*`, `RIGHTCLICK_ENTITY`, `THROW_POTION` de `ColonyPermissionEventHandler` ne sont pas câblés ; `BlockUse` documente ses écarts, `ColonyProtection` et `Explosions` non.
Impact : un joueur hostile frappe les citoyens et ramasse les objets d'une colonie protégée ; la config promet ce qu'elle ne fait pas.
Règle : § 6, § 3. Remède : `Deviation from MC:` listant les événements non portés (PLANIFIÉ SP4/SP5) et « blocs seulement » dans `Explosions` ; `refuses(player, pos, Action)` suffit déjà au plugin pour les événements d'entité.
Sévérité BAS · effort S (doc) / M (câblage) · confiance HAUTE · DÉJÀ CONNU non.

## 2. Non retenus

- `RetryingResolver.DELAY_TICKS = 1200`, `MAX_TRIES = 3` codés en dur : constantes chez MC aussi (la section `requestSystem` de `ServerConfiguration` ne contient plus que `creativeResolve`, vérifié) ; `creativeResolve` absent : M-23.
- `BLOCK_MINING_DELAY = 500` : ancienne option MC supprimée, constante aujourd'hui (`docs/research/config-inventory.md:15,181`).
- `Client.buildGoggleRange` en réglage serveur : écart documenté (« Deviation from MC: a server setting »).
- Bornes et défauts de `Gameplay`, `Claims`, `Permissions`, `Commands` : identiques à `ServerConfiguration` (`ColonyConfigTest` les couvre).
- `styles.json` vide dans le jar : les styles vivent dans les sous-plugins `Styles_Outlander`/`Styles_Kweebec`, fusionnés au chargement ; `manifest_dependencies` et `SubPlugins` le documentent.
- Aucun nom JSON renommé par Update 7 (`DurationMs`, `SmallerThan`, `IsMajor`, `CursedItems`…) dans les 4 packs ni dans `tools/` ; aucune borne 319/320.
- Parité en-US/fr-FR : 0 différence de clés sur les 5 fichiers ; toutes les clés dynamiques (statuts, directions, stades, modes, outils, compétences, types) couvertes (relecteur plugin).
- Traductions imbriquées : les 24 sites `.param(..., Message)` sont rendus sur `.TextSpans` ; `TownHallStatsTab.java:22-31` le commente.
- `Message.raw(" - ")`, `" / "`, `""` : séparateurs, pas des textes.
- `manifest.json:11` `Website` pointe sur le dépôt MineColonies : cosmétique.
- Descriptions de commandes en anglais brut : DÉJÀ CONNU (BACKLOG).
- `HyColonyConfig` : anciennes clés plates relues puis réécrites en sections au premier `save()` : documenté.

## 3. Ce qui est bien fait

- `plugin/src/main/resources/hycolony/id-map.json` (42 clés) + `plugin/src/main/java/dev/hycolony/plugin/IdMap.java` + `WorldRuntimes.enableIfIdsValid` : un id manquant désactive proprement le mod (« Saves are untouched ») au lieu de planter, et `/hycolony selftest` le nomme.
- `core/src/main/java/dev/hycolony/core/kernel/config/ColonyConfig.java:24-27, 32-43, 53-60` : sections, défauts et bornes de `ServerConfiguration` reproduits, clampés dans les constructeurs compacts, et `blockui/.../ConfigQuarantine` qui met de côté un `config.json` illisible au lieu d'abattre le serveur (`docs/TESTING.md` point 59).
- `plugin/src/main/resources/hycolony/crafting.json` + `core/.../crafting/recipe/CraftingRulesJson.java` : les règles d'artisanat par métier en données, lues entrée par entrée avec avertissement (le § 5 appliqué au contenu).

## 4. Tableau

| Sév. | `chemin:ligne` | Titre |
|---|---|---|
| MOYEN | `core/.../kernel/config/ColonyConfig.java:19-27` | H-1 `MaxCitizenPerColony` sans effet |
| MOYEN | `core/.../construction/workorder/WorkManager.java:92-98` | H-2 id brut dans le message « ordre créé » |
| BAS | `core/.../app/ColonyFoundation.java:58` ; `domum/plugin/.../cutter/CutterOpener.java:30` ; `plugin/.../ui/ColonyPage.java:141-145` | H-3 textes anglais ou bruts |
| BAS | `plugin/.../adapter/HytaleCitizenBodies.java:265-270` (+4) | H-4 ids d'assets hors id-map |
| BAS | `core/.../kernel/config/Explosions.java:5-9` ; `app/ColonyProtection.java:14-18` | H-5 modes d'explosion sans effet, événements d'entité non déclarés |
