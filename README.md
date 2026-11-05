# M-Motors - Examen Bloc 3

Application pédagogique réalisée pour Adam Ghajdaoui. React + Java 21 / Spring Boot 3.5, PostgreSQL en cloud et H2 persistant en local. Données fictives exclusivement.

## Lancer la démo locale

Sous Windows, depuis ce dossier :

```powershell
.\start-local.ps1
```

Prérequis sur une autre machine : Java 21, Maven 3.9+, Node 22.12+ (Node 24 LTS convient). Le script détecte aussi les outils portables préparés dans `../.tools` sur cette machine. Il compile React, exécute les tests et démarre l'application sur http://localhost:8080. Si le JAR fourni est déjà compilé : `./start-local.ps1 -SkipBuild`.

| Usage local de démonstration | E-mail | Mot de passe fictif |
|---|---|---|
| Administrateur | admin@mmotors.example | DemoAdmin2026! |
| Client | client@mmotors.example | DemoClient2026! |

Ces mots de passe sont définis seulement par le script de démo. Le serveur n'active pas les comptes sans `DEMO_MODE=true`. En cloud, choisir les valeurs via les variables de l'hébergeur et les transmettre au correcteur. Changer les variables ne réinitialise pas les comptes déjà présents : pour modifier un mot de passe existant, prévoir une migration ou une procédure dédiée.

## Commandes de développement

```sh
cd frontend
npm ci --ignore-scripts
npm run build
cd ../backend
mvn clean verify
java -jar target/m-motors-1.0.0.jar
```

Le front est compilé dans les ressources statiques du backend : un seul domaine et un seul déploiement. Pour le rechargement pendant le développement : lancer le backend, puis `npm run dev` côté frontend (proxy `/api`).

Rapport de couverture : `backend/target/site/jacoco/index.html`. Résultats JUnit : `backend/target/surefire-reports/`.

```sh
cd frontend
npx cypress install
npm run e2e
```

Cypress nécessite l'application démarrée sur 8080 et les comptes de démo ci-dessus. Il crée un utilisateur fictif à chaque exécution. Les tests de base supposent les six véhicules initiaux disponibles. Les tests backend reviennent à leur état initial par transaction ; les tests navigateur conservent leurs données fictives.

## Fonctionnalités

- Recherche par achat/location, marque/modèle/motorisation et budget ; fiche détaillée.
- Inscription et connexion par session avec CSRF ; rôle CLIENT imposé à l'inscription.
- Choix des options de location, calcul mensuel et photographie du prix à la création.
- Dossier avec deux catégories de pièces, contrôle de taille, signature et structure (PDFBox/ImageIO).
- Soumission après complétude, suivi du statut et téléchargement privé des pièces.
- Administration : ajout de véhicule, bascule achat/location sans dossier actif, validation/refus motivé.
- Réservation atomique lors de la validation : un véhicule ne peut être attribué deux fois.
- Migration de schéma Flyway, logs d'événements sans contenu des pièces ni mots de passe et endpoint de santé.

## Déploiement

Voir [docs/DEPLOIEMENT.md](docs/DEPLOIEMENT.md). `Dockerfile`, `compose.yaml`, `render.yaml` et `.github/workflows/ci.yml` sont fournis. La base PostgreSQL n'expose aucun port public dans Compose. `docker compose --env-file .local.env up --build` nécessite trois variables : DATABASE_PASSWORD, ADMIN_PASSWORD et USER_PASSWORD, toutes à définir localement. Ce fichier est ignoré par Git.

Le dépôt est un monorepo : le lien `frontend/` et le lien `backend/` remplacent deux dépôts séparés dans le tableau du dossier. Ne pas confondre préparation de déploiement et preuve d'une application publique en fonctionnement : l'état vérifié est décrit dans le dossier.

## Limites du périmètre pédagogique

Pas de paiement, signature électronique, calcul de crédit réglementaire, mails transactionnels ou connexion réelle au financement. Durée, apport et valeur de rachat LOA sont à contractualiser avec un conseiller. Les tarifs des services sont des hypothèses de démo.

La sauvegarde des pièces est cohérente avec la BDD puisque les fichiers y sont stockés. Pour l'échelle de l'entreprise du Bloc 1, prévoir stockage objet privé, antivirus, politique de rétention, pagination, limitation de débit au proxy, supervision avec alertes et sessions partagées avant réplication horizontale. Les journaux applicatifs ne constituent pas à eux seuls un audit de sécurité complet. Les RPO/RTO sont des objectifs à valider et mesurer avec le PO.
