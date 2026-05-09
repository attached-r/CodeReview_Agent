package rj.agent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import rj.agent.entity.ConversationMessage;
import rj.agent.security.model.Result;
import rj.agent.service.ConversationMessageService;

import java.util.List;
import java.util.Map;

/**
 * 对话消息 CRUD 控制器
 * <p>
 * 所有操作通过 conversationId 关联对话，配合 @CheckOwnership AOP 校验归属权。
 */
@RestController
@RequestMapping("/api/conversations/{conversationId}/messages")
@RequiredArgsConstructor
public class ConversationMessageController {

    private final ConversationMessageService conversationMessageService;

    /**
     * 获取某对话的消息列表（分页，按 messageIndex 升序）
     */
    @GetMapping
    public Result<Page<ConversationMessage>> list(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(conversationMessageService.listByConversation(conversationId, pageNum, pageSize));
    }

    /**
     * 获取某对话的全部消息（按 messageIndex 升序）
     */
    @GetMapping("/all")
    public Result<List<ConversationMessage>> listAll(@PathVariable String conversationId) {
        return Result.ok(conversationMessageService.listAllByConversation(conversationId));
    }

    /**
     * 获取单条消息详情
     */
    @GetMapping("/{id}")
    public Result<ConversationMessage> getById(@PathVariable String conversationId,
                                                @PathVariable Long id) {
        return Result.ok(conversationMessageService.getById(id));
    }

    /**
     * 创建消息
     */
    @PostMapping
    public Result<ConversationMessage> create(
            @PathVariable String conversationId,
            @RequestBody CreateMessageRequest req) {
        if (req.getRole() == null || req.getRole().isBlank()) {
            return Result.error(400, "角色不能为空");
        }
        if (req.getContent() == null || req.getContent().isBlank()) {
            return Result.error(400, "内容不能为空");
        }
        return Result.ok(conversationMessageService.create(
                conversationId, req.getRole(), req.getContent(),
                req.getTokenCount(), req.getModelName(),
                req.getMetadata(), req.getMessageIndex(), req.getParentMessageId()));
    }

    /**
     * 更新消息内容 / 元数据
     */
    @PutMapping("/{id}")
    public Result<ConversationMessage> update(
            @PathVariable String conversationId,
            @PathVariable Long id,
            @RequestBody UpdateMessageRequest req) {
        return Result.ok(conversationMessageService.updateContent(id, req.getContent(), req.getMetadata()));
    }

    /**
     * 删除单条消息
     */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable String conversationId,
                            @PathVariable Long id) {
        conversationMessageService.delete(id);
        return Result.ok();
    }

    /**
     * 删除某对话下的全部消息
     */
    @DeleteMapping
    public Result<?> deleteByConversation(@PathVariable String conversationId) {
        conversationMessageService.deleteByConversation(conversationId);
        return Result.ok();
    }

    // ========== 内部请求体 ==========

    @Data
    public static class CreateMessageRequest {
        private String role;
        private String content;
        private Integer tokenCount;
        private String modelName;
        private Map<String, Object> metadata;
        private Integer messageIndex;
        private String parentMessageId;
    }

    @Data
    public static class UpdateMessageRequest {
        private String content;
        private Map<String, Object> metadata;
    }
}
