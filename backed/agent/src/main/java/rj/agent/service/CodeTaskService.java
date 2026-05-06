package rj.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import rj.agent.agent.CoderAgent;
import rj.agent.agent.PlannerAgent;
import rj.agent.agent.ReviewerAgent;
import rj.agent.graph.CodeGenerationGraph;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 代码生成任务服务 - 协调多 Agent 协作的代码生成流程
 *
 * <p>职责：统筹 PlannerAgent、CoderAgent、ReviewerAgent 三个智能体，
 * 完成从需求分析到代码生成的完整流程，并通过 SSE 实时推送执行进度。</p>
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>异步执行代码生成任务，避免阻塞主线程</li>
 *   <li>通过 SSE（Server-Sent Events）实时推送任务执行进度和状态</li>
 *   <li>协调三个 Agent 的顺序执行：规划 → 编码 → 审查</li>
 *   <li>处理任务执行过程中的异常和错误</li>
 *   <li>封装最终结果并返回给前端</li>
 * </ul>
 *
 * <p>工作流程：</p>
 * <ol>
 *   <li>接收用户需求描述</li>
 *   <li>创建唯一的任务 ID 用于追踪</li>
 *   <li>异步执行 CodeGenerationGraph 工作流</li>
 *   <li>实时推送各阶段事件（START、AGENT_EVENT、COMPLETED、ERROR）</li>
 *   <li>完成任务后关闭 SSE 连接</li>
 * </ol>
 *
 * <p>SSE 事件类型：</p>
 * <ul>
 *   <li>START：任务开始，包含任务 ID 和需求描述</li>
 *   <li>AGENT_EVENT：Agent 执行过程中的节点切换事件</li>
 *   <li>COMPLETED：任务完成，包含规划、代码、审查结果</li>
 *   <li>ERROR：任务执行失败，包含错误信息</li>
 * </ul>
 */
@Slf4j
@Service
public class CodeTaskService {

    private final PlannerAgent plannerAgent;
    private final CoderAgent coderAgent;
    private final ReviewerAgent reviewerAgent;
    private final ObjectMapper objectMapper;

    /**
     * 构造函数，注入所需的 Agent 和工具类
     *
     * @param plannerAgent 任务规划 Agent，负责将需求拆解为子任务
     * @param coderAgent 代码生成 Agent，负责根据计划生成代码
     * @param reviewerAgent 代码审查 Agent，负责审查生成的代码质量
     * @param objectMapper JSON 序列化工具，用于 SSE 事件数据转换
     */
    public CodeTaskService(PlannerAgent plannerAgent, CoderAgent coderAgent,
                           ReviewerAgent reviewerAgent, ObjectMapper objectMapper) {
        this.plannerAgent = plannerAgent;
        this.coderAgent = coderAgent;
        this.reviewerAgent = reviewerAgent;
        this.objectMapper = objectMapper;
    }

    /**
     * 异步执行代码生成任务，通过 SSE 推送进度
     *
     * <p>该方法会立即返回，实际的任务执行在后台线程中进行。
     * 执行过程中会通过 SseEmitter 实时推送以下事件：</p>
     * <ul>
     *   <li>START：任务启动事件，包含任务 ID 和需求信息</li>
     *   <li>AGENT_EVENT：Agent 工作流中的节点切换事件</li>
     *   <li>COMPLETED：任务完成事件，包含最终的规划、代码和审查结果</li>
     *   <li>ERROR：任务失败事件，包含错误信息</li>
     * </ul>
     *
     * @param requirement 用户的代码生成需求描述，不能为空
     * @param emitter SSE 发射器，用于向客户端推送实时事件
     */
    public void executeTask(String requirement, SseEmitter emitter) {
        String taskId = UUID.randomUUID().toString().substring(0, 8);
        log.info("[Task-{}] 开始新任务: {}", taskId, truncate(requirement, 100));

        CompletableFuture.runAsync(() -> {
            try {
                CodeGenerationGraph graph = new CodeGenerationGraph(
                        plannerAgent, coderAgent, reviewerAgent, objectMapper);

                sendEvent(emitter, "START", Map.of(
                        "taskId", taskId,
                        "node", "start",
                        "message", "任务开始执行",
                        "requirement", requirement
                ));

                Map<String, Object> result = graph.execute(requirement, event -> {
                    try {
                        Map<String, Object> data = Map.of(
                                "node", event.node(),
                                "target", event.target(),
                                "message", event.message()
                        );
                        sendEvent(emitter, "AGENT_EVENT", data);
                    } catch (Exception e) {
                        log.error("发送SSE事件失败", e);
                    }
                });

                boolean accepted = (boolean) result.getOrDefault(CodeGenerationGraph.STATE_ACCEPTED, false);
                String error = (String) result.get(CodeGenerationGraph.STATE_ERROR);

                if (error != null) {
                    sendEvent(emitter, "ERROR", Map.of(
                            "message", error
                    ));
                } else {
                    Object codeObj = result.get(CodeGenerationGraph.STATE_CODE_OBJ);
                    Object reviewObj = result.get(CodeGenerationGraph.STATE_REVIEW_OBJ);
                    Object planObj = result.get(CodeGenerationGraph.STATE_PLAN_OBJ);

                    Map<String, Object> finalData = new java.util.LinkedHashMap<>();
                    finalData.put("accepted", accepted);
                    finalData.put("plan", planObj);
                    finalData.put("code", codeObj);
                    finalData.put("review", reviewObj);

                    sendEvent(emitter, "COMPLETED", finalData);
                }

                emitter.complete();
                log.info("[Task-{}] 任务完成, accepted={}", taskId, accepted);

            } catch (Exception e) {
                log.error("[Task-{}] 任务执行异常", taskId, e);
                try {
                    sendEvent(emitter, "ERROR", Map.of(
                            "message", "任务执行失败: " + e.getMessage()
                    ));
                } catch (Exception ex) {
                    log.error("发送错误事件失败", ex);
                }
                emitter.completeWithError(e);
            }
        });
    }

    /**
     * 通过 SSE 发送事件到客户端
     *
     * <p>将事件名称和数据对象序列化为 JSON 格式，
     * 并通过 SseEmitter 发送给前端。事件 ID 使用当前时间戳。</p>
     *
     * @param emitter SSE 发射器，用于向客户端推送事件
     * @param eventName 事件名称，如 START、AGENT_EVENT、COMPLETED、ERROR
     * @param data 事件数据对象，会被序列化为 JSON 字符串
     * @throws RuntimeException 当 SSE 发送失败时抛出运行时异常
     */
    private void sendEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(objectMapper.writeValueAsString(data))
                    .id(String.valueOf(System.currentTimeMillis())));
        } catch (IOException e) {
            throw new RuntimeException("SSE发送失败", e);
        }
    }

    /**
     * 截断字符串到指定长度，超出部分用省略号替代
     *
     * @param str 待截断的字符串，可以为 null
     * @param maxLen 最大长度，超过此长度会被截断
     * @return 截断后的字符串，如果原字符串为 null 则返回 null
     */
    private String truncate(String str, int maxLen) {
        return str != null && str.length() > maxLen
                ? str.substring(0, maxLen) + "..."
                : str;
    }
}
