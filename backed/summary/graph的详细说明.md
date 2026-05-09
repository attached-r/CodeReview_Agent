# Graph 编排与会话记忆实现说明

> 基于 Spring AI Alibaba StateGraph 的多 Agent 协作代码生成工作流

---

## 一、架构总览

系统采用 **StateGraph** 有向图驱动三个 Agent 协作完成代码生成任务：

```
  ┌─────────────────────────────────────────────────────┐
  │                  CodeTaskController                  │
  │              POST /api/code-task/generate            │
  └──────────────┬──────────────────────────────────────┘
                 │ userId (StpUtil), requirement
                 ▼
  ┌─────────────────────────────────────────────────────┐
  │              CodeTaskServiceImpl.executeTask()       │
  │  1. 创建 Conversation → conversations 表             │
  │  2. chatMemory.add(UserMessage) → conversation_messages │
  │  3. graph.execute(requirement, conversationId, userId) │
  └──────────────┬──────────────────────────────────────┘
                 │
                 ▼
  ┌─────────────────────────────────────────────────────┐
  │              CodeGenerationGraph                     │
  │  ┌─────────┐   ┌───────┐   ┌─────────┐             │
  │  │ Planner ├──►│ Coder ├──►│ Reviewer│             │
  │  └─────────┘   └───┬───┘   └────┬────┘             │
  │                     ▲            │                  │
  │                     │   ┌────────┴────────┐         │
  │                     │   │  retry / accept │         │
  │                     │   │  / max_retries  │         │
  │                     │   └────────────────┘         │
  │                     └────────────┘                  │
  └─────────────────────────────────────────────────────┘
```

---

## 二、StateGraph 编排

### 2.1 图结构

`CodeGenerationGraph` 使用 `com.alibaba.cloud.ai.graph.StateGraph` 构建三节点有向图：

| 组件 | 类/方法 | 职责 |
|------|---------|------|
| **StateGraph** | `StateGraph("CodeGeneration", HashMap::new)` | 定义图结构，HashMap 作为状态容器（KeyStrategy 默认 Replace） |
| **Node** | `node_async(this::plannerNode)` 等 | 异步节点函数，接收 `OverAllState`，返回 `Map<String, Object>` 更新 |
| **Edge** | `addEdge(START, NODE_PLANNER)` | 固定边：START → planner → coder → reviewer |
| **Conditional Edge** | `addConditionalEdges(NODE_REVIEWER, router, routingMap)` | 根据审查结果路由到不同节点 |
| **CompiledGraph** | `graph.compile()` | 编译为可执行图，**线程安全，可复用** |
| **Stream** | `compiledGraph.stream(inputs, config)` | 流式执行，返回 `Flux<NodeOutput>` |

### 2.2 节点定义

| 节点名 | 常量 | 执行类 | 输入 | 输出 |
|--------|------|--------|------|------|
| `planner` | `NODE_PLANNER` | `PlannerAgent` | requirement | 子任务列表、技术栈、风险警告 |
| `coder` | `NODE_CODER` | `CoderAgent` | requirement + plan + 前次 review | 代码文件、依赖声明 |
| `reviewer` | `NODE_REVIEWER` | `ReviewerAgent` | requirement + code | 审查问题、评分、幻觉检测 |

### 2.3 条件路由

`routeAfterReview()` 根据审查结果返回路由键：

```
routeAfterReview(state)
  ├─ accepted    → END          (审查通过，流程结束)
  ├─ max_retries → END          (重试达到 3 次上限，结束)
  └─ retry       → coder 节点   (未通过，回退 Coder 重试)
```

路由表定义：
```java
private static final Map<String, String> ROUTING_MAP = Map.of(
    GraphState.ROUTE_ACCEPTED, END,
    GraphState.ROUTE_MAX_RETRIES, END,
    GraphState.ROUTE_RETRY, GraphState.NODE_CODER
);
```

### 2.4 执行流程

```java
compiledGraph.stream(inputs, config)           // 流式驱动节点执行
    .doOnNext(nodeOutput -> lastStateRef.set(...)) // 保存最终状态
    .doOnNext(nodeOutput -> persistNodeOutput(...))// 持久化各节点输出
    .concatMap(this::toNodeEvents)                 // 映射为前端 SSE 事件
    .concatWith(defer -> toFinalEvents(...))       // 追加结束事件
```

每个节点执行完成后，`persistNodeOutput` 异步写入 `conversation_messages` 表。

---

## 三、Graph State 管理

### 3.1 State Key 定义

`GraphState.java` 定义了所有状态键：

| Key | 类型 | 用途 | 写入者 | 读取者 |
|-----|------|------|--------|--------|
| `requirement` | `String` | 用户原始需求 | 初始输入 | 所有节点 |
| `plannerOutput` | `PlannerOutput` | 任务规划 | planner 节点 | coder 节点 |
| `coderOutput` | `CoderOutput` | 生成代码 | coder 节点 | reviewer 节点 |
| `reviewOutput` | `ReviewOutput` | 审查结果 | reviewer 节点 | 条件路由 |
| `retryCount` | `Integer` | 当前重试次数 | planner(0) / reviewer(+1) | coder / 条件路由 |
| `conversationId` | `String` | 对话 UUID | 初始输入 | persistNodeOutput / readHistory |
| `userId` | `Long` | 操作用户 ID | 初始输入 | persistNodeOutput / readHistory |

### 3.2 数据流经各节点

```
[初始 State]
  requirement:      "用户输入"
  conversationId:   "uuid-xxx"
  userId:           123
  retryCount:       0

[planner 节点执行后]
  plannerOutput:    { taskAnalysis, subTasks, techStack }
  retryCount:       0          ← 重置

[coder 节点执行后]
  coderOutput:      { codeFiles, dependencies, explanation }

[reviewer 节点执行后 (不通过)]
  reviewOutput:     { issues, score: 60, accepted: false }
  retryCount:       1          ← +1

[reviewer 节点执行后 (通过)]
  reviewOutput:     { issues, score: 85, accepted: true }
  (不修改 retryCount)
```

---

## 四、会话记忆实现

### 4.1 存储模型

**`conversations` 表** — 每次代码生成任务对应一条记录：

| 字段 | 说明 |
|------|------|
| `conversation_id` | UUID，对外身份标识 |
| `user_id` | 用户 ID，关联 users 表 |
| `title` | 对话标题（取 requirement 前 200 字符） |
| `requirement` | 完整需求描述 |
| `status` | ACTIVE → COMPLETED |

**`conversation_messages` 表** — 存储 ChatMemory 消息记录：

| 字段 | 说明 |
|------|------|
| `conversation_id` | 关联 conversations 表 |
| `message_id` | 消息 UUID |
| `role` | user / assistant / system |
| `content` | 消息文本内容 |
| `message_index` | 消息序号，从 0 递增 |
| `metadata` | JSONB 扩展字段 |
| `parent_message_id` | 支持分支对话 |

### 4.2 MessageChatMemory 实现

`MessageChatMemory` 实现了 `org.springframework.ai.chat.memory.ChatMemory` 接口：

```java
public class MessageChatMemory implements ChatMemory {

    add(conversationId, messages)    → 自动编号，批量插入
    get(conversationId)             → 按 message_index 排序返回全部
    get(conversationId, lastN)      → 取最近 N 条
    clear(conversationId)           → 删除全部消息
}
```

每条消息写入时自动计算 `messageIndex`（基于当前最大序号），保证顺序正确。

### 4.3 会话记忆写入链路

```
CodeTaskServiceImpl.executeTask()
  │
  ├─ 1. chatMemory.add(conversationId, [UserMessage(requirement)])
  │       → INSERT INTO conversation_messages (role='user', content=requirement)
  │
  └─ 2. graph.execute(...)
          │
          ├─ plannerNode 完成 → persistNodeOutput
          │     → chatMemory.add(conversationId, [AssistantMessage("【任务规划】\n...")])
          │
          ├─ coderNode 完成 → persistNodeOutput
          │     → chatMemory.add(conversationId, [AssistantMessage("【生成代码】\n...")])
          │
          └─ reviewerNode 完成 → persistNodeOutput
                → chatMemory.add(conversationId, [AssistantMessage("【代码审查】\n...")])
```

每次任务执行在 `conversation_messages` 表中产生 **4 条记录**（1 user + 3 assistant）。

### 4.4 历史消息读取链路

Agent 执行前，Node Function 调用 `readHistory(conversationId, userId)`：

```
readHistory()
  → StpUtil.switchTo(userId)           // 恢复异步线程身份
  → chatMemory.get(conversationId)      // 读取全部历史消息
  → 截取最近 10 条
  → 格式化为文本: "【历史对话回顾】\nuser: ...\nassistant: ..."
  → StpUtil.endSwitch()
  → 传入 Agent execute(requirement, historyContext)
  → Agent 将 historyContext 拼入 User Prompt 顶部
```

历史消息按时间倒序取最近 10 条，转为纯文本插入 LLM Prompt，让 Agent 感知对话上下文。

---

## 五、多租户安全体系

三层防御，从应用到数据库逐层兜底：

```
┌─────────────────────────────────────────┐
│  第一层：Service 层过滤                  │
│  ConversationServiceImpl.list()         │
│  直接 .eq(UserId, StpUtil.getLoginId()) │
├─────────────────────────────────────────┤
│  第二层：AOP 归属校验                    │
│  @CheckOwnership + @ConversationId      │
│  OwnershipAspect 拦截 get/update/delete │
├─────────────────────────────────────────┤
│  第三层：数据库 RLS                      │
│  RlsInterceptor 设置 PG 会话变量        │
│  app.current_user_id → RLS Policy       │
└─────────────────────────────────────────┘
```

### 5.1 MessageChatMemory 归属校验

| 方法 | 校验方式 |
|------|---------|
| `add()` | `checkOwnership(conversationId)` |
| `get()` | `checkOwnership(conversationId)` |
| `clear()` | `checkOwnership(conversationId)` |

`checkOwnership` 查询 `conversations` 表的 `userId`，与当前登录用户比对，不一致则抛出异常。

### 5.2 异步线程身份传播

StateGraph 的 Node Function 运行在 `Schedulers.boundedElastic()` 线程池上，没有 `StpUtil` 的请求上下文。解决方案：

1. **写入 State**：`execute()` 将 `userId` 存入 `GraphState.KEY_USER_ID`
2. **恢复身份**：在异步回调中通过 `StpUtil.switchTo(userId)` 恢复身份
3. **清理**：`finally` 块中调用 `StpUtil.endSwitch()`

```java
StpUtil.switchTo(userId);
try {
    chatMemory.add(conversationId, messages);  // checkOwnership 和 RLS 都正常工作
} finally {
    StpUtil.endSwitch();
}
```

---

## 六、Agent Prompt 历史注入

每个 Agent 的 User Prompt 顶部预留 `{historyContext}` 占位符：

```
{historyContext}
请为以下需求制定代码实现计划：

需求描述：
{requirement}
```

格式化后的历史示例：

```
【历史对话回顾】
user: 帮我生成一个 Spring Boot 项目
assistant: 【任务规划】
1. 创建项目结构（pom.xml, 主类）
2. 配置数据库连接
3. 编写 REST API
assistant: 【生成代码】
...
```

Agent 执行时不直接操作 `ChatMemory`，仅接收预格式化的纯文本字符串，保持 Agent 的纯计算特性。

---

## 七、事件流（SSE）

图执行结果通过 `Flux<StreamEvent>` 以 SSE 形式推送给前端：

| 事件类型 | 来源 | 内容 |
|----------|------|------|
| `start` | execute() | "开始执行代码生成任务" |
| `planner` | toNodeEvents | "开始分析需求，拆解任务..." |
| `planner_stream` | toNodeEvents | 任务分析文本 |
| `coder` | toNodeEvents | "开始生成代码..." |
| `coder_stream` | toNodeEvents | 代码说明文本 |
| `reviewer` | toNodeEvents | "开始审查代码..." |
| `reviewer_stream` | toNodeEvents | 审查总结文本 |
| `end` | toFinalEvents | 结果摘要（通过/失败） |
| `result` | toFinalEvents | JSON 格式完整结果数据 |
| `error` | onErrorResume | 异常信息 |

长文本按 80 字符切块分发，前端逐块渲染实现流式效果。

---

## 八、文件索引

| 文件 | 关键职责 |
|------|---------|
| `graph/CodeGenerationGraph.java` | StateGraph 构建、节点编排、条件路由、消息持久化 |
| `graph/GraphState.java` | 状态键常量定义 |
| `service/impl/CodeTaskServiceImpl.java` | 任务入口，创建 Conversation 并驱动 Graph |
| `service/MessageChatMemory.java` | ChatMemory 接口实现，持久化到 conversation_messages |
| `agent/PlannerAgent.java` | 任务规划 Agent |
| `agent/CoderAgent.java` | 代码生成 Agent |
| `agent/ReviewerAgent.java` | 代码审查 Agent |
| `entity/Conversation.java` | 对话/任务会话实体 |
| `entity/ConversationMessage.java` | 消息实体，ChatMemory 持久化载体 |
| `security/aspect/OwnershipAspect.java` | AOP 归属权校验 |
| `config/RlsInterceptor.java` | 数据库行级安全拦截器 |
