# Spec : l'établi de l'apothicaire-herboriste, asset de test maître de blockpaint

Date : 2026-10-04. Demandé par l'utilisateur le 2026-10-04, puis agrandi d'après son image de référence (« un vrai
truc massif, pas forcément 1 de large », trois variantes NEUF / UTILISÉ / NÉGLIGÉ). Outil seulement : un modèle de
démonstration de `tools/blockpaint/`, **hors du jeu** (ni pack ni objet : il valide le pipeline et sert de vitrine),
peint par le catalogue comme une hutte, dans ses trois états. Il s'appuie sur les specs 2026-10-03 (surfaces,
décalques) et 2026-10-04 (illustration, compositeur).

## 1. Concept

L'établi d'un herboriste installé : un meuble massif de deux blocs et demi de large, plus de deux de haut (84 x 83 x 28
unités hors tabouret), adossé au mur de son officine.

De bas en haut :

- **le caisson** sur quatre pieds : à gauche **neuf tiroirs** étiquetés (trois par trois, boutons de laiton ; celui du
  milieu ouvert cent fois par jour, celui d'en bas à droite presque jamais), à droite **deux portes** cerclées de fer
  en haut et en bas, poignées de laiton ;
- **le plan de travail** en saillie, verni : le **registre** ouvert (couverture de cuir, pages écrites), le **mortier**
  et son pilon, la **balance** (focal), un bol d'herbes, deux rouleaux, une bougie sur son bougeoir de laiton, une
  botte d'herbes ; deux chiffons (lin, drap rouge) pendent sur le bord avant ;
- **le vaisselier** posé sur le plan de travail : un **dos peint** vert sauge (le grand fond calme), deux flancs, deux
  **étagères** de fioles bouchées, de pots à étiquette, une boîte de laiton, des livres couchés et debout, un
  **alambic** de cuivre ;
- **le fronton** : deux plantes en pot de terre cuite ; dessous, trois bottes d'herbes qui pendent ;
- **autour** : une sacoche de cuir pendue au flanc droit, un tabouret devant la gauche.

Le fond (dos, flancs, caisson) reste simple ; les objets se groupent en trois îlots (plan de travail, étagère basse,
étagère haute) séparés par des surfaces calmes.

## 2. Matériaux

| Pièces | Matière | Pinceau, revêtements |
|---|---|---|
| tout le bâti (pieds, caisson, tiroirs, portes, vaisselier), tabouret | noyer | `timber("walnut", "planed")`, teinte |
| plan de travail | chêne | `timber("oak", "sanded")`, teinte, vernis |
| dos du vaisselier | pin peint | `timber("pine", "rough_sawn")`, apprêt, peinture vert sauge |
| cercles des portes, balance, chaînettes, crochet | fer | `metal("iron", "forged")`, noircissement |
| boutons, poignées, plateaux, pilon, boîte, bougeoir | laiton | `metal("brass", "polished")` |
| alambic | cuivre | `metals.copper` |
| fioles | verre teinté (vert, ambre, rouge, bleu, violet) | `misc.glass` |
| pots, mortier, bol | grès émaillé | `brushes.clay`, glaçure |
| pots de fleurs | terre cuite | `brushes.terracotta` |
| rouleaux, pages du registre (dessin), étiquettes (décalques) | papier | `brushes.paper` |
| couverture du registre, livres, sacoche | cuir (rouge, vert, brun) | `organic.leather` |
| chiffons | lin, drap rouge | `textiles.linen` |
| bougie, flamme | cire, cristal | `misc.wax`, `brushes.crystal` |
| herbes, plantes | plante | `materials.leaf` |
| bouchons | liège | `brushes.wood` |

## 3. Rôles et usage

Rôles (`roles.ROLES`) : `leg`, `frame` (caisson, fronton), `drawer`, `handle`, `door`, `hinge` (cercles, crochet),
`worktop` (plan de travail, assise du tabouret), `shelf`, `wall` (vaisselier), `ledger`, `bottle`, `pot`, `rim`
(plateaux), `cloth_panel`, `strap`. Les rôles de mobilier (`frame`, `shelf`, `drawer`, `worktop`, `label`, `bottle`,
`ledger`) ont été ajoutés au moteur pour lui.

Usage : tiroir et bouton du milieu `contact(1.0)`, tiroir et bouton d'en bas à droite `contact(0.1)`.

## 4. Histoire

1. **La fiole verte a coulé** : `history.chemical("Shelf_1", …)`, une auréole sur l'étagère.
2. **Un coin du plan de travail a été réparé** : `history.repaired("Worktop", …)`.
3. **Le cercle haut de la porte gauche a saigné de la rouille** : `history.rust_from("Band_A_Top")`.
4. **Négligé seulement** : la pluie du fronton a coulé sur le dos peint (`history.water("Hutch_Back")`).

## 5. États

| État | Condition | Âge | Environnement | Histoire |
|---|---|---|---|---|
| Neuf | `PRISTINE` | `NEW` | aucun | aucune |
| Utilisé | `WORN` | `OLD` | `HUMID_INTERIOR` | § 4, 1 à 3 |
| Négligé | `NEGLECTED` | `ANCIENT` | `HUMID_INTERIOR` | § 4, 1 à 4 |

L'utilisé prend `WORN` et non `USED` : `USED`, la condition discrète des huttes, ne se distinguait presque pas du
neuf (3 % des texels changés). Les trois états doivent se distinguer au premier coup d'œil, comme sur l'image de
référence : c'est ce qu'a demandé le lot « états lisibles » du moteur (spec surfaces § 3.3) : `Condition.show`, le
plancher des zones de repos, le film terne du négligé, la poussière sous abri.

## 6. Passe d'illustration et compositeur

| Rang | Pièces |
|---|---|
| 0 (recule) | dos du vaisselier |
| 1 | tout le reste |
| 2 | registre et ses pages, fiole rouge (`Bottle_3`), alambic |
| 3 (focaux) | balance (pied, poteau, fléau, chaînettes, plateaux), fiole verte (`Bottle_1`) |

- **Budget d'accents** : un grand modèle garde sept accents ; les pièces d'un accent qui se touchent et partagent une
  famille n'en font qu'un.
- **Contraste des matières** : le bougeoir de laiton contre le plan de travail de chêne, sur un seul axe ; le petit
  objet bouge seul, le grand reste le décor (spec compositeur § 3.8).
- **Distance** : à 25 %, le rapport dit quels accents se lisent encore.

## 7. Fichiers

- `tools/showcase/apothecary_model.py` : le modèle, construit par le code (104 pièces, une boîte statique chacune).
- `tools/showcase/apothecary.py` : matières, décalques (étiquettes des tiroirs et des pots), rôles, usage, histoire,
  importance, les trois états (`STATES`, `module(state, names)`).
- `tools/showcase/generate.py` : écrit le modèle de chaque état et laisse le catalogue le peindre, dans
  `tools/showcase/out` (ignoré par git, hors de tout pack).

## 8. Validation

- Planche Blockbench : les trois états côte à côte, de face, de trois quarts et de près ; validée par l'utilisateur.
- Mesure : la part des texels qui changent par rapport au neuf, et les zones de chaque effet, par état.
- Rapports de la passe et du compositeur.
- Ce qui manque à l'œil devient une tâche du moteur, pas un réglage caché du prop.
