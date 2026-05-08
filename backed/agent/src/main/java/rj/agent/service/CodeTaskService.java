package rj.agent.service;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import rj.agent.agent.CoderAgent;
import rj.agent.agent.PlannerAgent;
import rj.agent.agent.ReviewerAgent;
import rj.agent.graph.CodeGenerationGraph;
import rj.agent.model.StreamEvent;

import java.util.UUID;

/**
 * 代码生成任务服务 — 以 Flux 流式输出协调多 Agent 协作的代码生成流程
 *
 * <p>职责：统筹 PlannerAgent、CoderAgent、ReviewerAgent 三个智能体，
 * 完成从需求分析到代码生成的完整流程，并通过 {@link Flux}<{@link StreamEvent}>
 * 实时推送执行进度。</p>
 */
@Slf4j
@org.springframework.stereotype.Service
public class CodeTaskService {

    private final PlannerAgent plannerAgent;
    private final CoderAgent coderAgent;
    private final ReviewerAgent reviewerAgent;

    public CodeTaskService(PlannerAgent plannerAgent, CoderAgent coderAgent,
                           ReviewerAgent reviewerAgent) {
        this.plannerAgent = plannerAgent;
        this.coderAgent = coderAgent;
        this.reviewerAgent = reviewerAgent;
    }

    /**
     * 以 Flux 流式执行代码生成任务
     *
     * <p>返回的 Flux 会自动在 {@link reactor.core.scheduler.Schedulers#boundedElastic()}
     * 上执行阻塞的 LLM 调用，不会阻塞 Web 容器线程。</p>
     *
     * @param requirement 用户的代码生成需求描述
     * @return Flux 流式事件序列，前端可通过 SSE 接收
     */
    public Flux<StreamEvent> executeTask(String requirement) {
        String taskId = UUID.randomUUID().toString().substring(0, 8);
        log.info("[Task-{}] 开始新任务: {}", taskId, truncate(requirement, 100));

        CodeGenerationGraph graph = new CodeGenerationGraph(
                plannerAgent, coderAgent, reviewerAgent);

        return Flux.concat(
                Flux.just(StreamEvent.phase("start",
                        String.format("任务[%s]开始执行", taskId))),
                graph.execute(requirement)
        ).doOnNext(event -> {
            if ("error".equals(event.getType())) {
                log.error("[Task-{}] 任务异常: {}", taskId, event.getContent());
            }
        }).doOnTerminate(() -> log.info("[Task-{}] 任务结束", taskId));
    }

    private static String truncate(String str, int maxLen) {
        return str != null && str.length() > maxLen
                ? str.substring(0, maxLen) + "..."
                : str;
    }
}
