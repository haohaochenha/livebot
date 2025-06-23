package com.example.douyinlive.controller;

import com.example.douyinlive.entity.Agent;
import com.example.douyinlive.entity.KnowledgeBase;
import com.example.douyinlive.entity.ModelConfig; // 已有：引入 ModelConfig 实体
import com.example.douyinlive.entity.User;
import com.example.douyinlive.mapper.AgentMapper;
import com.example.douyinlive.mapper.KnowledgeBaseMapper;
import com.example.douyinlive.mapper.ModelConfigMapper; // 已有：引入 ModelConfigMapper
import com.example.douyinlive.mapper.UserMapper;
import com.example.douyinlive.util.JwtUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgvector.PGvector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 智能体控制器，处理智能体的创建、调用、查询、删除等请求
 */
@RestController
@RequestMapping("/agents")
public class AgentController {

    // 保留：从配置文件读取向量维度限制
    @Value("${embedding.dimension}")
    private int embeddingDimension;

    // 注入 JwtUtil，用于解析 token 获取用户名
    @Autowired
    private JwtUtil jwtUtil;

    // 注入 AgentMapper 和 UserMapper
    @Autowired
    private AgentMapper agentMapper;

    @Autowired
    private UserMapper userMapper;

    // 注入 KnowledgeBaseMapper，用于知识库查询
    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    // 注入 RestTemplate，用于向量 API 调用
    @Autowired
    private RestTemplate restTemplate;

    // 注入 QwenController，用于复用对话逻辑
    @Autowired
    private QwenController qwenController;

    // 已有：注入 ModelConfigMapper，获取模型配置
    @Autowired
    private ModelConfigMapper modelConfigMapper;

    /**
     * 创建直播智能体
     * @param agentData 智能体数据（名称、系统提示词、知识库 ID 及模型参数）
     * @param token 用户 token
     * @return 创建结果
     */
    @PostMapping
    public Map<String, Object> createAgent(@RequestBody Map<String, Object> agentData,
                                           @RequestHeader("Authorization") String token) {
        Map<String, Object> response = new HashMap<>();
        try {
            // 去掉 "Bearer " 前缀，获取用户名
            token = token.substring(7);
            String username = jwtUtil.getUsernameFromToken(token);
            User user = userMapper.selectUserByName(username);
            if (user == null) {
                response.put("success", false);
                response.put("message", "用户不存在");
                return response;
            }

            // 检查智能体名称是否已存在
            String agentName = (String) agentData.get("name");
            Agent existingAgent = agentMapper.selectAgentByUserIdAndName(user.getId(), agentName);
            if (existingAgent != null) {
                response.put("success", false);
                response.put("message", "智能体名称已存在");
                return response;
            }

            // 验证知识库 ID
            String kbIds = null;
            if (agentData.containsKey("kb_ids")) {
                List<String> kbIdList = (List<String>) agentData.get("kb_ids");
                if (!kbIdList.isEmpty()) {
                    for (String kbId : kbIdList) {
                        if (!kbId.matches("^[a-zA-Z0-9_]+$")) {
                            response.put("success", false);
                            response.put("message", "知识库 ID 只能包含字母、数字和下划线");
                            return response;
                        }
                        // 检查知识库是否存在
                        List<KnowledgeBase> kbList = knowledgeBaseMapper.selectKnowledgeBasesByUserIdAndKbIdPrefix(user.getId(), kbId + "%");
                        if (kbList.isEmpty()) {
                            response.put("success", false);
                            response.put("message", "知识库 ID " + kbId + " 不存在");
                            return response;
                        }
                    }
                    kbIds = String.join(",", kbIdList);
                }
            }

            // 创建智能体对象
            Agent agent = new Agent();
            agent.setUserId(user.getId());
            agent.setName(agentName);
            agent.setSystemPrompt((String) agentData.get("system_prompt"));
            agent.setKbIds(kbIds);
            // 设置模型参数，使用推荐值作为默认值
            agent.setTemperature(agentData.get("temperature") != null ? ((Number) agentData.get("temperature")).doubleValue() : 0.8); // 改成 0.8
            agent.setTopP(agentData.get("top_p") != null ? ((Number) agentData.get("top_p")).doubleValue() : 0.8);
            agent.setPresencePenalty(agentData.get("presence_penalty") != null ? ((Number) agentData.get("presence_penalty")).doubleValue() : 0.3); // 改成 0.3
            agent.setMaxTokens((Integer) agentData.getOrDefault("max_tokens", 1024));
            agent.setN((Integer) agentData.getOrDefault("n", 1));
            agent.setSeed((Integer) agentData.get("seed"));
            agent.setStop((String) agentData.get("stop"));
            agent.setTools(agentData.get("tools") != null ? new ObjectMapper().writeValueAsString(agentData.get("tools")) : null);
            agent.setToolChoice((String) agentData.getOrDefault("tool_choice", "auto"));
            agent.setParallelToolCalls((Boolean) agentData.getOrDefault("parallel_tool_calls", false));
            agent.setEnableSearch((Boolean) agentData.getOrDefault("enable_search", false));
            agent.setSearchOptions(agentData.get("search_options") != null ? new ObjectMapper().writeValueAsString(agentData.get("search_options")) : null);
            agent.setTranslationOptions(agentData.get("translation_options") != null ? new ObjectMapper().writeValueAsString(agentData.get("translation_options")) : null);
            agent.setCreatedAt(LocalDateTime.now());
            agent.setUpdatedAt(LocalDateTime.now());

            // 插入智能体
            int result = agentMapper.insertAgent(agent);
            if (result > 0) {
                response.put("success", true);
                response.put("message", "智能体创建成功");
                response.put("data", agent);
            } else {
                response.put("success", false);
                response.put("message", "智能体创建失败");
            }
        } catch (Exception e) {
            System.out.println("创建智能体失败：" + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "创建智能体失败，服务器错误");
        }
        return response;
    }

    /**
     * 查询当前用户的所有智能体
     * @param token 用户 token
     * @return 智能体列表
     */
    @GetMapping
    public Map<String, Object> getAgents(@RequestHeader("Authorization") String token) {
        Map<String, Object> response = new HashMap<>();
        try {
            // 去掉 "Bearer " 前缀，获取用户名
            token = token.substring(7);
            String username = jwtUtil.getUsernameFromToken(token);
            User user = userMapper.selectUserByName(username);
            if (user == null) {
                response.put("success", false);
                response.put("message", "用户不存在");
                return response;
            }

            // 查询用户的所有智能体
            List<Agent> agents = agentMapper.selectAgentsByUserId(user.getId());
            response.put("success", true);
            response.put("message", "查询成功");
            response.put("data", agents);
        } catch (Exception e) {
            System.out.println("查询智能体失败：" + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "查询智能体失败，服务器错误");
        }
        return response;
    }

    /**
     * 删除智能体
     * @param id 智能体 ID
     * @param token 用户 token
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteAgent(@PathVariable("id") Integer id,
                                           @RequestHeader("Authorization") String token) {
        Map<String, Object> response = new HashMap<>();
        try {
            // 去掉 "Bearer " 前缀，获取用户名
            token = token.substring(7);
            String username = jwtUtil.getUsernameFromToken(token);
            User user = userMapper.selectUserByName(username);
            if (user == null) {
                response.put("success", false);
                response.put("message", "用户不存在");
                return response;
            }

            // 获取智能体
            Agent agent = agentMapper.selectAgentById(id);
            if (agent == null) {
                response.put("success", false);
                response.put("message", "智能体不存在");
                return response;
            }
            // 验证智能体是否属于当前用户
            if (agent.getUserId() != user.getId()) {
                response.put("success", false);
                response.put("message", "无权删除该智能体");
                return response;
            }

            // 删除智能体
            int result = agentMapper.deleteAgentById(id);
            if (result > 0) {
                response.put("success", true);
                response.put("message", "智能体删除成功");
            } else {
                response.put("success", false);
                response.put("message", "智能体删除失败");
            }
        } catch (Exception e) {
            System.out.println("删除智能体失败：" + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "删除智能体失败，服务器错误");
        }
        return response;
    }

    /**
     * 调用智能体进行 AI 对话，基于通义千问 API，返回 JSON 响应
     * @param id 智能体 ID
     * @param requestBody 前端传递的请求体，包含消息、可选模型参数
     * @param token JWT token，用于验证用户
     * @param session HTTP 会话，用于获取 session ID
     * @return JSON 响应，包含 AI 回复内容
     */
    @PostMapping("/{id}/chat")
    public ResponseEntity<Map<String, Object>> chatWithAgent(@PathVariable("id") Integer id,
                                                             @RequestBody Map<String, Object> requestBody,
                                                             @RequestHeader("Authorization") String token,
                                                             HttpSession session) throws JsonProcessingException {
        System.out.println("收到智能体对话请求，智能体 ID：" + id + "，Session ID：" + session.getId());
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

            // 去掉 "Bearer " 前缀，获取用户名
            token = token.substring(7);
            String username = jwtUtil.getUsernameFromToken(token);
            System.out.println("对话请求用户：" + username);

            // 获取智能体
            Agent agent = agentMapper.selectAgentById(id);
            if (agent == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "智能体不存在");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(errorResponse);
            }
            // 验证智能体是否属于当前用户
            User user = userMapper.selectUserByName(username);
            if (agent.getUserId() != user.getId()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "无权访问该智能体");
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(errorResponse);
            }

            // 准备请求体，合并智能体配置
            Map<String, Object> chatRequestBody = new HashMap<>(requestBody);
            ObjectMapper mapper = new ObjectMapper();
            chatRequestBody.put("model", requestBody.getOrDefault("model", config.getQwenmodel()));
            chatRequestBody.put("temperature", requestBody.get("temperature") != null ? requestBody.get("temperature") : agent.getTemperature());
            chatRequestBody.put("top_p", requestBody.get("top_p") != null ? requestBody.get("top_p") : agent.getTopP());
            chatRequestBody.put("presence_penalty", requestBody.get("presence_penalty") != null ? requestBody.get("presence_penalty") : agent.getPresencePenalty());
            chatRequestBody.put("max_tokens", requestBody.get("max_tokens") != null ? requestBody.get("max_tokens") : agent.getMaxTokens());
            chatRequestBody.put("n", requestBody.get("n") != null ? requestBody.get("n") : agent.getN());
            chatRequestBody.put("seed", requestBody.get("seed") != null ? requestBody.get("seed") : agent.getSeed());
            chatRequestBody.put("stop", requestBody.get("stop") != null ? requestBody.get("stop") : agent.getStop());
            chatRequestBody.put("tools", requestBody.get("tools") != null ? requestBody.get("tools") : (agent.getTools() != null ? mapper.readValue(agent.getTools(), Object.class) : null));
            chatRequestBody.put("tool_choice", requestBody.get("tool_choice") != null ? requestBody.get("tool_choice") : agent.getToolChoice());
            chatRequestBody.put("parallel_tool_calls", requestBody.get("parallel_tool_calls") != null ? requestBody.get("parallel_tool_calls") : agent.getParallelToolCalls());
            chatRequestBody.put("enable_search", requestBody.get("enable_search") != null ? requestBody.get("enable_search") : agent.getEnableSearch());
            chatRequestBody.put("search_options", requestBody.get("search_options") != null ? requestBody.get("search_options") : (agent.getSearchOptions() != null ? mapper.readValue(agent.getSearchOptions(), Object.class) : null));
            chatRequestBody.put("translation_options", requestBody.get("translation_options") != null ? requestBody.get("translation_options") : (agent.getTranslationOptions() != null ? mapper.readValue(agent.getTranslationOptions(), Object.class) : null));

            // 获取用户输入消息
            List<Map<String, Object>> inputMessages = (List<Map<String, Object>>) requestBody.getOrDefault("messages", new ArrayList<>());
            String userInput = inputMessages.stream()
                    .filter(msg -> "user".equals(msg.get("role")))
                    .map(msg -> (String) msg.get("content"))
                    .findFirst()
                    .orElse("");

            // 如果智能体配置了知识库 ID，执行向量检索
            List<String> relevantContents = new ArrayList<>();
            if (agent.getKbIds() != null && !agent.getKbIds().isEmpty()) {
                // 将用户输入向量化
                float[] inputEmbedding = generateEmbedding(userInput);
                if (inputEmbedding != null) {
                    // 获取知识库 ID 列表
                    List<String> kbIdList = Arrays.asList(agent.getKbIds().split(","));
                    // 查询最相似的知识库内容（限制返回 3 条）
                    List<KnowledgeBase> similarKbs = knowledgeBaseMapper.selectTopSimilarKnowledgeBases(
                            user.getId(), kbIdList, new PGvector(inputEmbedding), 3);
                    relevantContents = similarKbs.stream()
                            .map(KnowledgeBase::getContent)
                            .collect(Collectors.toList());
                    System.out.println("检索到相关知识库内容：" + relevantContents);
                }
            }

            // 处理消息，添加系统提示词和知识库上下文
            List<Map<String, Object>> messages = new ArrayList<>();
            // 将知识库内容直接拼接到系统提示词中，不加引导语
            StringBuilder systemPrompt = new StringBuilder(agent.getSystemPrompt() != null ? agent.getSystemPrompt() : "");
            if (!relevantContents.isEmpty()) {
                // 直接拼接知识库内容，不加任何引导语
                systemPrompt.append("\n\n");
                systemPrompt.append(String.join("\n", relevantContents));
            }
            messages.add(Map.of("role", "system", "content", systemPrompt.toString()));
            messages.addAll(inputMessages);
            chatRequestBody.put("messages", messages);

            // 大白话：调用 QwenController 的 chat 方法，获取 JSON 响应
            ResponseEntity<Map<String, Object>> qwenResponse = qwenController.chat(chatRequestBody, "Bearer " + token, session);
            return qwenResponse;

        } catch (Exception e) {
            System.out.println("智能体对话失败：" + e.getMessage());
            e.printStackTrace();
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "智能体对话失败，服务器错误：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error);
        }

    }

    /**
     * 编辑智能体
     * @param id 智能体 ID
     * @param agentData 智能体数据（名称、系统提示词、知识库 ID 及模型参数）
     * @param token 用户 token
     * @return 编辑结果
     */
    @PutMapping("/{id}")
    public Map<String, Object> updateAgent(@PathVariable("id") Integer id,
                                           @RequestBody Map<String, Object> agentData,
                                           @RequestHeader("Authorization") String token) {
        Map<String, Object> response = new HashMap<>();
        try {
            // 去掉 "Bearer " 前缀，获取用户名
            token = token.substring(7);
            String username = jwtUtil.getUsernameFromToken(token);
            User user = userMapper.selectUserByName(username);
            if (user == null) {
                response.put("success", false);
                response.put("message", "用户不存在");
                return response;
            }

            // 获取智能体
            Agent agent = agentMapper.selectAgentById(id);
            if (agent == null) {
                response.put("success", false);
                response.put("message", "智能体不存在");
                return response;
            }
            // 验证智能体是否属于当前用户
            if (agent.getUserId() != user.getId()) {
                response.put("success", false);
                response.put("message", "无权编辑该智能体");
                return response;
            }

            // 检查智能体名称是否冲突（排除自身）
            String agentName = (String) agentData.get("name");
            Agent existingAgent = agentMapper.selectAgentByUserIdAndName(user.getId(), agentName);
            if (existingAgent != null && existingAgent.getId() != id) {
                response.put("success", false);
                response.put("message", "智能体名称已存在");
                return response;
            }

            // 验证知识库 ID
            String kbIds = null;
            if (agentData.containsKey("kb_ids")) {
                List<String> kbIdList = (List<String>) agentData.get("kb_ids");
                if (!kbIdList.isEmpty()) {
                    for (String kbId : kbIdList) {
                        if (!kbId.matches("^[a-zA-Z0-9_]+$")) {
                            response.put("success", false);
                            response.put("message", "知识库 ID 只能包含字母、数字和下划线");
                            return response;
                        }
                        // 检查知识库是否存在
                        List<KnowledgeBase> kbList = knowledgeBaseMapper.selectKnowledgeBasesByUserIdAndKbIdPrefix(user.getId(), kbId + "%");
                        if (kbList.isEmpty()) {
                            response.put("success", false);
                            response.put("message", "知识库 ID " + kbId + " 不存在");
                            return response;
                        }
                    }
                    kbIds = String.join(",", kbIdList);
                }
            }

            // 更新智能体对象
            agent.setName(agentName);
            agent.setSystemPrompt((String) agentData.get("system_prompt"));
            agent.setKbIds(kbIds);
            agent.setTemperature(agentData.get("temperature") != null ? ((Number) agentData.get("temperature")).doubleValue() : 0.8); // 改成 0.8
            agent.setTopP(agentData.get("top_p") != null ? ((Number) agentData.get("top_p")).doubleValue() : 0.8);
            agent.setPresencePenalty(agentData.get("presence_penalty") != null ? ((Number) agentData.get("presence_penalty")).doubleValue() : 0.3); // 改成 0.3
            agent.setMaxTokens((Integer) agentData.getOrDefault("max_tokens", 1024));
            agent.setN((Integer) agentData.getOrDefault("n", 1));
            agent.setSeed((Integer) agentData.get("seed"));
            agent.setStop((String) agentData.get("stop"));
            agent.setTools(agentData.get("tools") != null ? new ObjectMapper().writeValueAsString(agentData.get("tools")) : null);
            agent.setToolChoice((String) agentData.getOrDefault("tool_choice", "auto"));
            agent.setParallelToolCalls((Boolean) agentData.getOrDefault("parallel_tool_calls", false));
            agent.setEnableSearch((Boolean) agentData.getOrDefault("enable_search", false));
            agent.setSearchOptions(agentData.get("search_options") != null ? new ObjectMapper().writeValueAsString(agentData.get("search_options")) : null);
            agent.setTranslationOptions(agentData.get("translation_options") != null ? new ObjectMapper().writeValueAsString(agentData.get("translation_options")) : null);
            agent.setUpdatedAt(LocalDateTime.now());

            // 更新数据库
            int result = agentMapper.updateAgent(agent);
            if (result > 0) {
                response.put("success", true);
                response.put("message", "智能体编辑成功");
                response.put("data", agent);
            } else {
                response.put("success", false);
                response.put("message", "智能体编辑失败");
            }
        } catch (Exception e) {
            System.out.println("编辑智能体失败：" + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "编辑智能体失败，服务器错误");
        }
        return response;
    }

    /**
     * 生成输入文本的嵌入向量
     * @param input 输入文本
     * @return 向量数组
     */
    private float[] generateEmbedding(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }

        // 已有：从数据库获取配置
        List<ModelConfig> configs = modelConfigMapper.selectAllModelConfigs();
        if (configs.isEmpty()) {
            System.out.println("未找到模型配置");
            return null;
        }
        ModelConfig config = configs.get(0); //

        String url = config.getEmbeddingurl() + "/embeddings";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + config.getEmbeddingkey());

        Map<String, Object> body = new HashMap<>();
        body.put("model", config.getEmbeddingmodel());
        body.put("input", input);
        body.put("dimension", embeddingDimension); // 使用配置文件中的维度
        body.put("encoding_format", "float");

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
                if (!data.isEmpty()) {
                    List<Double> embedding = (List<Double>) data.get(0).get("embedding");
                    float[] vector = new float[embedding.size()];
                    for (int i = 0; i < embedding.size(); i++) {
                        vector[i] = embedding.get(i).floatValue();
                    }
                    return vector;
                }
            }
            System.out.println("向量生成失败，状态码：" + response.getStatusCode());
            return null;
        } catch (HttpClientErrorException e) {
            System.out.println("向量 API 返回错误：" + e.getResponseBodyAsString());
            return null;
        }
    }
}