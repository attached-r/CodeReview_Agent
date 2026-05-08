package rj.agent.security.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import rj.agent.security.model.Result;

/**
 * 全局异常处理器
 * <p>
 * 将 Sa-Token 异常转换为标准 HTTP JSON 响应，避免返回 500 或 Whitelabel 错误页。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 未登录 / token 无效 / token 过期
     * <p>
     * Sa-Token 拦截器抛出 NotLoginException 时，转换为 401 响应。
     */
    @ExceptionHandler(NotLoginException.class)
    public Result<?> handleNotLogin(NotLoginException e) {
        log.debug("未登录访问受限资源: {}", e.getMessage());
        return Result.unauthorized("未登录或 token 已失效，请重新登录");
    }

    /**
     * 无角色权限
     * <p>
     * 使用 @SaCheckRole 等注解校验失败时抛出。
     */
    @ExceptionHandler(NotRoleException.class)
    public Result<?> handleNotRole(NotRoleException e) {
        log.debug("权限不足: {}", e.getMessage());
        return Result.forbidden("权限不足");
    }

    /**
     * 通用业务异常（如登录失败、用户名重复等）
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<?> handleRuntime(RuntimeException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(400, e.getMessage());
    }

    /**
     * 输入过滤安全异常（Prompt 注入/指令篡改）
     */
    @ExceptionHandler(SecurityException.class)
    public Result<?> handleSecurity(SecurityException e) {
        log.warn("输入过滤拦截: {}", e.getMessage());
        return Result.error(400, e.getMessage());
    }
}
