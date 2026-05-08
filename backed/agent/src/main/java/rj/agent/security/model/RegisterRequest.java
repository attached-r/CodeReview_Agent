package rj.agent.security.model;

import lombok.Data;

/**
 * 注册请求 DTO
 */
@Data
public class RegisterRequest {

    /** 用户名 */
    private String username;

    /** 明文密码 */
    private String password;

    /** 昵称（可选） */
    private String nickname;

    /** 邮箱（可选） */
    private String email;
}
