# GPT Compare

## Description

GPT Compare est une application web full‑stack permettant de comparer les réponses de différents modèles GPT sur un même prompt, avec des paramètres configurables (modèle, température, nombre de tokens, etc.).

Le projet est composé de :

* un **backend** en Java (Spring Boot WebFlux) qui centralise les appels à l’API OpenAI ;
* un **frontend** en Angular qui fournit l’interface utilisateur.

L’application peut être lancée localement soit en mode développement classique, soit **via Docker**, sans installer Java, Node.js ou Angular sur la machine.

---

## Architecture

```
.
├── gptcompare-backend    # Backend Spring Boot (WebFlux)
├── gptcompare-frontend   # Frontend Angular
├── docker-compose.yml    # Orchestration Docker
└── README.md
```

---

## Prérequis

### Option 1 – Avec Docker (recommandé)

* Docker Desktop (Windows / macOS / Linux)
* Docker Compose (inclus avec Docker Desktop)

### Option 2 – Sans Docker

* Java 21+
* Maven 3.9+
* Node.js 20+
* npm
* Angular CLI

---

## Configuration

### Variable d’environnement

Le backend nécessite une clé OpenAI.

Créer un fichier `.env` à la racine du projet :

```env
OPENAI_API_KEY=sk-xxxxxxxxxxxxxxxxxxxx
```

Ce fichier n’est pas versionné et ne doit pas être commité.

---

## Démarrage avec Docker

C’est le moyen le plus simple pour lancer l’application.

Depuis la racine du projet :

```bash
docker compose up --build
```

Une fois le build terminé :

* Frontend : [http://localhost:4200](http://localhost:4200)
* Backend : [http://localhost:8080](http://localhost:8080)

Pour arrêter l’application :

```bash
docker compose down
```

---

## Démarrage sans Docker (mode développement)

### Backend

```bash
cd gptcompare-backend
mvn spring-boot:run
```

Le backend démarre sur : [http://localhost:8080](http://localhost:8080)

### Frontend

```bash
cd gptcompare-frontend
npm install
npm start
```

Le frontend démarre sur : [http://localhost:4200](http://localhost:4200)

---

## Vérification rapide

Une fois l’application démarrée :

* Ouvrir [http://localhost:4200](http://localhost:4200)
* Saisir un prompt simple
* Lancer la comparaison

Pour tester le backend directement :

```bash
curl http://localhost:8080/api/chat/ping
```

---

## Notes techniques

* Le frontend est servi par Nginx en production Docker.
* Les appels API passent par `/api/*` et sont proxyfiés vers le backend.
* La clé OpenAI n’est jamais exposée côté navigateur.