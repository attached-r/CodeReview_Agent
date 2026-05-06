package rj.agent.controller;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import rj.agent.service.CodeTaskService;

@Slf4j
@RestController
@RequestMapping("/api/code-task")
public class CodeTaskController {

    private final CodeTaskService codeTaskService;

    public CodeTaskController(CodeTaskService codeTaskService) {
        this.codeTaskService = codeTaskService;
    }

    /**
     * SSE 接口：提交代码生成任务，实时流式推送执行进度
     */
    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generate(@RequestBody CodeTaskRequest request) {
        log.info("收到代码生成请求: {}", truncate(request.getRequirement(), 100));

        SseEmitter emitter = new SseEmitter(-1L);

        codeTaskService.executeTask(request.getRequirement(), emitter);

        return emitter;
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public String health() {
        return "OK";
    }

    private String truncate(String str, int maxLen) {
        return str != null && str.length() > maxLen
                ? str.substring(0, maxLen) + "..."
                : str;
    }

    @Data
    public static class CodeTaskRequest {
        private String requirement;
    }
}
