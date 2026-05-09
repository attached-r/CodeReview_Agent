package rj.agent.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.Statement;

/**
 * MyBatis RLS 拦截器
 * <p>
 * 在每次数据库查询/更新前，自动设置 PostgreSQL 会话变量 {@code app.current_user_id}，
 * 配合 {@code init.sql} 中定义的 RLS 策略实现行级安全隔离。
 * <p>
 * 作为防御兜底（defense-in-depth）：即使业务层 AOP 校验漏过，
 * 数据库层仍能阻止越权查询。未登录用户不会设置该变量，RLS 策略会
 * 使用 {@code current_setting(_, true)} 安全兜底返回空结果。
 * <p>
 * 在异步线程（Graph/Reactor）中，Sa-Token 上下文不可用，会回退到
 * {@link AsyncUserContext} 获取用户 ID。
 */
@Slf4j
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {
                org.apache.ibatis.mapping.MappedStatement.class,
                Object.class,
                org.apache.ibatis.session.RowBounds.class,
                org.apache.ibatis.session.ResultHandler.class
        }),
        @Signature(type = Executor.class, method = "update", args = {
                org.apache.ibatis.mapping.MappedStatement.class,
                Object.class
        })
})
public class RlsInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        setSessionUserId(invocation);
        return invocation.proceed();
    }

    /**
     * 从 Sa-Token 上下文获取当前用户 ID，注入 PostgreSQL 会话变量。
     * <p>
     * 在异步线程（如 Graph）中 Sa-Token 上下文不可用，回退到
     * {@link AsyncUserContext}。
     */
    private void setSessionUserId(Invocation invocation) {
        Long userId = resolveUserId();
        if (userId == null) {
            return;
        }
        try {
            Object target = invocation.getTarget();
            if (target instanceof Executor executor) {
                Connection connection = executor.getTransaction().getConnection();
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("SET SESSION app.current_user_id = " + userId);
                }
            }
        } catch (Exception e) {
            // RLS 是防御兜底，不影响主流程
            log.warn("设置 RLS 会话变量失败: {}", e.getMessage());
        }
    }

    /**
     * 按优先级解析当前用户 ID：
     * <ol>
     *   <li>异步上下文 {@link AsyncUserContext#getUserId()}</li>
     *   <li>Sa-Token 登录上下文 {@link StpUtil#getLoginIdAsLong()}</li>
     * </ol>
     */
    private Long resolveUserId() {
        // 优先级 1：异步线程上下文（Graph/Reactor）
        Long asyncUserId = AsyncUserContext.getUserId();
        if (asyncUserId != null) {
            return asyncUserId;
        }
        // 优先级 2：Sa-Token 上下文（主线程请求）
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (NotLoginException e) {
            log.trace("未登录请求，跳过 RLS 变量设置");
            return null;
        } catch (Exception e) {
            log.trace("Sa-Token 上下文不可用: {}", e.getMessage());
            return null;
        }
    }
}
