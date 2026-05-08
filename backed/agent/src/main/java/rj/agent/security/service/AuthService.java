package rj.agent.security.service;

import rj.agent.entity.User;
import rj.agent.security.model.LoginRequest;
import rj.agent.security.model.RegisterRequest;

import java.util.Map;

/**
 * 认证服务接口
 */
public interface AuthService {

    /**
     * 用户注册
     *
     * @param req 注册请求（用户名、密码、可选昵称/邮箱）
     * @return 注册成功的用户信息
     */
    User register(RegisterRequest req);

    /**
     * 用户登录
     *
     * @param req 登录请求（用户名、密码）
     * @return token 相关数据 + 用户基本信息
     */
    Map<String, Object> login(LoginRequest req);
}
