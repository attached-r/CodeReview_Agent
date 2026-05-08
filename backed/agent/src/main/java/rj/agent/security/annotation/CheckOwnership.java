package rj.agent.security.annotation;

import java.lang.annotation.*;

/**
 * 方法级权限校验注解
 * <p>
 * 标注在需要校验对话归属权的 Service / Controller 方法上。
 * 配合 {@link ConversationId} 参数注解使用，拦截器会自动校验
 * 当前登录用户是否为该对话的所有者。
 * <p>
 * 用法：
 * <pre>
 * &#064;CheckOwnership
 * public Conversation get(&#064;ConversationId String conversationId) {
 *     // ...
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CheckOwnership {
}
