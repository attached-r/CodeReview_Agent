package rj.agent.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import rj.agent.config.AsyncUserContext;
import rj.agent.entity.Conversation;
import rj.agent.entity.ConversationMessage;
import rj.agent.mapper.ConversationMapper;
import rj.agent.mapper.ConversationMessageMapper;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * 基于 {@link ConversationMessage} 的 ChatMemory 实现
 * <p>
 * 直接使用 {@link ConversationMessageMapper} 将对话记忆持久化到
 * {@code conversation_messages} 表。get 操作前校验对话归属权。
 * <p>
 * 以 conversationId 为隔离 Key，不同对话之间全量隔离。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageChatMemory implements ChatMemory {

    private final ConversationMessageMapper messageMapper;
    private final ConversationMapper conversationMapper;

    @Override
    public void add(String conversationId, List<Message> messages) {
        checkOwnership(conversationId);
        if (messages == null || messages.isEmpty()) return;

        Long existingCount = messageMapper.selectCount(
                new LambdaQueryWrapper<ConversationMessage>()
                        .eq(ConversationMessage::getConversationId, conversationId));
        int startIndex = existingCount != null ? existingCount.intValue() : 0;

        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            ConversationMessage entity = new ConversationMessage();
            entity.setConversationId(conversationId);
            entity.setMessageId(UUID.randomUUID().toString());
            entity.setRole(msg.getMessageType().name().toLowerCase());
            entity.setContent(msg.getText() != null ? msg.getText() : "");
            entity.setMessageIndex(startIndex + i);
            entity.setCreatedAt(OffsetDateTime.now());
            messageMapper.insert(entity);
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        checkOwnership(conversationId);
        List<ConversationMessage> list = messageMapper.selectList(
                new LambdaQueryWrapper<ConversationMessage>()
                        .eq(ConversationMessage::getConversationId, conversationId)
                        .orderByAsc(ConversationMessage::getMessageIndex));
        return toMessages(list);
    }

    @Override
    public void clear(String conversationId) {
        checkOwnership(conversationId);
        messageMapper.delete(
                new LambdaQueryWrapper<ConversationMessage>()
                        .eq(ConversationMessage::getConversationId, conversationId));
    }

    public List<Message> get(String conversationId, int lastN) {
        checkOwnership(conversationId);
        List<ConversationMessage> list = messageMapper.selectList(
                new LambdaQueryWrapper<ConversationMessage>()
                        .eq(ConversationMessage::getConversationId, conversationId)
                        .orderByDesc(ConversationMessage::getId)
                        .last("LIMIT " + lastN));
        Collections.reverse(list);
        return toMessages(list);
    }

    private List<Message> toMessages(List<ConversationMessage> entities) {
        return entities.stream().<Message>map(e -> {
            String content = e.getContent() != null ? e.getContent() : "";
            return switch (e.getRole()) {
                case "user" -> new UserMessage(content);
                case "assistant" -> new AssistantMessage(content);
                case "system" -> new SystemMessage(content);
                default -> new UserMessage(content);
            };
        }).toList();
    }

    private void checkOwnership(String conversationId) {
        Long userId = resolveUserId();
        if (userId == null) throw new RuntimeException("无法获取当前用户身份");
        Conversation c = conversationMapper.selectOne(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getConversationId, conversationId));
        if (c == null) throw new RuntimeException("对话不存在");
        if (!c.getUserId().equals(userId)) throw new RuntimeException("无权访问该对话的记忆");
    }

    /**
     * 按优先级解析当前用户 ID：
     * <ol>
     *   <li>异步上下文 {@link AsyncUserContext#getUserId()}</li>
     *   <li>Sa-Token 登录上下文 {@link StpUtil#getLoginIdAsLong()}</li>
     * </ol>
     */
    private Long resolveUserId() {
        Long asyncUserId = AsyncUserContext.getUserId();
        if (asyncUserId != null) {
            return asyncUserId;
        }
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return null;
        }
    }
}
