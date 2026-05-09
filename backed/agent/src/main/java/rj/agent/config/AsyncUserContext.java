package rj.agent.config;

/**
 * 异步线程用户上下文 — 用于在 Reactor/Graph 线程中传递用户 ID
 * <p>
 * Graph 节点执行在 Schedulers.boundedElastic() 上，该线程池没有
 * Sa-Token 上下文（ThreadLocal）。而 RLS 拦截器在每个 SQL 执行前需要
 * 当前用户 ID。此类提供一个脱离 Sa-Token 的轻量级 ThreadLocal 方案。
 * <p>
 * 用法：
 * <pre>
 * AsyncUserContext.setUserId(userId);
 * try {
 *     // 执行 SQL/ChatMemory 操作
 * } finally {
 *     AsyncUserContext.clear();
 * }
 * </pre>
 */
public final class AsyncUserContext {

    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

    private AsyncUserContext() {}

    public static void setUserId(Long userId) {
        USER_ID_HOLDER.set(userId);
    }

    public static Long getUserId() {
        return USER_ID_HOLDER.get();
    }

    public static void clear() {
        USER_ID_HOLDER.remove();
    }
}
