# 🤖 GPT Compare (Angular + Spring WebFlux)

Application full-stack permettant de comparer les réponses de modèles GPT sur une même question, avec des réglages A/B (modèle, température, max tokens…).

Le backend appelle l’endpoint OpenAI Responses API (`/v1/responses`) et renvoie une réponse normalisée :
- texte
- métriques de tokens
- latence
- statut de tronquage

---

## 🚀 Fonctionnalités

### Architecture

- Frontend Angular → appelle le backend via HTTP
- Backend Spring WebFlux → appelle l’API OpenAI
- Aucun appel OpenAI direct depuis le navigateur (clé sécurisée)
---
### Frontend (Angular)

- Mode **simple** (A) ou **comparaison A/B**
- Réglages par modèle :
  - `model`
  - `temperature` (désactivée automatiquement pour GPT-5*)
  - `maxOutputTokens`

- Affichage :
  - réponse texte
  - latence (`latencyMs`)
  - tokens (`totalTokens`, etc.)
  - détection “réponse tronquée”
  - boutons **Relancer à 4000 / 8000**

- UX :
  - bouton **Réglages**
  - bouton **Effacer**
  - raccourci **Ctrl / Cmd + Entrée**
  - gestion d’état robuste : `loading`, `error`, résultats

---

### Backend (Spring Boot WebFlux)

- API REST réactive :
  - `POST /api/chat/send`
  - `GET /api/chat/ping`

- Appel OpenAI via `WebClient` (JSON)

- Garde-fous :
  - `max_output_tokens` par défaut : **800**
  - cap maximum : **8000**
  - omission de `temperature` quand `null` ou non supportée

- Parsing robuste de la réponse OpenAI :
  - extraction prioritaire de `output_text`
  - fallback sur n’importe quel champ `text`
  - gestion des statuts `completed` / `incomplete`

- Gestion d’erreurs :
  - erreurs HTTP OpenAI (400 / 401 / 500…)
  - coupure réseau / timeout
  - réponse invalide (champ `output` manquant)

---

## 🧱 Stack technique

### Frontend
- Angular (standalone components)
- TypeScript
- RxJS
- Vitest (tests + coverage)

### Backend
- Java 21
- Spring Boot 4 + WebFlux
- OkHttp MockWebServer (tests)
- Reactor Test (StepVerifier)
- JaCoCo (coverage)
- Approche réactive (RxJS / Reactor) pour une gestion fluide des appels asynchrones,
des états de chargement et des erreurs réseau.


### Tests & qualité

- Frontend : tests unitaires avec Vitest
- Backend : tests réactifs avec StepVerifier
- Couverture de code mesurée avec JaCoCo

---

## ⚡ Démarrage rapide

### Backend

#### Prérequis
- Java 21
- Maven
- Une clé OpenAI dans la variable d’environnement `OPENAI_API_KEY`

#### Lancer le backend
```bash
cd gptcompare-backend
export OPENAI_API_KEY="sk-..."
mvn spring-boot:run
```
Backend disponible sur : http://localhost:8080

```bash
curl http://localhost:8080/api/chat/ping
```

### Frontend


#### Prérequis
- Node.js + npm
- Angular CLI

#### Lancer le frontend
```bash
cd gptcompare-frontend
npm install
ng serve
```

Frontend disponible sur : http://localhost:4200


### 🎬 Vidéo de démonstration
---
https://youtu.be/gXUBaCgB8fo
