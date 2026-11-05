**黑马点评与 RAG Agent 项目**

这是一个供学习和本地运行的店铺点评与优惠券系统。浏览器页面由 Vue 3 构建、Nginx 提供；Java 服务处理用户、店铺、优惠券和订单；Python Agent 提供演唱会购票知识问答。三个应用分别运行，通过 HTTP 接口连接。当前前端有普通用户和管理员入口，客服聊天经 Java 转发给 Agent。秒杀券订单只展示抢购记录，没有接入支付。

**项目结构**

```text
项目根目录/
├─ backend/                 Spring Boot 业务后端
│  ├─ pom.xml               Maven 依赖及 Java 源码级别
│  └─ src/main/
│     ├─ java/com/hmdp/     controller、service、mapper、config 等
│     └─ resources/         application.yaml、SQL 迁移、Mapper XML、Lua 脚本
├─ frontend/                Windows Nginx 与 Vue 3 项目
│  ├─ frontend-vue3/
│  │  ├─ src/api/            浏览器调用 Java 接口的封装
│  │  ├─ src/components/     用户端、管理端及客服界面
│  │  ├─ src/router/         Vue 路由
│  │  ├─ public/             构建时复制的静态资源
│  │  └─ dist/               npm run build 的产物，不提交到 Git
│  ├─ conf/nginx.conf       监听端口、静态目录及 /api 反向代理
│  └─ html/hmdp/            Nginx 实际读取的前端文件
├─ ragAgent/                FastAPI、LangChain Agent 与 RAG
│  ├─ backend/main.py       /api/chat、/api/health、知识上传等入口
│  ├─ agent/                Agent、工具和短期记忆
│  ├─ rag/                  检索与向量库逻辑
│  ├─ model/、config/、prompts/  模型、配置及提示词
│  ├─ data/                 原始知识文件
│  ├─ database/             SQLite 会话与 checkpoint，运行时创建
│  └─ chroma_db/            Chroma 向量数据，运行时创建
└─ db/生成.sql              当前 Java 业务所需的 MySQL 空库建表脚本
```

`mall/` 是独立学习项目，不属于此系统，也不纳入本仓库。

**技术与职责**

| 层 | 当前技术 | 主要职责 |
| --- | --- | --- |
| 浏览器端 | Vue 3、Vue Router、Axios、Vite | 店铺浏览与搜索、博客、登录、客服、秒杀券订单、管理员店铺及优惠券页面 |
| 静态服务与代理 | Nginx for Windows | 提供 Vue 构建文件，将 `/api/` 转发至 Java；客服 SSE 关闭代理缓冲 |
| Java 后端 | Spring Boot 2.3.12、MyBatis-Plus、MySQL、Redis/Redisson、RabbitMQ | 业务接口、用户会话与角色检查、店铺及优惠券管理、秒杀资格判断和异步订单入库 |
| Agent 服务 | Python 3.12、FastAPI、LangChain/LangGraph、DashScope、Chroma、SQLite | 演唱会知识检索、模型调用、按 thread_id 隔离的对话记忆、SSE 回复 |

后端 `pom.xml` 的 `java.version` 为 **1.8**，这是源码/目标级别；实际运行 JDK 还需与项目依赖兼容。Agent 在 `config/rag.yml` 中使用 `qwen-max` 聊天模型和 `text-embedding-v4` 向量模型，调用模型需要有效的 DashScope 密钥。

**请求与数据流**

```text
浏览器 :90
  └─ Nginx /api → Spring Boot :8081
       ├─ MySQL hmdp：店铺、用户、优惠券、订单、客服会话与消息
       ├─ Redis：登录状态、缓存、秒杀资格与库存等
       ├─ RabbitMQ：秒杀订单异步写入
       └─ /api/chat → FastAPI :8000 → Agent → DashScope / Chroma
                                  └─ SSE 分片返回 Java，再返回浏览器
```

浏览器不直接传入可决定订单归属的用户 ID；Java 根据当前登录用户读取其订单。客服由 Java 传 `message`、`user_id`、`thread_id` 给 Agent；Agent 的短期记忆按 `thread_id` 隔离。秒杀请求先完成 Redis 资格判断，再经 RabbitMQ 异步写入 MySQL，因此接口返回与订单可查询之间可能有短暂延迟。

**本地端口与启动顺序（Windows）**

| 服务 | 默认地址 | 配置位置 |
| --- | --- | --- |
| Nginx 页面 | `http://127.0.0.1:90` | `frontend/conf/nginx.conf` |
| Vue 开发服务 | `http://127.0.0.1:5173` | `frontend/frontend-vue3/vite.config.js` |
| Spring Boot | `http://127.0.0.1:8081` | `backend/src/main/resources/application.yaml` |
| Agent API | `http://127.0.0.1:8000` | Agent 启动命令；Java 的 `agent.base-url` |

建议先启动 **MySQL、Redis、RabbitMQ**，再启动 **Agent → Java → Nginx**。前端开发时可以运行 Vite；使用 Nginx 时不需要同时运行 Vite。

**1. 准备 MySQL、Redis 与 RabbitMQ**

Java 使用 MySQL 的 `hmdp` 库。全新空库可在 MySQL 客户端执行：

```sql
SOURCE D:/java_heima_learning/hmdp+langchain/db/生成.sql;
```

`db/生成.sql` 包含当前业务用到的 `tb_*` 和 `cs_*` 共 13 张表，只含结构，不含用户、店铺、订单或聊天数据。它用于**空库初始化**；已有业务表时不要重复执行。空库导入后也不会凭空出现店铺、管理员账号或知识内容，需要另行录入。`backend/src/main/resources/db/hmdp.sql` 是旧版样例脚本，包含 `DROP TABLE`；同目录的 `customer_thread_migration.sql`、`customer_login_session.sql`、`user_role.sql` 用于旧库迁移，全新空库导入 `db/生成.sql` 后不要再执行这些脚本。

MySQL 库和表**不会**由 IDEA、MyBatis 或 Java 服务自动创建。Redis 与 RabbitMQ 服务、RabbitMQ 用户及 virtual-host 也需事先准备；Java 的 `RabbitMQConfig` 会声明秒杀订单所用交换机与队列。连接地址在 `backend/src/main/resources/application.yaml`，换机器后应按实际环境修改。

**2. 创建 Agent 虚拟环境并启动**

在 PowerShell 中从 `ragAgent/` 目录运行。仓库中的实际依赖文件名是 `requirements.txt` 与 `requirements-fastapi.txt`；后者会读取前者，并补充 FastAPI 依赖。

```powershell
cd D:\java_heima_learning\hmdp+langchain\ragAgent
py -3.12 -m venv venv
.\venv\Scripts\python.exe -m pip install --upgrade pip
.\venv\Scripts\python.exe -m pip install -r requirements-fastapi.txt
$env:DASHSCOPE_API_KEY = '你的有效密钥'
.\venv\Scripts\python.exe -m uvicorn backend.main:app --host 127.0.0.1 --port 8000
```

不必激活虚拟环境，直接使用 `venv\Scripts\python.exe` 可以避免误用 Conda 或系统 Python。`venv/` 不应提交或从旧电脑直接复制；现有环境的 `pyvenv.cfg` 记录了创建时的本机路径。启动命令必须在 `ragAgent/` 根目录执行，开发时可追加 `--reload`。已有环境也可运行 `start.ps1` 或 `start.bat`；脚本要求 `venv` 目录存在。

检查 `http://127.0.0.1:8000/api/health`，接口文档位于 `http://127.0.0.1:8000/docs`。健康检查成功只能说明 API 已启动；密钥无效或模型不可达仍会导致聊天失败。不要将真实密钥写进仓库。

Agent 的本地数据创建方式：

| 路径 | 创建时机 | 是否会自动出现知识内容 |
| --- | --- | --- |
| `ragAgent/database/conversations.sqlite3` | FastAPI 启动时创建聊天会话表 | 不适用 |
| `ragAgent/database/checkpoints.db` | Agent 短期记忆初始化时创建 | 不适用 |
| `ragAgent/chroma_db/` | 首次初始化向量库时创建集合 | **不会**；需上传知识 |
| `ragAgent/data/`、`ragAgent/md5.txt` | 知识文件上传或入库时按需创建/更新 | 需手动上传或重新导入 |

知识文件可调用 `POST /api/knowledge/upload` 入库。若新电脑没有原来的 `chroma_db/`，即使保留 `data/` 原始文本，也需要重新入库。上述相对路径以 `ragAgent/` 为工作目录。

**3. 在 IDEA 中启动 Java 后端**

用 IntelliJ IDEA 打开 `backend/pom.xml`，等待 Maven 同步，运行 `com.hmdp.HmDianPingApplication`。首次克隆时，将 `backend/src/main/resources/application.example.yaml` 复制为同目录的 `application.yaml`，填写 MySQL、Redis、RabbitMQ 的实际地址，并设置 `DB_PASSWORD`、`REDIS_PASSWORD`、`RABBITMQ_PASSWORD` 等环境变量。真实 `application.yaml` 不应提交到公开仓库。

确认 `agent.base-url` 指向正在运行的 Agent，默认是 `http://127.0.0.1:8000`。Java 默认监听 8081；若端口占用，停止旧进程或修改 `server.port`。需要 JAR 时，在 `backend/` 执行 `mvn -DskipTests package`，构建结果为 `target/hm-dianping-0.0.1-SNAPSHOT.jar`；部署只需可执行 JAR 和外部服务配置，`target/` 其他中间文件可重新生成。

**4. 构建 Vue 页面并启动 Nginx**

Vue 源码在 `frontend/frontend-vue3/`，Nginx 读取 `frontend/html/hmdp/`。初次部署或修改页面后：

```powershell
cd D:\java_heima_learning\hmdp+langchain\frontend\frontend-vue3
npm install
npm run build
Copy-Item -Path .\dist\* -Destination ..\html\hmdp -Recurse -Force

cd ..
.\nginx.exe -t
.\nginx.exe
```

日常更新只需重新构建并复制 `dist/` **内部内容**，无需复制 `src/` 或 `node_modules/`；`public/` 资源会随 Vite 构建进入 `dist/`。从 `frontend/` 目录启动 Nginx，配置中的 `html/hmdp` 才能按预期解析。Nginx 监听 90，把 `/api/` 转发到 Java 8081，并关闭客服 SSE 的代理缓冲。修改 `conf/nginx.conf` 后先执行 `nginx.exe -t`，再执行 `nginx.exe -s reload`；只替换静态文件一般无需重启 Nginx。

浏览器打开 `http://127.0.0.1:90`。当前 Vue 使用 hash 路由，例如订单页地址为 `http://127.0.0.1:90/#/orders`。如果还看到旧页面，强制刷新浏览器。

**常见排查**

- `8000` 健康检查失败：确认 Agent 是否在 `ragAgent/` 目录启动、虚拟环境依赖是否安装完整。
- 聊天模型调用失败：检查 `DASHSCOPE_API_KEY`、模型网络连接和 Agent 日志；健康接口正常不代表模型可用。
- `8081` 启动失败：检查端口占用，以及 MySQL、Redis、RabbitMQ 的地址和凭据。
- 页面接口 `404`：检查 Nginx `/api/` 代理、Java 是否已重启加载新代码，以及浏览器是否仍在用旧构建文件。
- 数据为空：确认导入的是目标 MySQL 的 `hmdp` 库；空库建表脚本不包含业务数据，Chroma 空集合也不包含知识。
