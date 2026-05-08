package rj.agent.security.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rj.agent.security.model.LoginRequest;
import rj.agent.security.model.RegisterRequest;
import rj.agent.security.model.Result;
import rj.agent.security.service.AuthService;

import java.util.Map;

/**
 * 认证控制器
 * <p>
 * 提供用户注册和登录两个公开接口（无需 token），
 * 已通过 SaTokenConfig 中的拦截器配置放行 /api/auth/**。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户注册
     *
     * @param req 注册信息（username、password 必填）
     * @return 注册结果
     */
    @PostMapping("/register")
    public Result<?> register(@RequestBody RegisterRequest req) {
        // 简单参数校验
        if (req.getUsername() == null || req.getUsername().isBlank()) {
            return Result.error(400, "用户名不能为空");
        }
        if (req.getPassword() == null || req.getPassword().length() < 6) {
            return Result.error(400, "密码长度不能少于6位");
        }

        authService.register(req);
        return Result.ok();
    }

    /**
     * 用户登录
     *
     * @param req 登录信息（username、password）
     * @return token 数据 + 用户基本信息
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest req) {
        if (req.getUsername() == null || req.getUsername().isBlank()) {
            return Result.error(400, "用户名不能为空");
        }
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            return Result.error(400, "密码不能为空");
        }

        Map<String, Object> data = authService.login(req);
        return Result.ok(data);
    }
}
