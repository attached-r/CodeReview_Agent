package rj.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流式事件模型，用于 Flux SSE 实时推送工作流执行状态
 *
 * <p>type 取值：
 * <ul>
 *   <li><b>phase</b> — 阶段切换事件，表示当前正在执行的 Agent 阶段</li>
 *   <li><b>token</b> — LLM Token 流式输出事件（预留）</li>
 *   <li><b>result</b> — 最终结果事件，包含完整的 plan / code / review 数据</li>
 *   <li><b>error</b> — 错误事件</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamEvent {
    private String type;
    private String phase;
    private String content;
    private Object data;

    public static StreamEvent phase(String phase, String content) {
        return StreamEvent.builder()
                .type("phase")
                .phase(phase)
                .content(content)
                .build();
    }

    public static StreamEvent result(Object data) {
        return StreamEvent.builder()
                .type("result")
                .phase("end")
                .content("任务完成")
                .data(data)
                .build();
    }

    public static StreamEvent error(String message) {
        return StreamEvent.builder()
                .type("error")
                .phase("end")
                .content(message)
                .build();
    }
}
