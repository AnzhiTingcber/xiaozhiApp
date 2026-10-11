<div align="center">

# ⚡ 电力小智 · 供电服务 LLM 智能客服

**LangChain4j 智能体 × Function Calling 工具调用 × RAG 向量检索 × MongoDB 会话记忆**

[![Java](https://img.shields.io/badge/Java-17-orange)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.0-brightgreen)](https://spring.io/projects/spring-boot)
[![LangChain4j](https://img.shields.io/badge/LangChain4j-1.20.0-blue)](https://docs.langchain4j.dev/)
[![MySQL](https://img.shields.io/badge/MySQL-8-4479a1)](https://www.mysql.com/)
[![MongoDB](https://img.shields.io/badge/MongoDB-%E2%89%A56-47A248)](https://www.mongodb.com/)
[![Pinecone](https://img.shields.io/badge/Pinecone-Serverless-111111)](https://www.pinecone.io/)

*A LangChain4j-powered power-service AI assistant — Function Calling + RAG + Persistent Chat Memory.*

</div>

---

基于 LangChain4j AiServices 的智能体应用：**电力小智**是面向供电服务场景的 LLM 智能客服，支持**用电业务咨询、故障报修与上门服务预约**，实现多轮对话记忆、RAG 与工具调用闭环。大模型通过 **Function Calling 自主决策**调用预约工具并落库 MySQL，回答前经 **RAG** 检索电价政策 / 办电指南知识库，多轮对话记忆持久化到 MongoDB，接口全程流式输出。

> 项目为 LangChain4j 学习实战项目，从单智能体出发，预留了 MCP、多智能体协同的演进路线（见文末 Roadmap）。

## ✨ 功能特性

- 🤖 **智能体与流式对话**：基于 LangChain4j 声明式 AI Service 构建客服智能体，一个 `@AiService` 接口完成全部装配——对接 GLM 大模型，`Flux<String>` 逐 token 实现 SSE 流式输出
- 💾 **会话记忆持久化**：实现 `ChatMemoryStore` SPI，将会话消息 JSON 序列化存入 MongoDB，按 `memoryId` 隔离会话；滑动窗口控制上下文长度，服务重启后会话状态不丢失（实测 20 条窗口较全量上下文**节约 44.1%** 输入 Token）
- 🔍 **RAG 知识库**：使用 Pinecone + bge-m3 构建向量检索知识库，将电价政策、办电指南等文档切片入库，检索时按相似度阈值过滤无关片段；`minScore=0.8` 高置信检索显著降低模型幻觉（实测知识库外问题无效检索泄漏 **24 条 → 0 条**）
- 🛠️ **Function Calling 与业务校验**：编排预约、取消、可办理时段查询三个工具，由大模型自主决策调用；预约落库前按**客户、证件、业务、日期、时段**联合查重，避免重复预约
- 📋 **提示词工程**：供电客服人设 + 预约必填信息校验规则 + `{{current_date}}` 动态日期，系统提示词外置为资源文件可随时调整
- 📡 **接口文档**：集成 Knife4j，接口自动生成中文文档

## 🏗️ 架构

```mermaid
flowchart LR
    U["用户<br/>POST /xiaozhi/chat"] -->|"ChatForm{memoryId, message}"| C["XiaozhiController"]
    C -->|"Flux 流式返回"| U
    C --> A["XiaozhiAgent<br/>@AiService 声明式智能体"]

    subgraph LC["LangChain4j 运行时"]
        A -->|"加载人设与规则"| PT["xiaozhi-promt-template.txt<br/>供电客服系统提示词"]
        A -->|"回答前语义检索<br/>maxResults=1 · minScore=0.8"| RAG["Pinecone 向量库<br/>电价政策 / 办电指南 / 故障报修知识"]
        A -->|"每轮对话读写<br/>滑动窗口"| MEM[("MongoDB<br/>chat_memory_db")]
        A -->|"Function Calling<br/>模型自主决策"| TOOLS["AppointmentTools<br/>@Tool × 3"]
    end

    TOOLS -->|"MyBatis-Plus"| DB[("MySQL<br/>appointment 上门服务预约记录")]
```

**工具调用闭环（这就是"智能体"的运行方式）**：

1. 用户消息进入 `XiaozhiAgent`，LangChain4j 把 3 个 `@Tool` 的方法签名与描述生成 JSON Schema，随请求一并发给大模型；
2. 模型结合系统提示词、RAG 检索结果与对话记忆推理，**自主决定**是否调用工具（返回 tool-call 而非普通文本）；
3. 框架在本地执行对应 Java 方法，预约记录写入 MySQL；
4. 执行结果作为"观察"回喂模型，模型据此生成最终自然语言回答流式输出。

> 一句话：业务代码里没有任何一行去调用 `bookAppointment()`——什么时候预约、怎么收集信息，全是模型自己决定的。把 `tools` 这行配置删掉，项目就退化成一个普通的"带知识库的聊天机器人"。

| 工具（`@Tool`） | 职责 | 落库 |
|---|---|---|
| `预约 bookAppointment` | 客户 + 证件 + 业务 + 日期 + 时段联合查重 → 防幻觉清 id → 写入预约记录 | MySQL `appointment` |
| `取消预约 cancelAppointment` | 存在校验 → 删除预约记录 | MySQL `appointment` |
| `查询可办理时段 queryDepartment` | 可办理时段查询（**当前为占位实现，固定返回 true**，见 Roadmap） | — |

## 🚀 快速开始

### 环境要求

| 依赖 | 要求 |
|---|---|
| JDK | 17 |
| Maven | 3.6.3+（Spring Boot 3.5 的硬性要求） |
| MySQL | 8.x（库名 `guiguxiaozhi`） |
| MongoDB | 4.x+（默认 `localhost:27017`） |
| Pinecone | Serverless 账号（索引首次启动自动创建） |
| LLM | 任意 OpenAI 兼容端点（默认智谱 GLM） |

### 1. 初始化 MySQL

```sql
CREATE DATABASE guiguxiaozhi DEFAULT CHARACTER SET utf8mb4;
USE guiguxiaozhi;
CREATE TABLE appointment (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  username    VARCHAR(64)  COMMENT '客户姓名',
  id_card     VARCHAR(32)  COMMENT '证件号码',
  department  VARCHAR(64)  COMMENT '业务类型（故障报修/新装办电等）',
  date        VARCHAR(32)  COMMENT '预约日期',
  time        VARCHAR(16)  COMMENT '预约时段（上午/下午）',
  doctor_name VARCHAR(64)  COMMENT '指定服务人员（可选）'
);
```

### 2. 配置密钥

编辑 `src/main/resources/application.yml`（该文件含密钥，建议加入 `.gitignore`，勿提交）：

```yaml
langchain4j:
  open-ai:
    streaming-chat-model:
      base-url: https://open.bigmodel.cn/api/paas/v4   # 智谱 OpenAI 兼容端点
      api-key: <你的 LLM API Key>
      model-name: glm-4.5-air
    embedding-model:
      base-url: https://api.siliconflow.cn/v1          # 硅基流动
      api-key: <你的 Embedding API Key>
      model-name: BAAI/bge-m3

spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/chat_memory_db
  datasource:
    url: jdbc:mysql://localhost:3306/guiguxiaozhi?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false
    username: root
    password: <你的 MySQL 密码>

pinecone:
  api-key: <你的 Pinecone API Key>
```

常用 LLM 端点（OpenAI 兼容协议，替换 `base-url` + `model-name` 即可切换厂商）：

| 厂商 | base-url | 模型示例 |
|---|---|---|
| 智谱 AI | `https://open.bigmodel.cn/api/paas/v4` | `glm-4.5-air` |
| DeepSeek | `https://api.deepseek.com/v1` | `deepseek-chat` |
| OpenAI | `https://api.openai.com/v1` | `gpt-4o-mini` |

### 3. 启动

```bash
mvn spring-boot:run
```

| 入口 | 地址 |
|---|---|
| 对话接口 | `POST http://localhost:8080/xiaozhi/chat` |
| 接口文档 Knife4j | <http://localhost:8080/doc.html> |

### 4. 调用示例

```bash
curl -N -X POST http://localhost:8080/xiaozhi/chat \
  -H "Content-Type: application/json" \
  -d '{"memoryId": 1, "message": "我想了解一下居民阶梯电价是怎么算的？"}'
```

- `memoryId`：会话 ID，相同 `memoryId` 共享一份记忆（存 MongoDB）
- 响应为 `text/stream;charset=utf-8` 流式文本
- 换一句试试："家里停电了，帮我预约明天上午上门检修"——观察日志，能看到模型发起的 tool-call 与工具执行记录

## 🧠 记忆与 RAG

**会话记忆**：`MongoChatMemoryStore` 实现 LangChain4j 的 `ChatMemoryStore` SPI，消息列表经 `ChatMessageSerializer` 序列化为 JSON 存入 MongoDB（`upsert` 按 `memoryId` 更新），读取时反序列化还原，跨重启不丢。

**向量检索**：`EmbeddingStoreConfig` 使用 Pinecone Serverless（AWS `us-east-1`），索引 `xiaozhi-index`、命名空间 `xiaozhi-namespace` 不存在时自动创建，向量维度自动对齐 `bge-m3`（1024 维）。检索器每次回答最多取 1 条、最低得分 0.8，将电价政策、办电指南等知识以高置信度注入上下文，过滤知识库外问题的无效检索。


## 🧪 测试与验证

三个测试类分别量化三条核心指标，位于 `src/test/java/com/tinglan/test/`，均采用"免费档 + 精确档"两档设计：

| 测试类 | 验证指标 | 免费档（默认可跑） | 精确档（`@Disabled` 手动跑） |
|---|---|---|---|
| `TokenWindowSavingTest` | 滑动窗口的 Token 节省比例 | 同一 30 轮对话脚本，离线粗估三种窗口策略的累计 Prompt Token | 调真实 LLM 读取接口 `usage` 字段精确计量（约 90 次请求） |
| `RagThresholdEvalTest` | 相似度阈值对无效回答率的影响 | 组件级：不调 LLM，构建 8 类知识库外干扰问题，验证 `minScore=0.8` 挡住检索污染 | 端到端：无阈值 vs 有阈值两组助手作答 + LLM-as-Judge 判定无效回答率（约 64 次请求） |
| `ChatLoadTest` | 接口并发容量（QPS / 延迟分位数 / 错误率） | — | 参数化压测：`-Dconcurrency=50 -Drounds=4`，输出成功率 / QPS / P50 / P95 |

> **实测参考**（30 轮对话脚本，离线粗估档）：20 条滑动窗口相比不控制上下文**节省 44.1%** Prompt Token；`RagThresholdEvalTest` 中 8 类知识库外干扰问题在无阈值时产生 **24 条无效检索泄漏**，引入 `minScore=0.8` 后泄漏降为 **0 条**，显著降低模型幻觉。

运行说明：

- `@Disabled` 用例需手动去掉注解运行，会消耗真实 Token 与费用
- `RagThresholdEvalTest` 要求 Pinecone 索引 `xiaozhi-index` 已入库知识文档，索引为空时自动跳过并提示
- 全链路压测的 QPS 瓶颈在 LLM 推理时长（秒级），QPS ≈ 并发数 ÷ 平均延迟；验证应用层容量时，把模型 `base-url` 临时指向本地 mock 模型再压


## 📁 项目结构

```
src/main/java/com/tinglan
├── assistant/    # XiaozhiAgent：@AiService 声明式智能体入口（模型/记忆/工具/RAG 装配点）
├── bean/         # ChatForm 请求体、XiaozhiChatMessages Mongo 文档
├── config/       # XiaozhiAgentConfig（记忆提供者+检索器）、EmbeddingStoreConfig（Pinecone）
├── controller/   # XiaozhiController：POST /xiaozhi/chat 流式接口
├── entity/       # Appointment 上门服务预约实体（MyBatis-Plus）
├── mapper/       # AppointmentMapper
├── service/      # AppointmentService 预约业务（五字段联合查重）
├── store/        # MongoChatMemoryStore：ChatMemoryStore 的 MongoDB 实现
└── tools/        # AppointmentTools：@Tool 预约 / 取消 / 可办理时段查询
src/main/resources
├── xiaozhi-promt-template.txt   # 系统提示词：供电客服人设、业务咨询、预约必填规则
└── mappper/AppointmentMapper.xml
```

## 🗺️ Roadmap（进阶演进路线）

- [ ] **补齐真实可办理时段查询**：`queryDepartment` 目前为占位实现，接入营业厅 / 上门服务排班数据后 agent 才有"真判断"
- [ ] **接入 MCP 工具生态**：引入 `langchain4j-mcp`，用 `McpClient` + `McpToolProvider` 把预约工具迁出为独立 MCP Server（供多端复用），或接入 95598 工单 / 联网搜索等现成 Server，与本地 `@Tool` 并列按需调用
- [ ] **多智能体协同（方案 A · 专家即工具）**：把提示词里的三个角色拆成独立专家——用电咨询专家（只挂电价政策 RAG）、故障报修专家、上门预约专家，包装成主 agent 的 `@Tool`，由模型自主调度
- [ ] **多智能体协同（方案 B · Supervisor 编排）**：引入 `langchain4j-agentic` 模块，`supervisorBuilder` 声明调度员，`AgenticScope` 在专家间共享状态（如用电咨询产出的业务类型直接被预约专家读取）；配合 `conditionalBuilder`（按意图路由）与 `sequenceBuilder`（咨询 → 预约接力）
- [ ] **并行智能体**：`parallelBuilder` 实现多业务并行受理（报修 / 办电 / 咨询并行给意见后汇总）、`parallelMapperBuilder` 批量解读多份工单
- [ ] **A2A 跨进程协作**：`langchain4j-agentic-a2a` 把远程 agent 当子 agent 调用——MCP 让 agent 挂上"工具"，A2A 让 agent 调用"别的 agent"

## 📄 License

