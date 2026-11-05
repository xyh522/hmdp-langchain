import threading
from dataclasses import dataclass
from pathlib import Path

from langchain_chroma import Chroma
from langchain_core.documents import Document
from langchain_text_splitters import RecursiveCharacterTextSplitter

from model.factory import embedding_model
from utils.config_handler import chroma_conf
from utils.file_handler import get_file_md5_hex, listdir_with_allowed_type, pdf_loader, txt_loader
from utils.logger_handler import logger
from utils.path_tool import get_abs_path


@dataclass(frozen=True)
class IngestResult:
    filename: str
    chunks: int
    duplicate: bool
    md5: str


class VectorStoreService:
    _ingest_lock = threading.Lock()

    def __init__(self):
        self.vector_store = Chroma(
            collection_name=chroma_conf["collection_name"],
            embedding_function=embedding_model,
            persist_directory=chroma_conf["persist_directory"],
        )
        self.spliter = RecursiveCharacterTextSplitter(
            chunk_size=chroma_conf["chunk_size"],
            chunk_overlap=chroma_conf["chunk_overlap"],
            separators=chroma_conf["separators"],
            length_function=len,
        )

    @property
    def data_path(self) -> Path:
        path = Path(get_abs_path(chroma_conf["data_path"]))
        path.mkdir(parents=True, exist_ok=True)
        return path

    @property
    def allowed_extensions(self) -> set[str]:
        return {f".{item.lower().lstrip('.')}" for item in chroma_conf["allow_knowledge_file_type"]}

    @property
    def md5_store_path(self) -> Path:
        path = Path(get_abs_path(chroma_conf["md5_hex_store"]))
        path.parent.mkdir(parents=True, exist_ok=True)
        path.touch(exist_ok=True)
        return path

    def get_retriever(self):
        return self.vector_store.as_retriever(search_kwargs={"k": chroma_conf["k"]})

    def _has_md5(self, md5_hex: str) -> bool:
        with self.md5_store_path.open("r", encoding="utf-8") as file:
            return any(line.strip() == md5_hex for line in file)

    def _save_md5(self, md5_hex: str) -> None:
        with self.md5_store_path.open("a", encoding="utf-8") as file:
            file.write(md5_hex + "\n")

    def _load_file(self, path: Path) -> list[Document]:
        suffix = path.suffix.lower()
        if suffix == ".txt":
            return txt_loader(str(path))
        if suffix == ".pdf":
            return pdf_loader(str(path))
        raise ValueError(f"不支持的知识库文件类型: {suffix}")

    def ingest_file(self, file_path: str | Path, source_name: str | None = None) -> IngestResult:
        path = Path(file_path)
        if not path.is_file():
            raise FileNotFoundError(f"知识库文件不存在: {path}")
        if path.suffix.lower() not in self.allowed_extensions:
            raise ValueError(f"仅支持以下文件类型: {', '.join(sorted(self.allowed_extensions))}")

        md5_hex = get_file_md5_hex(str(path))
        if not md5_hex:
            raise ValueError(f"无法计算文件 MD5: {path.name}")

        display_name = source_name or path.name
        with self._ingest_lock:
            if self._has_md5(md5_hex):
                return IngestResult(filename=display_name, chunks=0, duplicate=True, md5=md5_hex)

            documents = self._load_file(path)
            if not documents:
                raise ValueError("文件中没有可入库的有效文本")

            for document in documents:
                document.metadata["source"] = display_name
                document.metadata["file_md5"] = md5_hex

            split_documents = self.spliter.split_documents(documents)
            if not split_documents:
                raise ValueError("文件切分后没有可入库的有效文本")

            self.vector_store.add_documents(split_documents)
            self._save_md5(md5_hex)

        logger.info("[加载知识库]%s 入库成功，共 %d 个分片", display_name, len(split_documents))
        return IngestResult(
            filename=display_name,
            chunks=len(split_documents),
            duplicate=False,
            md5=md5_hex,
        )

    def load_document(self) -> list[IngestResult]:
        results: list[IngestResult] = []
        allowed_types = tuple(item.lower().lstrip(".") for item in chroma_conf["allow_knowledge_file_type"])
        paths = listdir_with_allowed_type(str(self.data_path), allowed_types)

        for path in paths:
            try:
                result = self.ingest_file(path)
                results.append(result)
                if result.duplicate:
                    logger.info("[加载知识库]%s 已存在，跳过", path)
            except Exception:
                logger.exception("[加载知识库]%s 加载失败", path)

        return results


if __name__ == "__main__":
    service = VectorStoreService()
    service.load_document()
    for result in service.get_retriever().invoke("迷路"):
        print(result.page_content)
        print("-" * 20)