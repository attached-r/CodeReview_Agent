package rj.agent.security.model;

import lombok.Data;

/**
 * 登录请求 DTO
 */
@Data
public class LoginRequest {

    /** 用户名 */
    private String username;

    /** 明文密码 */
    private String password;
}
