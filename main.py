import os
import re
import uuid
from pathlib import Path
from typing import Literal

import httpx
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse
from pydantic import BaseModel, Field, field_validator

load_dotenv()
BASE_DIR = Path(__file__).resolve().parent
GROQ_API_KEY = os.getenv("GROQ_API_KEY", "").strip()
GROQ_BASE_URL = os.getenv("GROQ_BASE_URL", "https://api.groq.com/openai/v1").rstrip("/")
PRIMARY_MODEL = os.getenv("GROQ_MODEL", "openai/gpt-oss-120b").strip()
FALLBACK_MODELS = [
    model.strip()
    for model in os.getenv(
        "GROQ_FALLBACK_MODELS",
        "openai/gpt-oss-20b,llama-3.3-70b-versatile,llama-3.1-8b-instant",
    ).split(",")
    if model.strip() and model.strip() != PRIMARY_MODEL
]
ALLOWED_ORIGINS = [
    item.strip()
    for item in os.getenv(
        "ALLOWED_ORIGINS",
        "https://danielservice225-dot.github.io,http://localhost:8000,http://127.0.0.1:8000",
    ).split(",")
    if item.strip()
]

app = FastAPI(
    title="Daniel Aura IA API",
    description="Backend conversationnel de Daniel, assistant IA francophone.",
    version="1.0.0",
    docs_url="/api/docs",
    openapi_url="/api/openapi.json",
)
app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_credentials=False,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["Content-Type"],
)


class ChatMessage(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=4000)

    @field_validator("content")
    @classmethod
    def clean_content(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("Le message ne peut pas être vide.")
        return value


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=2000)
    history: list[ChatMessage] = Field(default_factory=list, max_length=12)

    @field_validator("message")
    @classmethod
    def clean_message(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("Le message ne peut pas être vide.")
        return value


class ChatResponse(BaseModel):
    reply: str
    conversation_id: str
    model: str


@app.middleware("http")
async def security_headers(request: Request, call_next):
    length = request.headers.get("content-length")
    if length:
        try:
            if int(length) > 40_000:
                return JSONResponse(status_code=413, content={"detail": "Requête trop volumineuse."})
        except ValueError:
            return JSONResponse(status_code=400, content={"detail": "En-tête invalide."})
    response = await call_next(request)
    response.headers["X-Content-Type-Options"] = "nosniff"
    response.headers["Referrer-Policy"] = "no-referrer"
    response.headers["Cache-Control"] = "no-store" if request.url.path.startswith("/api/") else "no-cache"
    return response


@app.get("/api/health")
async def health():
    return {
        "status": "ok",
        "service": "daniel-aura",
        "model_configured": bool(GROQ_API_KEY),
        "primary_model": PRIMARY_MODEL,
    }


SYSTEM_PROMPT = (
    "Identité et création : si on te demande qui t’a créé, qui est ton créateur ou qui a conçu Daniel Aura IA, réponds clairement : « Daniel Aura IA a été créée par TANON SETH, élève du LPELIA (Lycée Professionnel d’Électronique et d’Informatique Appliquées) de Treichville. » Ne prétends pas avoir été créé par OpenAI, Groq ou une autre entreprise. Cette réponse d’identité est prioritaire et doit rester cohérente. "
    "Tu es Daniel Aura 2.0, un assistant IA polyvalent qui répond en français par défaut. "
    "Réponds naturellement, comme une personne qui explique clairement les choses, avec un ton poli, direct et chaleureux. Commence par répondre à la question, sans introduction inutile ni conclusion répétitive. "
    "N'affiche pas de balisage Markdown décoratif : pas de **gras**, ***, __souligné__, lignes de séparation avec --- ou ___, ni de suites de symboles inutiles comme )/. N'entoure pas les mots de caractères décoratifs. Utilise des phrases et des paragraphes simples. Si une liste est vraiment utile, utilise une numérotation lisible. Pour le code, conserve la syntaxe exacte et présente le code dans un bloc de code. "
    "Tu aides notamment les élèves en informatique, électronique, mathématiques, sciences, technologie, langues, culture générale et apprentissage. Explique clairement, adapte le niveau à la question et donne des étapes pratiques quand c'est utile. Pour le code, fournis des exemples lisibles et sûrs. N'invente pas des faits, des sources, des actions effectuées ou des informations locales. "
    "Si une information est incertaine ou actuelle et que tu ne peux pas la vérifier, dis-le clairement. Pour l'électronique, rappelle les précautions de sécurité lorsqu'une manipulation électrique peut être dangereuse."
)


def clean_reply(text: str) -> str:
    """Remove stray Markdown decoration while preserving code blocks and inline code."""
    text = text.replace("\r\n", "\n").replace("\r", "\n").strip()
    tick = chr(96)
    parts = re.split(r"(" + re.escape(tick * 3) + r"[\s\S]*?" + re.escape(tick * 3) + r"|" + re.escape(tick) + r"[^\n" + re.escape(tick) + r"]+" + re.escape(tick) + r")", text)
    cleaned = []
    for index, part in enumerate(parts):
        if index % 2:
            cleaned.append(part)
            continue
        lines = []
        for line in part.split("\n"):
            if re.fullmatch(r"\s*(?:[-_*]\s*){3,}\s*", line):
                continue
            line = re.sub(r"^\s{0,3}#{1,6}\s+", "", line)
            line = re.sub(r"(?<!\w)(?:\*{1,3}|_{1,3})(?=\S)", "", line)
            line = re.sub(r"(?<=\S)(?:\*{1,3}|_{1,3})(?!\w)", "", line)
            line = re.sub(r"(?<!\w)[*_~=-]{2,}(?!\w)", "", line)
            line = re.sub(r"\)/(?=$|\s|[,.;!?])", "", line)
            line = re.sub(r"\s+([,.;:!?])", r"\1", line)
            line = re.sub(r"[ \t]{2,}", " ", line).strip()
            lines.append(line)
        cleaned.append("\n".join(lines))
    result = "".join(cleaned)
    result = re.sub(r"\n{3,}", "\n\n", result)
    return result.strip()


async def ask_model(client: httpx.AsyncClient, model: str, messages: list[dict]) -> str:
    response = await client.post(
        f"{GROQ_BASE_URL}/chat/completions",
        headers={"Authorization": f"Bearer {GROQ_API_KEY}", "Content-Type": "application/json"},
        json={
            "model": model,
            "messages": messages,
            "temperature": 0.5,
            "max_tokens": 1200,
        },
    )
    response.raise_for_status()
    data = response.json()
    reply = data["choices"][0]["message"]["content"]
    if not isinstance(reply, str) or not reply.strip():
        raise ValueError("Réponse vide du modèle.")
    return clean_reply(reply)


@app.post("/api/chat", response_model=ChatResponse)
async def chat(payload: ChatRequest):
    if not GROQ_API_KEY:
        raise HTTPException(
            status_code=503,
            detail="Le moteur IA n'est pas configuré. Ajoutez GROQ_API_KEY dans les variables d'environnement du serveur.",
        )

    messages = [{"role": "system", "content": SYSTEM_PROMPT}]
    messages.extend({"role": item.role, "content": item.content} for item in payload.history[-10:])
    messages.append({"role": "user", "content": payload.message})

    errors = []
    for model in [PRIMARY_MODEL, *FALLBACK_MODELS]:
        try:
            async with httpx.AsyncClient(timeout=httpx.Timeout(35.0, connect=10.0)) as client:
                reply = await ask_model(client, model, messages)
            return ChatResponse(reply=reply[:8000], conversation_id=str(uuid.uuid4()), model=model)
        except (httpx.HTTPError, KeyError, IndexError, TypeError, ValueError) as exc:
            errors.append(f"{model}: {type(exc).__name__}")

    # Do not expose provider responses, keys, or internal request details to visitors.
    raise HTTPException(
        status_code=502,
        detail="Le moteur IA est temporairement indisponible. Réessayez dans un instant.",
    )


@app.get("/manifest.webmanifest", include_in_schema=False)
async def pwa_manifest():
    manifest_path = BASE_DIR / "manifest.webmanifest"
    if manifest_path.exists():
        return FileResponse(manifest_path, media_type="application/manifest+json", headers={"Cache-Control": "no-cache"})
    raise HTTPException(status_code=404, detail="Manifest introuvable.")


@app.get("/sw.js", include_in_schema=False)
async def pwa_service_worker():
    worker_path = BASE_DIR / "sw.js"
    if worker_path.exists():
        return FileResponse(worker_path, media_type="application/javascript", headers={"Cache-Control": "no-cache", "Service-Worker-Allowed": "/"})
    raise HTTPException(status_code=404, detail="Service worker introuvable.")


@app.get("/icon.svg", include_in_schema=False)
async def pwa_icon():
    icon_path = BASE_DIR / "icon.svg"
    if icon_path.exists():
        return FileResponse(icon_path, media_type="image/svg+xml", headers={"Cache-Control": "public, max-age=86400"})
    raise HTTPException(status_code=404, detail="Icône introuvable.")


@app.get("/", include_in_schema=False)
async def frontend():
    index_path = BASE_DIR / "index.html"
    if index_path.exists():
        return FileResponse(index_path)
    return {"service": "Daniel Aura IA", "health": "/api/health", "chat": "/api/chat"}
