package rj.agent.graph;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import rj.agent.agent.CoderAgent;
import rj.agent.agent.PlannerAgent;
import rj.agent.agent.ReviewerAgent;
import rj.agent.model.CoderOutput;
import rj.agent.model.PlannerOutput;
import rj.agent.model.ReviewOutput;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 代码生成工作流图 - 协调多 Agent 协作的完整代码生成流程
 *
 * <p>职责：实现基于状态机的代码生成工作流，通过 Planner → Coder → Reviewer 的循环迭代，
 * 自动生成高质量代码，直到审查通过或达到最大重试次数。</p>
 *
 * <p>工作流程：</p>
 * <ol>
 *   <li><b>Planner 阶段</b>：分析用户需求，拆解为具体的子任务计划</li>
 *   <li><b>Coder 阶段</b>：根据任务计划生成完整的源代码文件</li>
 *   <li><b>Reviewer 阶段</b>：审查生成的代码质量和正确性</li>
 *   <li><b>条件分支</b>：
 *     <ul>
 *       <li>如果审查通过（accepted=true），流程结束，返回成功结果</li>
 *       <li>如果审查未通过且未达到最大重试次数，将审查反馈传递给 Coder 重新生成</li>
 *       <li>如果达到最大重试次数（3次），流程结束，返回失败结果</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>状态管理：</p>
 * <ul>
 *   <li>STATE_REQUIREMENT: 用户需求描述</li>
 *   <li>STATE_PLAN: 任务规划结果（JSON 字符串）</li>
 *   <li>STATE_CODE: 代码生成结果（JSON 字符串）</li>
 *   <li>STATE_REVIEW: 代码审查结果（JSON 字符串）</li>
 *   <li>STATE_RETRY_COUNT: 当前重试次数</li>
 *   <li>STATE_ACCEPTED: 代码是否被接受</li>
 *   <li>STATE_ERROR: 错误信息（如果有）</li>
 *   <li>STATE_PLAN_OBJ/CODE_OBJ/REVIEW_OBJ: 对应的对象形式（非序列化）</li>
 * </ul>
 *
 * <p>事件通知机制：</p>
 * <p>通过 Consumer&lt;GraphEvent&gt; 回调函数实时推送工作流执行进度，
 * 支持前端展示实时的任务执行状态和日志信息。</p>
 */
@Slf4j
public class CodeGenerationGraph {

    public static final String STATE_REQUIREMENT = "requirement";
    public static final String STATE_PLAN = "plan";
    public static final String STATE_CODE = "code";
    public static final String STATE_REVIEW = "review";
    public static final String STATE_RETRY_COUNT = "retryCount";
    public static final String STATE_ACCEPTED = "accepted";
    public static final String STATE_ERROR = "error";
    public static final String STATE_PLAN_OBJ = "planObj";
    public static final String STATE_CODE_OBJ = "codeObj";
    public static final String STATE_REVIEW_OBJ = "reviewObj";

    private static final int MAX_RETRIES = 3;

    private final PlannerAgent plannerAgent;
    private final CoderAgent coderAgent;
    private final ReviewerAgent reviewerAgent;
    private final ObjectMapper objectMapper;

    /**
     * 构造函数，注入三个核心 Agent 和 JSON 序列化工具
     *
     * @param plannerAgent 任务规划 Agent，负责需求分析和任务拆解
     * @param coderAgent 代码生成 Agent，负责根据计划生成代码
     * @param reviewerAgent 代码审查 Agent，负责审查代码质量
     * @param objectMapper JSON 序列化工具，用于状态对象的序列化
     */
    public CodeGenerationGraph(PlannerAgent plannerAgent, CoderAgent coderAgent,
                               ReviewerAgent reviewerAgent, ObjectMapper objectMapper) {
        this.plannerAgent = plannerAgent;
        this.coderAgent = coderAgent;
        this.reviewerAgent = reviewerAgent;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行代码生成全流程：Planner → Coder → Reviewer → (循环至通过或达到最大重试次数)
     *
     * <p>该方法实现了完整的代码生成工作流，包含以下关键步骤：</p>
     * <ol>
     *   <li>初始化状态容器，设置初始重试次数为 0，接受状态为 false</li>
     *   <li>执行 Planner Agent 进行需求分析和任务规划</li>
     *   <li>进入 Coder-Reviewer 循环：
     *     <ul>
     *       <li>Coder 根据规划和历史审查反馈生成代码</li>
     *       <li>Reviewer 审查生成的代码并给出评分和建议</li>
     *       <li>如果审查通过，退出循环并标记为 accepted</li>
     *       <li>如果未通过且未达到最大重试次数，将审查结果反馈给 Coder 重新生成</li>
     *       <li>如果达到最大重试次数（3次），退出循环并标记为失败</li>
     *     </ul>
     *   </li>
     *   <li>捕获并处理异常，将错误信息存入状态容器</li>
     *   <li>返回包含所有中间结果和最终状态的 Map</li>
     * </ol>
     *
     * <p>每次状态变化都会通过 eventConsumer 推送事件，支持实时监控任务进度。</p>
     *
     * @param requirement 用户的代码生成需求描述，不能为空
     * @param eventConsumer 事件消费者回调函数，用于接收工作流执行过程中的事件通知
     *                      事件包含节点名称、目标节点和消息内容
     * @return 包含完整执行状态的 Map，主要包含以下键值：
     *         <ul>
     *           <li>requirement: 原始需求描述</li>
     *           <li>plan/planObj: 任务规划结果（JSON 字符串和对象）</li>
     *           <li>code/codeObj: 代码生成结果（JSON 字符串和对象）</li>
     *           <li>review/reviewObj: 代码审查结果（JSON 字符串和对象）</li>
     *           <li>retryCount: 实际重试次数</li>
     *           <li>accepted: 代码是否通过审查（boolean）</li>
     *           <li>error: 错误信息（如果发生异常）</li>
     *         </ul>
     */
    public Map<String, Object> execute(String requirement, Consumer<GraphEvent> eventConsumer) {
        Map<String, Object> state = new HashMap<>();
        state.put(STATE_REQUIREMENT, requirement);
        state.put(STATE_RETRY_COUNT, 0);
        state.put(STATE_ACCEPTED, false);

        try {
            // === Node 1: Planner ===
            eventConsumer.accept(new GraphEvent("PLANNER", "planner", "开始分析需求，拆解任务..."));
            PlannerOutput plan = plannerAgent.execute(requirement);
            state.put(STATE_PLAN, objectMapper.writeValueAsString(plan));
            state.put(STATE_PLAN_OBJ, plan);
            log.info("[Graph] Planner 完成，子任务数: {}",
                    plan.getSubTasks() != null ? plan.getSubTasks().size() : 0);

            // === Node 2: Coder ===
            ReviewOutput previousReview = null;
            int retryCount = 0;

            do {
                eventConsumer.accept(new GraphEvent("CODER", "coder",
                        String.format("开始生成代码(第%d次)...", retryCount + 1)));

                CoderOutput code = coderAgent.execute(requirement, plan, previousReview);
                state.put(STATE_CODE, objectMapper.writeValueAsString(code));
                state.put(STATE_CODE_OBJ, code);
                log.info("[Graph] Coder 完成，文件数: {}",
                        code.getCodeFiles() != null ? code.getCodeFiles().size() : 0);

                // === Node 3: Reviewer ===
                eventConsumer.accept(new GraphEvent("REVIEWER", "reviewer", "开始审查代码..."));
                ReviewOutput review = reviewerAgent.execute(requirement, code);
                state.put(STATE_REVIEW, objectMapper.writeValueAsString(review));
                state.put(STATE_REVIEW_OBJ, review);
                log.info("[Graph] Reviewer 完成: accepted={}, score={}", review.isAccepted(), review.getScore());

                // === Conditional Edge: 根据审查结果决定下一步 ===
                if (review.isAccepted()) {
                    state.put(STATE_ACCEPTED, true);
                    eventConsumer.accept(new GraphEvent("ACCEPTED", "end",
                            String.format("✅ 代码审查通过！评分: %d/100", review.getScore())));
                    break;
                }

                retryCount++;
                state.put(STATE_RETRY_COUNT, retryCount);

                if (retryCount >= MAX_RETRIES) {
                    eventConsumer.accept(new GraphEvent("FAILED", "end",
                            "❌ 已达到最大重试次数(3次)，代码未通过审查"));
                    break;
                }

                eventConsumer.accept(new GraphEvent("REWORK", "coder",
                        String.format("⚠️ 代码未通过审查(评分: %d)，准备第%d次重试...",
                                review.getScore(), retryCount + 1)));

                previousReview = review;

            } while (true);

        } catch (JsonProcessingException e) {
            log.error("[Graph] JSON处理异常", e);
            state.put(STATE_ERROR, "JSON序列化异常: " + e.getMessage());
            eventConsumer.accept(new GraphEvent("ERROR", "end", "系统异常: " + e.getMessage()));
        } catch (Exception e) {
            log.error("[Graph] 执行异常", e);
            state.put(STATE_ERROR, "执行异常: " + e.getMessage());
            eventConsumer.accept(new GraphEvent("ERROR", "end", "系统异常: " + e.getMessage()));
        }

        return state;
    }

    /**
     * 静态工厂方法，快速创建 GraphEvent 事件对象
     *
     * @param node 当前执行的节点名称（如 PLANNER、CODER、REVIEWER）
     * @param target 目标节点名称或结束标记（如 end）
     * @param message 事件描述消息，用于前端展示
     * @return 新创建的 GraphEvent 记录对象
     */
    public static GraphEvent event(String node, String target, String message) {
        return new GraphEvent(node, target, message);
    }

    /**
     * 工作流事件记录类，用于封装节点执行过程中的事件信息
     *
     * <p>该记录类包含三个字段，用于描述工作流中每个节点的执行状态和转移信息。</p>
     *
     * @param node 当前执行的节点名称（如 "PLANNER"、"CODER"、"REVIEWER"）
     * @param target 下一个目标节点名称或终止标记（如 "end"）
     * @param message 事件的详细描述消息，通常包含进度信息和状态说明
     */
    public record GraphEvent(String node, String target, String message) {
    }
}
