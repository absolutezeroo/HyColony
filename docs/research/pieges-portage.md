# Pièges du portage MineColonies → Hytale

Liste de contrôle lue par les agents (`hycolony-implementer`, `hycolony-reviewer`, `mc-fidelity-checker`) et par les skills `port-mc` et `hytale-api`. La plupart des pièges ont déjà produit un bug dans HyColony (audit du 2026-09-30, `docs/research/architecture/audit-global/`) ; les autres sont des faits vérifiés qui en préviennent un. Chacun cite sa source. On ajoute un piège dès qu'un bug en révèle un nouveau.

## 1. Pièges Hytale

1. **Un chunk n'est pas chargé à l'accès.** Minecraft charge un chunk quand on lit un de ses blocs ; Hytale non. Une position non chargée se lit « vide ». Tout parcours de blocs (chantier, champ, scan) teste `WorldBlocks.isLoaded` et **attend** la position au lieu de la sauter ; sinon un chantier se termine avec des trous. Bug : audit M-1 (`BuilderAI.structureStep`, `StructureScan.needsWork`).
2. **La hauteur du monde est bornée.** `ChunkUtil.MIN_Y = 0`, `HEIGHT = 320` (`math/util/ChunkUtil.java`). Une section hors de cette hauteur n'existe jamais : une attente « jusqu'au chargement » ne finirait jamais. `HytaleWorldBlocks.isLoaded` répond donc `true` hors de cette hauteur (`plugin-b-api.md` § 34). Trouvé à la relecture du correctif M-1.
3. **Remplacer un bloc à entité garde le contenu, le casser le jette.**
   - Poser un bloc à la place d'un autre recrée l'entité du bloc : `BlockOperations.setBlock` clone une entité neuve (l. 91-96).
   - `BlockEntity.setBlockEntity` (`server/core/modules/block/BlockEntity.java`, à ne pas confondre avec l'homonyme de `server/core/entity/entities`) envoie d'abord un `BlockReplaceEvent` à l'ancienne entité (l. 115-117), puis la retire (l. 120-121). La recréation n'a lieu que sans le réglage 2 (`(settings & 2) == 0`, `BlockOperations` l. 87).
   - `ItemContainerSystems.OnReplaced` (l. 138-165) déplace alors tout le contenu dans le nouveau conteneur (`moveAllItemStacksTo`). `onEntityRemove` (l. 97-122) ne jette au sol que ce qui n'a pas tenu, et ferme les fenêtres ouvertes.
   - Si le nouveau bloc n'a pas d'entité (cassage, bloc ordinaire), tout le contenu tombe au sol (`plugin-b-api.md`, cassage).
   - Pour le portage, c'est Structurize qui décide : un bloc du plan qui porte des `tileEntityData` n'est jamais « le même bloc » (`StructurePlacer` l. 228, § 2.1).
4. **Une exception qui sort d'un système ECS tue un thread.**
   - `TickingThread` n'attrape qu'**hors** de sa boucle (`util/thread/TickingThread.java`, `while` l. 57, `catch (Throwable)` l. 88). Une exception dans un `TickingSystem`, un `EntityTickingSystem` ou un système d'événement ECS arrête donc le monde.
   - Les fournisseurs de marqueurs de carte (`MapMarkerTracker`, l. 79-81) n'attrapent rien non plus.
   - Les écouteurs du bus d'événements, eux, sont protégés : `SyncEventBusRegistry` attrape `Throwable` et journalise (l. 143-146).
   - Les tâches passées par `world.execute` sont protégées : `World.consumeTaskQueue` attrape `Exception` (l. 1165).
   - Chaque système ECS et chaque fournisseur de marqueurs du plugin attrape donc `RuntimeException`, comme les gestionnaires d'événements de CLAUDE.md § 4. Bug : audit E-3.
5. **`World.execute` lève quand le monde s'arrête** (`SkipSentryException`). Un appel depuis un autre monde, la déconnexion ou l'arrêt est lui-même gardé ; sinon un monde arrêté empêche le nettoyage des autres. Bug : audit E-7.
6. **Changements structurels interdits pendant `processing`.**
   - Ajouter ou retirer une entité ou un composant lève dans un système d'événement, un `RefSystem`, une interaction ou un `EntityTickingSystem` : `Store.tick(ArchetypeTickingSystem…)` prend le verrou (`component/Store.java:2015-2037`). On passe alors par le `CommandBuffer`, ou on diffère par `world.execute`.
   - Seul le tick d'un `TickingSystem` simple n'est **pas** sous ce verrou (`Store.tickInternal`, l. 1990-2013).
   - Bug : audit E-8 (commentaires faux).
7. **Le verrou d'assets bloque le thread du monde pour toujours.** `World.tick` garde le verrou de lecture de `AssetRegistry.ASSET_LOCK` pendant tout le tick (`World.java`, verrou l. 372-390 en 0.7.0-pre.5). `loadAssets` prend le verrou d'écriture (`AssetStore.java:833-921`) : appelé depuis le thread du monde, il ne rend jamais la main. On charge hors du thread du monde, puis on revient par `world.execute` (`plugin-b-api.md` § 17).
8. **Deux enums `Rotation`.** `protocol/Rotation` a `getValue()` ; `server/core/asset/type/blocktype/config/Rotation` (celui de `yaw()`) a `getDegrees()`, pas `getValue()`. Ne jamais persister un `ordinal()`. Bug : audit G-3 et commit `6b5467be` qui ne compile pas.
9. **Le rechargement du plugin n'est pas pris en charge.** `NPCPlugin.registerCoreComponentType` lève au second `setup()` : `BuilderFactory.add` refuse un nom déjà enregistré (`server/npc/asset/builder/BuilderFactory.java:41-44`). Bug : audit D-1/D-2 (non corrigé).
10. **Chunks de 32 blocs** (`ChunkUtil.SIZE`). Les distances MC en chunks passent par les cellules de claim de 16 blocs (`ClaimCell.SIZE`), jamais par les chunks Hytale (spec SP0 § 3.2).

Toute API Hytale se vérifie dans `build/vineflower/hytale-server` avant usage (skill `hytale-api`) ; aucune signature supposée.

## 2. Pièges MineColonies

Sources lues sur `github.com/ldtteam/minecolonies`, branche `version/main` (Structurize : `github.com/ldtteam/Structurize`).

1. **Lire aussi les classes parentes.** Beaucoup de règles vivent dans les parents, pas dans la classe du métier. Si le parent n'est pas porté, le comportement manque entièrement. Bug : le fermier ne recevait jamais ses requêtes (audit E-2), faute du `cleanAsync` de `AbstractEntityAIBasic` (événement AI_BLOCKING tous les 200 ticks, `AbstractEntityAIBasic.java:236` ; méthode l. 703-716). Parents à lire systématiquement :
   - `AbstractEntityAIBasic` : `cleanAsync`, vidage selon les règles de la hutte, NEEDS_ITEM, `onException` (l. 353-363) ;
   - `AbstractEntityAIStructure` et Structurize `StructurePlacer` / `BuildingStructureHandler` / `IPlacementHandler` : `allowReplace` vrai hors CLEAR (`BuildingStructureHandler`), `sameBlockInWorld` = même bloc **et** `tileEntityData == null` (`StructurePlacer`), `handleRemoval` (`IPlacementHandler`), CLEAR_NON_SOLIDS par placement ;
   - `AbstractBuilding` / `AbstractBuildingContainer` : `getContainers` (racks puis hutte, l. 140-145), `keepX`, `getRequiredItemsAndAmount` ;
   - `Permissions.hasPermission(Player, Action)` : contournement opérateur (OP_RANK, l. 694-706).
   Ce qui vient d'un parent commun à tous les travailleurs va dans `job/work`, jamais dans un métier.
2. **Vérifier la cadence réelle, pas seulement la constante.** Une constante n'a de sens qu'avec la fréquence d'appel de sa méthode. `RETRY_DELAY = 1200` se décompte une fois par mise à jour (`StandardRetryingRequestResolver.java:151-153`), et le système de requêtes se met à jour tous les 11 ticks (`UPDATE_RS_INTERVAL`, `ColonyConstants.java:60`) : 13 200 ticks par essai. Bug : audit M-3 (×11 trop rapide).
3. **Connaître la machine d'état de MC.** `TickRateStateMachine.tick` (`api/entity/ai/statemachine/tickratestatemachine/TickRateStateMachine.java:73-103`) s'arrête sur le premier événement AI_BLOCKING vrai, avant de décompter les cibles de l'état. Deux délais posés l'un après l'autre (`setDelay` puis `setCurrentDelay`, `AbstractEntityAIBasic.java:360-361`) s'additionnent donc. Faux positif de relecture : la « pause doublée » de `WorkerMachine`, qui est celle de MC.
4. **Chercher les omissions, pas seulement les différences.** Une branche entière absente (loisir à 5 % de `EntityAICitizenWander`, `LEISURE_CHANCE` l. 38) ne se voit pas en comparant ce qui existe.
5. **Un test fige la source (MC ou Hytale), pas notre code.** L'attendu d'un test vient de la source MC citée (`MC Fichier:ligne`). Pour une règle du monde (CLAUDE.md § 6), il vient du fait Hytale cité (classe décompilée ou chemin d'asset). Plusieurs tests figeaient un comportement faux (liste noire héritée, 1200 ticks, fondation à 16 cellules, pierre tournée payante). Un test dont l'attendu change est suspect jusqu'à preuve par la source.
6. **« Comme MC » se prouve.** Toute phrase d'une spec, d'une Javadoc ou d'un rapport qui affirme la fidélité, ou qui dénonce un écart, cite `fichier:ligne` de MC. De même, « suit Hytale » cite la classe décompilée ou le chemin d'asset lu. Sinon, ce n'est pas vérifié.

## 3. Pièges de méthode

1. **Un test qui passe sans le correctif ne teste rien.** Avant de rendre la main, retirer le correctif (ou sa condition clé) et vérifier que le test échoue. Un relecteur le fait dans un export (`git archive`). Un implémenteur peut le faire sur ses propres fichiers : copie de sauvegarde, retrait, test, restauration, puis `git diff` pour vérifier que rien d'autre n'a bougé. Les relectures du 2026-09-30 ont trouvé ainsi trois tests trop faibles.
2. **Plusieurs sessions dans le même dossier.**
   - Vérifier la branche et `git status` avant chaque commit ; indexer des chemins explicites.
   - Formater fichier par fichier : `./gradlew :core:spotlessApply -PspotlessIdeHook="<chemin absolu>"`. Un `spotlessApply` sur un module entier reformate les fichiers en cours d'une autre session (2026-09-28).
   - **Jamais `git commit --amend`** : un amend est tombé sur le commit d'une autre session (refusé par `guard.js` depuis le 2026-09-30).
   - Construire une relecture sur un export `git archive <sha>`, pas sur l'arbre partagé ; pour des changements non commités, y appliquer `git diff HEAD` **et** y copier les fichiers non suivis (`git ls-files --others --exclude-standard -- <chemins>`).
3. **Lire le code de sortie du build avant de commiter.** Un commit a été fait sur un build en échec.
4. **Une Javadoc dit vrai.** Un prédicat plus large que sa Javadoc (`anyMatch` sur tous les résolveurs au lieu du stock de la hutte) a créé une boucle de requêtes.
5. **Longueur des lignes.** palantir ne recoupe pas la Javadoc ni les commentaires ; `checkLineLength` (build et pre-commit) refuse toute ligne Java de plus de 120 colonnes depuis le 2026-09-30.
6. **Vérifier une affirmation avant de l'écrire comme un fait**, même quand elle vient d'un relecteur. Le piège 1.3 a d'abord été écrit faux (« le contenu tombe au sol à chaque pose ») sur la foi d'une relecture, puis corrigé par une autre, sources à l'appui.

## 4. Pièges du monde

Depuis le 2026-10-02, les systèmes de MC sont portés à l'identique, mais le monde suit Hytale (CLAUDE.md § 6, spec `2026-10-02-hycolony-monde-hytale-design.md`). Avant de recopier une règle de MC, se demander si elle mesure le monde Minecraft. Règles du monde déjà rencontrées :

1. **Chunks et hauteur** : chunks de 32 blocs, hauteur 0–320 (pièges 1.2 et 1.10).
2. **Culture** : stades, durées, eau ×2,5, engrais ×2, lumière ×2, sol labouré qui revient, essence de vie (`sp3b-hytale-farming.md`, vérifié en 0.6.8).
3. **Nourriture et cuisson** : objets, effets, bancs de cuisson (`sp4b-hytale-food.md`).
4. **Monstres** : la plupart des monstres de surface (squelettes, loups, araignées) n'ont pas de condition de lumière ; ceux du Vide la nuit et des grottes en ont une (`LightRanges`, `colony-bounds-and-mob-spawns.md`).
5. **Arbres, minerais, échelles, navigation des PNJ** : `sp3a-mc-miner-hytale-world.md` § B, vérifié en 0.6.8.

L'audit du monde (`audit-monde-hytale.md`, tâche 5 du plan `2026-10-02-hycolony-monde-hytale.md`) recensera le reste.
