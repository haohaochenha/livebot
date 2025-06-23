package com.example.douyinlive.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value; // 新增：注入配置
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 验证码控制器，处理验证码生成
 */
@RestController
public class CaptchaController {

    // 大白话：注入前端服务URL
    @Value("${frontend.url}")
    private String frontendUrl;

    /**
     * 生成4位数字验证码
     * @param session HTTP会话，用于存储验证码
     * @return 验证码
     */
    @GetMapping("/captcha")
    public Map<String, Object> generateCaptcha(HttpSession session) {
        // 生成4位随机数字
        Random random = new Random();
        String captcha = String.format("%04d", random.nextInt(10000)); // 0000-9999
        // 存入session，设置5分钟有效期
        session.setAttribute("captcha", captcha);
        session.setMaxInactiveInterval(5 * 60); // 5分钟
        // 调试：打印存储的验证码、Session ID、是否新会话和 JSESSIONID
        System.out.println("生成验证码: " + captcha + ", Session ID: " + session.getId());
        System.out.println("存储到 Session 后的验证码: " + session.getAttribute("captcha"));
        System.out.println("Session 是否为新创建: " + session.isNew());
        System.out.println("JSESSIONID: " + session.getId());
        // 返回验证码
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("captcha", captcha); // 实际项目中可能返回图片，这里直接返回文本
        // 调试：打印 CORS 响应头
        System.out.println("CORS 响应头 - Access-Control-Allow-Origin: " + frontendUrl);
        System.out.println("CORS 响应头 - Access-Control-Allow-Credentials: true");
        return response;
    }
}