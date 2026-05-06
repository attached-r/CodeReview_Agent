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
import rj.agent.model.ReviewOutput;

import java.util.List;
/**
 * 代码审查 Agent - 多编程语言代码审查专家
 *
 * <p>职责：审查生成的代码，发现潜在问题，特别是幻觉问题（编造 API、依赖等）。</p>
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>API 真实性校验：检查所有使用的编程语言 API、类库、框架是否真实存在</li>
 *   <li>依赖有效性校验：检查所有包管理器依赖是否有效、版本号是否合理</li>
 *   <li>代码可编译性校验：检查语法、类型、导入路径是否正确</li>
 *   <li>逻辑正确性校验：检查业务逻辑是否符合需求</li>
 *   <li>语言规范校验：确保代码符合该语言的惯用写法（idiomatic code）</li>
 *   <li>质量打分：根据代码质量给出 0-100 分的评分</li>
 * </ul>
 *
 * <p>幻觉问题判定标准：</p>
 * <ul>
 *   <li>使用了编程语言中不存在的类、方法或 API → 幻觉</li>
 *   <li>引用了不存在的包管理器依赖或错误的版本号 → 幻觉</li>
 *   <li>包/模块导入路径错误 → 幻觉</li>
 *   <li>语法错误或类型错误 → 基础问题</li>
 *   <li>违反了语言的最佳实践 → 代码质量问题</li>
 * </ul>
 *
 * <p>支持的语言生态：</p>
 * <ul>
 *   <li>Java: Spring Boot、JPA、Jakarta EE 等官方 API，Maven Central / Gradle 仓库</li>
 *   <li>Python: Django、Flask、FastAPI、SQLAlchemy 等标准库，PyPI 仓库</li>
 *   <li>JavaScript/TypeScript: React、Vue、Express、Node.js 等 npm 生态，npm / yarn 仓库</li>
 *   <li>Go: Gin、Echo、标准库等官方包，Go Module Registry</li>
 *   <li>Rust: Actix、Tokio、标准库等 crates.io 包，crates.io 仓库</li>
 * </ul>
 *
 * <p>打分标准：</p>
 * <ul>
 *   <li>90-100分：代码质量优秀，无幻觉问题，可直接使用</li>
 *   <li>70-89分：有小问题，但不影响编译/运行</li>
 *   <li>60-69分：有影响编译的问题或轻微幻觉</li>
 *   <li>0-59分：存在严重幻觉问题，必须打回重修</li>
 * </ul>
 *
 * <p>工作流程：</p>
 * <ol>
 *   <li>接收需求描述和 CoderAgent 生成的代码输出</li>
 *   <li>构建系统提示词和用户提示词（包含代码内容、文件列表、依赖声明）</li>
 *   <li>调用 LLM 进行代码审查</li>
 *   <li>解析并返回结构化的 ReviewOutput 对象（包含问题列表、幻觉问题、修复建议、评分等）</li>
 * </ol>
 *
 * <p>审查结果应用：</p>
 * <ul>
 *   <li>如果 accepted=true 且 score>=70，代码可以接受</li>
 *   <li>如果 accepted=false 或 score<70，需要将审查反馈传递给 CoderAgent 重新生成</li>
 *   <li>hallucinationIssues 中的问题必须优先修复</li>
 *   <li>fixSuggestions 提供具体的修复指导</li>
 * </ul>
 */

@Slf4j
@Component
public class ReviewerAgent {

    private static final String SYSTEM_PROMPT = """
            你是一个多编程语言代码审查专家，专门负责发现代码中的问题，特别是幻觉问题。
            
            【审查重点】
            1. API真实性校验 — 检查所有使用的编程语言 API、类库、框架是否真实存在
               - Java: Spring Boot、JPA、Jakarta EE 等官方 API
               - Python: Django、Flask、FastAPI、SQLAlchemy 等标准库
               - JavaScript/TypeScript: React、Vue、Express、Node.js 等 npm 生态
               - Go: Gin、Echo、标准库等官方包
               - Rust: Actix、Tokio、标准库等 crates.io 包
            2. 依赖有效性校验 — 检查所有包管理器依赖是否有效、版本号是否合理
               - Java: Maven Central / Gradle 仓库
               - JavaScript/TypeScript: npm / yarn 仓库
               - Python: PyPI 仓库
               - Rust: crates.io 仓库
               - Go: Go Module Registry
            3. 代码可编译性校验 — 检查语法、类型、导入路径是否正确
            4. 逻辑正确性校验 — 检查业务逻辑是否符合需求
            5. 语言规范校验 — 确保代码符合该语言的惯用写法（idiomatic code）
    
            【幻觉问题判定标准】
            - 使用了编程语言中不存在的类、方法或 API → 幻觉
            - 引用了不存在的包管理器依赖或错误的版本号 → 幻觉
            - 包/模块导入路径错误 → 幻觉
            - 语法错误或类型错误 → 基础问题
            - 违反了语言的最佳实践 → 代码质量问题
    
            【打分标准】
            - 90-100分：代码质量优秀，无幻觉问题，可直接使用
            - 70-89分：有小问题，但不影响编译/运行
            - 60-69分：有影响编译的问题或轻微幻觉
            - 0-59分：存在严重幻觉问题，必须打回重修

            输出必须严格匹配以下 JSON Schema 格式：
            {format}
            """;

    private static final String USER_PROMPT_TEMPLATE = """
            请审查以下生成的代码：

            【需求描述】
            {requirement}

            【生成的代码】
            {code}

            【代码文件列表】
            {fileList}

            【依赖声明】
            {dependencies}
            """;

    private final ChatModel chatModel;
    private final BeanOutputConverter<ReviewOutput> outputConverter;

    public ReviewerAgent(@Qualifier("reviewerChatModel") ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.outputConverter = new BeanOutputConverter<>(ReviewOutput.class);
    }

    public ReviewOutput execute(String requirement, CoderOutput coderOutput) {
        String format = outputConverter.getFormat();

        StringBuilder codeContent = new StringBuilder();
        StringBuilder fileList = new StringBuilder();

        if (coderOutput.getCodeFiles() != null) {
            for (CoderOutput.CodeFile file : coderOutput.getCodeFiles()) {
                fileList.append("- ").append(file.getFilePath())
                        .append(" (").append(file.getLanguage()).append(")")
                        .append(": ").append(file.getDescription()).append("\n");
                codeContent.append("=== ").append(file.getFilePath()).append(" ===\n");
                codeContent.append(file.getContent()).append("\n\n");
            }
        }

        String dependencies = coderOutput.getDependencies() != null
                ? String.join("\n", coderOutput.getDependencies())
                : "无";

        String userContent = USER_PROMPT_TEMPLATE
                .replace("{requirement}", requirement)
                .replace("{code}", codeContent.toString())
                .replace("{fileList}", fileList.toString())
                .replace("{dependencies}", dependencies);

        Prompt prompt = new Prompt(List.of(
                new SystemMessage(SYSTEM_PROMPT.replace("{format}", format)),
                new UserMessage(userContent)
        ));

        log.info("[ReviewerAgent] 开始审查代码...");

        ChatResponse response = chatModel.call(prompt);
        String content = response.getResult().getOutput().getText();

        ReviewOutput output = null;
        if (content != null) {
            output = outputConverter.convert(content);
        }

        log.info("[ReviewerAgent] 审查完成: accepted={}, score={}, 问题数={}, 幻觉数={}",
                output.isAccepted(), output.getScore(),
                output.getIssues() != null ? output.getIssues().size() : 0,
                output.getHallucinationIssues() != null ? output.getHallucinationIssues().size() : 0);

        return output;
    }
}
