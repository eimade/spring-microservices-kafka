# Uimad Ref : données de référence en microservices (Spring Boot 3, Kafka, MongoDB)

![CI](https://github.com/eimade/spring-microservices-kafka/actions/workflows/ci.yml/badge.svg)

## Problématique

Un éditeur SaaS doit gérer des **données de référence** (produits, fournisseurs) partagées entre plusieurs services : saisie contrôlée, **validation avant diffusion**, **historique des versions** et **propagation des changements en temps réel**. Uimad Ref en est un MVP complet de bout en bout.

## Fonctionnalités

- **Service `referentiel`** (Spring Boot 3, Java 21) : CRUD de références (PRODUIT, FOURNISSEUR), workflow brouillon -> validé, historique des versions, authentification **JWT** et rôles (`EDITOR` crée/modifie/supprime, `VALIDATOR` valide).
- Une modification d'une référence validée la repasse en brouillon et incrémente sa version.
- À la validation (ou suppression d'une référence déjà diffusée), un événement est publié sur **Kafka** (topic `reference-changes`) **après commit** de la transaction.
- **Service `consumer`** : consomme le topic et maintient une **vue lecture MongoDB** (idempotente, ignore les versions périmées), exposée par une API sécurisée par le même JWT.
- **PostgreSQL** avec **Flyway** (un jeu de migrations par SGBD, profil Oracle optionnel).
- **Front minimal en Lit / Web Components** : connexion, liste, création, validation, historique, bascule référentiel / vue lecture.
- Docker Compose, manifestes Kubernetes, CI GitHub Actions.

## Architecture

```
 Front Lit (servi par referentiel, :18300)
        |  JWT                         JWT
        v                               v
 +------------------+   Kafka    +--------------------+
 |   referentiel    | ---------> |      consumer      |
 | controller       |  topic     | KafkaListener      |
 |  -> service      | reference- |  -> handler        |
 |  -> repository   |  changes   |  -> repository     |
 | Spring Security  |            | Spring Security    |
 +--------+---------+            +---------+----------+
          |                                |
     PostgreSQL (Flyway)             MongoDB (vue lecture)
```

## Lancement en 1 commande

```bash
docker compose up --build
```

| Élément | Adresse |
|---|---|
| Front + API referentiel | http://localhost:18300 |
| API lecture (consumer) | http://localhost:18301 |
| Kafka (hôte) | localhost:19092 |
| MongoDB | localhost:27018 |
| PostgreSQL | localhost:15435 |

Comptes de démonstration : `editeur` / `editeur123` (EDITOR) et `validateur` / `validateur123` (VALIDATOR). Des données de démo sont chargées au premier démarrage (variable `SEED_ENABLED`).

Sans conteneurs applicatifs : `docker compose up -d postgres mongo kafka`, puis `./mvnw package -DskipTests` et `java -jar referentiel/target/referentiel-1.0.0.jar` / `java -jar consumer/target/consumer-1.0.0.jar`.

## Exemples d'appels API

```bash
TOKEN=$(curl -s -X POST localhost:18300/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"validateur","password":"validateur123"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')

curl -H "Authorization: Bearer $TOKEN" localhost:18300/api/references
curl -X POST -H "Authorization: Bearer $TOKEN" localhost:18300/api/references/4/validate
curl -H "Authorization: Bearer $TOKEN" localhost:18300/api/references/1/history
curl -H "Authorization: Bearer $TOKEN" localhost:18301/api/read/references/PRODUIT/PRD-003
```

Résultat observé après validation de PRD-003 : la référence apparaît dans la vue MongoDB quelques millisecondes plus tard (`"status":"VALIDATED","validatedBy":"validateur"`).

Codes d'erreur : 400 (validation, détail par champ), 401 (jeton absent ou identifiants invalides), 403 (rôle insuffisant), 404, 409 (code déjà utilisé, référence déjà validée).

## Tests

```bash
./mvnw verify
```

- Unitaires JUnit 5 / Mockito : règles du workflow (entité), service, gestionnaire d'événements.
- Intégration **Testcontainers** : `referentiel` (PostgreSQL + Kafka : sécurité, workflow, historique, événement réellement publié) et `consumer` (MongoDB + Kafka : message JSON -> vue -> API sécurisée). Docker est requis.
- 23 tests au total (16 referentiel, 7 consumer).

## Simulations

Ce projet est volontairement minimaliste. Ce qui est simulé ou non exécuté :

- **Oracle** : profil **optionnel**. Les migrations `db/migration/oracle`, le profil Spring `oracle`, le profil Maven `-Poracle` (pilote + Flyway Oracle) et le service Compose `oracle` (`docker compose --profile oracle up oracle`, port 11521) sont fournis, mais la chaîne Oracle n'est **ni testée ni exécutée en CI** ; PostgreSQL est la base de référence.
- **Kubernetes** : les manifestes `k8s/` sont **validés en dry-run** (CI : `kubectl apply --dry-run=server` sur un cluster kind éphémère) et n'ont pas été déployés sur un cluster réel. Les bases et Kafka y sont des déploiements de développement sans persistance. Le `Secret` contient des valeurs de démonstration.
- **Identité** : les utilisateurs sont des comptes de démonstration en mémoire ; en production, un fournisseur d'identité (OIDC) remplacerait `AuthController`.

## Choix techniques

- Publication Kafka via `@TransactionalEventListener(AFTER_COMMIT)` : jamais d'événement pour une transaction annulée. Une panne Kafka juste après le commit pourrait perdre un événement ; l'étape suivante serait un pattern *outbox* (non implémenté).
- Consommateur idempotent avec contrôle de version : rejeu et désordre sans effet de bord.
- Secret JWT partagé HS256 pour rester simple ; clé publique asymétrique (RS256/JWKS) en production.
- Flyway avec `{vendor}` : un dossier de migrations par SGBD.
- Front sans étape de build (Lit chargé depuis un CDN), ce qui impose une connexion internet pour l'afficher.

## Limites connues

Pas de pagination, pas de recherche, pas d'outbox, front minimal, Oracle et Kubernetes non déployés réellement.
