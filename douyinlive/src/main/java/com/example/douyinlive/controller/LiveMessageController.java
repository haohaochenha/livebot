package com.example.douyinlive.controller;

import com.example.douyinlive.config.LiveMessageWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 直播消息控制器，提供启动/停止接口和消息推送端点，实际消息通过 WebSocket 推送
 */
@RestController
@RequestMapping("/live-messages")
public class LiveMessageController {

    // 控制是否拉取弹幕的标志，线程安全
    private final AtomicBoolean isFetching = new AtomicBoolean(false);

    // 注入 WebSocket 处理类
    @Autowired
    private LiveMessageWebSocketHandler webSocketHandler;

    // 用于 JSON 序列化
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * API 端点：启动拉取弹幕（实际由 Python 手动控制）
     * @return 启动结果
     */
    @PostMapping("/start")
    public Map<String, Object> startFetching() {
        Map<String, Object> response = new HashMap<>();
        if (isFetching.compareAndSet(false, true)) {
            System.out.println("开始拉取弹幕，状态切换为 true");
            response.put("status", "success");
            response.put("message", "已启动弹幕拉取，请确保 Python 程序运行");
        } else {
            System.out.println("已经处于拉取弹幕状态，无需重复启动");
            response.put("status", "success");
            response.put("message", "弹幕拉取已处于启动状态");
        }
        return response;
    }

    /**
     * API 端点：停止拉取弹幕（实际由 Python 手动控制）
     * @return 停止结果
     */
    @PostMapping("/stop")
    public Map<String, Object> stopFetching() {
        Map<String, Object> response = new HashMap<>();
        if (isFetching.compareAndSet(true, false)) {
            System.out.println("停止拉取弹幕，状态切换为 false");
            response.put("status", "success");
            response.put("message", "已停止弹幕拉取，请确保 Python 程序停止");
        } else {
            System.out.println("已经处于停止拉取弹幕状态，无需重复停止");
            response.put("status", "success");
            response.put("message", "弹幕拉取已处于停止状态");
        }
        return response;
    }

    /**
     * API 端点：接收 Python 推送的弹幕消息并分发给对应用户的前端
     * @param message 弹幕消息（JSON 格式）
     * @return 处理结果
     */
    @PostMapping("/push")
    public Map<String, Object> pushMessage(@RequestBody Map<String, Object> message) {
        Map<String, Object> response = new HashMap<>();
        try {
            // 大白话：获取消息中的 username 字段（数据库用的是 name）
            String username = (String) message.get("username");
            System.out.println("收到 Python 推送的弹幕消息，目标用户：" + username + "，消息内容：" + message);
            if (username == null || username.isEmpty()) {
                System.err.println("消息中缺少 username 字段，消息：" + message);
                response.put("status", "error");
                response.put("message", "消息中缺少 username 字段");
                return response;
            }

            // 大白话：检查消息类型，若为 enter_room，转换为弹幕消息格式
            String messageType = (String) message.get("type");
            Map<String, Object> formattedMessage = new HashMap<>(message);

            if ("enter_room".equals(messageType)) {
                Map<String, Object> data = (Map<String, Object>) message.get("data");
                String viewerName = (String) data.get("viewer_name");
                String viewerId = String.valueOf(data.get("viewer_id")); // 确保是字符串
                formattedMessage.put("type", "chat"); // 改为 chat 类型
                formattedMessage.put("data", new HashMap<String, Object>() {{
                    put("user_id", viewerId);
                    put("user_name", viewerName);
                    put("content", viewerName + " 进入了直播间");
                }});
                System.out.println("转换 enter_room 消息为弹幕格式，用户：" + username + "，转换后：" + formattedMessage);
            }

            // 大白话：将消息转为 JSON 字符串并分发给对应用户
            String messageJson = objectMapper.writeValueAsString(formattedMessage);
            webSocketHandler.sendMessageToUser(messageJson, username);
            System.out.println("成功分发弹幕消息给用户 " + username + "：" + messageJson);
            response.put("status", "success");
            response.put("message", "消息已分发给用户 " + username);
        } catch (Exception e) {
            System.err.println("推送弹幕消息失败，消息：" + message + "，错误：" + e.getMessage());
            e.printStackTrace();
            response.put("status", "error");
            response.put("message", "推送消息失败：" + e.getMessage());
        }
        return response;
    }
}