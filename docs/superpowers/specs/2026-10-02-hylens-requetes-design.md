# HyLens V2, lot 3 : les requêtes (onglet, « Fournir » en créatif, remise à zéro)

Suite de `2026-10-02-hylens-livre-mc-design.md` (§ 2, lot 3) et de `2026-10-02-hylens-citoyens-design.md` (API 1.2).
Recherche : `docs/research/request-system-reset.md`.

## 1. Objectif

L'utilisateur veut agir sur les requêtes d'une colonie depuis HyLens (2026-10-02), en MC strict :
- voir les requêtes ouvertes d'une colonie ;
- les remplir comme le bouton « Fournir » de MC, gratuit en mode créatif ;
- remettre à zéro le système de requêtes (MC `/mc colony requestsystem-reset`).

« Donner un objet » quelconque à un citoyen est écarté : MC n'a rien de tel.

## 2. MineColonies

**« Fournir »** (`RequestWindowCitizen.CitizenRequestTreeWindowModule`, l. 157-245) :
- possible sur une requête d'objets (`IDeliverable`) du citoyen, sauf une requête fille dont le demandeur est ailleurs ; en créatif toujours, sinon si le joueur porte un objet qui convient ;
- en créatif : l'objet est le premier de `request.getDisplayStacks()`, la quantité celle demandée, et rien n'est pris au joueur ; sinon le premier emplacement du joueur qui convient (hors armure et bouclier) et `min(demandé, possédé)` ;
- le serveur met l'objet dans l'inventaire du citoyen (`TransferItemsToCitizenRequestMessage`, l. 85-180, autant que possible), puis la requête passe `OVERRULED` avec cet objet et cette quantité (`UpdateRequestStateMessage`, permission `MANAGE_HUTS`).

**Remise à zéro** (`CommandRSReset`, recherche § 1) :
- un joueur, ou une source op 4 ; un non-op seulement si `canPlayerUseResetCommand` (défaut `false`), sinon `notenabledinconfig` ; aucun contrôle d'appartenance à la colonie ;
- `StandardRequestManager.reset()` : de nouveaux stores vides (toutes les requêtes et affectations disparaissent, sans rappel d'annulation), de nouveaux résolveurs joueur et retrying, puis chaque bâtiment ré-ajouté comme fournisseur (`InitialUpdate`) ;
- les files des artisans et des livreurs, rangées dans ces stores, se vident ; la file de l'entrepôt, hors du gestionnaire, garde ses jetons morts, que le livreur purge au passage ;
- les ouvriers redemandent à leur prochain besoin.

## 3. HyColony : « Fournir » en créatif (cœur, TDD)

`RequestFulfil` (appelé par `RequestActions.fulfil` et par l'API) gagne la branche créative de MC : si le joueur est en créatif (`PlayerDirectory.isCreative`), l'objet est le premier objet affiché de la requête et la quantité celle demandée, sans rien prendre au joueur ; l'objet va au citoyen, ou aux conteneurs de la hutte pour une requête sans citoyen (écart documenté), puis la requête est clôturée par `overrule` avec la quantité demandée, comme MC. La fenêtre du citoyen et le presse-papiers proposent « Fournir » en créatif sur toute requête d'objets, comme MC `isFulfillable`.

**Objet affiché** (`Deliverable.displayed(ItemCatalog)`, MC `getDisplayStacks().findFirst()`) :
- `StackRequest` : son objet ;
- `StackList` : son premier objet accepté ;
- `ToolRequest` : l'outil du catalogue du bon type, entre les niveaux demandés, du niveau le plus bas (puis par identifiant). Le port `ItemCatalog` gagne `tools()`, la liste des objets qui sont des outils, lue dans `Item.getAssetMap()` comme `foods()` ;
- aucun : la requête est clôturée sans livraison, comme MC (qui `OVERRULED` avec une pile vide).

Hors créatif, inchangé : le premier objet du joueur qui convient, `min(demandé, possédé)` ; un citoyen plein clôture quand même la requête avec ce que le joueur possède, comme MC.

## 4. HyColony : la remise à zéro (cœur, TDD)

D'après la recommandation de la recherche (§ 3) :
`RequestSystemReset.reset(Colony)` appelle `RequestManager.reset(builtIns, providers, alsoForget)`, qui fait tout en une étape de son `OperationQueue` (une remise à zéro demandée pendant une affectation attend qu'elle finisse) :
1. vide le store des requêtes (en gardant ses écouteurs) et le registre des résolveurs, sans rappel d'annulation (MC n'en joue pas) ;
2. enregistre un nouveau résolveur joueur et un nouveau résolveur retrying, puis chaque hutte comme fournisseur ;
3. vide la file et les tâches planifiées des artisans (`CraftingTasks`) et la file et les livraisons en cours des livreurs (`DeliverymanJob`), que MC range dans ses stores ; garde la file de l'entrepôt et les compteurs des artisans, comme MC ;

puis la colonie est marquée à réécrire.

Aucun état ne doit attendre pour toujours (CLAUDE.md § 4) : la recherche a vérifié que les détenteurs de jetons restants (file de l'entrepôt, ouvrier en attente) tolèrent un jeton inconnu.

**Config** : `ColonyConfig.Commands.canPlayerUseResetCommand` (défaut `false`, MC), clé `CanPlayerUseResetCommand` de la section `Commands`.

## 5. L'API (1.3)

`DebugAccess`, `@Experimental` comme le reste, `ApiVersion.CURRENT` 1.3.0, `ApiCompatibility.BUILT_AGAINST` 1.3.0 :

```java
/** MC's request window "Fulfill": free in creative mode, else from the player's inventory. @since 1.3 */
ActionResult fulfilRequest(Actor actor, ColonyRef colony, String requestId);

/** MC /mc colony requestsystem-reset. @since 1.3 */
ActionResult resetRequests(Actor actor, ColonyRef colony);
```

- `requestId` est l'`id` de `RequestSnapshot` ; un identifiant inconnu, d'une requête fermée ou d'une requête qui ne demande pas d'objets (livraison, ramassage) donne `NotFound`.
- **Droits de `fulfilRequest`**, vérifiés avant la recherche de la requête : un joueur, `MANAGE_HUTS` (refus du cœur `hycolony.permission.denied`) ; un plugin fournit gratuitement, comme en créatif ; la colonie est refusée. `Unavailable` quand le joueur n'en porte pas (en créatif, la requête est close même si rien ne peut être reçu, comme MC).
- **Droits de `resetRequests`** : un opérateur toujours ; un autre joueur seulement si `CanPlayerUseResetCommand` (sinon `hycolony.debug.refused.config`), sans contrôle de membre, comme MC ; un plugin oui ; la colonie non.

## 6. HyLens

**Onglet « Requêtes »** (emplacement 3 du livre, sceau `red_wax_work_orders` de MC et rubans `_04`, copiés dans le pack de HyLens) :
- page de gauche : les requêtes ouvertes de la colonie choisie (`ColonyWorld.requests`, états ouverts de `CREATED` à `FOLLOWUP_IN_PROGRESS`, `RESOLVED` compris car MC ne prévient le demandeur qu'à `COMPLETED`), une ligne chacune : objet × quantité, demandeur (hutte ou citoyen), état, et la flèche « > » pour la choisir ;
- page de droite : la requête choisie (objet, quantité, demandeur, état, résolveur), le bouton **« Fournir »** pour une requête d'objets seulement (pile, outil, liste), et en bas **« Remettre à zéro les requêtes »** ;
- relue à chaque affichage : rien de fantôme après une remise à zéro.

**Cœur de HyLens** (TDD) : `MenuTab.REQUESTS` ; `MenuState` retient la requête choisie (`Optional<String>`), oubliée en changeant de colonie ; `MenuView` porte les lignes de requêtes et la requête choisie (`RequestRow`), construites par `MenuViews` depuis `RequestSnapshot` ; le nom de l'objet est traduit côté plugin (`Item.getTranslationKey`, sur `.TextSpans`).

**Textes** (`hylens.lang`, en-US et fr-FR) : l'onglet, les en-têtes, « Fournir », « Remettre à zéro les requêtes », « aucune requête », une clé par état affiché.

## 7. Tests

- Cœur de HyColony :
  - `fulfil` en créatif : objet affiché, quantité demandée, rien pris au joueur, requête clôturée ; pour une requête de pile, de liste et d'outil ; sans objet affiché, clôturée sans livraison ; une requête fille va à la hutte ; hors créatif, inchangé (citoyen plein compris) ;
  - `Deliverable.displayed` : les trois genres, l'outil du niveau le plus bas, un outil hors niveau écarté, aucun outil ;
  - « Fournir » proposé en créatif sans l'objet, dans la fenêtre du citoyen et le presse-papiers ;
  - remise à zéro : plus aucune requête ni index, résolveurs joueur et retrying neufs et seuls servis, huttes ré-enregistrées, files d'artisan et de livreur vidées, file d'entrepôt gardée, colonie marquée, remise à zéro demandée pendant une affectation ;
  - API : droits et refus des deux actions, `NotFound`, `Unavailable` ; config par défaut `false`.
- Cœur de HyLens : l'onglet, la requête choisie et son oubli, les lignes (états ouverts seulement), `ApiCompatibility` 1.3.
- En jeu (`docs/TESTING.md`) : l'onglet, « Fournir » en créatif et en survie, la remise à zéro et le retour des requêtes, la fenêtre du citoyen en créatif.

## 8. Écarts à MineColonies

Chacun porte un `Deviation from MC:` dans le code.

- Pas de message « redémarré en 1.618 secondes » (une durée fictive écrite en dur par MC) : `Done`.
- Pas d'événement d'API pour la remise à zéro (MC n'en a pas) : un addon qui suit les requêtes par événements ne la voit pas ; HyLens relit tout.
- `fulfilRequest` d'un plugin fournit gratuitement : un plugin n'a pas d'inventaire (la console de MC ne peut pas fournir du tout).
- `fulfilRequest` répond un `ActionResult` sans le texte de MC, et accepte toute requête d'objets ouverte de la colonie, là où la fenêtre de MC ne montre que celles d'un citoyen et de sa hutte.
- La remise à zéro n'est pas annoncée aux opérateurs (MC le fait) : HyLens affiche son propre compte rendu.
- Pas de remise à zéro de toutes les colonies (`requestsystem-reset-full`) : HyLens agit colonie par colonie.
- Les résolveurs des huttes sont ré-enregistrés tels quels (ils n'ont pas d'état), là où MC en crée de nouveaux.
- L'outil affiché est celui du niveau le plus bas : Hytale n'a pas l'ordre des onglets créatifs que suit MC.
- Les objets d'une requête sans citoyen (requête fille, de hutte) vont aux conteneurs de la hutte ; MC les donne au citoyen de la fenêtre.
- Hors créatif, le joueur donne toutes les piles de l'objet accepté, quelle que soit leur usure ; MC ne prend que les piles identiques à la première (le port d'inventaire compte les objets sans leur usure). Pour la même raison, la quantité de clôture compte un outil cassé que le joueur garde.
