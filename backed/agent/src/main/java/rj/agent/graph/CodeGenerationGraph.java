package rj.agent.graph;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;
import rj.agent.agent.CoderAgent;
import rj.agent.agent.PlannerAgent;
import rj.agent.agent.ReviewerAgent;
import rj.agent.model.CoderOutput;
import rj.agent.model.PlannerOutput;
import rj.agent.model.ReviewOutput;
import rj.agent.model.StreamEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 代码生成工作流图 — 协调多 Agent 协作的完整代码生成流程
 *
 * <p>职责：实现基于状态机的代码生成工作流，通过 Planner → Coder → Reviewer 的循环迭代，
 * 自动生成高质量代码，直到审查通过或达到最大重试次数。</p>
 *
 * <p>以 {@link Flux}<{@link StreamEvent}> 形式实时推送工作流执行进度，
 * 支持前端展示实时的阶段切换、进度信息和最终结果。</p>
 */
@Slf4j
public class CodeGenerationGraph {

    private static final int MAX_RETRIES = 3;

    private final PlannerAgent plannerAgent;
    private final CoderAgent coderAgent;
    private final ReviewerAgent reviewerAgent;

    public CodeGenerationGraph(PlannerAgent plannerAgent, CoderAgent coderAgent,
                               ReviewerAgent reviewerAgent) {
        this.plannerAgent = plannerAgent;
        this.coderAgent = coderAgent;
        this.reviewerAgent = reviewerAgent;
    }

    /**
     * 执行代码生成全流程并以 Flux 流式输出事件
     *
     * <p>流程：Planner → Coder → Reviewer → (循环至通过或达到最大重试次数)</p>
     *
     * <p>返回的 Flux 在 {@link reactor.core.scheduler.Schedulers#boundedElastic()} 上执行，
     * 避免阻塞主线程。事件类型包括：</p>
     * <ul>
     *   <li><b>phase</b> — 阶段切换事件（planner / coder / reviewer）</li>
     *   <li><b>result</b> — 最终结果事件（包含完整的 plan / code / review / accepted）</li>
     *   <li><b>error</b> — 错误事件</li>
     * </ul>
     *
     * @param requirement 用户的代码生成需求描述
     * @return Flux 流式事件序列
     */
    public Flux<StreamEvent> execute(String requirement) {
        return Flux.<StreamEvent>create(sink -> {
            log.info("[Graph] 开始执行，requirement={}", truncate(requirement, 100));

            try {
                // === Phase 1: Planner ===
                sink.next(StreamEvent.phase("planner", "开始分析需求，拆解任务..."));
                PlannerOutput plan = plannerAgent.execute(requirement);
                log.info("[Graph] Planner 完成，子任务数: {}",
                        plan.getSubTasks() != null ? plan.getSubTasks().size() : 0);

                // 流式推送 Planner 输出内容
                streamAgentOutput("planner", plan.getTaskAnalysis(), sink);
                streamAgentOutput("planner", "技术栈: " + plan.getTechStack(), sink);
                if (plan.getRiskWarning() != null && !plan.getRiskWarning().isBlank()) {
                    streamAgentOutput("planner", "⚠️ 风险警告: " + plan.getRiskWarning(), sink);
                }

                // === Phase 2 & 3: Coder → Reviewer 循环 ===
                ReviewOutput previousReview = null;
                int retryCount = 0;

                do {
                    sink.next(StreamEvent.phase("coder",
                            String.format("开始生成代码(第%d次)...", retryCount + 1)));

                    CoderOutput code = coderAgent.execute(requirement, plan, previousReview);
                    log.info("[Graph] Coder 完成，文件数: {}",
                            code.getCodeFiles() != null ? code.getCodeFiles().size() : 0);

                    // 流式推送 Coder 输出内容
                    streamAgentOutput("coder", code.getExplanation(), sink);
                    if (code.getUncertaintyNote() != null && !code.getUncertaintyNote().isBlank()) {
                        streamAgentOutput("coder", "⚠️ 不确定性说明: " + code.getUncertaintyNote(), sink);
                    }

                    sink.next(StreamEvent.phase("reviewer", "开始审查代码..."));
                    ReviewOutput review = reviewerAgent.execute(requirement, code);
                    log.info("[Graph] Reviewer 完成: accepted={}, score={}",
                            review.isAccepted(), review.getScore());

                    // 流式推送 Reviewer 输出内容
                    streamAgentOutput("reviewer", review.getSummary(), sink);

                    // 根据审查结果决定下一步
                    if (review.isAccepted()) {
                        sink.next(StreamEvent.phase("end",
                                String.format("代码审查通过！评分: %d/100", review.getScore())));
                        sink.next(StreamEvent.result(buildResult(true, plan, code, review, retryCount)));
                        break;
                    }

                    retryCount++;

                    if (retryCount >= MAX_RETRIES) {
                        sink.next(StreamEvent.phase("end", "已达到最大重试次数(3次)，代码未通过审查"));
                        sink.next(StreamEvent.result(buildResult(false, plan, code, review, retryCount)));
                        break;
                    }

                    sink.next(StreamEvent.phase("coder",
                            String.format("代码未通过审查(评分: %d)，准备第%d次重试...",
                                    review.getScore(), retryCount + 1)));
                    previousReview = review;

                } while (true);

                sink.complete();

            } catch (Exception e) {
                log.error("[Graph] 执行异常", e);
                sink.next(StreamEvent.error("执行异常: " + e.getMessage()));
                sink.complete();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Map<String, Object> buildResult(boolean accepted, PlannerOutput plan,
                                            CoderOutput code, ReviewOutput review,
                                            int retryCount) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accepted", accepted);
        data.put("plan", plan);
        data.put("code", code);
        data.put("review", review);
        data.put("retryCount", retryCount);
        return data;
    }

    /**
     * 将 Agent 输出文本切成小块，以 phase_{agent}_stream 事件逐条推送
     */
    private void streamAgentOutput(String phase, String content, FluxSink<StreamEvent> sink) {
        if (content == null || content.isBlank()) return;
        List<String> chunks = splitContent(content, 80);
        for (String chunk : chunks) {
            sink.next(StreamEvent.phase(phase + "_stream", chunk));
        }
    }

    /**
     * 将长文本按最大长度切成多个片段
     */
    private List<String> splitContent(String text, int maxLen) {
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
