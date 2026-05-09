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
import rj.agent.model.PlannerOutput;

import java.util.List;
import java.util.Map;
/**
 * 任务规划 Agent - 多语言全栈开发任务规划专家
 *
 * <p>职责：将用户的开发需求拆解为具体的、可执行的子任务计划，支持多种编程语言和框架。</p>
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>分析用户需求，理解业务场景和技术要求</li>
 *   <li>根据需求选择合适的技术栈（Java/Python/Node.js/Go/Rust 等）</li>
 *   <li>将复杂需求拆解为独立的子任务，明确每个任务的目标文件</li>
 *   <li>识别潜在的技术风险和不确定性，提供风险警告</li>
 * </ul>
 *
 * <p>反幻觉机制：</p>
 * <ul>
 *   <li>仅使用真实存在的编程语言 API、类库、框架或工具</li>
 *   <li>禁止编造包管理器依赖或版本号</li>
 *   <li>所有技术方案必须有明确的实现依据</li>
 *   <li>不确定内容必须在 riskWarning 中标注"待确认"</li>
 * </ul>
 *
 * <p>支持的技术栈：</p>
 * <ul>
 *   <li>后端：Java(Spring Boot)、Python(FastAPI/Django/Flask)、Node.js(Express/NestJS)、Go(Gin/Echo)、Rust(Actix/Rocket)</li>
 *   <li>前端：React、Vue、Angular、Svelte、原生 HTML/CSS/JavaScript</li>
 *   <li>数据库：MySQL、PostgreSQL、MongoDB、Redis、SQLite</li>
 *   <li>其他：Docker、Kubernetes、CI/CD、微服务架构等</li>
 * </ul>
 *
 * <p>工作流程：</p>
 * <ol>
 *   <li>接收用户需求描述</li>
 *   <li>通过 LLM 分析需求并生成任务规划（JSON 格式）</li>
 *   <li>解析并返回结构化的 PlannerOutput 对象</li>
 * </ol>
 */
@Slf4j
@Component
public class PlannerAgent {

    private static final String SYSTEM_PROMPT = """
            你是一个全栈开发任务规划专家。你的职责是将用户的需求拆解为具体的开发任务，支持多种编程语言和框架。

            【反幻觉强制规则 - 不可违反】
            1. 禁止编造不存在的编程语言 API、类库、框架或工具 — 只使用真实存在的技术栈
            2. 禁止编造不存在的包管理器依赖或版本号（如 Maven、npm、pip、cargo、go mod 等）
            3. 所有拆解的任务必须有明确的实现依据和技术可行性
            4. 如果不确定某个技术方案，必须在 riskWarning 中标注"待确认"
            5. 严格遵循用户的原始需求，不要自行添加未要求的功能
            6. 根据需求选择合适的技术栈，并在 taskAnalysis 中说明选择理由
            7. 输出必须严格匹配以下 JSON Schema 格式，禁止输出任何无关内容

            【支持的技术栈示例】
            - 后端：Java(Spring Boot)、Python(FastAPI/Django/Flask)、Node.js(Express/NestJS)、Go(Gin/Echo)、Rust(Actix/Rocket)
            - 前端：React、Vue、Angular、Svelte、原生 HTML/CSS/JavaScript
            - 数据库：MySQL、PostgreSQL、MongoDB、Redis、SQLite
            - 其他：Docker、Kubernetes、CI/CD、微服务架构等

            {format}
            """;

    private static final String USER_PROMPT = """
            {historyContext}
            请为以下需求制定代码实现计划：

            需求描述：
            {requirement}
            """;

    private final ChatModel chatModel;
    private final BeanOutputConverter<PlannerOutput> outputConverter;

    public PlannerAgent(@Qualifier("plannerChatModel") ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.outputConverter = new BeanOutputConverter<>(PlannerOutput.class);
    }

    public PlannerOutput execute(String requirement, String historyContext) {
        String format = outputConverter.getFormat();
        String userContent = USER_PROMPT
                .replace("{historyContext}", historyContext != null ? historyContext : "")
                .replace("{requirement}", requirement);

        Prompt prompt = new Prompt(List.of(
                new SystemMessage(SYSTEM_PROMPT.replace("{format}", format)),
                new UserMessage(userContent)
        ));

        log.info("[PlannerAgent] 开始规划任务...");
        ChatResponse response = chatModel.call(prompt);
        String content = response.getResult().getOutput().getText();

        log.info("[PlannerAgent] 收到响应: {}...", truncate(content, 200));

        PlannerOutput output = null;
        if (content != null) {
            output = outputConverter.convert(content);
        }
        log.info("[PlannerAgent] 规划完成，子任务数: {}, 风险警告: {}",
                output.getSubTasks() != null ? output.getSubTasks().size() : 0,
                output.getRiskWarning() != null ? output.getRiskWarning() : "无");

        return output;
    }

    private String truncate(String str, int maxLen) {
        return str != null && str.length() > maxLen ? str.substring(0, maxLen) + "..." : str;
    }
}
