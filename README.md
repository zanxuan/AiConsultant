## 🌐 Online Demo

项目已完成云服务器部署。

访问地址：

[http://106.55.103.123](http://106.55.103.123)

由于系统涉及 AI API 调用，目前采用测试账号访问。

体验账号将在面试或交流时提供，感谢理解！

# AI Enterprise Knowledge Platform

> 基于 Vue3 + Spring Boot + LangChain4j + RAG 的企业知识助手平台。
> 支持企业知识库管理、文档解析、多轮智能问答以及答案来源追踪。

## 📖 项目介绍

在企业研发过程中，大量技术文档、接口文档、Wiki 等知识分散存储，传统关键词搜索难以理解用户语义，导致研发人员需要在大量文档中反复查找。

本项目面向企业内部知识管理场景，构建了一套基于 RAG（Retrieval-Augmented Generation，检索增强生成）的 AI 知识助手系统。

用户可以上传企业文档，系统经过：

```
文档解析
↓
文本分块
↓
Embedding向量化
↓
知识库存储
↓
语义检索
↓
LLM生成回答
```

---

## ⭐ 项目亮点

- 基于 RAG 构建企业知识问答链路，实现文档检索增强生成
- 使用 Redis Stack + RediSearch Vector 实现向量检索，并结合 Metadata Filter 实现精准检索
- 基于 Workflow 编排 RAG 问答流程，整合 Query Rewrite、Memory、Retrieval 等模块
- 支持 Citation 来源追踪，提高回答可信度
- 前后端分离架构，支持独立部署

---



## ✨ 核心功能 (V1 已完成)



## 🖥️ 前端交互

- Vue3 前端页面
- 知识库管理界面
- 文档上传交互
- AI 对话页面
- Markdown回答渲染
- Citation引用展示



## 📄 文档与知识库管理

- 知识库创建与管理
- 企业文档上传
- PDF / Markdown / TXT 文档解析
- 文档自动分块 Chunking
- 文档 Embedding 向量化并写入 Redis Vector Store



## 🔍 RAG 检索链路

- Embedding 向量生成
- Redis Vector 向量数据库存储
- 基于语义相似度的知识检索
- Metadata Filter 精确过滤



## 💬 智能问答

- Query Rewrite 查询重写
- 多轮对话上下文 Memory
- 基于知识库增强回答
- Citation 引用来源返回



## 🔐 基础系统能力

- 用户认证
- REST API 接口设计
- 模块化业务架构

---



## 🏗️ 系统架构

```
用户问题
  │
  ▼
Vue3 前端
  │
  ▼
Spring Boot API
  │
  ▼
Workflow (工作流编排)
  │
  ▼
Memory (历史上下文)
  │
  ▼
Query Rewrite (查询重写)
  │
  ▼
Vector Retrieval (Vector Search)
  │
  ▼
Prompt Assembly
  │
  ▼
LLM (大模型生成)
  │
  ▼
Answer + Citation (引用来源)
```

---



## 📄 文档处理流程

```
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

chat

### 知识库管理

knowledge

### 文档管理

upload

---



## 🛠️ 技术栈


| 模块          | 技术选型                     |
| ----------- | ------------------------ |
| 前端框架        | Vue3                     |
| 构建工具        | Vite                     |
| UI组件        | Element Plus             |
| HTTP请求      | Axios                    |
| 后端框架        | Spring Boot              |
| 开发语言        | Java 17                  |
| ORM框架       | MyBatis Plus             |
| AI框架        | LangChain4j              |
| 大语言模型       | Qwen                     |
| Embedding模型 | text-embedding-v3        |
| 数据库         | MySQL                    |
| 缓存          | Redis                    |
| 向量检索        | Redis Stack + RediSearch |
| API测试       | Postman                  |


---



## 📂 项目结构

本项目采用前后端分离架构：

- 后端：Spring Boot + MySQL + Redis + RAG
- 前端：Vue3 + Vite

目录结构如下：

```text
AI-Enterprise-Knowledge-Platform
│
├── backend
│   └── AiConsultant              # Spring Boot 后端
│       │
│       ├── src
│       │   ├── main
│       │   │   ├── java
│       │   │   │   └── com
│       │   │   │       └── zx
│       │   │   │           └── consultant
│       │   │   │               ├── chat              # 聊天模块
│       │   │   │               ├── common            # 公共组件
│       │   │   │               ├── document          # 文档处理
│       │   │   │               ├── knowledge         # 知识库管理
│       │   │   │               ├── llm               # 大模型调用
│       │   │   │               ├── memory            # 多轮记忆
│       │   │   │               ├── rag               # RAG检索
│       │   │   │               ├── user              # 用户模块
│       │   │   │               ├── workflow          # 工作流
│       │   │   │               └── ConsultantApplication.java
│       │   │   │
│       │   │   └── resources
│       │   │       ├── mapper
│       │   │       ├── application.yml
│       │   │       ├── application-prod.yml
│       │   │       └── systemPrompt.txt
│       │   │
│       │   └── test
│       │       └── java
│       │
│       ├── uploads   # 本地上传文件目录（运行时生成）
│       ├── pom.xml
│       └── .env.example
│
│
├── frontend                    # Vue3 前端
│   │
│   ├── src
│   │   ├── api                  # 接口请求
│   │   ├── assets               # 静态资源
│   │   ├── components           # 公共组件
│   │   ├── composables          # Vue组合逻辑
│   │   ├── constants            # 常量
│   │   ├── layouts              # 页面布局
│   │   ├── router               # 路由
│   │   ├── stores               # 状态管理
│   │   ├── types                # TS类型
│   │   ├── utils                # 工具类
│   │   └── views                # 页面
│   │
│   ├── package.json
│   ├── vite.config.ts
│   └── tsconfig.json
│
│
├── docs                         # 项目文档
│   ├── images                   # README展示图片
│   └── test
│       └── V1-test.md
│
└── README.md
```

---



## 🚀 Production Deployment

项目已完成云服务器部署，并通过公网访问验证。

### 部署环境


| 模块        | 技术选型                    |
| --------- | ----------------------- |
| 操作系统      | Ubuntu 22.04 LTS        |
| 容器化       | Docker + Docker Compose |
| 后端服务      | Spring Boot             |
| 前端服务      | Vue3 + Nginx            |
| 数据库       | MySQL 8.0               |
| 缓存 / 向量存储 | Redis Stack             |
| 部署方式      | 云服务器部署                  |




### 系统部署架构

服务器环境准备
↓
Docker 安装
↓
Docker Compose 启动 MySQL / Redis
↓
Spring Boot 后端部署
↓
Vue3 前端构建部署
↓
公网访问验证

### 已完成部署能力

- [x] 云服务器部署
- [x] Docker 容器化运行 MySQL
- [x] Docker 容器化运行 Redis Stack
- [x] Spring Boot 后端线上运行
- [x] Vue3 前端线上部署
- [x] RAG 核心链路生产环境验证
- [x] 数据库持久化配置
- [x] Redis Vector Store 正常运行



### 生产环境说明

项目通过环境变量管理生产环境配置：

包含：

- 数据库连接配置
- Redis连接配置
- AI模型 API Key
- JWT安全配置

敏感配置不会提交到 GitHub。

请参考：
backend/AiConsultant/.env.example

创建对应 `.env` 文件后运行。

---



## 🚀 快速启动



### 1. 环境要求

- Java 17+
- MySQL 8+
- Redis 7+



### 2. 环境配置

项目通过环境变量管理运行配置。

首次运行前，请根据 `.env.example` 创建 `.env` 文件并填写配置。

参考：

backend/AiConsultant/.env.example

数据库：

- DB_URL
- DB_USERNAME
- DB_PASSWORD

Redis：

- REDIS_HOST
- REDIS_PORT
- REDIS_PASSWORD

AI服务：

- API_KEY

安全配置：

- JWT_SECRET_KEY

application.yml 会自动读取对应环境变量。

### 3. 后端启动

进入后端项目目录 

```bash
cd backend/AiConsultant
```

启动：

```bash
mvn spring-boot:run
```



### 4. 前端启动

进入前端项目目录

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

当前提供 REST API，可通过 Postman 调试。

**请求接口：**
POST /api/v1/chat

**请求入参：**

```json
{
  "conversationId": 1,
  "message": "Redis有哪些持久化方式？"
}
```

**返回结果：**
成功返回答案以及 Citation 引用信息

```json
{
  "code": 1,
  "data": {
    "answer": "Redis主要有RDB和AOF两种持久化方式...",
    "reference": [
      {
        "documentId": 2080836111764529154,
        "documentName": "redis持久化.pdf",
        "page": 1,
        "content": "redis持久化..."
      }
    ]
  }
}
```

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



## 当前状态

目前已完成前后端完整链路：

- 企业知识库管理
- 文档解析与向量化
- RAG增强检索
- 多轮对话Memory
- Citation引用返回
- Vue前端交互页面

支持本地部署运行。

---



## 🗺️ Roadmap



### V1 阶段 (已完成)

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



### V2 阶段 (规划中)

- [ ] Trace 链路日志追踪
- [ ] RAG Evaluation 检索效果评估
- [ ] Fallback 大模型降级策略
- [ ] Long-term Memory 长期记忆
- [ ] Docker Compose 部署优化