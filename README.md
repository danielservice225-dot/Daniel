# Daniel — Aura IA

Assistant vocal responsive avec interface web et backend Python FastAPI. Le backend permet à Daniel de répondre aux questions générales, notamment en informatique, électronique, mathématiques, sciences et culture générale.

## Fichiers du backend

- `main.py` : API FastAPI, endpoint `/api/chat`, santé `/api/health`.
- `requirements.txt` : dépendances Python.
- `.env.example` : exemple de configuration, sans clé secrète.
- `render.yaml` : Blueprint de déploiement Render.
- `index.html` : interface de Daniel.

## Déployer sur Render

1. Ouvrez Render et créez un nouveau **Blueprint** depuis le dépôt `danielservice225-dot/Daniel`, ou créez un Web Service avec ce dépôt.
2. Render utilise `pip install -r requirements.txt` pour l'installation et `uvicorn main:app --host 0.0.0.0 --port $PORT` pour le démarrage.
3. Dans les variables d'environnement du service, définissez `GROQ_API_KEY` avec votre clé Groq. Ne placez jamais la clé dans `index.html` ni dans GitHub.
4. Attendez la fin du déploiement puis testez `https://VOTRE-SERVICE.onrender.com/api/health`. Remplacez l'exemple par l'URL réellement attribuée par Render.
5. Ouvrez Daniel, allez dans **Réglages**, indiquez l'URL de base du service Render dans le champ de l'API (par exemple `https://VOTRE-SERVICE.onrender.com`), puis enregistrez.

Le nom exact du service et son URL dépendent du déploiement Render. Le fichier `render.yaml` configure le service nommé `daniel-aura-api` et déclare `GROQ_API_KEY` comme variable secrète à renseigner dans Render.

## Modèle IA et solutions de repli

Modèle principal par défaut : `openai/gpt-oss-120b` via l'API compatible OpenAI de Groq. En cas d'échec, le backend essaie successivement les modèles configurés dans `GROQ_FALLBACK_MODELS` : `openai/gpt-oss-20b`, `llama-3.3-70b-versatile` et `llama-3.1-8b-instant`.

Vous pouvez changer ces variables dans Render sans modifier le code. Les modèles doivent être disponibles pour votre compte Groq et peuvent changer selon les disponibilités du fournisseur.

## Lancer localement

Python 3.11 ou supérieur est recommandé.

Windows PowerShell :
```powershell
py -3.11 -m venv .venv
.venv\Scripts\Activate.ps1
pip install -r requirements.txt
Copy-Item .env.example .env
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

macOS / Linux :
```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

Renseignez ensuite `GROQ_API_KEY` dans le fichier `.env` local et ouvrez `http://localhost:8000`. Ne commitez jamais ce fichier.

## Endpoints

- `GET /api/health` : vérifie que l'API répond et indique si une clé est configurée.
- `POST /api/chat` : reçoit `message` et un historique limité, puis renvoie `reply`.
- `/api/docs` : documentation interactive de l'API.

## Sécurité et limites

- La clé Groq reste côté serveur.
- Les messages et l'historique sont validés et limités.
- CORS est configuré avec des origines explicites via `ALLOWED_ORIGINS`.
- Les erreurs du fournisseur ne sont pas renvoyées aux visiteurs.
- Pour un service public à grande échelle, ajoutez authentification, limitation de débit et supervision.
- Les réponses IA peuvent être inexactes ; vérifiez les informations importantes.
