package rj.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 用户实体类
 * 对应数据库表: users
 * 用于存储系统用户的基本信息、认证信息和状态
 *
 * @author agent
 */
@Data
@TableName("users")
public class User {

    /**
     * 用户唯一标识（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名
     * 用于登录认证，长度限制50字符
     */
    private String username;

    /**
     * 邮箱地址
     * 用户邮箱，具有唯一性约束，长度限制100字符
     */
    private String email;

    /**
     * 密码哈希值
     * 使用 BCrypt 算法加密后的密码，长度限制200字符
     * 原始密码经过加密存储，不可逆向解密
     */
    private String passwordHash;

    /**
     * 用户昵称
     * 显示名称，长度限制50字符
     */
    private String nickname;

    /**
     * 用户角色
     * 可选值：USER（普通用户）、ADMIN（管理员）
     * 默认值：USER
     * 长度限制20字符
     */
    private String role;

    /**
     * 账号是否启用
     * true: 账号正常可用
     * false: 账号被禁用，无法登录
     * 默认值：true
     */
    private Boolean enabled;

    /**
     * 创建时间
     * 记录用户账号创建的时间戳
     */
    private OffsetDateTime createdAt;

    /**
     * 更新时间
     * 记录用户信息最后一次修改的时间戳
     */
    private OffsetDateTime updatedAt;
}
