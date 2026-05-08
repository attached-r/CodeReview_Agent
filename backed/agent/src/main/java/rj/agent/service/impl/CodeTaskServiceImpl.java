package rj.agent.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import rj.agent.agent.CoderAgent;
import rj.agent.agent.PlannerAgent;
import rj.agent.agent.ReviewerAgent;
import rj.agent.entity.Conversation;
import rj.agent.graph.CodeGenerationGraph;
import rj.agent.mapper.ConversationMapper;
import rj.agent.model.StreamEvent;
import rj.agent.service.CodeTaskService;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 代码生成任务服务实现 — 以 Flux 流式输出协调多 Agent 协作的代码生成流程
 *
 * <p>职责：统筹 PlannerAgent、CoderAgent、ReviewerAgent 三个智能体，
 * 完成从需求分析到代码生成的完整流程，并通过 {@link Flux}<{@link StreamEvent}>
 * 实时推送执行进度。同时将任务关联到当前登录用户并持久化对话记录。</p>
 */
@Slf4j
@Service
public class CodeTaskServiceImpl implements CodeTaskService {

    private final PlannerAgent plannerAgent;
    private final CoderAgent coderAgent;
    private final ReviewerAgent reviewerAgent;
    private final ConversationMapper conversationMapper;

    public CodeTaskServiceImpl(PlannerAgent plannerAgent, CoderAgent coderAgent,
                               ReviewerAgent reviewerAgent,
                               ConversationMapper conversationMapper) {
        this.plannerAgent = plannerAgent;
        this.coderAgent = coderAgent;
        this.reviewerAgent = reviewerAgent;
        this.conversationMapper = conversationMapper;
    }

    @Override
    public Flux<StreamEvent> executeTask(String requirement, Long userId) {
        String taskId = UUID.randomUUID().toString().substring(0, 8);
        log.info("[Task-{}] 用户[{}]开始新任务: {}", taskId, userId, truncate(requirement, 100));

        // 持久化对话记录
        Conversation conversation = new Conversation();
        conversation.setConversationId(UUID.randomUUID().toString());
        conversation.setUserId(userId);
        conversation.setTitle(truncate(requirement, 200));
        conversation.setRequirement(requirement);
        conversation.setStatus("ACTIVE");
        conversation.setCreatedAt(OffsetDateTime.now());
        conversation.setUpdatedAt(OffsetDateTime.now());
        conversationMapper.insert(conversation);

        CodeGenerationGraph graph = new CodeGenerationGraph(
                plannerAgent, coderAgent, reviewerAgent);

        return Flux.concat(
                Flux.just(StreamEvent.phase("start",
                        String.format("任务[%s]开始执行", taskId))),
                graph.execute(requirement)
        ).doOnNext(event -> {
            if ("error".equals(event.getType())) {
                log.error("[Task-{}] 用户[{}]任务异常: {}", taskId, userId, event.getContent());
            }
        }).doOnTerminate(() -> {
            // 任务完成或失败后更新对话状态
            conversation.setStatus("COMPLETED");
            conversation.setUpdatedAt(OffsetDateTime.now());
            conversationMapper.updateById(conversation);
            log.info("[Task-{}] 用户[{}]任务结束", taskId, userId);
        });
    }

    private static String truncate(String str, int maxLen) {
        return str != null && str.length() > maxLen
                ? str.substring(0, maxLen) + "..."
                : str;
    }
}
