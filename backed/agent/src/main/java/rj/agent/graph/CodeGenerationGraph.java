package rj.agent.graph;

import com.alibaba.cloud.ai.graph.*;
import rj.agent.config.AsyncUserContext;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import rj.agent.agent.CoderAgent;
import rj.agent.agent.PlannerAgent;
import rj.agent.agent.ReviewerAgent;
import rj.agent.model.CoderOutput;
import rj.agent.model.PlannerOutput;
import rj.agent.model.ReviewOutput;
import rj.agent.model.StreamEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * 代码生成工作流图 — 基于 Spring AI Alibaba StateGraph 的多 Agent 协作流程
 *
 * <p>将代码生成流程定义为有向图：Planner → Coder → Reviewer ↴
 * Reviewer 根据审查结果条件路由：通过 → 结束；不通过且未达上限 → 回到 Coder 重试；
 * 不通过且达上限 → 结束。</p>
 *
 * <p>以 {@link Flux}<{@link StreamEvent}> 形式实时推送工作流执行进度。</p>
 */
@Slf4j
public class CodeGenerationGraph {

    private static final int MAX_RETRIES = 3;

    /** 条件边路由表：路由结果 → 目标节点 */
    private static final Map<String, String> ROUTING_MAP = Map.of(
            GraphState.ROUTE_ACCEPTED, END,
            GraphState.ROUTE_MAX_RETRIES, END,
            GraphState.ROUTE_RETRY, GraphState.NODE_CODER
    );

    private final PlannerAgent plannerAgent;
    private final CoderAgent coderAgent;
    private final ReviewerAgent reviewerAgent;
    private final ChatMemory chatMemory;

    /** 编译后的可执行图（线程安全，可复用） */
    private final CompiledGraph compiledGraph;

    public CodeGenerationGraph(PlannerAgent plannerAgent, CoderAgent coderAgent,
                               ReviewerAgent reviewerAgent,
                               ChatMemory chatMemory) throws GraphStateException {
        this.plannerAgent = plannerAgent;
        this.coderAgent = coderAgent;
        this.reviewerAgent = reviewerAgent;
        this.chatMemory = chatMemory;
        this.compiledGraph = buildAndCompile();
        log.info("[Graph] StateGraph 编译完成");
    }

    // ==================== Graph 构建 ====================

    /**
     * 构建并编译完整的 StateGraph
     * <pre>
     * START → [planner] → [coder] → [reviewer]
     *                                    │
     *                ┌───────────────────┼───────────────────┐
     *                │ accepted          │ max_retries       │ retry
     *                ▼                   ▼                   ▼
     *               END                 END               [coder] (循环)
     * </pre>
     */
    private CompiledGraph buildAndCompile() throws GraphStateException {
        StateGraph graph = new StateGraph("CodeGeneration", HashMap::new);

        graph.addNode(GraphState.NODE_PLANNER, node_async(this::plannerNode));
        graph.addNode(GraphState.NODE_CODER, node_async(this::coderNode));
        graph.addNode(GraphState.NODE_REVIEWER, node_async(this::reviewerNode));

        graph.addEdge(START, GraphState.NODE_PLANNER);
        graph.addEdge(GraphState.NODE_PLANNER, GraphState.NODE_CODER);
        graph.addEdge(GraphState.NODE_CODER, GraphState.NODE_REVIEWER);

        graph.addConditionalEdges(
                GraphState.NODE_REVIEWER,
                edge_async(this::routeAfterReview),
                ROUTING_MAP
        );
        return graph.compile();
    }

    // ==================== Node Functions ====================

    /**
     * Planner 节点：分析需求，拆解任务
     */
    private Map<String, Object> plannerNode(OverAllState state) {
        String requirement = state.value(GraphState.KEY_REQUIREMENT, "");
        log.info("[Graph] Planner 节点开始执行");

        String conversationId = state.value(GraphState.KEY_CONVERSATION_ID, "");
        Long userId = state.value(GraphState.KEY_USER_ID, 0L);
        String historyContext = readHistory(conversationId, userId);

        PlannerOutput output = plannerAgent.execute(requirement, historyContext);
        log.info("[Graph] Planner 完成，子任务数: {}",
                output.getSubTasks() != null ? output.getSubTasks().size() : 0);

        Map<String, Object> updates = new HashMap<>();
        updates.put(GraphState.KEY_PLANNER_OUTPUT, output);
        updates.put(GraphState.KEY_RETRY_COUNT, 0);
        return updates;
    }

    /**
     * Coder 节点：根据规划和审查反馈生成代码
     */
    private Map<String, Object> coderNode(OverAllState state) {
        String requirement = state.value(GraphState.KEY_REQUIREMENT, "");
        PlannerOutput plan = state.value(GraphState.KEY_PLANNER_OUTPUT, (PlannerOutput) null);
        ReviewOutput previousReview = state.value(GraphState.KEY_REVIEW_OUTPUT, (ReviewOutput) null);

        String conversationId = state.value(GraphState.KEY_CONVERSATION_ID, "");
        Long userId = state.value(GraphState.KEY_USER_ID, 0L);
        String historyContext = readHistory(conversationId, userId);

        int retryCount = state.value(GraphState.KEY_RETRY_COUNT, 0);
        log.info("[Graph] Coder 节点开始执行 (第{}次)", retryCount + 1);

        CoderOutput output = coderAgent.execute(requirement, plan, previousReview, historyContext);
        log.info("[Graph] Coder 完成，文件数: {}",
                output.getCodeFiles() != null ? output.getCodeFiles().size() : 0);

        return Map.of(GraphState.KEY_CODER_OUTPUT, output);
    }

    /**
     * Reviewer 节点：审查代码质量
     */
    private Map<String, Object> reviewerNode(OverAllState state) {
        String requirement = state.value(GraphState.KEY_REQUIREMENT, "");
        CoderOutput code = state.value(GraphState.KEY_CODER_OUTPUT, (CoderOutput) null);
        log.info("[Graph] Reviewer 节点开始执行");

        String conversationId = state.value(GraphState.KEY_CONVERSATION_ID, "");
        Long userId = state.value(GraphState.KEY_USER_ID, 0L);
        String historyContext = readHistory(conversationId, userId);

        ReviewOutput output = reviewerAgent.execute(requirement, code, historyContext);
        log.info("[Graph] Reviewer 完成: accepted={}, score={}",
                output.isAccepted(), output.getScore());

        if (output.isAccepted()) {
            return Map.of(GraphState.KEY_REVIEW_OUTPUT, output);
        }

        int retryCount = state.value(GraphState.KEY_RETRY_COUNT, 0);
        Map<String, Object> updates = new HashMap<>();
        updates.put(GraphState.KEY_REVIEW_OUTPUT, output);
        updates.put(GraphState.KEY_RETRY_COUNT, retryCount + 1);
        return updates;
    }

    // ==================== Condition Edge ====================

    /**
     * 条件路由：根据审查结果和重试次数决定下一步
     */
    private String routeAfterReview(OverAllState state) {
        ReviewOutput review = state.value(GraphState.KEY_REVIEW_OUTPUT, (ReviewOutput) null);
        int retryCount = state.value(GraphState.KEY_RETRY_COUNT, 0);

        if (review != null && review.isAccepted()) {
            log.info("[Graph] 条件路由 → accepted (评分: {}/100)", review.getScore());
            return GraphState.ROUTE_ACCEPTED;
        }
        if (retryCount >= MAX_RETRIES) {
            log.info("[Graph] 条件路由 → max_retries (已达上限 {})", MAX_RETRIES);
            return GraphState.ROUTE_MAX_RETRIES;
        }
        log.info("[Graph] 条件路由 → retry (评分: {}, {}/{})",
                review != null ? review.getScore() : "N/A", retryCount, MAX_RETRIES);
        return GraphState.ROUTE_RETRY;
    }

    // ==================== 外部执行入口 ====================

    /**
     * 执行代码生成全流程并以 Flux 流式输出事件
     */
    public Flux<StreamEvent> execute(String requirement, String conversationId, Long userId) {
        log.info("[Graph] 开始执行，requirement={}", truncate(requirement, 100));

        Map<String, Object> inputs = new HashMap<>();
        inputs.put(GraphState.KEY_REQUIREMENT, requirement);
        inputs.put(GraphState.KEY_CONVERSATION_ID, conversationId);
        inputs.put(GraphState.KEY_USER_ID, userId);
        inputs.put(GraphState.KEY_RETRY_COUNT, 0);

        RunnableConfig config = RunnableConfig.builder()
                .threadId("code-gen-" + System.currentTimeMillis())
                .build();

        AtomicReference<OverAllState> lastStateRef = new AtomicReference<>();

        return Flux.concat(
                Flux.just(StreamEvent.phase("start", "开始执行代码生成任务")),
                compiledGraph.stream(inputs, config)
                        .doOnNext(nodeOutput -> lastStateRef.set(nodeOutput.state()))
                        .doOnNext(nodeOutput -> persistNodeOutput(nodeOutput, conversationId, userId))
                        .concatMap(this::toNodeEvents)
                        .concatWith(Flux.defer(() -> toFinalEvents(lastStateRef.get())))
        ).onErrorResume(e -> {
            log.error("[Graph] 执行异常", e);
            return Flux.just(StreamEvent.error("执行异常: " + e.getMessage()));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ==================== 节点输出持久化 ====================

    /**
     * 将节点执行结果持久化到 ChatMemory 中。
     * 在异步线程中执行，通过 AsyncUserContext 传递用户身份，
     * 确保 RLS 拦截器能正确设置数据库会话变量。
     */
    private void persistNodeOutput(NodeOutput nodeOutput, String conversationId, Long userId) {
        if (userId == null || userId == 0L) return;
        AsyncUserContext.setUserId(userId);
        try {
            String nodeName = nodeOutput.node();
            OverAllState state = nodeOutput.state();
            switch (nodeName) {
                case GraphState.NODE_PLANNER -> {
                    PlannerOutput plan = state.value(GraphState.KEY_PLANNER_OUTPUT, (PlannerOutput) null);
                    if (plan != null) {
                        chatMemory.add(conversationId, List.of(new AssistantMessage("【任务规划】\n" + plan.getTaskAnalysis())));
                    }
                }
                case GraphState.NODE_CODER -> {
                    CoderOutput code = state.value(GraphState.KEY_CODER_OUTPUT, (CoderOutput) null);
                    if (code != null) {
                        chatMemory.add(conversationId, List.of(new AssistantMessage("【生成代码】\n" + code.getExplanation())));
                    }
                }
                case GraphState.NODE_REVIEWER -> {
                    ReviewOutput review = state.value(GraphState.KEY_REVIEW_OUTPUT, (ReviewOutput) null);
                    if (review != null) {
                        chatMemory.add(conversationId, List.of(new AssistantMessage("【代码审查】\n" + review.getSummary() + " (评分: " + review.getScore() + "/100)")));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[Graph] 持久化消息失败 node={}", nodeOutput.node(), e);
        } finally {
            AsyncUserContext.clear();
        }
    }

    // ==================== 历史消息读取 ====================

    /**
     * 从 ChatMemory 读取当前对话的历史消息，格式化为文本上下文供 Agent 使用。
     * 在异步线程中执行，通过 AsyncUserContext 传递用户身份，
     * 确保 RLS 拦截器能正确设置数据库会话变量。
     */
    private String readHistory(String conversationId, Long userId) {
        if (conversationId == null || conversationId.isBlank() || userId == null || userId == 0L) {
            return "";
        }
        AsyncUserContext.setUserId(userId);
        try {
            List<Message> all = chatMemory.get(conversationId);
            if (all == null || all.isEmpty()) return "";
            // 取最近 10 条作为上下文
            List<Message> recent = all.size() > 10
                    ? all.subList(all.size() - 10, all.size())
                    : all;
            StringBuilder sb = new StringBuilder("\n【历史对话回顾】\n");
            for (Message msg : recent) {
                String role = msg.getMessageType().name().toLowerCase();
                String text = msg.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(role).append(": ").append(text).append("\n");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("[Graph] 读取历史消息失败", e);
            return "";
        } finally {
            AsyncUserContext.clear();
        }
    }

    // ==================== NodeOutput → StreamEvent 映射 ====================

    /**
     * 将单个 NodeOutput 转换为对应的 StreamEvent 事件序列
     */
    private Flux<StreamEvent> toNodeEvents(NodeOutput nodeOutput) {
        String nodeName = nodeOutput.node();
        OverAllState state = nodeOutput.state();

        List<StreamEvent> events = new ArrayList<>();

        switch (nodeName) {
            case GraphState.NODE_PLANNER -> {
                events.add(StreamEvent.phase("planner", "开始分析需求，拆解任务..."));
                PlannerOutput plan = state.value(GraphState.KEY_PLANNER_OUTPUT, (PlannerOutput) null);
                if (plan != null) {
                    streamAgentOutput("planner", plan.getTaskAnalysis(), events);
                    streamAgentOutput("planner", "技术栈: " + plan.getTechStack(), events);
                    if (plan.getRiskWarning() != null && !plan.getRiskWarning().isBlank()) {
                        streamAgentOutput("planner", "风险警告: " + plan.getRiskWarning(), events);
                    }
                }
            }
            case GraphState.NODE_CODER -> {
                int retryCount = state.value(GraphState.KEY_RETRY_COUNT, 0);
                String msg = retryCount == 0
                        ? "开始生成代码..."
                        : String.format("开始生成代码(第%d次)...", retryCount + 1);
                events.add(StreamEvent.phase("coder", msg));
                if (retryCount > 0) {
                    ReviewOutput prevReview = state.value(GraphState.KEY_REVIEW_OUTPUT, (ReviewOutput) null);
                    if (prevReview != null) {
                        streamAgentOutput("coder",
                                String.format("正在根据审查反馈修复问题(评分: %d)...", prevReview.getScore()), events);
                    }
                }
                CoderOutput coder = state.value(GraphState.KEY_CODER_OUTPUT, (CoderOutput) null);
                if (coder != null) {
                    streamAgentOutput("coder", coder.getExplanation(), events);
                    if (coder.getUncertaintyNote() != null && !coder.getUncertaintyNote().isBlank()) {
                        streamAgentOutput("coder", "不确定性说明: " + coder.getUncertaintyNote(), events);
                    }
                }
            }
            case GraphState.NODE_REVIEWER -> {
                events.add(StreamEvent.phase("reviewer", "开始审查代码..."));
                ReviewOutput review = state.value(GraphState.KEY_REVIEW_OUTPUT, (ReviewOutput) null);
                if (review != null) {
                    streamAgentOutput("reviewer", review.getSummary(), events);
                }
            }
            default -> {
                // 其他节点（如有）不做特殊处理
            }
        }

        return Flux.fromIterable(events);
    }

    /**
     * 根据最终状态生成结束事件和结果数据
     */
    private Flux<StreamEvent> toFinalEvents(OverAllState state) {
        List<StreamEvent> events = new ArrayList<>();

        if (state == null) {
            log.warn("[Graph] 最终状态为空，跳过结果事件");
            return Flux.empty();
        }

        PlannerOutput plan = state.value(GraphState.KEY_PLANNER_OUTPUT, (PlannerOutput) null);
        CoderOutput code = state.value(GraphState.KEY_CODER_OUTPUT, (CoderOutput) null);
        ReviewOutput review = state.value(GraphState.KEY_REVIEW_OUTPUT, (ReviewOutput) null);
        int retryCount = state.value(GraphState.KEY_RETRY_COUNT, 0);
        boolean accepted = review != null && review.isAccepted();

        if (accepted) {
            events.add(StreamEvent.phase("end",
                    String.format("代码审查通过！评分: %d/100", review.getScore())));
        } else {
            events.add(StreamEvent.phase("end",
                    "已达到最大重试次数(3次)，代码未通过审查"));
        }

        Map<String, Object> resultData = new LinkedHashMap<>();
        resultData.put("accepted", accepted);
        resultData.put("plan", plan);
        resultData.put("code", code);
        resultData.put("review", review);
        resultData.put("retryCount", retryCount);
        events.add(StreamEvent.result(resultData));

        return Flux.fromIterable(events);
    }

    // ==================== 工具方法 ====================

    private void streamAgentOutput(String phase, String content, List<StreamEvent> events) {
        if (content == null || content.isBlank()) return;
        for (String chunk : splitContent(content, 80)) {
            events.add(StreamEvent.phase(phase + "_stream", chunk));
        }
    }

    private static List<String> splitContent(String text, int maxLen) {
        List<String> chunks = new ArrayList<>();
        int len = text.length();
        for (int i = 0; i < len; i += maxLen) {
            chunks.add(text.substring(i, Math.min(i + maxLen, len)));
        }
        return chunks;
    }

    private static String truncate(String str, int maxLen) {
        return str != null && str.length() > maxLen
                ? str.substring(0, maxLen) + "..."
                : str;
    }
}
