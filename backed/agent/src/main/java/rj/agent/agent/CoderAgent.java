package rj.agent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import rj.agent.model.CoderOutput;
import rj.agent.model.PlannerOutput;
import rj.agent.model.ReviewOutput;

import java.util.List;

/**
 * 代码生成 Agent - 多编程语言代码生成专家
 *
 * <p>职责：根据任务规划生成可编译/可执行的完整代码，支持多种编程语言和框架。</p>
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>基于 PlannerAgent 生成的任务计划，生成完整的源代码文件</li>
 *   <li>支持多种编程语言（Java、Python、JavaScript/TypeScript、Go、Rust、HTML/CSS 等）</li>
 *   <li>自动处理代码审查反馈，进行迭代优化</li>
 *   <li>生成符合语言规范的惯用代码（idiomatic code）</li>
 *   <li>输出结构化的代码文件和依赖声明</li>
 * </ul>
 *
 * <p>反幻觉机制：</p>
 * <ul>
 *   <li>仅使用各编程语言生态中真实存在的 API、类库、框架</li>
 *   <li>所有依赖必须在对应的包管理器仓库中存在（Maven/npm/PyPI/crates.io/Go Module）</li>
 *   <li>生成的代码必须是可编译/可执行的完整代码，不是伪代码或片段</li>
 *   <li>不确定内容必须在 uncertaintyNote 中标注"待确认"</li>
 *   <li>每个文件必须包含完整的导入语句、包声明和主类/函数定义</li>
 * </ul>
 */
@Slf4j
@Component
public class CoderAgent {
    // 系统提示词
// ... existing code ...

    private static final String SYSTEM_PROMPT = """
            你是一个多编程语言代码生成专家。根据任务计划生成可编译/可执行的代码。

            【反幻觉强制规则 - 不可违反】
            1. 禁止编造不存在的编程语言 API、类库、框架、方法或注解
               - 只使用各编程语言生态中真实存在的类库和框架
               - 例如：Java 使用 Spring Data JPA、Python 使用 SQLAlchemy、JavaScript 使用 React Hooks 等
            2. 禁止编造不存在的包管理器依赖 — 所有依赖必须在对应的包管理器仓库中存在
               - Java: Maven Central / Gradle
               - JavaScript/TypeScript: npm / yarn
               - Python: PyPI
               - Rust: crates.io
               - Go: Go Module Registry
            3. 生成的代码必须是可编译/可执行的完整代码，不是伪代码或片段
            4. 引用的任何第三方类库必须是真实存在的
            5. 不确定的内容必须在 uncertaintyNote 中标注"待确认"
            6. 每个文件必须有完整的导入语句、包声明（如需要）和主类/函数定义

            【多语言代码规范】
            7. 根据目标文件扩展名使用正确的语法和约定
               - .java: Java 17+ 语法，包含 package 和 import
               - .py: Python 3.x 语法，包含必要的 import
               - .js/.ts: JavaScript ES6+ / TypeScript，包含 import/export
               - .go: Go 模块语法，包含 package 和 import
               - .rs: Rust 2021 edition，包含 use 声明
               - .html/.css: 标准的 HTML5 / CSS3
            8. 确保代码符合该语言的惯用写法（idiomatic code）

            【JSON格式要求 - 必须严格遵守】
            9. codeFiles[i].content 字段的值包含完整的代码文本
            10. 代码文本中的所有双引号(")必须转义为 \\"，反斜杠(\\)必须转义为 \\\\
            11. 这是最关键的一条：如果不转义双引号，JSON 解析会直接失败
            12. 输出必须是合法的 JSON，以 { 开头，以 } 结尾，不要包含 

            模型参数约束：Temperature=0.2, Top_P=0.3
            输出必须严格匹配以下 JSON Schema 格式，禁止输出任何无关内容：

            {format}
            """;
    // 用户提示词
    private static final String USER_PROMPT_TEMPLATE = """
            请根据以下任务计划生成代码：

            需求描述：
            {requirement}

            任务计划：
            {plan}

            {reviewContext}
            """;

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper; // JSON序列化工具
    private final BeanOutputConverter<CoderOutput> outputConverter; // 输出转换工具

    // 构造函数 注入ChatModel和ObjectMapper
    public CoderAgent(@Qualifier("coderChatModel") ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.outputConverter = new BeanOutputConverter<>(CoderOutput.class);
    }

    /**
     * 执行代码生成
     * @param requirement
     * @param plan
     * @param previousReview
     * @return
     */
    public CoderOutput execute(String requirement, PlannerOutput plan, ReviewOutput previousReview) {
        String format = outputConverter.getFormat();

        String reviewContext = buildReviewContext(previousReview);
        String planJson = serializePlan(plan);
        String userContent = USER_PROMPT_TEMPLATE
                .replace("{requirement}", requirement)
                .replace("{plan}", planJson)
                .replace("{reviewContext}", reviewContext);

        Prompt prompt = new Prompt(List.of(
                new SystemMessage(SYSTEM_PROMPT.replace("{format}", format)),
                new UserMessage(userContent)
        ));

        log.info("[CoderAgent] 开始生成代码...");
        ChatResponse response = chatModel.call(prompt);
        String content = response.getResult().getOutput().getText();

        log.info("[CoderAgent] 收到响应: {}...", truncate(content, 200));

        // 预处理：剥离 markdown 代码块标记
        String cleaned = stripMarkdownFences(content);

        CoderOutput output = null;
        try {
            output = outputConverter.convert(cleaned);
        } catch (Exception e) {
            log.warn("[CoderAgent] BeanOutputConverter 解析失败，尝试手动修复JSON: {}", e.getMessage());
            // 兜底：尝试提取花括号包裹的最外层 JSON
            String extracted = extractJsonObject(cleaned);
            if (extracted != null) {
                try {
                    output = outputConverter.convert(extracted);
                } catch (Exception ex) {
                    log.error("[CoderAgent] 手动修复后仍解析失败, 原始响应={}", content);
                    throw new RuntimeException("Coder输出JSON解析失败", ex);
                }
            } else {
                log.error("[CoderAgent] 无法提取JSON, 原始响应={}", content);
                throw new RuntimeException("Coder输出JSON解析失败", e);
            }
        }

        int fileCount = output.getCodeFiles() != null ? output.getCodeFiles().size() : 0;
        log.info("[CoderAgent] 代码生成完成，文件数: {}, 依赖数: {}",
                fileCount,
                output.getDependencies() != null ? output.getDependencies().size() : 0);

        return output;
    }

    /**
     * 剥离 markdown 代码块标记 (```json 和 ```)
     */
    private String stripMarkdownFences(String text) {
        if (text == null) return null;
        String result = text.trim();
        if (result.startsWith("```")) {
            int newlineIdx = result.indexOf('\n');
            if (newlineIdx != -1) {
                result = result.substring(newlineIdx + 1).trim();
            }
        }
        if (result.endsWith("```")) {
            result = result.substring(0, result.lastIndexOf("```")).trim();
        }
        // 去掉结尾多余的 ```
        result = result.replaceAll("```$", "").trim();
        return result;
    }

    /**
     * 兜底：从文本中提取最外层 {} 包裹的内容
     */
    private String extractJsonObject(String text) {
        if (text == null) return null;
        int start = text.indexOf('{');
        if (start == -1) return null;

        int depth = 0;
        int openIdx = -1;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                if (depth == 0) openIdx = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && openIdx != -1) {
                    return text.substring(openIdx, i + 1);
                }
            }
        }
        return null;
    }

    private String buildReviewContext(ReviewOutput previousReview) {
        if (previousReview == null) return "";

        StringBuilder sb = new StringBuilder("\n【上一次审查反馈 - 需要修复以下问题】\n");
        if (previousReview.getIssues() != null) {
            previousReview.getIssues().forEach(i -> sb.append("- ").append(i).append("\n"));
        }
        if (previousReview.getHallucinationIssues() != null) {
            sb.append("【幻觉问题 - 必须修复】\n");
            previousReview.getHallucinationIssues().forEach(i -> sb.append("- ").append(i).append("\n"));
        }
        if (previousReview.getFixSuggestions() != null) {
            sb.append("【修复建议】\n");
            previousReview.getFixSuggestions().forEach(s -> sb.append("- ").append(s).append("\n"));
        }
        return sb.toString();
    }

    private String serializePlan(PlannerOutput plan) {
        if (plan == null) return "无计划";
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(plan);
        } catch (Exception e) {
            return plan.toString();
        }
    }

    private String truncate(String str, int maxLen) {
        return str != null && str.length() > maxLen ? str.substring(0, maxLen) + "..." : str;
    }
}
