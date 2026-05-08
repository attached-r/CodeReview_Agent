package rj.agent.service;

/**
 * 用户输入前置过滤服务接口
 * <p>
 * 在用户输入到达 AI Agent 之前进行安全检查，拦截 Prompt 注入、
 * 指令篡改等恶意输入，避免触发模型幻觉。
 */
public interface InputFilterService {

    /**
     * 校验用户输入，发现恶意模式时抛出 SecurityException
     *
     * @param input 用户原始输入
     * @throws SecurityException 输入包含恶意内容时抛出
     */
    void validate(String input);
}
