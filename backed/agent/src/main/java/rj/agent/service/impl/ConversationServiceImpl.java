package rj.agent.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rj.agent.entity.Conversation;
import rj.agent.mapper.ConversationMapper;
import rj.agent.security.annotation.CheckOwnership;
import rj.agent.security.annotation.ConversationId;
import rj.agent.service.ConversationService;

import java.time.OffsetDateTime;

/**
 * 对话 CRUD 服务实现
 * <p>
 * 所有查询强制过滤当前登录用户的 userId，防止越权访问。
 * 在 {@link #getByConversationId}、{@link #updateTitle}、{@link #delete} 上
 * 叠加了 {@link CheckOwnership} 注解，通过 AOP 确保对话归属权校验。
 */
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationMapper conversationMapper;

    @Override
    public Page<Conversation> list(int pageNum, int pageSize, String status) {
        Long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getUserId, userId)
                .orderByDesc(Conversation::getUpdatedAt);

        if (status != null && !status.isBlank()) {
            wrapper.eq(Conversation::getStatus, status);
        }
        return conversationMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    @CheckOwnership
    public Conversation getByConversationId(@ConversationId String conversationId) {
        Conversation conversation = conversationMapper.selectOne(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getConversationId, conversationId));
        if (conversation == null) {
            throw new RuntimeException("对话不存在");
        }
        return conversation;
    }

    @Override
    @CheckOwnership
    public Conversation updateTitle(@ConversationId String conversationId, String newTitle) {
        Conversation conversation = getByConversationId(conversationId);
        conversation.setTitle(newTitle);
        conversation.setUpdatedAt(OffsetDateTime.now());
        conversationMapper.updateById(conversation);
        return conversation;
    }

    @Override
    @CheckOwnership
    public void delete(@ConversationId String conversationId) {
        Conversation conversation = getByConversationId(conversationId);
        conversationMapper.deleteById(conversation.getId());
    }
}
