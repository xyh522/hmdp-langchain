# RAG 与智能体 FastAPI 服务

本项目提供 Agent、RAG 知识库和 FastAPI 接口。用户界面位于独立的黑马点评 Vue 项目，本项目不再包含独立前端。

## 启动

在项目根目录执行：

```powershell
.\venv\Scripts\python.exe -m uvicorn backend.main:app --host 127.0.0.1 --port 8000 --reload
```

也可运行 `start.bat` 或 `start.ps1`。服务地址为 `http://127.0.0.1:8000`，接口文档为 `/docs`，健康检查为 `/api/health`。

## Java 服务调用

Java 服务向 `POST /api/chat` 发送 JSON，请求包含 `message`、`user_id` 和 `thread_id`（兼容 `session_id`）。请求头 `Accept: text/event-stream` 时返回 SSE 流；每个内容事件的 `data` 是包含 `content` 的 JSON，结束事件为 `[DONE]`。

`/api/knowledge/upload` 用于知识文件入库；`/api/threads` 等接口保留，供调试或其他客户端使用。会话数据位于 `database/`，向量知识库位于 `chroma_db/`，不要在清理前端时删除。

若需重建虚拟环境，先创建 `venv`，再安装 `requirements-fastapi.txt`。
