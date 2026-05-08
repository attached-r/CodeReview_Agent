package rj.agent.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import rj.agent.entity.Conversation;

/**
 * 对话 CRUD 服务接口
 * <p>
 * 所有查询强制过滤当前登录用户的 userId，防止越权访问。
 */
public interface ConversationService {

    /**
     * 获取当前用户的对话列表（分页）
     *
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param status 对话状态（可选）
     * @return 分页结果
     */
    Page<Conversation> list(int pageNum, int pageSize, String status);

    /**
     * 根据 conversationId 获取对话（AOP 校验归属权）
     *
     * @param conversationId 对话ID
     * @return 对话实体
     */
    Conversation getByConversationId(String conversationId);

    /**
     * 更新对话标题（AOP 校验归属权）
     *
     * @param conversationId 对话ID
     * @param newTitle 新标题
     * @return 更新后的对话实体
     */
    Conversation updateTitle(String conversationId, String newTitle);

    /**
     * 删除对话（AOP 校验归属权）
     *
     * @param conversationId 对话ID
     */
    void delete(String conversationId);
}
