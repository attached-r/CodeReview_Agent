package rj.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 对话消息实体类
 * 对应数据库表: conversation_messages
 * 用于持久化 ChatMemory，存储对话中的每一条消息记录
 *
 * @author agent
 */
@Data
@TableName("conversation_messages")
public class ConversationMessage {

    /**
     * 主键 ID（自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 对话 ID（UUID）
     * 关联 conversations 表的 conversationId 字段
     * 长度36字符
     */
    private String conversationId;

    /**
     * 消息唯一标识（UUID）
     * 每条消息的唯一标识，长度36字符
     * 默认值: gen_random_uuid() 自动生成
     */
    private String messageId;

    /**
     * 消息角色
     * 可选值：user（用户）、assistant（助手）、system（系统）等
     * 长度限制20字符
     */
    private String role;

    /**
     * 消息内容
     * 存储消息的完整文本内容，文本类型无长度限制
     */
    private String content;

    /**
     * Token 数量
     * 记录该消息消耗的 Token 数量，用于计费和统计
     */
    private Integer tokenCount;

    /**
     * 模型名称
     * 生成该消息所使用的 AI 模型名称
     * 长度限制100字符
     */
    private String modelName;

    /**
     * 元数据（JSON 格式）
     * JSONB 扩展信息，存储工具调用参数、上下文等额外数据
     * 使用 JacksonTypeHandler 自动序列化/反序列化
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    /**
     * 消息序号
     * 消息在对话中的序号，从 0 开始递增
     * 用于排序和定位消息
     */
    private Integer messageIndex;

    /**
     * 父消息 ID（UUID）
     * 支持分支对话/人工干预场景
     * 指向父消息的 messageId，为空表示根消息
     * 长度36字符
     */
    private String parentMessageId;

    /**
     * 创建时间
     * 记录消息创建的时间戳
     * 默认值: now()
     */
    private OffsetDateTime createdAt;
}
