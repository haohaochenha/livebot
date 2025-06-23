package com.example.douyinlive.controller;

import com.example.douyinlive.entity.ModelConfig; // 新增
import com.example.douyinlive.mapper.ModelConfigMapper; // 新增
import com.example.douyinlive.util.JwtUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import jakarta.servlet.http.HttpSession;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/qwen")
public class QwenController {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ModelConfigMapper modelConfigMapper; // 新增

//    private final Map<String, Map<String, Object>> userContexts = new ConcurrentHashMap<>();


    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> requestBody,
                                                    @RequestHeader("Authorization") String token,
                                                    HttpSession session) throws JsonProcessingException {
        System.out.println("收到 AI 对话请求，Session ID：" + session.getId());
        try {
            // 从数据库获取模型配置
            List<ModelConfig> configs = modelConfigMapper.selectAllModelConfigs();
            if (configs.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "未找到模型配置");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(errorResponse);
            }
            ModelConfig config = configs.get(0); // 假设取第一条配置

            token = token.substring(7);
            String username = jwtUtil.getUsernameFromToken(token);
            System.out.println("对话请求用户：" + username);

            RestTemplate restTemplate = new RestTemplate();
            // 直接使用数据库的完整 URL
            String url = config.getQwenurl();
            System.out.println("调用通义千问 API URL：" + url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + config.getQwenkey());

            Map<String, Object> body = new HashMap<>();
            body.put("model", requestBody.getOrDefault("model", config.getQwenmodel()));
            body.put("stream", false); // 大白话：关闭流式传输，获取完整响应
            body.put("modalities", requestBody.getOrDefault("modalities", List.of("text")));
            body.put("temperature", requestBody.get("temperature"));
            body.put("top_p", requestBody.get("top_p"));
            body.put("presence_penalty", requestBody.get("presence_penalty"));
            body.put("response_format", requestBody.getOrDefault("response_format", Map.of("type", "text")));
            body.put("max_tokens", requestBody.get("max_tokens"));
            body.put("n", requestBody.getOrDefault("n", 1));
            body.put("seed", requestBody.get("seed"));
            body.put("stop", requestBody.get("stop"));
            body.put("tools", requestBody.get("tools")); // 保留 tools=null
            body.put("enable_search", requestBody.getOrDefault("enable_search", false));
            body.put("search_options", requestBody.get("search_options"));
            body.put("translation_options", requestBody.get("translation_options"));

            // 大白话：直接用前端传来的消息，不加历史
            List<Map<String, Object>> messages = (List<Map<String, Object>>) requestBody.getOrDefault("messages", new ArrayList<>());
            body.put("messages", messages);

            System.out.println("通义千问 API 请求体：" + new ObjectMapper().writeValueAsString(body));

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // 大白话：不再用流式请求，直接获取完整的 JSON 响应
            ResponseEntity<Map> response;
            try {
                response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, Map.class);
            } catch (HttpClientErrorException e) {
                String errorBody = e.getResponseBodyAsString();
                System.out.println("通义千问 API 返回错误：" + errorBody);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "通义千问 API 调用失败：" + e.getStatusCode() + " - " + errorBody);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(errorResponse);
            }

            // 大白话：提取 AI 的回复内容
            Map<String, Object> responseBody = response.getBody();
            String aiResponse = "";
            if (responseBody != null && responseBody.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                if (!choices.isEmpty() && choices.get(0).containsKey("message")) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    aiResponse = (String) message.getOrDefault("content", "");
                }
            }

            // 大白话：构造返回给前端的响应
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("content", aiResponse);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(result);

        } catch (Exception e) {
            System.out.println("AI 对话失败：" + e.getMessage());
            e.printStackTrace();
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "AI 对话失败，服务器错误：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error);
        }
    }



}