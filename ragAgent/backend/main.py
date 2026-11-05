import asyncio
import json
import logging
import re
import sqlite3
import threading
from datetime import datetime, timezone
from functools import lru_cache
from pathlib import Path
from uuid import uuid4

from fastapi import FastAPI, File, HTTPException, Request, UploadFile
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from agent.react_agent import ReactAgent
from rag.vector_store import VectorStoreService

DB_PATH = Path("database/conversations.sqlite3")
THREAD_ID_PATTERN = re.compile(r"^[A-Za-z0-9_.:-]{1,128}$")
logger = logging.getLogger("uvicorn.error")
MAX_KNOWLEDGE_FILE_SIZE = 20 * 1024 * 1024
UPLOAD_CHUNK_SIZE = 1024 * 1024


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=4000)
    thread_id: str | None = Field(default=None, max_length=128)
    user_id: str | None = Field(default=None, min_length=1, max_length=64)
    session_id: str | None = Field(default=None, min_length=1, max_length=128)


class ChatResponse(BaseModel):
    thread_id: str
    answer: str


class KnowledgeUploadResponse(BaseModel):
    filename: str
    chunks: int
    duplicate: bool
    message: str


class ThreadCreateResponse(BaseModel):
    thread_id: str
    title: str
    created_at: str
    updated_at: str


class ThreadItem(BaseModel):
    thread_id: str
    title: str
    created_at: str
    updated_at: str


class MessageItem(BaseModel):
    id: int
    role: str
    content: str
    created_at: str


@lru_cache(maxsize=1)
def get_agent() -> ReactAgent:
    return ReactAgent()


@lru_cache(maxsize=1)
def get_vector_store() -> VectorStoreService:
    return VectorStoreService()


class UploadTooLargeError(ValueError):
    pass


def sanitize_upload_filename(filename: str | None) -> str:
    raw_name = (filename or "").replace("\\", "/").rsplit("/", maxsplit=1)[-1]
    suffix = Path(raw_name).suffix.lower()
    stem = Path(raw_name).stem
    safe_stem = re.sub(r'[<>:"/\\|?*]', "_", stem).strip(" .")[:120]
    if not safe_stem or not suffix:
        raise HTTPException(status_code=422, detail="文件名或扩展名无效")
    return safe_stem + suffix


def write_upload_file(source, destination: Path) -> int:
    size = 0
    with destination.open("wb") as target:
        while chunk := source.read(UPLOAD_CHUNK_SIZE):
            size += len(chunk)
            if size > MAX_KNOWLEDGE_FILE_SIZE:
                raise UploadTooLargeError
            target.write(chunk)
    return size


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def validate_thread_id(thread_id: str) -> str:
    if not THREAD_ID_PATTERN.fullmatch(thread_id):
        raise HTTPException(status_code=422, detail="thread_id 只能包含字母、数字、下划线、点、冒号和短横线，长度 1-128")
    return thread_id


def connect_db() -> sqlite3.Connection:
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_chat_db() -> None:
    with connect_db() as conn:
        conn.execute("PRAGMA journal_mode=WAL")
        conn.execute("PRAGMA foreign_keys=ON")
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS chat_threads (
                thread_id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
            """
        )
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS chat_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                thread_id TEXT NOT NULL,
                role TEXT NOT NULL CHECK(role IN ('user', 'assistant')),
                content TEXT NOT NULL,
                created_at TEXT NOT NULL,
                FOREIGN KEY(thread_id) REFERENCES chat_threads(thread_id) ON DELETE CASCADE
            )
            """
        )
        conn.execute("CREATE INDEX IF NOT EXISTS idx_chat_messages_thread_id_id ON chat_messages(thread_id, id)")


def create_thread(title: str = "新会话", thread_id: str | None = None) -> ThreadItem:
    init_chat_db()
    thread_id = validate_thread_id(thread_id) if thread_id else uuid4().hex
    timestamp = now_iso()
    with connect_db() as conn:
        try:
            conn.execute(
                "INSERT INTO chat_threads(thread_id, title, created_at, updated_at) VALUES (?, ?, ?, ?)",
                (thread_id, title, timestamp, timestamp),
            )
        except sqlite3.IntegrityError as exc:
            raise HTTPException(status_code=409, detail="会话已存在") from exc
    return ThreadItem(thread_id=thread_id, title=title, created_at=timestamp, updated_at=timestamp)


def ensure_thread(thread_id: str | None) -> str:
    if not thread_id:
        return create_thread().thread_id
    thread_id = validate_thread_id(thread_id)
    with connect_db() as conn:
        exists = conn.execute("SELECT 1 FROM chat_threads WHERE thread_id = ?", (thread_id,)).fetchone()
    if not exists:
        create_thread(thread_id=thread_id)
    return thread_id


def save_turn(thread_id: str, user_message: str, assistant_message: str) -> None:
    title = user_message[:30] or "新会话"
    timestamp = now_iso()
    with connect_db() as conn:
        row = conn.execute("SELECT title FROM chat_threads WHERE thread_id = ?", (thread_id,)).fetchone()
        new_title = title if row and row["title"] == "新会话" else row["title"]
        conn.execute(
            "UPDATE chat_threads SET title = ?, updated_at = ? WHERE thread_id = ?",
            (new_title, timestamp, thread_id),
        )
        conn.execute(
            "INSERT INTO chat_messages(thread_id, role, content, created_at) VALUES (?, 'user', ?, ?)",
            (thread_id, user_message, timestamp),
        )
        conn.execute(
            "INSERT INTO chat_messages(thread_id, role, content, created_at) VALUES (?, 'assistant', ?, ?)",
            (thread_id, assistant_message, timestamp),
        )


app = FastAPI(title="RAG Agent API", version="1.0.0")

agent_lock = threading.Lock()
knowledge_upload_lock = asyncio.Lock()


@app.on_event("startup")
async def startup() -> None:
    init_chat_db()


@app.get("/api/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/api/threads", response_model=ThreadCreateResponse)
async def new_thread() -> ThreadCreateResponse:
    item = create_thread()
    return ThreadCreateResponse(**item.model_dump())


@app.get("/api/threads", response_model=list[ThreadItem])
async def list_threads() -> list[ThreadItem]:
    init_chat_db()
    with connect_db() as conn:
        rows = conn.execute(
            "SELECT thread_id, title, created_at, updated_at FROM chat_threads ORDER BY updated_at DESC"
        ).fetchall()
    return [ThreadItem(**dict(row)) for row in rows]


@app.get("/api/threads/{thread_id}/messages", response_model=list[MessageItem])
async def list_messages(thread_id: str) -> list[MessageItem]:
    thread_id = validate_thread_id(thread_id)
    init_chat_db()
    with connect_db() as conn:
        rows = conn.execute(
            "SELECT id, role, content, created_at FROM chat_messages WHERE thread_id = ? ORDER BY id ASC",
            (thread_id,),
        ).fetchall()
    return [MessageItem(**dict(row)) for row in rows]


@app.post("/api/knowledge/upload", response_model=KnowledgeUploadResponse)
async def upload_knowledge(file: UploadFile = File(...)) -> KnowledgeUploadResponse:
    filename = sanitize_upload_filename(file.filename)
    service = get_vector_store()
    suffix = Path(filename).suffix.lower()
    if suffix not in service.allowed_extensions:
        allowed = ", ".join(sorted(service.allowed_extensions))
        raise HTTPException(status_code=415, detail=f"仅支持以下文件类型: {allowed}")

    temp_path = service.data_path / f".upload-{uuid4().hex}{suffix}"
    try:
        async with knowledge_upload_lock:
            size = await asyncio.to_thread(write_upload_file, file.file, temp_path)
            if size == 0:
                raise HTTPException(status_code=422, detail="不能上传空文件")

            result = await asyncio.to_thread(service.ingest_file, temp_path, filename)
            if result.duplicate:
                return KnowledgeUploadResponse(
                    filename=filename,
                    chunks=0,
                    duplicate=True,
                    message="文件内容已存在，无需重复入库",
                )

            destination = service.data_path / filename
            if destination.exists():
                destination = service.data_path / f"{Path(filename).stem}-{result.md5[:8]}{suffix}"
            temp_path.replace(destination)

            return KnowledgeUploadResponse(
                filename=filename,
                chunks=result.chunks,
                duplicate=False,
                message=f"入库成功，共生成 {result.chunks} 个文本分片",
            )
    except UploadTooLargeError as exc:
        raise HTTPException(status_code=413, detail="文件不能超过 20 MiB") from exc
    except HTTPException:
        raise
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except Exception as exc:
        logger.exception("Knowledge upload failed: filename=%s", filename)
        raise HTTPException(status_code=500, detail="知识库文件入库失败，请检查服务日志") from exc
    finally:
        await file.close()
        temp_path.unlink(missing_ok=True)


@app.post("/api/chat")
async def chat(payload: ChatRequest, request: Request):
    message = payload.message.strip()
    if not message:
        raise HTTPException(status_code=422, detail="消息不能为空")

    wants_sse = "text/event-stream" in request.headers.get("accept", "").lower()

    # Java 侧的 session_id 就是 LangGraph 的 thread_id。
    if payload.thread_id:
        thread_id = validate_thread_id(payload.thread_id)
    elif payload.session_id:
        thread_id = validate_thread_id(payload.session_id)
    else:
        thread_id = ensure_thread(None)

    if wants_sse:
        if not payload.user_id or not (payload.thread_id or payload.session_id):
            raise HTTPException(status_code=422, detail="SSE 调用必须提供 user_id 和 thread_id")

        def event_stream():
            try:
                with agent_lock:
                    for chunk in get_agent().execute_stream(
                            message,
                            thread_id=thread_id,
                            user_id=payload.user_id,
                    ):
                        if chunk:
                            data = json.dumps({"content": chunk}, ensure_ascii=False)
                            yield f"data: {data}\n\n"
                yield "event: done\ndata: [DONE]\n\n"
            except Exception:
                logger.exception(
                    "Agent SSE invocation failed: user_id=%s, session_id=%s",
                    payload.user_id,
                    thread_id,
                )
                error_data = json.dumps({"error": "智能体调用失败"}, ensure_ascii=False)
                yield f"event: error\ndata: {error_data}\n\n"

        return StreamingResponse(
            event_stream(),
            media_type="text/event-stream",
            headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
        )

    def run_agent() -> str:
        with agent_lock:
            return "".join(get_agent().execute_stream(message, thread_id=thread_id)).strip()

    try:
        answer = await asyncio.to_thread(run_agent)
    except Exception as exc:
        logger.exception("Agent invocation failed: thread_id=%s", thread_id)
        raise HTTPException(status_code=500, detail="智能体调用失败，请检查模型配置和服务日志") from exc

    if not answer:
        raise HTTPException(status_code=502, detail="智能体未返回有效内容")

    save_turn(thread_id, message, answer)
    return ChatResponse(thread_id=thread_id, answer=answer)

