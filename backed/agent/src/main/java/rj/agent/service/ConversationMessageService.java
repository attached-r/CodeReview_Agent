package rj.agent.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import rj.agent.entity.ConversationMessage;

import java.util.List;
import java.util.Map;

/**
 * 对话消息 CRUD 服务接口
 * <p>
 * 所有查询基于 conversationId 作用域，配合 @CheckOwnership 校验归属权。
 */
public interface ConversationMessageService {

    /**
     * 获取某对话的消息列表（分页，按 messageIndex 升序）
     */
    Page<ConversationMessage> listByConversation(String conversationId, int pageNum, int pageSize);

    /**
     * 获取某对话的全部消息（按 messageIndex 升序）
     */
    List<ConversationMessage> listAllByConversation(String conversationId);

    /**
     * 根据主键 ID 获取单条消息
     */
    ConversationMessage getById(Long id);

    /**
     * 创建消息
     */
    ConversationMessage create(String conversationId, String role, String content,
                                Integer tokenCount, String modelName,
                                Map<String, Object> metadata, Integer messageIndex,
                                String parentMessageId);

    /**
     * 更新消息内容（仅允许更新 content / metadata）
     */
    ConversationMessage updateContent(Long id, String content, Map<String, Object> metadata);

    /**
     * 删除单条消息
     */
    void delete(Long id);

    /**
     * 删除某对话下的所有消息
     */
    void deleteByConversation(String conversationId);
}
