# Audit global HyColony : 02, axe F, hygiène ECS

```
ÉTAT : phase 2, axe F écrit. Code audité : commit 3e2e70ca. Sources : rapport « ECS » (vérifications dans
build/vineflower), rapport « threads ». Chemins relatifs à la racine du dépôt.
```

**Note de l'axe : 5/5, proportionnée à une surface ECS minuscule.** Deux composants de données pures (`CitizenTag` avec codec, `MoveTarget` transitoire), 19 systèmes tous enregistrés dans `setup()` par les proxies, aucun `isParallel`, aucun « manager » qui itère toutes les entités (l'état vit dans le cœur, indexé par id), `Ref` toujours vérifiées par `isValid()` avant usage. Les deux constats sont des détails.

## 1. Constats

### F-1 — BAS — Les systèmes de huttes ne déclarent aucun ordre vis-à-vis des autres mods
`plugin/src/main/java/dev/hycolony/plugin/block/HutBlockSystems.java:64, 120, 162`
```java
public static final class Place extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
```
Mécanisme : `HutBlockSystems.*` n'ont ni `getGroup()` ni `getDependencies()` ; `BlockUseProtectionSystem` et `FieldBlockSystems` en déclarent. L'ordre d'enregistrement ne fixe pas l'ordre d'exécution.
Impact : une protection d'un autre mod qui annule la pose **après** que la hutte a été enregistrée dans le cœur laisserait une hutte sans bloc. Risque faible (aucun autre mod de protection aujourd'hui).
Règle : § 4 (robustesse). Remède : `SystemDependency(Order.AFTER, ProtectionSystems.Place.class)` comme `FieldBlockSystems`, et un groupe « filter ».
Sévérité BAS · effort S · confiance HAUTE · DÉJÀ CONNU non.

### F-2 — BAS — Gardes `isCancelled()` redondantes
`plugin/src/main/java/dev/hycolony/plugin/block/BlockUseProtectionSystem.java:72` ; `FieldBlockSystems.java:67, 114, 160` ; `domum/plugin/.../cutter/CutterSystem.java:44` ; `vanilla/plugin/.../block/FlowerPotSystem.java:55`
Mécanisme : `EntityEventSystem.handleInternal` saute déjà un événement annulé (`EventSystem.shouldProcessEvent`, `EntityEventSystem.java:17-21`) ; seul l'ordre déclaré compte.
Impact : aucun ; code qui laisse croire que la garde est nécessaire.
Règle : § 3 (le commentaire ou le code doit dire le pourquoi exact). Remède : retirer, ou commenter « défense en profondeur ».
Sévérité BAS · effort S · confiance HAUTE · DÉJÀ CONNU non.

## 2. Non retenus

- `CitizenTag` et `MoveTarget` : données pures, `clone()`, codec pour le seul persisté ; `ComponentType` statiques assignés une fois dans `setup()` (tolérés par la consigne).
- `GogglesSystems.Visibility` : seule `EntityTickingSystem`, groupe et dépendance déclarés (`FIND_VISIBLE_ENTITIES_GROUP`, `AFTER CollectVisible`), écrit `EntityViewer` en place comme le système vanilla qu'elle suit ; sa garde manquante est E-3.
- `CitizenBodyLifecycleSystem` (`RefSystem`) : `AddReason.LOAD` seul, `isValid` par `ref(body)` ; `RefChangeSystem` : aucun, donc pas de garde à vérifier.
- Index inverse colonie → corps : tenu par événement (`track/untrack`), pas par balayage.
- `CommandBuffer` utilisé pour lectures et effets seulement : correct pour les systèmes d'événements ; les changements structurels sont différés par `world.execute` (voir E-8 pour le tick).
- Aucun système sur `ChunkStore`, aucune `Resource`, aucun `DelayedSystem` (choix documenté dans `ColonyTickSystem` : « DelayedSystem resets its timer instead of carrying the remainder »).

## 3. Ce qui est bien fait

- `plugin/src/main/java/dev/hycolony/plugin/npc/HyColonyComponents.java:19-20` : un composant persisté (`CitizenTag`, id `HyColonyCitizen`, codec) pour retrouver la colonie et le citoyen d'un corps après rechargement, un composant transitoire sans codec pour la cible de marche ; rien d'autre dans l'ECS.
- `plugin/src/main/java/dev/hycolony/plugin/block/BlockUseProtectionSystem.java:38-56` : `SystemDependency(Order.BEFORE, HyDomumSystems.cutterUse())` et `HyVanillaSystems.flowerPotUse()` : l'ordre entre mods est déclaré par les `api`, et les mods frères enregistrent leur système inconditionnellement pour que la dépendance existe (commentaire `ComponentRegistry.java:657-659`).
- `plugin/src/main/java/dev/hycolony/plugin/adapter/HytaleCitizenBodies.java:96-99` : `ref(body)` = `refs.get` + `isValid()` pour tout accès à un corps.

## 4. Tableau

| Sév. | `chemin:ligne` | Titre |
|---|---|---|
| BAS | `plugin/.../block/HutBlockSystems.java:64, 120, 162` | F-1 pas d'ordre vis-à-vis des autres mods |
| BAS | `plugin/.../block/BlockUseProtectionSystem.java:72` (+5) | F-2 gardes `isCancelled()` redondantes |
