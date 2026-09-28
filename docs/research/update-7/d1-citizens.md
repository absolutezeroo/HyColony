# Update 7 (0.7.0-pre.4) : axe D1, citoyens PNJ (vérifié le 2026-09-29)

Question : les citoyens (entités PNJ, déplacement, futurs combats) et tout ce qui touche aux entités restent-ils identiques en Update 7, et qu'apporte-t-elle ?

Abréviations :
- `u7/` : sources décompilées Update 7, `scratchpad/u7/src/com/hypixel/hytale/` ;
- `hs/` : sources 0.6.8, `build/vineflower/hytale-server/com/hypixel/hytale/` (Javadoc injectée : on compare le code, pas les commentaires) ;
- `zip7:` / `zip6:` : `Assets.zip` pre-release / release ;
- `notes:` : notes de version `scratchpad/u7/notes.txt` (numéro de ligne) ;
- `compile.log` : `scratchpad/u7/compile.log`, compilation de notre plugin contre U7 (javac seul) ;
- `MC/` : `github.com/ldtteam/minecolonies`, branche `version/main`, sous `src/main/java/com/minecolonies/`.

Méthode : diff de chaque classe Hytale que touche notre code PNJ (commentaires retirés), diff des assets que notre rôle et nos effets référencent, lecture des notes. Les différences purement de décompilation (génériques, casts, boucles `Iterator`) sont ignorées.

Résultat de la compilation : `compile.log` n'a que 3 erreurs, toutes hors de cet axe (`HytaleWorldEffects.java:155,166`, `HytaleWorldQuery.java:48`). **Aucune classe de `plugin/.../npc/`, de `HytaleCitizenBodies` ni de `ui/highlight/` n'échoue.** Error Prone n'a pas tourné (javac s'arrête avant) : les `@RestrictedApi` d'U7 ne sont donc pas encore contrôlés, mais notre code PNJ n'appelle aucune des méthodes restreintes listées (notes:202-215 ; voir « Vérifié sans impact »).

## 1. Bloquant

Rien pour cet axe. Le citoyen apparaît, marche, s'anime, s'ouvre au clic et reste immunisé au feu avec le même code et le même JSON :
- `NPCPlugin.spawnNPCWithColumnProbe(store, role, group, world, x, z, yHint, Rotation3fc, postSpawn)` : même signature (`u7/server/npc/NPCPlugin.java:1146-1156`, `hs/…/NPCPlugin.java:1241-1251`). Seul changement : un modèle nul renvoie `FAIL_NOT_SPAWNABLE` au lieu de `FAIL_INVALID_POSITION` (u7 l. 1182-1183). Notre log (`HytaleCitizenBodies.java:121-126`) affiche la valeur telle quelle : rien à changer.
- Rôle `plugin/src/main/resources/Server/NPC/Roles/HyColony/HyColony_Citizen.json` l. 1-31 : `Type: Generic` toujours enregistré (`u7/server/npc/NPCPlugin.java:1830`) ; clés `MaxWalkSpeed`, `Gravity`, `MaxFallSpeed`, `Acceleration` inchangées, mêmes défauts (`u7/…/movement/controllers/builders/BuilderMotionControllerWalk.java:95,121`, comparaison clé par clé sans différence sauf `HoverFreq`) ; `BuilderRole` ne fait qu'**ajouter** des clés (`HiddenUIComponents`, `UseFixedKnockback`, `IgnoreDisableNPCIncomingDamage`, `MemoriesUnlockedBy`, `MemoriesKillRange`), aucune n'est retirée ; `Seek` = `BuilderBodyMotionFind` (`u7/…/NPCPlugin.java:893`), clés et défauts identiques (`ThrottleDelayRange` défaut `[3, 5]`, `ThrottleIgnoreCount` 3 : `u7/…/builders/BuilderBodyMotionFindBase.java:24,100-117`).
- Modèle `PlayerTestModel_V` et son parent `Player` : identiques octet pour octet (`zip6:`/`zip7:Server/Models/Human/PlayerTestModel_V.json`, `Player.json`).
- 162 types de composants cœur enregistrés dans les deux versions, même liste (`registerCoreComponentType`) : notre capteur `HyColonyTarget` (`HyColonyPlugin.java:60`) s'enregistre pareil. `SensorBase`, `BuilderSensorBase`, `PositionProvider`, `Feature`, `Sensor`, `ExecutionSupport`, `InfoProvider`, `NavState`, `DisplayNameSupport`, `SpawnTestResult` : 0 ligne de diff.

## 2. À migrer

Rien d'obligatoire. Une seule question de réglage, liée au changement 3.1 : faut-il `"ThrottleDelayRange": [0, 0]` sur le `Seek` du rôle ? À trancher après l'essai en jeu (voir 3.1).

## 3. Changement de comportement

### 3.1 Pause de 3 à 5 s devant un obstacle (pathfinding)

- 0.6.8 : `BodyMotionFindBase.onBlockedPath()` est vide ; la temporisation (`throttle`) ne sert qu'après des **recherches de chemin échouées** (`hs/…/corecomponents/movement/BodyMotionFindBase.java`, champs `throttleCount/throttleDelay`, `resetThrottleCount`).
- U7 : la logique passe dans `PathRetryThrottle` (`u7/server/npc/util/PathRetryThrottle.java`) et **un chemin bloqué compte aussi** : `onBlockedPath()` fait `recordRetry()` puis `armDelay()` (`u7/…/BodyMotionFindBase.java:588-591`), appelé quand `isObstructed() && !aStar.isComputing()` (l. 719-724). Au-delà de `ThrottleIgnoreCount` (3) essais, le PNJ attend `ThrottleDelayRange` (3 à 5 s) avec l'état `PROGRESSING` (`setNavStateThrottling`, l. 668). Le compteur ne repart à zéro qu'au but atteint **et** non obstrué (diff l. 421-423). notes:338 : « `ThrottleDelayRange` à `[0, 0]` restaure l'ancien comportement sur un obstacle ».
- Chez nous : `HytaleCitizenBodies.navStatus` traduit `PROGRESSING` en `MOVING` (`HytaleCitizenBodies.java:183-188`) ; `StuckHandler` relance la marche après `DELAY_BEFORE_ACTIONS` = 100 ticks sans progrès, puis téléporte `NEXT_ACTION_DELAY` = 200 ticks plus tard (`core/.../kernel/nav/StuckHandler.java:40-41`). La relance vise la même cible, donc `BodyMotionFindWithTarget.mustAbortThrottling` (qui n'interrompt la pause que si la cible a bougé, `u7/…/BodyMotionFindWithTarget.java:202-211`) ne l'abrège pas. Rien ne reste bloqué : au pire le citoyen marque une pause de 5 s avant d'être relancé ou téléporté.
- MC ne marque pas de pause (`PathingStuckHandler` agit sur les nœuds de chemin). **[in-game]** Si les citoyens s'arrêtent visiblement devant les obstacles, poser `"ThrottleDelayRange": [0, 0]` sur le `Seek`. Coût : cela retire aussi la pause après une recherche échouée, qui existait déjà en 0.6.8 (plus de calculs A* répétés).

### 3.2 Eau peu profonde vers eau profonde

- notes:275 et u7 `MotionControllerWalk` : un PNJ **déjà dans l'eau** sans contrainte relâchée `WADE` ne peut plus entrer dans une position de marche invalide (condition `inWater && !WADE && !isCurrentWalkPositionValid`, diff de `u7/…/movement/controllers/MotionControllerWalk.java`). La profondeur admise ne change pas (`WalkFluidDepth.calculateConstraintDepth`, même formule qu'en 0.6.8 : `min(0.25, respiration / 2)` pour un PNJ qui respire l'air).
- Notre `Seek` n'a pas de `RelaxedMoveConstraints` (`HyColony_Citizen.json:20-25`). Effet attendu : un citoyen dans une mare ne s'enfonce plus dans l'eau profonde, et la note dit que cela corrige aussi des PNJ « coincés près des liquides ». MC laisse nager ses citoyens (écart qui existait déjà). **[in-game]** près des champs irrigués et des rivières.

### 3.3 Apparition : fenêtre verticale de ±16 blocs et 8 intervalles au plus

- 0.6.8 : `SpawningContext.set(world, x, y, z)` cherche les intervalles libres dans **toute la plage d'environnement** qui contient `y` (`hs/server/spawning/SpawningContext.java:571-627`, `environmentColumn.getMin/getMax`), les garde tous (tableau agrandi à la demande, l. 844-852) et prend le plus proche de `y`.
- U7 : la recherche est limitée à `y ± 16` et bornée à 0..319 (`u7/server/spawning/SpawningContext.java:522-524,559-560`) ; les intervalles passent par un **réservoir aléatoire de 8** (`GapReservoir(8)`, l. 135 ; tirage `random.nextInt(seen)` dans `u7/…/chunk/heightmap/GapReservoir.java:50`). Le choix reste « l'intervalle qui contient `y`, sinon le plus proche » (`SpawnGapBounds.selectGap`, l. 1430-1447).
- Chez nous : `yHint = near.y()` = hôtel de ville, dernière position ou point de réapparition (`core/.../citizen/CitizenManager.java:156-160`, `HytaleCitizenBodies.java:106-113`). Le sol est presque toujours à moins de 16 blocs. Seul risque : plus de 8 intervalles libres dans ces 33 blocs (tour à nombreux étages), où l'intervalle le plus proche peut être écarté au hasard. **[in-game]**, risque faible.

### 3.4 La boîte de collision suit l'échelle : copie lumineuse de la surbrillance

- U7 : `BoundingBox` garde une boîte non mise à l'échelle et applique `scale` à la boîte et aux boîtes de détail (`u7/server/core/modules/entity/component/BoundingBox.java:59-66,92-97`) ; le nouveau `EntityScaleBoundingBoxSystem` (enregistré `u7/…/entity/EntityModule.java:622`) recopie chaque tick l'échelle d'`EntityScaleComponent` (divisée par l'échelle du modèle s'il y en a un) dans la boîte. notes:131, 233.
- Chez nous : `GlowingBlock` (`plugin/.../ui/highlight/GlowingBlock.java:39,68-70`) pose `EntityScaleComponent(1.05)` sur une entité de bloc, `Intangible`, sans `Velocity`. Sa boîte, centrée (`BlockEntity.createBoundingBoxComponent`, `u7/server/core/entity/entities/BlockEntity.java:101-122`, inchangé), passe de ±0,5 à ±0,525 : elle déborde du vrai bloc de 2,5 % par face.
- `Intangible` exclut l'entité de `EntitySpatialSystem` (`u7/…/entity/system/EntitySpatialSystem.java:18`) : aucune requête spatiale serveur ne la voit. **[in-game]** Le client vise-t-il cette boîte plus grande avant le bloc (clic ou casse d'un bloc en surbrillance) ? Si oui, deux replis : remettre l'échelle à 1.0 (la lueur de `Drop_Legendary` couvre déjà le bloc) ou garder 1.05 et accepter.
- Le citoyen n'a pas d'`EntityScaleComponent` (ni notre code ni `NPCPlugin.spawnEntity` n'en posent ; seul `npc/pages/EntitySpawnPage.java` en pose un) : sa boîte ne change pas.

### 3.5 `UIComponentList` posé par le rôle

- U7 : `Role.createAndAttach` pose toujours `UIComponentList(shownUIComponents(HiddenUIComponents))` (`u7/server/npc/role/Role.java:228,236-252`). Sans la clé, la liste vaut `null` = tous les composants (`u7/…/entityui/UIComponentList.java:36-49`), soit le comportement de 0.6.8. Le rôle est reconstruit au chargement (`RoleBuilderSystem.onEntityAdd` → `NPCPlugin.buildRole`, `u7/server/npc/systems/RoleBuilderSystem.java:142` → `createAndAttach`, `NPCPlugin.java:1574`) : la liste est donc réécrite sur les citoyens déjà sauvegardés. Aucun effet sans `HiddenUIComponents` ; voir l'opportunité 4.1.

### 3.6 Ouverture de la fenêtre du citoyen : nouvelle liste noire de mondes

- U7 : `UseEntityInteraction` vérifie `WorldConfig.isRootInteractionAllowed(root)` **avant** d'émettre `UseEntityEvent.Pre` (`u7/…/interaction/config/client/UseEntityInteraction.java:66`). Liste vide = tout est permis (`u7/…/asset/type/gameplay/WorldConfig.java:136-139`).
- Seul `zip7:Server/GameplayConfigs/ForgottenTemple.json` l. 8 remplit `BlockedRootInteractionTags` (`["Type=Bed"]`). Dans les mondes normaux, `CitizenUseSystem` (`plugin/.../npc/CitizenUseSystem.java:24-65`) reçoit toujours l'événement. Un monde qui bloquerait le tag de `*UseNPC` empêcherait d'ouvrir la fenêtre du citoyen.

## 4. Opportunités (liées à un système MineColonies)

### 4.1 Masquer la barre de vie et les chiffres de dégâts (MC `RenderBipedCitizen`)

- MC n'affiche au-dessus d'un citoyen que son nom et une icône d'état (`MC/core/client/render/RenderBipedCitizen.java:101-131`, `renderNameTag` + `getStatusIcon`) : ni barre de vie ni chiffres de dégâts.
- U7 : clé de rôle `HiddenUIComponents` (`u7/server/npc/role/builders/BuilderRole.java:284-296`, validée par `EntityUIComponentExistsValidator`). Les deux composants existants : `zip7:Server/Entity/UI/Healthbar.json` (`Type: EntityStat`, `Health`) et `CombatText.json`.
- Proposition : `"HiddenUIComponents": ["Healthbar", "CombatText"]` dans `HyColony_Citizen.json`. Utile surtout quand les citoyens deviendront mortels (`docs/research/citizen-death.md` § 2.4) ; aujourd'hui `Invulnerable` annule les dégâts, donc pas de chiffres. **[in-game]** La barre de vie d'un PNJ invulnérable à pleine vie est-elle affichée aujourd'hui ?
- Le nom n'est pas concerné : c'est un `Nameplate`, pas un `EntityUIComponent`. Le réglage MC `alwaysrendernametag` (`docs/research/config-inventory.md:155`) reste sans API.

### 4.2 Gardes futurs : `IgnoreDisableNPCIncomingDamage`

- U7 : clé de rôle qui pose `IgnoreDisableNPCIncomingDamage` (`u7/server/npc/systems/RoleBuilderSystem.java:155-156`), lue par `DamageSystems` pour ignorer `CombatConfig.DisableNPCIncomingDamage` (diff de `u7/…/damage/DamageSystems.java`).
- MC ne connaît pas ce réglage de serveur. Recommandation pour la spec de la mort des citoyens : **ne pas** poser la clé, pour respecter la règle du serveur (un serveur qui coupe les dégâts aux PNJ protège aussi les citoyens). Décision à écrire dans la spec, pas un gain.

### 4.3 Sans analogue MC : à ne pas utiliser pour les citoyens ni les gardes

- **Sursaut `Component_Instruction_Hit_Interrupt`** (notes:167-170 ; `zip7:Server/NPC/Roles/_Core/Components/Instructions/Combat/Component_Instruction_Hit_Interrupt.json`, effets `Hit_Interrupt_*`, absents de `zip6:`) : MC n'annule pas l'attaque d'un garde ou d'un pillard touché. Non.
- **Changement de cible `Component_Instruction_Combat_Target_Swap`** (notes:165,220, `TargetRequireLoS`) : MC choisit la cible par table de menace (`MC/api/entity/ai/combat/threat/ThreatTable.java`) et exige la ligne de vue seulement à la recherche (`MC/core/entity/ai/combat/TargetAI.java:136-170`). C'est une règle de jeu : elle ira dans le cœur (CLAUDE.md § 1), pilotée comme la marche par un capteur à nous. Non.
- **Charge** (`BodyMotionCharge`, `ChargeAccelerationDistance`, `ProbeRelaxedConstraints`, notes:631-636) : aucun garde ni pillard MC ne charge. Non.
- **Dégâts sortants par effet** (`EntityEffect.OutgoingDamage`, `u7/…/entityeffect/config/EntityEffect.java:232` ; `DamageSystems.ScaleOutgoingDamageFromEntityEffects`, l. 1539) : MC calcule la valeur exacte de chaque coup (`MC/core/entity/ai/workers/guard/MeleeCombatAI.java:310-345`, `RangeCombatAI.java:274-320`, recherches `MELEE_DAMAGE`, `ARCHER_DAMAGE`, critiques). Le cœur calculera le montant ; un effet en pourcentage n'apporte rien.
- **Rayons** (`Beam`, `AttachBeam`, `BeamComponent`, notes:157,359,386,406-407) : pas d'équivalent MC. Non.
- **Recherche de position des marqueurs d'apparition** (`SpawnPositionSearch`, `RequireFitAtMarkerHeight`, notes:172-176) : réservée à `SpawnMarkerEntity` (`u7/server/spawning/spawnmarkers/SpawnMarkerEntity.java:638,727,1311`). `SpawnPositionRangeSearch.java` n'a pas été décompilé (absent de `u7/server/spawning/util/`). MC place un citoyen par sa propre règle, `EntityUtils.getSpawnPoint` (`MC/api/util/EntityUtils.java`, `findAround` + 2 blocs libres + voisin libre, appelé par `MC/core/colony/managers/CitizenManager.java:253`). Le portage fidèle est une règle du cœur, pas cette API.

### 4.4 Hiérarchie d'entités : pas utilisable pour attacher quoi que ce soit de visible

- U7 : `Store.addEntity(holder, ref, reason, parent)`, `Store/CommandBuffer.setParent`, `getParent`, `forEachChunk(parent, …)` (`u7/component/Store.java:427,969-1015,1427-1450`) ; un enfant est supprimé avec son parent (`removeChildrenOf`, l. 709,876,2454) ; un parent ne peut pas recevoir de parent, ni un enfant avoir d'enfants (l. 994, 2420).
- `QuerySystem.getHierarchyScope()` vaut `ROOT` par défaut (`u7/component/system/QuerySystem.java:45-47`), qui couvre les racines sans enfant et les parents, **pas les enfants** (`u7/component/system/HierarchyScope.java:3-7`, `Store.inScope` l. 2394-2400).
- **Aucun système vanilla ne redéfinit `getHierarchyScope`, et aucun code hors du paquet `component` n'appelle `setParent`, `getParent` ou `addEntity(…, parent)`** (grep sur `u7/`). Un enfant n'est donc ni suivi par le réseau, ni déplacé avec son parent, ni simulé : c'est une entité de données. L'icône d'état MC au-dessus de la tête (`RenderBipedCitizen.java:110-131`, `MC/api/entity/citizen/VisibleCitizenStatus.java`) ne peut pas passer par là. Persistance de la hiérarchie : non vérifiée.

### 4.5 Aperçu du PNJ dans une fenêtre, portraits, armure : rien de neuf

- Aucune nouvelle classe serveur `*Preview*` hors éditeur d'assets et prefabs (`added.lst` : seuls `PrefabPreview`, `PersistentPrefabPreview`, déjà en 0.6.8 selon `docs/research/citizen-inventory-window.md` § 8.3) ; aucun `.ui` de `zip7:` ne contient « Preview ». `UICommandBuilder` : diff de décompilation seulement. La conclusion de `citizen-inventory-window.md` § 8.3 (aperçu impossible) tient. Les caméras serveur à curseur sont « de nouveau jouables » (notes:389,446-448) mais `CameraSequence*` existait déjà : aucune piste neuve pour un aperçu.
- Portraits (`docs/research/citizen-portraits.md`) : `CosmeticsModule`, `PlayerSkinComponent`, `ModelSystems`, `EntityTrackerSystems`, `NPCSpawnCommand` n'ont que des diffs de décompilation.
- Armure (en attente des gardes) : `InventoryComponent.Armor`, `ItemArmorSlot`, `RoleUtils`, `NPCSystems` inchangés ; U7 ajoute seulement `AbilitySlots` et `RuneBag` (runes, sans analogue MC). Les règles de masquage d'armure passent de `ClientFeature` à `GameplayConfig` (notes:395) : côté joueur, pas PNJ.

## 5. Vérifié sans impact

- **Hiérarchie et nos systèmes.** Nos systèmes d'entités (`CitizenBodyLifecycleSystem`, `CitizenFireImmunitySystems.Grant/Guard`, `CitizenUseSystem`, `GogglesSystems.*`, `HutBlockSystems.*`, `ProtectionSystems.*`, `FieldBlockSystems.*`, `FlowerPotSystem`, `BlockUseProtectionSystem` ; `HyColonyPlugin.java:71-79`, `block/BlockSystems.java:20-34`) gardent la portée `ROOT`. Les citoyens et les joueurs sont des racines (personne ne crée d'enfant, § 4.4) : ils restent vus. `ColonyTickSystem` est un `TickingSystem` sans requête. Nous n'attachons rien aux citoyens.
- **Écouteurs.** Un écouteur qui lève n'arrête plus les autres (notes:359) : nos gestionnaires attrapent déjà (`CitizenUseSystem.java:54-63`). `DrainPlayerFromWorldEvent` devenu asynchrone (notes:234) : nous ne l'écoutons pas (`HyColonyPlugin.java:65,80-98`).
- **Téléportation anti-blocage.** `MotionControllerWalk.translateToAccessiblePosition` lit maintenant les sections (`u7/…/MotionControllerWalk.java:778-900`) au lieu de `BlockChunk`/`WorldChunk` (hs) ; même algorithme, mêmes bornes 0..320 (l. 785-790). `isValidPosition`, `getNavState`, `Teleport.createExact`, `BoundingBox.getBoundingBox` : inchangés. `HytaleCitizenBodies.teleport` (l. 310-344) identique.
- **Regard, animations, objet tenu.** `AnimationUtils`, `HeadRotation`, `ModelComponent.getEyeHeight`, `TransformComponent`, `PhysicsMath`, `InventoryHelper` : diffs de décompilation seulement. Animations d'objets : `Hoe.json`, `Pickaxe.json` identiques ; `Block.json` ne fait qu'ajouter `HeavyThrow` (`Build` intact).
- **Vitesse.** `EffectControllerComponent.addInfiniteEffect/removeEffect/hasEffect` (`u7/…/entity/effect/EffectControllerComponent.java:218,314,460`) et `NPCEntity.getCurrentHorizontalSpeedMultiplier` (u7 l. 514) inchangés ; `ApplicationEffects.HorizontalSpeedMultiplier` toujours lu (`u7/…/entityeffect/config/ApplicationEffects.java:67`). La note « vitesse des effets appliquée en l'air » (notes:114) concerne le mouvement joueur.
- **Immunité au feu.** `Immunity_Fire`, `Burn`, `Burn_Template`, `Lava_Burn`, `Block_Damage.json` identiques entre zips. Nouveauté U7 : un effet porte un `owner`, et ses dégâts ont alors pour source `EntitySource(owner)` au lieu de l'effet (`u7/…/entity/effect/ActiveEntityEffect.java:204`). `ApplyEffectInteraction` ne pose un `owner` que si l'applicateur n'est pas la cible (l. 85) ; les braises (`Block_Damage`, cible `USER`) s'appliquent à celui qui les touche, donc `owner = null` et la source reste l'`ActiveEntityEffect` que `CitizenFireImmunitySystems.Guard` teste (`CitizenFireImmunitySystems.java:158`). **[in-game]** reste valable pour les braises.
- **Dégâts et recul.** `Invulnerable`, `DamageEventSystem`, `DamageModule.getFilterDamageGroup`, `NPCDamageSystems` : inchangés (sauf la nouvelle couleur des chiffres de dégâts et `ScaleOutgoingDamage…`). Le recul modifié (`UseFixedKnockback`, `preScaleKnockback`, `HackKnockbackValues`) ne touche pas un citoyen invulnérable (`DamageSystems.FilterUnkillable` annule d'abord, `citizen-death.md:196`).
- **Nom affiché.** `Nameplate`, `DisplayNameComponent`, `DisplayNameSupport` : 0 ligne de diff.
- **Mondes cubiques (Y hors 0..319).** Ils n'existent qu'avec un chargeur et un générateur cubiques (`u7/…/world/storage/ChunkGrid.java:103-111`, `World.java:1061-1063`) ; les mondes normaux gardent 10 sections. Notre cœur n'a aucune borne Y codée en dur (grep `319|320|MIN_Y|MAX_Y` vide sur `core/` et `plugin/`) ; `DetouringBodies.packBlock` garde 12 bits pour Y (-2048..2047, `core/.../kernel/nav/DetouringBodies.java:109-113`), assez. Dans un monde cubique, l'apparition (`SpawningContext.set`, 0..319) et la téléportation (0..320) échoueraient hors de la bande classique : échec journalisé, pas de plantage. Les lectures de blocs de nos adaptateurs relèvent de l'axe « monde ».
- **Vieille API du joueur.** `Player.getPlayerRef()` (`HighlightMarkers.java:24`, avertissement dans `compile.log`) était déjà `@Deprecated(forRemoval = true)` en 0.6.8 (`hs/…/entity/entities/Player.java:1148`) : pas un changement U7.
- **Performance.** Nos chemins chauds (`SensorHyColonyTarget.matches`, `HytaleCitizenBodies.navStatus`) n'appellent que des méthodes inchangées. U7 ajoute par tick : `EntityScaleBoundingBoxSystem` pour chaque entité mise à l'échelle (nos copies lumineuses, peu nombreuses et brèves ; `setScale` ne fait rien si l'échelle ne change pas, `BoundingBox.java:59-66`) et un test `consumeNetworkOutdated` par entité visible (`UIComponentSystems.Update`). U7 allège les collisions d'entités (notes:261) et les lectures `BlockDataProvider` (notes:401). `Role.createAndAttach` n'alloue un `TreeSet` qu'à l'apparition, et seulement avec `HiddenUIComponents`. Capteurs de blocs « à toute hauteur » (notes:494) : notre rôle n'en utilise pas.
- **Divers sans objet.** Clés de bloc inconnues ignorées dans les données de rôle (notes:724), `KnockbackScale` du modèle volant (notes:221), validation des marqueurs d'apparition (notes:195), sol trouvé hors de l'ancienne bande (notes:277) : aucun de ces mécanismes n'est utilisé par notre rôle.
