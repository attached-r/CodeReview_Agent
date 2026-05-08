package rj.agent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import rj.agent.entity.Conversation;
import rj.agent.security.model.Result;
import rj.agent.service.ConversationService;

/**
 * 对话 CRUD 控制器
 * <p>
 * 所有操作强制校验当前登录用户的归属权（通过 ConversationService）。
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    /**
     * 获取当前用户的对话列表（分页）
     *
     * @param pageNum  页码，默认 1
     * @param pageSize 每页条数，默认 20
     * @param status   按状态过滤（ACTIVE / COMPLETED / FAILED），可选
     */
    @GetMapping
    public Result<Page<Conversation>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        Page<Conversation> page = conversationService.list(pageNum, pageSize, status);
        return Result.ok(page);
    }

    /**
     * 获取单个对话详情
     *
     * @param conversationId 对话 UUID
     */
    @GetMapping("/{conversationId}")
    public Result<Conversation> get(@PathVariable String conversationId) {
        return Result.ok(conversationService.getByConversationId(conversationId));
    }

    /**
     * 更新对话标题
     *
     * @param conversationId 对话 UUID
     * @param req            { title: "新标题" }
     */
    @PutMapping("/{conversationId}")
    public Result<Conversation> updateTitle(
            @PathVariable String conversationId,
            @RequestBody UpdateTitleRequest req) {
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            return Result.error(400, "标题不能为空");
        }
        return Result.ok(conversationService.updateTitle(conversationId, req.getTitle()));
    }

    /**
     * 删除对话
     *
     * @param conversationId 对话 UUID
     */
    @DeleteMapping("/{conversationId}")
    public Result<?> delete(@PathVariable String conversationId) {
        conversationService.delete(conversationId);
        return Result.ok();
    }

    @Data
    public static class UpdateTitleRequest {
        private String title;
    }
}
