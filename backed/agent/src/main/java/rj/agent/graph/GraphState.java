package rj.agent.graph;

/**
 * 代码生成工作流的 State 键定义 — 存储在图工作流的 OverAllState 中
 *
 * <p>所有节点通过 {@code state.value(KEY_xxx)} 读取/写入中间数据，
 * 实现节点间的数据传递与共享。</p>
 */
public final class GraphState {

    private GraphState() {}

    /** 用户原始需求描述 */
    public static final String KEY_REQUIREMENT = "requirement";

    /** Planner 输出的任务规划 */
    public static final String KEY_PLANNER_OUTPUT = "plannerOutput";

    /** Coder 输出的生成代码 */
    public static final String KEY_CODER_OUTPUT = "coderOutput";

    /** Reviewer 输出的审查结果 */
    public static final String KEY_REVIEW_OUTPUT = "reviewOutput";

    /** 当前已重试次数（每次 Review 不通过 +1） */
    public static final String KEY_RETRY_COUNT = "retryCount";

    /** 对话 UUID，关联 conversations 表 */
    public static final String KEY_CONVERSATION_ID = "conversationId";

    /** 当前操作用户 ID（存入 State 后在异步线程中直接使用，不依赖 StpUtil） */
    public static final String KEY_USER_ID = "userId";

    // ========== 图节点名称 ==========

    public static final String NODE_PLANNER = "planner";
    public static final String NODE_CODER = "coder";
    public static final String NODE_REVIEWER = "reviewer";

    // ========== 条件边路由结果 ==========

    /** 审查通过，结束流程 */
    public static final String ROUTE_ACCEPTED = "accepted";
    /** 达到最大重试次数，结束流程 */
    public static final String ROUTE_MAX_RETRIES = "max_retries";
    /** 需要重试，回到 Coder */
    public static final String ROUTE_RETRY = "retry";
}
