# Checklist de test en jeu — SP0, SP1+2

Serveur de dev : `./gradlew :plugin:runServer`. Il faut deux comptes : A (propriétaire) et B (étranger).
Pour obtenir les objets : `/give HyColony_TownHall`, `/give HyColony_Hut_Builder`, `/give HyColony_Hut_Residence`.

1. **Fondation.** A pose l'hôtel de ville, la fenêtre « Fonder une colonie » s'ouvre, A saisit le nom « Test » puis clique sur Fonder.
   Attendu : un message de fondation. Un double-clic ne crée qu'une colonie (vérifier avec `/hycolony info`).
2. **Annulation.** A pose un hôtel de ville dans un autre monde, puis clique sur Annuler.
   Attendu : le bloc disparaît et l'objet tombe au sol.
3. **Citoyens.** Dans les 2 minutes, 4 citoyens nommés apparaissent un par un et errent autour de l'hôtel de ville.
   S'éloigner loin pour décharger la zone, puis revenir : toujours 4 citoyens, aucun doublon.
4. **Redémarrage.** Arrêter le serveur puis le relancer.
   Attendu : la colonie et les 4 citoyens sont là, avec les mêmes noms et sans doublon. Les logs affichent `(1 colonies loaded)`.
5. **Protection.** B essaie de casser ou de poser un bloc dans la colonie, et d'ouvrir un coffre : refusé, avec un message.
   A lance `/hycolony rank B officer` : B peut alors le faire.
6. **Fenêtre.** Un clic droit sur l'hôtel de ville affiche le nom, le propriétaire, le jour et les citoyens. A renomme la colonie : le nouveau nom s'affiche.
7. **Jour/nuit.** Lancer `/hycolony info` avant et après une aube : le jour augmente de 1.
   Si l'aube réelle ne correspond pas, ajuster `HytaleGameClock.DAY_START_HOUR` et `NIGHT_START_HOUR`.
8. **Selftest.** En opérateur, `/hycolony selftest`, debout avec de l'air au-dessus de la tête : toutes les lignes sont `[OK]` (ids, stockage, `blueprint`, `place`, `container`, `break`, `spawn`, `move`).
9. **Fichiers.** `<sauvegarde du monde>/hycolony/colony-1.json` existe et contient `"schemaVersion":2` (une sauvegarde SP0 en version 1 est migrée au chargement).
10. **Échap.** A pose un hôtel de ville hors colonie, puis ferme la fenêtre « Fonder une colonie » avec Échap.
    Attendu : le bloc disparaît, l'objet tombe au sol, et le serveur ne plante pas.
11. **Commandes réservées.** B, non opérateur, lance `/hycolony delete 1` puis `/hycolony selftest` : les deux sont refusées.
12. **Citoyens orphelins.** Placer des citoyens dans des chunks, s'en éloigner pour les décharger, supprimer la colonie avec `/hycolony delete`, puis redémarrer et revenir.
    Attendu : aucun plantage, et les PNJ orphelins sont retirés.

## SP1+2 : requêtes et construction

Avant de commencer : un hôtel de ville posé **avant** cette version n'a pas de conteneur. Casser et reposer l'hôtel de ville, ou fonder une nouvelle colonie.

13. **Hutte du constructeur.** A pose `HyColony_Hut_Builder` dans sa colonie. B (étranger) ne peut pas en poser.
    Clic droit : la fenêtre « Bâtiment » s'ouvre (niveau 0 / 5, « Pas encore construit »), sans plantage du client.
14. **Embauche.** Dans les 30 secondes (tick lent), un citoyen apparaît dans « Travailleurs ». Renvoyer puis Embaucher fonctionne ; le bouton du mode d'embauche fait défiler les modes.
15. **Commande.** Bouton « Construire » sur la hutte du constructeur elle-même.
    Attendu : un message de création, la ligne d'ordre (Construire au niveau 1) et le bouton « Annuler l'ordre ». La fenêtre « Ordres » (depuis l'hôtel de ville) liste l'ordre.
16. **Chantier.** Le constructeur marche jusqu'à la hutte, dégage le terrain (haut en bas), puis pose la structure (bas en haut) et la décoration. Il tient un outil ou un bloc, les animations se jouent (sinon, le noter : repli prévu).
    Les blocs qu'il casse vont dans son inventaire, puis dans la hutte (« Stockage »).
17. **Matériaux.** La fenêtre « Ressources » liste les objets avec les couleurs rouge / orange / vert / gris. « Ajouter » déplace les objets de l'inventaire de A vers la hutte et le constructeur reprend.
    La fenêtre « Requêtes » (hôtel de ville) liste ses demandes ; « Fournir » les satisfait.
18. **Outils.** Sans pioche, le constructeur demande une pioche dès qu'il doit casser de la roche. En fournir une : il reprend. Une pioche finit par se casser (environ 150 blocs pour une rudimentaire).
19. **Fin de chantier.** Le bâtiment correspond au prefab Outlander niveau 1 autour de la hutte, tourné selon la pose de la hutte (tester les 4 orientations).
    Attendu : niveau 1, message de fin, le territoire s'agrandit (`/hycolony info`), les coffres du plan servent de stockage.
20. **Redémarrage en plein chantier.** Arrêter le serveur pendant la structure, relancer.
    Attendu : le chantier reprend là où il en était, sans reposer les blocs déjà posés ni redemander les matériaux déjà livrés.
21. **Bloc cassé.** Pendant le chantier, A casse un bloc déjà posé : il est reposé avant la fin.
22. **Amélioration puis démolition.** « Améliorer » vers le niveau 2 (plan plus grand), puis « Déconstruire » : les blocs sont retirés, le niveau est conservé et la fenêtre affiche « Déconstruit ». « Ramasser » rend la hutte (inventaire plein : refus avec un message, la hutte reste).
23. **Résidence et style.** Poser une résidence, choisir le style `kweebec` puis « Construire » : la maison Kweebec est construite.
24. **Stockage.** « Stockage » ouvre le coffre de la hutte ; le fermer puis le rouvrir fonctionne. Y déposer un objet demandé débloque la requête.
25. **Permissions.** B, sans rang, ne peut ni ouvrir une hutte de la colonie ni utiliser les boutons.
26. **Ordre gratuit.** A, opérateur (`/op self`) en mode Créatif, commande « Construire » sur une résidence : le constructeur bâtit sans matériaux ni requêtes (fenêtre « Ressources » vide) ; il casse et range toujours les blocs et demande une pioche si besoin. Désactivable avec `CreativeOperatorFreeBuilds: false` ; `BuilderInfiniteResources: true` rend tout ordre gratuit.

27. **Fenêtre citoyen.** A utilise (touche d'interaction) un citoyen de sa colonie : la fenêtre « Citoyen » s'ouvre avec son nom, son métier (« Constructeur » ou « aucun »), son lieu de travail, son activité (« Au travail », « Se promène », « Inactif » ou « Absent »), ses 11 compétences avec leur niveau, son inventaire (icône, nom traduit, quantité) et ses requêtes ouvertes. Aucune interaction PNJ vanilla ne démarre.
    B, sans rang, fait de même : pas de fenêtre, le message « Vous n'avez pas le droit… » s'affiche. Un ami (FRIEND) peut l'ouvrir.
28. **Fournir depuis le citoyen.** Pendant que le constructeur attend un objet ou un outil, sa fenêtre affiche « En attente de : 64 x Pierre » ou « Pelle (niveau 0 à 0) », et la requête avec le bouton « Fournir » quand A a l'objet. « Fournir » le donne au constructeur, la fenêtre se rafraîchit sans la requête et il reprend le travail.
29. **Annonce dans le chat.** Quand une requête ne peut plus être servie que par un joueur (après les essais du résolveur « retrying », environ 3 minutes), le propriétaire et les officiers en ligne reçoivent une seule ligne : « Jean (Constructeur) a besoin de : Pelle (niveau 0 à 0) » (en anglais : « Jean (Builder) needs: … »). Les noms d'objets sont traduits (vérifier que le paramètre imbriqué ne s'affiche pas en `{…}`). Un ami (FRIEND) ne la reçoit pas.
    Attendu : pas de nouvelle ligne pour la même requête ensuite, même quand elle repasse par « retrying » puis revient au joueur.
30. **Outil déposé dans la hutte.** Le constructeur attend une pelle (requête chez le joueur). A pose une pelle dans le coffre de la hutte par un autre moyen que le bouton « Stockage » (ou via « Stockage » sans refermer) : dans les secondes qui suivent, le constructeur la prend, la requête disparaît de « Requêtes » et il reprend le chantier. Une pelle d'un niveau supérieur à celui de la hutte n'est pas prise.
31. **Objets déposés dans la hutte.** Même chose avec une pile de blocs attendue : dès que la hutte contient la quantité complète, le constructeur la prend et reprend ; aucune requête ne reste ouverte.
32. **Marqueur « ! ».** Quand une requête d'un citoyen n'est plus servie que par un joueur (la même que l'annonce du point 29), son nom au-dessus de la tête devient « ! Brina O. Underhill » en une seconde environ. Après « Fournir » (ou l'annulation de l'ordre), le nom redevient normal. Redémarrer le serveur pendant l'attente : le « ! » est toujours là après le chargement ; il l'est aussi sur un corps réapparu.
33. **Le constructeur regarde son travail.** Pendant un chantier, le constructeur se tourne vers chaque bloc avant de le poser ou de le casser (corps et tête), au lieu de travailler dans son dos. Noter si la tête revient droite aussitôt (inclinaison) ou si le corps se retourne pendant l'animation.
34. **Position de travail.** Il se tient à 2 à 4 blocs du bloc, de préférence du côté extérieur du bâtiment, jamais dans un bloc prévu par le plan ; il se déplace dès que le bloc suivant est à plus de 5 blocs (|dx| + |dz|) de lui, et non plus tous les 10 blocs.
35. **Constructeur bloqué.** Reproduire le cas du terrain : un chantier dont un mur ou le toit est au-dessus du sol (la cible de marche était en l'air). Attendu : s'il ne progresse plus pendant 5 s, il relance sa marche, puis 10 s plus tard il est téléporté près de sa position de travail, et reprend. Il ne reste jamais planté indéfiniment, un bloc à la main.
36. **Activité dans la fenêtre citoyen.** La fenêtre du constructeur affiche ce que fait son IA : « structure : bloc 102 - se déplace », « … - pose Wood_Deadwood_Roof », « … - casse », « Va chercher des matériaux à la hutte », « Attend des objets », etc. Le nom de l'étape est traduit (pas de `{…}`).
