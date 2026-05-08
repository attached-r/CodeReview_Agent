package rj.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 对话/任务实体类
 * 对应数据库表: conversations
 * 用于存储用户与 Agent 之间的对话会话信息
 */
@Data
@TableName("conversations")
public class Conversation {

    /** 主键 ID（自增） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 对话唯一标识（UUID）
     * 对外暴露的业务层标识，长度36字符
     * 默认值: gen_random_uuid() 自动生成
     */
    private String conversationId;

    /**
     * 用户 ID
     * 关联 users 表的外键
     */
    private Long userId;

    /**
     * 对话标题
     * 默认值: '新任务'
     * 长度限制200字符
     */
    private String title;

    /**
     * 用户需求描述
     * 存储用户的完整需求内容，文本类型无长度限制
     */
    private String requirement;

    /**
     * 对话状态
     * 可选值：ACTIVE（进行中）、COMPLETED（已完成）等
     * 默认值：ACTIVE
     * 长度限制20字符
     */
    private String status;

    /**
     * 创建时间
     * 记录对话会话创建的时间戳
     * 默认值: now()
     */
    private OffsetDateTime createdAt;

    /**
     * 更新时间
     * 记录对话信息最后一次修改的时间戳
     * 默认值: now()
     */
    private OffsetDateTime updatedAt;
}
