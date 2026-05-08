package rj.agent.security.annotation;

import java.lang.annotation.*;

/**
 * 参数级注解，标记方法参数中的 conversationId
 * <p>
 * 配合 {@link CheckOwnership} 使用，{@link rj.agent.security.aspect.OwnershipAspect}
 * 通过该注解定位方法参数中的 conversationId，从而查询对话归属权。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ConversationId {
}
