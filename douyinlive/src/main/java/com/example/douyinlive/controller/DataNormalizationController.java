package com.example.douyinlive.controller;

import com.example.douyinlive.entity.User;
import com.example.douyinlive.mapper.ModelConfigMapper;
import com.example.douyinlive.entity.ModelConfig;
import com.example.douyinlive.mapper.UserMapper;
import com.example.douyinlive.util.JwtUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 数据规范化控制器，专门处理用户上传的杂乱文本，整理成问答对 JSON 格式
 * 需要 JWT 验证，只有登录用户才能调用
 */
@RestController
@RequestMapping("/normalize")
public class DataNormalizationController {

    // 注入 RestTemplate，调用通义千问 API
    @Autowired
    private RestTemplate restTemplate;

    // 注入 JwtUtil，解析 token
    @Autowired
    private JwtUtil jwtUtil;

    // 注入 UserMapper，查询用户信息
    @Autowired
    private UserMapper userMapper;

    // 注入 ModelConfigMapper，获取模型配置
    @Autowired
    private ModelConfigMapper modelConfigMapper;

    // 从配置文件读取最大 Token 数
    @Value("${embedding.max-tokens}")
    private int maxTokens;

    // 从配置文件读取文件大小限制
    @Value("${knowledge-base.max-file-size}")
    private long maxFileSize;

    /**
     * 硬编码的提示词，用于规范化文本
     * 大白话注释：更新提示词，要求添加 link_id 字段，按主题或产品分组，前三个问答对归为“1号链接”，后续按需分组
     */
    private static final String NORMALIZE_PROMPT =
            "你是一个文本规范化助手。你的任务是将以下杂乱的文本转换为结构化的问答对 JSON 格式，每个对象包含 \"question\"、\"answer\" 和 \"link_id\" 字段。要求如下：\n" +
                    "1. 问题要简洁明了，准确概括文本内容的主题、产品特点或关键信息。\n" +
                    "2. 答案必须逐句复用原文的表达，保留原文的风格、语气和全部细节，禁止简化、改写或删除任何信息，包括但不限于功能描述、价格、优惠信息、推荐理由、目标人群、使用场景等。\n" +
                    "3. 按文本的主题或产品分段生成问答对，确保每个关键信息点（如功能、价格、优惠）都有对应的问答对。\n" +
                    "4. 为每个问答对添加 \"link_id\" 字段，值为字符串，表示关联的链接编号（如 \"1号链接\"、\"2号链接\"）。\n" +
                    "5. 输出必须是标准 JSON 字符串，直接返回 JSON 数组，不包含 ```json 前缀或后缀，不包含额外说明文字，确保数据的完整。\n" +
                    "6. 如果文本无法生成问答对，返回空数组 []。\n" +
                    "输出格式为 JSON 数组：[{ \"question\": \"问题\", \"answer\": \"答案\", \"link_id\": \"链接编号\" }, ...]\n" +
                    "输入文本：\n%s";

    /**
     * 规范化上传的文本文件为问答对 JSON，并返回可下载的文件
     * 需要 JWT 验证，确保用户已登录
     * @param file 上传的 TXT 或 Word 文件
     * @param token Authorization 头中的 token
     * @return 包含问答对的 JSON 文件
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> normalizeText(
            @RequestPart("file") MultipartFile file,
            @RequestHeader("Authorization") String token) {
        System.out.println("收到文本规范化请求，文件名：" + (file != null ? file.getOriginalFilename() : "无"));
        Map<String, Object> response = new HashMap<>();
        try {
            // 验证 token，去掉 "Bearer " 前缀
            if (token == null || !token.startsWith("Bearer ")) {
                response.put("success", false);
                response.put("message", "未提供有效的 token");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ObjectMapper().writeValueAsString(response).getBytes());
            }
            token = token.substring(7);

            // 解析 token，获取用户名
            String username = jwtUtil.getUsernameFromToken(token);
            User user = userMapper.selectUserByName(username);
            if (user == null) {
                response.put("success", false);
                response.put("message", "用户不存在");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ObjectMapper().writeValueAsString(response).getBytes());
            }
            System.out.println("用户验证通过，用户名：" + username);

            // 验证文件
            if (file == null || file.isEmpty()) {
                response.put("success", false);
                response.put("message", "文件不能为空");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ObjectMapper().writeValueAsString(response).getBytes());
            }
            // 检查文件大小
            if (file.getSize() > maxFileSize) {
                response.put("success", false);
                response.put("message", "文件大小超过限制（最大 " + (maxFileSize / 1024 / 1024) + "MB）");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ObjectMapper().writeValueAsString(response).getBytes());
            }
            String fileName = file.getOriginalFilename().toLowerCase();
            if (!fileName.endsWith(".txt") && !fileName.endsWith(".doc") && !fileName.endsWith(".docx")) {
                response.put("success", false);
                response.put("message", "仅支持 TXT、Word（.doc、.docx）文件");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ObjectMapper().writeValueAsString(response).getBytes());
            }

            // 解析文件为文本
            String content;
            try (InputStream is = file.getInputStream()) {
                if (fileName.endsWith(".txt")) {
                    content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    System.out.println("解析 TXT 文件内容，长度：" + content.length());
                } else {
                    content = extractTextFromWord(is, fileName);
                    System.out.println("解析 Word 文件内容，长度：" + content.length());
                }
            }
            if (content.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "文件内容为空");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ObjectMapper().writeValueAsString(response).getBytes());
            }

            // 调用通义千问 API 规范化文本
            List<Map<String, String>> qaPairs = normalizeToQuestionAnswer(content);
            System.out.println("生成问答对数量：" + qaPairs.size());
            if (qaPairs.isEmpty()) {
                response.put("success", false);
                response.put("message", "无法从文本中提取问答对");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ObjectMapper().writeValueAsString(response).getBytes());
            }

            // 验证 qaPairs 格式
            for (Map<String, String> qa : qaPairs) {
                if (!qa.containsKey("question") || !qa.containsKey("answer") || !qa.containsKey("link_id")) {
                    System.out.println("无效的问答对格式：" + qa);
                    response.put("success", false);
                    response.put("message", "生成的问答对格式无效，缺少必要字段");
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new ObjectMapper().writeValueAsString(response).getBytes());
                }
            }

            // 生成 JSON 文件
            byte[] jsonBytes;
            try {
                jsonBytes = new ObjectMapper().writeValueAsString(qaPairs).getBytes(StandardCharsets.UTF_8);
                System.out.println("JSON 序列化成功，字节长度：" + jsonBytes.length);
            } catch (JsonProcessingException e) {
                System.out.println("JSON 序列化失败：" + e.getMessage());
                response.put("success", false);
                response.put("message", "JSON 序列化失败：" + e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new ObjectMapper().writeValueAsString(response).getBytes());
            }

            // 设置响应头，支持文件下载
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setContentDisposition(ContentDisposition.attachment().filename("normalized_data.json").build());

            return new ResponseEntity<>(jsonBytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            System.out.println("文本规范化失败：" + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "文本规范化失败，服务器错误：" + e.getMessage());
            try {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new ObjectMapper().writeValueAsString(response).getBytes());
            } catch (JsonProcessingException ex) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("服务器错误".getBytes());
            }
        }
    }

    /**
     * 从 Word 文件（.doc 或 .docx）中提取文本内容
     * @param is 文件输入流
     * @param fileName 文件名，用于判断文件类型
     * @return 提取的文本内容
     * @throws IOException 如果文件解析失败
     */
    private String extractTextFromWord(InputStream is, String fileName) throws IOException {
        StringBuilder content = new StringBuilder();
        try {
            if (fileName.endsWith(".doc")) {
                // 解析 .doc 文件
                HWPFDocument document = new HWPFDocument(is);
                WordExtractor extractor = new WordExtractor(document);
                content.append(extractor.getText());
                extractor.close();
                document.close();
            } else if (fileName.endsWith(".docx")) {
                // 解析 .docx 文件
                XWPFDocument document = new XWPFDocument(is);
                for (XWPFParagraph paragraph : document.getParagraphs()) {
                    content.append(paragraph.getText()).append("\n");
                }
                document.close();
            }
        } catch (Exception e) {
            System.out.println("Word 文件解析失败：" + e.getMessage());
            throw new IOException("无法解析 Word 文件：" + e.getMessage(), e);
        }
        return content.toString();
    }

    /**
     * 调用通义千问 API，将杂乱文本整理为问答对
     * @param content 原始文本
     * @return 问答对列表
     */
    private List<Map<String, String>> normalizeToQuestionAnswer(String content) {
        // 从数据库获取模型配置
        List<ModelConfig> configs = modelConfigMapper.selectAllModelConfigs();
        if (configs.isEmpty()) {
            System.out.println("未找到模型配置");
            return new ArrayList<>();
        }
        ModelConfig config = configs.get(0); // 假设取第一条配置

        String url = config.getDatageshiurl() + "/chat/completions";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + config.getDatageshikey());

        // 构造提示词
        String prompt = String.format(NORMALIZE_PROMPT, content);

        // 构造请求体
        Map<String, Object> body = new HashMap<>();
        body.put("model", config.getDatageshimodel());
        body.put("messages", List.of(
                Map.of("role", "user", "content", prompt)
        ));
        body.put("stream", false);
        body.put("response_format", Map.of("type", "json_object")); // 强制输出标准 JSON
        body.put("presence_penalty", 1.5); // 增加多样性，保留原文细节
        body.put("max_tokens", maxTokens); // 使用配置文件中的 maxTokens
        body.put("temperature", 0.0); // 设置模型温度为 0.0，数据格式化要最保守，严格按原文和提示词生成

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
            System.out.println("通义千问 API 响应状态码：" + response.getStatusCode());
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String result = (String) message.get("content");
                    // 清理可能的非 JSON 内容
                    result = result.replace("```json", "").replace("```", "")
                            .replaceAll("(?m)^\\s*//.*$", "") // 移除注释
                            .replaceAll("\\s*\\n\\s*", "") // 移除多余换行和空白
                            .trim();
                    System.out.println("通义千问 API 返回内容：" + result);
                    // 解析返回的 JSON
                    List<Map<String, String>> qaPairs = new ObjectMapper().readValue(result, List.class);
                    System.out.println("解析后的问答对：" + qaPairs);
                    return qaPairs;
                }
            }
            System.out.println("通义千问 API 调用失败，状态码：" + response.getStatusCode());
            return new ArrayList<>();
        } catch (HttpClientErrorException | JsonProcessingException e) {
            System.out.println("通义千问 API 返回错误：" + e.getMessage());
            return new ArrayList<>();
        }
    }
}