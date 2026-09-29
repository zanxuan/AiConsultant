# AI Enterprise Knowledge Platform

基于 **RAG + Workflow + Intent Orchestration** 架构的企业级 AI 知识助手平台。

面向企业内部技术文档、Wiki、接口文档等知识管理场景，构建从**文档解析 → 知识检索 → 多轮上下文管理 → 大模型生成 → 来源追踪 → 链路观测 → 检索评测**的完整 AI 应用链路。

项目当前重点围绕 RAG 应用工程化展开，并进一步加入 Intent Orchestration、异步任务、SSE 流式交互、Summary / Clarification、Trace 与 Evaluation 等能力。

---

## 🌐 Online Demo

项目已完成云服务器部署。

访问地址：

[Online Demo（点击访问）](http://106.55.103.123)

由于系统依赖第三方 LLM API 服务，Demo 暂未开放公开注册。

Demo 体验账号：grest01  / Demo 密码：46r567656757fg7tf6g7g676f6f7

---

## 📖 项目介绍

在企业研发过程中，大量技术文档、接口文档、Wiki 等知识分散存储，传统关键词搜索难以理解用户真实语义。本项目面向企业内部知识管理场景，构建了一套基于 **RAG（Retrieval-Augmented Generation）** 的 AI 知识助手系统。

系统在请求入口增加 **Intent Orchestrator**，根据用户请求选择不同业务链路：

```text
User Query
    │
    ▼
Intent Orchestrator
    │
    ├───────────────┬────────────────┐
    ▼               ▼                ▼
 RAG_QA          SUMMARY           CHAT
    │               │                │
    ▼               ▼                ▼
RAG Workflow   Summary Service  CasualChatService
```

其中 `SUMMARY` 支持单文档总结与整库总结；单文档目标不明确时，通过 Clarification / PendingTask 等待用户补充信息后继续执行。

RAG_QA 基于 **Memory + Query Rewrite + Hybrid Retrieval + RRF + Citation** 完成知识增强问答。

---

## ⭐ 项目亮点

- 基于 Intent Orchestration 实现请求意图识别与任务分发，将用户请求路由至 RAG_QA、SUMMARY、CHAT 等不同业务链路，避免无关请求进入 RAG 流程
- 基于 PendingTask 管理需要用户进一步指定目标的任务状态，支持多轮交互后继续执行；澄清回复会先判断是回答澄清还是换了新问题，知识库换绑后旧的 PendingTask 会作废
- 实现 Vector Retrieval + BM25 + RRF 的 Hybrid Retrieval，两路检索均基于 knowledgeId 进行 Metadata Filter，实现知识库范围隔离
- Vector Retrieval 用于语义匹配，BM25 补充技术术语与精确关键词场景，并通过 RRF 融合检索结果
- 实现 RAG Early Stop，在无召回结果时提前结束流程，避免无效调用 LLM；低分结果 Early Stop 逻辑已保留但当前未启用

---

## ✨ 核心功能 (V2.1 已完成)

### 🖥️ 前端交互

- Vue3 前端页面
- 知识库管理界面
- 文档上传交互
- AI 对话页面
- Markdown 回答渲染
- Citation 引用展示
- RAG Evaluation 评测页面
- 对话历史
- 用户信息
- 登录弹窗



### 📄 文档与知识库管理

- 知识库创建与管理
- 企业文档上传
- PDF / Markdown / TXT 文档解析
- 文档 Embedding 向量化并写入 Redis Vector Store
- 文档处理状态跟踪
- 文档删除时同步清理数据库、向量索引与本地文件
- 文档自动分块 Chunking：按页递归切分（约 500 字符、50 重叠），页码会进入 metadata，供引用使用；当前 token_size 记的是字符数



### 🔍 RAG 检索链路

- Query Rewrite 优化用户查询
- Vector Retrieval + BM25 Retrieval 实现混合检索；BM25 复用向量入库时的 RediSearch TEXT 字段
- Metadata Filter 基于 `knowledgeId` 限制检索范围
- RRF 融合两路检索结果
- 无召回结果时触发 Early Stop，避免无效 LLM 调用
- Prompt Construction 组装检索上下文并进行 LLM Generation
- Citation 追踪生成结果对应的知识来源



### 💬 智能问答

- Intent Recognition 意图识别
- Query Rewrite 查询重写
- 多轮对话上下文 Memory
- 基于知识库增强回答
- Hybrid Retrieval 混合检索
- Citation 引用来源返回
- 异步任务处理
- SSE 流式输出
- Summary 文档 / 知识库总结
- Clarification 澄清与 PendingTask 任务管理



### 🔐 基础系统能力

- 用户认证
- JWT 鉴权
- REST API 接口设计
- 模块化业务架构

---



## 🏗️ 系统架构

```text
                           User
                            │
                           Vue3
                            │
                    Spring Boot API
                            │
                   Intent Orchestrator
                            │
             ┌──────────────┼──────────────┐
             ▼              ▼              ▼
          RAG_QA         SUMMARY          CHAT
             │              │              │
             ▼              ▼              ▼
       RAG Workflow   Summary Service  CasualChatService
             │
     ┌───────┼────────┬────────┬──────────┐
     ▼       ▼        ▼        ▼          ▼
   Rewrite Retrieve  Prompt    LLM      Citation
    Node     Node     Node     Node       Node
              │                 │
         ┌────┴────┐            │
         ▼         ▼            ▼
       Vector     BM25         LLM
      Retrieval Retrieval       │
         │         │            │
         └────┬────┘            │
              ▼                 ▼
          RRF Fusion       Trace System
                              │
                        （贯穿全链路）
```

RAG Workflow 实际包含 `Rewrite Node`、`Retrieve Node`、`Prompt Node`、`LLM Generation Node`、`Citation Node` 五个节点。Memory 在 Workflow 前后分别进行 Load / Persist，不作为 Workflow Node。

---

## 📄 文档处理流程

```text
用户上传文档

↓

Document Parser (PDF / Markdown / TXT)

↓

Chunk Service (文本分块)

↓

Embedding Model

↓

Redis Vector Store

↓

企业知识库
```

---

## 🎬 系统展示



### AI问答

AI问答

### 知识库管理

知识库管理

### 文档管理

文档管理

---

## 🛠️ 技术栈


| 模块          | 技术选型                     |
| ----------- | ------------------------ |
| 前端框架        | Vue3                     |
| 构建工具        | Vite                     |
| 开发语言        | TypeScript               |
| UI组件        | Element Plus             |
| 状态管理        | Pinia                    |
| 路由          | Vue Router               |
| HTTP请求      | Axios                    |
| 后端框架        | Spring Boot 3            |
| 开发语言        | Java 17                  |
| ORM框架       | MyBatis Plus             |
| AI框架        | LangChain4j              |
| 大语言模型       | DashScope 兼容接口 / Qwen    |
| Chat模型      | qwen-plus / qwen-turbo   |
| Embedding模型 | text-embedding-v3        |
| 数据库         | MySQL                    |
| 缓存          | Redis                    |
| 向量检索        | Redis Stack + RediSearch |
| API测试       | Postman                  |
| API文档       | SpringDoc                |
| 鉴权          | JWT                      |


---

## 📂 项目结构

本项目采用前后端分离架构：

- 后端：Spring Boot + MySQL + Redis + RAG
- 前端：Vue3 + Vite

目录结构如下：

```text
AI-Enterprise-Knowledge-Platform

├── backend
│   └── AiConsultant
│       ├── common
│       ├── chat
│       ├── document
│       ├── knowledge
│       ├── llm
│       ├── memory
│       ├── orchestrator
│       ├── pending
│       ├── rag
│       ├── summary
│       ├── trace
│       ├── user
│       └── workflow
│
├── frontend
│   ├── views
│   ├── components
│   ├── api
│   ├── stores
│   ├── router
│   ├── layouts
│   ├── composables
│   ├── types
│   └── utils
│
├── docs
└── README.md
```

---

## 🧩 Engineering Challenges



### 1. 多节点流程参数管理

**问题**：

RAG 流程包含 Query Rewrite、Memory、Retrieval、Generation 等多个处理阶段，需要在不同节点之间共享流转数据。

**方案**：

设计 `WorkflowContext` 上下文对象，统一承载全流程流转数据。

```text
WorkflowContext

- originalQuery
- rewrittenQuery
- memory
- retrievedDocuments
- finalAnswer
- knowledgeId
- intent
- citations
- earlyStop
- needsClarification
- taskId
```

---

### 2. LLM 稳定性

**问题**：

调用第三方大模型服务，存在超时、限流、报错等服务失败风险。

**方案**：

非流式 `generateAnswer` 实现主模型重试与兜底模型机制。主模型 `qwen-plus` 共调用 2 次（首次调用 + 1 次重试），两次均失败后切换默认 `qwen-turbo`，同时记录 Trace 用于问题排查。

```text
Primary Model
      |
失败重试
      |
Fallback Model
```

用户聊天使用流式 `qwen-plus` 调用，当前没有配置流式副模型，因此流式调用失败时不会执行上述 Fallback。

---

### 3. 检索效果评估

**问题**：

RAG 效果不能只靠人工肉眼观察主观判断，需要可量化指标做客观评测。

**方案**：

构建标准测试集 `golden dataset`，通过固定 Top5 检索结果进行离线评测。评测页面位于 /eval，支持查看测试集、点击「开始评测」，展示 Hit@5、Recall@5、MRR、平均耗时、P95 等指标，并可在用例表中按搜索、Passed / Failed 筛选；

查看单个用例时，可通过右侧抽屉查看检索链路、Top 召回文档、Ground Truth 与命中标记。评测只覆盖检索效果，不评估生成质量。评测结果会写入 docs/eval/result/eval-result-yyyy-MM-dd.md。

计算指标：

- Recall@5
- MRR
- Hit Rate
- Avg Latency
- P95 Latency

当前 Recall@5 与 Hit Rate 均按照「召回列表中是否出现任一期望文档」进行计算，因此在当前评测实现中两者结果相同。

---

## 🚀 Production Deployment

项目已完成云服务器部署，并通过公网访问验证。

### 部署环境


| 模块        | 技术选型             |
| --------- | ---------------- |
| 操作系统      | Ubuntu 22.04 LTS |
| 后端服务      | Spring Boot      |
| 前端服务      | Vue3             |
| 数据库       | MySQL            |
| 缓存 / 向量存储 | Redis Stack      |
| 部署方式      | 云服务器部署           |


仓库当前未提供 Dockerfile、Docker Compose 或 Nginx 配置文件。

### 系统部署架构

```text
服务器环境准备

↓

Spring Boot 后端部署

↓

Vue3 前端构建部署

↓

MySQL / Redis 配置

↓

公网访问验证
```



### 已完成部署能力

- [x] 云服务器部署
- [x] Spring Boot 后端线上运行
- [x] Vue3 前端线上部署
- [x] RAG 核心链路线上环境验证
- [x] 数据库持久化配置
- [x] Redis Vector Store 正常运行

Docker Compose 部署方案目前仍未纳入仓库。

### 生产环境说明

生产环境需显式启用 `prod` profile：

```bash
SPRING_PROFILES_ACTIVE=prod
```

此时会加载 `application.yml` + `application-prod.yml`（后者覆盖 Redis / DB 等连接配置）。

敏感配置通过服务器环境变量注入（不要依赖仓库里的 `.env`），变量名可参考：

`backend/AiConsultant/.env.example`

后端默认端口为：

```text
8087
```

---

## 🚀 快速启动



### 1. 环境要求

- Java 17+
- MySQL 8+
- Redis 7+（本地向量检索建议 Redis Stack）
- Node.js

### 2. 环境配置

**Clone 后默认走本地配置**：只加载 `application.yml`（MySQL / Redis 默认 `localhost`），**不会**自动启用 `application-prod.yml`。

首次本地运行前，复制并填写 `.env`：

```bash
cd backend/AiConsultant

cp .env.example .env
```

本地 `.env` 至少填写：

- `API_KEY` / `EMBEDDING_API_KEY`
- `DB_USERNAME` / `DB_PASSWORD`
- `JWT_SECRET_KEY`

启动时 `LocalDotenvBootstrap` 会把 `.env` 读入环境，供 `application.yml` 中的 `${...}` 占位符使用。

本地 `.env` 中**不要**设置 `SPRING_PROFILES_ACTIVE=prod`，否则会按生产配置连接远程库。

### 3. 后端启动

进入后端项目目录：

```bash
cd backend/AiConsultant
```

启动：

```bash
mvn spring-boot:run
```

后端默认运行在 `8087` 端口。

### 4. 前端启动

进入前端项目目录：

```bash
cd frontend
```

安装依赖：

```bash
npm install
```

启动：

```bash
npm run dev
```

---

## 🔌 API 示例

当前聊天接口采用**异步任务 + SSE** 方式返回结果。

### 1. 提交聊天任务

**请求接口：**

```http
POST /api/v1/chat
```

**请求入参：**

```json
{
  "conversationId": 1,
  "message": "Redis有哪些持久化方式？"
}
```

**返回结果：**

提交成功后立即返回 `taskId`：

```json
{
  "code": 1,
  "data": {
    "taskId": "..."
  }
}
```



### 2. 建立 SSE 连接

```http
GET /api/v1/chat/stream/{taskId}
```

SSE 过程中：

- `RAG_QA` 和 `CHAT` 走流式 `qwen-plus`，会多次推送 `answer` 生成片段，流式调用失败后发送 `error` 事件并关闭连接，不进行模型降级
- `SUMMARY`、意图识别、澄清判断走非流式 `generateAnswer`，支持主模型重试与 `qwen-turbo` 兜底，SSE 上通常表现为 `progress` 事件，最后以 `complete` 事件结束，中间不会推送 `answer` 生成片段
- 成功时以 `complete` 事件结束，并返回最终 `answer` 与 `references`
- 流式处理失败时发送 `error` 事件并关闭连接，不再发送 `complete`

最终成功结果通过 `complete` 事件返回：

```json
{
  "answer": "Redis主要有RDB和AOF两种持久化方式...",
  "references": [
    {
      "documentId": 2080836111764529154,
      "documentName": "redis持久化.pdf",
      "page": 1,
      "content": "redis持久化..."
    }
  ]
}
```



### 3. RAG Evaluation 评测

GET /api/v1/eval/dataset
POST /api/v1/eval/run

评测接口用于加载测试集与执行检索评测。评测结果会写入 docs/eval/result/eval-result-yyyy-MM-dd.md。

### 4. Trace 查询

GET /api/v1/traces/{traceId}

Trace 查询可查看节点耗时、状态，以及 model_used、是否发生降级、降级原因等信息，数据落在 trace_span。当前 Trace 只有接口与落库能力，前端没有独立 Trace 页面或侧边栏入口。

---

## 🧪 测试记录

完整 RAG 链路测试记录：

包含：

- 文档上传测试
- 检索测试
- 多轮对话 Memory 测试
- Citation 返回测试

详情：

[docs/test/V1-test.md](docs/test/V1-test.md)

---

## ✅ 当前状态

目前已完成前后端完整链路，并完成云服务器环境验证：

- 企业知识库管理
- 文档解析与向量化
- RAG 增强检索（向量 + BM25 Hybrid / RRF）
- 多轮对话 Memory
- Citation 引用返回
- Trace 链路观测
- LLM Fallback 降级（非流式调用）
- RAG Evaluation 检索评测
- Intent Orchestration
- 文档 / 知识库 Summary
- Clarification / PendingTask
- 异步任务与 SSE 流式输出
- Vue 前端交互页面

其中 Memory 当前为 Redis 短期记忆，支持滑动窗口与摘要，不包含 Long-term Memory。

游客可以访问前端页面，但后端业务接口仍需要 JWT 鉴权，因此未登录状态下无法进行实际问答或获取业务数据。

支持本地部署与云服务器环境运行。

---

## 🗺️ Roadmap



### V1.0 - RAG 基础能力 ✅

- [x] 用户认证
- [x] 知识库管理
- [x] 文档上传与解析
- [x] Chunk文本分割
- [x] Embedding向量化
- [x] Redis Vector检索
- [x] Query Rewrite
- [x] Short-term Memory
- [x] Citation引用来源
- [x] REST API
- [x] Vue 前端基础交互界面



### V2.1 - 可观测性与生产部署

- [x] Trace 链路日志追踪
- [x] RAG Evaluation 检索效果评估
- [x] Fallback 大模型降级策略
- [x] Hybrid Retrieval（向量 + BM25 + RRF）
- [x] Intent Orchestration
- [x] Summary / Clarification / PendingTask
- [x] 异步任务与 SSE 流式输出
- [x] 游客浏览与登录弹窗
- [ ] Long-term Memory 长期记忆
- [ ] 完善 Docker Compose 部署方案



### V3.0 - Advanced AI Application （计划）🚀

- [ ] Semantic Chunking 语义切片优化
- [ ] Hybrid Retrieval Optimization 混合检索优化
- [ ] Online Evaluation 在线效果监控
- [ ] Prompt Management 提示词管理
- [ ] Agent Workflow 探索
