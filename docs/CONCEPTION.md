# M-Motors - Bloc 3

## Objet et continuité

Réaliser les parcours du sujet : catalogue achat/location, fiche véhicule, inscription, dossier dématérialisé, suivi client, gestion catalogue et décision administrateur. Le dossier B1 propose React, une API REST et une base cloud ; le B2 retient Scrum, Trello et Cypress. Le backend Java/Spring Boot est le choix retenu pour ce Bloc 3 après la proposition dans le chat. L'énoncé n'impose aucun langage.

## Périmètre de la réalisation

Deux dossiers dans un monorepo public à publier : `frontend` et `backend`. React/Vite côté interface ; Java 21, Spring Boot, Spring Security, JDBC, Flyway côté serveur. PostgreSQL en hébergement, H2 persistant en local et en mémoire pour les tests. Un seul conteneur sert le front et l'API : pas de CORS nécessaire. Une session HttpOnly et un jeton CSRF protègent les actions. Les dossiers et pièces sont accessibles au propriétaire ou à un administrateur. Les fichiers sont privés, limités à 5 Mo, contrôlés par signature et servis en téléchargement.

Les options LOA sont sélectionnables, avec un prix mensuel explicite de démonstration ; les valeurs commerciales sont des hypothèses. Une décision favorable réserve atomiquement le véhicule. Aucun paiement ni raccordement réel à un organisme de financement n'est simulé comme étant opérationnel. La signature du contrat reste hors périmètre.

## Règles vérifiables

- Deux types de pièces sont nécessaires avant soumission : identité et domicile.
- Achat : aucune option LOA ; location : options appartenant à la liste autorisée.
- Un dossier soumis ne peut plus être modifié par le client.
- Seul un dossier soumis peut être approuvé/refusé ; un refus doit être motivé.
- Le prix du véhicule et des options est figé à la création du dossier.
- Un véhicule réservé ne peut plus recevoir de nouveau dossier.
- La bascule vente/location est refusée tant qu'un dossier actif existe.
- Le rôle client est imposé par le serveur à l'inscription.

## Preuves et limites

JUnit et tests HTTP avec base réelle en mémoire ; couverture JaCoCo générée par Maven. Cypress conserve la continuité B2 pour le parcours navigateur. La CI construit le front, teste le backend et publie les rapports comme artefacts. Les résultats réellement exécutés et les étapes de publication restantes figurent dans le dossier ; aucune URL ni mesure n'est inventée.

La démo utilise exclusivement des données fictives. Stockage des pièces en BDD pour garder une sauvegarde cohérente dans ce périmètre pédagogique ; pour l'entreprise du B1, extraire vers un stockage objet privé avec analyse antivirus et règles de rétention. Les objectifs RPO/RTO restent à valider avec le PO, pas des garanties de la démo.
