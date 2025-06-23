package com.example.douyinlive.interceptor;

import com.example.douyinlive.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * JWT拦截器，验证请求中的token
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 调试：打印请求的完整路径和上下文路径
        System.out.println("JwtInterceptor 拦截请求：" + request.getRequestURI());
        System.out.println("请求路径：" + request.getRequestURI() + ", 上下文路径：" + request.getContextPath() + ", Servlet路径：" + request.getServletPath());

        // 如果是 /music/** 路径，直接放行
        if (request.getRequestURI().startsWith("/music/")) {
            System.out.println("请求匹配 /music/**，直接放行");
            return true;
        }

        // 如果是 OPTIONS 请求（CORS 预检请求），直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK); // 返回 200 状态码
            return true;
        }

        // 从请求头中获取token
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "未提供有效的token");
            response.getWriter().write(new ObjectMapper().writeValueAsString(error));
            return false;
        }

        try {
            // 去掉"Bearer "前缀
            token = token.substring(7);
            // 使用 jwtUtil 验证 token 是否过期
            if (jwtUtil.isTokenExpired(token)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "token已过期");
                response.getWriter().write(new ObjectMapper().writeValueAsString(error));
                return false;
            }
            // 验证通过，放行
            return true;
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "无效的token");
            response.getWriter().write(new ObjectMapper().writeValueAsString(error));
            return false;
        }
    }
}
