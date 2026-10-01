# Fenêtres du citoyen, des requêtes et du champ comme MineColonies : plan

> Exécution : en ligne (superpowers:executing-plans), comme le sous-projet des huttes ; relectures par lot (CLAUDE.md § 9.3) puis relecture finale.

**But :** porter l'apparence et le contenu de MC sur la fenêtre du citoyen, les requêtes (arbre, détail, presse-papiers) et la fenêtre du champ.

**Spec :** `docs/superpowers/specs/2026-10-01-hycolony-citizen-requests-field-windows-mc-design.md`.

## Contraintes globales

- Positions et tailles des `.xml` de MC ×2 ; textures copiées de `sources/` ×4 au plus proche voisin en `@2x` sous `Pages/HyColony/Mc/` (`python $SP/copy_mc_tex.py <chemins sous textures/gui>`).
- Styles de boutons de `Mc/Book.ui` (texte noir, même texture au survol, comme BlockUI).
- Toute règle dans le cœur, en TDD ; le plugin dessine les vues et appelle les actions.
- Fichiers ≤ 300 lignes visés, paquets ≤ 15 fichiers : `app/view` et `app/ui` en ont 13, `app/action` 14 ; les nouveaux types vont dans un sous-paquet (`app/citizen`, `app/request`) quand un paquet serait plein.
- Textes en en-US et fr-FR, clés MC reprises (`gui.citizen.*`, `gui.requests.*`, `item.clipboard.*`, `gui.field.*`).
- Modifications avec Edit/Write seulement ; `git add` par chemins ; build vert avant chaque commit.

## Points à surveiller (relecture)

1. Une fenêtre ouverte dont la requête, le citoyen ou le champ a disparu : boutons sans effet, retour qui ferme.
2. Les événements portent un id stable (jeton de requête, compétence, direction), jamais un index de ligne.
3. Annuler et ajuster une compétence vérifient le droit (`MANAGE_HUTS`, créatif) dans le cœur.
4. Le presse-papiers : métadonnée absente ou colonie disparue, objet dans l'autre main.
5. Le survol (+/−) : un `MouseExited` perdu ne doit pas laisser des boutons actifs hors créatif.

## Lot 1 : fenêtre du citoyen

**Cœur**

1. `SkillRows` reste (ordre « métier d'abord » et XP gardés à la demande de l'utilisateur) ; seul le rendu change.
2. `HealthBar.of(int health)` : port de `createHealthBar` (10 emplacements, chacun un cœur de fond et un demi-cœur posé dessus, ordre bleu, vert, doré, rouge, seuils de `WindowConstants`), avec tests sur 0, 5, 20, 21, 40 (au-delà de 20, inatteignable avec la mise à l'échelle).
3. `SaturationBar.of(double saturation)` : `MAX_SATURATION / 6` emplacements vides, pleins par tranche de 6, un demi si reste ; tests 0, 3, 60, 33.
4. Port `CitizenBodies.healthPercent(BodyId)` → `int` (0 sans corps vivant ; un `int` pour ne pas alourdir le couplage de `DetouringBodies`) ; `Fake` ; la vue ramène à l'échelle de MC en tronquant comme MC (`percent × 20 / 100`), 20 sans corps.
5. Port `PlayerDirectory.isCreative(UUID)` (faux hors ligne) ; action `CitizenActions.adjustSkill(player, colonyId, citizenId, skill, ±1)` : refus hors créatif (MC `AdjustSkillCitizenMessage`), niveau borné comme `CitizenSkillHandler.incrementLevel`, colonie marquée à sauver, citoyen ré-affiché.
6. `CitizenView` : nom, santé, saturation, compétences, genre, créatif, requêtes (lot 2), compétences du métier ; l'activité, l'attente, la ligne du métier et l'XP restent (demandés par l'utilisateur, registre).

**Plugin**

7. Textures : `citizen/colonist_paper`, `colonist_text_decor_down`, `colonist_decor_up_ribbon_smaller`, `colonist_wax_male_smaller`, `colonist_wax_female_smaller`, `citizen/empty`, `full`, `half`, `green_bluehearts`, `modules/info`, `requests`, `inventory`, `main`, `tab_left_side1..3`, `entity/skills/small/*` (hors `textures/gui` : adapter le script).
8. `Citizen.ui` (papier, onglets de `nav.xml`, `#Page`), `Citizen/Main.ui`, `Citizen/Job.ui`, `Citizen/Requests.ui`, `Mc/SkillLine.ui`, `Mc/Heart.ui` ; `CitizenPage` sur le modèle de `BuildingPage` (onglets dans la page, l'onglet gardé) ; le survol des icônes de compétence (`MouseEntered`/`MouseExited`) montre les +/−.
9. Cœurs rouges et dorés : dessinés à partir des cœurs de `green_bluehearts.png` (écart § 8 de la spec).
10. `docs/TESTING.md` : point par onglet.

## Lot 2 : arbre des requêtes, détail, Annuler

**Cœur**

1. `RequestRow` gagne la position du demandeur, le nom du résolveur, `cancellable` (racine) et `fulfillable` (règle de contexte) ; la description reste rendue par le plugin depuis le `Requestable`.
2. Requêtes du citoyen = celles de sa hutte de travail portées par lui, puis celles de la hutte sans citoyen (−1), chacune avec ses enfants.
3. Fournir côté citoyen : livrable, et racine ou demandeur à la place de la hutte, et le joueur a l'objet (`isFulfillable`) ; tests de chaque refus.
4. `RequestActions.cancel(player, colonyId, token)` : `MANAGE_HUTS`, racine seulement, `CANCELLED`, ré-affichage ; tests droit, enfant refusé, jeton inconnu.

**Plugin**

5. Textures `citizen/detail_button`, `colonist_button_small` ; `Mc/RequestLine.ui` (ligne de 80) ; `RequestTree` (dessin partagé par l'onglet du citoyen et le presse-papiers) ; `RequestDetailPage` (fenêtre à part, retour vers l'origine).

## Lot 3 : presse-papiers

**Cœur**

1. `ClipboardActions` : `register(player, hutPos)` → colonie d'une hutte (message `clipboard.registered`) ; `open(player, colonyId, hideUnimportant)` → `clipboard.needcolony` sans colonie, accès vérifié ; le filtre « ! » cache les requêtes asynchrones (sans citoyen) quand il est éteint ; tests.

**Plugin**

2. Objet `HyColony_Clipboard` (JSON d'objet, icône, recette), `ClipboardInteraction` (page `OpenCustomUI`, bloc visé ou non, métadonnées `colony` et `hideunimportant`) ; `Clipboard.ui` (texture `gui/clipboard`, « ! » mini) ; suppression du bouton Requests de l'hôtel de ville et de son action.

## Lot 4 : champ

**Cœur**

1. `FieldView` : le fermier comme MC (premier citoyen de la hutte qui possède le champ), le biome si le port le donne ; `FieldDirections.relative(facing, direction)` (port de `getDirectionalTranslationKey`), testé.

**Plugin**

2. Textures `builderhut/builder_paper_short`, `scarecrow`, sketch ; `Field.ui` réécrit ; `SelectRes.ui` (choix de graine sans papier, tri : porté par le joueur d'abord, puis nom ou Levenshtein) ; infobulles des directions.

## Fin

Relecture finale du sous-projet (modèle le plus capable), une passe de corrections relue, `docs/TESTING.md` complet, feu vert à l'utilisateur.
