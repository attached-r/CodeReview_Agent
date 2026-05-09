package rj.agent.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rj.agent.entity.ConversationMessage;
import rj.agent.mapper.ConversationMessageMapper;
import rj.agent.security.annotation.CheckOwnership;
import rj.agent.security.annotation.ConversationId;
import rj.agent.service.ConversationMessageService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationMessageServiceImpl implements ConversationMessageService {

    private final ConversationMessageMapper messageMapper;

    @Override
    @CheckOwnership
    public Page<ConversationMessage> listByConversation(@ConversationId String conversationId,
                                                         int pageNum, int pageSize) {
        return messageMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<ConversationMessage>()
                        .eq(ConversationMessage::getConversationId, conversationId)
                        .orderByAsc(ConversationMessage::getMessageIndex));
    }

    @Override
    @CheckOwnership
    public List<ConversationMessage> listAllByConversation(@ConversationId String conversationId) {
        return messageMapper.selectList(
                new LambdaQueryWrapper<ConversationMessage>()
                        .eq(ConversationMessage::getConversationId, conversationId)
                        .orderByAsc(ConversationMessage::getMessageIndex));
    }

    @Override
    public ConversationMessage getById(Long id) {
        ConversationMessage msg = messageMapper.selectById(id);
        if (msg == null) {
            throw new RuntimeException("消息不存在");
        }
        return msg;
    }

    @Override
    @CheckOwnership
    public ConversationMessage create(@ConversationId String conversationId,
                                       String role, String content,
                                       Integer tokenCount, String modelName,
                                       Map<String, Object> metadata, Integer messageIndex,
                                       String parentMessageId) {
        ConversationMessage msg = new ConversationMessage();
        msg.setConversationId(conversationId);
        msg.setMessageId(UUID.randomUUID().toString());
        msg.setRole(role);
        msg.setContent(content);
        msg.setTokenCount(tokenCount);
        msg.setModelName(modelName);
        msg.setMetadata(metadata);
        msg.setMessageIndex(messageIndex);
        msg.setParentMessageId(parentMessageId);
        msg.setCreatedAt(OffsetDateTime.now());

        messageMapper.insert(msg);
        return msg;
    }

    @Override
    public ConversationMessage updateContent(Long id, String content, Map<String, Object> metadata) {
        ConversationMessage msg = getById(id);
        if (content != null) {
            msg.setContent(content);
        }
        if (metadata != null) {
            msg.setMetadata(metadata);
        }
        messageMapper.updateById(msg);
        return msg;
    }

    @Override
    public void delete(Long id) {
        ConversationMessage msg = getById(id);
        messageMapper.deleteById(msg.getId());
    }

    @Override
    @CheckOwnership
    public void deleteByConversation(@ConversationId String conversationId) {
        messageMapper.delete(
                new LambdaQueryWrapper<ConversationMessage>()
                        .eq(ConversationMessage::getConversationId, conversationId));
    }
}
