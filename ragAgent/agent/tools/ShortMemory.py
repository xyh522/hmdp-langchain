# agent/tools/ShortMemory.py

import sqlite3
import uuid
from pathlib import Path

from langgraph.checkpoint.sqlite import SqliteSaver

from utils.config_handler import agent_conf


class ShortTermMemory:
    """
    面向对象的短期记忆封装。
    底层使用 LangGraph 的 SqliteSaver，并按 thread_id 隔离不同会话。
    """

    def __init__(self, db_path: str | None = None):
        self.db_path = db_path or agent_conf.get("shortmemory_path")
        Path(self.db_path).parent.mkdir(parents=True, exist_ok=True)
        self.connection = sqlite3.connect(self.db_path, check_same_thread=False)
        self.checkpointer = SqliteSaver(self.connection)
        self.checkpointer.setup()

    def new_thread_id(self) -> str:
        """生成一个新的会话 ID，用于新用户/新会话。"""
        return str(uuid.uuid4())

    def get_config(self, thread_id: str) -> dict:
        """
        LangGraph 通过 configurable.thread_id 定位某个会话的短期记忆。
        传给 agent.stream(..., config=...) 即可自动读写历史。
        """
        return {"configurable": {"thread_id": thread_id}}

    def delete_thread(self, thread_id: str):
        """删除某个会话的所有 checkpoint，相当于清空该会话的短期记忆。"""
        self.checkpointer.delete_thread(thread_id)

    def close(self):
        self.connection.close()


shortTermMemory = ShortTermMemory()
