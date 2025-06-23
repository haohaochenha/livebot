// 文件：src/main/java/com/example/douyinlive/config/CorsConfig.java
// 大白话注释：配置 CORS，允许前端跨域访问后端 API
package com.example.douyinlive.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 允许所有路径
                .allowedOriginPatterns(
                        "http://120.4.13.212:*", // 大白话：允许公网 IP 任意端口
                        "https://120.4.13.212:*", // 大白话：如果用 HTTPS，允许任意端口
                        "http://localhost:*", // 大白话：本地开发，任意端口
                        "http://192.168.10.17:*" // 大白话：内网测试 IP，任意端口
                ) // 大白话：只匹配 IP，端口随便
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 允许的请求方法
                .allowedHeaders("*") // 允许所有请求头
                .allowCredentials(true) // 大白话：启用 Cookie 支持，允许携带凭据
                .maxAge(3600); // 预检请求有效期
    }
}