package rj.agent.security.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 拦截器配置
 * <p>
 * 注册全局拦截器，对 /api/** 路径进行登录校验，排除 /api/auth/**（登录/注册）。
 * 密码加密直接使用 Sa-Token 内置的 BCrypt 工具类，无需额外依赖。
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 Sa-Token 路由拦截器：匹配 /api/**，放行 /api/auth/** 和健康检查
        registry.addInterceptor(new SaInterceptor(handle -> {
            SaRouter.match("/api/**")
                    .notMatch("/api/auth/**")
                    .notMatch("/api/code-task/health")
                    .check(r -> StpUtil.checkLogin());
        })).addPathPatterns("/api/**");
    }
}
