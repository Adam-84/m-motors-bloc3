# Textes de remplacement pour la copie à rendre

Réponses corrigées après le retour de l'évaluateur sur le dépôt 1. À coller dans le document Word, à la place des réponses actuelles. Tout ce qui est écrit ici est vérifiable dans le dépôt.

## Organisation Git (C3.1)

Le dépôt est un monorepo public : https://github.com/Adam-84/m-motors-bloc3 (dossiers `frontend` et `backend`).

Le principe retenu est un trunk-based simplifié : `main` reste stable et déployable, et chaque évolution passe par une branche courte (`feat/`, `fix/`, `refactor/`, `test/`, `docs/`), des commits en Conventional Commits, une pull request avec le modèle du dépôt, puis une fusion après CI verte. Les règles sont dans `CONTRIBUTING.md`.

**Point à reconnaître.** Le premier incrément, le parcours complet US-01 à US-10, a été livré en un seul commit sur `feat/mvp`. L'historique ne montre donc ni une branche par story ni des tests écrits avant le code pour ces dix stories, et je ne le reconstruis pas après coup. Depuis le retour de l'évaluateur, la méthode est appliquée pour de bon : chaque amélioration a sa branche, ses commits, sa pull request et sa CI. La protection de `main` est activée sur GitHub depuis le 11 novembre 2026 (pull request obligatoire, contrôle `build-test` requis). La story US-11 (annulation d'un brouillon) en est la démonstration : branche `feat/US-11-annulation-brouillon`, commit rouge `206ca6b`, commit vert `a9cec34`, pull request 20.

## Démarche d'investigation sur un bug de code (C3.3)

Bug retenu : le contrôle des pièces justificatives acceptait un faux PDF.

1. **Reproduire.** J'envoie en pièce d'identité un fichier de cinq octets, `%PDF-`. L'ancienne version répond par un succès, alors que le résultat attendu est un refus.
2. **Isoler la couche.** L'interface et le contrôleur ne font que transmettre les octets, et la base les stocke telles quelles. Le défaut est dans la règle métier `BusinessRules.fileType`.
3. **Écrire le plus petit test.** Un test unitaire sans HTTP passe un tableau d'octets tronqué à `fileType` et attend un refus. C'est `rejectsTruncatedPdfAndImagesEvenWithCorrectSignature`, qui couvre aussi les en-têtes JPEG et PNG tronqués.
4. **Corriger la cause.** La vérification s'arrêtait à la signature, qui prouve le début du fichier et non sa lisibilité. Le fichier est maintenant réellement lu : PDFBox pour les PDF (non chiffrés, 1 à 100 pages) et ImageIO pour les images (20 mégapixels maximum).
5. **Valider.** Le test passe, puis la suite complète (32 tests) et la CI GitHub Actions. Le test reste dans la suite comme test de non-régression.

D'autres défauts de code ont été trouvés de la même façon :
- le schéma échouait sur H2 parce que `year` est un mot réservé, d'où la colonne `model_year` ;
- l'historique des dossiers disparaissait au-delà de 200 à cause d'un `LIMIT 200`, d'où le retrait de la limite et un test avec 201 dossiers.

Limite assumée : l'historique Git ne prouve pas l'ordre test puis correctif pour ces trois bugs, corrigés pendant la réalisation initiale. Pour tout nouveau bug, je procède en deux commits sur une branche `fix/`, le test qui échoue puis la correction. C'est fait pour un bug de recherche : la recherche publique « diesel » (avec une espace à la fin) ne renvoyait rien, car le terme gardait ses espaces de bord alors que la requête compare marque, modèle et carburant. Le commit rouge `59e48a3` ajoute deux tests qui échouent ; le commit vert `2bbfedc` ajoute un `trim()` ; l'ensemble est sur la branche `fix/recherche-terme-avec-espaces`, fusionnée par la pull request 17 avec un commit de fusion pour garder l'ordre visible. La suite compte alors 56 tests, tous verts. Ce bug a été trouvé en lisant le code, puis confirmé par les tests.

## Qualité du code

L'évaluateur a relevé un code compact et peu lisible. Les corrections sont faites par branches : `BusinessRules` et `MotorsService` ont d'abord été réécrits (une instruction par ligne, constantes nommées pour les statuts, méthodes extraites). Une branche `test/` (PR 11) a porté la couverture des branches de 74,42 % à 96,12 % avec 54 tests. Deux branches `refactor/` ont ensuite reformaté `ApiController`, `ApiErrors`, `SecurityConfig`, `DemoData` et `Models`, puis découpé `MotorsService` en `AccountService`, `VehicleService` et `DossierService`. Aucune ligne de plus de 120 caractères ne reste dans le code principal ; les tests restent verts (54, puis 56 avec le bug de recherche, puis 59 avec US-11) et la couverture des branches est de 96,06 % (122 sur 127). Limite honnête : les résultats des services sont encore des `Map` et non des objets typés.
