// 文件：LiveMessageWebSocketHandler.java
// 修改：完整替换文件内容，确保用户管理和消息分发逻辑正确
package com.example.douyinlive.config;

import com.example.douyinlive.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 处理类，管理直播消息的推送
 */
@Component
public class LiveMessageWebSocketHandler extends TextWebSocketHandler {

    // 存储用户 WebSocket 会话，key 是 username，value 是 WebSocketSession
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // 注入 JwtUtil 用于解析 token
    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 大白话：新的 WebSocket 连接建立了，赶紧把用户和会话存起来
        String token = (String) session.getAttributes().get("token");
        System.out.println("WebSocket 连接建立，Session ID：" + session.getId() + "，token：" + (token != null ? token : "缺失"));
        if (token == null) {
            System.err.println("WebSocket 连接缺少 token，关闭连接，Session ID：" + session.getId());
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        try {
            // 解析 token 获取 username
            String username = jwtUtil.getUsernameFromToken(token);
            System.out.println("解析 token 成功，用户：" + username + "，Session ID：" + session.getId());
            // 检查用户是否已存在会话，若存在则关闭旧会话
            WebSocketSession oldSession = sessions.get(username);
            if (oldSession != null && oldSession.isOpen()) {
                System.out.println("用户 " + username + " 已存在会话，关闭旧会话，旧 Session ID：" + oldSession.getId());
                try {
                    oldSession.close(CloseStatus.NORMAL);
                } catch (IOException e) {
                    System.err.println("关闭旧会话失败，用户 " + username + "，错误：" + e.getMessage());
                }
            }
            // 存储新会话
            sessions.put(username, session);
            System.out.println("已存储用户 " + username + " 的新会话，当前会话数：" + sessions.size());
        } catch (Exception e) {
            System.err.println("解析 token 失败，关闭连接，Session ID：" + session.getId() + "，错误：" + e.getMessage());
            session.close(CloseStatus.BAD_DATA);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // 大白话：WebSocket 连接断了，把用户的会话信息删掉
        String token = (String) session.getAttributes().get("token");
        if (token != null) {
            try {
                String username = jwtUtil.getUsernameFromToken(token);
                sessions.remove(username);
                System.out.println("WebSocket 连接关闭，用户：" + username + "，状态码：" + status.getCode());
            } catch (Exception e) {
                System.out.println("解析 token 失败，无法移除会话：" + e.getMessage());
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        // 大白话：传输出错了，记录一下然后关掉连接
        System.out.println("WebSocket 传输错误，Session ID：" + session.getId() + "，错误：" + exception.getMessage());
        session.close(CloseStatus.SERVER_ERROR);
    }

    /**
     * 向指定用户推送消息
     * @param message 消息内容（JSON 字符串）
     * @param name 目标用户名字（数据库中的 name 字段）
     */
    public void sendMessageToUser(String message, String name) {
        // 大白话：找到用户的 WebSocket 会话，把消息发过去
        System.out.println("尝试推送消息给用户 " + name + "，消息内容：" + message);
        WebSocketSession session = sessions.get(name);
        if (session != null && session.isOpen()) {
            try {
                synchronized (session) { // 确保线程安全
                    session.sendMessage(new TextMessage(message));
                    System.out.println("成功推送消息给用户 " + name + "，Session ID：" + session.getId());
                }
            } catch (IOException e) {
                System.err.println("推送消息给用户 " + name + " 失败，Session ID：" + session.getId() + "，错误：" + e.getMessage());
                try {
                    session.close(CloseStatus.SERVER_ERROR);
                    sessions.remove(name);
                    System.out.println("已关闭并移除用户 " + name + " 的会话");
                } catch (IOException ex) {
                    System.err.println("关闭会话失败，用户 " + name + "，错误：" + ex.getMessage());
                }
            }
        } else {
            System.err.println("用户 " + name + " 的 WebSocket 会话不存在或已关闭，当前会话数：" + sessions.size());
        }
    }
}