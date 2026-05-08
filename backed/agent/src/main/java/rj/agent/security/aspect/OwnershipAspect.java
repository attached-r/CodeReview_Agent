package rj.agent.security.aspect;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import rj.agent.entity.Conversation;
import rj.agent.mapper.ConversationMapper;
import rj.agent.security.annotation.CheckOwnership;
import rj.agent.security.annotation.ConversationId;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * 对话归属权校验切面
 * <p>
 * 拦截所有标注 {@link CheckOwnership} 的方法，提取参数中的
 * {@link ConversationId} 值，查询该对话的 userId 并与当前登录用户比对，
 * 不一致则抛出异常，防止越权访问。
 * <p>
 * 作为业务层权限校验的核心实现，与数据库 RLS 构成双层防御。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OwnershipAspect {

    private final ConversationMapper conversationMapper;

    @Before("@annotation(rj.agent.security.annotation.CheckOwnership)")
    public void checkOwnership(JoinPoint joinPoint) {
        // 获取当前登录用户
        long currentUserId = StpUtil.getLoginIdAsLong();

        // 从方法参数中提取 conversationId
        String conversationId = extractConversationId(joinPoint);
        if (conversationId == null) {
            log.warn("未找到 @ConversationId 参数，跳过归属校验");
            return;
        }

        // 查询对话
        Conversation conversation = conversationMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getConversationId, conversationId));

        if (conversation == null) {
            throw new RuntimeException("对话不存在");
        }

        // 核心校验：当前用户是否为对话所有者
        if (!conversation.getUserId().equals(currentUserId)) {
            log.warn("越权访问: 用户[{}]尝试访问用户[{}]的对话[{}]",
                    currentUserId, conversation.getUserId(), conversationId);
            throw new RuntimeException("无权访问该对话");
        }

        log.debug("归属校验通过: 用户[{}] -> 对话[{}]", currentUserId, conversationId);
    }

    /**
     * 从被拦截方法的参数中提取标注 {@link ConversationId} 的参数值
     */
    private String extractConversationId(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Annotation[][] paramAnnotations = method.getParameterAnnotations();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < paramAnnotations.length; i++) {
            for (Annotation ann : paramAnnotations[i]) {
                if (ann.annotationType() == ConversationId.class) {
                    return args[i] != null ? args[i].toString() : null;
                }
            }
        }
        return null;
    }
}
