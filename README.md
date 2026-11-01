# AI Enterprise Knowledge Platform

基于 **RAG + Workflow + Intent Orchestration** 架构的企业级 AI 知识助手平台。

面向企业内部技术文档、Wiki、接口文档等知识管理场景，构建从 **文档解析 → 知识入库 → 混合检索 → LLM 生成 → 引用溯源** 的完整知识问答流程，并通过 Workflow、Intent Orchestration、短期记忆和异步任务机制支持多类型用户请求。

项目重点关注 RAG 系统的工程落地，包括混合检索、任务编排、流式响应、可观测性与检索效果评测。

---

## 🌐Online Demo

- **在线体验：** [http://106.55.103.123](http://106.55.103.123)
- **项目状态：** Demo 环境，暂未开放注册
- **体验账号：** `grest01`
- **体验密码：** `46r567656757fg7tf6g7g676f6f7`

---

## ✨核心功能

### 1. 知识库与文档管理

- 支持创建和管理多个知识库。
- 支持 PDF、Markdown、TXT 文档上传。
- 基于文档解析与递归切分，将文档转换为 Chunk。
- 切块内容附带页码等 Metadata，支持后续检索与引用定位。
- 文档删除时同步清理数据库记录、向量索引和本地文件。

### 2. RAG 混合检索

采用 **Vector Retrieval + BM25 + RRF** 的混合检索方案：

- 向量检索：基于 `text-embedding-v3` 生成 Embedding，通过 Redis Vector Search 检索语义相关内容。
- BM25 检索：基于 RediSearch 构建关键词检索索引，并针对中文检索进行优化。
- RRF 融合：对向量检索和 BM25 检索结果进行融合排序。
- Metadata Filter：两路检索均按照 `knowledgeId` 进行知识库范围过滤。
- 融合后分数过滤：先计算 RRF 分数，再根据原始向量分数和 BM25 分数判断是否保留结果。
- 默认在线召回 Top10 条结果，评测使用前 5 条结果计分。

### 3. Intent Orchestration 与 Workflow

通过意图识别和任务编排，将用户请求分发至不同处理流程：

| Intent    | 处理方式             |
| --------- | ---------------- |
| `RAG_QA`  | 检索知识库并生成带引用的回答   |
| `SUMMARY` | 根据总结范围执行文档或知识库总结 |
| `CHAT`    | 普通对话，不执行知识库检索    |

其中：

- `SUMMARY` 支持 `DOCUMENT` 和 `KNOWLEDGE_BASE` 两种总结范围。
- `SUMMARY_DOCUMENT` 是待澄清任务类型，不是独立 Intent。

具体处理方式：

- **Intent Recognition**：识别用户请求类型，选择对应处理流程。
- **RAG Workflow**：执行查询改写、混合检索、上下文构建和回答生成。
- **Summary Service**：处理单文档或整个知识库的总结请求。
- **CasualChatService**：处理不需要知识库检索的普通对话。
- **Clarification**：当任务缺少必要参数时，向用户发起澄清。
- **PendingTask**：通过 Redis 保存待补充任务，支持用户补充信息后继续执行。

### 4. Memory 与 SSE

- **Short-term Memory**：基于 Redis 保存用户会话短期上下文。
- **Context Management**：通过滑动窗口与历史摘要控制上下文长度。
- **SSE Streaming**：支持聊天任务异步提交和流式响应。
- **Task Management**：通过 taskId 管理异步任务及其 SSE 连接。

当前 RAG 问答和普通聊天支持模型流式输出；总结、意图识别和澄清判断等流程使用非流式模型调用，并通过 SSE 推送进度和最终结果。

### 5. RAG Evaluation

构建独立的 RAG 检索评测模块，用于分析检索效果和定位 Badcase。

- 支持上传测试集并指定知识库，也支持直接运行固定 Golden Set。
- 保存评测运行快照和逐 Chunk 检索轨迹。
- 支持评测历史分页、两次运行结果对比。
- 提供用例详情页，展示最终召回结果、检索过程和逐 Chunk 检索链路。
- 支持展开标准 Chunk 原文，辅助判断召回是否命中。
- 支持对固定 Golden Set 中指定 Badcase 进行诊断。

评测指标包括：

- Hit@5
- Recall@5
- MRR
- Average Latency
- P95 Latency

**指标口径按测试集粒度区分：**

| 测试集类型          | Hit@5                      | Recall@5                     | 判断依据                                     |
| -------------- | -------------------------- | ---------------------------- | ---------------------------------------- |
| 文档级 Golden Set | 是否召回任一期望文档                 | 命中为 1，未命中为 0                 | 前 5 个结果中的文档 ID                           |
| Chunk 级测试集     | 前 5 个 Chunk 是否命中任一标准 Chunk | 前 5 个 Chunk 覆盖的标准 Chunk 数量占比 | `groundTruthChunkIds` 与实际召回 Chunk ID 的交集 |

对于带有 `groundTruthChunkIds` 的 Chunk 级用例：

- Hit@5：前 5 个召回 Chunk 中，只要存在一个标准 Chunk，即判定命中。
- Recall@5：前 5 个召回 Chunk 命中的标准 Chunk 数量，除以标准 Chunk 总数。
- 文档 ID 不参与这套 Chunk 级命中判断。

因此，文档级 Golden Set 中 Hit@5 与 Recall@5 的结果相同；Chunk 级测试集中的两项指标则可能不同。

---

## 🏗️系统架构
### 整体架构

```text
                         User
                          │
                         Vue3
                          │
                   Spring Boot API
                          │
                  Intent Orchestrator
                          │
             ┌────────────┼────────────┐
             ▼            ▼            ▼
          RAG_QA        SUMMARY        CHAT
             │            │            │
             ▼            ▼            ▼
        RAG Workflow  Summary Service  CasualChatService
             │
      ┌──────┼────────┬────────┬──────────┐
      ▼      ▼        ▼        ▼          ▼
   Rewrite Retrieve  Prompt   LLM      Citation
    Node    Node     Node     Node       Node
             │                   │
        ┌────┴────┐              ▼
        ▼         ▼         Trace System
      Vector     BM25       （贯穿全链路）
    Retrieval Retrieval
        │         │
        └────┬────┘
             ▼
         RRF Fusion
```

### RAG 核心流程
```
User Query
    |
    ▼
Query Rewrite
    |
    ▼
Hybrid Retrieval
    |
    +---- Vector Retrieval
    |
    +---- BM25 Retrieval
    |
    ▼
RRF Fusion
    |
    ▼
Score Filtering
    |
    ▼
Context Assembly
    |
    ▼
Prompt Construction
    |
    ▼
LLM Generation
    |
    ▼
Answer + References

```

Memory 在 Workflow 执行前后完成上下文加载与持久化，不作为 RAG Workflow 中的独立业务节点。

---

## 🧪Engineering Practices

### 1. 中文 BM25 混合检索

针对中文文档中关键词检索效果不稳定的问题，在原有向量检索基础上增加独立的中文 BM25 索引。

**索引设计**

- 独立索引名称：`embedding-bm25-zh`。
- 使用 RediSearch 的 `LANGUAGE chinese` 配置。
- 复用原有 `embedding:` 记录，不额外存储一份正文。
- 向量索引与 BM25 索引分别执行检索，最终通过 RRF 融合结果。

**中文查询处理**

1. 对用户问句按照标点进行切分。
2. 纯问句不直接提交 BM25 查询。
3. 通过中文查询解释流程提取可用于检索的词项。
4. 解释成功后，将中文词项组织为 OR 查询并提交 BM25。

**检索参数**

| 参数                      | 配置   |
| ----------------------- | ---- |
| Vector Score Threshold  | 0.75 |
| BM25 Score Threshold    | 1.0  |
| Online Retrieval TopK   | 10   |
| Evaluation Scoring TopK | 5    |

**RRF 与分数过滤**

两路检索均应用 `knowledgeId` Metadata Filter，在各自完成召回后进入 RRF 融合。

分数阈值过滤发生在 RRF 计算之后，而不是两路召回阶段：

1. 根据向量检索和 BM25 检索的排名计算 RRF 分数。
2. 根据原始向量分数和 BM25 分数判断结果是否保留。
3. 过滤完成后，按 RRF 分数排序。
4. 在线场景取 Top10，评测场景使用前 5 条结果计分。

具体过滤规则：

| 召回情况              | 保留规则                |
| ----------------- | ------------------- |
| 同时存在向量分数和 BM25 分数 | 只有两路原始分数都低于各自阈值时才丢弃 |
| 只有向量分数            | 向量分数低于 0.75 时丢弃     |
| 只有 BM25 分数        | BM25 分数低于 1.0 时丢弃   |
| 分数等于阈值            | 保留                  |

双路都有分数时，不能因为其中一路低于阈值就直接丢弃结果。

### 2. Intent Orchestration 与 PendingTask

将用户请求拆分为不同 Intent，并根据任务类型选择对应处理流程。

当前支持：

- `RAG_QA`：知识库问答。
- `SUMMARY`：文档或知识库总结。
- `CHAT`：普通对话。

对于缺少必要信息的请求，系统不会直接执行不完整任务，而是通过 Clarification 向用户询问缺失参数，并使用 PendingTask 暂存任务状态。

PendingTask 存储于 Redis，设置 TTL 为 30 分钟。用户补充信息后，可以继续执行原任务。

总结请求由 `SUMMARY` 处理，范围包括 `DOCUMENT` 和 `KNOWLEDGE_BASE`；`SUMMARY_DOCUMENT` 用于标识待澄清的文档总结任务类型。

### 3. SSE 异步任务与流式响应

采用任务提交与流式订阅分离的方式：
```
POST /api/v1/chat
        |
        v
    Return taskId
        |
        v
GET /api/v1/chat/stream/{taskId}
        |
        v
    SSE Events
```

- `POST /api/v1/chat`：提交聊天任务并返回 taskId。
- `GET /api/v1/chat/stream/{taskId}`：建立 SSE 连接，接收任务进度和结果。
- RAG_QA、CHAT：支持 qwen-plus 流式输出。
- SUMMARY、意图识别和澄清判断：使用非流式模型调用，通过 SSE 推送进度及最终结果。
- 成功完成后，complete 事件返回最终回答和 references。

### 4. LLM Fallback

针对非流式回答生成流程实现模型降级机制：
```
qwen-plus
    |
    v
First Attempt
    |
    +---- Success --> Return Answer
    |
    v
Retry Once
    |
    +---- Success --> Return Answer
    |
    v
qwen-turbo Fallback
    |
    v
Return Answer
```

- 主模型使用 `qwen-plus`。
- 首次调用失败后重试一次。
- 两次调用均失败后，降级至 `qwen-turbo`。
- Trace 记录模型使用情况及降级原因。

当前流式聊天流程未实现流式模型 Fallback：qwen-plus 流式调用失败后发送 error 事件并关闭 SSE，不发送 complete 事件。

### 5. Trace Observability

通过 Trace 记录请求执行过程中的节点信息，便于定位执行异常和分析耗时。

- 记录节点执行耗时与状态。
- 记录模型使用情况 `model_used`。
- 记录是否发生模型降级及降级原因。
- Trace 数据持久化至 MySQL `trace_span` 表。

Trace 查询接口：
```
GET /api/v1/traces/{traceId}
```

当前提供 Trace 查询接口和数据落库能力，尚未提供独立的前端 Trace 可视化页面或侧边栏入口。

### 6. RAG Evaluation

评测模块不仅支持固定 Golden Set，也支持上传测试集进行独立评测。

**测试集与评测运行**

- 支持获取评测数据集。
- 支持上传测试集并指定目标知识库。
- 不上传测试集时，运行评测仍执行固定 Golden Set。
- 保存每次评测的运行快照至 `rag_eval_run`。
- 保存逐 Chunk 检索轨迹至 `rag_eval_retrieval_trace`。
- Markdown 评测报告保存在 `docs/eval/result/`。

**评测指标**

| 指标          | 含义                         |
| ----------- | -------------------------- |
| Hit@5       | 根据测试集粒度判断前 5 个结果是否命中标准答案   |
| Recall@5    | 根据测试集粒度计算前 5 个结果对标准答案的召回情况 |
| MRR         | 首个相关结果的倒数排名                |
| Avg Latency | 平均检索耗时                     |
| P95 Latency | 95 分位检索耗时                  |

Hit@5 和 Recall@5 的计算方式根据测试集粒度区分：

**文档级 Golden Set**

按照文档 ID 判断前 5 个结果是否包含任一期望文档。

- Hit@5：命中任一期望文档时为 1，否则为 0。
- Recall@5：命中任一期望文档时为 1，否则为 0。

当前文档级口径下，Hit@5 与 Recall@5 结果相同。

**Chunk 级测试集**

当测试用例提供 `groundTruthChunkIds` 时，按照 Chunk ID 计算：

- Hit@5：前 5 个召回 Chunk 与标准 Chunk 集合是否存在交集。
- Recall@5：前 5 个召回 Chunk 覆盖的标准 Chunk 数量，占标准 Chunk 总数的比例。

Chunk 级评测不使用文档 ID 判断命中。因此，两种粒度下的 Hit@5 和 Recall@5 不能统一描述为始终相同。

**评测历史与对比**

- 支持评测历史分页查询。
- 支持选择两次评测运行结果进行对比。
- 前端分别读取两次运行详情，再进行结果对比。
- 通过运行快照保留每次评测的结果，便于分析不同检索配置下的效果变化。

**用例详情**

用例详情页面：
```
/eval/runs/:runId/cases/:caseId
```

这是前端路由，页面数据来自对应评测运行的详情接口：
```
GET /api/v1/eval/runs/{id}
```

页面分为三个页签，分别承担不同职责：

| 页签   | 展示内容                                     | 作用                 |
| ---- | ---------------------------------------- | ------------------ |
| 检索结果 | 最终召回结果与命中标记                              | 判断最终检索是否命中         |
| 检索过程 | Query、Hybrid Retrieval、召回结果、Ground Truth | 查看一次检索执行的步骤摘要      |
| 检索链路 | 向量排名、BM25 排名、RRF、过滤原因、最终排名               | 追踪逐 Chunk 的检索与排序过程 |

**检索结果**

这是最终召回与命中判断的展示区域：

- Chunk 级用例：对照标准 Chunk 和实际召回 Chunk，展示命中标记，并支持展开标准 Chunk 原文。
- 文档级用例：对照期望文档和实际召回文档。
- 展示最终召回列表及其命中情况。

**检索过程**

以步骤摘要的形式展示一次检索执行过程：

- Query
- Hybrid Retrieval
- 召回结果
- Ground Truth

该页签用于理解检索流程，不展示逐 Chunk 排名。

**检索链路**

展示逐 Chunk 的完整检索轨迹：

- 向量检索排名。
- BM25 检索排名。
- RRF 融合过程。
- 过滤原因。
- 最终排名。
- 期望文档在检索链路中的阶段情况。

检索链路中的阶段标记针对期望文档，而不是标准 Chunk。通过检索链路，可以进一步判断期望文档是否进入向量召回、BM25 召回、RRF 融合，以及是否在后续过滤或排序阶段丢失。

**Badcase 诊断**

支持通过 `--diagnose-badcase` 对固定 Golden Set 中指定用例进行诊断：

- Case 16
- Case 29
- Case 34
- Case 37

该诊断流程用于分析指定 Badcase，不写入正式评测历史。

青屿空港相关测试材料、测试集和排查文档保存在 `docs/eval/`。

当前评测重点是检索效果，不对 LLM 最终生成质量进行评估。

---

## 🛠️ 技术栈

| Category            | Technology               |
| ------------------- | ------------------------ |
| Frontend            | Vue 3, Vite, TypeScript  |
| UI                  | Element Plus             |
| State Management    | Pinia                    |
| Routing             | Vue Router               |
| HTTP Client         | Axios                    |
| Backend             | Java 17, Spring Boot 3   |
| ORM                 | MyBatis Plus             |
| LLM Framework       | LangChain4j              |
| LLM                 | DashScope Qwen           |
| Embedding           | text-embedding-v3        |
| Vector Database     | Redis Stack / RediSearch |
| Full-text Search    | RediSearch BM25          |
| Relational Database | MySQL                    |
| Cache / Session     | Redis                    |
| API Documentation   | SpringDoc                |
| Authentication      | JWT                      |
| Testing             | Postman                  |

---

## 📂Project Structure
```
AiConsultant/
│
├── backend/
│   └── AiConsultant/
│       └── src/main/java/com/zx/consultant/
│           ├── common/          # Common components
│           ├── chat/            # Chat and SSE
│           ├── document/        # Document parsing and management
│           ├── knowledge/       # Knowledge base management
│           ├── llm/             # LLM integration
│           ├── memory/          # Short-term conversation memory
│           ├── orchestrator/    # Intent orchestration
│           ├── pending/         # Pending tasks and clarification
│           ├── rag/             # Retrieval and RAG pipeline
│           ├── summary/         # Document and knowledge base summary
│           ├── trace/           # Trace observability
│           ├── user/            # User and authentication
│           └── workflow/        # Workflow execution
│
├── frontend/
│   └── src/
│       ├── views/               # Page views
│       ├── components/          # Reusable components
│       ├── api/                 # API requests
│       ├── stores/              # Pinia stores
│       ├── router/              # Vue Router
│       ├── layouts/             # Page layouts
│       ├── composables/          # Composition API logic
│       ├── types/               # TypeScript types
│       └── utils/               # Utilities
│
├── docs/
│   ├── eval/                    # Evaluation datasets and reports
│   │   └── result/              # Markdown evaluation reports
│   └── ...
│
└── README.md
```

---

## 🚀 Deployment & Quick Start

### Requirements

- Java 17
- Maven
- Node.js
- MySQL
- Redis Stack with RediSearch
- DashScope API Key

### 1. Clone the Repository
```
git clone https://github.com/zanxuan/AiConsultant.git
cd AiConsultant
```

### 2. Backend Configuration

进入后端目录：
```
cd backend/AiConsultant
```

复制环境变量模板：
```
cp .env.example .env
```

配置必要环境变量：
```
API_KEY=your_dashscope_api_key
EMBEDDING_API_KEY=your_embedding_api_key

DB_USERNAME=your_database_username
DB_PASSWORD=your_database_password

JWT_SECRET_KEY=your_jwt_secret
```

项目通过 `LocalDotenvBootstrap` 加载本地 `.env` 文件。

本地开发默认使用 `application.yml`，不需要设置 `SPRING_PROFILES_ACTIVE=prod`。

启动后端：
```
mvn spring-boot:run
```

### 3. Frontend Configuration

进入前端目录：
```
cd frontend
```

安装依赖：
```
npm install
```

启动开发服务器：
```
npm run dev
```

### 4. Production Deployment

生产环境通过：
```
SPRING_PROFILES_ACTIVE=prod
```

启用 `application-prod.yml`。

生产环境敏感配置通过服务器环境变量注入，不依赖仓库中的 `.env` 文件。

当前 Demo 部署环境：

- Ubuntu 22.04 LTS
- Backend：Spring Boot，端口 8087
- Frontend：Vue 3 + Vite
- Database：MySQL
- Cache / Search：Redis Stack

线上环境通过 Nginx 转发 `/api/` 请求至后端服务。

当前仓库未提供 Dockerfile、Docker Compose 和 Nginx 配置文件，Docker Compose 部署尚未纳入仓库。

---

## API Examples

### Chat

提交聊天任务：

```http
POST /api/v1/chat
Content-Type: application/json
```

提交成功后返回 `taskId`，通过 SSE 获取任务执行过程：

```http
GET /api/v1/chat/stream/{taskId}
```

### Trace

根据 Trace ID 查询请求执行链路：

```http
GET /api/v1/traces/{traceId}
```

### Evaluation

提交评测任务：

```http
POST /api/v1/eval/run
```

支持上传自定义测试集，也可以直接运行固定 Golden Set。

查询评测历史及运行详情：

```http
GET /api/v1/eval/runs
GET /api/v1/eval/runs/{id}
```

查看指定用例的 Chunk 检索轨迹：

```http
GET /api/v1/eval/runs/{id}/cases/{caseId}/retrieval-trace
```

---

## 🗺️ Roadmap

### V1.0 — RAG 基础能力
- [x] 用户认证与知识库管理
- [x] 文档上传、解析与切块
- [x] Embedding 与向量检索
- [x] RAG 问答与 Citation

### V2.5 — RAG Engineering & Workflow
- [x] Vector + BM25 混合检索
- [x] 中文 BM25 检索优化
- [x] RRF 融合排序
- [x] Intent Orchestration
- [x] Workflow 执行
- [x] Document / Knowledge Base Summary
- [x] Clarification 与 PendingTask
- [x] Redis Short-term Memory
- [x] SSE 异步任务与流式响应
- [x] LLM Fallback
- [x] Trace 数据记录与查询接口
- [x] RAG Evaluation
- [x] 评测历史与运行对比
- [x] Chunk 级检索轨迹
- [x] 用例详情与标准 Chunk 原文展示
- [x] Badcase 诊断
- [x] 游客浏览


### V3 — Planned

- [ ] Long-term Memory
- [ ] Semantic Chunking
- [ ] Hybrid Retrieval Optimization
- [ ] Online Evaluation
- [ ] Prompt Management
- [ ] Agent Workflow
- [ ] Docker Compose Deployment

---

## Notes

本项目主要用于探索企业知识库问答系统中的 RAG 工程实践，重点包括混合检索、任务编排、异步交互、可观测性和检索评测。

项目中的评测数据用于检索效果分析，测试结果受测试集内容、文档切块版本及检索配置影响。不同测试集或不同切块版本之间的结果不应直接视为同一评测基准。

