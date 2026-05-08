package rj.agent.controller;

import cn.dev33.satoken.stp.StpUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import rj.agent.service.CodeTaskService;
import rj.agent.service.InputFilterService;

@Slf4j
@RestController
@RequestMapping("/api/code-task")
public class CodeTaskController {

    private final CodeTaskService codeTaskService;
    private final InputFilterService inputFilterService;

    public CodeTaskController(CodeTaskService codeTaskService,
                              InputFilterService inputFilterService) {
        this.codeTaskService = codeTaskService;
        this.inputFilterService = inputFilterService;
    }

    /**
     * Flux SSE 接口：提交代码生成任务，实时流式推送执行进度
     * <p>
     * 前置调用 {@link InputFilterService#validate(String)} 过滤注入，
     * 再从 Sa-Token 获取当前用户 ID，传递给服务层创建对话记录。
     */
    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> generate(@RequestBody CodeTaskRequest request) {
        // 输入过滤（Prompt 注入/指令篡改检测）
        inputFilterService.validate(request.getRequirement());

        Long userId = StpUtil.getLoginIdAsLong();
        log.info("用户[{}]提交代码生成请求: {}", userId, truncate(request.getRequirement(), 100));

        return codeTaskService.executeTask(request.getRequirement(), userId)
                .map(event -> ServerSentEvent.builder()
                        .event(event.getType())
                        .data(event)
                        .build());
    }

    /**
     * 健康检查（公开，无需登录）
     */
    @GetMapping("/health")
    public String health() {
        return "OK";
    }

    private static String truncate(String str, int maxLen) {
        return str != null && str.length() > maxLen
                ? str.substring(0, maxLen) + "..."
                : str;
    }

    @Data
    public static class CodeTaskRequest {
        private String requirement;
    }
}
