package com.example.douyinlive.controller;

import com.example.douyinlive.entity.ModelConfig;
import com.example.douyinlive.entity.User;
import com.example.douyinlive.mapper.ModelConfigMapper;
import com.example.douyinlive.mapper.UserMapper;
import com.example.douyinlive.util.JwtUtil;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 直播剧本生成控制器，处理用户上传的商品文档，生成带货直播剧本
 * 需要 JWT 验证，只有登录用户才能调用
 */
@RestController
@RequestMapping("/live-script")
public class LiveScriptController {

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

    // 大白话注释：从配置文件读取直播剧本专用的最大 Token 数
    @Value("${live-script.max-tokens}")
    private int maxTokens;

    // 大白话注释：从配置文件读取直播剧本专用的文件大小限制
    @Value("${live-script.max-file-size}")
    private long maxFileSize;

    // 大白话注释：从配置文件读取直播剧本专用的最大输入 Token 数
    @Value("${live-script.max-input-tokens}")
    private int maxInputTokens;

    /**
     * 硬编码的提示词，用于生成直播剧本
     * 大白话注释：提示词要求直接进入直播带货状态，生成紧凑、热情、纯商品相关的剧本，突出真人情感语音复刻、实时互动、云端运行等优势，仅提取商品文档中的商品信息，强力忽略无关内容，规避抖音敏感词，适合直接语音合成，长度至少 5000 字。
     */
    /**
     * 硬编码的提示词，用于生成直播剧本
     * 大白话注释：提示词要求直接进入直播带货状态，生成紧凑、热情、纯商品相关的剧本，突出真人情感语音复刻、实时互动、云端运行等优势，仅提取商品文档中的商品信息，强力忽略无关内容，规避抖音敏感词，适合直接语音合成，长度至少 5000 字。
     */
    private static final String LIVE_SCRIPT_PROMPT =

            "0，你必须主动性的去引导用户 ，比如说多点点关注 看看小黄车 点点爱心之类的 一个主播该有的话术 不要说下播不要说任何跟下播有关的话语"+
            "1，你是一个直播带货主播助手 ，切记 很重要这个 不得生成直播的开头和结尾就是（欢迎来到我们的直播间！谢谢大家的支持，我们下期再见！ 这种类似的话） ，你得每个十句话就让观众点点关注点点爱心，如果有喜欢的可以到小黄车看一看说类似的话，当然是尽量描述的好一点 你的任务是根据以下商品文档生成一段适合直播的带货剧本 ，你在生成的时候 尽量用中文 (例如：一盒10张 每张3 层 所以一共3乘于10 就是30层这个乘于 就是我说的中文，因为常见的加减乘除语音识别可能不识别所以尽量用中文) " +
           "2.模拟真人主播口吻 直接介绍产品 要求如下 禁止生成markdown格式的数据，直接开始口述产品纯文本形式不要markdown的，只能生成常见的标点符号 ，。！？ ： 禁止生成其他的例如：- ** # ，主要是为了防止你生成markdown" +
           "3.不要有开始和结尾，不要有废话，介绍的必须大白话并且接地气，" +
           "4.在介绍参数的时候有些重要的部分比如说(咱们家的内裤都是5A级抗菌的啊，对的哦你没听错5A级别的，质量超级好，真的闭眼入就对了) 这种类似的话" +
           "5.特别注意 注意千万不要生成开播的话比如就是大家好欢迎大家来到直播间类似的话，还有就是直播结尾的话比如什么谢谢大家支持咱们下期再见之类的 真的这个很重要的，千万不要生成直播的结束语" +
            "6.在介绍价格的时候一定要走话术方式的介绍(例如：在外面得998块钱 888块钱吧，咱们今天不要998 不要888 399块钱上车，真的限时爆款活动 你没听错哈399 很划算的比外面便宜很多很多) 这种类似的话"  +

           "商品文档内容如下：%s";

    /**
     * 生成直播带货剧本，返回纯文本
     * 需要 JWT 验证，确保用户已登录
     * @param file 上传的 TXT 或 Word 文件
     * @param token Authorization 头中的 token
     * @return 直播剧本纯文本
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> generateLiveScript(
            @RequestPart("file") MultipartFile file,
            @RequestHeader("Authorization") String token) {
        System.out.println("收到直播剧本生成请求，文件名：" + (file != null ? file.getOriginalFilename() : "无"));
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

            // 大白话注释：预处理 content，替换 % 为 %%，避免 String.format 解析错误
            content = content.replace("%", "%%");

            // 大白话注释：检查输入 Token 数，文档内容 + 提示词不能超过配置的最大输入限制
            String prompt = String.format(LIVE_SCRIPT_PROMPT, content);
            int estimatedTokens = estimateTokenCount(prompt);
            System.out.println("估计输入 Token 数：" + estimatedTokens);
            if (estimatedTokens > maxInputTokens) {
                response.put("success", false);
                response.put("message", "输入内容超过最大 Token 限制（最大 " + maxInputTokens + " Token）");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ObjectMapper().writeValueAsString(response).getBytes());
            }

            // 调用通义千问 API 生成剧本
            String script = generateScript(content);
            System.out.println("生成剧本长度：" + script.length());
            if (script.isEmpty()) {
                response.put("success", false);
                response.put("message", "无法从文档中生成剧本");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ObjectMapper().writeValueAsString(response).getBytes());
            }

            // 返回纯文本剧本
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.setContentDisposition(ContentDisposition.attachment().filename("live_script.txt").build());

            return new ResponseEntity<>(script.getBytes(StandardCharsets.UTF_8), headers, HttpStatus.OK);

        } catch (Exception e) {
            System.out.println("直播剧本生成失败：" + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "直播剧本生成失败，服务器错误：" + e.getMessage());
            try {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new ObjectMapper().writeValueAsString(response).getBytes());
            } catch (Exception ex) {
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
     * 调用通义千问 API，生成直播剧本
     * @param content 商品文档内容
     * @return 直播剧本纯文本
     */
    // 大白话注释：调用通义千问 API，生成直播剧本，优化为推理模式
    private String generateScript(String content) {
        // 从数据库获取模型配置
        List<ModelConfig> configs = modelConfigMapper.selectAllModelConfigs();
        if (configs.isEmpty()) {
            System.out.println("未找到模型配置");
            return "";
        }
        ModelConfig config = configs.get(0); // 假设取第一条配置

        // 大白话注释：检查 liveurl 是否为 null
        if (config.getLiveurl() == null || config.getLiveurl().trim().isEmpty()) {
            System.out.println("直播剧本生成失败：liveurl 未配置");
            return "";
        }

        // 大白话注释：修复 URL 构造，避免重复拼接 /chat/completions
        String url = config.getLiveurl(); // 直接使用 liveurl，不再额外拼接
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + config.getLivekey());

        // 构造提示词
        String prompt = String.format(LIVE_SCRIPT_PROMPT, content);

        // 构造请求体，优化为推理模式
        Map<String, Object> body = new HashMap<>();
        body.put("model", config.getLivemodel());
        body.put("messages", List.of(
                Map.of("role", "user", "content", prompt)
        ));
        body.put("stream", false);
        body.put("response_format", Map.of("type", "text")); // 输出纯文本
        body.put("temperature", 0.5); // 大白话注释：温度设为0.0，确保严格遵循提示词
        body.put("top_p", 0.9); // 大白话注释：限制生成范围，减少无关内容
        body.put("presence_penalty", 0.0); // 大白话注释：减少生成额外内容
        body.put("max_tokens", maxTokens); // 大白话注释：保持最大 Token 数，支持长输出

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
            System.out.println("通义千问 API 响应状态码：" + response.getStatusCode());
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String script = (String) message.get("content");
                    // 大白话注释：后处理，过滤开场白和结束语
                    script = filterScript(script);
                    System.out.println("通义千问 API 返回剧本：" + script);
                    return script.trim();
                }
            }
            System.out.println("通义千问 API 调用失败，状态码：" + response.getStatusCode());
            return "";
        } catch (HttpClientErrorException e) {
            System.out.println("通义千问 API 返回错误：" + e.getMessage());
            return "";
        }
    }

    // 大白话注释：过滤剧本，移除可能的开场白和结束语
    private String filterScript(String script) {
        String[] startPhrases = {"大家好", "欢迎来到", "直播间", "今天我们"};
        String[] endPhrases = {"感谢观看", "下期再见", "谢谢大家", "再见"};
        String result = script;

        // 移除开场白
        for (String phrase : startPhrases) {
            if (result.toLowerCase().startsWith(phrase.toLowerCase())) {
                int index = result.toLowerCase().indexOf(phrase.toLowerCase()) + phrase.length();
                result = result.substring(index).trim();
                break;
            }
        }

        // 移除结束语
        for (String phrase : endPhrases) {
            if (result.toLowerCase().endsWith(phrase.toLowerCase())) {
                int index = result.toLowerCase().lastIndexOf(phrase.toLowerCase());
                result = result.substring(0, index).trim();
                break;
            }
        }

        return result;
    }

    /**
     * 估算输入内容的 Token 数，简单基于字符数
     * 大白话注释：中文 1 字符 ≈ 1 Token，英文单词算半个 Token，粗略估算
     * @param content 输入内容
     * @return 估算的 Token 数
     */
    private int estimateTokenCount(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        // 大白话注释：直接用字符数作为 Token 数，中文为主的文档比较准
        return content.length();
    }
}