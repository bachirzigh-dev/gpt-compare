# 🤖 GPT Compare (Angular + Spring WebFlux)

Application full-stack permettant de comparer les réponses de modèles GPT sur une même question, avec des réglages A/B (modèle, température, max tokens…).  

Le backend appelle l’endpoint OpenAI Responses API (`/v1/responses`) et renvoie une réponse normalisée (texte + métriques tokens + latence + statut de tronquage).

---


## Fonctionnalités

### Frontend (Angular)

- Mode **simple** (A) ou **comparaison A/B**

- Réglages par modèle :
  - `model`
  - `temperature` désactivée automatiquement pour GPT-5*
  - `maxOutputTokens`

- Affichage :
  - réponse texte
  - latence (`latencyMs`)
  - tokens (`totalTokens`, etc.)
  - détection “réponse tronquée” + boutons "Relancer à 4000/8000"

- UX :
  - bouton “Réglages”
  - “Effacer”
  - raccourci "Ctrl/Cmd + Entrée"
  - gestion d’état robuste : `loading`, `error`, résultats

### Backend (Spring Boot WebFlux)
  - API REST réactive :
  - `POST /api/chat/send`
  - `GET /api/chat/ping`

- Appel OpenAI via `WebClient` (JSON)

- Garde-fous :
  - `max_output_tokens` par défaut = **800**
  - cap maximum = **8000**
  - omission de `temperature` quand `null` (ou non supportée)

- Parsing robuste de la réponse OpenAI :
  - extraction `output_text` prioritaire
  - fallback sur n’importe quel champ `text`
  - gestion des statuts `completed` / `incomplete`

- Gestion d’erreurs :
  - erreurs HTTP OpenAI (400/401/500…)
  - coupure réseau / timeout
  - réponse invalide (champ `output` manquant)

---

## Stack technique

### Frontend
  - Angular (standalone component)
  - TypeScript
  - RxJS
  - Vitest (tests + coverage)

### Backend
  - Java 21
  - Spring Boot 4 + WebFlux
  - Reactor
  - OkHttp MockWebServer (tests)
  - Reactor Test (StepVerifier)
  - JaCoCo (coverage)

---

## Structure du repo
---
gptcompare-frontend/
  src/app/
    app.ts
    app.html
    app.spec.ts
    chat-api.service.ts
    chat-api.service.spec.ts

gptcompare-backend/
  src/main/java/com/example/gptcompare_backend/
    controller/
    dto/
    service/
    GptCompareBackendApplication.java
  src/test/java/...
  src/main/resources/application.yml
  pom.xml




#Démarrage rapide

##Backend

###Prérequis
---
- Java 21
- Maven

une clé OpenAI dans OPENAI_API_KEY

Lancer

cd gptcompare-backend
export OPENAI_API_KEY="sk-..."
mvn spring-boot:run


- Backend sur : http://localhost:8080
------
Test rapide :

curl http://localhost:8080/api/chat/ping

##Frontend

###Prérequis
---
- Node.js + npm

- Angular CLI

---

Lancer
cd gptcompare-frontend
npm install
ng serve


- Frontend sur : http://localhost:4200
