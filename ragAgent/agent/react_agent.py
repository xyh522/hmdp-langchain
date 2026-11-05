from langchain.agents import create_agent

from model.factory import chat_model
from utils.prompt_loader import load_system_prompts
from agent.tools.concert_tools import rag_summarize
from agent.tools.middleware import monitor_tool, log_before_model
from typing import TypedDict
from agent.tools.ShortMemory import shortTermMemory
from langchain.agents.middleware import SummarizationMiddleware
from langchain_core.messages import AIMessage, AIMessageChunk


class ConcertContext(TypedDict):
    user_id: str | None


class ReactAgent:
    def __init__(self):
        self.agent = create_agent(
            model=chat_model,
            system_prompt=load_system_prompts(),
            tools=[rag_summarize],
            context_schema=ConcertContext,
            middleware=[monitor_tool, 
                        log_before_model, 
                        SummarizationMiddleware(
                         model=chat_model,
                          trigger=("tokens",4000),
                          keep=("messages",20)
                        )
                        ],
            checkpointer=shortTermMemory.checkpointer,
        )

    def close(self) -> None:
        shortTermMemory.close()

    def execute_stream(self, query: str, thread_id: str = "default", user_id: str | None = None):
        input_dict = {
            "messages": [
                {"role": "user", "content": query},
            ]
        }
        config = shortTermMemory.get_config(thread_id)

        # session_id 直接作为 thread_id，LangGraph 按会话隔离短期记忆。
        # user_id 放入运行时上下文，后续工具可读取当前用户身份。
        context = {"user_id": user_id}
        for message, _metadata in self.agent.stream(
                input_dict,
                config=config,
                stream_mode="messages",
                context=context,
        ):
            if not isinstance(message, (AIMessage, AIMessageChunk)):
                continue

            content = message.content
            if isinstance(content, str):
                if content:
                    yield content
                continue

            # 兼容 LangChain 的内容块格式，例如 [{"type": "text", "text": "..."}]。
            if isinstance(content, list):
                for block in content:
                    if isinstance(block, str) and block:
                        yield block
                    elif isinstance(block, dict):
                        text = block.get("text")
                        if isinstance(text, str) and text:
                            yield text


if __name__ == '__main__':
    agent = ReactAgent()

    for chunk in agent.execute_stream("演唱会抢票前要准备什么？", thread_id="cli-demo"):
        print(chunk, end="", flush=True)
