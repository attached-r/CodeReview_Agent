package rj.agent.security.service.impl;

import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rj.agent.entity.User;
import rj.agent.mapper.UserMapper;
import rj.agent.security.model.LoginRequest;
import rj.agent.security.model.RegisterRequest;
import rj.agent.security.service.AuthService;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证服务实现
 * <p>
 * 注册：使用 BCrypt 加密密码后落库。
 * 登录：校验用户名、密码和账号状态后调用 StpUtil.login() 签发 token。
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;

    @Override
    public User register(RegisterRequest req) {
        // 校验用户名是否已存在
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (count != null && count > 0) {
            throw new RuntimeException("用户名已被注册");
        }

        // 构建用户实体
        User user = new User();
        user.setUsername(req.getUsername());
        // BCrypt 加密密码，Sa-Token 内置了 BCrypt 工具类
        user.setPasswordHash(BCrypt.hashpw(req.getPassword(), BCrypt.gensalt()));
        user.setNickname(req.getNickname() != null ? req.getNickname() : req.getUsername());
        user.setEmail(req.getEmail());
        user.setRole("USER");
        user.setEnabled(true);

        // 保存到数据库
        userMapper.insert(user);
        return user;
    }

    @Override
    public Map<String, Object> login(LoginRequest req) {
        // 根据用户名查询用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }

        // 检查账号是否被禁用
        if (Boolean.FALSE.equals(user.getEnabled())) {
            throw new RuntimeException("账号已被禁用，请联系管理员");
        }

        // 校验密码（使用 BCrypt 比对）
        if (!BCrypt.checkpw(req.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("用户名或密码错误");
        }

        // 执行 Sa-Token 登录：以用户 ID 为登录标识
        StpUtil.login(user.getId());
        // 获取当前登录对应的 Token 信息
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();

        // 组装返回数据
        Map<String, Object> data = new HashMap<>();
        data.put("tokenInfo", tokenInfo);
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname());
        data.put("role", user.getRole());
        return data;
    }

    @Override
    public User getCurrentUser() {
        long userId = StpUtil.getLoginIdAsLong();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return user;
    }
}
