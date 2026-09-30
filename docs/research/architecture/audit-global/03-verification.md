# Audit global HyColony : 03, vérification (phase 4)

```
ÉTAT : phase 4 en cours, interrompue par la limite d'utilisation de la session (2026-09-29, ~21:55).
Code audité : commit 3e2e70ca (identique à c24bf475 hors docs). Attention : l'arbre de travail est en cours de
modification par une autre session (citizen/*, builder/*Walker, *Context, BodyWalker) : les lignes citées valent
pour la version committée (`git show HEAD:<chemin>`).
Fait : 1 des 4 rapports de vérification reçu (axes A, B, D, H, J, K, L), reporté ci-dessous.
Reste : les 3 autres rapports (M-1 à M-8 ; M-9 à M-17 ; E-1 à E-6 et I-1 à I-7) étaient en cours quand la session
s'est arrêtée : leurs conclusions arrivent dans la conversation à leur fin ; sinon relancer un vérificateur par
groupe avec la consigne de la phase 4 (réfuter, 6 questions, décision CONFIRMÉ/DÉCLASSÉ/DÉJÀ CONNU/AVERTISSEMENT/
ABANDONNÉ). Puis : reporter les décisions ici, mettre à jour les sévérités finales dans les fichiers 02, écrire
AUDIT.md (format § 11 du prompt : décompte, résumé, scorecard, à conserver, top 25, quick wins, plan en 3 paliers,
architecture cible, checklist de complétude = 02-findings-completude.md, compatibilité 0.7.0-pre.4, annexes).
```

## 1. Axes A, B, D, H, J, K, L (rapport reçu)

| # | Décision | Sévérité finale | Raison |
|---|---|---|---|
| A-1 | DÉCLASSÉ | BAS | 279 l., PMD vert, aucune règle [build] ; l'impact « chaque métier rallonge la classe » est réfuté par `LogisticsActions`/`CraftingActions`/`FieldActions` ; couplage et triplet déjà connus (`audit-A-core.md:171`, `audit-duplication-couplage.md:30`) |
| A-2 | DÉCLASSÉ | BAS | 25 méthodes (pas 48, l'estimation shell comptait les `else if`), règles statiques ~25 l. déclarées dans le périmètre de la classe, imports partagés par `WorkOrderValidation`/`ManualSelection` ; diffusion déjà connue (`audit-duplication-couplage.md:40-42`) |
| A-3 | DÉCLASSÉ | BAS | Exact, mais un seul appelant (test), `public` imposé par les paquets, remède par `FakeClock`/`FakeBodies` ; aucun impact réel |
| A-4 | DÉJÀ CONNU | BAS | `BACKLOG.md:80` (reporté par décision) ; les règles sont déjà dans `domum/core` avec 13 tests (`CutterCraftTest`), le plugin garde l'orchestration et le `10` du bouton |
| B-1 | DÉJÀ CONNU | BAS | `BACKLOG.md:76,86`, `audit-B` § 2 ; liste de 16 lignes = mécanisme sanctionné par § 8, forcée à rétrécir par le build |
| D-1 | DÉCLASSÉ | BAS | Mécanisme vérifié (`BuilderFactory.add` lève, `/plugin reload` existe), mais rechargement hors règles, hors `TESTING.md`, hors processus (§ 9.4) ; correctif S ou spec subplugins l. 57 à amender (elle promet le désenregistrement « au rechargement ») |
| D-2 | DÉCLASSÉ | BAS | Mécanisme vérifié (un seul `LoadAssetEvent`, `start` jamais rappelé), même cadre que D-1 ; la paire est déjà non rechargeable par la dépendance HyColony → HyDomum |
| H-1 | AVERTISSEMENT | retiré | Décision écrite : spec SP0 l. 206/251 et `config-inventory.md:269` (MC force l'apparition initiale ; 10 < 25, le plafond ne peut jamais mordre) ; le remède serait un écart à MC |
| H-2 | CONFIRMÉ | MOYEN | `displayName()` = `hycolony:builder` rendu brut par `Texts.translated` dans le chat de chaque membre à chaque ordre (`hycolony.lang:100`) ; § 7 violé, non connu, `WorkManagerTest.java:129` n'assertionne que la clé |
| J-1 | DÉCLASSÉ | BAS | Écriture d'adaptateur fidèle à MC (`SlotItemHandler`), permission à l'ouverture fidèle, règle et test dans le cœur (`CitizenInventoryTest`) ; reste l'id du joueur absent d'`onPlayerEdit` |
| K-1 | DÉCLASSÉ | BAS | Toutes les 6 000 ticks, pas un chemin chaud (§ 4) ; « toujours sale » est normal ; MC sauve pareil ; coût non mesuré |
| L-1 | DÉCLASSÉ | BAS | Trou exact mais permutations d'un mécanisme de reprise unique déjà testé sur CLEAR, SOLID, CLEAR_LEFTOVERS ; stades testés sans restart |
| L-2 | DÉCLASSÉ | BAS | `onBodyUnloaded` sans test (vrai, 8 l.) ; `PermissionsSerializer` testée par `PersistenceTest.java:73-74` (seul le cas clés absentes manque) ; cutter DÉJÀ CONNU |

Bilan partiel : 1 CONFIRMÉ (H-2), 1 AVERTISSEMENT retiré (H-1), 2 DÉJÀ CONNU (A-4, B-1), 9 DÉCLASSÉ vers BAS.

## 2. Axe M, seconde moitié (M-9 à M-17 : rapport reçu)

| # | Décision | Sévérité finale | Raison |
|---|---|---|---|
| M-9 | AVERTISSEMENT | BAS (doc) | Spec SP0 § 3.1 l. 123 et § 6 l. 341 choisissent `reset()` ; MC hors production attend aussi 100 ticks fixes (`AbstractEntityAIBasic.java:360`) ; l'avancement n'est pas perdu (il vit dans l'ordre) ; reste le `Deviation from MC:` sur les deux `onException`. |
| M-10 | AVERTISSEMENT | BAS | Spec SP0 § 3.4 l. 205, 358 fixe la chaîne `respawnPosition ?: lastPosition ?: hôtel de ville` ; cadence 5 min identique à MC (`CitizenManager.java:587-589`) ; seuls les candidats hutte de travail/logement, arrivés après SP0, manquent. |
| M-11 | DÉCLASSÉ | BAS | Réel et non marqué, mais disque/carré ne diverge qu'aux coins d'un carré de 41 cellules et la boîte ne compte qu'au niveau 0 ; `Blueprint.min/max` permet de porter la boîte (spec SP1-2 l. 299 périmée). |
| M-12 | AVERTISSEMENT | BAS (doc + spec) | Spec SP0 § 3.2 l. 133-135 décide « centre chargé » (justifié) et « ≥ Friend », ce dernier étiqueté à tort « reprise de MC » (`isColonyManager`, `EventHandler.java:471`) ; le filtre de monde est sans effet (UNLOADED ≡ INACTIVE en HyColony). |
| M-13 | DÉCLASSÉ | BAS | Citation MC fausse et ajout non marqué, mais information supplémentaire sans règle changée ; « rang personnalisé » sans objet (cinq rangs fixes, `Permissions.java:62-63`). |
| M-14 | DÉCLASSÉ | BAS | Écart réel (`RegisteredStructureManager.java:295`) mais les quatre modules tickants ne touchent que des données (recettes, champ, embauche, rattachement) : garde `isLoaded` d'une ligne ou marqueur. |
| M-15 | DÉCLASSÉ | BAS | Ordre inversé partout (`getContainers` :140-145, handler combiné :635-655) mais seul le conteneur rempli/vidé en premier change ; une ligne dans `Building.containers()` aligne tous les usages ; spec SP3a l. 73 déjà partielle. |
| M-16 | DÉCLASSÉ | BAS | `ToolType` ⊂ `keepX` MC (pas d'outil hors liste), seaux suivants rarement portés et repris à la hutte sans requête ; plan SP3a l. 66 l'a choisi ; remède d'une ligne `dumpKeepingHutRules(true)`. |
| M-17 | DÉCLASSÉ | BAS | Plafond = correction d'un dépassement MC quasi inatteignable ; `onLevelUp` n'importe que pour le livreur, couvert par `applySpeed` (spec SP3a l. 74) ; restent le marqueur sur `addXp` et le son/particules. |

Bilan partiel : 0 confirmé MOYEN, 3 AVERTISSEMENT (décisions de spec sans marqueur de code : M-9, M-10, M-12), 6 DÉCLASSÉ vers BAS ; aucun abandonné (les neuf mécanismes sont réels).

## 2 bis. Axe M, première moitié (M-1 à M-8) : rapport reçu

Tous les fichiers du cœur relus à `3e2e70ca` via `git show` (l'arbre de travail modifie `CitizenAI`). Aucun des huit n'est dans `docs/BACKLOG.md` ni dans les audits antérieurs (grep).

| # | Décision | Sévérité finale | Raison |
|---|---|---|---|
| M-1 | CONFIRMÉ | HAUT | Chemin réel (`blocks.get` vide → CLEAR sauté ; `satisfied(e, null)` faux → SOLID/DECORATE « à faire » → marche « terminée » par l'anti-blocage → `place` false → index avancé → COMPLETE_BUILD) ; MC garde `isBlockLoaded` (`AbstractEntityAIStructureWithWorkOrder.java:479-496`, STATE_BLOCKING cadence 1) et Minecraft charge les chunks en synchrone, que Hytale n'a pas ; aucune garde, aucun marqueur, aucune décision écrite. |
| M-2 | DÉCLASSÉ | MOYEN | Exact (MC `Permissions.java:694-706`, `EventHandler.java:601, 642, 743`), 14 appelants directs, et la spec SP0 § 3.2 l. 175 **appuie** le constat ; déclassé parce que l'impact ne touche que les opérateurs en créatif dans une colonie étrangère (contournable par `/hycolony rank`). |
| M-3 | AVERTISSEMENT | — | Exact (MC = 13 200 ticks par essai), mais décidé par le plan sp12-core l. 358 (« décrémente chaque délai de 11 ») et l. 30, la spec sp12 l. 97, `docs/TESTING.md` l. 61 (validé en jeu) ; `minecolonies-analysis.md:78` dit l'inverse ; il manque le marqueur § 6 : à trancher (marqueur + spec, ou alignement). |
| M-4 | DÉCLASSÉ | MOYEN | MC `RequestHandler.java:461-468` vérifié ; mais pour un parent `Deliverable` le résolveur « retrying » (accepte tout `Deliverable`) rattrape avant le joueur : le blocage n'existe que pour un parent **non-`Deliverable`** (tâche `Crafting` héritant `{player}` dont la livraison de suite passe FAILED sans artisan) : chaîne rare mais sans sortie (§ 4), correction d'une ligne. |
| M-5 | DÉCLASSÉ | MOYEN | Remontée MC réelle (`StandardPlayerRequestResolver.java:190-220`), mais MC n'appelle `onColonyUpdate` que depuis `AbstractCraftingBuildingModule` (recettes) ; `claimOpenFromHut` et `onContainerChanged` sont des déclencheurs propres à HyColony (spec sp12 l. 99, 239) : écart réel pour le seul cas « recette apprise » ; le joueur reste une sortie. |
| M-6 | AVERTISSEMENT | — | Exact (MC `ColonyManager.java:311-324` : centres ≥ 128 blocs + 9×9 libre ; HyColony strictement plus restrictif) ; décidé par la spec SP0 § 3.2 l. 152 sous le titre « conversion fidèle à MineColonies » (l. 149) : prétention de fidélité inexacte ; marqueur ou alignement à décider. |
| M-7 | CONFIRMÉ | MOYEN | UPGRADE part à SOLID (`WorkOrder.java:131-138`), `mustMineFirst` mine avec délai/usure/XP **avant** `lacking` ; Structurize `StructurePlacer.java:233-239, 330-351` remplace sans délai après MISSING_ITEMS ; aucun des 8 marqueurs du paquet `builder` ne le couvre, rien dans la spec sp12 § 11. |
| M-8 | AVERTISSEMENT | — | Exact (MC `EntityAICitizenWander.java:82, 258-297` : 100 ticks, depuis la position courante, sans état WANDERING) ; comportement conçu par la spec SP0 § 3.4 l. 214-217 comme « version réduite » ; marqueur à ajouter ou alignement. |

Bilan partiel : 1 CONFIRMÉ HAUT (M-1), 1 CONFIRMÉ MOYEN (M-7), 3 DÉCLASSÉS vers MOYEN (M-2, M-4, M-5), 3 AVERTISSEMENTS (M-3, M-6, M-8).

## 5. Bilan final de la vérification (4 rapports sur 4, 43 constats MOYEN ou plus)

- **HAUT confirmés (2)** : M-1 (chantier « terminé » dans un chunk non chargé), E-2 (le fermier ne redemande jamais graines ni engrais).
- **MOYEN confirmés ou déclassés vers MOYEN (7)** : M-2, M-4, M-5, M-7, H-2, E-3, I-1.
- **AVERTISSEMENTS (7)** : décisions écrites (specs SP0/SP1-2, plan SP1-2) sans le marqueur `Deviation from MC:` exigé par § 6, ou avec une prétention de fidélité inexacte : M-3, M-6, M-8, M-9, M-10, M-12, H-1 (retiré : le plafond ne peut pas mordre).
- **ABANDONNÉS (2)** : E-4 (attente identique à celle de Hytale), E-6 (déjà gardé par fenêtre).
- **DÉJÀ CONNU (4)** : A-4, B-1, I-7, plus le cutter de L-2.
- **DÉCLASSÉS vers BAS (21)** : A-1, A-2, A-3, D-1, D-2, J-1, K-1, L-1, L-2, M-11, M-13, M-14, M-15, M-16, M-17, E-1, E-5, I-2, I-3, I-4, I-5, I-6.

Aucun constat CRITIQUE. Les sévérités finales ci-dessus priment sur celles des fichiers `02-findings-*` (qui gardent leur sévérité d'avant vérification, conformément à la consigne « ne réécris pas les constats »).

```
ÉTAT (mis à jour) : phase 4 terminée. Reste : AUDIT.md (format § 11 du prompt), avec le décompte final :
constats principaux 60 (A 8, B 3, C 2, D 4, E 11, F 2, G 5, H 5, I 10, J 4, K 7, L 5, M 10 ; hors M-11..M-32
secondaires et hors AVERTISSEMENTS/ABANDONNÉS), sévérités finales : 2 HAUT, 7 MOYEN, le reste BAS ;
compat 0.7.0-pre.4 : 0 symbole à migrer, 2 points restants d'update-7 (isLoaded par colonne, entrée
tools/decorations à retirer).
```

## 3. Axes E (E-1 à E-6) et I (I-1 à I-7) : rapport reçu

Décompilé lu par le vérificateur : `Store.java:693-735, 1184-1186, 1990-2039, 2310-2315`, `PlayerRef.java:264-268`, `PlayerRefAddedSystem.java:76-110`, `TeleportSystems.java:305-309`, `World.java:165, 300-455, 1120-1126`, `TickingThread.java:57, 88-97`, `MapMarkerTracker.java:69-81`, `Universe.java:955-963, 1294-1364`, `CloseWorldWhenBreakingDeviceSystems.java:23-80`, `PageManager.java:129-139, 208-227`, `PlayerSystems.java:474`.

| # | Décision | Sévérité finale | Raison |
|---|---|---|---|
| E-1 | DÉCLASSÉ | BAS | Le mécanisme n'existe pas : `world.getPlayerRefs()` est alimentée par `PlayerRefAddedSystem.onEntityAdded:81-82` et vidée par `onEntityRemove:109` (synchrone dans `Store.removeEntity:733`) ; le transfert fait `removeFromStore()` sur l'ancien monde avant `addPlayer` sur le nouveau (`PlayerRef:264-268`) : une ref de `A.players` est nulle ou dans le store de A. Et une exception de `ColonyState.of` serait attrapée par `TickRateStateMachine:120-125` (colonie suspendue 5 min), jamais par `tickCore`. Reste la cohérence avec `facing`/`isCreative` (hygiène). |
| E-2 | CONFIRMÉ | HAUT | Requête de hutte `COMPLETED` (ordinal 7) `isBefore(RECEIVED)` (10) : `askOnce` sort ; les trois seuls émetteurs de `RECEIVED` sont `Building.onRequestComplete:198` (non livrables seulement), `SyncRequests.pickUp:105` (id du citoyen), `BuilderRequests:83` (bâtisseur seul) ; « Fournir » → `overrule` → `COMPLETED` sans parent, reste dans `byRequester(hut)`. Test l. 100-114 ne complète jamais la requête. Spec farmer § 12 décide le dépôt au nom de la hutte, pas l'absence de réception : remède compatible. |
| E-3 | CONFIRMÉ, DÉCLASSÉ | MOYEN | Thread tué prouvé (`Store.java` sans aucun `catch`, `TickingThread` : `catch (Throwable)` hors de la boucle `while`, `MapMarkerTracker:79-81` sans garde), mais aucune exception atteignable aujourd'hui dans `hideFromOthers` (itère sa propre map) ni `Highlights` (CHM) ; garde triviale par cohérence. |
| E-4 | ABANDONNÉ | — | Le cycle W → F → écrivain est réel mais il est **celui de Hytale**, dans la même pile juste après (`Universe.removeWorld:1310-1315` `runAsync(F::stopIndividualWorld).join()` → `World.stopIndividualWorld:319-325` → file de F → `readLock`) ; un `orTimeout` ne déplace le blocage que d'une ligne ; `SkipSentryException` est une `RuntimeException` couverte par le `catch` l. 59. Un commentaire citant `Universe.removeWorld:1314` suffirait. |
| E-5 | DÉCLASSÉ | BAS | Mécanisme réel (`openCustomPage:136` `build` sans `try`), mais seule chaîne d'appel `CitizenPage.select:167` sous `PageEvents.guard` ; fantôme fermé par `closeAllWindows` (`PlayerSystems:474`) au départ du joueur. |
| E-6 | ABANDONNÉ | — | `OpenWindows.refresh:55-62` et `LiveWindows:31-53` attrapent tout et retirent la fenêtre fautive : rien ne sort de `windows.tick()`, l'autosave n'est jamais manquée par ce chemin. |
| I-1 | CONFIRMÉ, DÉCLASSÉ | MOYEN | § 5 littéral violé et incohérent avec `CourierAssignmentModule:100-107` (qui replie déjà sur `DEFAULT`) ; `SavedJson.enumOf` existe ; mais l'issue est le verrou **conçu** (fichier jamais réécrit, corps gardés), et le déclencheur (retour de version après ajout d'un mode, fichier édité) est rare. Correctif d'une ligne. |
| I-2 | DÉCLASSÉ | BAS | Tous les champs de rang existent depuis la fixture v1 (`colony-v1.json:11-12`), aucune migration n'en a ajouté : édition manuelle seulement ; `ColonySerializer:76-78` déclare `permissions` bloquant. |
| I-3 | DÉCLASSÉ | BAS | Formes jamais écrites par `write` (l. 86-91) ; fichier édité ou corrompu en gardant un JSON valide seulement ; norme interne `SavedJson` non suivie. |
| I-4 | DÉCLASSÉ | BAS | Perte auto-réparée par conception (Javadoc l. 17 « the requesters ask again » ; demandeur inconnu gardé brut et redemande au retour du pack) ; conforme à la spec sous-plugins l. 12 (« ignorée, avec une ligne de journal ») ; seul le commentaire `SavedRequests.java:29-30` est faux. |
| I-5 | DÉCLASSÉ | BAS | Double condition rare (verrou + fondation exactement là + nouveau retour de version), aucune donnée détruite, au rechargement `claimSquare` (`putIfAbsent`) partage les cellules ; le territoire est « non persisté : voulu ». |
| I-6 | DÉCLASSÉ | BAS | Reprise bornée par `SCAN_LIMIT` (cases satisfaites sautées par lots), effet = comportement de première passe déjà documenté comme limite (`docs/research/connected-blocks.md:113`) ; recoupé par L-1. |
| I-7 | DÉJÀ CONNU | BAS | Audit B § 1.3 (l. 48, 178 : « au prochain type de requête ») ; `write` est exhaustif à la compilation, seul `read` est ouvert. |

Bilan partiel : 1 CONFIRMÉ HAUT (E-2), 2 CONFIRMÉ déclassés en MOYEN (E-3, I-1), 2 ABANDONNÉS (E-4, E-6), 1 DÉJÀ CONNU, 7 DÉCLASSÉS vers BAS.

## 4. Bilan provisoire (3 rapports sur 4)

Sur 35 constats MOYEN ou plus vérifiés : **1 HAUT confirmé** (E-2), **3 MOYEN confirmés** (H-2, E-3, I-1), 7 AVERTISSEMENTS (décisions écrites sans marqueur : H-1 retiré, M-9, M-10, M-12), 2 ABANDONNÉS (E-4, E-6), 4 DÉJÀ CONNU, 21 DÉCLASSÉS vers BAS. Restent M-1 à M-8 (dont 5 HAUT avant vérification).
