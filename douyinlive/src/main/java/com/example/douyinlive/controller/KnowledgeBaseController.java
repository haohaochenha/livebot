package com.example.douyinlive.controller;

import com.example.douyinlive.entity.KnowledgeBase;
import com.example.douyinlive.entity.User;
import com.example.douyinlive.entity.ModelConfig; // 新增：引入 ModelConfig
import com.example.douyinlive.mapper.KnowledgeBaseMapper;
import com.example.douyinlive.mapper.UserMapper;
import com.example.douyinlive.mapper.ModelConfigMapper; // 新增：引入 ModelConfigMapper
import com.example.douyinlive.util.JwtUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgvector.PGvector;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库控制器，处理知识库创建、查询、更新、删除等操作
 */
@RestController
@RequestMapping("/knowledge-base")
public class KnowledgeBaseController {

    // 注入 UserMapper，查询用户信息
    @Autowired
    private UserMapper userMapper;

    // 注入 KnowledgeBaseMapper，操作知识库表
    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    // 注入 JwtUtil，解析 token
    @Autowired
    private JwtUtil jwtUtil;

    // 注入 RestTemplate，调用向量 API
    @Autowired
    private RestTemplate restTemplate;

    // 新增：注入 ModelConfigMapper，获取模型配置
    @Autowired
    private ModelConfigMapper modelConfigMapper;

    // 从配置文件读取向量维度及限制
    @Value("${embedding.dimension}")
    private int embeddingDimension;

    @Value("${embedding.max-tokens}")
    private int maxTokens;

    @Value("${embedding.max-lines}")
    private int maxLines;

    // 从配置文件读取文件限制
    @Value("${knowledge-base.max-text-length}")
    private int maxTextLength;

    @Value("${knowledge-base.min-text-length}")
    private int minTextLength;

    @Value("${knowledge-base.max-file-size}")
    private long maxFileSize;

    /**
     * 创建知识库，上传文件并生成向量
     * @param kbId 知识库 ID
     * @param file 上传的文件
     * @param token 用户 token
     * @return 创建创建$创建结果
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional // 添加事务管理
    public ResponseEntity<Map<String, Object>> createKnowledgeBase(
            @RequestPart("kb_id") String kbId,
            @RequestPart("file") MultipartFile file,
            @RequestHeader("Authorization") String token) {
        System.out.println("收到知识库创建请求，kb_id：" + kbId + "，文件名：" + (file != null ? file.getOriginalFilename() : "无"));
        Map<String, Object> response = new HashMap<>();
        try {
            // 去掉 "Bearer " 前缀，获取用户名
            token = token.substring(7);
            String username = jwtUtil.getUsernameFromToken(token);
            User user = userMapper.selectUserByName(username);
            if (user == null) {
                response.put("success", false);
                response.put("message", "用户不存在"); // 修复：正确设置 message 键
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // 验证知识库 ID
            if (kbId == null || kbId.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "知识库 ID 不能为空");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            // 验证 kbId 格式（只允许字母、数字、下划线）
            if (!kbId.matches("^[a-zA-Z0-9_]+$")) {
                response.put("success", false);
                response.put("message", "知识库 ID 只能包含字母、数字和下划线");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            // 检查知识库 ID 是否重复
            KnowledgeBase existingKb = knowledgeBaseMapper.selectKnowledgeBaseByUserIdAndKbId(user.getId(), kbId);
            if (existingKb != null) {
                response.put("success", false);
                response.put("message", "知识库 ID 已存在");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // 验证文件
            if (file == null || file.isEmpty()) {
                response.put("success", false);
                response.put("message", "文件不能为空");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            if (file.getSize() > maxFileSize) {
                response.put("success", false);
                response.put("message", "文件大小超过限制（最大 " + (maxFileSize / 1024 / 1024) + "MB）");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            String fileName = file.getOriginalFilename().toLowerCase();
            if (!fileName.endsWith(".txt") && !fileName.endsWith(".doc") && !fileName.endsWith(".docx") &&
                    !fileName.endsWith(".xls") && !fileName.endsWith(".xlsx") && !fileName.endsWith(".pdf") &&
                    !fileName.endsWith(".json")) {
                response.put("success", false);
                response.put("message", "不支持的文件类型，仅支持 TXT、Word、Excel、PDF、JSON");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // 解析文件为文本
            String content;
            boolean isJsonFile = fileName.endsWith(".json");
            if (isJsonFile) {
                // 直接读取 JSON 文件内容
                try (InputStream is = file.getInputStream()) {
                    content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    // 验证 JSON 格式
                    new ObjectMapper().readValue(content, List.class);
                    System.out.println("解析 JSON 文件内容，长度：" + content.length());
                } catch (JsonProcessingException e) {
                    response.put("success", false);
                    response.put("message", "JSON 文件格式无效");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                }
            } else {
                // 其他文件类型使用原有解析逻辑
                content = parseFile(file);
                System.out.println("解析文件内容，长度：" + content.length());
            }
            if (content.length() < minTextLength) {
                response.put("success", false);
                response.put("message", "文本长度过短，至少 " + minTextLength + " 字符");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            if (content.length() > maxTextLength) {
                // 自动截断超长文本
                content = content.substring(0, maxTextLength);
                System.out.println("文本超长，已截断至 " + maxTextLength + " 字符");
            }

            // 分割文本（按 8192 Token 估算）
            List<String> segments = isJsonFile ? segmentJsonContent(content) : segmentText(content, maxTokens);
            System.out.println("文本分割为 " + segments.size() + " 段");
            if (segments.size() > maxLines) {
                response.put("success", false);
                response.put("message", "文本段数超过限制（最大 " + maxLines + " 段）");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // 生成向量
            List<float[]> embeddings = generateEmbeddings(segments);
            if (embeddings.size() != segments.size()) {
                response.put("success", false);
                response.put("message", "向量生成失败");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

            // 检查所有 kbId 唯一性
            LocalDateTime now = LocalDateTime.now();
            List<KnowledgeBase> kbList = new ArrayList<>();
            for (int i = 0; i < segments.size(); i++) {
                String newKbId = kbId + "_" + i;
                existingKb = knowledgeBaseMapper.selectKnowledgeBaseByUserIdAndKbId(user.getId(), newKbId);
                if (existingKb != null) {
                    response.put("success", false);
                    response.put("message", "知识库 ID " + newKbId + " 已存在");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                }
                KnowledgeBase kb = new KnowledgeBase();
                kb.setUserId(user.getId());
                kb.setKbId(newKbId);
                kb.setContent(segments.get(i));
                kb.setEmbedding(new PGvector(embeddings.get(i)));
                kb.setCreatedAt(now);
                kb.setUpdatedAt(now);
                kbList.add(kb);
            }

            // 批量插入数据库
            int result = knowledgeBaseMapper.batchInsertKnowledgeBase(kbList);
            if (result != kbList.size()) {
                throw new RuntimeException("存储知识库失败，插入数量不匹配");
            }

            response.put("success", true);
            response.put("message", "知识库创建成功");
            Map<String, Object> data = new HashMap<>();
            data.put("kb_id", kbId);
            data.put("user_id", user.getId());
            data.put("segments", segments.size());
            response.put("data", data);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.out.println("创建知识库失败：" + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "创建知识库失败，服务器错误：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 分割 JSON 内容为问答对段落
     * @param jsonContent JSON 字符串
     * @return 分割后的段落列表
     */
    private List<String> segmentJsonContent(String jsonContent) {
        List<String> segments = new ArrayList<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, String>> qaPairs = mapper.readValue(jsonContent, List.class);
            for (Map<String, String> qa : qaPairs) {
                String segment = mapper.writeValueAsString(qa);
                segments.add(segment);
            }
        } catch (JsonProcessingException e) {
            System.out.println("JSON 分割失败：" + e.getMessage());
            throw new RuntimeException("JSON 分割失败：" + e.getMessage());
        }
        return segments;
    }

    /**
     * 获取用户知识库列表
     * @param token 用户 token
     * @return 知识库列表
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listKnowledgeBases(@RequestHeader("Authorization") String token) {
        System.out.println("收到知识库列表请求");
        Map<String, Object> response = new HashMap<>();
        try {
            // 去掉 "Bearer " 前缀，获取用户名
            token = token.substring(7);
            String username = jwtUtil.getUsernameFromToken(token);
            User user = userMapper.selectUserByName(username);
            if (user == null) {
                response.put("success", false);
                response.put("message", "用户不存在");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // 查询用户的知识库列表
            List<KnowledgeBase> kbList = knowledgeBaseMapper.selectKnowledgeBasesByUserId(user.getId());
            System.out.println("查询到知识库数量：" + kbList.size());

            // 转换为前端所需格式
            List<Map<String, Object>> kbData = new ArrayList<>();
            for (KnowledgeBase kb : kbList) {
                Map<String, Object> item = new HashMap<>();
                item.put("kbId", kb.getKbId());
                item.put("createdAt", kb.getCreatedAt().toString());
                kbData.add(item);
            }

            response.put("success", true);
            response.put("message", "获取知识库列表成功");
            response.put("data", kbData);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.out.println("获取知识库列表失败：" + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "获取知识库列表失败，服务器错误：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 查询具体知识库内容
     * @param kbId 知识库 ID
     * @param token 用户 token
     * @return 知识库详细信息
     */
    @GetMapping("/{kbId}")
    public ResponseEntity<Map<String, Object>> getKnowledgeBase(
            @PathVariable("kbId") String kbId,
            @RequestHeader("Authorization") String token) {
        System.out.println("收到知识库查询请求，kb_id：" + kbId);
        Map<String, Object> response = new HashMap<>();
        try {
            // 去掉 "Bearer " 前缀，获取用户名
            token = token.substring(7);
            String username = jwtUtil.getUsernameFromToken(token);
            User user = userMapper.selectUserByName(username);
            if (user == null) {
                response.put("success", false);
                response.put("message", "用户不存在");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // 验证知识库 ID
            if (kbId == null || kbId.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "知识库 ID 不能为空");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // 查询以 kbId 开头的所有知识库记录
            List<KnowledgeBase> kbList = knowledgeBaseMapper.selectKnowledgeBasesByUserIdAndKbIdPrefix(user.getId(), kbId + "%");
            if (kbList.isEmpty()) {
                response.put("success", false);
                response.put("message", "知识库不存在");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // 整理返回数据
            List<Map<String, Object>> kbData = new ArrayList<>();
            for (KnowledgeBase kb : kbList) {
                Map<String, Object> item = new HashMap<>();
                item.put("kbId", kb.getKbId());
                item.put("content", kb.getContent());
                item.put("createdAt", kb.getCreatedAt().toString());
                item.put("updatedAt", kb.getUpdatedAt().toString());
                kbData.add(item);
            }

            response.put("success", true);
            response.put("message", "查询知识库成功");
            response.put("data", kbData);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.out.println("查询知识库失败：" + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "查询知识库失败，服务器错误：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 删除知识库
     * @param kbId 知识库 ID
     * @param token 用户 token
     * @return 删除结果
     */
    @DeleteMapping("/{kbId}")
    @Transactional // 添加事务管理
    public ResponseEntity<Map<String, Object>> deleteKnowledgeBase(
            @PathVariable("kbId") String kbId,
            @RequestHeader("Authorization") String token) {
        System.out.println("收到知识库删除请求，kb_id：" + kbId);
        Map<String, Object> response = new HashMap<>();
        try {
            // 去掉 "Bearer " 前缀，获取用户名
            token = token.substring(7);
            String username = jwtUtil.getUsernameFromToken(token);
            User user = userMapper.selectUserByName(username);
            if (user == null) {
                response.put("success", false);
                response.put("message", "用户不存在");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // 验证知识库 ID
            if (kbId == null || kbId.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "知识库 ID 不能为空");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // 删除以 kbId 开头的所有知识库记录（因为上传时可能生成多个 kbId_i）
            int result = knowledgeBaseMapper.deleteKnowledgeBasesByUserIdAndKbIdPrefix(user.getId(), kbId + "%");
            System.out.println("删除知识库记录数：" + result);

            if (result == 0) {
                response.put("success", false);
                response.put("message", "知识库不存在");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            response.put("success", true);
            response.put("message", "知识库删除成功");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.out.println("删除知识库失败：" + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "删除知识库失败，服务器错误：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 更新知识库
     * @param kbId 知识库 ID
     * @param file 上传的新文件
     * @param token 用户 token
     * @return 更新结果
     */
    @PutMapping(value = "/{kbId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional // 添加事务管理
    public ResponseEntity<Map<String, Object>> updateKnowledgeBase(
            @PathVariable("kbId") String kbId,
            @RequestPart("file") MultipartFile file,
            @RequestHeader("Authorization") String token) {
        System.out.println("收到知识库更新请求，kb_id：" + kbId + "，文件名：" + (file != null ? file.getOriginalFilename() : "无"));
        Map<String, Object> response = new HashMap<>();
        try {
            // 去掉 "Bearer " 前缀，获取用户名
            token = token.substring(7);
            String username = jwtUtil.getUsernameFromToken(token);
            User user = userMapper.selectUserByName(username);
            if (user == null) {
                response.put("success", false);
                response.put("message", "用户不存在");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // 验证知识库 ID
            if (kbId == null || kbId.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "知识库 ID 不能为空");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            if (!kbId.matches("^[a-zA-Z0-9_]+$")) {
                response.put("success", false);
                response.put("message", "知识库 ID 只能包含字母、数字和下划线");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // 检查知识库是否存在
            List<KnowledgeBase> existingKbs = knowledgeBaseMapper.selectKnowledgeBasesByUserIdAndKbIdPrefix(user.getId(), kbId + "%");
            if (existingKbs.isEmpty()) {
                response.put("success", false);
                response.put("message", "知识库不存在");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // 验证文件
            if (file == null || file.isEmpty()) {
                response.put("success", false);
                response.put("message", "文件不能为空");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            if (file.getSize() > maxFileSize) {
                response.put("success", false);
                response.put("message", "文件大小超过限制（最大 " + (maxFileSize / 1024 / 1024) + "MB）");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            String fileName = file.getOriginalFilename().toLowerCase();
            if (!fileName.endsWith(".txt") && !fileName.endsWith(".doc") && !fileName.endsWith(".docx") &&
                    !fileName.endsWith(".xls") && !fileName.endsWith(".xlsx") && !fileName.endsWith(".pdf") &&
                    !fileName.endsWith(".json")) {
                response.put("success", false);
                response.put("message", "不支持的文件类型，仅支持 TXT、Word、Excel、PDF、JSON");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            // 解析文件为文本
            String content;
            boolean isJsonFile = fileName.endsWith(".json");
            if (isJsonFile) {
                // 直接读取 JSON 文件内容
                try (InputStream is = file.getInputStream()) {
                    content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    // 验证 JSON 格式
                    new ObjectMapper().readValue(content, List.class);
                    System.out.println("解析 JSON 文件内容，长度：" + content.length());
                } catch (JsonProcessingException e) {
                    response.put("success", false);
                    response.put("message", "JSON 文件格式无效");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                }
            } else {
                // 其他文件类型使用原有解析逻辑
                content = parseFile(file);
                System.out.println("解析文件内容，长度：" + content.length());
            }
            if (content.length() < minTextLength) {
                response.put("success", false);
                response.put("message", "文本长度过短，至少 " + minTextLength + " 字符");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            if (content.length() > maxTextLength) {
                // 自动截断超长文本
                content = content.substring(0, maxTextLength);
                System.out.println("文本超长，已截断至 " + maxTextLength + " 字符");
            }

            // 分割文本
            List<String> segments = isJsonFile ? segmentJsonContent(content) : segmentText(content, maxTokens);
            System.out.println("文本分割为 " + segments.size() + " 段");

            // 生成向量
            List<float[]> embeddings = generateEmbeddings(segments);
            if (embeddings.size() != segments.size()) {
                response.put("success", false);
                response.put("message", "向量生成失败");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

            // 先删除旧的知识库记录
            int deleteResult = knowledgeBaseMapper.deleteKnowledgeBasesByUserIdAndKbIdPrefix(user.getId(), kbId + "%");
            System.out.println("删除旧知识库记录数：" + deleteResult);

            // 插入新的知识库记录
            LocalDateTime now = LocalDateTime.now();
            List<KnowledgeBase> kbList = new ArrayList<>();
            for (int i = 0; i < segments.size(); i++) {
                KnowledgeBase kb = new KnowledgeBase();
                kb.setUserId(user.getId());
                kb.setContent(segments.get(i));
                kb.setEmbedding(new PGvector(embeddings.get(i)));
                kb.setCreatedAt(now);
                kb.setUpdatedAt(now);
                kbList.add(kb);
            }

            // 批量插入新记录
            int insertResult = knowledgeBaseMapper.batchInsertKnowledgeBase(kbList);
            if (insertResult != kbList.size()) {
                throw new RuntimeException("存储知识库失败，插入数量不匹配");
            }

            response.put("success", true);
            response.put("message", "知识库更新成功");
            Map<String, Object> data = new HashMap<>();
            data.put("kb_id", kbId);
            data.put("user_id", user.getId());
            data.put("segments", segments.size());
            response.put("data", data);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.out.println("更新知识库失败：" + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "更新知识库失败，服务器错误：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 解析文件为文本
     * @param file 上传的文件
     * @return 解析后的文本
     */
    private String parseFile(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename().toLowerCase();
        try (InputStream is = file.getInputStream()) {
            if (fileName.endsWith(".txt")) {
                // 明确指定 UTF-8 编码，避免乱码
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } else if (fileName.endsWith(".doc") || fileName.endsWith(".docx")) {
                try {
                    XWPFDocument doc = new XWPFDocument(is);
                    StringBuilder sb = new StringBuilder();
                    for (XWPFParagraph para : doc.getParagraphs()) {
                        sb.append(para.getText()).append("\n");
                    }
                    return sb.toString();
                } catch (Exception e) {
                    throw new IOException("解析 Word 文件失败，文件可能损坏或格式不支持");
                }
            } else if (fileName.endsWith(".xls") || fileName.endsWith(".xlsx")) {
                try {
                    Workbook workbook = fileName.endsWith(".xls") ? new HSSFWorkbook(is) : new XSSFWorkbook(is);
                    StringBuilder sb = new StringBuilder();
                    // 只解析第一个 Sheet，避免复杂结构
                    Sheet sheet = workbook.getSheetAt(0);
                    for (Row row : sheet) {
                        for (Cell cell : row) {
                            sb.append(cell.toString()).append(" ");
                        }
                        sb.append("\n");
                    }
                    return sb.toString();
                } catch (Exception e) {
                    throw new IOException("解析 Excel 文件失败，文件可能损坏或格式不支持");
                }
            } else if (fileName.endsWith(".pdf")) {
                try {
                    PDDocument pdf = PDDocument.load(is);
                    PDFTextStripper stripper = new PDFTextStripper();
                    String text = stripper.getText(pdf);
                    pdf.close();
                    return text;
                } catch (Exception e) {
                    throw new IOException("解析 PDF 文件失败，文件可能加密或格式不支持");
                }
            }
        }
        throw new IOException("无法解析文件，文件类型不支持");
    }

    /**
     * 分割文本为不超过 maxTokens 的段落
     * @param content 原始文本
     * @param maxTokens 最大 Token 数
     * @return 分割后的段落列表
     */
    private List<String> segmentText(String content, int maxTokens) {
        List<String> segments = new ArrayList<>();
        // 简单按字符数估算 Token（1 Token ≈ 0.75 字），后续可引入 tiktoken
        int maxChars = maxTokens * 3 / 4;
        // 清理空行和多余空格
        String[] lines = content.replaceAll("\r\n", "\n").replaceAll("\n{2,}", "\n").trim().split("\n");
        StringBuilder segment = new StringBuilder();
        int currentLength = 0;

        for (String line : lines) {
            if (line.trim().isEmpty()) continue; // 跳过空行
            if (currentLength + line.length() > maxChars) {
                if (segment.length() > 0) {
                    segments.add(segment.toString());
                    segment = new StringBuilder();
                    currentLength = 0;
                }
            }
            segment.append(line).append("\n");
            currentLength += line.length();
        }
        if (segment.length() > 0) {
            segments.add(segment.toString());
        }
        return segments;
    }

    /**
     * 调用向量 API 生成嵌入向量
     * @param segments 文本段落
     * @return 向量列表
     */
    private List<float[]> generateEmbeddings(List<String> segments) {
        // 从数据库获取模型配置
        List<ModelConfig> configs = modelConfigMapper.selectAllModelConfigs();
        if (configs.isEmpty()) {
            System.out.println("未找到模型配置");
            throw new RuntimeException("未找到模型配置");
        }
        ModelConfig config = configs.get(0); // 假设取第一条配置

        List<float[]> embeddings = new ArrayList<>();
        String url = config.getEmbeddingurl() + "/embeddings";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + config.getEmbeddingkey());

        // 分批处理，每 50 段一组
        int batchSize = 10;
        for (int i = 0; i < segments.size(); i += batchSize) {
            List<String> batch = segments.subList(i, Math.min(i + batchSize, segments.size()));
            Map<String, Object> body = new HashMap<>();
            body.put("model", config.getEmbeddingmodel());
            body.put("input", batch);
            body.put("dimension", embeddingDimension);
            body.put("encoding_format", "float");

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            try {
                ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
                    if (data.size() != batch.size()) {
                        System.out.println("向量数量与输入段落不匹配，预期：" + batch.size() + "，实际：" + data.size());
                        throw new RuntimeException("向量生成失败，数量不匹配");
                    }
                    for (Map<String, Object> item : data) {
                        List<Double> embedding = (List<Double>) item.get("embedding");
                        if (embedding.size() != embeddingDimension) {
                            System.out.println("向量维度错误，预期：" + embeddingDimension + "，实际：" + embedding.size());
                            throw new RuntimeException("向量维度不匹配");
                        }
                        float[] vector = new float[embedding.size()];
                        for (int j = 0; j < embedding.size(); j++) {
                            vector[j] = embedding.get(j).floatValue();
                        }
                        embeddings.add(vector);
                    }
                } else {
                    System.out.println("向量 API 调用失败，状态码：" + response.getStatusCode());
                    throw new RuntimeException("向量 API 调用失败");
                }
            } catch (HttpClientErrorException e) {
                System.out.println("向量 API 返回错误：" + e.getResponseBodyAsString());
                throw new RuntimeException("向量 API 调用失败：" + e.getStatusCode());
            }
        }
        return embeddings;
    }
}