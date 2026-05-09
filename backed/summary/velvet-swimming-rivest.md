# 持久化会话记忆实现计划

## Context

项目已经完整实现了持久化会话记忆的基础设施，但**完全未被使用**：
- `MessageChatMemory` — Spring AI `ChatMemory` 接口的完整实现，可将消息持久化到 `conversation_messages` 表，但**未在任何地方注入使用**
- `ConversationMessage` 实体 / `ConversationMessageMapper` — 数据层就绪
- `conversations` 表 — `CodeTaskServiceImpl` 创建对话记录但**从不写入消息**

目前每次 `CodeGenerationGraph.execute()` 都是完全无状态的独立调用，Agent 收不到任何历史上下文，执行结果也不持久化到消息表中。

## 目标

将 `MessageChatMemory` 正确接入工作流，实现：
1. **写** — 每次代码生成任务完成后，将用户需求、Agent 输出（plan/code/review）持久化到 `conversation_messages`
2. **读** — Agent 在执行时能读取当前对话的最近 N 条消息，纳入 Prompt 上下文
3. **查** — 前端可通过现有 API 查看对话消息历史

## 改动涉及的文件

| 文件 | 改动 |
|------|------|
| `CodeGenerationGraph.java` | `execute()` 增加 `conversationId` 参数；钩子中调用 `ChatMemory.add()` 持久化消息 |
| `CodeTaskServiceImpl.java` | 传入 `conversationId`，构建 UserMessage 存入 ChatMemory |
| `PlannerAgent.java` | 注入 `ChatMemory`，支持 `execute()` 读取历史消息加入 Prompt |
| `CoderAgent.java` | 注入 `ChatMemory`，支持 `execute()` 读取历史消息加入 Prompt |
| `ReviewerAgent.java` | 注入 `ChatMemory`，支持 `execute()` 读取历史消息加入 Prompt |
| `GraphState.java` | 新增 `KEY_CONVERSATION_ID` |
| `ConversationMessage.java` | 可选的 `Entity` 字段调整 |

## 设计思路

```
CodeTaskServiceImpl.executeTask()
  │
  ├─ 1. 创建 Conversation (status=ACTIVE)
  ├─ 2. chatMemory.add(conversationId, [UserMessage(requirement)])
  ├─ 3. graph.execute(requirement, conversationId, chatMemory)
  │       │
  │       ├─ plannerNode → chatMemory.add(assistant, plan.taskAnalysis)
  │       ├─ coderNode   → chatMemory.add(assistant, code.explanation + files)
  │       └─ reviewerNode → chatMemory.add(assistant, review.summary)
  │
  └─ 4. 更新 Conversation (status=COMPLETED)
```

## 详细步骤

### Step 1: GraphState 增加常量

在 `GraphState.java` 添加：
```java
public static final String KEY_CONVERSATION_ID = "conversationId";
```

### Step 2: CodeGenerationGraph 接收 conversationId

**`execute(requirement)` → `execute(requirement, conversationId, ChatMemory chatMemory)`**

- 将 `conversationId` 存入初始 State
- 在每个 Node Function 完成后读取 State 中的关键输出，调用 `chatMemory.add()` 写入：
  - planner 完成 → 写入 `taskAnalysis` 和 `techStack` 作为 assistant 消息
  - coder 完成 → 写入 `explanation` 和文件列表作为 assistant 消息
  - reviewer 完成 → 写入 `summary` 和评分作为 assistant 消息

### Step 3: CodeTaskServiceImpl 串联

修改 `executeTask()`：
1. 创建 Conversation 后立即调用 `chatMemory.add(conversationId, [UserMessage(requirement)])`
2. 将 `conversationId` 和 `chatMemory` 传入 `graph.execute()`

### Step 4: Agent 读取历史消息（可选增强）

每个 Agent 的 `execute()` 增加重载，从 `ChatMemory.get(conversationId, lastN)` 获取最近消息列表，拼接到 `UserMessage` 前作为上下文。

这使 Agent 能感知同一 conversation 中的前序对话。

### Step 5: 消息结构与内容

每条 `ConversationMessage` 存储内容：

| Role | Content | Metadata |
|------|---------|----------|
| `user` | 原始需求全文 | `{"type": "requirement"}` |
| `assistant` | Planner 的任务分析 | `{"type": "plan", "techStack": "..."}` |
| `assistant` | Coder 的解释 + 文件概览 | `{"type": "code", "fileCount": 3}` |
| `assistant` | Reviewer 的总结 + 评分 | `{"type": "review", "score": 85, "accepted": true}` |

## 不包含在本计划中的事项

- **State Checkpointing / Graph Resume** — 持久化图执行中间状态用于中断恢复，暂不实现
- **Vector Store / RAG** — 不使用向量数据库进行记忆检索
- **多轮对话 UI** — 前端交互不在本计划范围

## 验证方式

1. 启动应用，发送 `POST /api/code-task/generate` 执行一个代码生成任务
2. 确认 `conversation_messages` 表中插入了至少 4 条消息（1 user + 3 assistant）
3. 调用 `GET /api/conversations/{conversationId}` 确认对话状态为 COMPLETED
4. （通过数据库直接查询或新加调试端点）确认消息顺序正确、内容完整
