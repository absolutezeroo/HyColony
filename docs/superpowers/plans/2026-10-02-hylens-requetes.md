# HyLens V2, lot 3 : les requêtes — plan d'implémentation

> Exécuté en natif (superpowers:executing-plans), l'utilisateur ayant dit « tu peux tout faire » (2026-10-02).

**Goal :** l'onglet Requêtes de HyLens, « Fournir » en créatif (MC) dans HyColony et l'API, la remise à zéro du système de requêtes (MC `requestsystem-reset`).

**Spec :** `docs/superpowers/specs/2026-10-02-hylens-requetes-design.md` ; recherche `docs/research/request-system-reset.md`.

## Global Constraints

Celles du lot 2 (CLAUDE.md entier, TDD, `Deviation from MC:`, en-US et fr-FR, commits par chemins explicites avec `git commit -- <chemins>`, spotless fichier par fichier, `./gradlew build` vert avant chaque commit). Paquets pleins (15 fichiers) : `request`, `colony`, `app/action`, `kernel/port` — aucun fichier nouveau n'y va ; le nouveau code de requêtes de l'app va dans `app/requests`.

## Review Focus

1. Une remise à zéro pendant qu'un livreur porte une livraison, qu'un artisan a une tâche planifiée, qu'un bâtisseur attend ses matériaux : rien n'attend pour toujours (§ 4), et l'ouvrier redemande.
2. Une remise à zéro appelée depuis un rappel du gestionnaire (réentrance de l'`OperationQueue`).
3. « Fournir » gratuit d'un outil quand aucun outil du catalogue ne convient : rien de clôturé.
4. Un citoyen plein en créatif : MC clôture quand même la requête avec la quantité demandée.
5. HyLens contre HyColony 1.2 : refusé.

## Tâches

1. **Objet affiché d'une requête** (cœur) : `ItemCatalog.tools()` (port, plus `FakeCatalog`) ; `Deliverable.displayed(ItemCatalog)` : `StackRequest` son objet, `StackList` son premier accepté, `ToolRequest` le premier de `catalog.tools()` (triés par id) qu'elle accepte (`matches`). Tests : les trois genres, un outil hors niveau écarté, aucun outil. Plugin : `HytaleItemCatalog.tools()` (Item.getAssetMap().getAssetMap().values(), ceux dont `tool()` est présent, triés, gardés en cache), sans dépasser 400 lignes (extraire si besoin).
2. **« Fournir » en créatif** (cœur) : `app/requests/RequestFulfil` reprend de `RequestActions` l'essentiel de `fulfil` (`itemFor`, `deliver`, rendu au joueur) avec un payeur facultatif : absent = gratuit (objet affiché, quantité demandée, rien pris, requête clôturée avec la quantité demandée même si le citoyen est plein, comme MC). `RequestActions.fulfil` garde la permission et le refus dit, puis passe le joueur comme payeur sauf en créatif. Tests : créatif pile, liste, outil, aucun outil ; survie inchangé (tests existants).
3. **Remise à zéro** (cœur) : `RequestStore.clear()` et `ResolverRegistry.clear()` ; `RequestManager.reset(List<Resolver> builtIns, Collection<ResolverProvider> providers)` dans l'`OperationQueue` : vide, réenregistre les intégrés, réajoute les fournisseurs. `app/requests/RequestSystemReset.reset(Colony)` : nouveaux `PlayerResolver` et `RetryingResolver`, les huttes, vide les tâches des artisans (`CraftingTasks.onTaskDeletion` de chaque jeton) et la file et les livraisons en cours des livreurs (`DeliverymanJob`), garde la file d'entrepôt, `markDirty`. Config `Commands.canPlayerUseResetCommand` (défaut false), `CommandsSection`, `config-inventory.md`. Tests : plus de requête ; une nouvelle requête est assignée (huttes réenregistrées) ; files vidées ; file d'entrepôt gardée ; colonie marquée ; réentrance (reset depuis un rappel).
4. **API 1.3** : `DebugAccess.fulfilRequest(Actor, ColonyRef, String)` et `resetRequests(Actor, ColonyRef)` ; `ApiVersion` 1.3.0, `ApiValuesTest`, README § 5 ; `CoreDebugRequests` (paquet `app/api`) : droits de la spec § 5, `NotFound` pour un id inconnu, mal formé ou une requête fermée. HyLens : `BUILT_AGAINST` 1.3, `FakeColonyWorld`, `ApiCompatibilityTest`. Textes `hycolony.lang` si besoin.
5. **Cœur de HyLens** : `MenuTab.REQUESTS` ; `MenuState.request` (`Optional<String>`, `withRequest`, oublié par `withColony`) ; `MenuView.RequestRow(id, item, count, building, citizen, state, resolver, chosen)` et `requests` (états ouverts) ; `MenuViews`. Tests.
6. **Plugin de HyLens** : sceau et rubans de l'emplacement 3 (`red_wax_work_orders`, `bookmark_*_04`) copiés ; `Menu.ui` (emplacement 3) ; `Book/Requests.ui`, `Book/RequestRow.ui` ; `RequestsTab` ; `MenuRender` (onglet et nom) ; clics `request`, `fulfil`, `resetRequests` ; `MenuActions` ; textes ; `docs/TESTING.md`.
7. **Relectures** : `hycolony-reviewer`, `mc-fidelity-checker` (tâches 2 et 3), `ui-lang-checker` (tâche 6) ; corrections relues.
