黑马点评 + RAG Agent 本地启动说明（Windows）
========================================

一、目录与端口

backend/   Spring Boot 业务后端，默认端口 8081。
frontend/  Nginx 及 Vue 3 前端，Nginx 默认端口 90；Vue 开发服务默认端口 5173。
ragAgent/  FastAPI、Agent 和 RAG 服务，默认端口 8000。
mall/      参考学习项目，不是当前系统的启动依赖。

调用链：浏览器 http://127.0.0.1:90 → Nginx /api 代理 → Java 8081 → Agent 8000。
Nginx 不直接调用 Agent；Java 的 agent.base-url 配置指向 Agent。

二、先准备外部依赖

1. MySQL、Redis、RabbitMQ 需要先运行。当前连接地址和账号在
   backend/src/main/resources/application.yaml，迁移到其他电脑时必须修改。
2. MySQL 当前库名是 hmdp。Spring Boot/IDEA 启动不会自动创建该库和业务表。
   全新空库使用根目录 db/生成.sql：它会创建 hmdp 库和当前后端需要的
   tb_*、cs_* 表，只包含结构，不包含用户、店铺、订单或聊天数据。
   在 MySQL 客户端中执行 SOURCE D:/java_heima_learning/hmdp+langchain/db/生成.sql;
   脚本仅用于空库初始化，已有表时不要重复执行。
3. backend/src/main/resources/db/ 下的 hmdp.sql 是旧版样例数据脚本，
   customer_thread_migration.sql、customer_login_session.sql、user_role.sql
   是针对旧库的迁移脚本。全新空库导入 db/生成.sql 后，不要再执行这些脚本。
4. RabbitMQ 的交换机和队列由 Java 的 RabbitMQConfig 声明，但 RabbitMQ
   服务、账号及 virtual-host 需要预先配置。Redis 服务也需要预先启动。

三、启动 Agent（ragAgent）

在 PowerShell 中进入 ragAgent 目录。项目曾使用 Python 3.12；建议用
Python 3.12 创建新的虚拟环境。venv/ 不应提交或直接复制到另一台电脑，
现有 venv/pyvenv.cfg 仍记录旧机器的 Python 路径。

    cd D:\java_heima_learning\hmdp+langchain\ragAgent
    py -3.12 -m venv venv
    .\venv\Scripts\python.exe -m pip install --upgrade pip
    .\venv\Scripts\python.exe -m pip install -r requirements-fastapi.txt

实际文件名是 requirements.txt 和 requirements-fastapi.txt，不是
requestment.txt。requirements-fastapi.txt 会继续读取 requirements.txt，
并安装 FastAPI 依赖，因此安装前者即可。不必激活虚拟环境，直接调用
venv\Scripts\python.exe 可避免误用 Conda 或其他 Python。

模型使用通义千问/DashScope。启动前在当前 PowerShell 会话中设置有效密钥：

    $env:DASHSCOPE_API_KEY = '你的有效密钥'

不要把真实密钥写入 README、源码或提交到 Git。然后启动：

    .\venv\Scripts\python.exe -m uvicorn backend.main:app --host 127.0.0.1 --port 8000

启动命令必须在 ragAgent 根目录执行。开发时可在末尾加 --reload。
也可以运行 .\start.ps1 或 start.bat；启动脚本要求 venv 已经存在。
检查地址：http://127.0.0.1:8000/api/health；接口文档：/docs。
模型密钥无效时，health 仍可能正常，但实际聊天会失败。

Agent 数据是否自动创建：
  database/conversations.sqlite3：FastAPI 启动时创建文件及 chat_threads、
      chat_messages 表。
  database/checkpoints.db：Agent 短期记忆初始化时创建 SQLite 文件及表。
  chroma_db/：首次初始化向量库时创建 Chroma 存储和集合。
  data/ 与 md5.txt：知识文件目录及去重记录按代码需要创建。
  注意：创建空的 Chroma 集合不等于导入知识。知识文本需通过
      POST /api/knowledge/upload 上传；已有 chroma_db 若被忽略或删除，
      换机后也要重新上传/导入知识。上述路径相对 ragAgent 根目录。

四、启动 Java 后端（backend）

使用 IntelliJ IDEA 打开 backend/ 下的 pom.xml，等待 Maven 依赖同步，
运行 Spring Boot 主类 HmDianPingApplication。项目 pom.xml 设置 Java 21
源码级别；IDEA 的 JDK 和 Maven 配置需与依赖兼容。

从仓库首次下载时，将 backend/src/main/resources/application.example.yaml 复制为
application.yaml，并通过环境变量填写 DB_PASSWORD、REDIS_PASSWORD、
RABBITMQ_PASSWORD；真实 application.yaml 不纳入 Git。
启动前确认 backend/src/main/resources/application.yaml 中 MySQL、Redis、
RabbitMQ 及 agent.base-url 的地址、密码适用于当前机器；默认 Agent 地址为
http://127.0.0.1:8000。Java 默认监听 8081，若端口被占用，应停止旧进程
或更改 server.port。MySQL 表不会由 IDEA 或 MyBatis 自动建好。

如需 JAR，可在 backend/ 目录执行 mvn -DskipTests package，再运行
target/hm-dianping-0.0.1-SNAPSHOT.jar；target/ 其他内容不是部署必需文件。

五、启动前端（frontend）

Vue 开发源码在 frontend/frontend-vue3/，Nginx 实际读取的网页目录为
frontend/html/hmdp/。部署更新前端时，在 PowerShell 中执行：

    cd D:\java_heima_learning\hmdp+langchain\frontend\frontend-vue3
    npm install
    npm run build
    Copy-Item -Path .\dist\* -Destination ..\html\hmdp -Recurse -Force

初次安装依赖后，日常更新只需 npm run build 并复制 dist 内的内容；不要
复制 node_modules 或 src 到 Nginx 网页目录。public/ 中的静态资源会随
Vite 构建进入 dist/。

从 frontend/ 目录启动 Nginx，以便正确解析 conf/nginx.conf 中的
html/hmdp 等相对路径：

    cd D:\java_heima_learning\hmdp+langchain\frontend
    .\nginx.exe -t
    .\nginx.exe

浏览器打开 http://127.0.0.1:90。Nginx 配置监听 90，并将 /api/ 转发到
127.0.0.1:8081；直接打开 /orders 等 Vue 路由由 try_files 回退到 index.html。
更改 conf/nginx.conf 后执行 .\nginx.exe -t 和 .\nginx.exe -s reload。
仅替换前端构建文件通常不需要重启 Nginx；浏览器若仍显示旧页面可强制刷新。

六、建议启动顺序与排查

MySQL、Redis、RabbitMQ → Agent 8000 → Java 8081 → Nginx 90。
若客服聊天失败，先检查 Agent /api/health，再检查 Java 的 agent.base-url。
若页面接口 404，检查 Nginx /api 代理和 Java 是否加载了最新代码。
若店铺或订单无法查询，检查 MySQL hmdp 表结构及当前登录状态。
