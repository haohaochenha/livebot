package com.example.douyinlive.config;

import org.springframework.beans.factory.annotation.Value; // 新增：注入配置
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.Arrays;
import java.util.Map;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    // 大白话：注入前端服务URL
    @Value("${frontend.url}")
    private String frontendUrl;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(liveMessageWebSocketHandler(), "/ws/live-messages")
                .addInterceptors(new HandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
                        String uri = request.getURI().toString();
                        String query = request.getURI().getQuery();
                        System.out.println("WebSocket 握手，完整 URI：" + uri);
                        System.out.println("WebSocket 握手，查询参数：" + (query != null ? query : "null"));

                        if (query == null || query.isEmpty()) {
                            System.err.println("查询参数为空，未找到 token，拒绝握手");
                            response.setStatusCode(HttpStatus.BAD_REQUEST);
                            response.getBody().write("Missing token parameter".getBytes());
                            return false;
                        }

                        // 大白话：尝试从查询参数中提取 token，支持复杂编码
                        try {
                            String[] params = query.split("&");
                            for (String param : params) {
                                if (param.startsWith("token=")) {
                                    String token = param.substring("token=".length());
                                    // 大白话：解码 token，防止编码问题
                                    token = java.net.URLDecoder.decode(token, "UTF-8");
                                    attributes.put("token", token);
                                    System.out.println("提取到 token：" + token);
                                    return true;
                                }
                            }
                            System.err.println("查询参数中未找到 token，参数列表：" + Arrays.toString(params));
                            response.setStatusCode(HttpStatus.BAD_REQUEST);
                            response.getBody().write("Missing token parameter".getBytes());
                            return false;
                        } catch (Exception e) {
                            System.err.println("解析 token 失败，错误：" + e.getMessage());
                            response.setStatusCode(HttpStatus.BAD_REQUEST);
                            response.getBody().write(("Failed to parse token: " + e.getMessage()).getBytes());
                            return false;
                        }
                    }

                    @Override
                    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                               WebSocketHandler wsHandler, Exception exception) {
                        System.out.println("WebSocket 握手完成，异常：" + (exception != null ? exception.getMessage() : "无"));
                    }
                })
                // 大白话：只允许前端服务URL访问WebSocket
                .setAllowedOrigins(frontendUrl);
    }

    @Bean
    public LiveMessageWebSocketHandler liveMessageWebSocketHandler() {
        return new LiveMessageWebSocketHandler();
    }
}