package rj.agent.service;

import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import reactor.core.publisher.Flux;
import rj.agent.model.StreamEvent;

/**
 * 代码生成任务服务接口
 * <p>
 * 统筹 PlannerAgent、CoderAgent、ReviewerAgent 三个智能体，
 * 完成从需求分析到代码生成的完整流程。
 */
public interface CodeTaskService {

    /**
     * 以 Flux 流式执行代码生成任务，关联指定用户
     *
     * @param requirement 用户的代码生成需求描述
     * @param userId      当前登录用户 ID（从 StpUtil 获取）
     * @return Flux 流式事件序列，前端可通过 SSE 接收
     */
    Flux<StreamEvent> executeTask(String requirement, Long userId) throws GraphStateException;
}
