package rj.agent.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import rj.agent.service.InputFilterService;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 用户输入前置过滤服务实现
 * <p>
 * 在用户输入到达 AI Agent 之前进行安全检查，拦截 Prompt 注入、
 * 指令篡改等恶意输入，避免触发模型幻觉。
 * <p>
 * 校验失败时抛出 {@link SecurityException}，由全局异常处理器返回 400 响应。
 */
@Slf4j
@Service
public class InputFilterServiceImpl implements InputFilterService {

    /**
     * 指令篡改/注入模式
     * <p>
     * 覆盖以下常见攻击向量：
     * - 忽略/遗忘历史指令
     * - 角色劫持（假装是系统/机器人）
     * - 泄露系统提示词
     * - Base64 编码的恶意指令
     * - 多语言/Unicode 混淆（常见于越狱尝试）
     */
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            // === 指令忽略/覆盖 ===
            Pattern.compile("(?i)(ignore|forget|override|bypass|skip)\\s+(all\\s+)?(above|previous|prior|earlier|instructions?|commands?|rules?|prompts?)"),
            Pattern.compile("(?i)(忽略|忘记|无视|跳过).{0,10}(以上|之前|上面|历史|指令|规则|提示)"),

            // === 角色劫持 ===
            Pattern.compile("(?i)you\\s+(are\\s+)?(now|are)\\s+(a|an|the)\\s+(AI|assistant|system|bot|chatbot|model|GPT)"),
            Pattern.compile("(?i)(你的角色是|你现在是|你扮演|你是一个?)(AI|人工智能|助手|机器人|系统)"),

            // === 系统提示泄露 ===
            Pattern.compile("(?i)(reveal|show|display|output|print|leak|expose|dump)\\s+(the\\s+)?(system|original|initial|core)\\s+(prompt|instruction|message)"),
            Pattern.compile("(?i)(泄露|显示|输出|展示)(系统|原始|初始化)(提示|指令|消息)"),

            // === 越狱尝试 ===
            Pattern.compile("(?i)DAN\\s+(mode|is\\s+back|jailbreak)"),
            Pattern.compile("(?i)do\\s+anything\\s+now"),
            Pattern.compile("(?i)hypothetical\\s+(response|scenario)"),

            // === base64/编码注入 ===
            Pattern.compile("[A-Za-z0-9+/]{40,}={0,2}"), // base64 长串

            // === 分隔符混淆 ===
            Pattern.compile("(?i)-{3,}\\s*(begin|start|system|instruction|prompt)"),
            Pattern.compile("(?i)#{3,}\\s*(指令|系统|提示|开始)")
    );

    /**
     * 单次输入最大长度（字符），防止 token 洪水攻击
     */
    private static final int MAX_INPUT_LENGTH = 50000;

    @Override
    public void validate(String input) {
        if (input == null || input.isBlank()) {
            throw new SecurityException("输入不能为空");
        }

        // 长度检查
        if (input.length() > MAX_INPUT_LENGTH) {
            log.warn("输入超长: {} 字符（限制 {}）", input.length(), MAX_INPUT_LENGTH);
            throw new SecurityException("输入内容过长，最大允许 " + MAX_INPUT_LENGTH + " 字符");
        }

        // 模式匹配
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                String matched = pattern.matcher(input).group();
                log.warn("检测到疑似注入: pattern={}, matched=[{}]",
                        pattern.pattern(), truncate(matched, 80));
                throw new SecurityException("输入包含不安全的指令模式，请调整后重试");
            }
        }
    }

    private static String truncate(String str, int maxLen) {
        return str != null && str.length() > maxLen
                ? str.substring(0, maxLen) + "..."
                : str;
    }
}
