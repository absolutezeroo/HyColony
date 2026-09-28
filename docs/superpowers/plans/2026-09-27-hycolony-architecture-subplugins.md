# Assainissement de l'architecture et sous-plugins : plan

> **Pour les agents :** sous-skill requis : superpowers:subagent-driven-development. Étapes à cocher (`- [ ]`).

**But :** ajouter un métier, un bâtiment ou un pack en local, puis livrer les sous-plugins.

**Spec :** `docs/superpowers/specs/2026-09-27-hycolony-architecture-subplugins-design.md`. **Preuves :** `docs/research/architecture/audit-A-core.md` et `audit-B-requests-plugin.md`, avec les lignes exactes.

## Contraintes globales

- `CLAUDE.md` lu en entier.
- TDD : le test qui échoue d'abord, pour tout changement du cœur.
- `./gradlew build` vert à chaque commit ; `git add` avec des chemins explicites ; l'utilisateur committe aussi sur cette branche.
- Aucun changement de comportement en jeu hors de la spec : les tests existants restent verts, sans modifier leurs assertions (sauf un renommage d'API mécanique).
- Pas de nouvelle classe abstraite. Au plus 15 fichiers par paquet, 400 lignes par fichier, 40 lignes par méthode.
- Le serveur n'est jamais lancé. Les listes d'exceptions ne font que rétrécir.

## Points à surveiller en relecture

1. Une vieille sauvegarde (fixtures v1, v2, v3) se charge à l'identique.
2. Le constructeur et le livreur se comportent exactement comme avant : leurs simulations doivent rester vertes.
3. Les onglets affichés sont identiques. Aucun `Message` brut sur `.Text`.
4. Les packs désactivés ne cassent ni le démarrage ni une sauvegarde.

---

### Tâche 1 : sauvegardes tolérantes
- [x] `RequestSerializer` lit un type de requête ou un état inconnu comme vide : la requête est ignorée, avec une ligne de journal, au lieu d'une exception. Même traitement pour une requête dont le parent ou le résolveur manque.
- [x] `CitizenSerializer` garde un métier inconnu brut (son JSON), le réécrit tel quel et ne vide pas l'affectation. Le reste du citoyen fonctionne ; le métier reste inactif.
- [x] Tests :
  - `aRequestOfAnUnknownTypeIsSkippedAndTheColonyLoads` ;
  - `anUnknownJobIsKeptAndWrittenBackUnchanged` ;
  - `reEnablingTheJobRestoresTheAssignment`.
  - Chacun s'appuie sur une fixture de sauvegarde.
- [x] Commit : `fix(core): unknown requests and jobs never lock or erase a colony`.

### Tâche 2 : API des bâtiments et code mort
- [x] `TickingModule` : une seule méthode, `onColonyTick(Colony, Building)`. Mettre à jour les implémentations et les tests.
- [x] `BuildingEventsModule` : l'appeler au retrait et à la fin d'amélioration, là où MC `AbstractBuilding` le fait (lignes citées dans l'audit A § 3). MC n'a pas d'événement de pose : `onPlaced` est retiré. `ColonyBuildingListener` délègue au module au lieu de tester `instanceof WorkerModule`. Tests par événement.
- [x] Supprimer `ConstructionPorts.unavailable()` et les lignes périmées de `BuildingPage` dans `config/pmd/known-violations.txt`, avec l'outil Edit uniquement. `pmdMain` doit rester vert.
- [x] Commits : `refactor(core): one colony tick method for modules`, `feat(core): building events reach their modules (MC AbstractBuilding)`, `refactor: remove dead code`.

### Tâche 3 : socle commun des ouvriers
- [x] Extraire de `construction/builder` les parties génériques vers un paquet partagé, par exemple `job/work` :
  - de `BuilderStock` : dépôt, `keepX`, outil le plus efficace → `WorkerStock` ;
  - de `BuilderRequests` : demande d'outil → `ToolRequests`.
- [x] Le constructeur les compose. Faire de même pour le livreur si c'est pertinent.
- [x] Règle ArchUnit : aucune classe hors de `construction.builder` n'en dépend.
- [x] Tests du constructeur inchangés et verts, plus des tests unitaires des composants extraits.
- [x] Commit : `refactor(core): shared worker stock and tool requests (MC AbstractEntityAIBasic)`.

### Tâche 4 : enregistrement unique
- [x] `CoreFeatures.register(BuildingRegistry, JobRegistry)` déclare le cœur : hôtel de ville, constructeur, résidence, entrepôt, livreur. Ils ne sont pas désactivables.
- [x] `WorldRuntime` et `TestContexts` l'appellent. `HutBlockSystems` construit sa liste de huttes depuis `BuildingRegistry.all()` : le commentaire « BuildingRegistry has no listing » est périmé.
- [x] ~~`FeatureFlags`~~ : reporté à la tâche 7, car aucune fonctionnalité du cœur n'est désactivable.
- [x] Autotest du plugin : chaque type enregistré a son objet de hutte dans `id-map` et au moins un plan.
- [x] Commit : `refactor: one place registers buildings and jobs`.

### Tâche 5 : onglets de hutte fournis par les modules
- [x] Cœur : `BuildingView.tabs` devient une `List<ModuleTab>` scellée, à la place des trois `Optional<*Tabs>`. Chaque module qui a un onglet le fournit, via une capability `ProvidesTabs` ou l'équivalent, comme le producteur de vue de module de MC.
- [x] Plugin : un rendu par type d'onglet, choisi par `switch` exhaustif. Chaque onglet a son propre `.ui`, ajouté comme `RequestsPage` ajoute ses lignes. `Building.ui` ne garde que le cadre.
- [ ] Valider les `.ui` avec le cœur de l'éditeur de l'utilisateur (`C:\Users\Ctuto\Desktop\Hytale UI Editor`) : zéro diagnostic.
- [x] Les onglets affichés restent identiques. Tests des vues.
- [x] Commits : `refactor(core): building tabs come from modules`, `refactor(plugin): one .ui and renderer per hut tab`.

### Tâche 6 : règles ArchUnit (non protégées)
- [x] Figer les frontières de la spec § 6, dont la profondeur d'héritage. Chaque règle doit être vraie aujourd'hui : le prouver en la faisant échouer avec une classe temporaire, puis retirer cette classe.
- [x] Noter dans le rapport les règles qui attendent une session déverrouillée : `NcssCount` dans `ruleset.xml`, et l'échec sur une exception PMD inutilisée dans les contrôles du `build.gradle.kts` racine.
- [x] Commit : `test(core): architecture rules for feature boundaries`.

### Tâche 7 : mécanisme de sous-plugins
- [x] Vérifier dans vineflower `AssetModule.registerPack` et la façon dont un pack apporte `Common/`, `Server/` et les `.lang`. Vérifier aussi si deux packs peuvent fournir des clés du même fichier `hycolony.lang`. Noter tout dans `plugin-b-api.md`.
- [x] Ressources : `subplugins/<Nom>/` avec un manifeste (`Name`, `Version`, `EnabledByDefault`), `Common/` et `Server/`, et des fragments `id-map.json` / `styles.json` fusionnés dans ceux du cœur, avec un conflit de clé signalé au démarrage.
- [x] Un seul registre partagé : `HutBlockSystems` et `ProtectionSystems` lisent le registre de `WorldRuntime` au lieu d'un registre jetable (sinon, la hutte d'un pack n'est pas reconnue).
- [x] `FeatureFlags` est lu depuis `config.json`, section `HyColony` (clé `SubPlugins` : nom → booléen), avec une lecture tolérante.
- [x] Au démarrage : les packs activés (drapeau `SubPlugins`, sinon le défaut du manifeste) sont enregistrés auprès de Hytale, et leurs fragments auprès du plugin. Un pack peut fournir une classe d'enregistrement pour `CoreFeatures` ; ce ne sera utile qu'à partir de SP3, mais le point d'entrée doit exister et être testé.
- [x] Tests du cœur pour la fusion des fragments et les drapeaux ; autotest du plugin pour les packs chargés.
- [x] Commit(s) : `feat(plugin): optional sub-plugins`.

### Tâche 8 : les styles en premiers packs
- [x] Déplacer les assets propres aux styles et leurs entrées `styles.json` dans `subplugins/Styles_Outlander/` et `subplugins/Styles_Kweebec/`, activés par défaut. Le comportement en jeu est inchangé.
- [x] `docs/TESTING.md` : désactiver un pack de style ; la baguette ne propose plus ce style, et une colonie qui l'utilise se charge quand même.
- [x] Commit : `feat: Outlander and Kweebec styles as sub-plugins`.

### Tâche 9 : relecture finale
- [ ] `hycolony-reviewer` sur toute la série. Corrections en un seul lot, avec leur relecture. Feu vert à l'utilisateur.
