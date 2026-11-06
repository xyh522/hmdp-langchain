**黑马点评与 RAG Agent 项目**

这是一个供学习和本地运行的店铺点评与优惠券系统。浏览器页面由 Vue 3 构建、Nginx 提供；Java 服务处理用户、店铺、优惠券和订单；Python Agent 提供演唱会购票知识问答。三个应用分别运行，通过 HTTP 接口连接。当前前端有普通用户和管理员入口，客服聊天经 Java 转发给 Agent。普通券和秒杀券订单支持模拟支付、主动取消、一分钟未支付自动取消和前端倒计时；模拟支付会更新订单状态及秒杀券数据库库存，不涉及真实扣款。

**项目结构**

```text
项目根目录/
├─ README.md                              项目说明、架构与启动方式
├─ backend/                               Spring Boot 业务后端
│  ├─ pom.xml                             Maven 依赖，包含 Caffeine 2.9.3
│  └─ src/
│     ├─ main/
│     │  ├─ java/com/hmdp/
│     │  │  ├─ HmDianPingApplication.java  后端启动入口
│     │  │  ├─ cache/
│     │  │  │  └─ SeckillVoucherListLocalCache.java  列表本地缓存、失效和广播发送方法
│     │  │  ├─ config/
│     │  │  │  ├─ RabbitMQConfig.java      下单、超时等待、死信与缓存广播配置
│     │  │  │  ├─ RedissonConfig.java      Redis 分布式锁客户端
│     │  │  │  ├─ MvcConfig.java           登录与管理权限拦截器配置
│     │  │  │  ├─ MybatisConfig.java       MyBatis-Plus 配置
│     │  │  │  └─ WebExceptionAdvice.java  接口异常处理
│     │  │  ├─ controller/
│     │  │  │  ├─ VoucherController.java       普通券、秒杀券列表与新增入口
│     │  │  │  ├─ VoucherOrderController.java  下单、模拟支付、取消及订单查询
│     │  │  │  ├─ AdminController.java         管理端接口
│     │  │  │  ├─ CustomerController.java      客服聊天与 Agent 转发入口
│     │  │  │  ├─ UserController.java          用户与登录接口
│     │  │  │  ├─ ShopController.java          店铺接口
│     │  │  │  ├─ BlogController.java          博客接口
│     │  │  │  └─ …                           店铺分类、关注、评论、上传接口
│     │  │  ├─ dto/
│     │  │  │  ├─ SeckillVoucherDTO.java   完整秒杀券展示信息，不含实时库存
│     │  │  │  ├─ VoucherOrderView.java    订单展示对象
│     │  │  │  ├─ UserDTO.java             登录用户信息
│     │  │  │  └─ …                       登录参数、统一结果与滚动分页结果
│     │  │  ├─ entity/
│     │  │  │  ├─ Voucher.java             优惠券基础信息
│     │  │  │  ├─ SeckillVoucher.java      秒杀时间与数据库库存
│     │  │  │  ├─ VoucherOrder.java        优惠券订单
│     │  │  │  └─ …                       用户、店铺、博客及客服实体
│     │  │  ├─ listener/
│     │  │  │  ├─ SeckillOrderListener.java         下单消费、超时关闭与死信处理
│     │  │  │  └─ SeckillVoucherCacheListener.java  接收跨 JVM 缓存失效通知
│     │  │  ├─ mapper/
│     │  │  │  ├─ VoucherMapper.java       普通券、秒杀券及管理列表查询
│     │  │  │  ├─ VoucherOrderMapper.java  订单数据访问
│     │  │  │  └─ …                       其他业务表的数据访问
│     │  │  ├─ service/
│     │  │  │  ├─ ISeckillVoucherService.java  秒杀券列表服务接口
│     │  │  │  ├─ MessageProducerService.java  订单消息生产接口
│     │  │  │  ├─ …                          其他业务服务接口
│     │  │  │  └─ impl/
│     │  │  │     ├─ SeckillVoucherServiceImpl.java  三级缓存查询与回填
│     │  │  │     ├─ VoucherServiceImpl.java         券查询、新增及提交后失效调用
│     │  │  │     ├─ VoucherOrderServiceImpl.java    抢购、订单落库、支付扣库存与取消释放预占
│     │  │  │     ├─ MessageProducerServiceImpl.java RabbitMQ 订单消息生产者
│     │  │  │     ├─ AdminManagementService.java     管理端店铺与优惠券业务
│     │  │  │     └─ …                              用户、店铺、博客及客服业务
│     │  │  └─ utils/
│     │  │     ├─ RedisConstants.java      Redis 缓存、库存及锁的键前缀
│     │  │     ├─ RedisIdWorker.java       订单 ID 生成
│     │  │     ├─ CacheClient.java         通用 Redis 缓存工具
│     │  │     ├─ UserHolder.java          当前线程登录用户
│     │  │     └─ …                       拦截器、锁、密码与校验工具
│     │  └─ resources/
│     │     ├─ application.example.yaml   本地配置模板
│     │     ├─ application.yaml           本机连接配置，不应公开提交
│     │     ├─ seckill.lua                Redis 库存与重复购买校验、预扣库存
│     │     ├─ unlock.lua                 Redis 锁释放脚本
│     │     ├─ mapper/
│     │     │  ├─ VoucherMapper.xml        普通券与秒杀券列表 SQL
│     │     │  └─ VoucherOrderMapper.xml   订单查询 SQL
│     │     ├─ db/                        旧版样例 SQL 与客服、角色迁移脚本
│     │     └─ hmdp-loadtest/              压测辅助资源
│     └─ test/java/com/hmdp/
│        ├─ cache/
│        │  └─ SeckillVoucherListCacheTest.java  列表缓存相关测试
│        └─ …                               其他后端测试
├─ frontend/                               Windows Nginx 与 Vue 3 项目
│  ├─ frontend-vue3/
│  │  ├─ package.json                      前端依赖与构建命令
│  │  ├─ vite.config.js                    开发服务与构建配置
│  │  ├─ src/
│  │  │  ├─ main.js、App.vue               Vue 应用入口
│  │  │  ├─ api/
│  │  │  │  ├─ http.js                     HTTP 客户端封装
│  │  │  │  ├─ catalog.js                  店铺、优惠券与购买接口
│  │  │  │  └─ auth.js、admin.js、blog.js、customer.js
│  │  │  ├─ components/
│  │  │  │  ├─ VoucherCard.vue             普通券、秒杀券卡片与购买按钮
│  │  │  │  ├─ CustomerChat.vue            客服聊天界面
│  │  │  │  ├─ SiteLayout.vue              页面公共布局
│  │  │  │  └─ pages/
│  │  │  │     ├─ ShopDetailPage.vue       店铺详情、普通券和秒杀券独立列表
│  │  │  │     ├─ OrdersPage.vue           订单列表、一分钟倒计时、模拟支付与取消
│  │  │  │     ├─ AdminPage.vue            管理端页面
│  │  │  │     ├─ HomePage.vue、ShopsPage.vue、SearchPage.vue
│  │  │  │     └─ …                       登录、个人中心及博客详情页面
│  │  │  ├─ router/index.js                Vue 路由
│  │  │  ├─ assets/                        公共页面与聊天样式
│  │  │  └─ utils/                         图片与客服会话辅助工具
│  │  ├─ public/                          构建时复制的静态资源
│  │  └─ dist/                            构建产物，不提交到 Git
│  ├─ conf/nginx.conf                     端口、静态目录及 /api 反向代理
│  └─ html/hmdp/                          Nginx 实际读取的前端构建文件
├─ ragAgent/                              FastAPI、LangChain Agent 与 RAG
│  ├─ README.md                           Agent 独立说明
│  ├─ requirements.txt                    Agent 核心依赖
│  ├─ requirements-fastapi.txt            FastAPI 服务依赖
│  ├─ start.ps1、start.bat                 本地启动脚本
│  ├─ backend/main.py                     聊天、健康检查与知识上传 API
│  ├─ agent/
│  │  ├─ react_agent.py                   Agent 编排入口
│  │  └─ tools/
│  │     ├─ ShortMemory.py                短期记忆
│  │     ├─ concert_tools.py              演唱会相关工具
│  │     ├─ agent_tools.py                Agent 工具
│  │     └─ middleware.py                 Agent 中间件
│  ├─ rag/
│  │  ├─ rag_service.py                   RAG 检索服务
│  │  └─ vector_store.py                  向量库访问
│  ├─ model/factory.py                    模型工厂
│  ├─ config/
│  │  └─ agent.yml、rag.yml、chroma.yml、prompts.yml
│  ├─ prompts/                            提示词资源
│  ├─ utils/                              配置、文件、日志与提示词加载工具
│  ├─ data/                               原始知识文件
│  ├─ database/                           SQLite 会话与 checkpoint，运行时创建
│  └─ chroma_db/                          Chroma 向量数据，运行时创建
└─ db/
   └─ 生成.sql                            当前 MySQL 业务空库建表脚本
```

上表展开主要业务文件，`…` 表示省略的同类文件；运行时目录不一定存在于全新克隆中。

`mall/` 是独立学习项目，不属于此系统，也不纳入本仓库。

**技术与职责**

| 层 | 当前技术 | 主要职责 |
| --- | --- | --- |
| 浏览器端 | Vue 3、Vue Router、Axios、Vite | 店铺浏览与搜索、博客、登录、客服、秒杀券订单、管理员店铺及优惠券页面 |
| 静态服务与代理 | Nginx for Windows | 提供 Vue 构建文件，将 `/api/` 转发至 Java；客服 SSE 关闭代理缓冲 |
| Java 后端 | Spring Boot 2.3.12、MyBatis-Plus、MySQL、Redis/Redisson、Caffeine 2.9.3、RabbitMQ | 业务接口、用户会话与角色检查、店铺及优惠券管理、秒杀资格判断、异步订单入库、支付与超时取消 |
| Agent 服务 | Python 3.12、FastAPI、LangChain/LangGraph、DashScope、Chroma、SQLite | 演唱会知识检索、模型调用、按 thread_id 隔离的对话记忆、SSE 回复 |

后端 `pom.xml` 的 `java.version` 为 **1.8**，这是源码/目标级别；实际运行 JDK 还需与项目依赖兼容。Agent 在 `config/rag.yml` 中使用 `qwen-max` 聊天模型和 `text-embedding-v4` 向量模型，调用模型需要有效的 DashScope 密钥。

**请求与数据流**

```text
浏览器 :90
  └─ Nginx /api → Spring Boot :8081
       ├─ MySQL hmdp：店铺、用户、优惠券、订单、客服会话与消息
       ├─ Redis：登录状态、缓存、秒杀资格与库存等
       ├─ RabbitMQ：秒杀订单异步写入、一分钟未支付订单的超时检查
       └─ /api/chat → FastAPI :8000 → Agent → DashScope / Chroma
                                  └─ SSE 分片返回 Java，再返回浏览器
```

浏览器不直接传入可决定订单归属的用户 ID；Java 根据当前登录用户读取其订单。客服由 Java 传 `message`、`user_id`、`thread_id` 给 Agent；Agent 的短期记忆按 `thread_id` 隔离。秒杀请求先完成 Redis 资格判断，再经 RabbitMQ 异步写入 MySQL，因此接口返回与订单可查询之间可能有短暂延迟。

**秒杀券列表与三级缓存**

秒杀券展示与抢购流程分离：店铺页面分别请求普通券列表和秒杀券列表；Caffeine 只缓存店铺下的完整秒杀券信息列表，不缓存实时库存，不参与库存扣减和订单创建。

| 接口（Java 路径） | 用途 |
| --- | --- |
| `GET /voucher/list/{shopId}` | 普通券列表，查询上架的普通券，不经过 Caffeine |
| `GET /voucher/seckill/list/{shopId}` | 秒杀券列表，按 Caffeine → Redis → MySQL 加载 |
| `POST /voucher-order/ordinary/{id}` | 普通券购买入口 |
| `POST /voucher-order/seckill/{id}` | 现有秒杀抢购入口，Redis Lua 资格校验与扣库存、RabbitMQ 异步落库 |

浏览器通过 Nginx 调用时，以上路径带 `/api` 前缀。券卡片展示标题、描述、使用规则、金额及秒杀开始/结束时间；列表 DTO 不包含实时库存。秒杀列表查询上架且尚未结束的活动，也包含尚未开始的活动。

```text
查询店铺秒杀券列表
  ├─ Caffeine：shopId → List<SeckillVoucherDTO>
  ├─ 未命中 → Redis 店铺索引 → 批量读取每张券的完整 JSON
  └─ 索引或详情不可用 → MySQL 联表查询
                         → 回填 Redis 券详情、店铺索引
                         → 回填当前 JVM 的 Caffeine
```

| 缓存或锁 | 内容 | 有效期／用途 |
| --- | --- | --- |
| Caffeine：`shopId` | 完整秒杀券 DTO 列表，最多 1,000 个店铺条目 | 到列表中最早一张券的结束时间；空列表 60 秒 |
| Redis：`seckill:voucher:list:{shopId}` | JSON 券 ID 数组，例如 `[19,20]` | 到列表中最晚一张券的结束时间；空数组 `[]` 保留 60 秒 |
| Redis：`seckill:voucher:{voucherId}` | 单张券完整 DTO JSON，包含描述、规则、金额、开始和结束时间等 | 各自到该券的结束时间 |
| Redisson：`lock:seckill:voucher:list:{shopId}` | 店铺粒度的分布式锁 | 协调列表加载、重建与缓存失效 |
| Redis：`seckill:stock:{voucherId}` | 抢购使用的实时库存 | 由原抢购流程管理，不属于展示缓存清理范围 |

表中的花括号表示变量占位，实际键如 `seckill:voucher:list:1`、`seckill:voucher:19`，不含花括号。每张券独立设置 TTL，因此不同活动可在不同时间到期。索引缺失、任一详情缺失/过期或格式不符合当前 DTO 时，一次联表查询重建该店铺列表，而不是逐张查数据库。

主要代码位置：

| 文件（相对项目根目录） | 职责 |
| --- | --- |
| `backend/src/main/java/com/hmdp/cache/SeckillVoucherListLocalCache.java` | Caffeine 容量与动态过期、事务提交后失效、Redis 清理及广播发送方法 |
| `backend/src/main/java/com/hmdp/service/impl/SeckillVoucherServiceImpl.java` | Redis 索引与详情读取、数据库回源、逐券 TTL 回填及返回 DTO 副本 |
| `backend/src/main/java/com/hmdp/dto/SeckillVoucherDTO.java` | 展示用完整券信息，不包含实时库存 |
| `backend/src/main/java/com/hmdp/utils/RedisConstants.java` | 列表索引、券详情、重建锁的键前缀 |
| `backend/src/main/resources/mapper/VoucherMapper.xml` | 普通券与秒杀券的独立查询；通过 `tb_voucher.id = tb_seckill_voucher.voucher_id` 联表 |
| `backend/src/main/java/com/hmdp/config/RabbitMQConfig.java` | 缓存广播交换机、每 JVM 独立匿名队列及绑定 |
| `backend/src/main/java/com/hmdp/listener/SeckillVoucherCacheListener.java` | 接收券 ID 消息体和店铺 ID 消息头，执行缓存失效，不再次广播 |
| `frontend/frontend-vue3/src/components/VoucherCard.vue` | 普通券、秒杀券卡片展示和购买按钮 |
| `frontend/frontend-vue3/src/components/pages/ShopDetailPage.vue` | 分别加载普通券、秒杀券列表，调用各自购买入口 |

写入端已有的新增、删除秒杀券路径调用 `invalidateAfterCommit(shopId, voucherId)`：数据库提交后删除 Redis 店铺索引、受影响的券详情及当前 JVM 列表；回滚时不执行提交回调。后续修改接口也需要显式调用此入口，直接修改数据库不会自动触发缓存清理。

**广播当前接入状态：** 已声明 `seckill.cache.invalidation.exchange` Fanout 交换机，并实现每 JVM 独立匿名队列、消费者和 `publishInvalidation()` 发送方法。但当前 `invalidateAfterCommit()` 仍直接调用 `invalidateNow()`，没有调用发送方法，因此新增/删除尚不会自动广播。跨 JVM 失效链路仍需接通，不能仅凭存在消费者就认定广播已生效。匿名队列使用 `setMasterLocator(null)` 移除旧版 Spring AMQP 默认的弃用参数，以避免 RabbitMQ 拒绝 `x-queue-master-locator` 队列声明。

展示缓存不负责库存校验或售罄自动隐藏。订单及库存处理见下文。

**订单、支付与库存**

普通券直接创建待支付订单。秒杀券先通过 Redis Lua 检查库存和重复购买资格、预扣 Redis 库存，再经 RabbitMQ 异步写入订单，创建订单时不扣 MySQL 库存。订单支付期限为 `create_time + 1 分钟`，秒杀订单的创建时间在消费者落库时设置。

| Java 接口路径 | 用途 |
| --- | --- |
| `POST /voucher-order/ordinary/{id}` | 创建普通券待支付订单 |
| `POST /voucher-order/seckill/{id}` | 提交秒杀下单请求，返回订单 ID，不代表已支付 |
| `GET /voucher-order/list?current=1` | 查询当前用户订单列表，包含 `status`、`createTime` 等信息 |
| `GET /voucher-order/{id}` | 查询当前用户的单个订单 |
| `POST /voucher-order/{id}/pay` | 模拟支付，仅允许本人、待支付且创建未满一分钟的订单 |
| `POST /voucher-order/{id}/cancel` | 主动取消本人待支付订单 |

通过 Nginx 访问时接口带 `/api` 前缀。支付不涉及真实扣款，也不调用 RabbitMQ。

订单状态使用现有 `status` 字段：`1` 待支付、`2` 已支付、`4` 已取消。主动取消和超时取消共用状态 `4`，页面显示“支付失败（已取消）”。`3` 已核销、`5` 退款中、`6` 已退款为预留状态，尚无对应业务流程。

| 秒杀订单阶段 | MySQL `tb_seckill_voucher.stock` | Redis 库存与资格 |
| --- | --- | --- |
| 下单 | 不修改库存，只创建订单 | 库存减一，记录用户购买资格 |
| 模拟支付成功 | 库存减一，与订单 `1 → 2` 在同一事务提交 | 不重复扣减，保留购买资格 |
| 主动取消或超时取消 | 不修改库存，订单 `1 → 4` | 库存加一，移除购买资格 |
| 已支付或已取消订单收到到期消息 | 不修改 | 不修改 |

支付扣库存要求 `stock > 0`；扣减失败会回滚支付状态。取消使用 `WHERE id = ? AND status = 1` 条件更新，只有成功变为已取消才释放 Redis 预占。普通券不涉及秒杀库存。MySQL 库存没有扣除未支付预占，因此不能直接覆盖运行中的 Redis 可售库存。

Redis 中 `seckill:stock:{voucherId}` 为整数 String，保存可售库存；`seckill:order:{voucherId}` 为用户 ID Set，用于重复购买校验。`seckill.lua` 还写入 `stream.orders`，但对应的消费代码已注释，订单实际由 RabbitMQ 处理，该 Stream 不承担故障补偿。

**超时消息与页面倒计时**

订单事务提交后，`sendTimeoutAfterCommit()` 将订单 ID 发送至等待队列。相关队列复用 `seckill.order.exchange` 直连交换机：

| 队列 | 路由键 | 职责 |
| --- | --- | --- |
| `seckill.order.queue` | `seckill.order` | 立即消费，创建秒杀订单 |
| `seckill.order.timeout.wait.1m.queue` | `order.wait.1m` | 无消费者，消息 TTL 为 `60000` 毫秒 |
| `seckill.order.timeout.queue` | `order.timeout` | 消费到期消息，检查并取消待支付订单 |

```text
订单事务提交 → 发送 orderId → 等待队列停留一分钟
  → 过期消息通过死信路由进入超时处理队列
  → 查询订单：待支付则取消并释放 Redis；其他状态或订单不存在则结束
  → 方法正常返回，Spring 容器发送 ACK
```

超时监听器显式配置 `ackMode="AUTO"`，覆盖全局手动确认设置，不需要再手动 ACK。提前支付或主动取消不修改等待中的消息；消息到期后根据数据库最新状态决定是否需要处理。

`OrdersPage.vue` 每秒按 `createTime + 60 * 1000 - Date.now()` 计算剩余时间，刷新页面不重置。支付成功后隐藏倒计时；归零后禁用支付，每三秒读取订单列表，后端仍待支付时显示“正在确认订单状态”，读到已取消才显示支付失败。前端不查询队列剩余 TTL，也不在归零时主动取消订单。浏览器时钟偏差会影响显示，支付资格由后端独立校验。

**实现边界**

- 队列 TTL 从消息入队开始，支付期限从订单创建时间开始，提交、投递和消费延迟可能让实际关闭稍晚。
- 支付状态与 MySQL 库存扣减在同一事务中；取消时的数据库状态更新和 Redis 释放不属于同一事务，尚无跨系统故障补偿。
- 超时队列未配置失败死信出口，监听器未启用自动重试，且 `default-requeue-rejected=false`，处理异常不保证重试。事务提交后的超时消息发送也没有补偿扫描。
- 下单消费者仍有内层捕获异常、自定义重试计数未递增的问题；下单失败死信消费者只记录日志并确认消息，不负责释放预占。

RabbitMQ 参考：[消息 TTL](https://www.rabbitmq.com/docs/ttl)、[队列声明](https://www.rabbitmq.com/docs/queues)、[死信路由](https://www.rabbitmq.com/docs/dlx)。

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

MySQL 库和表**不会**由 IDEA、MyBatis 或 Java 服务自动创建。Redis 与 RabbitMQ 服务、RabbitMQ 用户及 virtual-host 也需事先准备；Java 的 `RabbitMQConfig` 会声明秒杀订单、失败死信、一分钟超时等待与处理队列，以及缓存失效广播交换机和每实例独立的临时队列。连接地址在 `backend/src/main/resources/application.yaml`，换机器后应按实际环境修改。

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

订单有效期需要同时配置三处：`RabbitMQConfig.java` 的 `x-message-ttl=60000`（毫秒）、`VoucherOrderServiceImpl.payOrder()` 的 `minusMinutes(1)`、`OrdersPage.vue` 的 `paymentWindow=60 * 1000`。后端修改后重启服务，前端修改后重新构建并部署静态文件。

变更库存扣减策略前，应先暂停新下单并处理完已有待支付订单，避免将不同扣减策略混用于同一批订单。

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

- RabbitMQ `406 PRECONDITION_FAILED` 且提示 `x-message-ttl` 不一致：同名队列的声明参数必须一致，重启不会覆盖已有 TTL。核对队列名称、路由和配置；当前等待队列为 `seckill.order.timeout.wait.1m.queue`，TTL 为 `60000` 毫秒。调整参数时可使用不同队列名和匹配路由；已有队列需先处理完消息再清理。
- `SerializedLambdaMeta` / `InaccessibleObjectException`：检查旧版 MyBatis-Plus 与运行 JDK 的反射兼容性。支付、取消接口使用固定列名的普通 `UpdateWrapper`；确认构建结果与运行代码一致。
- 页面倒计时或状态未更新：确认前端产物已复制到 `frontend/html/hmdp`，强制刷新浏览器；倒计时按订单原创建时间计算，可新建订单检查完整一分钟流程。
