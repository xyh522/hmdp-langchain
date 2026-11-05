"""演唱会知识检索，不执行实际购票操作。"""
from functools import lru_cache
from langchain_core.tools import tool
from rag.vector_store import VectorStoreService

@lru_cache(maxsize=1)
def _store():
    return VectorStoreService()

@tool
def rag_summarize(query: str) -> str:
    """检索演唱会抢票、候补、实名、支付、退票、入场知识；不查询实时库存或订单。"""
    docs = _store().get_retriever().invoke(query)
    if not docs:
        return "知识库没有相关资料，不能确认该问题。"
    return "\n\n".join(
        f"来源：{doc.metadata.get('source', '未知')}\n{doc.page_content}"
        for doc in docs
    )
